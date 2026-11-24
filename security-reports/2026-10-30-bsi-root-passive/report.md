# Security assessment (Non-Exploit Mode)

Records: 4
Confirmed: 0; Indications: 4; False positives: 0

## Coverage and limitations

- Non-exploit assessment; no active scanning or exploit validation performed by this report generator.
- Scanner matches are indications unless independently validated; false positives are retained separately from valid findings.
- Raw HTTP bodies, secrets, and scanner snippets are omitted. Manual secret review is required.
- Scope: GET https://elearning.bsi.ac.id/ dan GET https://students.bsi.ac.id/; lingkungan produksi; izin pengguna dicatat di scope.json. Maksimal dua request total, 1 request/detik, concurrency 1, timeout 10 detik, tanpa retry. Izin berlaku 15 menit sejak 2026-10-30T08:11:07.4836088Z.
- Pelaksanaan: masing-masing endpoint mengembalikan HTTP 200. Dua GET selesai, tanpa login, redirect, payload, atau perubahan data. Kredensial tidak digunakan dan tidak disimpan dalam artefak assessment ini.
- Keempat temuan adalah indikasi hardening berisiko rendah. Ketiadaan header teramati pada respons saat itu; tidak membuktikan kerentanan yang dapat dieksploitasi. Tidak ada kerentanan terkonfirmasi dan tidak ada false positive yang telah dibuktikan.
- SQL Injection, XSS, IDOR, broken access control, autentikasi, sesi login, cookie setelah login, dan keamanan REST API internal belum dinilai. Tidak tersedia source server atau trafik autentikasi dalam scope ini.
- Analisis hanya berlaku untuk dua URL tersebut; hubungan students.bsi.ac.id dengan aplikasi bernama studentv2 dan endpoint lainnya belum diverifikasi.
- Tidak menjalankan ZAP, Nuclei, atau Semgrep pada tahap ini: observer terbatas sudah menggunakan seluruh budget dua request. Tidak ada scan aktif, crawl, login, atau audit OWASP Top 10 secara menyeluruh.
- Laporan lama di direktori lain tidak dijadikan bukti baru atau izin tambahan. Hasil ini adalah snapshot, bukan pernyataan bahwa kedua website aman.

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
