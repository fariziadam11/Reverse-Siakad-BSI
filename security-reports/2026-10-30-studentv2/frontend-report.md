# Security assessment (Non-Exploit Mode)

Records: 7
Confirmed: 0; Indications: 4; False positives: 3

## Coverage and limitations

- Non-exploit assessment; no active scanning or exploit validation performed by this report generator.
- Scanner matches are indications unless independently validated; false positives are retained separately from valid findings.
- Raw HTTP bodies, secrets, and scanner snippets are omitted. Manual secret review is required.
- Target hanya Student V2 di https://students.bsi.ac.id. Scope yang disetujui: GET/POST /login dan GET /mahasiswa/beranda, HTTPS, tanpa redirect otomatis, payload, akun lain, atau perubahan data akademik. Tidak mengakses Elearning pada tahap ini.
- Login normal terverifikasi: GET /login 200, POST /login 302 ke /dashboard, lalu GET langsung ke endpoint beranda yang sudah diotorisasi menghasilkan 200 dengan elemen profil mahasiswa dan tanpa form login. /dashboard tidak diikuti atau diakses. Penanda profil adalah pemeriksaan struktur DOM, bukan ekspor identitas.
- Tahap ini mengirim tiga request; hitungan gabungan tahap login adalah enam dari maksimum dua puluh. Semua berurutan, 1 request/detik, timeout 10 detik, tanpa retry. Waktu observasi tercatat pada capture.har.
- Kontrol positif: cookie berindikasi sesi memakai Secure, HttpOnly, SameSite=Lax; cookie lainnya juga Secure dan SameSite=Lax. Cache-Control no-cache, private dan X-Content-Type-Options nosniff terlihat pada login dan beranda. Ini bukan audit kebijakan cache menyeluruh atau pembuktian pencegahan CSRF.
- Form login memiliki hidden field \_token. Validasi CSRF server, rotasi cookie saat login, masa hidup sesi, dan invalidasi logout tidak diuji. Cookie tanpa HttpOnly tidak otomatis dianggap celah karena perannya belum dibuktikan.
- REA inspect\_web\_network\_capture dan inspect\_analysis\_view memeriksa tiga record HAR tersanitasi secara offline tanpa mengambil ulang URL. Evidence \[REDACTED\]; SHA-256 capture \[REDACTED\]. Bukti lengkap ada pada rea-evidence.json.
- HAR dibuat observer stdlib, bukan rekaman browser. Header request, body POST/response, cookie, query redirect, dan fragment sengaja dihilangkan; versi HTTP serta fase timing tidak diketahui. REA mempertahankan keterbatasan atribusi historis dan bukan bukti independen deployment.
- Kode lokal StudentPortalClient.kt:65-81 menjadi petunjuk untuk memeriksa beranda langsung setelah respons redirect. Graph codebase-memory dan coverage dua file terkait diperiksa; source klien bukan implementasi backend.
- Referensi script pada HTML diinventarisasi dalam observations.json tanpa query, tidak diunduh atau dieksekusi. Tidak mengakses CDN, API internal, /dashboard, SSO, atau URL lain di luar scope. Tidak ada dekompilasi JavaScript karena byte script belum tersedia.
- SQLi, XSS, IDOR, broken access control, upload, endpoint akademik lain, dan keamanan dependency belum dinilai. Tidak ada eksploitasi, fuzzing, brute force, perubahan data, atau scan aktif. ZAP/Nuclei/Semgrep tidak dijalankan pada target ini.
- Tiga temuan merupakan indikasi hardening Low. Tidak ada kerentanan eksploitabel yang terkonfirmasi dan tidak ada false positive yang telah dibuktikan. Kehadiran kontrol dan keberhasilan login dilaporkan sebagai observasi, bukan kerentanan.
- Kredensial sementara dihapus dalam finally; cookie jar hanya berada dalam memori proses yang sudah berakhir. Tidak memanggil logout server karena di luar scope. Tidak menyimpan raw body, nama/NIM, token CSRF, atau nilai cookie dalam artefak laporan.
- Pembaruan frontend: delapan GET ke delapan URL exact dalam frontend-scope.json, masing-masing 200. Tidak login ulang, tidak memakai cookie/kredensial, tidak mengikuti redirect, tidak mengakses CDN, dan tidak memanggil endpoint yang ditemukan. Manifest sumber dan SHA-256 ada di frontend-manifest.json.
- REA mem-parsing 8/8 file, total 87.978 byte, tanpa parse failure atau eksekusi JavaScript. Application inventory complete dalam delapan file ini; semantic coverage unknown dengan 2.843 unknown. Angka 42 findings pada statistik REA adalah observasi ekstraksi, bukan 42 kerentanan.
- Semgrep CE memakai image terpasang, network none, source mount read-only, local rules saja. Hasil akhir memindai 8/8 file dengan 4 rule dan tanpa error. Satu file minified awalnya terlewat oleh filter default lalu diperiksa melalui daftar target eksplisit. Tidak mengunduh rules/dependency atau memperbarui tools.
- Tiga match Semgrep berasal dari rule inventaris HTML sink dan sudah ditriase sebagai false positive untuk XSS pada assignment konstan/angka. Tidak ada match pada rule terbatas URL-to-HTML, URL-to-dynamic-code, dan password-key storage; ini bukan bukti ketiadaan seluruh jenis XSS atau penyimpanan sensitif.
- Kandidat Select2 berasal dari review manual dengan rujukan dokumentasi primer, terpisah dari hasil Semgrep. Contoh UI/template yang tidak dipakai dapat menghasilkan indikasi yang tidak berlaku. Keterjangkauan dan kontrol data masih harus diverifikasi sebelum menyebutnya kerentanan aktual.
- choices.js mengatur allowHTML pada beberapa komponen. Ini dicatat sebagai area review, tidak dihitung sebagai celah tersendiri karena versi/implementasi library, pemakaian selector, dan data tidak tepercaya belum tersedia. Tombol show-password hanya mengubah tipe input pada source yang ditinjau, bukan bukti kebocoran password.
- authentication-main.js yang diambil terutama mengatur tema/arah tampilan. Nama file tidak membuktikan adanya logika autentikasi backend di dalamnya. Delapan file ini tidak cukup untuk menilai API, SQLi, IDOR, broken access control, proses login server, atau seluruh dependency.
- Bukti REA lengkap dan source JavaScript disimpan pada direktori privat dengan ACL khusus pengguna. Ringkasan tanpa source berada pada frontend-evidence-summary.json; rule lokal disertakan sebagai frontend-semgrep-rules.yaml. Laporan hanya memuat lokasi dan bukti minimal yang ditinjau.
- Hasil gabungan: empat indikasi Low \(tiga header dari tahap sebelumnya dan satu kandidat template frontend\), nol kerentanan terkonfirmasi, serta tiga kandidat sink yang disingkirkan. Risiko yang berkaitan dengan CSP/framing tidak dijumlahkan.

