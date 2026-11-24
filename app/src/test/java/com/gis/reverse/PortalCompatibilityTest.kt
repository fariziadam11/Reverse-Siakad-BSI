package com.gis.reverse

import com.gis.reverse.data.*
import org.junit.Assert.*
import org.junit.Test

class PortalCompatibilityTest {
    private fun fixture(name: String, path: String) = PortalParser.document(
        javaClass.getResource("/bsi/$name.html")!!.readText(), PortalParser.ORIGIN + path)

    @Test fun stylingChangesDoNotLoseScheduleFields() {
        val doc = fixture("schedule", "/sch")
        doc.select(".jadwal-title, .jadwal-info-label, .jadwal-info-val").removeAttr("class")
        PortalParser.validatePage(doc)
        val course = PortalParser.courses(doc).first()
        assertEquals("BAHASA INGGRIS I", course.title)
        assertEquals("104", course.code)
        assertEquals("Jumat, 17:30-18:50", course.schedule)
    }

    @Test fun changedScheduleIsAnErrorRatherThanAnEmptyList() {
        val doc = fixture("schedule", "/sch")
        doc.select(".jadwal-card").attr("class", "new-course-layout")
        val error = assertThrows(PortalError::class.java) { PortalParser.validatePage(doc) }
        assertTrue(error.message!!.contains("diperbarui"))
    }

    @Test fun onlineCoursesUseTheObservedWaktuLabelForTheirSchedule() {
        val doc = fixture("schedule", "/sch")
        doc.select(".jadwal-info-label").filter { it.text() == "Hari & Jam:" }.forEach { it.text("Waktu:") }
        PortalParser.validatePage(doc)
        assertEquals("Jumat, 17:30-18:50", PortalParser.courses(doc).first().schedule)
    }

    @Test fun explicitEmptyScheduleAndEmptyDiscussionAreValid() {
        PortalParser.validatePage(PortalParser.document("<main><p>Belum ada jadwal kuliah.</p></main>", PortalParser.ORIGIN + "/sch"))
        PortalParser.validatePage(fixture("native/discussion", "/form-diskusimhs/test"))
    }

    @Test fun renamedDiscussionItemsDoNotDisappearSilently() {
        val doc = fixture("native/discussion-topics", "/form-diskusimhs/test")
        PortalParser.validatePage(doc)
        doc.select(".task-block").attr("class", "new-topic-layout")
        assertThrows(PortalError::class.java) { PortalParser.validatePage(doc) }
    }

    @Test fun commentsRequireTheirAuthorAndSubmissionContext() {
        val doc = fixture("native/comments", "/form-komentarmhs/test")
        PortalParser.validatePage(doc)
        val noAuthor = doc.clone().also { it.select(".chat-avatar").remove() }
        assertThrows(PortalError::class.java) { PortalParser.validatePage(noAuthor) }
        doc.select("input[name=id_chat]").remove()
        assertThrows(PortalError::class.java) { PortalParser.validatePage(doc) }
    }

    @Test fun emptyCommentsKeepAValidForm() {
        val doc = fixture("native/comments", "/form-komentarmhs/test")
        doc.select(".chat-box").empty()
        PortalParser.validatePage(doc)
        assertTrue(NativePortalParser.page(doc).comments.isEmpty())
    }

    @Test fun materialsAndAssignmentsKeepTheirContainersEvenWhenTablesAreEmpty() {
        val material = fixture("learning", "/learning/test")
        PortalParser.validatePage(material)
        material.select(".doc-block, table, iframe").remove()
        assertThrows(PortalError::class.java) { PortalParser.validatePage(material) }
        val assignments = fixture("assignments", "/assignment/test")
        PortalParser.validatePage(assignments)
        assignments.select("table").remove()
        assertThrows(PortalError::class.java) { PortalParser.validatePage(assignments) }
    }

    @Test fun dashboardIdentityCannotDisappearSilently() {
        val doc = PortalParser.document("<span class=user-display-name>TEST</span><span class=user-identifier>TEST_NIM</span>", PortalParser.ORIGIN + "/user/dashboard")
        PortalParser.validatePage(doc)
        doc.select(".user-identifier").remove()
        assertThrows(PortalError::class.java) { PortalParser.validatePage(doc) }
    }

    @Test fun semanticMainSurvivesStudentContainerClassChanges() {
        val doc = PortalParser.document("""<aside>Do not include sidebar</aside><div role=main>
            <table><thead><tr><th>Mata Kuliah</th></tr></thead><tbody><tr><td>TEST COURSE</td></tr></tbody></table></div>""", StudentPortalParser.ORIGIN + "/mahasiswa/krs")
        StudentPortalParser.validatePage(doc)
        assertEquals("TEST COURSE", StudentPortalParser.page(doc).tables.single().rows.single().fields.single().second)
        doc.select("table").remove()
        assertThrows(PortalError::class.java) { StudentPortalParser.validatePage(doc) }
    }

    @Test fun semesterControlsCannotVanishButServerRestrictionsArePreserved() {
        val url = StudentPortalParser.ORIGIN + "/mahasiswa/khs-semester"
        val doc = PortalParser.document("<main><form><select name=semester><option>1</option></select></form></main>", url)
        StudentPortalParser.validatePage(doc)
        doc.select("select").remove()
        assertThrows(PortalError::class.java) { StudentPortalParser.validatePage(doc) }
        doc.body().append("<div class=alert-warning>Layanan belum tersedia untuk akun ini.</div>")
        StudentPortalParser.validatePage(doc)
        assertTrue(StudentPortalParser.page(doc).notices.single().text.contains("belum tersedia"))
    }
}
