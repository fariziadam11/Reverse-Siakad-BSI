package com.gis.reverse

import com.gis.reverse.data.PortalError
import com.gis.reverse.data.PortalParser
import org.junit.Assert.*
import org.junit.Test

class PortalParserTest {
    private fun fixture(name: String) = javaClass.getResource("/bsi/$name.html")!!.readText()

    @Test fun loginKeepsCsrfAndImageWithoutTreatingSvgTextAsAnAnswer() {
        val doc = PortalParser.document(fixture("login"))
        assertTrue(PortalParser.isLogin(doc))
        val challenge = PortalParser.challenge(doc)
        assertEquals("TEST_CSRF", challenge.csrf)
        assertTrue(challenge.svg.contains("<svg"))
        assertEquals("", challenge.error)
    }
    @Test fun changedLoginMarkupFailsWithAnActionableError() {
        val error = assertThrows(PortalError::class.java) { PortalParser.challenge(PortalParser.document("<form></form>")) }
        assertTrue(error.message!!.contains("berubah"))
    }
    @Test fun schedulePreservesOpaqueUrlsAndClassifiesToday() {
        val courses = PortalParser.courses(PortalParser.document(fixture("schedule")))
        assertEquals(2, courses.size)
        assertEquals("104", courses[0].code)
        assertEquals("TEST.104", courses[0].classroom)
        assertEquals("Jumat, 17:30-18:50", courses[0].schedule)
        assertFalse(courses[0].today)
        assertTrue(courses[1].today)
        assertEquals("https://elearning.bsi.ac.id/learning/opaque%2Fid==", courses[0].links.first { it.title == "Materi" }.url)
    }
    @Test fun legacyMalformedTableStillSeparatesTasksAndGrades() {
        val tables = PortalParser.tables(PortalParser.document(fixture("assignments")))
        assertEquals(listOf("Data Penugasan", "Data Nilai Tugas"), tables.map { it.title })
        val task = tables[0].rows.single()
        assertEquals("1", task.fields.first { it.first == "Pertemuan" }.second)
        assertFalse(task.fields.any { it.first == "Aksi" || it.first == "No" })
        assertEquals("https://elearning.bsi.ac.id/assignment/send/fixture-id", task.links.single().url)
        assertEquals("80", tables[1].rows.single().fields.first { it.first == "Nilai" }.second)
        assertFalse(tables[1].rows.single().fields.any { it.first == "Catatan" })
    }
    @Test fun materialsKeepExternalDownloadsAndNamedVideo() {
        val result = PortalParser.learning(PortalParser.document(fixture("learning")))
        assertEquals("Silabus", result.documents.single().title)
        assertTrue(result.documents.single().url.startsWith("https://students.bsi.ac.id/"))
        assertEquals("Meeting 1 Introduction", result.videos.single().title)
        assertTrue(result.tables.single().rows.isEmpty())
    }
    @Test fun navigationNeverTreatsLogoutAsAReadOnlyLink() {
        val doc = PortalParser.document("<div class=sidebar-menu><a href='/sch'>Jadwal</a><a href='#'>Menu</a><a href='/logout'>Keluar</a></div>")
        assertEquals(listOf("Jadwal"), PortalParser.navigation(doc).map { it.title })
    }
}
