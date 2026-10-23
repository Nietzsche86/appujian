# Panduan Proyek: Kiosk Offline Exam Reader (Android)

Repositori ini berisi kode sumber aplikasi Android Kiosk Exam Reader untuk ujian sekolah semi-online (soal digital terenkripsi, lembar jawaban fisik di LJK). Aplikasi beroperasi penuh secara luring (*full offline*) saat ujian.

---

## 1. Aturan Khusus Lingkungan Pengembangan (Zero-Local Build)

* **Komputer Lokal:** Laptop berspesifikasi rendah (*laptop kentang*). Dilarang mengasumsikan adanya Gradle Wrapper biner (`gradlew` / `gradle-wrapper.jar`), Android Studio, atau Android SDK lokal.
* **Proses Kompilasi:** Seluruh kompilasi Gradle (`assembleDebug`), linting, dan build APK dilakukan 100% di cloud via **GitHub Actions** (`.github/workflows/build-apk.yml`).
* **Verifikasi Kode:** Pastikan kode bebas dari *missing imports*, sintaks error, atau dependensi yang tidak didefinisikan di `app/build.gradle.kts` sebelum melakukan commit.

---

## 2. Tech Stack & Batasan Sistem

* **Target OS:** Android 8.0 (API 26) hingga Android 14 (API 34).
* **Bahasa:** Kotlin 1.9.22 (Coroutines + Flow).
* **UI Layer:** Android Native XML Views + `ViewBinding` (DILARANG menggunakan Jetpack Compose demi efisiensi RAM dan build time cloud).
* **Alokasi RAM Klien:** Maksimal **45 MB heap memory**.
* **Engine PDF:** Native `android.graphics.pdf.PdfRenderer` (DILARANG menambahkan dependensi library PDF pihak ketiga seperti Pdfium/AndroidPdfViewer).
* **Format Render Halaman:** Wajib menggunakan `Bitmap.Config.RGB_565` (menghemat 50% memori dibanding `ARGB_8888`). Segera panggil `bitmap.recycle()` saat halaman keluar dari layar (*unbound*).

---

## 3. Konvensi Kode & Arsitektur

### Pola Arsitektur
* Gunakan arsitektur sederhana: `MVVM` tanpa dependency injection berat (gunakan manual injection / singleton terkelola).
* Package root: `id.sch.examkiosk`

### Struktur Direktori
* `data/crypto/`: Logika PBKDF2, derivasi kunci, dan stream decryptor AES-GCM 256-bit.
* `data/local/`: `EncryptedSharedPreferences` untuk state timer, penalti, dan manifest ujian.
* `data/sync/`: Klien HTTP (`OkHttpClient`) untuk sinkronisasi LAN server lokal.
* `security/`: `EnvironmentMonitor` (Receiver Pesawat/BT/USB), `KioskEnforcer`, dan `PenaltyManager`.
* `ui/dashboard/`: Halaman filter angkatan, sinkronisasi, dan list mapel.
* `ui/kiosk/`: Halaman ujian, Custom Status Bar, dan PDF RecyclerView.

### Aturan Koding AI
1. **No Binary Assets:** Semua ikon (gembok, baterai, wifi, peringatan) WAJIB berupa file teks **Android Vector Drawable XML** (`res/drawable/ic_*.xml`). Jangan pernah mereferensikan file `.png` atau `.webp`.
2. **Lengkap & Eksplisit:** Tuliskan seluruh *import statement* secara utuh. Jangan gunakan singkatan komentar seperti `// ... other imports`.
3. **Android 14 Broadcast Compliance:** Semua `registerReceiver` di runtime WAJIB menyertakan flag `ContextCompat.RECEIVER_NOT_EXPORTED`.
4. **Keamanan PDF di Disk:** Dekripsi file `.enc` dilakukan via streaming (buffer 8 KB) ke file sementara di `cacheDir`. Buka file descriptor via `ParcelFileDescriptor.open()`, lalu **segera panggil `tempFile.delete()`** agar file fisik hilang dari filesystem sementara proses baca tetap berjalan di memori OS.
5. **Timer Tamper-Proof:** Seluruh timer (ujian & masa ban) wajib dihitung berbasis `SystemClock.elapsedRealtime()`, BUKAN `System.currentTimeMillis()`. Catat juga `BOOT_COUNT` untuk mendeteksi upaya restart perangkat di tengah ujian.

---

## 4. Pipeline CI/CD GitHub Actions

Build dipicu otomatis pada setiap push ke branch `main`. Workflow menggunakan `gradle/actions/setup-gradle@v4` dengan versi `8.6` eksplisit untuk mengatasi ketiadaan `gradle-wrapper.jar` lokal:

```bash
# Perintah git standar dari terminal lokal:
git add .
git commit -m "feat: [deskripsi fitur]"
git push origin main