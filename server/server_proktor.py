#!/usr/bin/env python3
"""
Server Proktor Lokal & Enkriptor Soal Ujian
MTS ROUDHOTUT THOWALIB
Mendukung enkripsi PDF PBKDF2 + AES-GCM 256-bit dan HTTP Server LAN mandiri.
"""

import argparse
import hashlib
import json
import os
import socket
import sys
import time
from http.server import HTTPServer, BaseHTTPRequestHandler
from pathlib import Path

try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
except ImportError:
    print("[ERROR] Library 'cryptography' belum terpasang. Jalankan: pip install cryptography")
    sys.exit(1)

def get_local_ip() -> str:
    """Mendeteksi IP LAN lokal perangkat yang terhubung ke router Wi-Fi."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        # Menghubungkan ke alamat dummy untuk mendeteksi interface keluar
        s.connect(('10.255.255.255', 1))
        ip = s.getsockname()[0]
    except Exception:
        try:
            ip = socket.gethostbyname(socket.gethostname())
        except Exception:
            ip = "127.0.0.1"
    finally:
        s.close()
    return ip

def encrypt_pdf_exam(pdf_path: Path, output_enc_path: Path, token: str) -> dict:
    """
    Mengenkripsi berkas PDF menggunakan PBKDF2WithHmacSHA256 (10.000 iterasi) + AES-GCM 256-bit.
    Kompatibel 100% dengan CryptoEngine.kt pada aplikasi Android.
    """
    with open(pdf_path, "rb") as f:
        pdf_data = f.read()

    salt_bytes = os.urandom(16)
    iv_bytes = os.urandom(12)

    # Derivasi kunci PBKDF2 (10.000 iterasi, 32 bytes / 256 bits)
    key = hashlib.pbkdf2_hmac(
        'sha256',
        token.strip().encode('utf-8'),
        salt_bytes,
        10000,
        32
    )

    # Enkripsi AES-GCM (Auth Tag 128 bits otomatis di-append di akhir ciphertext)
    aesgcm = AESGCM(key)
    ciphertext = aesgcm.encrypt(iv_bytes, pdf_data, None)

    output_enc_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_enc_path, "wb") as f:
        f.write(ciphertext)

    # Hitung checksum SHA-256 dari berkas .enc
    sha256_hash = hashlib.sha256(ciphertext).hexdigest()

    return {
        "salt_hex": salt_bytes.hex(),
        "iv_hex": iv_bytes.hex(),
        "file_sha256": sha256_hash,
        "enc_size": len(ciphertext)
    }

def generate_exam_package(server_base_url: str, master_pin: str) -> dict:
    """
    Menghasilkan berkas terenkripsi dan manifest.json berdasarkan file di folder raw_pdfs/.
    """
    base_dir = Path(__file__).parent
    raw_dir = base_dir / "raw_pdfs"
    files_dir = base_dir / "files"
    files_dir.mkdir(parents=True, exist_ok=True)

    # Jika folder raw_pdfs belum ada isinya, buat sample otomatis
    if not raw_dir.exists() or not list(raw_dir.glob("*.pdf")):
        print("[INFO] Membuat dokumen soal sample otomatis...")
        from generate_sample_exams import main as gen_sample
        gen_sample()

    # Konfigurasi paket soal standar MTS ROUDHOTUT THOWALIB
    sample_configs = [
        {
            "id": "BINDO-7-2026",
            "pdf_name": "soal_bindo_7.pdf",
            "subject_name": "Bahasa Indonesia",
            "grade_level": 7,
            "duration_minutes": 90,
            "token": "BINDO7"
        },
        {
            "id": "IPA-8-2026",
            "pdf_name": "soal_ipa_8.pdf",
            "subject_name": "Ilmu Pengetahuan Alam (IPA)",
            "grade_level": 8,
            "duration_minutes": 90,
            "token": "IPA8MTS"
        },
        {
            "id": "MAT-9-2026",
            "pdf_name": "soal_matematika_9.pdf",
            "subject_name": "Matematika",
            "grade_level": 9,
            "duration_minutes": 90,
            "token": "MAT9MTS"
        }
    ]

    exams_manifest = []

    print("\n" + "=" * 65)
    print("PROSES ENKRIPSI LEMBAR SOAL UJIAN (AES-GCM 256-BIT)")
    print("=" * 65)

    # Pastikan stdout mendukung UTF-8 di Windows
    if sys.stdout.encoding != 'utf-8':
        try:
            sys.stdout.reconfigure(encoding='utf-8')
        except Exception:
            pass

    for cfg in sample_configs:
        pdf_file = raw_dir / cfg["pdf_name"]
        enc_file = files_dir / f"{cfg['id']}.enc"

        if not pdf_file.exists():
            print(f"[SKIP] Berkas {pdf_file.name} tidak ditemukan.")
            continue

        crypto_meta = encrypt_pdf_exam(pdf_file, enc_file, cfg["token"])
        download_url = f"{server_base_url}/files/{cfg['id']}.enc"

        exam_entry = {
            "id": cfg["id"],
            "subject_name": cfg["subject_name"],
            "grade_level": cfg["grade_level"],
            "duration_minutes": cfg["duration_minutes"],
            "download_url": download_url,
            "file_sha256": crypto_meta["file_sha256"],
            "pbkdf2_salt_hex": crypto_meta["salt_hex"],
            "gcm_iv_hex": crypto_meta["iv_hex"]
        }
        exams_manifest.append(exam_entry)

        print(f"[+] Mapel: {cfg['subject_name']} (Kelas {cfg['grade_level']})")
        print(f"  * Token Angkatan: {cfg['token']}")
        print(f"  * Berkas Enkripsi: {enc_file.name} ({crypto_meta['enc_size']} bytes)")
        print(f"  * SHA-256: {crypto_meta['file_sha256'][:16]}...")

    master_pin_sha256 = hashlib.sha256(master_pin.strip().encode('utf-8')).hexdigest()

    manifest = {
        "school_name": "MTS ROUDHOTUT THOWALIB",
        "sync_timestamp": int(time.time()),
        "master_pin_sha256": master_pin_sha256,
        "exams": exams_manifest
    }

    manifest_path = base_dir / "manifest.json"
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)

    print("=" * 65)
    print(f"[OK] Manifest tersimpan di: {manifest_path}")
    print(f"  • Master PIN Pengawas: {master_pin} (Hash: {master_pin_sha256[:16]}...)")
    print("=" * 65 + "\n")

    return manifest

class ExamServerHandler(BaseHTTPRequestHandler):
    """HTTP Request Handler khusus untuk melayani manifest.json dan berkas .enc."""

    server_dir = Path(__file__).parent

    def do_HEAD(self):
        # Digunakan untuk uji koneksi cepat dari tombol 'Tes Koneksi' di aplikasi siswa
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Type", "text/plain")
        self.end_headers()

    def do_GET(self):
        clean_path = self.path.split("?")[0].strip("/")

        # Endpoint Root / Status
        if clean_path == "" or clean_path == "status":
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            html = f"""
            <html>
            <head><title>MTS ROUDHOTUT THOWALIB - Server Ujian</title></head>
            <body style='font-family: sans-serif; padding: 40px; background: #0F172A; color: #F8FAFC;'>
                <h1 style='color: #10B981;'>Server Proktor MTS ROUDHOTUT THOWALIB</h1>
                <p>Status: <b>ONLINE &amp; SIAP MELAYANI SINKRONISASI</b></p>
                <p><a style='color: #38BDF8;' href='/manifest.json'>Lihat manifest.json</a></p>
            </body>
            </html>
            """
            self.wfile.write(html.encode("utf-8"))
            return

        # Endpoint manifest.json
        if clean_path == "manifest.json":
            manifest_path = self.server_dir / "manifest.json"
            if manifest_path.exists():
                with open(manifest_path, "rb") as f:
                    content = f.read()
                self.send_response(200)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(content)))
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(content)
                print(f"[SYNC] HP Siswa ({self.client_address[0]}) mengunduh manifest.json")
            else:
                self.send_error(404, "Manifest file not found")
            return

        # Endpoint berkas terenkripsi /files/<filename>.enc
        if clean_path.startswith("files/"):
            filename = clean_path.replace("files/", "")
            file_path = self.server_dir / "files" / filename
            if file_path.exists() and file_path.is_file():
                file_size = file_path.stat().st_size
                self.send_response(200)
                self.send_header("Content-Type", "application/octet-stream")
                self.send_header("Content-Length", str(file_size))
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                with open(file_path, "rb") as f:
                    # Stream buffer 16 KB
                    while chunk := f.read(16384):
                        self.wfile.write(chunk)
                print(f"[DOWNLOAD] HP Siswa ({self.client_address[0]}) mengunduh: {filename} ({file_size} bytes)")
            else:
                self.send_error(404, f"File {filename} tidak ditemukan")
            return

        self.send_error(404, "Halaman atau endpoint tidak ditemukan")

    def log_message(self, format, *args):
        # Meredam logging bawaan agar terminal proktor tetap bersih
        pass

def run_server(port: int = 8080, master_pin: str = "888999", build_only: bool = False):
    local_ip = get_local_ip()
    base_url = f"http://{local_ip}:{port}"

    # Generate berkas enkripsi & manifest
    manifest = generate_exam_package(base_url, master_pin)

    if build_only:
        print("[INFO] Mode build-only selesai.")
        return

    server_address = ('0.0.0.0', port)
    httpd = HTTPServer(server_address, ExamServerHandler)

    print("\n" + "#" * 65)
    print("  SERVER PROKTOR LOKAL - MTS ROUDHOTUT THOWALIB")
    print("#" * 65)
    print(f"  • Alamat IP Server Proktor : {base_url}")
    print(f"  • Endpoint Manifest Ujian : {base_url}/manifest.json")
    print(f"  • Master PIN Pengawas     : {master_pin}")
    print("-" * 65)
    print("  PETUNJUK PROKTOR:")
    print(f"  1. Pastikan laptop proktor & HP siswa terhubung ke Wi-Fi lab yang sama.")
    print(f"  2. Pada aplikasi HP siswa, ketikkan alamat berikut di kotak IP Server:")
    print(f"     >>> {base_url} <<<")
    print(f"  3. Tekan 'Tes Koneksi' lalu tekan 'Sinkronkan Soal'.")
    print("-" * 65)
    print("  DAFTAR TOKEN ANGKATAN UNTUK SISWA:")
    for ex in manifest.get("exams", []):
        token_name = "BINDO7" if "7" in ex["id"] else ("IPA8MTS" if "8" in ex["id"] else "MAT9MTS")
        print(f"  • {ex['subject_name']} (Kelas {ex['grade_level']}) -> TOKEN: [{token_name}]")
    print("#" * 65)
    print("\n[READY] Server sedang berjalan... Tekan Ctrl+C untuk menghentikan server.\n")

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n[STOP] Server proktor dihentikan oleh pengguna.")
        httpd.server_close()

def main():
    parser = argparse.ArgumentParser(description="Server Proktor Ujian Lokal MTS ROUDHOTUT THOWALIB")
    parser.add_argument("--port", type=int, default=8080, help="Port server HTTP (default: 8080)")
    parser.add_argument("--pin", type=str, default="888999", help="6-digit Master PIN pengawas (default: 888999)")
    parser.add_argument("--build-only", action="store_true", help="Hanya bangun manifest & berkas .enc tanpa menyalakan server")
    args = parser.parse_args()

    run_server(port=args.port, master_pin=args.pin, build_only=args.build_only)

if __name__ == "__main__":
    main()
