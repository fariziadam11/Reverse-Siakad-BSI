# Security assessment (Non-Exploit Mode)

Records: 7
Confirmed: 0; Indications: 7; False positives: 0

## Coverage and limitations

- Non-exploit assessment; no active scanning or exploit validation performed by this report generator.
- Scanner matches are indications unless independently validated; false positives are retained separately from valid findings.
- Raw HTTP bodies, secrets, and scanner snippets are omitted. Manual secret review is required.
- Tahap lanjutan: izin login normal diberikan secara eksplisit pada percakapan; scope dicatat di login-scope.json. Periode dimulai 2026-10-30T08:16:20.4952278Z, berlaku 15 menit. Maksimum total 20 request, 1 request/detik, concurrency 1, timeout 10 detik, tanpa retry/redirect otomatis.
- Trafik tahap ini: tiga request total, yaitu GET /login Elearning \(200\), GET /login Students \(200\), dan satu POST /login Students \(302\). Tidak ada request dashboard. Dua GET root berasal dari tahap sebelumnya dan diberi endpoint masing-masing.
- Elearning: CAPTCHA terdeteksi pada formulir; tidak mengirim kredensial atau POST ke portal ini. Tidak mencoba menjawab atau melewati CAPTCHA.
- Students: POST login menghasilkan redirect di luar allowlist; tidak diikuti. Ini bukan bukti sukses/gagal autentikasi atau open redirect. Tidak ada percobaan ulang, akses dashboard, atau pengujian akun lain.
- Kontrol teramati: Cache-Control no-cache, private pada kedua login; hidden \_token pada kedua form \(efektivitas CSRF backend belum diuji\); cookie Students memakai Secure dan SameSite; satu cookie tiap portal memakai HttpOnly. Cookie tanpa HttpOnly belum dianggap celah karena fungsinya belum diketahui.
- CSP hadir pada login Elearning tetapi isinya belum dianalisis; tidak adanya X-Frame-Options saja tidak dinyatakan celah framing karena CSP mungkin menyediakan frame-ancestors.
- Semua temuan adalah indikasi kontrol/hardening berisiko Low, bukan eksploitasi terkonfirmasi. Tidak ada false positive yang telah dibuktikan. Temuan CSP dan framing Students terkait respons yang sama, sehingga risikonya tidak dijumlahkan.
- SQLi, XSS, IDOR, broken access control, sesi sesudah login, rotasi sesi, logout, dan REST API internal belum dinilai. Tidak menjalankan ZAP/Nuclei/Semgrep atau active scan; observasi terbatas dipakai untuk menjaga scope. Source backend tidak tersedia.
- Kredensial sementara digunakan melalui berkas lokal dengan ACL khusus pengguna, bukan argumen CLI, lalu berkas dihapus. Laporan tidak memuat username/password, nama/nilai cookie, token CSRF, raw body, atau data akademik.
- State lokal, cookie jar, dan fragmen CAPTCHA tahap ini dihapus sesudah pemeriksaan. Sesi server tidak dicabut melalui logout karena endpoint itu di luar scope. Ini bukan penghapusan forensik atau jaminan pencabutan sesi server.
- Bukti lama dari direktori 2026-10-30-bsi hanya menjadi petunjuk awal dan bukan sumber otorisasi baru. Kesimpulan laporan menggunakan observasi root tahap sebelumnya dan login tahap ini.

## Header X-Content-Type-Options tidak ditemukan

- Severity: Low
- Status: indication
- Source: curated/observer
- Location: GET https://elearning.bsi.ac.id/
- Evidence: Respons tidak menyertakan X-Content-Type-Options. Ketiadaan header saja tidak membuktikan MIME sniffing yang dapat dieksploitasi. Bukti: observasi satu GET berstatus HTTP 200 pada 2026-10-30T08:11:07.4836088Z; tanpa redirect dan tanpa autentikasi.
- CWE: CWE-693 \(Protection Mechanism Failure; klasifikasi kontrol yang perlu ditinjau\)
- CVSS: Tidak tersedia: dampak dan metrik eksploitasi belum dapat ditentukan.
- Impact: Potensi berkurangnya perlindungan MIME sniffing; dampak bergantung pada tipe konten dan cara browser memakainya.
- Reproduction: Tinjau keberadaan header pada bukti observasi yang tersimpan. Jangan mengirim request tambahan di luar scope.
- Mitigation: Tambahkan X-Content-Type-Options: nosniff dan pastikan Content-Type sesuai pada setiap respons.
- Retest: Setelah perbaikan dan otorisasi baru, periksa header pada endpoint serta konteks konten yang sama.

