#!/usr/bin/env python3
"""
Generator PDF Resmi Token & PIN Pengawas Harian (Pure Python, Tanpa Dependensi Eksternal)
Menghasilkan dokumen PDF 1.4 A4 siap unduh & bagikan ke grup guru.
"""

import struct
import zlib
from datetime import datetime
from pathlib import Path

PAGE_W = 595.0
PAGE_H = 842.0
MARGIN_X = 40.0
CONTENT_W = PAGE_W - (MARGIN_X * 2)

DAY_LABEL_TO_KEY = {
    "senin": "monday",
    "selasa": "tuesday",
    "rabu": "wednesday",
    "kamis": "thursday",
    "jumat": "friday",
    "sabtu": "saturday",
    "minggu": "sunday",
}

DAY_KEY_TO_LABEL = {
    "monday": "Senin",
    "tuesday": "Selasa",
    "wednesday": "Rabu",
    "thursday": "Kamis",
    "friday": "Jumat",
    "saturday": "Sabtu",
    "sunday": "Minggu",
}


def _paeth(a: int, b: int, c: int) -> int:
    p = a + b - c
    pa = abs(p - a)
    pb = abs(p - b)
    pc = abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c


def load_png_as_pdf_rgb(png_path: Path, bg_rgb=(255, 255, 255)):
    """
    Mendekode PNG (8-bit RGB/RGBA/Gray/Gray+Alpha, non-interlaced) menjadi
    stream RGB terkompresi FlateDecode untuk Image XObject PDF, mengompositkan
    alpha channel ke bg_rgb agar tampil sempurna di semua PDF viewer HP/Desktop.
    Mengembalikan (width, height, compressed_rgb_bytes) atau None jika gagal.
    """
    try:
        if not png_path or not png_path.exists():
            return None
        data = png_path.read_bytes()
        if data[:8] != b"\x89PNG\r\n\x1a\n":
            return None

        pos = 8
        width = height = bit_depth = color_type = interlace = 0
        idat_chunks = []

        while pos + 8 <= len(data):
            length = struct.unpack(">I", data[pos:pos + 4])[0]
            chunk_type = data[pos + 4:pos + 8]
            chunk_data = data[pos + 8:pos + 8 + length]
            pos += 12 + length
            if chunk_type == b"IHDR":
                width, height, bit_depth, color_type, _comp, _filt, interlace = struct.unpack(">IIBBBBB", chunk_data)
            elif chunk_type == b"IDAT":
                idat_chunks.append(chunk_data)
            elif chunk_type == b"IEND":
                break

        if width <= 0 or height <= 0 or bit_depth != 8 or interlace != 0:
            return None
        if color_type not in (0, 2, 4, 6):
            return None

        bpp_map = {0: 1, 2: 3, 4: 2, 6: 4}
        bpp = bpp_map[color_type]
        raw = zlib.decompress(b"".join(idat_chunks))
        stride = width * bpp
        if len(raw) < height * (stride + 1):
            return None

        # Unfilter scanlines
        prev = bytearray(stride)
        bg_r, bg_g, bg_b = bg_rgb
        rgb_out = bytearray(width * height * 3)
        out_idx = 0

        for y in range(height):
            row_start = y * (stride + 1)
            ftype = raw[row_start]
            scan = bytearray(raw[row_start + 1:row_start + 1 + stride])

            if ftype == 1:
                for i in range(bpp, stride):
                    scan[i] = (scan[i] + scan[i - bpp]) & 0xFF
            elif ftype == 2:
                for i in range(stride):
                    scan[i] = (scan[i] + prev[i]) & 0xFF
            elif ftype == 3:
                for i in range(stride):
                    left = scan[i - bpp] if i >= bpp else 0
                    scan[i] = (scan[i] + ((left + prev[i]) >> 1)) & 0xFF
            elif ftype == 4:
                for i in range(stride):
                    left = scan[i - bpp] if i >= bpp else 0
                    up = prev[i]
                    up_left = prev[i - bpp] if i >= bpp else 0
                    scan[i] = (scan[i] + _paeth(left, up, up_left)) & 0xFF

            prev = scan

            if color_type == 6:  # RGBA
                for x in range(0, stride, 4):
                    r, g, b, a = scan[x], scan[x + 1], scan[x + 2], scan[x + 3]
                    if a == 255:
                        rgb_out[out_idx] = r
                        rgb_out[out_idx + 1] = g
                        rgb_out[out_idx + 2] = b
                    elif a == 0:
                        rgb_out[out_idx] = bg_r
                        rgb_out[out_idx + 1] = bg_g
                        rgb_out[out_idx + 2] = bg_b
                    else:
                        inv = 255 - a
                        rgb_out[out_idx] = (r * a + bg_r * inv) // 255
                        rgb_out[out_idx + 1] = (g * a + bg_g * inv) // 255
                        rgb_out[out_idx + 2] = (b * a + bg_b * inv) // 255
                    out_idx += 3
            elif color_type == 2:  # RGB
                rgb_out[out_idx:out_idx + stride] = scan
                out_idx += stride
            elif color_type == 0:  # Grayscale
                for g in scan:
                    rgb_out[out_idx] = g
                    rgb_out[out_idx + 1] = g
                    rgb_out[out_idx + 2] = g
                    out_idx += 3
            elif color_type == 4:  # Gray+Alpha
                for x in range(0, stride, 2):
                    g, a = scan[x], scan[x + 1]
                    inv = 255 - a
                    rgb_out[out_idx] = (g * a + bg_r * inv) // 255
                    rgb_out[out_idx + 1] = (g * a + bg_g * inv) // 255
                    rgb_out[out_idx + 2] = (g * a + bg_b * inv) // 255
                    out_idx += 3

        compressed = zlib.compress(bytes(rgb_out), level=6)
        return (width, height, compressed)
    except Exception:
        return None


