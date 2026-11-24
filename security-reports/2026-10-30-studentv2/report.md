# Security assessment (Non-Exploit Mode)

Records: 3
Confirmed: 0; Indications: 3; False positives: 0

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
