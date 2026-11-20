#!/usr/bin/env python3
"""
Server Proktor Web Dashboard & Enkriptor Soal Ujian
MTS ROUDHOTUT THOWALIB
Antarmuka web modern untuk mengatur waktu ujian, token, master PIN, serta upload & enkripsi PDF.

Keamanan:
- Dashboard & seluruh Admin API dibatasi HANYA untuk localhost (127.0.0.1 / ::1) + token CSRF per-sesi.
- Proteksi ketat terhadap Path Traversal pada endpoint unduh/hapus file.
- Manifest ditandatangani secara digital menggunakan ECDSA P-256 (SHA-256).
- PIN Pengawas di-hash menggunakan PBKDF2-HMAC-SHA256 bersalt (210.000 iterasi).
- Enkripsi PDF menggunakan PBKDF2-HMAC-SHA256 (210.000 iterasi) + AES-256-GCM.
"""

import argparse
import base64
import email
from email.policy import default as email_default_policy
import hashlib
import hmac
import json
import os
import re
import secrets
import socket
import sys
import threading
import time
import webbrowser
from datetime import datetime
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from pdf_exporter import generate_token_pin_pdf, normalize_day_selection

try:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
except ImportError:
    print("[ERROR] Library 'cryptography' belum terpasang. Jalankan: pip install cryptography")
    sys.exit(1)

BASE_DIR = Path(__file__).parent
RAW_PDFS_DIR = BASE_DIR / "raw_pdfs"
FILES_DIR = BASE_DIR / "files"
CONFIG_FILE = BASE_DIR / "exams_config.json"
MANIFEST_FILE = BASE_DIR / "manifest.json"
SIGNING_KEY_FILE = BASE_DIR / "proctor_signing_key.pem"
HTML_FILE = BASE_DIR / "web_dashboard.html"
LOGO_FILE = BASE_DIR / "logo_sekolah.png"
APP_LOGO_FALLBACK = BASE_DIR.parent / "app" / "src" / "main" / "res" / "drawable" / "logo_sekolah.png"

# Hari efektif pelaksanaan ujian untuk rotasi PIN Pengawas Harian (Senin - Sabtu)
DAILY_PIN_KEYS = ("monday", "tuesday", "wednesday", "thursday", "friday", "saturday")

# Konstanta kriptografi & batas keamanan
PBKDF2_ITERATIONS = 210_000
MAX_JSON_BODY_BYTES = 1 * 1024 * 1024       # 1 MB untuk payload JSON API
MAX_UPLOAD_BODY_BYTES = 50 * 1024 * 1024    # 50 MB untuk upload PDF soal
SAFE_EXAM_ID_RE = re.compile(r"^[A-Za-z0-9_-]{1,80}$")
SAFE_ENC_FILENAME_RE = re.compile(r"^[A-Za-z0-9_-]{1,80}\.enc$")
PIN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
DEFAULT_INSECURE_PIN = "888999"

# Token CSRF acak per-boot untuk melindungi seluruh POST endpoint admin
PROCTOR_CSRF_TOKEN = secrets.token_urlsafe(32)

# Thread locks untuk sinkronisasi aman lintas thread
CONFIG_LOCK = threading.RLock()
LOGS_LOCK = threading.Lock()
PIN_HASH_LOCK = threading.Lock()
SIGNING_KEY_LOCK = threading.Lock()

# Cache hash PBKDF2 PIN di memori agar manifest.json cepat melayani banyak siswa serentak
PIN_HASH_CACHE = {}

# Cache kunci penandatangan ECDSA P-256
_SIGNING_PRIVATE_KEY = None
_SIGNING_PUBLIC_KEY_SPKI_B64 = ""

# Log aktivitas perangkat siswa di memori
ACTIVITY_LOGS = []


def generate_secure_code(length: int = 8, numeric_only: bool = False) -> str:
    alphabet = "0123456789" if numeric_only else PIN_ALPHABET
    return "".join(secrets.choice(alphabet) for _ in range(length))


def hash_pin_pbkdf2(pin: str) -> str:
    """
    Menghasilkan hash PIN format: pbkdf2_sha256$<iterations>$<salt_hex>$<derived_hex>
    menggunakan salt acak 16-byte dan 210.000 iterasi.
    """
    clean = pin.strip()
    if not clean:
        return ""
    with PIN_HASH_LOCK:
        cached = PIN_HASH_CACHE.get(clean)
        if cached:
            return cached
        salt = os.urandom(16)
        derived = hashlib.pbkdf2_hmac("sha256", clean.encode("utf-8"), salt, PBKDF2_ITERATIONS, 32)
        formatted = f"pbkdf2_sha256${PBKDF2_ITERATIONS}${salt.hex()}${derived.hex()}"
        PIN_HASH_CACHE[clean] = formatted
        return formatted


def load_or_create_signing_key():
    """
    Memuat atau membuat pasangan kunci ECDSA P-256 untuk menandatangani manifest.json.
    """
    global _SIGNING_PRIVATE_KEY, _SIGNING_PUBLIC_KEY_SPKI_B64
    with SIGNING_KEY_LOCK:
        if _SIGNING_PRIVATE_KEY is not None and _SIGNING_PUBLIC_KEY_SPKI_B64:
            return _SIGNING_PRIVATE_KEY, _SIGNING_PUBLIC_KEY_SPKI_B64

        if SIGNING_KEY_FILE.exists():
            pem_data = SIGNING_KEY_FILE.read_bytes()
            priv_key = serialization.load_pem_private_key(pem_data, password=None)
        else:
            priv_key = ec.generate_private_key(ec.SECP256R1())
            pem_bytes = priv_key.private_bytes(
                encoding=serialization.Encoding.PEM,
                format=serialization.PrivateFormat.PKCS8,
                encryption_algorithm=serialization.NoEncryption(),
            )
            tmp_key = SIGNING_KEY_FILE.with_suffix(".tmp")
            tmp_key.write_bytes(pem_bytes)
            try:
                os.chmod(tmp_key, 0o600)
            except Exception:
                pass
            os.replace(tmp_key, SIGNING_KEY_FILE)
            print(f"[SECURITY] Kunci penandatangan ECDSA P-256 baru dibuat: {SIGNING_KEY_FILE.name}")

        pub_der = priv_key.public_key().public_bytes(
            encoding=serialization.Encoding.DER,
            format=serialization.PublicFormat.SubjectPublicKeyInfo,
        )
        _SIGNING_PRIVATE_KEY = priv_key
        _SIGNING_PUBLIC_KEY_SPKI_B64 = base64.b64encode(pub_der).decode("ascii")
        return _SIGNING_PRIVATE_KEY, _SIGNING_PUBLIC_KEY_SPKI_B64


