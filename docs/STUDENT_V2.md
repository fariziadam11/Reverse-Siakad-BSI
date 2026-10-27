# Port Student V2

Pemeriksaan baca saja pada 26–27 Oktober 2026 terhadap `https://students.bsi.ac.id/`, memakai akun yang diberikan pengguna. Identitas, password, cookie, CSRF dan token SSO aktual tidak dimasukkan ke source atau fixture. HTML dan screenshot pemeriksaan berada di direktori build yang diabaikan.

## Alur yang diamati

- GET `/login` memuat `_token`, `username`, `password`, dan Remember me. Login yang diamati tidak meminta CAPTCHA. Kontrol CAPTCHA bersyarat tetap dirender untuk diisi pengguna jika dikirim server.
- POST login mengalihkan ke `/dashboard`, kemudian `/mahasiswa/beranda`. Keberhasilan diperiksa melalui identitas sidebar; redirect saja tidak dianggap bukti login.
- Jika percobaan MyLMS dilakukan sebelum login Student, server dapat menyimpan URL itu sebagai tujuan login. Setelah login, redirect SSO eksternal dari tujuan tersebut tidak dianggap kegagalan login: klien memeriksa beranda Student dengan cookie yang baru. Tujuan eksternal tidak diikuti oleh transport login Student.
- Menu berasal dari `.app-sidebar`, isi halaman dari `.app-content`. Profil menggunakan kontrol readonly tanpa nama; KRS memiliki header tabel bertingkat dan KHS memiliki baris total dengan colspan. Parser memetakan struktur tersebut ke native views.
- Nilai tertentu serta cuti mengembalikan HTTP 200 dengan pesan SweetAlert tentang ketersediaan atau hak akun. Pesan ditampilkan sebagai teks tanpa menjalankan JavaScript atau melewati batas server.
- KHS semester memakai GET `semester` dari pilihan server. Tautan PDF mengalir lewat klien terautentikasi; halaman cetak KRS dan jadwal yang berupa HTML ditampilkan native.
- Jadwal dosen memakai kode persis dari `data-kd`, kemudian GET `/get-jadwal-dosen/{kode}`. Respons yang diperiksa berupa array dengan `kd_mtk`, `nm_mtk`, `sks`, `hari`, `jam`, `ruang`. Daftar besar memiliki pencarian dan pemuatan baris bertahap.
- Informasi PKL berasal dari URL `data-url` di beranda. Akun yang diperiksa menerima HTTP 204, sehingga placeholder modal tidak ditampilkan. Data JSON yang tersedia ditampilkan sesuai field server.
- Form surat riset mengisi rentang bulan berdasarkan jenis surat dan mewajibkan judul untuk izin riset. Anggota pertama berasal dari form akun; anggota tambahan dicari berdasarkan NIM yang dimasukkan pengguna. Field array dikirim berulang, bukan digabung atau diganti namanya. Surat tugas satu hari memakai jam; kegiatan beberapa hari memakai tanggal akhir. Native date/time picker membantu input.
- Form perubahan biodata, verifikasi diri, surat, DigiSAYS, kritik/saran dan password mempertahankan nama field, hidden context, pilihan serta aturan file dari halaman server. Pengiriman memerlukan konfirmasi. Keberhasilan proses bisnis di BSI belum diverifikasi karena pengujian tidak mengirim aplikasi tersebut.

## SSO dan sesi

GET Student `/mahasiswa/myLMS` mengembalikan tujuan resmi `https://elearning.bsi.ac.id/authenticate-student` dengan token opaque. Klien menerima hanya origin dan path itu, memakai cookie e-learning terpisah, lalu memverifikasi `/user/dashboard`. HTTP langsung dan uji Android berhasil memperoleh dashboard tanpa CAPTCHA. Token tidak disimpan atau dicatat; cookie Student tidak diteruskan ke origin lain.

Sesi Student yang masih aktif dapat memulihkan MyBest. Ini tidak memperpanjang sesi yang sudah dicabut server. Ketika Student juga berakhir, pengguna perlu login. Logout satu portal tidak menghapus sesi portal lain; logout MyBest yang disengaja tidak langsung dibatalkan oleh SSO otomatis.

## Verifikasi

- Uji JVM menjalankan klien HTTP asli terhadap fixture sintetis: login/rotasi cookie, GET semester, HTML 422, unggahan PDF biner, 419 tanpa kehilangan autentikasi, penolakan replay POST, redirect eksternal, unduhan, logout, SSO dan lookup JSON.
- `Student V2 Contract` diturunkan dari spesifikasi untuk dokumentasi manual. Auth hasil generator disesuaikan menjadi header Cookie melalui variabel sesi; jangan memakai header API key yang dibuat generator untuk skema cookie. Collection `Student V2 Checks` adalah suite sintetis lokal yang dapat dijalankan ulang; tidak ada collection yang dikirim ke akun produksi atau dipublikasikan.
- `StudentResearchFormTest` memakai data sintetis untuk memeriksa validasi judul, konversi nama field anggota berulang, durasi, serta pengecualian kontrol yang tidak aktif. Callback pengiriman tidak menghubungi BSI.
- `StudentV2SmokeTest` bersifat opt-in. Run pertama berhasil membaca KRS, semester, profil, pesan nilai, rotasi Activity dan login MyBest melalui SSO dari emulator. Pemeriksaan lanjutan juga mencakup jadwal dosen dan validasi kosong surat riset. Lihat hasil terbaru di `VERIFICATION.md`.
- HAR metadata dua pembacaan JSON diperiksa REA sebagai `ev_f1b95039e4e8e636692029de4080a1e60f10ccf1d11fef37dcf67a84fc6db2de`. Rekaman tersebut menyimpan metadata status/URL tanpa header autentikasi atau isi respons; ini bukan bukti implementasi server atau atribusi browser.

Perubahan HTML dapat memerlukan penyesuaian parser. Layanan eksternal tetap membuka aplikasi tujuan dari tautan yang ditawarkan BSI. Pembatasan akun tetap mengikuti server; semua kondisi akun, proses pengajuan, dan kebijakan TTL server belum dipastikan.
