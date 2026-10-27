package com.gis.reverse

import com.gis.reverse.data.PortalError
import com.gis.reverse.data.PortalParser
import com.gis.reverse.data.StudentPortalParser
import org.junit.Assert.*
import org.junit.Test

class StudentPortalParserTest {
    @Test fun radioChoicesUseTheirOwnLabelsBeforeTheGroupHeading() {
        val doc = PortalParser.document("""<main><form method=post action=/mahasiswa/surat-riset/template>
            <div class=form-group><label>Jenis Pengajuan</label>
            <div><input id=solo type=radio name=jenis_pelaksanaan value=Mandiri><label for=solo>Mandiri</label></div>
            <div><input id=group type=radio name=jenis_pelaksanaan value=Berkelompok><label for=group>Berkelompok</label></div>
            </div><button type=submit>Kirim</button></form></main>""", StudentPortalParser.ORIGIN)
        val field = StudentPortalParser.page(doc).forms.single().fields.single()
        assertEquals(listOf("Mandiri", "Berkelompok"), field.options.map { it.label })
        assertEquals(listOf("Mandiri", "Berkelompok"), field.options.map { it.value })
    }
    private fun document(body: String, path: String = "/mahasiswa/beranda") =
        PortalParser.document(body, StudentPortalParser.ORIGIN + path)

    @Test fun loginPreservesFormFieldsWithoutInventingACaptcha() {
        val doc = document("""<form method=post action=/login><input type=hidden name=_token value=TEST_CSRF>
            <input name=username required><input type=password name=password required>
            <label><input type=checkbox name=remember>Remember me</label><button type=submit>Sign In</button></form>""", "/login")
        assertTrue(StudentPortalParser.isLogin(doc))
        val challenge = StudentPortalParser.challenge(doc)
        assertEquals("https://students.bsi.ac.id/login", challenge.form.action)
        assertTrue(challenge.form.hidden.contains("_token" to "TEST_CSRF"))
        assertEquals(listOf("username", "password", "remember"), challenge.form.fields.map { it.name })
        assertEquals("", challenge.captchaPrompt)
        assertEquals("", challenge.captchaSvg)
        assertEquals("/login", java.net.URI(challenge.form.sourceUrl).path)
    }

    @Test fun conditionalCaptchaIsShownAsServerSuppliedQuestion() {
        val doc = document("""<div class=alert-danger>Periksa jawaban Anda.</div><form method=post action=/login>
            <input type=hidden name=_token value=TEST_CSRF><input name=username><input name=password type=password>
            <div class="form-group mb-3"><label for=captcha>Berapa 8 + 4?</label><input id=captcha name=captcha_answer required></div>
            <button type=submit>Sign In</button></form>""", "/login")
        val challenge = StudentPortalParser.challenge(doc)
        assertEquals("Berapa 8 + 4?", challenge.captchaPrompt)
        assertTrue(challenge.form.fields.any { it.name == "captcha_answer" && it.required })
        assertEquals("Periksa jawaban Anda.", challenge.error)
    }

    @Test(expected = PortalError::class) fun loginRequiresCsrfAndSameOriginAction() {
        StudentPortalParser.challenge(document("""<form method=post action=https://other.invalid/login>
            <input name=_token type=hidden value=SECRET><input name=username><input type=password name=password></form>""", "/login"))
    }

    @Test fun navigationUsesOnlyRealSidebarDestinationsAndKeepsOpaqueIds() {
        val doc = document("""<aside class=app-sidebar>
            <a class=side-menu__item href=/mahasiswa/krs><span class=side-menu__label>Kartu Rencana Studi</span></a>
            <a class=side-menu__item href=javascript:void(0)>Kategori</a><a class=side-menu__item href=#>Belum tersedia</a>
            <a class=side-menu__item href=/mahasiswa/krs>KRS duplikat</a>
            <a class=side-menu__item href=/mahasiswa/konseling-pa/periode/synthetic-opaque-id>Periode saat ini</a>
            <a class=side-menu__item href=https://other.invalid/steal>Luar portal</a></aside>
            <a href=/mahasiswa/not-a-menu>Bukan menu</a>""")
        val links = StudentPortalParser.navigation(doc)
        assertEquals(2, links.size)
        assertEquals("Kartu Rencana Studi", links.first().title)
        assertTrue(links.last().url.endsWith("synthetic-opaque-id"))
    }

