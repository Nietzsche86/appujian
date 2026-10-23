#!/usr/bin/env python3
"""
Generator PDF Lembar Soal Ujian Mock (Sample)
MTS ROUDHOTUT THOWALIB
Menghasilkan dokumen PDF 1.4 valid multi-halaman tanpa dependensi library eksternal.
"""

import os
from pathlib import Path

def create_exam_pdf(file_path: Path, subject_name: str, grade: int, total_pages: int = 3):
    """
    Membuat file PDF valid multi-halaman dengan format standar PDF 1.4.
    """
    objects = []
    
    # 1 0 obj: Catalog
    catalog = "<< /Type /Catalog /Pages 2 0 R >>"
    objects.append(catalog)
    
    # 2 0 obj: Pages
    kids = " ".join([f"{3 + i * 3} 0 R" for i in range(total_pages)])
    pages_obj = f"<< /Type /Pages /Kids [{kids}] /Count {total_pages} >>"
    objects.append(pages_obj)
    
    # Generate page objects
    for i in range(total_pages):
        page_num = i + 1
        page_obj_idx = 3 + i * 3
        content_obj_idx = page_obj_idx + 1
        font_obj_idx = page_obj_idx + 2
        
        # Page Object
        page_entry = f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents {content_obj_idx} 0 R /Resources << /Font << /F1 {font_obj_idx} 0 R >> >> >>"
        objects.append(page_entry)
        
        # Content Stream
        text_lines = [
            f"MTS ROUDHOTUT THOWALIB",
            f"ASESMEN SUMATIF AKHIR SEMESTER (ASAS)",
            f"Mata Pelajaran: {subject_name} - Kelas {grade}",
            f"Halaman {page_num} dari {total_pages}",
            f"----------------------------------------------------------------",
            f"PETUNJUK: Berdoalah sebelum mengerjakan. Lembar jawaban diisi pada LJK fisik.",
            f"",
            f"Soal Nomor {((page_num - 1) * 5) + 1}:",
            f"Manakah pernyataan berikut yang paling tepat sesuai kaidah pembelajaran?",
            f"A. Membaca dan memahami konsep secara mandiri dan tekun.",
            f"B. Mengabaikan instruksi dan langsung menjawab sembarangan.",
            f"C. Mengisi lembar jawaban LJK tanpa membaca soal terlebih dahulu.",
            f"D. Membuka aplikasi lain selama ujian berlangsung.",
            f"",
            f"Soal Nomor {((page_num - 1) * 5) + 2}:",
            f"Dalam pelaksanaan ujian semi-online luring (full offline), siswa wajib mematuhi:",
            f"A. Menyalakan koneksi internet hotspot seluler.",
            f"B. Mengaktifkan Mode Pesawat dan mematikan koneksi Bluetooth.",
            f"C. Mengaktifkan koneksi USB transfer data ke laptop lain.",
            f"D. Membuka fitur split screen dan floating apps di HP.",
            f"",
            f"[ Lembar Soal Resmi MTS ROUDHOTUT THOWALIB - Dokumen Rahasia Ujian ]"
        ]
        
        stream_content = "BT\n/F1 12 Tf\n50 780 Td\n18 TL\n"
        for line in text_lines:
            escaped = line.replace("(", "\\(").replace(")", "\\)")
            if "MTS ROUDHOTUT" in line:
                stream_content += f"/F1 16 Tf\n({escaped}) Tj\nT*\n/F1 12 Tf\n"
            else:
                stream_content += f"({escaped}) Tj\nT*\n"
        stream_content += "ET\n"
        
        stream_bytes = stream_content.encode('latin1')
        content_entry = f"<< /Length {len(stream_bytes)} >>\nstream\n{stream_content}endstream"
        objects.append(content_entry)
        
        # Font Object
        font_entry = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
        objects.append(font_entry)
        
    # Build complete PDF with XREF table
    pdf_output = b"%PDF-1.4\n"
    offsets = []
    
    for i, obj in enumerate(objects):
        obj_num = i + 1
        offsets.append(len(pdf_output))
        pdf_output += f"{obj_num} 0 obj\n{obj}\nendobj\n".encode('latin1')
        
    xref_offset = len(pdf_output)
    pdf_output += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode('latin1')
    for offset in offsets:
        pdf_output += f"{offset:010d} 00000 n \n".encode('latin1')
        
    pdf_output += f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref_offset}\n%%EOF\n".encode('latin1')
    
    file_path.parent.mkdir(parents=True, exist_ok=True)
    with open(file_path, "wb") as f:
        f.write(pdf_output)
    print(f"[OK] Dibuat PDF: {file_path.name} ({len(pdf_output)} bytes, {total_pages} halaman)")

def main():
    base_dir = Path(__file__).parent / "raw_pdfs"
    base_dir.mkdir(parents=True, exist_ok=True)
    
    create_exam_pdf(base_dir / "soal_bindo_7.pdf", "Bahasa Indonesia", 7, total_pages=4)
    create_exam_pdf(base_dir / "soal_ipa_8.pdf", "Ilmu Pengetahuan Alam (IPA)", 8, total_pages=4)
    create_exam_pdf(base_dir / "soal_matematika_9.pdf", "Matematika", 9, total_pages=4)
    print("\nSemua berkas PDF soal sample berhasil digenerate di folder raw_pdfs/")

if __name__ == "__main__":
    main()