## Header HSTS tidak terlihat pada login dan beranda mahasiswa

- Severity: Low
- Status: indication
- Source: curated/observer
- Location: GET https://students.bsi.ac.id/login ; GET https://students.bsi.ac.id/mahasiswa/beranda
- Evidence: GET /login dan GET /mahasiswa/beranda masing-masing HTTP 200, hsts\_present=false. HAR tersanitasi diproses offline oleh REA, Evidence \[REDACTED\]. Preload dan kebijakan domain induk tidak diperiksa.
- CWE: CWE-693 \(Protection Mechanism Failure; indikasi kontrol\)
- CVSS: Tidak tersedia: dampak eksploitasi dan metrik yang diperlukan belum terbukti.
- Impact: Kebijakan untuk memaksa HTTPS pada browser tidak terlihat pada kedua respons. Tidak membuktikan downgrade atau kebocoran sesi.
- Reproduction: Periksa observations.json dan capture.har yang tersanitasi. Tidak diperlukan payload atau akses akun lain.
- Mitigation: Evaluasi penerapan Strict-Transport-Security setelah memastikan kesiapan HTTPS; tinjau subdomain dan preload sebelum mengaktifkan kebijakan luas.
- Retest: Dengan scope yang masih berlaku atau diperbarui, verifikasi header pada endpoint yang sama setelah perubahan konfigurasi.

