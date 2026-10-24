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
import threading
import time
import webbrowser
from datetime import datetime
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
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

# Thread lock untuk sinkronisasi baca/tulis konfigurasi
CONFIG_LOCK = threading.RLock()

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

def get_all_local_ips() -> list:
    """Mendeteksi semua alamat IP LAN IPv4 lokal yang aktif (Wi-Fi, Ethernet, Hotspot)."""
    ips = set()
    try:
        hostname = socket.gethostname()
        for item in socket.getaddrinfo(hostname, None, socket.AF_INET):
            ip = item[4][0]
            if not ip.startswith("127.") and not ip.startswith("169.254."):
                ips.add(ip)
    except Exception:
        pass

    for target in ['192.168.1.1', '10.255.255.255', '8.8.8.8']:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            s.connect((target, 80))
            ip = s.getsockname()[0]
            if not ip.startswith("127.") and not ip.startswith("169.254."):
                ips.add(ip)
        except Exception:
            pass
        finally:
            s.close()

    result = sorted(list(ips))
    if not result:
        result = ["127.0.0.1"]
    return result

def get_local_ip() -> str:
    """Mendeteksi IP LAN utama untuk server proktor."""
    ips = get_all_local_ips()
    for ip in ips:
        if not ip.startswith("127."):
            return ip
    return "127.0.0.1"

def load_or_init_config() -> dict:
    with CONFIG_LOCK:
        if CONFIG_FILE.exists():
            try:
                with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                    return json.load(f)
            except Exception as e:
                # Jangan langsung menimpa data jika hanya error baca sesaat
                time.sleep(0.05)
                try:
                    with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                        return json.load(f)
                except Exception:
                    print(f"[WARN] Gagal membaca konfigurasi: {e}")

        # Default fallback config jika file memang belum pernah ada
        if not CONFIG_FILE.exists():
            default_config = {
                "school_name": "MTS ROUDHOTUT THOWALIB",
                "school_tagline": "Madrasah Tsanawiyah Unggulan & Berkarakter",
                "exam_title": "Penilaian Akhir Semester (PAS) Ganjil",
                "master_pin": "888999",
                "exam_rules": [
                    "Siswa wajib memasuki ruang ujian 15 menit sebelum ujian dimulai.",
                    "Perangkat Android WAJIB dalam Mode Pesawat dan koneksi Wi-Fi/Bluetooth mati.",
                    "Aplikasi mengunci layar secara otomatis (Mode Kiosk). Dilarang keras keluar dari aplikasi.",
                    "Percobaan kecurangan (membuka aplikasi lain, reboot HP, atau swipe keluar) memicu PENALTI KUNCI LAYAR BERJENJANG.",
                    "Waktu ujian tetap berjalan normal di latar belakang selama masa penalti.",
                    "Soal ujian akan otomatis terkunci atau selesai ketika waktu jam ujian berakhir.",
                    "Jawaban wajib disalin ke Lembar Jawaban Komputer (LJK) fisik yang disediakan pengawas.",
                    "Jika ada kendala teknis atau baterai lemah, segera angkat tangan dan hubungi Pengawas Ujian.",
                    "Pengawas memiliki Master PIN darurat untuk membuka penalti ban jika diperlukan.",
                    "Berdoalah sebelum mengerjakan soal ujian. Kejujuran adalah cerminan akhlak mulia."
                ],
                "exams": []
            }
            save_config(default_config)
            return default_config

        return {}

