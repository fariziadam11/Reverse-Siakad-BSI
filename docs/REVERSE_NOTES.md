# Observasi MyBest BSI

Tanggal: 26 Oktober 2026. Target: https://elearning.bsi.ac.id/. Pemeriksaan menggunakan akun yang disediakan pengguna. Kredensial, cookie, token SSO dan CSRF aktual tidak disimpan dalam catatan ini.

| Perilaku | Bukti dan penerapan |
| --- | --- |
| Login | GET `/login` mengandung hidden `_token`, `username`, `password`, `captcha_answer`, dan SVG di `#captcha-svg-container`. POST memakai URL-encoded form dan cookie dari GET. |
| Cookie | Nama cookie yang diamati: `mybest_session`, `XSRF-TOKEN`. Native dan WebView menggunakan Android CookieManager yang sama, termasuk cookie hasil rotasi. |
| CAPTCHA | Tiga karakter, dapat diganti melalui GET `/reload-captcha` dengan JSON `svg`. Tombol native memuat ulang form agar token dan gambar tetap berasal dari sesi terkini. Penyelesaian otomatis hanya ada di test live opt-in setelah persetujuan pengguna. |
| Dashboard | Tujuan mahasiswa yang berhasil dibaca: `/user/dashboard`. `/dashboard` melewati redirect legacy menuju login; satu redirect teramati menggunakan skema HTTP. Adapter meningkatkan URL tersebut menjadi HTTPS, mempertahankan pemeriksaan origin dan TLS. |
| Jadwal | GET `/sch`, elemen `.jadwal-card`, `.jadwal-title`, `.jadwal-info-row`, `.jadwal-footer`. Terdapat tautan kelas, diskusi, materi dan tugas dengan ID terenkripsi. |
| Materi | `/learning/{opaqueId}` memuat tabel tambahan, iframe video dan `.doc-block` untuk Silabus/RPS/Slide/Modul/RTM. Sebagian unduhan menuju `students.bsi.ac.id`. |
| Tugas dan nilai | `/assignment/{opaqueId}` menggunakan tabel pada tab Data Penugasan dan Data Nilai Tugas. HTML legacy yang tidak seimbang tetap diproses dengan parser HTML. POST-only download dan pengiriman memakai halaman resmi. |
| Kelas/diskusi | Tautan `/absen-mhs/{opaqueId}` dan `/form-diskusimhs/{opaqueId}` berasal dari jadwal. Halaman kelas yang diperiksa menampilkan status Belum Mulai. Tidak mengirim absensi atau pesan. |
| Layanan lain | Menu akun yang diamati: Student V2, Profil, Ujian, TOEFL, Jadwal Kuliah, Kuliah Pengganti dan Kuis. Student V2 dan Ujian mengarah ke layanan lain dengan token SSO yang tidak disimpan dalam source. |
| Keluar | Form POST `/logout` dengan `_token` teramati. Status remote logout tidak dipastikan; perilaku adapter diuji melalui simulasi. |

Observasi browser awal direkam oleh REA sebagai `ev_761d57ee0b7ae3db416d3dc0a7a670ab0c897e58de2074524131a587fa60c7ac`. Ini membuktikan DOM dan tampilan login saat perekaman, bukan isi backend. Uji live Android kemudian berhasil menggunakan jaringan asli dan memeriksa layar native, sebagaimana dicatat di `VERIFICATION.md`.

Aturan sesi, TTL cookie, kebijakan CAPTCHA dan seluruh respons error bukan kontrak publik yang dijamin. Penanganan 401, 419, 5xx, timeout dan redirect merupakan keputusan defensif klien; variasi tersebut diuji lokal, tidak sengaja dipicu terhadap akun produksi.