## Header HSTS tidak ditemukan

- Severity: Low
- Status: indication
- Source: curated/observer
- Location: GET https://elearning.bsi.ac.id/
- Evidence: Respons HTTPS tidak menyertakan Strict-Transport-Security. Status preload dan kebijakan subdomain tidak diperiksa; serangan downgrade tidak diuji. Bukti: observasi satu GET berstatus HTTP 200 pada 2026-10-30T08:11:07.4836088Z; tanpa redirect dan tanpa autentikasi.
- CWE: CWE-693 \(Protection Mechanism Failure; klasifikasi kontrol yang perlu ditinjau\)
- CVSS: Tidak tersedia: dampak dan metrik eksploitasi belum dapat ditentukan.
- Impact: Perlindungan browser untuk kunjungan berikutnya tidak dapat dibuktikan dari respons ini; downgrade atau kebocoran kredensial tidak terbukti.
- Reproduction: Tinjau keberadaan header pada bukti observasi yang tersimpan. Jangan mengirim request tambahan di luar scope.
- Mitigation: Terapkan Strict-Transport-Security setelah memastikan seluruh layanan terkait siap HTTPS. Evaluasi includeSubDomains dan preload sebelum mengaktifkannya.
- Retest: Setelah perbaikan dan otorisasi baru, periksa header pada endpoint serta konteks konten yang sama.

## Header Content-Security-Policy tidak ditemukan

- Severity: Low
- Status: indication
- Source: curated/observer
- Location: GET https://students.bsi.ac.id/
- Evidence: Respons dokumen HTML tidak menyertakan header Content-Security-Policy. CSP melalui meta tag tidak diperiksa karena body tidak disimpan; XSS tidak diuji. Bukti: observasi satu GET berstatus HTTP 200 pada 2026-10-30T08:11:07.4836088Z; tanpa redirect dan tanpa autentikasi.
- CWE: CWE-693 \(Protection Mechanism Failure; klasifikasi kontrol yang perlu ditinjau\)
- CVSS: Tidak tersedia: dampak dan metrik eksploitasi belum dapat ditentukan.
- Impact: Lapisan mitigasi injeksi konten melalui header tidak terlihat. Ini bukan bukti adanya XSS.
- Reproduction: Tinjau keberadaan header pada bukti observasi yang tersimpan. Jangan mengirim request tambahan di luar scope.
- Mitigation: Tinjau CSP yang mungkin tersedia melalui meta tag dan terapkan kebijakan header sesuai sumber konten aplikasi; evaluasi terlebih dahulu dengan Report-Only.
- Retest: Setelah perbaikan dan otorisasi baru, periksa header pada endpoint serta konteks konten yang sama.

## Header HSTS tidak ditemukan

- Severity: Low
- Status: indication
- Source: curated/observer
- Location: GET https://students.bsi.ac.id/
- Evidence: Respons HTTPS tidak menyertakan Strict-Transport-Security. Status preload dan kebijakan subdomain tidak diperiksa; serangan downgrade tidak diuji. Bukti: observasi satu GET berstatus HTTP 200 pada 2026-10-30T08:11:07.4836088Z; tanpa redirect dan tanpa autentikasi.
- CWE: CWE-693 \(Protection Mechanism Failure; klasifikasi kontrol yang perlu ditinjau\)
- CVSS: Tidak tersedia: dampak dan metrik eksploitasi belum dapat ditentukan.
- Impact: Perlindungan browser untuk kunjungan berikutnya tidak dapat dibuktikan dari respons ini; downgrade atau kebocoran kredensial tidak terbukti.
- Reproduction: Tinjau keberadaan header pada bukti observasi yang tersimpan. Jangan mengirim request tambahan di luar scope.
- Mitigation: Terapkan Strict-Transport-Security setelah memastikan seluruh layanan terkait siap HTTPS. Evaluasi includeSubDomains dan preload sebelum mengaktifkannya.
- Retest: Setelah perbaikan dan otorisasi baru, periksa header pada endpoint serta konteks konten yang sama.