    @Test fun profileValuesWithoutNamesBecomeFactsRatherThanEditableFields() {
        val doc = document("""<header><p>HEADER SHOULD NOT LEAK</p></header><div class="main-content app-content">
            <form><div class=row><label for=nim>NIM</label><div><input id=nim readonly value=TEST_NIM></div></div>
            <div class=row><label for=email>Email</label><input id=email readonly type=email value=student@example.invalid></div>
            <div class=row><label for=status>Status</label><select id=status disabled><option>Nonaktif</option><option selected>Aktif</option></select></div></form>
            <a href=/mahasiswa/perubahan-biodata>Ubah Biodata Diri</a></div><footer><p>FOOTER SHOULD NOT LEAK</p></footer>""", "/mahasiswa/data-pribadi")
        val page = StudentPortalParser.page(doc)
        assertEquals(listOf("NIM" to "TEST_NIM", "Email" to "student@example.invalid", "Status" to "Aktif"), page.facts)
        assertTrue(page.forms.isEmpty())
        assertEquals("Ubah Biodata Diri", page.links.single().title)
        assertTrue(page.paragraphs.none { it.contains("SHOULD NOT LEAK") })
    }

    @Test fun dashboardIdentityUsesSidebarAndProfileFacts() {
        val doc = document("""<aside><strong class=student-sidebar-profile__name>Mahasiswa Contoh</strong>
            <small class=student-sidebar-profile__nim>NIM: TEST_NIM</small></aside><div class=app-content>
            <div class=main-profile-social-list><div class=media-body><span>Email Terdaftar</span><a href=javascript:void(0)>student@example.invalid</a></div>
            <div class=media-body><span>Kelas</span><a href=javascript:void(0)>TEST_CLASS</a></div></div></div>""")
        val student = StudentPortalParser.student(doc)
        assertEquals("Mahasiswa Contoh", student.name)
        assertEquals("TEST_NIM", student.identifier)
        assertEquals("student@example.invalid", student.email)
        assertTrue(StudentPortalParser.page(doc).facts.contains("Kelas" to "TEST_CLASS"))
        assertFalse(StudentPortalParser.isLogin(doc))
    }

    @Test fun hiddenAndUnloadedModalFactsAreOmittedUntilDataIsAvailable() {
        val doc = document("""<div class=app-content>
            <div class=main-profile-social-list><div class=media-body><span>Program Studi</span><a>Program Contoh<span hidden>Do not show</span></a></div></div>
            <div hidden><dl><dt>Hidden fact</dt><dd>Do not show</dd></dl></div>
            <div class=d-none><label for=hidden-input>Hidden input</label><input id=hidden-input readonly value="Do not show"></div>
            <div class=d-none><div class=main-profile-social-list><div class=media-body><span>Hidden profile</span><a>Do not show</a></div></div></div>
            <div class=modal><dl><dt>Unloaded field</dt><dd data-pkl-field=example>-</dd></dl>
            <label for=unloaded-input>Unloaded input</label><input id=unloaded-input readonly value="-"></div>
            <div class=modal><dl><dt>Informasi terverifikasi</dt><dd>Konten dari server</dd></dl></div>
            </div>""")
        assertEquals(listOf("Program Studi" to "Program Contoh", "Informasi terverifikasi" to "Konten dari server"), StudentPortalParser.page(doc).facts)
    }

    @Test fun krsMultirowHeadersAndMergedTotalsRetainTheirMeaning() {
        val doc = document("""<div class=app-content><table><thead><tr><th rowspan=2>No</th><th rowspan=2>Kode</th>
            <th rowspan=2>Mata Kuliah</th><th rowspan=2>SKS</th><th colspan=2>Paraf</th></tr><tr><th>UTS</th><th>UAS</th></tr></thead>
            <tbody><tr><td>1</td><td>TEST</td><td>Mata Kuliah Contoh</td><td>3</td><td>Belum</td><td>Belum</td></tr>
            <tr><td colspan=3>Total SKS yang Diambil</td><td>3</td><td colspan=2></td></tr></tbody></table></div>""", "/mahasiswa/krs")
        val rows = StudentPortalParser.page(doc).tables.single().rows
        assertEquals(listOf("Kode", "Mata Kuliah", "SKS", "Paraf / UTS", "Paraf / UAS"), rows.first().fields.map { it.first })
        assertEquals(listOf("Total SKS yang Diambil" to "3"), rows.last().fields)
    }

    @Test fun lecturerScheduleButtonsBecomeLinksUsingExactServerCode() {
        val doc = document("""<div class=app-content><table><thead><tr><th>No</th><th>Kode Dosen</th><th>Nama Dosen</th><th>Aksi</th></tr></thead>
            <tbody><tr><td>1</td><td>Opaque /+? Code</td><td>Dosen Contoh</td><td>
            <button type=button class=lihat-jadwal data-kd="Opaque /+? Code">Lihat Jadwal</button></td></tr>
            <tr><td>2</td><td>CaseSensitive</td><td>Dosen Kedua</td><td>
            <button type=button class=lihat-jadwal data-kd="CaseSensitive" disabled>Lihat Jadwal</button></td></tr>
            </tbody></table></div>""", "/mahasiswa/jadwal-mengajar")
        val rows = StudentPortalParser.page(doc).tables.single().rows
        val link = rows.first().links.single()
        assertEquals("Lihat jadwal dosen", link.title)
        assertEquals(StudentPortalParser.ORIGIN + "/get-jadwal-dosen/Opaque%20%2F%2B%3F%20Code", link.url)
        assertEquals("Opaque /+? Code", java.net.URLDecoder.decode(link.url.substringAfterLast('/'), "UTF-8"))
        assertTrue(rows.last().links.isEmpty())
    }