def _pdf_escape(text: str) -> bytes:
    """Mengubah string ke cp1252 (WinAnsiEncoding) dan escape karakter literal PDF."""
    if text is None:
        text = ""
    raw = str(text).encode("cp1252", errors="replace")
    out = bytearray()
    for b in raw:
        if b in (40, 41, 92):  # '(', ')', '\'
            out.append(92)
            out.append(b)
        else:
            out.append(b)
    return bytes(out)


def _approx_text_width(text: str, font_size: float, is_bold: bool = False, is_mono: bool = False) -> float:
    if not text:
        return 0.0
    if is_mono:
        return len(text) * font_size * 0.60
    factor = 0.54 if is_bold else 0.50
    total = 0.0
    for ch in text:
        if ch in "ilI.,:;|!' ":
            total += font_size * 0.28
        elif ch in "MWmw@%—":
            total += font_size * 0.82
        elif ch.isupper() or ch.isdigit():
            total += font_size * (factor + 0.07)
        else:
            total += font_size * factor
    return total


def _truncate_text(text: str, max_width: float, font_size: float, is_bold: bool = False) -> str:
    if _approx_text_width(text, font_size, is_bold) <= max_width:
        return text
    s = text
    while len(s) > 3 and _approx_text_width(s + "...", font_size, is_bold) > max_width:
        s = s[:-1]
    return s.rstrip() + "..."


def _wrap_text(text: str, max_width: float, font_size: float, is_bold: bool = False) -> list:
    words = (text or "").split()
    if not words:
        return [""]
    lines = []
    cur = words[0]
    for w in words[1:]:
        cand = cur + " " + w
        if _approx_text_width(cand, font_size, is_bold) <= max_width:
            cur = cand
        else:
            lines.append(cur)
            cur = w
    lines.append(cur)
    return lines


