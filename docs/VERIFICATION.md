# Verifikasi MyBest Android 1.2 — 27 Oktober 2026

Versi 1.2 menambahkan Student V2 native dan SSO resmi ke MyBest. APK terbaru berada di `app/build/outputs/apk/debug/app-debug.apk`, versionCode 3. Catatan versi 1.1 di bawah dipertahankan sebagai riwayat; ukuran/hash lama tidak merujuk APK 1.2.

APK 1.2: 6.242.264 byte. SHA-256: `adec608a1f32d5a156d29269211ad211c2dc2c0e90f18b406591b32937296db4`.

## Hasil 1.2

- Build app/test APK dan Android lint berhasil. Lint menghasilkan 0 error dan 58 warning; sebagian besar terkait versi dependency, resource tak terpakai dan API Android lama.
- Seluruh 58 uji JVM lulus, 0 gagal dan 0 dilewati, dengan simulasi e-learning pada port 4914 dan Student pada port 4917. Dua belas pengujian Student menjalankan klien HTTP asli, termasuk JSON, lookup anggota, intended SSO redirect dan isolasi tujuan eksternal. Empat belas pengujian parser Student memeriksa struktur native serta label pilihan.
- Collection lokal: Android Session Checks 8 request/8 assertion; Native Form Checks 9/9; Student V2 Checks 10/10. Seluruhnya 0 kegagalan. Collection serta spesifikasi lint berhasil. CLI tetap signed out dan tidak ada artefak dipublikasikan.
- Run Android akhir menghasilkan `OK (6 tests)`: login e-learning publik dan rotasi, vault sintetis, isolasi cookie, dua pengujian form surat sintetis, serta smoke Student V2 baca saja. KRS, semester, profil, rotasi, pembatasan nilai, jadwal dosen JSON, validasi kosong surat riset dan SSO MyBest berhasil. Validasi lokal mencegah pengiriman form yang kosong.
- Smoke yang sama juga lulus pada lebar 320dp dan font 200%. Sistem emulator dikembalikan ke ukuran/font awal. Screenshot ditinjau di direktori build; label field pada font besar ditampilkan di luar outline agar terbaca penuh. Warna teks yang digunakan memiliki rasio kontras 7,07:1 atau lebih pada latar terkait.
- Pemeriksaan font besar menangkap crash pada pengaturan hint Material sebelum EditText dipasang; urutan pemasangan diperbaiki dan run berikutnya lulus `OK (1 test)` tanpa crash. Satu pembacaan KRS juga sempat menghasilkan HTTP 500; aplikasi menampilkan error dan tombol coba lagi tanpa menghapus sesi. Pemeriksaan ulang server serta run baca saja berikutnya berhasil.
- Perbaikan sesi: 419 tidak langsung berarti logout; sesi aktif meminta muat ulang token form. Logout hanya membersihkan origin terpilih. SSO tidak mengulang kiriman sebelumnya dan tidak langsung membatalkan logout MyBest yang disengaja.

Uji live tidak mengirim absensi, tugas, diskusi, perubahan password/profil/biodata, surat, kritik/saran atau pengajuan akademik. Lookup anggota dan pengiriman file diperiksa dengan data sintetis. Kelayakan tiap akun, respons bisnis pengajuan, seluruh layanan eksternal dan batas idle autentikasi server tetap belum terverifikasi. Rincian observasi ada di `STUDENT_V2.md` serta `SESSION_NOTES.md`.

## Riwayat 1.1 — 26 Oktober 2026

Laporan ini menggantikan laporan versi 1.0 yang memakai WebView untuk form kompleks. Versi 1.1 menggunakan kontrol native untuk halaman/form e-learning. Student V2 dan portal ujian dilewati.

APK debug akhir: `app/build/outputs/apk/debug/app-debug.apk`, 6.192.632 byte, versionCode 2. SHA-256: `19c25e06e0ed80360c5c86d46fa366222c06556ffb1db6ffea6222c3a4675b83`.

## Build dan HTTP lokal