## Header CSP tidak terlihat pada login dan beranda mahasiswa

- Severity: Low
- Status: indication
- Source: curated/observer
- Location: GET https://students.bsi.ac.id/login ; GET https://students.bsi.ac.id/mahasiswa/beranda
- Evidence: csp\_present=false pada kedua GET. Halaman merujuk skrip lokal dan CDN eksternal, tetapi skrip tidak diunduh/dieksekusi. CSP meta, encoding output, dan efektivitas sanitasi belum dianalisis.
- CWE: CWE-693 \(Protection Mechanism Failure; indikasi kontrol\)
- CVSS: Tidak tersedia: dampak eksploitasi dan metrik yang diperlukan belum terbukti.
- Impact: Lapisan mitigasi injeksi konten melalui header tidak terlihat. Ini bukan bukti XSS atau dependency rentan.
- Reproduction: Periksa observations.json dan capture.har yang tersanitasi. Tidak diperlukan payload atau akses akun lain.
- Mitigation: Susun CSP sesuai sumber daya aktual, mulai dengan Report-Only dan inventaris dependensi. Pertahankan escaping output dan tinjau kebijakan sumber skrip.
- Retest: Dengan scope yang masih berlaku atau diperbarui, verifikasi header pada endpoint yang sama setelah perubahan konfigurasi.

## Proteksi framing melalui header tidak terlihat pada login dan beranda mahasiswa

- Severity: Low
- Status: indication
- Source: curated/observer
- Location: GET https://students.bsi.ac.id/login ; GET https://students.bsi.ac.id/mahasiswa/beranda
- Evidence: x\_frame\_options kosong, csp\_present=false, dan frame\_ancestors\_present=false pada kedua GET. Tidak mencoba iframe, klik, atau perubahan data.
- CWE: CWE-1021 \(Improper Restriction of Rendered UI Layers or Frames; indikasi\)
- CVSS: Tidak tersedia: dampak eksploitasi dan metrik yang diperlukan belum terbukti.
- Impact: Kontrol deklaratif terhadap embedding halaman tidak terlihat. Kelayakan dan dampak clickjacking belum diverifikasi; risiko ini berkaitan dengan temuan CSP dan tidak dijumlahkan.
- Reproduction: Periksa observations.json dan capture.har yang tersanitasi. Tidak diperlukan payload atau akses akun lain.
- Mitigation: Jika embedding tidak diperlukan, gunakan CSP frame-ancestors &\#x27;none&\#x27;; jika diperlukan, batasi ke origin yang disetujui. Pertimbangkan X-Frame-Options untuk kompatibilitas.
- Retest: Dengan scope yang masih berlaku atau diperbarui, verifikasi header pada endpoint yang sama setelah perubahan konfigurasi.

## Template Select2 merangkai data opsi menjadi HTML tanpa escaping yang terlihat

- Severity: Low
- Status: indication
- Source: curated/observer
- Location: https://students.bsi.ac.id/assets/js/select2.js ; baris 23-52 dan 108-138
- Evidence: formatState/selectClient menggabungkan state.text/client.text serta nilai opsi ke string markup lalu membentuk objek jQuery. Callback escapeMarkup pada dua konfigurasi mengembalikan argumennya tanpa perubahan. Pola yang sama berulang pada konfigurasi RTL. Review manual atas byte file yang diambil, didukung parsing REA; ini bukan temuan dari rule taint Semgrep. Referensi perilaku library: https://select2.org/dropdown/ dan https://select2.org/selections/ .
- CWE: CWE-79 \(Improper Neutralization of Input During Web Page Generation; kandidat source-to-sink\)
- CVSS: Tidak tersedia: kendali penyerang atas data, keterjangkauan template, dan dampak belum terbukti.
- Impact: Jika callback ini aktif dan data opsi dapat dipengaruhi pihak tidak tepercaya, data berpotensi diperlakukan sebagai HTML. Keberadaan selector pada halaman relevan, sumber data opsi, dan eksekusi browser belum diverifikasi; pola dapat berupa kode demo yang tidak aktif. Tidak ada XSS terkonfirmasi.
- Reproduction: Tinjau fungsi dan konfigurasi Select2 pada file tersimpan tanpa menjalankannya. Pada source aplikasi yang diotorisasi, petakan selector aktif dan asal data opsi. Tidak mengirim payload atau mengubah opsi di website produksi.
- Mitigation: Bangun elemen secara terpisah, isi label memakai textContent atau jQuery .text\(\), dan batasi nilai path gambar ke allowlist. Pertahankan escaping default untuk teks; jika markup diperlukan, gunakan sanitasi yang sesuai konteks.
- Retest: Gunakan fixture lokal terisolasi untuk memeriksa bahwa karakter khusus pada teks opsi ditampilkan sebagai teks. Verifikasi keterjangkauan callback sebelum menaikkan prioritas temuan.