class _PdfCanvas:
    def __init__(self):
        self.cmds = []

    def set_fill_rgb(self, r: int, g: int, b: int):
        self.cmds.append(f"{r/255:.3f} {g/255:.3f} {b/255:.3f} rg".encode("ascii"))

    def set_stroke_rgb(self, r: int, g: int, b: int):
        self.cmds.append(f"{r/255:.3f} {g/255:.3f} {b/255:.3f} RG".encode("ascii"))

    def set_line_width(self, w: float):
        self.cmds.append(f"{w:.2f} w".encode("ascii"))

    def rect(self, x: float, y: float, w: float, h: float, fill: bool = True, stroke: bool = False):
        op = "B" if (fill and stroke) else ("f" if fill else "S")
        self.cmds.append(f"{x:.2f} {y:.2f} {w:.2f} {h:.2f} re {op}".encode("ascii"))

    def rounded_rect(self, x: float, y: float, w: float, h: float, r: float = 6.0, fill: bool = True, stroke: bool = False):
        r = min(r, w / 2.0, h / 2.0)
        k = 0.55228475 * r
        op = "B" if (fill and stroke) else ("f" if fill else "S")
        parts = [
            f"{x + r:.2f} {y:.2f} m",
            f"{x + w - r:.2f} {y:.2f} l",
            f"{x + w - r + k:.2f} {y:.2f} {x + w:.2f} {y + r - k:.2f} {x + w:.2f} {y + r:.2f} c",
            f"{x + w:.2f} {y + h - r:.2f} l",
            f"{x + w:.2f} {y + h - r + k:.2f} {x + w - r + k:.2f} {y + h:.2f} {x + w - r:.2f} {y + h:.2f} c",
            f"{x + r:.2f} {y + h:.2f} l",
            f"{x + r - k:.2f} {y + h:.2f} {x:.2f} {y + h - r + k:.2f} {x:.2f} {y + h - r:.2f} c",
            f"{x:.2f} {y + r:.2f} l",
            f"{x:.2f} {y + r - k:.2f} {x + r - k:.2f} {y:.2f} {x + r:.2f} {y:.2f} c",
            op,
        ]
        self.cmds.append(" ".join(parts).encode("ascii"))

    def line(self, x1: float, y1: float, x2: float, y2: float):
        self.cmds.append(f"{x1:.2f} {y1:.2f} m {x2:.2f} {y2:.2f} l S".encode("ascii"))

    def text(self, x: float, y: float, txt: str, font: str = "F1", size: float = 11.0, rgb=(15, 23, 42)):
        self.set_fill_rgb(*rgb)
        esc = _pdf_escape(txt)
        self.cmds.append(
            b"BT /" + font.encode("ascii") + f" {size:.1f} Tf 1 0 0 1 {x:.2f} {y:.2f} Tm (".encode("ascii") + esc + b") Tj ET"
        )

    def image(self, name: str, x: float, y: float, w: float, h: float):
        self.cmds.append(f"q {w:.2f} 0 0 {h:.2f} {x:.2f} {y:.2f} cm /{name} Do Q".encode("ascii"))

    def build_stream(self) -> bytes:
        return b"\n".join(self.cmds) + b"\n"


def normalize_day_selection(day_param: str):
    """
    Mengembalikan (day_key, day_label_id, is_all_days).
    day_param bisa berupa 'Sabtu', 'saturday', 'all', dll.
    """
    raw = (day_param or "").strip().lower()
    if raw in ("all", "semua"):
        return ("all", "Semua Hari", True)
    if raw in DAY_LABEL_TO_KEY:
        key = DAY_LABEL_TO_KEY[raw]
        return (key, DAY_KEY_TO_LABEL[key], False)
    if raw in DAY_KEY_TO_LABEL:
        return (raw, DAY_KEY_TO_LABEL[raw], False)

    # Fallback ke hari ini
    idx = datetime.now().weekday()  # 0=Mon .. 6=Sun
    keys = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]
    key = keys[idx]
    return (key, DAY_KEY_TO_LABEL[key], False)


