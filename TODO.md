# Implementation Checklist: Kiosk Offline Exam Reader (MTS ROUDHOTUT THOWALIB)

## Milestone 1: Setup Proyek & CI/CD Cloud Build
- [x] Buat file konfigurasi `.github/workflows/build-apk.yml` dengan setup Gradle 8.6 & JDK 17 tanpa wrapper biner.
- [x] Buat file konfigurasi Gradle:
  - [x] `settings.gradle.kts`
  - [x] `build.gradle.kts` (Project-level)
  - [x] `gradle.properties`
  - [x] `app/build.gradle.kts` (ViewBinding aktif, dependensi OkHttp, Security Crypto, Material, Coroutines).
- [x] Buat `res/xml/network_security_config.xml` untuk mengizinkan Cleartext HTTP pada subnet LAN lokal (`192.168.x.x` dan `10.x.x.x`).
- [x] Buat `AndroidManifest.xml` (izin jaringan lokal, Bluetooth, mode kiosk, `FLAG_SECURE`).
- [x] Salin dan integrasikan `Logo.png` ke `app/src/main/res/drawable/logo_sekolah.png`.
- [x] Buat vector drawable XML di `res/drawable/`:
  - [x] `ic_lock.xml`
  - [x] `ic_battery.xml`
  - [x] `ic_airplane.xml`
  - [x] `ic_warning.xml`
  - [x] `ic_sync.xml`
  - [x] `ic_bluetooth.xml`
  - [x] `ic_check_circle.xml`
  - [x] `ic_cancel.xml`
  - [x] `ic_timer.xml`
  - [x] `ic_server.xml`
  - [x] `ic_book.xml`
  - [x] `ic_wifi_off.xml`
- [x] Buat `DashboardActivity.kt` dan `activity_dashboard.xml` dengan identitas MTS ROUDHOTUT THOWALIB.
- [x] **Verifikasi:** Setup remote origin ke `https://github.com/Nietzsche86/appujian.git` dan push ke GitHub Actions.

---

## Milestone 2: Sinkronisasi Server LAN & Kriptografi
- [x] Buat data model JSON (`ExamManifest.kt`, `ExamItem.kt`, `ExamState.kt`).
- [x] Implementasikan `LocalSyncManager.kt`:
  - [x] Fungsi test koneksi ke IP server lokal.
  - [x] Unduh file `manifest.json`.
  - [x] Unduh file `.enc` bertahap dan simpan di `context.filesDir/exams/`.
  - [x] Verifikasi checksum SHA-256 berkas terunduh.
- [x] Implementasikan `CryptoEngine.kt`:
  - [x] Derivasi kunci PBKDF2 (10.000 iterasi) dari Token Angkatan + Salt.
  - [x] Stream Decryptor AES-256-GCM dengan buffer 8 KB.
  - [x] Alur unlinked temporary file descriptor (`pfd` dibuka, lalu `tempFile.delete()`).
- [x] Implementasikan UI Dashboard Modern & Aesthetic:
  - [x] Input field IP & Port server sekolah yang fleksibel diedit dengan tombol simpan & tes di `SecureStorage`.
  - [x] Tab/Filter pilihan angkatan jenjang MTs (Kelas 7, Kelas 8, Kelas 9, Semua).
  - [x] RecyclerView daftar mapel tersinkron beserta status (`TERSIMPAN`, `SELESAI`, `SEDANG_UJIAN`, `BELUM_DIUNDUH`).
  - [x] Dialog input Token Angkatan (`TokenInputDialog.kt`).
- [ ] **Verifikasi:** Uji unduh data mock dari web server lokal dan uji dekripsi token benar vs salah pada perangkat uji.

---

## Milestone 3: Kiosk PDF Viewer & Status Bar Kustom
- [x] Buat `KioskExamActivity.kt` dan layout `activity_kiosk_exam.xml`.
- [x] Terapkan Custom Status Bar (Tinggi 48dp, Background `#0F172A`, Wajib Tampil):
  - [x] Label Mapel & Angkatan (kiri).
  - [x] Tamper-proof Countdown Timer berbasis `SystemClock.elapsedRealtime()` (tengah).
  - [x] Indikator perubahan warna timer ($\le$ 15 menit kuning, $\le$ 5 menit merah berkedip).
  - [x] Indikator level baterai real-time via `ACTION_BATTERY_CHANGED` (kanan).
  - [x] Indikator halaman aktif (kanan).
  - [x] Tombol gembok akses pengawas (kanan).
- [x] Implementasikan penampil PDF ramah memori:
  - [x] `PdfPageAdapter.kt` menggunakan native `PdfRenderer`.
  - [x] Konfigurasi render bitmap `Bitmap.Config.RGB_565`.
  - [x] Logika `bitmap.recycle()` pada `onViewRecycled`.
  - [x] Bungkus kanvas dengan pendeteksi gestur *pinch-to-zoom* (`ZoomableLayout.kt`, 1.0x hingga 3.0x).
- [x] Atur flag jendela: `FLAG_SECURE` (anti-screenshot/recording) dan `FLAG_KEEP_SCREEN_ON`.
- [ ] **Verifikasi:** Pantau heap memory di bawah 45 MB saat membuka dokumen PDF 20+ halaman pada perangkat uji.

---

## Milestone 4: Anti-Cheat, Sensor Pelanggaran & Penalti Ban
- [x] Implementasikan `KioskEnforcer.kt`:
  - [x] Pemanggilan `startLockTask()`.
  - [x] Layar pemblokir jika status `lockTaskModeState == LOCK_TASK_MODE_NONE`.
  - [x] Penanganan masa tenggang (*grace period*) 3 detik setelah status `PINNED`.
- [x] Implementasikan `EnvironmentMonitor.kt` (Dynamic BroadcastReceiver):
  - [x] Pendaftaran dengan flag Android 14 `ContextCompat.RECEIVER_NOT_EXPORTED`.
  - [x] Deteksi Mode Pesawat nonaktif (`AIRPLANE_MODE_ON == 0`).
  - [x] Deteksi Bluetooth aktif (`BluetoothAdapter.STATE_ON`).
  - [x] Deteksi koneksi transfer data kabel USB.
  - [x] Deteksi kehilangan fokus jendela (`onWindowFocusChanged(false)`).
- [x] Implementasikan `PenaltyManager.kt`:
  - [x] State tracking di `EncryptedSharedPreferences` (`violation_count`, `remaining_ban_seconds`).
  - [x] Heartbeat penyimpanan sisa waktu ujian setiap 5 detik.
  - [x] Deteksi *reboot* curang via pengecekan `BOOT_COUNT`.
  - [x] Logika eskalasi ban:
    - [x] Pelanggaran 1: Ban 60 detik + Overlay Merah + Getar.
    - [x] Pelanggaran 2: Ban 180 detik + Overlay Merah + Getar.
    - [x] Pelanggaran 3: Ban 300 detik + Overlay Merah + Peringatan.
    - [x] Pelanggaran 4+: Kunci Total Permanen.
  - [x] Penegakan timer ujian utama tetap berjalan selama masa ban.
- [x] Implementasikan `SupervisorPinDialog.kt`:
  - [x] Validasi hash SHA-256 Master PIN secara offline.
  - [x] Fitur reset pelanggaran / bypass ban darurat.
  - [x] Fitur selesaikan ujian lebih awal (`stopLockTask()` dan tandai status mapel `SELESAI`).
- [ ] **Verifikasi:** Simulasi mematikan mode pesawat saat ujian untuk memastikan overlay ban merah aktif dan timer ujian utama tidak berhenti pada perangkat uji.
