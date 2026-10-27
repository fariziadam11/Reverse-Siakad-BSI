# MyBest Android 1.2

Port Android untuk portal mahasiswa [MyBest BSI](https://elearning.bsi.ac.id/), berdasarkan pemeriksaan langsung pada 26 Oktober 2026. Package: `com.gis.reverse`. Android minimum: 8.0 / API 26.

Layar native mencakup login/CAPTCHA, beranda, jadwal, materi, kelas, diskusi, tugas, nilai dan pengaturan profil e-learning, serta Student V2. Student V2 tersedia dari tombol login, menu Layanan, dan menu toolbar. KRS/KHS, semester, profil, kalender, jadwal dosen, unduhan dan form akademik mengikuti halaman akun. Portal ujian tetap di luar cakupan. Video serta layanan eksternal mengikuti aplikasi tujuan. Aplikasi membutuhkan koneksi dan akun BSI yang valid.

Form mengikuti field, CSRF, cookie dan ID dari server. Diskusi mendukung lampiran PDF maksimum 2 MB sesuai form yang diamati. Pengumpulan tugas yang diamati menggunakan tautan. Form profil/password, kuesioner dosen dan unduhan lampiran melalui POST juga dirender native. Pengiriman perubahan memerlukan konfirmasi; request perubahan tidak dikirim ulang otomatis setelah koneksi atau sesi terputus. Draft selama rotasi tidak menyimpan password.

Sesi digunakan kembali saat aplikasi dibuka. HTTP 419 kini memeriksa dashboard sekali: token form basi meminta muat ulang form ketika autentikasi masih valid. Jika sesi MyBest berakhir dan sesi Student V2 masih aktif, aplikasi mencoba SSO resmi BSI tanpa mengulang kiriman sebelumnya. Jalur ini berhasil diuji pada akun yang diberikan. Jika kedua sesi berakhir, pengguna perlu login; CAPTCHA yang diminta server tetap diisi pengguna. Logout MyBest yang disengaja menonaktifkan pemulihan otomatis sampai login berikutnya.

Opsi **Ingat akun** e-learning mengisi ulang NIM dan password dari penyimpanan AES-GCM dengan Android Keystore. Student V2 memakai kontrol Remember me milik server ketika dipilih pengguna; password Student V2 tidak disimpan oleh port. Tidak ada password akun asli di source atau APK. Logout masing-masing portal mempertahankan cookie portal lainnya. Rincian bukti dan batas sesi ada di `docs/SESSION_NOTES.md` dan `docs/STUDENT_V2.md`.

## Build

Gunakan JDK yang kompatibel dengan AGP project dan SDK pada `local.properties`:

```powershell
.\gradlew.bat :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`, versi 1.2 / versionCode 3. APK ini memakai debug signing untuk pengujian; konfigurasi signing distribusi belum dibuat.

## Verifikasi lokal

Jalankan masing-masing simulasi pada terminal terpisah dari root project:

```powershell
postman simulation run postman/simulations/bsi-android.sim.yaml --port 4900 --no-history --output ndjson
postman simulation run postman/simulations/student-v2.sim.yaml --port 4916 --no-history --output ndjson
```

```powershell
$env:BSI_SIMULATION_ORIGIN='http://127.0.0.1:4900/bsi'
$env:STUDENT_SIMULATION_ORIGIN='http://127.0.0.1:4916/student'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug --no-configuration-cache
postman collection run 'postman/collections/Android Session Checks' --no-report-events
postman collection run 'postman/collections/Native Form Checks' --env-var 'baseUrl=http://127.0.0.1:4900/bsi' --no-report-events
postman collection run 'postman/collections/Student V2 Checks' --env-var 'baseUrl=http://127.0.0.1:4916/student' --no-report-events
```

Sebanyak 58 pengujian JVM memeriksa parser dan klien HTTP asli terhadap simulasi Postman, termasuk rotasi cookie, form, unggahan biner, unduhan, 419, SSO dan pencarian anggota surat. Fixture sintetis mengikuti struktur yang diamati. Pengujian jaringan dilewati bila variabel simulasi tidak disetel. Jangan arahkan collection pemeriksaan lokal ke akun BSI produksi.

`LoginScreenTest` memeriksa halaman login publik, validasi, refresh CAPTCHA dan rotasi pada emulator. `CredentialVaultTest` menggunakan data sintetis dan penyimpanan terpisah untuk menguji enkripsi serta penolakan ciphertext rusak. `LiveReadOnlySmokeTest` bersifat opt-in: memerlukan kredensial sementara dan persetujuan CAPTCHA, atau argumen `bsiSessionOnly=true` dengan sesi yang masih aktif. Jangan simpan kredensial dalam Gradle properties, file source, atau command script.

## Struktur dan batas

- `data/BsiClient.kt`: HTTP form, cookie, CSRF, redirect, batas waktu dan sinyal sesi kedaluwarsa.
- `data/PortalParser.kt`, `data/NativePortalParser.kt`: pemetaan HTML dan form BSI menjadi model native. ID kelas berasal dari tautan server.
- `data/SecureSession.kt`: penyimpanan akun opt-in dan cookie HTTP native.
- `data/StudentPortalClient.kt`, `data/StudentPortalParser.kt`: sesi Student V2, SSO resmi, parser menu, data akademik dan form.
- `StudentResearchFormRenderer.kt`: anggota surat riset berulang dan pencarian NIM yang diminta pengguna.
- `NativeFormRenderer.kt`, `NativeFormState.kt`: kontrol, validasi, pemilih berkas dan draft selama perubahan konfigurasi.
- `MainActivity.kt`: navigasi, konfirmasi perubahan, staging lampiran dan penyimpanan hasil unduhan.
- `postman/specs/bsi-web.yaml`, `bsi-native-forms.yaml`: dokumentasi parsial interface web yang diamati, bukan spesifikasi resmi BSI.
- `postman/specs/student-v2.yaml`: dokumentasi parsial interface Student V2 dan layanan JSON yang diamati.
- `docs/VERIFICATION.md`: bukti, hasil uji dan tindakan yang belum diuji.

Perubahan HTML server dapat memerlukan penyesuaian parser. Saat inspeksi kelas berstatus Belum Mulai; endpoint absensi aktif tidak ditebak atau diuji. Pengiriman tugas/forum, profil/password dan kuesioner diuji pada simulasi lokal, belum dikirim ke BSI produksi. Keberhasilan bisnis di BSI belum dipastikan. SSO Student V2 ke MyBest telah diuji; video dan layanan eksternal lainnya belum seluruhnya diuji end-to-end. Source server/database BSI tidak direkonstruksi.

`NativeScreenSmokeTest` memeriksa navigasi dan validasi lokal pada sesi aktif tanpa mengirim perubahan. `InspectNativeFormsTest` membaca struktur halaman. Collection `BSI Native Forms Contract` adalah hasil generasi untuk dokumentasi manual; nilai contoh harus diganti dari form/sesi aktual dan bukan suite untuk dijalankan rutin pada produksi. Artefak Postman belum dipublikasikan ke cloud.

Tangkapan layar pengujian lokal berada di `build/verification/`; sebagian memuat data akun pengguna dan tidak disertakan sebagai fixture atau dokumentasi publik.