    @Test fun khsEmptyRowsAndMultilineTotalsAreStillVisible() {
        val doc = document("""<div class=app-content><table><thead><tr><th>No</th><th>Kode</th><th>Mata Kuliah</th><th>SKS</th></tr></thead>
            <tbody><tr><td colspan=4>Tidak ada data</td></tr><tr><td colspan=2>BOBOT NILAI<br>A = 4</td>
            <td>TOTAL MUTU<br>TOTAL SKS<br>IPK</td><td>0<br>0<br>0.00</td></tr></tbody></table></div>""", "/mahasiswa/khs")
        val rows = StudentPortalParser.page(doc).tables.single().rows
        assertEquals("Tidak ada data", rows.first().fields.single().second)
        assertTrue(rows.last().fields.contains("TOTAL MUTU" to "0"))
        assertTrue(rows.last().fields.contains("IPK" to "0.00"))
    }

    @Test fun gatedSweetAlertContentBecomesANoticeWithoutScriptExecution() {
        val doc = document("""<div class=app-content><script>Swal.fire({icon:'error', title:'Oops...',
            text:'Anda Tidak Termasuk Mahasiswa yang Berhak Cuti Akademik.'}).then(function(){window.location.href='/mahasiswa/beranda';});
            </script></div><script>const ignored = {text:'Not an alert'};</script>""", "/mahasiswa/cuti-mhs")
        val page = StudentPortalParser.page(doc)
        assertEquals(1, page.notices.size)
        assertTrue(page.notices.single().error)
        assertTrue(page.notices.single().text.contains("Berhak Cuti"))
        assertTrue(page.links.isEmpty())
        assertTrue(page.paragraphs.isEmpty())
    }

    @Test fun semesterAndUploadFormsPreserveNativeMethodsAndValidation() {
        val doc = document("""<div class=app-content><form action=/mahasiswa/khs-semester method=GET>
            <label for=semester>Pilih Semester:</label><select id=semester name=semester required>
            <option disabled selected value="">Pilih Semester</option><option value=1>Semester 1</option></select><button type=submit>Tampilkan</button></form>
            <form action=/mahasiswa/perubahan-biodata method=post enctype=multipart/form-data><input name=_token type=hidden value=TEST_CSRF>
            <label for=ktp>KTP</label><input id=ktp type=file name=ktp accept=.pdf required><button type=submit>Kirim</button></form></div>""")
        val forms = StudentPortalParser.page(doc).forms
        assertEquals("GET", forms.first().method)
        assertEquals("Pilih semester", forms.first().title)
        assertEquals("1", forms.first().fields.single().options.last().value)
        assertTrue(forms.last().multipart)
        assertTrue(forms.last().fields.single().required)
        assertEquals(".pdf", forms.last().fields.single().accept)
        assertEquals(StudentPortalParser.ORIGIN + "/mahasiswa/beranda", forms.last().sourceUrl)
    }

    @Test fun modalTextAndPdfFramesHaveNativeContentAndDeadLinksDoNot() {
        val doc = document("""<div class=app-content><button data-bs-toggle=modal data-bs-target=#information>Lihat informasi</button>
            <div class=modal id=information><h5 class=modal-title>Informasi Dosen</h5><div class=modal-body>
            <dl><dt>Dosen</dt><dd>Dosen Contoh</dd></dl><p>Jadwal konsultasi mengikuti pengumuman.</p></div></div>
            <a href=#information>Lihat</a><a href=javascript:void(0)>Tak ada tujuan</a>
            <iframe title="Kalender Akademik" src=/mhs_download/calendar.pdf></iframe>
            <a href=https://bad-user:secret@other.invalid/document.pdf>Tolak kredensial URL</a></div>""")
        val page = StudentPortalParser.page(doc)
        assertTrue(page.facts.contains("Dosen" to "Dosen Contoh"))
        assertTrue(page.paragraphs.contains("Jadwal konsultasi mengikuti pengumuman."))
        assertEquals(listOf("Kalender Akademik"), page.links.map { it.title })
        assertEquals(StudentPortalParser.ORIGIN + "/mhs_download/calendar.pdf", page.links.single().url)
    }
}