def generate_token_pin_pdf(
    config: dict,
    selected_day: str = "",
    logo_path: Path = None,
    show_time: bool = True,
    include_all_exams: bool = False,
) -> bytes:
    """
    Menghasilkan bytes PDF A4 berisi PIN Pengawas dan daftar Token & Mapel per kelas
    untuk dibagikan ke grup guru.
    """
    day_key, day_label, is_all_days = normalize_day_selection(selected_day)
    school_name = (config.get("school_name") or "MTS ROUDHOTUT THOWALIB").strip()
    exam_title = (config.get("exam_title") or "Penilaian Akhir Semester").strip()
    daily_pins = config.get("daily_pins") if isinstance(config.get("daily_pins"), dict) else {}
    exams = config.get("exams") or []

    # Filter jadwal ujian berdasarkan hari (kecuali all / include_all_exams)
    if is_all_days or include_all_exams:
        filtered_exams = list(exams)
    else:
        filtered_exams = [
            ex for ex in exams
            if str(ex.get("exam_day", "")).strip().lower() == day_label.lower()
        ]

    # Kelompokkan per kelas (7, 8, 9, dst.)
    grouped = {}
    for ex in filtered_exams:
        try:
            grade = int(ex.get("grade_level", 7))
        except Exception:
            grade = 7
        grouped.setdefault(grade, []).append(ex)

    for grade in grouped:
        grouped[grade].sort(
            key=lambda e: (str(e.get("exam_date", "")), str(e.get("start_time", "00:00")), str(e.get("subject_name", "")))
        )

    sorted_grades = sorted(grouped.keys())

    # Decode logo sekolah jika ada (dikompositkan ke putih untuk badge logo)
    logo_img = load_png_as_pdf_rgb(logo_path, bg_rgb=(255, 255, 255)) if logo_path else None

    pages = []

    def new_page(is_continuation: bool = False) -> tuple:
        c = _PdfCanvas()
        # Latar belakang header hijau zamrud gelap
        header_h = 84.0 if not is_continuation else 54.0
        header_y = PAGE_H - 32.0 - header_h
        c.set_fill_rgb(6, 95, 70)
        c.rounded_rect(MARGIN_X, header_y, CONTENT_W, header_h, r=10.0, fill=True, stroke=False)

        text_x = MARGIN_X + 16.0
        if logo_img and not is_continuation:
            box_size = 56.0
            box_x = MARGIN_X + 14.0
            box_y = header_y + (header_h - box_size) / 2.0
            c.set_fill_rgb(255, 255, 255)
            c.rounded_rect(box_x, box_y, box_size, box_size, r=8.0, fill=True, stroke=False)
            c.image("Im1", box_x + 4.0, box_y + 4.0, box_size - 8.0, box_size - 8.0)
            text_x = box_x + box_size + 14.0
        elif logo_img and is_continuation:
            box_size = 36.0
            box_x = MARGIN_X + 12.0
            box_y = header_y + (header_h - box_size) / 2.0
            c.set_fill_rgb(255, 255, 255)
            c.rounded_rect(box_x, box_y, box_size, box_size, r=6.0, fill=True, stroke=False)
            c.image("Im1", box_x + 3.0, box_y + 3.0, box_size - 6.0, box_size - 6.0)
            text_x = box_x + box_size + 12.0

        max_title_w = (MARGIN_X + CONTENT_W - 14.0) - text_x
        if not is_continuation:
            c.text(
                text_x,
                header_y + header_h - 27.0,
                _truncate_text(school_name.upper(), max_title_w, 14.0, is_bold=True),
                font="F2",
                size=14.0,
                rgb=(255, 255, 255),
            )
            c.text(
                text_x,
                header_y + header_h - 45.0,
                _truncate_text(exam_title, max_title_w, 10.5, is_bold=False),
                font="F1",
                size=10.5,
                rgb=(209, 250, 229),
            )
            c.text(
                text_x,
                header_y + 14.0,
                "Lembar Informasi Pengawas Ruang (Offline Exam System)",
                font="F1",
                size=9.0,
                rgb=(167, 243, 208),
            )
        else:
            cont_title = (
                "TOKEN DAN PIN PENGAWAS (LANJUTAN)"
                if is_all_days
                else f"TOKEN DAN PIN HARI {day_label.upper()} (LANJUTAN)"
            )
            c.text(text_x, header_y + 29.0, cont_title, font="F2", size=12.5, rgb=(255, 255, 255))
            c.text(text_x, header_y + 13.0, _truncate_text(school_name.upper(), max_title_w, 9.0), font="F1", size=9.0, rgb=(209, 250, 229))

        pages.append(c)
        return c, header_y - 18.0

    canvas, cur_y = new_page(is_continuation=False)

    # Judul Utama Dokumen: TOKEN DAN PIN HARI <HARI>
    banner_title = "TOKEN DAN PIN PENGAWAS (SEMUA HARI)" if is_all_days else f"TOKEN DAN PIN HARI {day_label.upper()}"
    canvas.set_fill_rgb(241, 245, 249)
    canvas.set_stroke_rgb(203, 213, 225)
    canvas.set_line_width(1.0)
    canvas.rounded_rect(MARGIN_X, cur_y - 36.0, CONTENT_W, 38.0, r=8.0, fill=True, stroke=True)
    canvas.text(MARGIN_X + 14.0, cur_y - 22.0, banner_title, font="F2", size=14.0, rgb=(15, 23, 42))

    # Badge jumlah mapel di kanan banner
    total_count_label = f"{len(filtered_exams)} Mapel"
    badge_w = max(68.0, _approx_text_width(total_count_label, 9.5, is_bold=True) + 20.0)
    canvas.set_fill_rgb(16, 185, 129)
    canvas.rounded_rect(MARGIN_X + CONTENT_W - badge_w - 10.0, cur_y - 28.0, badge_w, 22.0, r=11.0, fill=True, stroke=False)
    canvas.text(MARGIN_X + CONTENT_W - badge_w, cur_y - 20.5, total_count_label, font="F2", size=9.5, rgb=(255, 255, 255))

    cur_y -= 52.0

    # ==========================================================
    # BAGIAN 1: PIN PENGAWAS
    # ==========================================================
    if is_all_days:
        # Tampilkan ringkasan grid PIN Senin - Sabtu
        pin_box_h = 104.0
        canvas.set_fill_rgb(255, 251, 235)
        canvas.set_stroke_rgb(245, 158, 11)
        canvas.set_line_width(1.2)
        canvas.rounded_rect(MARGIN_X, cur_y - pin_box_h, CONTENT_W, pin_box_h, r=8.0, fill=True, stroke=True)
        canvas.text(MARGIN_X + 14.0, cur_y - 20.0, "PIN PENGAWAS HARIAN (SENIN - SABTU)", font="F2", size=11.0, rgb=(146, 64, 14))

        col_w = (CONTENT_W - 28.0 - 20.0) / 3.0
        ordered_keys = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday"]
        for idx, k in enumerate(ordered_keys):
            row_i = idx // 3
            col_i = idx % 3
            bx = MARGIN_X + 14.0 + col_i * (col_w + 10.0)
            by = cur_y - 54.0 - row_i * 34.0
            canvas.set_fill_rgb(255, 255, 255)
            canvas.set_stroke_rgb(253, 230, 138)
            canvas.set_line_width(0.8)
            canvas.rounded_rect(bx, by, col_w, 28.0, r=5.0, fill=True, stroke=True)
            lbl = DAY_KEY_TO_LABEL[k].upper()
            val = (daily_pins.get(k) or "").strip() or "Belum Diatur"
            canvas.text(bx + 8.0, by + 16.5, lbl, font="F2", size=7.5, rgb=(120, 53, 15))
            canvas.text(bx + 8.0, by + 5.5, val, font="F3" if val != "Belum Diatur" else "F1", size=10.0 if val != "Belum Diatur" else 8.5, rgb=(15, 23, 42) if val != "Belum Diatur" else (148, 163, 184))

        cur_y -= (pin_box_h + 18.0)
    else:
        pin_value = (daily_pins.get(day_key) or "").strip()
        is_pin_set = bool(pin_value)
        display_pin = pin_value if is_pin_set else "(Belum Diatur di Server)"
        pin_box_h = 74.0

        if is_pin_set:
            canvas.set_fill_rgb(255, 251, 235)
            canvas.set_stroke_rgb(245, 158, 11)
        else:
            canvas.set_fill_rgb(254, 242, 242)
            canvas.set_stroke_rgb(239, 68, 68)
        canvas.set_line_width(1.2)
        canvas.rounded_rect(MARGIN_X, cur_y - pin_box_h, CONTENT_W, pin_box_h, r=8.0, fill=True, stroke=True)

        canvas.text(MARGIN_X + 16.0, cur_y - 20.0, "PIN PENGAWAS", font="F2", size=10.5, rgb=(146, 64, 14) if is_pin_set else (153, 27, 27))
        if is_pin_set:
            canvas.text(
                MARGIN_X + 16.0,
                cur_y - 47.0,
                display_pin,
                font="F3",
                size=22.0,
                rgb=(15, 23, 42),
            )
        else:
            canvas.text(
                MARGIN_X + 16.0,
                cur_y - 45.0,
                display_pin,
                font="F2",
                size=14.0,
                rgb=(185, 28, 28),
            )

        canvas.text(
            MARGIN_X + 16.0,
            cur_y - 63.0,
            f"Berlaku khusus hari {day_label} untuk reset penalti, selesai awal, & akses susulan.",
            font="F1",
            size=8.5,
            rgb=(100, 116, 139),
        )
        cur_y -= (pin_box_h + 20.0)

    # ==========================================================
    # BAGIAN 2: TOKEN MAPEL PER KELAS
    # ==========================================================
    canvas.text(MARGIN_X, cur_y - 12.0, "TOKEN MAPEL", font="F2", size=12.5, rgb=(15, 23, 42))
    canvas.set_stroke_rgb(203, 213, 225)
    canvas.set_line_width(1.0)
    canvas.line(MARGIN_X, cur_y - 18.0, MARGIN_X + CONTENT_W, cur_y - 18.0)
    cur_y -= 28.0

    def ensure_space(needed_h: float):
        nonlocal canvas, cur_y
        if cur_y - needed_h < 60.0:
            canvas, cur_y = new_page(is_continuation=True)
            canvas.text(MARGIN_X, cur_y - 12.0, "TOKEN MAPEL (LANJUTAN)", font="F2", size=11.0, rgb=(15, 23, 42))
            canvas.set_stroke_rgb(203, 213, 225)
            canvas.set_line_width(0.8)
            canvas.line(MARGIN_X, cur_y - 17.0, MARGIN_X + CONTENT_W, cur_y - 17.0)
            cur_y -= 26.0

    if not sorted_grades:
        ensure_space(60.0)
        canvas.set_fill_rgb(248, 250, 252)
        canvas.set_stroke_rgb(226, 232, 240)
        canvas.set_line_width(1.0)
        canvas.rounded_rect(MARGIN_X, cur_y - 48.0, CONTENT_W, 48.0, r=8.0, fill=True, stroke=True)
        empty_msg = (
            f"Belum ada jadwal ujian untuk hari {day_label}."
            if not is_all_days
            else "Belum ada paket soal ujian yang tersimpan."
        )
        canvas.text(MARGIN_X + 16.0, cur_y - 28.0, empty_msg, font="F1", size=10.5, rgb=(100, 116, 139))
    else:
        for grade in sorted_grades:
            grade_exams = grouped[grade]
            # Pastikan header kelas + minimal 1 baris muat
            ensure_space(64.0)

            # Header Kelas (contoh: KELAS 7)
            canvas.set_fill_rgb(15, 23, 42)
            canvas.rounded_rect(MARGIN_X, cur_y - 24.0, CONTENT_W, 24.0, r=6.0, fill=True, stroke=False)
            canvas.text(MARGIN_X + 12.0, cur_y - 16.0, f"KELAS {grade}", font="F2", size=10.5, rgb=(255, 255, 255))
            count_txt = f"{len(grade_exams)} Mapel"
            cw = _approx_text_width(count_txt, 9.0, is_bold=False)
            canvas.text(MARGIN_X + CONTENT_W - cw - 12.0, cur_y - 15.5, count_txt, font="F1", size=9.0, rgb=(167, 243, 208))
            cur_y -= 30.0

            for idx, ex in enumerate(grade_exams):
                token = str(ex.get("token", "") or "-").strip().upper()
                subject = str(ex.get("subject_name", "") or "-").strip()
                ex_day = str(ex.get("exam_day", "") or "").strip()
                start_t = str(ex.get("start_time", "") or "").strip()
                end_t = str(ex.get("end_time", "") or "").strip()

                time_str = ""
                if show_time and (start_t or end_t):
                    time_str = f"{start_t} - {end_t} WIB"
                if (is_all_days or include_all_exams) and ex_day:
                    time_str = f"{ex_day}, {time_str}" if time_str else ex_day

                row_h = 30.0
                ensure_space(row_h + 6.0)

                # Zebra background halus
                if idx % 2 == 0:
                    canvas.set_fill_rgb(248, 250, 252)
                else:
                    canvas.set_fill_rgb(255, 255, 255)
                canvas.set_stroke_rgb(226, 232, 240)
                canvas.set_line_width(0.75)
                canvas.rounded_rect(MARGIN_X, cur_y - row_h, CONTENT_W, row_h, r=5.0, fill=True, stroke=True)

                # Token Pill + Em-dash + Subject Name
                token_pill_w = max(72.0, _approx_text_width(token, 10.5, is_bold=True, is_mono=True) + 18.0)
                canvas.set_fill_rgb(254, 243, 199)
                canvas.set_stroke_rgb(245, 158, 11)
                canvas.set_line_width(0.7)
                canvas.rounded_rect(MARGIN_X + 8.0, cur_y - row_h + 5.0, token_pill_w, row_h - 10.0, r=4.0, fill=True, stroke=True)
                canvas.text(MARGIN_X + 16.0, cur_y - row_h + 11.0, token, font="F3", size=10.5, rgb=(146, 64, 14))

                dash_x = MARGIN_X + 8.0 + token_pill_w + 10.0
                canvas.text(dash_x, cur_y - row_h + 11.0, "—", font="F2", size=11.0, rgb=(100, 116, 139))

                # Info waktu di kanan jika ada
                right_reserve = 12.0
                if time_str:
                    tw = _approx_text_width(time_str, 8.5, is_bold=False)
                    canvas.text(MARGIN_X + CONTENT_W - tw - 12.0, cur_y - row_h + 11.5, time_str, font="F1", size=8.5, rgb=(100, 116, 139))
                    right_reserve = tw + 24.0

                subj_x = dash_x + 18.0
                max_subj_w = (MARGIN_X + CONTENT_W - right_reserve) - subj_x
                subj_txt = _truncate_text(subject, max(80.0, max_subj_w), 11.0, is_bold=True)
                canvas.text(subj_x, cur_y - row_h + 11.0, subj_txt, font="F2", size=11.0, rgb=(15, 23, 42))

                cur_y -= (row_h + 4.0)

            cur_y -= 10.0

    # Tambahkan footer nomor halaman pada setiap halaman
    printed_at = datetime.now().strftime("%d/%m/%Y %H:%M WIB")
    total_pages = len(pages)
    for page_idx, pg in enumerate(pages, start=1):
        pg.set_stroke_rgb(226, 232, 240)
        pg.set_line_width(0.8)
        pg.line(MARGIN_X, 42.0, MARGIN_X + CONTENT_W, 42.0)
        pg.text(
            MARGIN_X,
            28.0,
            _truncate_text(f"E-UJIAN {school_name} • Dicetak {printed_at}", CONTENT_W - 90.0, 8.0),
            font="F1",
            size=8.0,
            rgb=(100, 116, 139),
        )
        page_lbl = f"Halaman {page_idx} / {total_pages}"
        pw = _approx_text_width(page_lbl, 8.0)
        pg.text(MARGIN_X + CONTENT_W - pw, 28.0, page_lbl, font="F1", size=8.0, rgb=(100, 116, 139))

    # ==========================================================
    # RAKIT DOKUMEN PDF 1.4 LENGKAP DENGAN XREF TABLE
    # ==========================================================
    objects = []  # index 0 = obj 1

    # 1: Catalog
    objects.append(b"<< /Type /Catalog /Pages 2 0 R >>")
    # 2: Pages (diisi nanti)
    objects.append(b"")
    # 3: Font F1 (Helvetica)
    objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
    # 4: Font F2 (Helvetica-Bold)
    objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
    # 5: Font F3 (Courier-Bold)
    objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Courier-Bold /Encoding /WinAnsiEncoding >>")

    has_logo = logo_img is not None
    img_obj_num = None
    if has_logo:
        w, h, comp_rgb = logo_img
        img_obj_num = len(objects) + 1
        img_dict = (
            f"<< /Type /XObject /Subtype /Image /Width {w} /Height {h} "
            f"/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length {len(comp_rgb)} >>\n"
            .encode("ascii")
        )
        objects.append(img_dict + b"stream\n" + comp_rgb + b"\nendstream")

    xobj_res = f" /XObject << /Im1 {img_obj_num} 0 R >>" if has_logo else ""
    resources_str = f"<< /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R >>{xobj_res} >>".encode("ascii")

    page_obj_nums = []
    for pg in pages:
        stream_bytes = pg.build_stream()
        compressed_stream = zlib.compress(stream_bytes, level=6)
        content_obj_num = len(objects) + 1
        objects.append(
            f"<< /Length {len(compressed_stream)} /Filter /FlateDecode >>\nstream\n".encode("ascii")
            + compressed_stream
            + b"\nendstream"
        )
        page_obj_num = len(objects) + 1
        page_obj_nums.append(page_obj_num)
        page_dict = (
            f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 {int(PAGE_W)} {int(PAGE_H)}] "
            f"/Contents {content_obj_num} 0 R /Resources "
        ).encode("ascii") + resources_str + b" >>"
        objects.append(page_dict)

    kids_str = " ".join(f"{n} 0 R" for n in page_obj_nums)
    objects[1] = f"<< /Type /Pages /Kids [{kids_str}] /Count {len(page_obj_nums)} >>".encode("ascii")

    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = []
    for idx, obj in enumerate(objects, start=1):
        offsets.append(len(out))
        out.extend(f"{idx} 0 obj\n".encode("ascii"))
        out.extend(obj)
        out.extend(b"\nendobj\n")

    xref_offset = len(out)
    out.extend(f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode("ascii"))
    for off in offsets:
        out.extend(f"{off:010d} 00000 n \n".encode("ascii"))

    out.extend(
        f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref_offset}\n%%EOF\n".encode("ascii")
    )
    return bytes(out)
