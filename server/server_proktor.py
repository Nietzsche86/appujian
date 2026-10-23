#!/usr/bin/env python3
"""
Server Proktor Web Dashboard & Enkriptor Soal Ujian
MTS ROUDHOTUT THOWALIB
Antarmuka web modern untuk mengatur waktu ujian, token, master PIN, serta upload & enkripsi PDF.
"""

import argparse
import email
from email.policy import default as email_default_policy
import hashlib
import json
import os
import re
import socket
import sys
import time
import webbrowser
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler
from pathlib import Path

try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
except ImportError:
    print("[ERROR] Library 'cryptography' belum terpasang. Jalankan: pip install cryptography")
    sys.exit(1)

BASE_DIR = Path(__file__).parent
RAW_PDFS_DIR = BASE_DIR / "raw_pdfs"
FILES_DIR = BASE_DIR / "files"
CONFIG_FILE = BASE_DIR / "exams_config.json"
MANIFEST_FILE = BASE_DIR / "manifest.json"
HTML_FILE = BASE_DIR / "web_dashboard.html"

# Log aktivitas perangkat siswa di memori
ACTIVITY_LOGS = []

def add_log(ip: str, action: str):
    timestamp = datetime.now().strftime("%H:%M:%S")
    ACTIVITY_LOGS.append({
        "time": timestamp,
        "ip": ip,
        "action": action
    })
    # Batasi maksimal 50 log terakhir
    if len(ACTIVITY_LOGS) > 50:
        ACTIVITY_LOGS.pop(0)

