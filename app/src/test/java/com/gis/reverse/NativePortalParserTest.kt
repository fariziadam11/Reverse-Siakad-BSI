package com.gis.reverse

import com.gis.reverse.data.NativePortalParser
import com.gis.reverse.data.PortalParser
import org.junit.Assert.*
import org.junit.Test

class NativePortalParserTest {
    private fun document(name: String, path: String = "/$name") = PortalParser.document(javaClass.getResource("/bsi/native/$name.html")!!.readText(), PortalParser.ORIGIN + path)

    @Test fun profileRetainsMethodOverridesAndCorrectRequiredLabels() {
        val forms = NativePortalParser.forms(PortalParser.main(document("profile", "/profil")))
        assertEquals(2, forms.size)
        val password = forms.first { it.title == "Ubah password" }
        assertEquals(listOf("current_password", "password", "password_confirmation"), password.fields.map { it.name })
        assertTrue(password.fields.all { it.type == "password" && it.required })
        assertTrue(password.hidden.contains("_method" to "patch"))
        val profile = forms.first { it.title == "Edit profil" }
        assertEquals("https://elearning.bsi.ac.id/foto-profil/update", profile.action)
        assertEquals(listOf("Nama Lengkap", "Email"), profile.fields.map { it.label })
        assertTrue(profile.fields.all { it.required })
        assertTrue(profile.hidden.contains("_token" to "TEST_CSRF"))
    }
    @Test fun legacyDiscussionSubmitOutsideFormStillRendersNativeCreateAction() {
        val form = NativePortalParser.page(document("discussion", "/form-diskusimhs/fixture-id")).forms.single()
        assertEquals("Buat diskusi", form.title)
        assertEquals("Kirim diskusi", form.submitLabel)
        assertTrue(form.collapsed)
        assertTrue(form.multipart)
        assertEquals(listOf("judul", "chat", "file"), form.fields.map { it.name })
        assertTrue(form.fields.first { it.name == "judul" }.required)
        assertTrue(form.fields.first { it.name == "chat" }.required)
        val file = form.fields.first { it.type == "file" }
        assertEquals("application/pdf", file.accept)
        assertEquals(2L * 1024 * 1024, file.maxBytes)
        assertFalse(file.required)
    }
    @Test fun assignmentSubmissionPreservesHiddenContextAndLinkInput() {
        val form = NativePortalParser.page(document("submission", "/assignment/send/fixture-id")).forms.single()
        assertEquals("https://elearning.bsi.ac.id/assignment", form.action)
        assertEquals("POST", form.method)
        assertEquals("Kirim Tugas", form.submitLabel)
        assertEquals(setOf("_token", "kd_mtk", "id_tugas", "nim", "kd_lokal"), form.hidden.map { it.first }.toSet())
        assertEquals("url", form.fields.single().type)
        assertEquals("isi", form.fields.single().name)
        assertEquals("Link Tugas", form.fields.single().label)
    }
    @Test fun commentsRetainEachAuthorIdentifierMessageAndTimeInServerOrder() {
        val page = NativePortalParser.page(document("comments", "/form-komentarmhs/fixture-id"))
        assertEquals(listOf("Mahasiswa Uji A", "Mahasiswa Uji B", "Dosen Uji"), page.comments.map { it.name })
        assertEquals(listOf("TEST_NIM_1", "TEST_NIM_2", "TEST_NIP"), page.comments.map { it.identifier })
        assertEquals(listOf("Terima kasih.", "Terima kasih.", "Baris pertama.\nBaris kedua & penutup."), page.comments.map { it.message })
        assertEquals("2026-10-23 19:34:41", page.comments.first().time)
        assertEquals(listOf("Pertemuan 5", "Pancasila sebagai sistem filsafat"), page.paragraphs)
        val form = page.forms.single()
        assertEquals("https://elearning.bsi.ac.id/send-komentarmhs", form.action)
        assertEquals("POST", form.method)
        assertEquals("Kirim komentar", form.submitLabel)
        assertEquals("Komentar", form.fields.single().label)
        assertTrue(form.fields.single().required)
        assertEquals(listOf("_token" to "TEST_CSRF", "id_chat" to "TEST_MEETING_5", "id" to "TEST_OPAQUE_ID"), form.hidden)
    }
    @Test fun discussionKeepsEachMeetingsDescriptionDownloadAndCommentLinkTogether() {
        val page = NativePortalParser.page(document("discussion-topics", "/form-diskusimhs/fixture-id"))
        assertEquals(listOf("Pertemuan 6", "Pertemuan 5"), page.topics.map { it.title })
        val sixth = page.topics.first()
        assertEquals("Topik keenam", sixth.description)
        assertEquals("Dosen Uji", sixth.author)
        assertEquals("2026-10-27 19:36:11", sixth.time)
        assertEquals("https://elearning.bsi.ac.id/form-komentarmhs/meeting-6", sixth.links.single().url)
        assertTrue(sixth.forms.single().download)
        assertEquals("TEST_6.pdf", sixth.forms.single().hidden.toMap()["file"])
        assertEquals("Topik kelima", page.topics.last().description)
        assertEquals("Komentar 83", page.topics.last().links.single().title)
        assertTrue(page.topics.last().forms.isEmpty())
        assertTrue(page.forms.isEmpty())
        assertTrue(page.links.isEmpty())
        assertTrue(page.paragraphs.isEmpty())
        assertTrue(page.statuses.isEmpty())
    }
    @Test fun emptyMeetingKeepsItsComposerAndDoesNotBorrowAnotherMeetingsDraft() {
        val doc = document("comments", "/form-komentarmhs/fixture-id")
        val first = NativePortalParser.page(doc).forms.single()
        doc.select(".chat-box").empty()
        doc.select("input[name=id_chat]").attr("value", "TEST_MEETING_6")
        val page = NativePortalParser.page(doc)
        assertTrue(page.comments.isEmpty())
        assertNotEquals(first.key, page.forms.single().key)
        assertEquals("Kirim komentar", page.forms.single().submitLabel)
    }
    @Test fun lecturerQuestionnaireGroupsRadioOptionsByQuestionAndUsesTableLabels() {
        val page = NativePortalParser.page(document("class", "/absen-mhs/fixture-id"))
        assertTrue(page.statuses.contains("Belum Mulai"))
        assertTrue(page.facts.any { it.first == "Dosen" })
        val form = page.forms.single()
        val radio = form.fields.first { it.name == "cf31" }
        assertEquals("Menguasai Materi yang Diajarkan (F3-1)", radio.label)
        assertEquals(listOf("5", "4", "3", "2", "1"), radio.options.map { it.value })
        assertEquals(listOf("Sangat Setuju", "Setuju", "Cukup Setuju", "Tidak Setuju", "Sangat Tidak Setuju"), radio.options.map { it.label })
        assertTrue(radio.required)
        assertEquals(form.fields.size, form.fields.map { it.name }.distinct().size)
        assertTrue(page.tables.all { table -> table.rows.none { row -> row.fields.any { it.second.contains("Menguasai Materi") } } })
    }
    @Test fun assignmentDownloadFormsStayOnTheirOwnTableRows() {
        val doc = document("assignments", "/assignment/fixture-id")
        val page = NativePortalParser.page(doc)
        assertTrue(page.forms.isEmpty())
        val forms = page.tables.flatMap { it.rows }.flatMap { it.forms }
        assertTrue(forms.isNotEmpty())
        assertTrue(forms.all { it.download && it.fields.isEmpty() && it.action.endsWith("/download-file-tugas") })
    }
    @Test fun unsafeFormActionsAndDisabledFieldsAreNotSubmitted() {
        val doc = PortalParser.document("""<form action="https://other.invalid/steal" method=post><input name=_token value=SECRET></form><form action=/save method=post><input type=hidden name=_token value=TEST><input name=disabled disabled value=no><input name=readonly readonly value=yes><button type=submit>Save</button></form>""")
        val forms = NativePortalParser.forms(doc.body())
        assertEquals(1, forms.size)
        assertEquals("readonly", forms.single().fields.single().name)
        assertTrue(forms.single().fields.single().readOnly)
    }
    @Test fun csrfRefreshDoesNotChangeDraftIdentity() {
        val first = document("submission", "/assignment/send/fixture-id")
        val second = first.clone()
        second.select("input[name=_token]").attr("value", "NEW_TOKEN")
        assertEquals(NativePortalParser.forms(PortalParser.main(first)).single().key, NativePortalParser.forms(PortalParser.main(second)).single().key)
    }
}