def save_config(config: dict):
    with CONFIG_LOCK:
        tmp_file = CONFIG_FILE.with_suffix(".tmp")
        with open(tmp_file, "w", encoding="utf-8") as f:
            json.dump(config, f, indent=2, ensure_ascii=False)
        os.replace(tmp_file, CONFIG_FILE)

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
            config = load_or_init_config()
        except Exception:
            pass

    exams_manifest = []
    config_dirty = False

    for ex in config.get("exams", []):
        pdf_file = RAW_PDFS_DIR / ex.get("pdf_filename", "")
        enc_file = FILES_DIR / f"{ex['id']}.enc"

        # Optimasi: Gunakan enkripsi yang sudah ada jika .enc file dan metadatanya valid
        if enc_file.exists() and ex.get("file_sha256") and ex.get("pbkdf2_salt_hex") and ex.get("gcm_iv_hex"):
            crypto_meta = {
                "file_sha256": ex["file_sha256"],
                "salt_hex": ex["pbkdf2_salt_hex"],
                "iv_hex": ex["gcm_iv_hex"]
            }
        elif pdf_file.exists():
            crypto_meta = encrypt_pdf_exam(pdf_file, enc_file, ex["token"])
            ex["file_sha256"] = crypto_meta["file_sha256"]
            ex["pbkdf2_salt_hex"] = crypto_meta["salt_hex"]
            ex["gcm_iv_hex"] = crypto_meta["iv_hex"]
            config_dirty = True
        elif enc_file.exists():
            sha256_hash = hashlib.sha256(enc_file.read_bytes()).hexdigest()
            crypto_meta = {
                "file_sha256": ex.get("file_sha256", sha256_hash),
                "salt_hex": ex.get("pbkdf2_salt_hex", ""),
                "iv_hex": ex.get("gcm_iv_hex", "")
            }
        else:
            continue

        download_url = f"{server_base_url}/files/{ex['id']}.enc"

        short_name = ex.get("short_name", "").strip()
        if not short_name:
            short_name = ex["subject_name"][:4].upper()

        exam_date = ex.get("exam_date", "").strip()
        if not exam_date:
            exam_date = datetime.now().strftime("%Y-%m-%d")
            ex["exam_date"] = exam_date
            config_dirty = True

        exams_manifest.append({
            "id": ex["id"],
            "subject_name": ex["subject_name"],
            "short_name": short_name,
            "exam_date": exam_date,
            "exam_day": ex.get("exam_day", "Senin"),
            "start_time": ex.get("start_time", "07:00"),
            "end_time": ex.get("end_time", "08:30"),
            "grade_level": int(ex["grade_level"]),
            "duration_minutes": int(ex["duration_minutes"]),
            "download_url": download_url,
            "file_sha256": crypto_meta["file_sha256"],
            "pbkdf2_salt_hex": crypto_meta["salt_hex"],
            "gcm_iv_hex": crypto_meta["iv_hex"]
        })

    if config_dirty:
        save_config(config)

    master_pin = config.get("master_pin", "888999")
    master_pin_sha256 = hashlib.sha256(master_pin.strip().encode('utf-8')).hexdigest()

    default_rules = [
        "Siswa wajib memasuki ruang ujian 15 menit sebelum ujian dimulai.",
        "Perangkat Android WAJIB dalam Mode Pesawat dan koneksi Wi-Fi/Bluetooth mati.",
        "Aplikasi mengunci layar secara otomatis (Mode Kiosk). Dilarang keras keluar dari aplikasi.",
        "Percobaan kecurangan (membuka aplikasi lain, reboot HP, atau swipe keluar) memicu PENALTI KUNCI LAYAR BERJENJANG.",
        "Waktu ujian tetap berjalan normal di latar belakang selama masa penalti.",
        "Soal ujian akan otomatis terkunci atau selesai ketika waktu jam ujian berakhir.",
        "Jawaban wajib disalin ke Lembar Jawaban Komputer (LJK) fisik yang disediakan pengawas.",
        "Jika ada kendala teknis atau baterai lemah, segera angkat tangan dan hubungi Pengawas Ujian.",
        "Pengawas memiliki Master PIN darurat untuk membuka penalti ban jika diperlukan.",
        "Berdoalah sebelum mengerjakan soal ujian. Kejujuran adalah cerminan akhlak mulia."
    ]

    manifest = {
        "school_name": config.get("school_name", "MTS ROUDHOTUT THOWALIB"),
        "school_tagline": config.get("school_tagline", "Madrasah Tsanawiyah Unggulan & Berkarakter"),
        "exam_title": config.get("exam_title", "Penilaian Akhir Semester (PAS) Ganjil"),
        "exam_rules": config.get("exam_rules", default_rules),
        "sync_timestamp": int(time.time()),
        "master_pin_sha256": master_pin_sha256,
        "exams": exams_manifest
    }

    tmp_manifest = MANIFEST_FILE.with_suffix(".tmp")
    with open(tmp_manifest, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)
    os.replace(tmp_manifest, MANIFEST_FILE)

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

    def do_OPTIONS(self):
        # Pre-flight CORS request
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, HEAD, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_HEAD(self):
        # Respons uji koneksi dari tombol 'Tes Koneksi' Android
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", "0")
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
                self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
                self.send_header("Pragma", "no-cache")
                self.send_header("Expires", "0")
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
                "school_tagline": config.get("school_tagline", "Madrasah Tsanawiyah Unggulan & Berkarakter"),
                "exam_title": config.get("exam_title", "Penilaian Akhir Semester (PAS) Ganjil"),
                "exam_rules": config.get("exam_rules", []),
                "master_pin": config.get("master_pin", "888999"),
                "server_url": self.server_base_url,
                "all_ips": get_all_local_ips(),
                "exams": config.get("exams", []),
                "activity_logs": ACTIVITY_LOGS
            })
            return

        # 3. Endpoint manifest.json (untuk HP Siswa)
        if clean_path == "manifest.json":
            # Selalu bangun ulang manifest agar data tanggal & perubahan proktor selalu mutakhir
            manifest_data = rebuild_manifest(self.server_base_url)

            # Rewrite download_url secara dinamis sesuai Host yang diminta client
            req_host = self.headers.get("Host", "").strip()
            if req_host:
                dynamic_base = f"http://{req_host}"
                for ex in manifest_data.get("exams", []):
                    ex["download_url"] = f"{dynamic_base}/files/{ex['id']}.enc"

            content = json.dumps(manifest_data, ensure_ascii=False, indent=2).encode('utf-8')

            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(content)))
            self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(content)

            client_ip = self.client_address[0]
            add_log(client_ip, "Sinkronisasi Berhasil (Unduh manifest.json)")
            print(f"[SYNC] Siswa ({client_ip}) mengunduh manifest.json (total {len(manifest_data.get('exams', []))} paket soal)")
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

        # 1. Update Pengaturan Madrasah & Ujian
        if clean_path == "api/settings":
            try:
                raw_body = self.rfile.read(content_length)
                payload = json.loads(raw_body.decode('utf-8'))
                config = load_or_init_config()

                if "school_name" in payload and payload["school_name"].strip():
                    config["school_name"] = payload["school_name"].strip()
                if "school_tagline" in payload:
                    config["school_tagline"] = payload["school_tagline"].strip()
                if "exam_title" in payload and payload["exam_title"].strip():
                    config["exam_title"] = payload["exam_title"].strip()
                if "master_pin" in payload and payload["master_pin"].strip():
                    config["master_pin"] = payload["master_pin"].strip()
                if "exam_rules" in payload and isinstance(payload["exam_rules"], list):
                    config["exam_rules"] = [r.strip() for r in payload["exam_rules"] if r.strip()]

                save_config(config)
                rebuild_manifest(self.server_base_url)
                self.send_json({"ok": True})
            except Exception as e:
                self.send_json({"ok": False, "error": str(e)}, status=500)
            return

        # 2. Update Cepat Durasi / Jadwal Ujian
        if clean_path == "api/update-duration":
            try:
                raw_body = self.rfile.read(content_length)
                payload = json.loads(raw_body.decode('utf-8'))
                exam_id = payload.get("id")

                config = load_or_init_config()
                found = False
                for ex in config.get("exams", []):
                    if ex["id"] == exam_id:
                        if "duration_minutes" in payload:
                            ex["duration_minutes"] = int(payload["duration_minutes"])
                        if "exam_date" in payload:
                            ex["exam_date"] = payload["exam_date"].strip()
                        if "exam_day" in payload:
                            ex["exam_day"] = payload["exam_day"].strip()
                        if "start_time" in payload:
                            ex["start_time"] = payload["start_time"].strip()
                        if "end_time" in payload:
                            ex["end_time"] = payload["end_time"].strip()
                        if "short_name" in payload:
                            ex["short_name"] = payload["short_name"].strip()
                        found = True
                        break

                if found:
                    save_config(config)
                    rebuild_manifest(self.server_base_url)
                    self.send_json({"ok": True, "message": "Jadwal dan tanggal ujian berhasil diperbarui!"})
                else:
                    self.send_json({"ok": False, "error": "Exam ID tidak ditemukan"}, 404)
            except Exception as err:
                print(f"[ERROR] Gagal update duration: {err}")
                self.send_json({"ok": False, "error": str(err)}, 500)
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
            short_name = fields.get("short_name", "").strip()
            exam_date = fields.get("exam_date", "").strip()
            if not exam_date:
                exam_date = datetime.now().strftime("%Y-%m-%d")
            exam_day = fields.get("exam_day", "Senin").strip()
            start_time = fields.get("start_time", "07:00").strip()
            end_time = fields.get("end_time", "08:30").strip()
            grade_level = int(fields.get("grade_level", 7))
            duration_minutes = int(fields.get("duration_minutes", 90))
            token = fields.get("token", "").strip().upper()

            if not short_name:
                short_name = re.sub(r'[^A-Za-z0-9]', '', subject_name)[:4].upper()

            if not subject_name or not token or not pdf_bytes:
                self.send_json({"ok": False, "error": "Semua kolom dan berkas PDF wajib diisi!"}, 400)
                return

            # Validasi Magic Header berkas PDF
            if not pdf_bytes.startswith(b"%PDF-"):
                self.send_json({
                    "ok": False,
                    "error": "Berkas yang Anda unggah BUKAN dokumen PDF yang valid! (Format berkas tidak diawali %PDF-). Pastikan file tersebut adalah dokumen PDF asli, bukan halaman web yang tersimpan sebagai .pdf."
                }, 400)
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
                "short_name": short_name,
                "exam_date": exam_date,
                "exam_day": exam_day,
                "start_time": start_time,
                "end_time": end_time,
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

    all_ips = get_all_local_ips()
    primary_ip = get_local_ip()
    base_url = f"http://{primary_ip}:{port}"
    ExamProctorWebHandler.server_base_url = base_url

    # Inisialisasi awal manifest
    rebuild_manifest(base_url)

    server_address = ('0.0.0.0', port)
    httpd = ThreadingHTTPServer(server_address, ExamProctorWebHandler)
    httpd.daemon_threads = True

    print("\n" + "=" * 68)
    print("  SERVER PROKTOR & WEB DASHBOARD - MTS ROUDHOTUT THOWALIB")
    print("=" * 68)
    print(f"  • Buka di Browser Laptop Proktor : http://localhost:{port}")
    print("  • Alamat untuk diisi di HP Siswa:")
    for ip in all_ips:
        print(f"      -> http://{ip}:{port}  (atau cukup masukkan: {ip})")
    print("=" * 68)
    print("  Status: Multi-threaded Server Aktif (Mendukung banyak siswa)")
    print("=" * 68)
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