def get_local_ip() -> str:
    """Mendeteksi IP LAN lokal perangkat yang terhubung ke router Wi-Fi."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
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

def load_or_init_config() -> dict:
    if CONFIG_FILE.exists():
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass

    # Default fallback config jika belum ada
    default_config = {
        "school_name": "MTS ROUDHOTUT THOWALIB",
        "master_pin": "888999",
        "exams": [
            {
                "id": "BINDO-7-2026",
                "subject_name": "Bahasa Indonesia",
                "grade_level": 7,
                "duration_minutes": 90,
                "token": "BINDO7",
                "pdf_filename": "soal_bindo_7.pdf"
            },
            {
                "id": "IPA-8-2026",
                "subject_name": "Ilmu Pengetahuan Alam (IPA)",
                "grade_level": 8,
                "duration_minutes": 90,
                "token": "IPA8MTS",
                "pdf_filename": "soal_ipa_8.pdf"
            },
            {
                "id": "MAT-9-2026",
                "subject_name": "Matematika",
                "grade_level": 9,
                "duration_minutes": 90,
                "token": "MAT9MTS",
                "pdf_filename": "soal_matematika_9.pdf"
            }
        ]
    }
    save_config(default_config)
    return default_config

def save_config(config: dict):
    with open(CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(config, f, indent=2, ensure_ascii=False)

def encrypt_pdf_exam(pdf_path: Path, output_enc_path: Path, token: str) -> dict:
    """
    Mengenkripsi PDF menggunakan PBKDF2 (10.000 iterasi) + AES-GCM 256-bit.
    """
    with open(pdf_path, "rb") as f:
        pdf_data = f.read()

    salt_bytes = os.urandom(16)
    iv_bytes = os.urandom(12)

    key = hashlib.pbkdf2_hmac(
        'sha256',
        token.strip().encode('utf-8'),
        salt_bytes,
        10000,
        32
    )

    aesgcm = AESGCM(key)
    ciphertext = aesgcm.encrypt(iv_bytes, pdf_data, None)

    output_enc_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_enc_path, "wb") as f:
        f.write(ciphertext)

    sha256_hash = hashlib.sha256(ciphertext).hexdigest()

    return {
        "salt_hex": salt_bytes.hex(),
        "iv_hex": iv_bytes.hex(),
        "file_sha256": sha256_hash,
        "enc_size": len(ciphertext)
    }

def rebuild_manifest(server_base_url: str):
    """
    Membangun ulang berkas .enc dan manifest.json berdasarkan config terbaru.
    """
    config = load_or_init_config()
    FILES_DIR.mkdir(parents=True, exist_ok=True)
    RAW_PDFS_DIR.mkdir(parents=True, exist_ok=True)

    # Buat sample jika folder raw kosong
    if not list(RAW_PDFS_DIR.glob("*.pdf")):
        try:
            from generate_sample_exams import main as gen_sample
            gen_sample()
        except Exception:
            pass

    exams_manifest = []

    for ex in config.get("exams", []):
        pdf_file = RAW_PDFS_DIR / ex.get("pdf_filename", "")
        enc_file = FILES_DIR / f"{ex['id']}.enc"

        if not pdf_file.exists():
            continue

        crypto_meta = encrypt_pdf_exam(pdf_file, enc_file, ex["token"])
        download_url = f"{server_base_url}/files/{ex['id']}.enc"

        exams_manifest.append({
            "id": ex["id"],
            "subject_name": ex["subject_name"],
            "grade_level": int(ex["grade_level"]),
            "duration_minutes": int(ex["duration_minutes"]),
            "download_url": download_url,
            "file_sha256": crypto_meta["file_sha256"],
            "pbkdf2_salt_hex": crypto_meta["salt_hex"],
            "gcm_iv_hex": crypto_meta["iv_hex"]
        })

    master_pin = config.get("master_pin", "888999")
    master_pin_sha256 = hashlib.sha256(master_pin.strip().encode('utf-8')).hexdigest()

    manifest = {
        "school_name": config.get("school_name", "MTS ROUDHOTUT THOWALIB"),
        "sync_timestamp": int(time.time()),
        "master_pin_sha256": master_pin_sha256,
        "exams": exams_manifest
    }

    with open(MANIFEST_FILE, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)

    return manifest

class ExamProctorWebHandler(BaseHTTPRequestHandler):

    server_base_url = ""

    def send_json(self, data: dict, status: int = 200):
        content = json.dumps(data, ensure_ascii=False).encode('utf-8')
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(content)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(content)

    def do_HEAD(self):
        # Respons uji koneksi dari tombol 'Tes Koneksi' Android
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Type", "text/plain")
        self.end_headers()

    def do_GET(self):
        clean_path = self.path.split("?")[0].strip("/")

        # 1. Halaman Web Dashboard Proktor
        if clean_path == "" or clean_path == "admin":
            if HTML_FILE.exists():
                with open(HTML_FILE, "rb") as f:
                    content = f.read()
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(content)))
                self.end_headers()
                self.wfile.write(content)
            else:
                self.send_error(404, "HTML template tidak ditemukan")
            return

        # 2. Endpoint Data Web API (Status, Konfigurasi, & Log)
        if clean_path == "api/data":
            config = load_or_init_config()
            self.send_json({
                "school_name": config.get("school_name", "MTS ROUDHOTUT THOWALIB"),
                "master_pin": config.get("master_pin", "888999"),
                "server_url": self.server_base_url,
                "exams": config.get("exams", []),
                "activity_logs": ACTIVITY_LOGS
            })
            return

        # 3. Endpoint manifest.json (untuk HP Siswa)
        if clean_path == "manifest.json":
            if not MANIFEST_FILE.exists():
                rebuild_manifest(self.server_base_url)

            with open(MANIFEST_FILE, "rb") as f:
                content = f.read()

            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(content)))
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(content)

            client_ip = self.client_address[0]
            add_log(client_ip, "Sinkronisasi Berhasil (Unduh manifest.json)")
            print(f"[SYNC] Siswa ({client_ip}) mengunduh manifest.json")
            return

        # 4. Endpoint berkas terenkripsi /files/<filename>.enc (untuk HP Siswa)
        if clean_path.startswith("files/"):
            filename = clean_path.replace("files/", "")
            file_path = FILES_DIR / filename
            if file_path.exists() and file_path.is_file():
                file_size = file_path.stat().st_size
                self.send_response(200)
                self.send_header("Content-Type", "application/octet-stream")
                self.send_header("Content-Length", str(file_size))
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                with open(file_path, "rb") as f:
                    while chunk := f.read(16384):
                        self.wfile.write(chunk)

                client_ip = self.client_address[0]
                add_log(client_ip, f"Unduh Paket Soal: {filename}")
                print(f"[DOWNLOAD] Siswa ({client_ip}) mengunduh: {filename}")
            else:
                self.send_error(404, f"File {filename} tidak ditemukan")
            return

        self.send_error(404, "Endpoint tidak ditemukan")

    def do_POST(self):
        clean_path = self.path.split("?")[0].strip("/")
        content_type = self.headers.get("Content-Type", "")
        content_length = int(self.headers.get("Content-Length", 0))

        # 1. Update Master PIN & Nama Madrasah
        if clean_path == "api/settings":
            raw_body = self.rfile.read(content_length)
            payload = json.loads(raw_body.decode('utf-8'))
            config = load_or_init_config()

            if "school_name" in payload:
                config["school_name"] = payload["school_name"].strip()
            if "master_pin" in payload:
                config["master_pin"] = payload["master_pin"].strip()

            save_config(config)
            rebuild_manifest(self.server_base_url)
            self.send_json({"ok": True})
            return

        # 2. Update Cepat Durasi Ujian (duration_minutes)
        if clean_path == "api/update-duration":
            raw_body = self.rfile.read(content_length)
            payload = json.loads(raw_body.decode('utf-8'))
            exam_id = payload.get("id")
            new_duration = int(payload.get("duration_minutes", 90))

            config = load_or_init_config()
            found = False
            for ex in config.get("exams", []):
                if ex["id"] == exam_id:
                    ex["duration_minutes"] = new_duration
                    found = True
                    break

            if found:
                save_config(config)
                rebuild_manifest(self.server_base_url)
                self.send_json({"ok": True, "message": f"Durasi berhasil diubah ke {new_duration} Menit"})
            else:
                self.send_json({"ok": False, "error": "Exam ID tidak ditemukan"}, 404)
            return

        # 3. Hapus Paket Soal
        if clean_path == "api/delete-exam":
            raw_body = self.rfile.read(content_length)
            payload = json.loads(raw_body.decode('utf-8'))
            exam_id = payload.get("id")

            config = load_or_init_config()
            config["exams"] = [ex for ex in config.get("exams", []) if ex["id"] != exam_id]
            save_config(config)

            # Hapus file .enc jika ada
            enc_file = FILES_DIR / f"{exam_id}.enc"
            if enc_file.exists():
                try:
                    enc_file.unlink()
                except Exception:
                    pass

            rebuild_manifest(self.server_base_url)
            self.send_json({"ok": True})
            return

        # 4. Enkripsi Ulang Seluruh Paket
        if clean_path == "api/rebuild":
            rebuild_manifest(self.server_base_url)
            self.send_json({"ok": True})
            return

        # 5. Upload & Enkripsi Paket Soal Baru (Multipart Form Data)
        if clean_path == "api/upload-exam":
            raw_data = self.rfile.read(content_length)
            msg = email.message_from_bytes(
                b"Content-Type: " + content_type.encode('utf-8') + b"\r\n\r\n" + raw_data,
                policy=email_default_policy
            )

            fields = {}
            pdf_bytes = b""
            pdf_filename = ""

            for part in msg.iter_parts():
                name = part.get_param('name', header='content-disposition')
                filename = part.get_param('filename', header='content-disposition')
                payload = part.get_payload(decode=True)

                if filename:
                    pdf_filename = filename
                    pdf_bytes = payload
                elif name:
                    fields[name] = payload.decode('utf-8', errors='ignore').strip()

            subject_name = fields.get("subject_name", "").strip()
            grade_level = int(fields.get("grade_level", 7))
            duration_minutes = int(fields.get("duration_minutes", 90))
            token = fields.get("token", "").strip().upper()

            if not subject_name or not token or not pdf_bytes:
                self.send_json({"ok": False, "error": "Semua kolom dan berkas PDF wajib diisi!"}, 400)
                return

            # Generate unique safe ID
            clean_sub = re.sub(r'[^A-Za-z0-9]', '', subject_name)[:6].upper()
            exam_id = f"{clean_sub}-{grade_level}-{int(time.time())}"
            safe_pdf_name = f"soal_{exam_id.lower()}.pdf"

            # Simpan file PDF asli ke raw_pdfs/
            RAW_PDFS_DIR.mkdir(parents=True, exist_ok=True)
            with open(RAW_PDFS_DIR / safe_pdf_name, "wb") as f:
                f.write(pdf_bytes)

            config = load_or_init_config()
            config["exams"].append({
                "id": exam_id,
                "subject_name": subject_name,
                "grade_level": grade_level,
                "duration_minutes": duration_minutes,
                "token": token,
                "pdf_filename": safe_pdf_name
            })
            save_config(config)

            # Langsung enkripsi dan perbarui manifest
            rebuild_manifest(self.server_base_url)
            self.send_json({"ok": True, "exam_id": exam_id})
            return

        self.send_error(404, "Endpoint POST tidak ditemukan")

    def log_message(self, format, *args):
        pass

def run_server(port: int = 8080):
    if sys.stdout.encoding != 'utf-8':
        try:
            sys.stdout.reconfigure(encoding='utf-8')
        except Exception:
            pass

    local_ip = get_local_ip()
    base_url = f"http://{local_ip}:{port}"
    ExamProctorWebHandler.server_base_url = base_url

    # Inisialisasi awal manifest
    rebuild_manifest(base_url)

    server_address = ('0.0.0.0', port)
    httpd = HTTPServer(server_address, ExamProctorWebHandler)

    print("\n" + "=" * 65)
    print("  SERVER PROKTOR & WEB DASHBOARD - MTS ROUDHOTUT THOWALIB")
    print("=" * 65)
    print(f"  • Buka di Browser Laptop Proktor : http://localhost:{port}")
    print(f"  • Alamat untuk HP Siswa (Wi-Fi)   : {base_url}")
    print("=" * 65)
    print("  Membuka browser otomatis...")

    # Buka web browser otomatis di laptop proktor
    try:
        webbrowser.open(f"http://localhost:{port}")
    except Exception:
        pass

    print("[READY] Server aktif! Tekan Ctrl+C untuk menghentikan server.\n")

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n[STOP] Server proktor dihentikan.")
        httpd.server_close()

def main():
    parser = argparse.ArgumentParser(description="Server Proktor Web Dashboard MTS ROUDHOTUT THOWALIB")
    parser.add_argument("--port", type=int, default=8080, help="Port server HTTP (default: 8080)")
    args = parser.parse_args()

    run_server(port=args.port)

if __name__ == "__main__":
    main()