def sign_manifest_dict(manifest_data: dict) -> dict:
    """
    Menandatangani manifest JSON kanonis (sort_keys=True, compact, ASCII) menggunakan ECDSA P-256 SHA-256.
    """
    priv_key, pub_b64 = load_or_create_signing_key()
    unsigned = {
        k: v for k, v in manifest_data.items()
        if k not in ("manifest_signature", "manifest_public_key_spki")
    }
    canonical_bytes = json.dumps(
        unsigned,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=True,
    ).encode("utf-8")
    signature_der = priv_key.sign(canonical_bytes, ec.ECDSA(hashes.SHA256()))
    signed = dict(unsigned)
    signed["manifest_public_key_spki"] = pub_b64
    signed["manifest_signature"] = base64.b64encode(signature_der).decode("ascii")
    return signed


def add_log(ip: str, action: str):
    timestamp = datetime.now().strftime("%H:%M:%S")
    with LOGS_LOCK:
        ACTIVITY_LOGS.append({
            "time": timestamp,
            "ip": ip,
            "action": action
        })
        if len(ACTIVITY_LOGS) > 50:
            ACTIVITY_LOGS.pop(0)


def get_logs_snapshot() -> list:
    with LOGS_LOCK:
        return list(ACTIVITY_LOGS)


def is_localhost_ip(client_ip: str) -> bool:
    clean = (client_ip or "").strip().lower()
    return clean in ("127.0.0.1", "::1", "::ffff:127.0.0.1")


def resolve_safe_enc_file(filename: str):
    """
    Mencegah Path Traversal: hanya mengizinkan nama file .enc valid di dalam FILES_DIR.
    """
    clean = (filename or "").strip()
    if not SAFE_ENC_FILENAME_RE.match(clean):
        return None
    if Path(clean).name != clean:
        return None
    base_resolved = FILES_DIR.resolve()
    candidate = (base_resolved / clean).resolve()
    try:
        if not candidate.is_relative_to(base_resolved):
            return None
    except AttributeError:
        if str(base_resolved) not in [str(p) for p in candidate.parents]:
            return None
    return candidate


def resolve_safe_raw_pdf(filename: str):
    clean = (filename or "").strip()
    if not clean or Path(clean).name != clean or not clean.lower().endswith(".pdf"):
        return None
    base_resolved = RAW_PDFS_DIR.resolve()
    candidate = (base_resolved / clean).resolve()
    try:
        if not candidate.is_relative_to(base_resolved):
            return None
    except AttributeError:
        if str(base_resolved) not in [str(p) for p in candidate.parents]:
            return None
    return candidate


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


def normalize_reminder_minutes(value, default: int = 5) -> int:
    try:
        parsed = int(value)
        return max(0, min(120, parsed))
    except Exception:
        return default


def normalize_daily_pins(raw_pins) -> dict:
    src = raw_pins if isinstance(raw_pins, dict) else {}
    return {day: str(src.get(day, "") or "").strip() for day in DAILY_PIN_KEYS}


def ensure_non_default_master_pin(config: dict) -> bool:
    """
    Memastikan master_pin tidak kosong dan tidak menggunakan PIN default tidak aman ('888999').
    Mengembalikan True jika config diubah.
    """
    current = str(config.get("master_pin", "") or "").strip()
    if not current or current == DEFAULT_INSECURE_PIN:
        generated = generate_secure_code(length=8, numeric_only=False)
        config["master_pin"] = generated
        print(f"[SECURITY] PIN Darurat Permanen acak baru dibuat: {generated}")
        print("[SECURITY] Catat PIN ini di tempat aman atau ubah melalui Dashboard Proktor (localhost).")
        return True
    return False


