# Pemeriksaan portal ujian Kampus A

Tanggal: 30 Oktober 2026. Target yang diminta pengguna: `https://ujiankampusa.bsi.ac.id/dashboard`. Pemeriksaan memakai login e-learning yang diizinkan, lalu mengikuti tautan ujian resmi pada dashboard. Hanya dashboard, dua halaman jadwal dan aset JavaScript yang dibaca. Tidak memulai attempt, membuka soal, menyimpan jawaban, menyelesaikan ujian atau mengubah pembatasan perangkat. Tidak mengubah implementasi aplikasi.

## Sesi

GET dashboard ujian tanpa sesi mengarah ke login e-learning. Setelah autentikasi e-learning, alur yang diamati:

```text
elearning.bsi.ac.id/halaman-ujian
  302 -> ujiankampusa.bsi.ac.id/authenticate?token=[REDACTED]
  302 -> ujiankampusa.bsi.ac.id/dashboard
```

Dashboard ujian berhasil terbaca dan menampilkan identitas akun. Token berasal dari redirect resmi, bukan konstruksi klien. Cookie diproses melalui cookie jar sesuai domain; cookie e-learning tidak disalin manual menjadi cookie portal ujian. Pada pengamatan, dashboard menyatakan tidak ada jadwal untuk hari itu. Hal ini tidak membuktikan status seluruh jadwal atau masa ujian.

## Pembatasan layar yang ditemukan

Dua halaman jadwal yang dibuka memuat `/js/jquery-right.js`. Aset itu dibaca tanpa mengeksekusinya. Pada baris 93–108, script membaca `window.screen.width` dan `window.screen.height`. Ketika lebar kurang dari 770, script menampilkan pesan bahwa perangkat tidak diperbolehkan untuk ujian online, lalu mengarahkan ke `https://elearning.bsi.ac.id/user/dashboard?alert=hp`. Pemeriksaan lebar yang sama diulang pada fungsi pembuka fullscreen.

Ini adalah penolakan eksplisit di frontend, tidak sekadar tata letak yang terlalu sempit. Angka resolusi fisik layar HP tidak sama dengan ukuran layar yang dilaporkan browser dalam CSS pixel. Pemeriksaan yang dibaca tidak menyebut model laptop atau Android secara langsung; identifikasi penolakan di sisi server atau pemeriksaan lain belum ditentukan.

Script `/js/toefl-jquery-ujian.js` juga memuat alur fullscreen, hitung mundur dengan waktu server, pemuatan soal dan penyimpanan jawaban. Script memperingatkan bahwa keluar dari fullscreen untuk kedua kali dianggap pelanggaran. Keberadaan kode ini bukan bukti bahwa seluruh alur itu telah dijalankan atau bahwa backend benar-benar mencatat pelanggaran. Endpoint soal, jawaban dan waktu ujian tidak dipanggil dalam pemeriksaan.

## Dampak pada aplikasi Android

SSO dan pembacaan informasi dashboard/jadwal dapat diintegrasikan secara teknis. Dukungan mengerjakan ujian resmi di Android belum terbukti: portal yang diamati membatasi perangkat berdasarkan lebar layar dan memuat aturan fullscreen. Integrasi pengerjaan perlu dukungan perangkat dari pengelola BSI dan harus mempertahankan aturan ujian, waktu server serta konfirmasi penyimpanan jawaban.

Proyek saat ini memang belum mengaktifkan portal ujian: `MainActivity.openLink` menangani `/halaman-ujian` dengan pesan layanan belum diaktifkan, dan menu layanan menyaring tautan tersebut. Tidak ada dukungan pengerjaan ujian yang ditambahkan dari pemeriksaan ini.

## Bukti dan cakupan

- Metadata redirect, teks halaman yang disunting, serta script terpilih tersimpan lokal di `build/exam-inspection`, yang diabaikan Git. Tidak ada nilai cookie, kredensial atau token SSO dalam catatan ini.
- SHA-256 `jquery-right.js`: `8b4d642ac367e64e5db4cad53ada8db53a9d194aa7533537c7064e2854a6745c`.
- SHA-256 `toefl-jquery-ujian.js`: `9c37810f323a3cb918870131d8f66d2bb07bf15cccbdc0c3b181da387e281461`.
- REA menganalisis empat script terpilih sebagai teks, tanpa eksekusi: Evidence `ev_2712aabf81fed76c72f4e6d461f334636ce5cca5d8ee36f944d8c35797da8c71`, summary `ev_029fe1d6a63df457fc3cb1dcce3c0c77b245402e4d254ca6c493af93b879153b`. Cakupan graph parsial; script/vendor di luar pilihan dan perilaku server tetap belum diverifikasi.
- HP tidak terdeteksi pada awal pemeriksaan ini. Peringatan Android disimpulkan dari kode yang benar-benar dikirim portal, belum direproduksi di browser HP pada sesi ini.
