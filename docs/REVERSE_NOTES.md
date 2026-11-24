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

## Diskusi per pertemuan, 30 Oktober 2026

Pemeriksaan baca-saja terhadap halaman Pancasila yang diberikan pengguna, setelah login dan penyelesaian CAPTCHA yang diizinkan. Pertemuan 5 menampilkan 83 komentar; jumlah elemen `.chat-box > li` sesuai dengan angka pada tautan komentar. Tidak ada komentar atau form akademik yang dikirim.

- Tautan tiap pertemuan membuka GET `/form-komentarmhs/{opaqueId}`. Judul pertemuan berada pada `.active-user-info h5`, topik pada `.active-user-info .typing`.
- Setiap komentar memuat identitas pada `.chat-avatar .chat-name`, nama pada `.chat-text > .chat-name`, isi pada `.chat-text p`, dan waktu pada `.chat-hour`. Identitas mencakup NIM mahasiswa dan NIP dosen. Urutan server dipertahankan, termasuk komentar dengan isi yang sama.
- Form komentar memakai POST `/send-komentarmhs`, textarea `komentar`, serta hidden `_token`, `id_chat`, dan `id`. Respons pengiriman belum diverifikasi. Klien mempertahankan form server dan konfirmasi pengiriman yang sudah ada; pemeriksaan komentar kosong merupakan validasi lokal.
- Parser sebelumnya mengambil paragraf dan menghilangkan identitas di elemen `div`; `distinct()` juga menggabungkan pesan yang sama. Kini komentar memiliki data terpisah dan ditampilkan per entri. Form komentar berada di luar daftar gulir, dengan tinggi terbatas dan penanganan inset keyboard yang sudah ada. Form lain mengikuti tata letak sebelumnya.

REA merekam halaman daftar diskusi sebagai `ev_e2989c4be775b3aae252ffd58a54c608e96281e0f2b8dd71164091b04cf7a806` dan halaman komentar sebagai `ev_24fa2dab6565e9156ae92ae18749b93c8523dfc90b403a22896674f9b212c631`. Inspeksi pasif ini membuktikan DOM/accessibility saat pengamatan, tidak membuktikan backend atau respons tulis. Pemuatan dan login dilakukan melalui browser terkontrol; inspeksi elemen terarah melengkapi bukti REA. Kredensial, cookie, dan token aktual tidak disalin ke catatan atau fixture.

Verifikasi lokal: `testDebugUnitTest`, `assembleDebug`, dan `assembleDebugAndroidTest` berhasil. Sebanyak 30 unit test lulus; 30 test simulasi/transport opt-in dilewati tanpa server simulasi. Regresi sintetis memeriksa nama/NIM/NIP, pesan yang sama, urutan komentar, konteks pertemuan, hidden form, serta pemisahan draf antarpertemuan. UI gulir/keyboard belum dijalankan pada Android karena tidak ada perangkat atau AVD tersedia dalam sesi ini. Warna teks dan tombol yang dipakai kembali lulus pemeriksaan kontras: ink/putih 17,85:1, muted/putih 7,58:1, putih/navy 10,36:1.

### Pemeriksaan lanjutan pada HP

Setelah HP Android model I2218 terhubung, screenshot awal membuktikan daftar judul pertemuan terpisah dari tautan komentarnya, lampiran terkumpul di bawah, dan judul halaman komentar berulang. Parser kini mengelompokkan `.task-block` menjadi topik dengan deskripsi, penulis, waktu, unduhan dan tautan komentarnya sendiri. Form konteks tanpa tombol pada task block tidak lagi menjadi tombol kirim terpisah. Fixture sintetis `discussion-topics.html` memeriksa agar unduhan dan tautan pertemuan tidak tercampur atau diduplikasi.

Kolom komentar dan tombol Kirim kini sejajar. Navigasi bawah disembunyikan selama keyboard terbuka pada halaman komentar dan kembali setelah keyboard ditutup. Baris baru dari teks komentar dipertahankan. Penerapan tetap memakai arah visual DESIGN.md, ENERGY 1 / RHYTHM 1 / MOTION 1, serta komponen, warna dan alur konfirmasi yang sudah ada.

- PASS pengelompokan konten: halaman Pancasila asli diperiksa pada HP; Pertemuan 6 dan 5 masing-masing menampilkan topik, dosen, tanggal, lampiran dan tombol komentar dalam kelompoknya sendiri.
- PASS input dan gulir: `DiscussionLayoutTest` lulus pada HP, membuktikan posisi composer tetap saat daftar digulir, validasi kosong, keyboard, tombol Kirim yang terlihat, serta navigasi bawah hilang/muncul mengikuti keyboard. Test hanya memakai draf sintetis dan tidak mengirim komentar.
- PASS keadaan kosong: halaman asli Pertemuan 6 menampilkan topik, pesan belum ada komentar dan composer. Pertemuan 5 menampilkan nama/NIM/NIP beserta waktu dan isi komentar.
- PASS build/regresi: 31 unit test lulus, 30 test transport/simulasi opt-in tetap dilewati; build aplikasi dan APK test berhasil. APK akhir dipasang ke HP dengan mode replace untuk mempertahankan data aplikasi.

Bukti visual lokal berada di `build/verification-discussion/`: `before`, `before-comments`, `before-keyboard`, `after-topics`, `after-comments`, `after-scroll-keyboard`, `after-empty`, dan `final-formatting`. Screenshot hanya untuk pemeriksaan lokal dan tidak dilacak Git. Pengujian ini melengkapi keterbatasan pemeriksaan sebelumnya; pengiriman komentar produksi tetap tidak diuji.