- `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:testDebugUnitTest` dan `:app:lintDebug`: berhasil.
- 30 tests: 6 parser portal, 7 parser form native, 6 HTTP sesi, 10 HTTP form dan 1 test aritmetika bawaan. Run dengan simulasi aktif tidak melewati test jaringan.
- HTTP dijalankan terhadap simulasi Postman `http://127.0.0.1:4901/bsi` dengan data sintetis. Client asli menangani cookie berganti, CSRF, expiry, 422 HTML, error 503, multipart biner, field berulang, `_method=patch`, Referer dan streaming unduhan GET/POST.
- PDF diuji dengan SHA-256 termasuk byte nonteks. Redirect lintas origin tidak meneruskan body/kredensial. POST 307/308 tidak diulang otomatis.
- JVM membatasi header Origin; uji lokal memverifikasi Referer, bukan pengiriman Origin pada JVM.
- Lint Android menghasilkan 0 errors, 30 warnings. Peringatan mencakup dependency/resource bawaan, saran KTX dan teks yang belum seluruhnya dipindah ke resource terjemahan. Tidak memakai baseline.
- Kontrak OpenAPI parsial diperiksa dengan syntax lint. Respons keberhasilan bisnis pada BSI belum ditetapkan melalui pengiriman produksi.

Output collection lokal, tanpa kegagalan:

```text
Native Form Checks
|                requests |                 9 |                0 |
|              assertions |                 9 |                0 |

Android Session Checks
|                requests |                 6 |                0 |
|              assertions |                 6 |                0 |
```

Mock menerima raw multipart (`interceptRequests: false`) agar byte biner tidak berubah menjadi UTF-8. Kegagalan awal digest PDF dan asumsi header Origin diperbaiki sebelum run yang lolos. Koleksi kontrak hasil generasi hanya untuk dokumentasi manual, bukan suite perubahan data produksi.

## Emulator dan pembacaan live

Target Android 14 / API 34, AVD `BsiPortTest`. APK dipasang dan dijalankan. Login live menggunakan kredensial sementara melalui instrumentation setelah izin CAPTCHA, tanpa menanamnya dalam source. `LiveReadOnlySmokeTest`: `OK (1 test)` pada APK native. `InspectNativeFormsTest`: `OK (1 test)` untuk pembacaan struktur form.

`NativeScreenSmokeTest` lolos pada layar standar dan 320dp dengan skala teks 130% (`OK (1 test)` masing-masing).

| Alur | Bukti |
| --- | --- |
| Kelas | Status server Belum Mulai tampil native, tanpa WebView. |
| Kuesioner dosen | Form dibuka dan pilihan radio dipilih; tidak dikirim. |
| Diskusi | Validasi judul wajib, isi draft, pemilih berkas dan pemulihan draft setelah Activity dibuat ulang. |
| Tugas | Daftar asli dan form Link Tugas terbuka; URL tidak valid ditolak lokal. |
| Profil | Form asli terbuka; nama kosong ditolak lokal. |
| Password | Field password native; isian kosong ditolak lokal. |
| Layanan | Student V2 dan ujian tidak ditampilkan. |
| Navigasi | Alur di atas menggunakan native views, tanpa membuka browser. |

Pemilih berkas dibatalkan melalui instrumentation. Byte multipart dan file unduhan diuji melalui HTTP lokal, bukan unggahan/unduhan akhir pada akun BSI. Monitor pemilih berkas awal tidak cocok dengan MIME type; filter uji diperbaiki dan run berikutnya lolos.

Screenshot pada `build/verification/narrow-native/` ditinjau untuk wrapping, scrolling dan label. Label field dibuat selalu mengambang; label konfirmasi password dipersingkat setelah inspeksi agar muat pada font besar. Screenshot berisi data akun tetap di direktori build. Fixture HTML native mengganti identitas, email, token CSRF dan ID opaque.

Uji login publik (validasi, refresh CAPTCHA dan rotasi) serta Android Keystore dengan data sintetis juga lolos: `OK (2 tests)`. Buffer crash emulator kosong setelah pengujian live.

## Batas dan sesi

Tidak ada absensi, pengiriman tugas/diskusi, perubahan profil/password atau kuesioner yang dikirim ke BSI selama pengujian. Kelas belum dimulai sehingga aksi absensi aktif tidak tersedia untuk inspeksi. Kontrak write mengikuti field yang diamati; keberhasilan transaksi dan respons produksi masih belum terverifikasi.

Expiry/pemulihan diuji lokal. Expiry live juga teramati selama pengerjaan dan diselesaikan dengan login baru yang disetujui pengguna. Aplikasi mengembalikan halaman tujuan setelah login tanpa mengulang request perubahan. CAPTCHA baru tetap memerlukan input pengguna. Pemutaran video, layanan eksternal/SSO dan signing distribusi berada di luar verifikasi ini.

UI mengikuti `DESIGN.md`: navy BSI, tema terang, satu kolom, target sentuh minimum 48dp, teks membungkus, loading/error/empty state dan konfirmasi sebelum perubahan. Verifikasi mencakup alur tertera, bukan setiap keadaan akun atau perubahan HTML di masa depan.
