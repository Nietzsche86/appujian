# Changelog E-UJIAN MTS RTH (MTS Roudhotut Thowalib)

## [v1.1.0] - Pembaruan Besar UI/UX, Mode Gelap, Pengingat H-5 Menit & Integrasi Server Proktor

### 1. Redesign Antarmuka Modern (Emerald & Slate Prestige - Material 3)
- Pembaruan palet warna harmonis berbasis **60-30-10** dan *Tonal Elevation* (`Deep Islamic Emerald`, `Cool Alabaster`, `Pure Crisp White`, serta warna status semantik Teal, Coral Red, dan Warm Amber).
- **Navigasi & Header:** Bottom Navigation Bar bergaya Material 3 dengan kapsul indikator lembut, animasi putar pada tombol sinkronisasi cepat di header, serta penggantian menu **"Dashboard"** menjadi **"Perangkat"** dengan ikon `PhoneAndroid`.
- **Mode Gelap & Mode Terang (Dark / Light Theme):** Penyesuaian palet gelap alami yang nyaman di mata beserta tombol pengalih mode terang/gelap langsung di Top App Bar.
- **Layar Perangkat (Kesiapan):** Banner status kesiapan interaktif berdenyut dengan aksi cepat ke Pengaturan Sistem, Bento Grid 2x2 untuk pemeriksaan sensor real-time (Mode Pesawat, Wi-Fi, Bluetooth, Mode Suara), serta spesifikasi kelayakan perangkat (OS & RAM).
- **Layar Panduan (Tata Tertib):** Kartu sorotan khusus untuk aturan krusial (Mode Kiosk & Penalti Kecurangan) dan kotak konfirmasi interaktif *"Saya Telah Membaca & Memahami Tata Tertib"*.
- **Layar Soal:** Pemindahan kartu **Statistik Pengerjaan Soal** (cincin progres + metrik Total/Selesai/Belum Selesai) ke menu Soal bersama Pre-Flight Check agar lebih tertata, lengkap dengan filter chip (`Semua`, `Aktif`, `Selesai`) dan *shimmer skeleton loading*.
- **Layar Pengaturan:** Kartu profil identitas madrasah (NPSN, NSM, Alamat), *Animated Sliding Segmented Control* untuk pilihan Kelas 7 / 8 / 9, riwayat IP server cepat, dan feedback visual *Tes Koneksi* serta *Sinkronkan Soal*.

### 2. Redesign Layar Ujian Kiosk (Jetpack Compose Material 3)
- Migrasi menyeluruh antarmuka layar ujian `KioskExamActivity` ke Compose Material 3 (`KioskScreen` & `KioskDialogs`).
- Penambahan **Eye-Comfort Reading Mode** (mode nyaman baca untuk mengurangi silau saat membaca soal panjang) dan dialog **Lompat ke Halaman** (*Quick Jump-to-Page*).
- Dialog PIN Pengawas dan Konfirmasi Selesai Ujian modern serta overlay peringatan penalti/pelanggaran yang responsif.

### 3. Notifikasi Pengingat Ujian Offline (H-5 Menit)
- Penjadwalan alarm lokal otomatis (`AlarmManager`) untuk memunculkan notifikasi **5 menit sebelum jam ujian dimulai**, bekerja penuh secara offline tanpa koneksi internet/LAN.
- Otomatis memfilter sesuai jenjang kelas siswa, tidak berbunyi untuk ujian yang sudah selesai atau sedang dikerjakan, otomatis ter-reschedule setelah perangkat reboot, dan langsung membuka tab **Soal** saat notifikasi diketuk.

### 4. Peningkatan & Integrasi Server Proktor (`/server`)
- **Perbaikan Paket Soal Kelas 7 & 8:** Mengganti entri Kelas 7 yang hilang dengan paket valid **Bahasa Indonesia Kelas 7** (`BINDO7`) dan memperbaiki PDF Matematika Kelas 8 (`MTK88`) menjadi dokumen 4 halaman valid beserta pembaharuan `.enc`, SHA-256, salt, dan IV.
- **Endpoint `/api/ping` & Verifikasi Server Asli:** Menambahkan endpoint `/api/ping` beridentitas `e-ujian-mts-rth` dan validasi `HEAD`/`manifest.json` agar Android tidak salah mengenali server lain di jaringan LAN.
- **Telemetri Monitor Siswa:** Sinkronisasi dan tes koneksi dari Android mengirimkan informasi jenjang kelas dan model perangkat ke log web proktor secara real-time.
- **Auto-Heal Enkripsi & Validasi Jadwal:** Server otomatis mendeteksi ketidakcocokan checksum `.enc` saat `rebuild_manifest`, mendukung enkripsi ulang penuh pada tombol **Enkripsi Ulang**, dan memvalidasi Jam Selesai > Jam Mulai agar pengingat H-5 menit di Android selalu akurat.
- **Penanganan Error Unduhan & Kapitalisasi Token:** Aplikasi Android melaporkan kegagalan unduhan/checksum secara eksplisit dan mendukung fallback huruf kapital saat validasi token ujian.