## Cookie login Elearning teramati tanpa atribut Secure

- Severity: Low
- Status: indication
- Source: curated/observer
- Location: GET https://elearning.bsi.ac.id/login
- Evidence: GET /login mengembalikan HTTP 200. Dua Set-Cookie diringkas dengan Secure=false, SameSite hadir pada keduanya, dan HttpOnly hadir pada satu cookie. Nama/nilai cookie tidak disertakan. Fungsi cookie dan HSTS preload belum diketahui.
- CWE: CWE-614 \(Sensitive Cookie in HTTPS Session Without Secure Attribute; sensitivitas cookie belum diverifikasi\)
- CVSS: Tidak tersedia: dampak dan metrik belum terbukti.
- Impact: Jika cookie tersebut membawa sesi atau informasi sensitif, ketiadaan Secure berpotensi memperluas pengirimannya melalui HTTP. Pengiriman HTTP dan kebocoran sesi tidak diuji atau dibuktikan.
- Reproduction: Tinjau atribut yang diringkas di login-observations.json. Tidak diperlukan payload atau percobaan login tambahan.
- Mitigation: Identifikasi fungsi tiap cookie; beri Secure pada cookie sesi dan cookie sensitif di layanan HTTPS. Tinjau konfigurasi HTTPS/proxy serta HSTS. Jangan mewajibkan HttpOnly untuk cookie yang memang perlu dibaca JavaScript, misalnya token CSRF tertentu.
- Retest: Dengan scope baru, verifikasi header/cookie sesuai fungsi aktual dan uji regresi alur login sesudah perbaikan.

## Header CSP tidak terlihat pada login Students

- Severity: Low
- Status: indication
- Source: curated/observer
- Location: GET https://students.bsi.ac.id/login
- Evidence: GET /login berstatus HTTP 200 dan csp\_header\_present=false. HTML tidak disimpan; CSP meta dan encoding kontekstual tidak ditinjau. Tidak ada payload XSS.
- CWE: CWE-693 \(Protection Mechanism Failure; kontrol tambahan perlu ditinjau\)
- CVSS: Tidak tersedia: dampak dan metrik belum terbukti.
- Impact: Lapisan pembatasan sumber konten melalui header tidak terlihat. Ini tidak membuktikan adanya XSS.
- Reproduction: Tinjau atribut yang diringkas di login-observations.json. Tidak diperlukan payload atau percobaan login tambahan.
- Mitigation: Evaluasi CSP yang sesuai dependensi dan sumber daya login, mulai dari Report-Only; pertahankan escaping output dan validasi input.
- Retest: Dengan scope baru, verifikasi header/cookie sesuai fungsi aktual dan uji regresi alur login sesudah perbaikan.

## Proteksi framing melalui header tidak terlihat pada login Students

- Severity: Low
- Status: indication
- Source: curated/observer
- Location: GET https://students.bsi.ac.id/login
- Evidence: Respons GET /login HTTP 200 mencatat framing\_header\_present=false dan csp\_header\_present=false. Tidak ada pengujian iframe atau interaksi pengguna.
- CWE: CWE-1021 \(Improper Restriction of Rendered UI Layers or Frames; indikasi belum diuji\)
- CVSS: Tidak tersedia: dampak dan metrik belum terbukti.
- Impact: Proteksi deklaratif X-Frame-Options atau CSP frame-ancestors tidak terlihat; kelayakan dan dampak clickjacking belum terbukti.
- Reproduction: Tinjau atribut yang diringkas di login-observations.json. Tidak diperlukan payload atau percobaan login tambahan.
- Mitigation: Jika halaman tidak perlu di-embed, gunakan CSP frame-ancestors &\#x27;none&\#x27; atau kebijakan asal yang sesuai; pertimbangkan X-Frame-Options untuk kompatibilitas.
- Retest: Dengan scope baru, verifikasi header/cookie sesuai fungsi aktual dan uji regresi alur login sesudah perbaikan.
