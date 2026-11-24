# Kompatibilitas portal BSI

Pemeriksaan 30 Oktober 2026 menggunakan akun yang diizinkan pengguna. Login dan pembacaan halaman dilakukan melalui HTTP dengan cookie di memori. Tidak mengirim komentar, pesan pribadi, tugas, absensi atau perubahan akademik. Catatan ini tidak menyimpan kredensial, cookie, token CSRF atau ID opaque akun.

## Hasil penelusuran JSON

Halaman utama yang diperiksa tetap mengirim `text/html` meskipun request memakai `Accept: application/json`. Ini membuktikan perilaku halaman yang diperiksa, bukan ketiadaan seluruh API internal BSI.

| Portal | Halaman HTML yang diperiksa | JSON yang sudah digunakan aplikasi |
| --- | --- | --- |
| E-learning | Dashboard, jadwal, materi, tugas, diskusi per mata kuliah, komentar per pertemuan | CAPTCHA melalui `/reload-captcha`; tidak menggantikan halaman akademik |
| Student V2 | Beranda, KRS, pilihan semester KHS, daftar jadwal mengajar | Detail `/get-jadwal-dosen/{kode}`, informasi PKL dari `data-url`, pencarian anggota surat `/mahasiswa/get-mahasiswa-by-nim` |

JSON yang tersedia belum menjadi sumber pengganti untuk seluruh data HTML di atas. Tidak menebak URL `/api`, mencoba akses di luar akun, atau menambahkan server perantara. Pembacaan live tidak memanggil endpoint tulis yang ditemukan dalam script. Catatan JSON Student yang lebih awal berada di `STUDENT_V2.md`.

## Perubahan aplikasi

- Pembacaan halaman melalui `BsiClient.page` memeriksa identitas dashboard, data inti jadwal, wadah materi/tabel tugas, struktur topik diskusi, serta identitas dan hidden context komentar. Respons HTTP 200 yang kehilangan struktur tersebut menghasilkan `PortalError`, sehingga alur error/muat ulang yang sudah ada dipakai. Ini tidak menghapus cookie dan tidak mengulang pengiriman.
- `StudentPortalClient.page` dan `read` memeriksa wadah konten halaman utama yang dikenal, tabel KRS/jadwal mengajar, dan kontrol pilihan semester. Pesan pembatasan/ketersediaan dari server tetap diterima. Respons JSON, redirect eksternal dan halaman di luar cakupan tersebut mengikuti alur sebelumnya.
- Jadwal memakai heading dan posisi elemen label/nilai sebagai fallback ketika kelas CSS kecil berubah. Pemeriksaan live menemukan label `Waktu` pada sebagian mata kuliah; parser kini menerima `Waktu` maupun `Hari & Jam`. Sebelumnya variasi itu menghasilkan jadwal kosong pada mata kuliah terkait.
- Elemen semantik `main` dan `[role=main]` menjadi fallback wadah konten. Daftar kosong dengan struktur valid, serta pesan kosong yang dikenali, tidak dianggap perubahan struktur.

Pemeriksaan ini berlaku pada kontrak pembacaan yang disebutkan, bukan validator seluruh HTML BSI. Perubahan besar masih memerlukan pembaruan parser. Pesan kosong baru yang belum dikenali dapat menghasilkan pesan kompatibilitas; hal itu sengaja lebih aman daripada menyimpulkan tidak ada data. Keberhasilan HTTP tidak dijadikan bukti bahwa parsing berhasil.

## Verifikasi dan cara mengulang

Build aplikasi dan APK instrumentation berhasil. Seluruh 74 tes JVM lulus, 0 gagal dan 0 dilewati, termasuk 11 tes kompatibilitas dan dua regresi transport. Regresi mengubah kelas CSS, label waktu, wadah halaman, identitas komentar, hidden context, tabel dan kontrol semester. Pengujian jaringan memakai server lokal serta data sintetis.

Jalankan mock pada dua terminal:

```powershell
npx -y postman-cli@1.74.0 mock run postman/mocks/bsi --port 4914 --no-history
npx -y postman-cli@1.74.0 mock run postman/mocks/student-v2 --port 4915 --no-history
```

Lalu pada terminal lain:

```powershell
$env:BSI_SIMULATION_ORIGIN = 'http://127.0.0.1:4914/bsi'
$env:STUDENT_SIMULATION_ORIGIN = 'http://127.0.0.1:4915/student'
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
npx -y postman-cli@1.74.0 collection run 'postman/collections/Android Session Checks' --env-var 'baseUrl=http://127.0.0.1:4914/bsi'
npx -y postman-cli@1.74.0 collection run 'postman/collections/Student V2 Checks' --env-var 'baseUrl=http://127.0.0.1:4915/student'
npx -y postman-cli@1.74.0 collection run 'postman/collections/Native Form Checks' --env-var 'baseUrl=http://127.0.0.1:4914/bsi'
```

Hasil Postman: Android Session Checks 9 request/9 assertion, Student V2 Checks 11/11, Native Form Checks 9/9; seluruhnya 0 kegagalan. Output lengkap tersimpan lokal di `build/compatibility-session-checks.txt`, `build/compatibility-student-checks.txt` dan `build/compatibility-native-checks.txt`. Percobaan awal gagal akibat prefiks URL mock; handler diperbaiki untuk menerima prefiks `/bsi` dan `/student`, lalu ketiga collection lulus. Seluruh spesifikasi lolos lint sintaks dan kedua collection yang diubah lolos lint. Tidak ada artefak dipublikasikan.

Respons akun asli juga diuji melalui validator dari JAR aplikasi yang baru dibangun, tanpa menyimpan HTML atau cookie; hasil berupa metadata ada di `build/portal-json-observations.json`. HP sudah tidak terhubung pada sesi ini, sehingga APK baru belum dipasang atau diuji ulang di HP. Tidak menambahkan monitor terjadwal yang menyimpan kredensial. Pemeriksaan struktur berjalan otomatis saat aplikasi membaca halaman, dan suite lokal dapat dijalankan ulang untuk memeriksa perubahan parser.

Postman `skills status` melaporkan skill lokal telah dimodifikasi; pembaruan paksa tidak dijalankan agar perubahan lokal tidak tertimpa.

## Verifikasi lanjutan setelah HP terhubung

APK dibangun ulang dan dipasang pada HP I2218 dengan mode replace, mempertahankan data aplikasi. SHA-256 APK yang dipasang: `fdcc24bebe19b6cae183b378b8e07996463bd85bc837d422e08e7ddbe94adf45`.

Tiga pengujian Android lulus tanpa skip: `LiveReadOnlySmokeTest`, `DiscussionLayoutTest` dan `StudentV2SmokeTest`. Pemeriksaan mencakup pembacaan dashboard, jadwal, materi, tugas, akun, KRS, pilihan semester, profil Student, pesan ketersediaan nilai, jadwal dosen JSON, rotasi Activity, serta SSO Student ke MyBest. Test komentar memeriksa input tetap terlihat saat menggulir/keyboard dan validasi lokal. Validasi form surat hanya memakai form kosong; tidak ada konfirmasi akhir atau pengiriman akademik yang dilakukan.

Screenshot aplikasi ditinjau dan disalin ke `build/verification-compatibility-device/elearning` dan `build/verification-compatibility-device/student`, keduanya tidak dilacak Git. Aplikasi dibuka kembali setelah pengujian. Hasil ini melengkapi keterbatasan uji HP pada catatan sebelumnya; pengiriman produksi dan seluruh kemungkinan perubahan server tetap di luar verifikasi.