## Penanda HTML sink custom.js:71 bukan bukti XSS pada alur yang ditinjau

- Severity: Informational
- Status: false-positive
- Source: curated/observer
- Location: https://students.bsi.ac.id/assets/js/custom.js ; baris 71
- Evidence: Rule studentv2.html-sink-review mendata sink HTML tanpa membuktikan taint. Nilai theme berasal dari array lokal nanoThemes yang berisi string tetap nano. Ruang lingkup penolakan hanya kandidat XSS pada assignment ini dan versi file tersimpan.
- CWE: CWE-79 \(kategori kandidat yang disingkirkan\)
- CVSS: Tidak berlaku untuk kandidat yang disingkirkan.
- Impact: Tidak ada alur input penyerang ke sink yang ditunjukkan oleh assignment ini.
- Mitigation: Tidak memerlukan perbaikan keamanan untuk alur konstan ini. textContent dapat dipakai untuk teks biasa bila kode diubah kemudian.
- Retest: Tinjau ulang jika sumber nilai berubah menjadi input pengguna atau data eksternal.

## Penanda HTML sink custom.js:153 bukan bukti XSS pada alur yang ditinjau

- Severity: Informational
- Status: false-positive
- Source: curated/observer
- Location: https://students.bsi.ac.id/assets/js/custom.js ; baris 153
- Evidence: Rule studentv2.html-sink-review mendata sink HTML tanpa membuktikan taint. Loop kedua juga mengambil theme dari array lokal nanoThemes yang sama, bukan input pengguna. Ruang lingkup penolakan hanya kandidat XSS pada assignment ini dan versi file tersimpan.
- CWE: CWE-79 \(kategori kandidat yang disingkirkan\)
- CVSS: Tidak berlaku untuk kandidat yang disingkirkan.
- Impact: Tidak ada alur input penyerang ke sink yang ditunjukkan oleh assignment ini.
- Mitigation: Tidak memerlukan perbaikan keamanan untuk alur konstan ini. textContent dapat dipakai untuk teks biasa bila kode diubah kemudian.
- Retest: Tinjau ulang jika sumber nilai berubah menjadi input pengguna atau data eksternal.

## Penanda HTML sink custom.js:314 bukan bukti XSS pada alur yang ditinjau

- Severity: Informational
- Status: false-positive
- Source: curated/observer
- Location: https://students.bsi.ac.id/assets/js/custom.js ; baris 314
- Evidence: Rule studentv2.html-sink-review mendata sink HTML tanpa membuktikan taint. Nilai yang dimasukkan adalah hasil numerik new Date\(\).getFullYear\(\), bukan teks dari pengguna. Ruang lingkup penolakan hanya kandidat XSS pada assignment ini dan versi file tersimpan.
- CWE: CWE-79 \(kategori kandidat yang disingkirkan\)
- CVSS: Tidak berlaku untuk kandidat yang disingkirkan.
- Impact: Tidak ada alur input penyerang ke sink yang ditunjukkan oleh assignment ini.
- Mitigation: Tidak memerlukan perbaikan keamanan untuk alur konstan ini. textContent dapat dipakai untuk teks biasa bila kode diubah kemudian.
- Retest: Tinjau ulang jika sumber nilai berubah menjadi input pengguna atau data eksternal.