def load_or_init_config() -> dict:
    with CONFIG_LOCK:
        if CONFIG_FILE.exists():
            for attempt in range(2):
                try:
                    with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                        data = json.load(f)
                        if isinstance(data, dict):
                            data["reminder_minutes_before"] = normalize_reminder_minutes(
                                data.get("reminder_minutes_before", 5)
                            )
                            data["daily_pins"] = normalize_daily_pins(data.get("daily_pins"))
                            if ensure_non_default_master_pin(data):
                                save_config(data)
                            return data
                except Exception as e:
                    if attempt == 0:
                        time.sleep(0.05)
                    else:
                        print(f"[WARN] Gagal membaca konfigurasi: {e}")

        if not CONFIG_FILE.exists():
            generated_pin = generate_secure_code(length=8, numeric_only=False)
            print(f"[SECURITY] Inisialisasi awal: PIN Darurat Permanen acak dibuat -> {generated_pin}")
            default_config = {
                "school_name": "MTS ROUDHOTUT THOWALIB",
                "school_tagline": "Madrasah Tsanawiyah Unggulan & Berkarakter",
                "school_npsn": "20278365",
                "school_nsm": "121232050172",
                "school_address": "Kp. Bojong Jaya, Des. Pasirkiamis, Kec. Pasirwangi, Kab. Garut, Jawa Barat.",
                "exam_title": "Penilaian Akhir Semester (PAS) Ganjil",
                "master_pin": generated_pin,
                "reminder_minutes_before": 5,
                "daily_pins": {day: "" for day in DAILY_PIN_KEYS},
                "exam_rules": [
                    "Siswa wajib memasuki ruang ujian 15 menit sebelum ujian dimulai.",
                    "Perangkat Android WAJIB dalam Mode Pesawat, Mode Hening, dan Wi-Fi/Bluetooth mati.",
                    "Aplikasi mengunci layar secara otomatis (Mode Kiosk). Dilarang keras mencoba keluar.",
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
    Mengenkripsi PDF menggunakan PBKDF2-HMAC-SHA256 (210.000 iterasi) + AES-GCM 256-bit.
    """
    with open(pdf_path, "rb") as f:
        pdf_data = f.read()

    salt_bytes = os.urandom(16)
    iv_bytes = os.urandom(12)

    key = hashlib.pbkdf2_hmac(
        'sha256',
        token.strip().encode('utf-8'),
        salt_bytes,
        PBKDF2_ITERATIONS,
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
        "pbkdf2_iterations": PBKDF2_ITERATIONS,
        "file_sha256": sha256_hash,
        "enc_size": len(ciphertext)
    }


def calculate_duration_from_times(start_time: str, end_time: str, fallback_duration: int = 60) -> int:
    try:
        if start_time and end_time and ":" in start_time and ":" in end_time:
            sh, sm = map(int, start_time.split(":")[:2])
            eh, em = map(int, end_time.split(":")[:2])
            diff = (eh * 60 + em) - (sh * 60 + sm)
            if diff > 0:
                return diff
    except Exception:
        pass
    return fallback_duration


def _parse_date_sort_key(date_str: str) -> tuple:
    raw = str(date_str or "").strip()
    if raw:
        parts = raw.split("-")
        if len(parts) == 3:
            try:
                y, m, d = int(parts[0]), int(parts[1]), int(parts[2])
                if 1 <= m <= 12 and 1 <= d <= 31:
                    return (y, m, d)
            except Exception:
                pass
    return (9999, 12, 31)


def _parse_time_sort_minutes(time_str: str) -> int:
    raw = str(time_str or "").strip().replace(".", ":")
    if raw and ":" in raw:
        parts = raw.split(":")
        try:
            h, m = int(parts[0]), int(parts[1])
            if 0 <= h <= 23 and 0 <= m <= 59:
                return h * 60 + m
        except Exception:
            pass
    return 9999


def exam_schedule_sort_key(ex: dict) -> tuple:
    """
    Kunci pengurutan kronologis jadwal ujian:
    Tanggal (terlama -> terbaru), Jam Mulai, Jam Selesai, Tingkat Kelas, Nama Mapel, ID.
    """
    try:
        grade = int(ex.get("grade_level", 99))
    except Exception:
        grade = 99
    return (
        _parse_date_sort_key(ex.get("exam_date", "")),
        _parse_time_sort_minutes(ex.get("start_time", "")),
        _parse_time_sort_minutes(ex.get("end_time", "")),
        grade,
        str(ex.get("subject_name", "") or "").strip().lower(),
        str(ex.get("id", "") or "").strip().lower(),
    )


def rebuild_manifest(server_base_url: str, force_reencrypt: bool = False):
    """
    Membangun ulang berkas .enc dan manifest.json bertanda tangan digital berdasarkan config terbaru.
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
        exam_id = str(ex.get("id", "")).strip()
        if not SAFE_EXAM_ID_RE.match(exam_id):
            continue

        pdf_file = resolve_safe_raw_pdf(ex.get("pdf_filename", ""))
        enc_file = resolve_safe_enc_file(f"{exam_id}.enc")
        if enc_file is None:
            continue

        current_iterations = int(ex.get("pbkdf2_iterations") or 0)
        enc_valid = (
            not force_reencrypt
            and enc_file.exists()
            and ex.get("file_sha256")
            and ex.get("pbkdf2_salt_hex")
            and ex.get("gcm_iv_hex")
            and current_iterations == PBKDF2_ITERATIONS
            and hashlib.sha256(enc_file.read_bytes()).hexdigest() == ex.get("file_sha256")
        )
        if enc_valid:
            crypto_meta = {
                "file_sha256": ex["file_sha256"],
                "salt_hex": ex["pbkdf2_salt_hex"],
                "iv_hex": ex["gcm_iv_hex"],
                "pbkdf2_iterations": current_iterations,
            }
        elif pdf_file is not None and pdf_file.exists():
            crypto_meta = encrypt_pdf_exam(pdf_file, enc_file, ex["token"])
            ex["file_sha256"] = crypto_meta["file_sha256"]
            ex["pbkdf2_salt_hex"] = crypto_meta["salt_hex"]
            ex["gcm_iv_hex"] = crypto_meta["iv_hex"]
            ex["pbkdf2_iterations"] = crypto_meta["pbkdf2_iterations"]
            config_dirty = True
        elif enc_file.exists():
            sha256_hash = hashlib.sha256(enc_file.read_bytes()).hexdigest()
            crypto_meta = {
                "file_sha256": ex.get("file_sha256", sha256_hash),
                "salt_hex": ex.get("pbkdf2_salt_hex", ""),
                "iv_hex": ex.get("gcm_iv_hex", ""),
                "pbkdf2_iterations": current_iterations if current_iterations > 0 else 10000,
            }
        else:
            continue

        download_url = f"{server_base_url}/files/{exam_id}.enc"

        short_name = ex.get("short_name", "").strip()
        if not short_name:
            short_name = ex["subject_name"][:4].upper()

        exam_date = ex.get("exam_date", "").strip()
        if not exam_date:
            exam_date = datetime.now().strftime("%Y-%m-%d")
            ex["exam_date"] = exam_date
            config_dirty = True

        start_time = ex.get("start_time", "07:00")
        end_time = ex.get("end_time", "08:30")
        auto_duration = calculate_duration_from_times(start_time, end_time, int(ex.get("duration_minutes", 60)))
        if ex.get("duration_minutes") != auto_duration:
            ex["duration_minutes"] = auto_duration
            config_dirty = True

        exams_manifest.append({
            "id": exam_id,
            "subject_name": ex["subject_name"],
            "short_name": short_name,
            "exam_date": exam_date,
            "exam_day": ex.get("exam_day", "Senin"),
            "start_time": start_time,
            "end_time": end_time,
            "grade_level": int(ex["grade_level"]),
            "duration_minutes": auto_duration,
            "download_url": download_url,
            "file_sha256": crypto_meta["file_sha256"],
            "pbkdf2_salt_hex": crypto_meta["salt_hex"],
            "pbkdf2_iterations": crypto_meta["pbkdf2_iterations"],
            "gcm_iv_hex": crypto_meta["iv_hex"],
        })

    exams_manifest.sort(key=exam_schedule_sort_key)

    if config_dirty:
        save_config(config)

    master_pin = str(config.get("master_pin", "")).strip()
    master_pin_hash = hash_pin_pbkdf2(master_pin)

    reminder_minutes = normalize_reminder_minutes(config.get("reminder_minutes_before", 5))
    daily_pins = normalize_daily_pins(config.get("daily_pins"))
    daily_pin_hashes = {
        day: hash_pin_pbkdf2(pin)
        for day, pin in daily_pins.items()
        if pin
    }

    default_rules = [
        "Siswa wajib memasuki ruang ujian 15 menit sebelum ujian dimulai.",
        "Perangkat Android WAJIB dalam Mode Pesawat, Mode Hening, dan Wi-Fi/Bluetooth mati.",
        "Aplikasi mengunci layar secara otomatis (Mode Kiosk). Dilarang keras mencoba keluar.",
        "Percobaan kecurangan (membuka aplikasi lain, reboot HP, atau swipe keluar) memicu PENALTI KUNCI LAYAR BERJENJANG.",
        "Waktu ujian tetap berjalan normal di latar belakang selama masa penalti.",
        "Soal ujian akan otomatis terkunci atau selesai ketika waktu jam ujian berakhir.",
        "Jawaban wajib disalin ke Lembar Jawaban Komputer (LJK) fisik yang disediakan pengawas.",
        "Jika ada kendala teknis atau baterai lemah, segera angkat tangan dan hubungi Pengawas Ujian.",
        "Pengawas memiliki Master PIN darurat untuk membuka penalti ban jika diperlukan.",
        "Berdoalah sebelum mengerjakan soal ujian. Kejujuran adalah cerminan akhlak mulia."
    ]

    manifest_unsigned = {
        "school_name": config.get("school_name", "MTS ROUDHOTUT THOWALIB"),
        "school_tagline": config.get("school_tagline", "Madrasah Tsanawiyah Unggulan & Berkarakter"),
        "school_npsn": config.get("school_npsn", "20278365"),
        "school_nsm": config.get("school_nsm", "121232050172"),
        "school_address": config.get("school_address", "Kp. Bojong Jaya, Des. Pasirkiamis, Kec. Pasirwangi, Kab. Garut, Jawa Barat."),
        "exam_title": config.get("exam_title", "Penilaian Akhir Semester (PAS) Ganjil"),
        "exam_rules": config.get("exam_rules", default_rules),
        "sync_timestamp": int(time.time()),
        "reminder_minutes_before": reminder_minutes,
        "master_pin_sha256": master_pin_hash,
        "daily_pin_hashes": daily_pin_hashes,
        "exams": exams_manifest,
    }

    manifest = sign_manifest_dict(manifest_unsigned)

    tmp_manifest = MANIFEST_FILE.with_suffix(".tmp")
    with open(tmp_manifest, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)
    os.replace(tmp_manifest, MANIFEST_FILE)

    return manifest


class ExamProctorWebHandler(BaseHTTPRequestHandler):

    server_base_url = ""
    server_port = 8080

    def _is_admin_client(self) -> bool:
        return is_localhost_ip(self.client_address[0])

    def _require_localhost_admin(self) -> bool:
        if self._is_admin_client():
            return True
        self.send_json(
            {
                "ok": False,
                "error": "Akses ditolak: Dashboard & API Proktor hanya dapat diakses langsung dari localhost laptop proktor."
            },
            status=403,
            allow_cors=False,
        )
        return False

    def _require_csrf_token(self) -> bool:
        # 1. Validasi Origin jika dikirim oleh browser
        origin = (self.headers.get("Origin") or "").strip()
        if origin:
            parsed_origin = urlparse(origin)
            allowed_hosts = {"localhost", "127.0.0.1", "[::1]"}
            if parsed_origin.scheme != "http" or (parsed_origin.hostname or "").lower() not in allowed_hosts:
                self.send_json({"ok": False, "error": "Origin permintaan tidak diizinkan."}, status=403, allow_cors=False)
                return False

        # 2. Validasi header X-Proctor-Token secara constant-time
        provided = (self.headers.get("X-Proctor-Token") or "").strip()
        if not provided or not hmac.compare_digest(provided, PROCTOR_CSRF_TOKEN):
            self.send_json(
                {"ok": False, "error": "Token keamanan sesi (CSRF) tidak valid. Muat ulang halaman Dashboard Proktor."},
                status=403,
                allow_cors=False,
            )
            return False
        return True

    def _read_body_limited(self, max_bytes: int):
        raw_len = (self.headers.get("Content-Length") or "").strip()
        try:
            content_length = int(raw_len) if raw_len else 0
        except ValueError:
            self.send_json({"ok": False, "error": "Header Content-Length tidak valid."}, status=400, allow_cors=False)
            return None

        if content_length < 0:
            self.send_json({"ok": False, "error": "Header Content-Length tidak valid."}, status=400, allow_cors=False)
            return None

        if content_length > max_bytes:
            self.send_json(
                {"ok": False, "error": f"Ukuran permintaan melebihi batas maksimum ({max_bytes // (1024 * 1024)} MB)."},
                status=413,
                allow_cors=False,
            )
            return None

        return self.rfile.read(content_length)

    def _resolve_trusted_host_base_url(self) -> str:
        """
        Memvalidasi header Host terhadap IP lokal/localhost yang valid sebelum digunakan
        untuk membentuk download_url (mencegah Host Header Injection).
        """
        req_host = (self.headers.get("Host") or "").strip()
        if not req_host:
            return self.server_base_url

        if ":" in req_host:
            host_part, port_part = req_host.rsplit(":", 1)
            if not port_part.isdigit() or int(port_part) != self.server_port:
                return self.server_base_url
        else:
            host_part = req_host

        allowed_hosts = set(get_all_local_ips()) | {"127.0.0.1", "localhost"}
        if host_part.lower() in allowed_hosts:
            return f"http://{host_part}:{self.server_port}"
        return self.server_base_url

    def send_json(self, data: dict, status: int = 200, allow_cors: bool = False):
        content = json.dumps(data, ensure_ascii=False).encode('utf-8')
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(content)))
        self.send_header("X-Eujian-Proktor", "MTS-RTH")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Cache-Control", "no-store")
        if allow_cors:
            self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(content)

    def do_OPTIONS(self):
        # Hanya izinkan preflight untuk endpoint baca siswa
        clean_path = urlparse(self.path).path.strip("/")
        if clean_path in ("api/ping", "manifest.json") or clean_path.startswith("files/"):
            self.send_response(200)
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
            self.send_header("Access-Control-Allow-Headers", "Content-Type")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        self.send_response(403)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_HEAD(self):
        clean_path = urlparse(self.path).path.strip("/")
        valid = clean_path in ("api/ping", "manifest.json", "logo_sekolah.png", "favicon.ico")
        if not valid and clean_path in ("", "admin", "api/data", "api/export-pdf"):
            valid = self._is_admin_client()
        elif not valid and clean_path.startswith("files/"):
            filename = clean_path.removeprefix("files/")
            safe_path = resolve_safe_enc_file(filename)
            valid = safe_path is not None and safe_path.is_file()

        self.send_response(200 if valid else 404)
        self.send_header("X-Eujian-Proktor", "MTS-RTH")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        clean_path = parsed.path.strip("/")
        query = parse_qs(parsed.query)

        # 0. Endpoint Ping / Healthcheck Resmi untuk HP Siswa ('Tes Koneksi')
        if clean_path == "api/ping":
            config = load_or_init_config()
            valid_exam_count = sum(
                1 for ex in config.get("exams", [])
                if (
                    (resolve_safe_enc_file(f"{ex.get('id', '')}.enc") or Path("__missing__")).exists()
                    or (resolve_safe_raw_pdf(ex.get("pdf_filename", "")) or Path("__missing__")).exists()
                )
            )
            device = (query.get("device", [""])[0] or "").strip()[:80]
            client_ip = self.client_address[0]
            if device:
                add_log(client_ip, f"Tes Koneksi Perangkat ({device})")
            self.send_json({
                "ok": True,
                "app": "e-ujian-mts-rth",
                "school_name": config.get("school_name", "MTS ROUDHOTUT THOWALIB"),
                "exam_title": config.get("exam_title", "Penilaian Akhir Semester (PAS) Ganjil"),
                "exam_count": valid_exam_count,
                "timestamp": int(time.time())
            }, allow_cors=True)
            return

        # 1. Halaman Web Dashboard Proktor (KHUSUS LOCALHOST)
        if clean_path == "" or clean_path == "admin":
            if not self._require_localhost_admin():
                return
            if HTML_FILE.exists():
                html_text = HTML_FILE.read_text(encoding="utf-8")
                html_text = html_text.replace("__PROCTOR_CSRF_TOKEN__", PROCTOR_CSRF_TOKEN)
                content = html_text.encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(content)))
                self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
                self.send_header("Pragma", "no-cache")
                self.send_header("Expires", "0")
                self.send_header("X-Frame-Options", "DENY")
                self.send_header("X-Content-Type-Options", "nosniff")
                self.send_header("Referrer-Policy", "no-referrer")
                self.end_headers()
                self.wfile.write(content)
            else:
                self.send_error(404, "HTML template tidak ditemukan")
            return

        # 1b. Logo Sekolah & Favicon
        if clean_path in ("logo_sekolah.png", "favicon.ico"):
            logo_path = LOGO_FILE if LOGO_FILE.exists() else APP_LOGO_FALLBACK
            if logo_path.exists():
                with open(logo_path, "rb") as f:
                    content = f.read()
                self.send_response(200)
                self.send_header("Content-Type", "image/png")
                self.send_header("Content-Length", str(len(content)))
                self.send_header("Cache-Control", "public, max-age=3600")
                self.send_header("X-Content-Type-Options", "nosniff")
                self.end_headers()
                self.wfile.write(content)
            else:
                self.send_error(404, "Logo sekolah tidak ditemukan")
            return

        # 2. Endpoint Data Web API (KHUSUS LOCALHOST)
        if clean_path == "api/data":
            if not self._require_localhost_admin():
                return
            config = load_or_init_config()
            self.send_json({
                "school_name": config.get("school_name", "MTS ROUDHOTUT THOWALIB"),
                "school_tagline": config.get("school_tagline", "Madrasah Tsanawiyah Unggulan & Berkarakter"),
                "school_npsn": config.get("school_npsn", "20278365"),
                "school_nsm": config.get("school_nsm", "121232050172"),
                "school_address": config.get("school_address", "Kp. Bojong Jaya, Des. Pasirkiamis, Kec. Pasirwangi, Kab. Garut, Jawa Barat."),
                "exam_title": config.get("exam_title", "Penilaian Akhir Semester (PAS) Ganjil"),
                "exam_rules": config.get("exam_rules", []),
                "master_pin": config.get("master_pin", ""),
                "reminder_minutes_before": normalize_reminder_minutes(config.get("reminder_minutes_before", 5)),
                "daily_pins": normalize_daily_pins(config.get("daily_pins")),
                "server_url": self.server_base_url,
                "all_ips": get_all_local_ips(),
                "exams": sorted(config.get("exams", []), key=exam_schedule_sort_key),
                "activity_logs": get_logs_snapshot()
            }, allow_cors=False)
            return

        # 2b. Endpoint Ekspor PDF Token & PIN Pengawas Harian (KHUSUS LOCALHOST)
        if clean_path == "api/export-pdf":
            if not self._require_localhost_admin():
                return
            try:
                config = load_or_init_config()
                day_param = (query.get("day", [""])[0] or "").strip()
                show_time_param = (query.get("show_time", ["1"])[0] or "1").strip().lower()
                all_exams_param = (query.get("all_exams", ["0"])[0] or "0").strip().lower()
                inline_param = (query.get("inline", ["0"])[0] or "0").strip().lower()

                show_time = show_time_param not in ("0", "false", "no")
                include_all_exams = all_exams_param in ("1", "true", "yes")
                is_inline = inline_param in ("1", "true", "yes")

                logo_path = LOGO_FILE if LOGO_FILE.exists() else APP_LOGO_FALLBACK
                pdf_bytes = generate_token_pin_pdf(
                    config=config,
                    selected_day=day_param,
                    logo_path=logo_path,
                    show_time=show_time,
                    include_all_exams=include_all_exams,
                )

                _day_key, day_label, is_all_days = normalize_day_selection(day_param)
                filename = "Token_PIN_Semua_Hari.pdf" if is_all_days else f"Token_PIN_Hari_{day_label}.pdf"
                disposition = "inline" if is_inline else "attachment"

                self.send_response(200)
                self.send_header("Content-Type", "application/pdf")
                self.send_header("Content-Disposition", f'{disposition}; filename="{filename}"')
                self.send_header("Content-Length", str(len(pdf_bytes)))
                self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
                self.send_header("X-Content-Type-Options", "nosniff")
                self.end_headers()
                self.wfile.write(pdf_bytes)
            except Exception as err:
                print(f"[ERROR] Gagal membuat PDF: {err}")
                self.send_json({"ok": False, "error": "Gagal membuat dokumen PDF di server."}, status=500)
            return

        # 3. Endpoint manifest.json bertanda tangan digital (untuk HP Siswa)
        if clean_path == "manifest.json":
            manifest_data = rebuild_manifest(self.server_base_url)

            # Rewrite download_url secara aman hanya untuk Host lokal yang terverifikasi,
            # lalu tandatangani ulang manifest agar signature cocok persis dengan respons yang dikirim.
            dynamic_base = self._resolve_trusted_host_base_url()
            for ex in manifest_data.get("exams", []):
                ex["download_url"] = f"{dynamic_base}/files/{ex['id']}.enc"
            signed_manifest = sign_manifest_dict(manifest_data)

            content = json.dumps(signed_manifest, ensure_ascii=False, indent=2).encode('utf-8')

            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(content)))
            self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
            self.send_header("X-Eujian-Proktor", "MTS-RTH")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(content)

            client_ip = self.client_address[0]
            grade_param = (query.get("grade", [""])[0] or "").strip()[:16]
            device_param = (query.get("device", [""])[0] or "").strip()[:80]
            meta_parts = []
            if grade_param:
                meta_parts.append(f"Kelas {grade_param}")
            if device_param:
                meta_parts.append(device_param)
            meta_suffix = f" ({' • '.join(meta_parts)})" if meta_parts else ""
            add_log(client_ip, f"Sinkronisasi Berhasil{meta_suffix}")
            print(f"[SYNC] Siswa ({client_ip}){meta_suffix} mengunduh manifest.json (total {len(signed_manifest.get('exams', []))} paket soal)")
            return

        # 4. Endpoint berkas terenkripsi /files/<filename>.enc (dilindungi anti Path Traversal)
        if clean_path.startswith("files/"):
            filename = clean_path.removeprefix("files/")
            file_path = resolve_safe_enc_file(filename)
            if file_path is not None and file_path.exists() and file_path.is_file():
                file_size = file_path.stat().st_size
                self.send_response(200)
                self.send_header("Content-Type", "application/octet-stream")
                self.send_header("Content-Length", str(file_size))
                self.send_header("X-Content-Type-Options", "nosniff")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                with open(file_path, "rb") as f:
                    while chunk := f.read(16384):
                        self.wfile.write(chunk)

                client_ip = self.client_address[0]
                add_log(client_ip, f"Unduh Paket Soal: {file_path.name}")
                print(f"[DOWNLOAD] Siswa ({client_ip}) mengunduh: {file_path.name}")
            else:
                self.send_error(404, "Berkas soal tidak ditemukan")
            return

        self.send_error(404, "Endpoint tidak ditemukan")

    def do_POST(self):
        # Seluruh operasi tulis/admin WAJIB berasal dari localhost + menyertakan CSRF token valid
        if not self._require_localhost_admin():
            return
        if not self._require_csrf_token():
            return

        clean_path = urlparse(self.path).path.strip("/")
        content_type = self.headers.get("Content-Type", "")

        # 1. Update Pengaturan Madrasah & Ujian
        if clean_path == "api/settings":
            raw_body = self._read_body_limited(MAX_JSON_BODY_BYTES)
            if raw_body is None:
                return
            try:
                payload = json.loads(raw_body.decode('utf-8'))
                if not isinstance(payload, dict):
                    self.send_json({"ok": False, "error": "Payload JSON tidak valid."}, status=400)
                    return

                config = load_or_init_config()

                if "school_name" in payload and str(payload["school_name"]).strip():
                    config["school_name"] = str(payload["school_name"]).strip()
                if "school_tagline" in payload:
                    config["school_tagline"] = str(payload["school_tagline"]).strip()
                if "school_npsn" in payload:
                    config["school_npsn"] = str(payload["school_npsn"]).strip()
                if "school_nsm" in payload:
                    config["school_nsm"] = str(payload["school_nsm"]).strip()
                if "school_address" in payload:
                    config["school_address"] = str(payload["school_address"]).strip()
                if "exam_title" in payload and str(payload["exam_title"]).strip():
                    config["exam_title"] = str(payload["exam_title"]).strip()
                if "master_pin" in payload:
                    new_master_pin = str(payload["master_pin"]).strip()
                    if len(new_master_pin) < 6 or new_master_pin == DEFAULT_INSECURE_PIN:
                        self.send_json(
                            {
                                "ok": False,
                                "error": "PIN Darurat Permanen minimal 6 karakter dan tidak boleh menggunakan PIN default '888999'."
                            },
                            status=400,
                        )
                        return
                    config["master_pin"] = new_master_pin
                if "reminder_minutes_before" in payload:
                    config["reminder_minutes_before"] = normalize_reminder_minutes(payload["reminder_minutes_before"])
                if "daily_pins" in payload and isinstance(payload["daily_pins"], dict):
                    normalized_daily = normalize_daily_pins(payload["daily_pins"])
                    for day_k, pin_v in normalized_daily.items():
                        if pin_v and len(pin_v) < 6:
                            self.send_json(
                                {"ok": False, "error": f"PIN Harian ({day_k}) minimal 6 karakter."},
                                status=400,
                            )
                            return
                    config["daily_pins"] = normalized_daily
                if "exam_rules" in payload and isinstance(payload["exam_rules"], list):
                    config["exam_rules"] = [str(r).strip() for r in payload["exam_rules"] if str(r).strip()]

                save_config(config)
                rebuild_manifest(self.server_base_url)
                self.send_json({"ok": True})
            except json.JSONDecodeError:
                self.send_json({"ok": False, "error": "Format JSON tidak valid."}, status=400)
            except Exception as e:
                print(f"[ERROR] Gagal menyimpan pengaturan: {e}")
                self.send_json({"ok": False, "error": "Terjadi kesalahan internal saat menyimpan pengaturan."}, status=500)
            return

        # 2. Update Cepat Durasi / Jadwal Ujian
        if clean_path == "api/update-duration":
            raw_body = self._read_body_limited(MAX_JSON_BODY_BYTES)
            if raw_body is None:
                return
            try:
                payload = json.loads(raw_body.decode('utf-8'))
                exam_id = str(payload.get("id", "")).strip()
                if not SAFE_EXAM_ID_RE.match(exam_id):
                    self.send_json({"ok": False, "error": "Format Exam ID tidak valid."}, status=400)
                    return

                config = load_or_init_config()
                target_exam = None
                for ex in config.get("exams", []):
                    if ex.get("id") == exam_id:
                        target_exam = ex
                        break

                if target_exam is not None:
                    next_start = str(payload.get("start_time", target_exam.get("start_time", ""))).strip()
                    next_end = str(payload.get("end_time", target_exam.get("end_time", ""))).strip()
                    if ":" in next_start and ":" in next_end:
                        sh, sm = map(int, next_start.split(":")[:2])
                        eh, em = map(int, next_end.split(":")[:2])
                        if (eh * 60 + em) <= (sh * 60 + sm):
                            self.send_json({"ok": False, "error": "Jam Selesai harus lebih besar dari Jam Mulai!"}, 400)
                            return

                    if "exam_date" in payload:
                        target_exam["exam_date"] = str(payload["exam_date"]).strip()
                    if "exam_day" in payload:
                        target_exam["exam_day"] = str(payload["exam_day"]).strip()
                    if "start_time" in payload:
                        target_exam["start_time"] = next_start
                    if "end_time" in payload:
                        target_exam["end_time"] = next_end
                    if "short_name" in payload:
                        target_exam["short_name"] = str(payload["short_name"]).strip()
                    target_exam["duration_minutes"] = calculate_duration_from_times(
                        target_exam.get("start_time", ""),
                        target_exam.get("end_time", ""),
                        int(payload.get("duration_minutes", target_exam.get("duration_minutes", 60)))
                    )

                    save_config(config)
                    rebuild_manifest(self.server_base_url)
                    self.send_json({"ok": True, "message": "Jadwal dan tanggal ujian berhasil diperbarui!"})
                else:
                    self.send_json({"ok": False, "error": "Exam ID tidak ditemukan"}, 404)
            except json.JSONDecodeError:
                self.send_json({"ok": False, "error": "Format JSON tidak valid."}, status=400)
            except Exception as err:
                print(f"[ERROR] Gagal update duration: {err}")
                self.send_json({"ok": False, "error": "Gagal memperbarui jadwal ujian."}, status=500)
            return

        # 3. Hapus Paket Soal (dilindungi anti Path Traversal)
        if clean_path == "api/delete-exam":
            raw_body = self._read_body_limited(MAX_JSON_BODY_BYTES)
            if raw_body is None:
                return
            try:
                payload = json.loads(raw_body.decode('utf-8'))
                exam_id = str(payload.get("id", "")).strip()
                if not SAFE_EXAM_ID_RE.match(exam_id):
                    self.send_json({"ok": False, "error": "Format Exam ID tidak valid."}, status=400)
                    return

                config = load_or_init_config()
                deleted_exam = next((ex for ex in config.get("exams", []) if ex.get("id") == exam_id), None)
                config["exams"] = [ex for ex in config.get("exams", []) if ex.get("id") != exam_id]
                save_config(config)

                enc_file = resolve_safe_enc_file(f"{exam_id}.enc")
                if enc_file is not None and enc_file.exists():
                    try:
                        enc_file.unlink()
                    except Exception:
                        pass

                if deleted_exam:
                    raw_pdf = resolve_safe_raw_pdf(deleted_exam.get("pdf_filename", ""))
                    if raw_pdf is not None and raw_pdf.exists():
                        try:
                            raw_pdf.unlink()
                        except Exception:
                            pass

                rebuild_manifest(self.server_base_url)
                self.send_json({"ok": True})
            except json.JSONDecodeError:
                self.send_json({"ok": False, "error": "Format JSON tidak valid."}, status=400)
            except Exception as err:
                print(f"[ERROR] Gagal menghapus paket soal: {err}")
                self.send_json({"ok": False, "error": "Gagal menghapus paket soal."}, status=500)
            return

        # 4. Enkripsi Ulang Seluruh Paket
        if clean_path == "api/rebuild":
            try:
                rebuild_manifest(self.server_base_url, force_reencrypt=True)
                self.send_json({"ok": True})
            except Exception as err:
                print(f"[ERROR] Gagal enkripsi ulang: {err}")
                self.send_json({"ok": False, "error": "Gagal mengenkripsi ulang paket soal."}, status=500)
            return

        # 5. Upload & Enkripsi Paket Soal Baru (Multipart Form Data dengan batas ukuran)
        if clean_path == "api/upload-exam":
            raw_data = self._read_body_limited(MAX_UPLOAD_BODY_BYTES)
            if raw_data is None:
                return
            try:
                msg = email.message_from_bytes(
                    b"Content-Type: " + content_type.encode('utf-8') + b"\r\n\r\n" + raw_data,
                    policy=email_default_policy
                )

                fields = {}
                pdf_bytes = b""

                for part in msg.iter_parts():
                    name = part.get_param('name', header='content-disposition')
                    filename = part.get_param('filename', header='content-disposition')
                    payload = part.get_payload(decode=True) or b""

                    if filename:
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
                duration_minutes = calculate_duration_from_times(
                    start_time, end_time, int(fields.get("duration_minutes", 60))
                )
                token = fields.get("token", "").strip().upper()

                if not short_name:
                    short_name = re.sub(r'[^A-Za-z0-9]', '', subject_name)[:4].upper()

                if not subject_name or not token or not pdf_bytes:
                    self.send_json({"ok": False, "error": "Semua kolom dan berkas PDF wajib diisi!"}, 400)
                    return

                if len(token) < 6:
                    self.send_json(
                        {"ok": False, "error": "Token soal minimal 6 karakter (disarankan 8 karakter acak) demi keamanan enkripsi!"},
                        400
                    )
                    return

                if ":" in start_time and ":" in end_time:
                    sh, sm = map(int, start_time.split(":")[:2])
                    eh, em = map(int, end_time.split(":")[:2])
                    if (eh * 60 + em) <= (sh * 60 + sm):
                        self.send_json({"ok": False, "error": "Jam Selesai harus lebih besar dari Jam Mulai!"}, 400)
                        return

                if not pdf_bytes.startswith(b"%PDF-"):
                    self.send_json({
                        "ok": False,
                        "error": "Berkas yang Anda unggah BUKAN dokumen PDF yang valid! (Format berkas tidak diawali %PDF-)."
                    }, 400)
                    return

                clean_sub = re.sub(r'[^A-Za-z0-9]', '', subject_name)[:6].upper() or "UJIAN"
                exam_id = f"{clean_sub}-{grade_level}-{int(time.time())}"
                safe_pdf_name = f"soal_{exam_id.lower()}.pdf"
                safe_pdf_path = resolve_safe_raw_pdf(safe_pdf_name)
                if safe_pdf_path is None:
                    self.send_json({"ok": False, "error": "Nama berkas tidak valid."}, 400)
                    return

                RAW_PDFS_DIR.mkdir(parents=True, exist_ok=True)
                with open(safe_pdf_path, "wb") as f:
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

                rebuild_manifest(self.server_base_url)
                self.send_json({"ok": True, "exam_id": exam_id})
            except Exception as err:
                print(f"[ERROR] Gagal mengunggah paket soal: {err}")
                self.send_json({"ok": False, "error": "Gagal memproses unggahan berkas PDF."}, status=500)
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
    ExamProctorWebHandler.server_port = port

    # Inisialisasi kunci ECDSA & manifest awal bertanda tangan digital
    load_or_create_signing_key()
    rebuild_manifest(base_url)

    server_address = ('0.0.0.0', port)
    httpd = ThreadingHTTPServer(server_address, ExamProctorWebHandler)
    httpd.daemon_threads = True

    print("\n" + "=" * 72)
    print("  SERVER PROKTOR & WEB DASHBOARD - MTS ROUDHOTUT THOWALIB (HARDENED)")
    print("=" * 72)
    print(f"  • Dashboard Proktor (KHUSUS LOCALHOST) : http://localhost:{port}")
    print("  • Alamat Sinkronisasi untuk HP Siswa   :")
    for ip in all_ips:
        print(f"      -> http://{ip}:{port}  (atau cukup masukkan: {ip})")
    print("-" * 72)
    print("  • Keamanan Aktif:")
    print("    - Admin Dashboard & API diblokir dari jaringan LAN (Localhost-only + CSRF)")
    print("    - Manifest ditandatangani digital (ECDSA P-256) + Key Pinning di APK")
    print(f"    - Enkripsi PDF & Hash PIN: PBKDF2-HMAC-SHA256 ({PBKDF2_ITERATIONS:,} iterasi)")
    print("=" * 72)
    print("  Membuka browser otomatis...")

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
