package com.gis.reverse

import com.gis.reverse.data.*
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.security.MessageDigest

class StudentPortalClientSimulationTest {
    private lateinit var client: StudentPortalClient
    private lateinit var cookies: SessionCookies
    private lateinit var origin: String

    @Before fun configure() {
        val configured = System.getenv("STUDENT_SIMULATION_ORIGIN")
        assumeTrue("Start the local Student Postman simulation and set STUDENT_SIMULATION_ORIGIN", !configured.isNullOrBlank())
        origin = configured!!
        cookies = object : SessionCookies {
            val jar = CookieManager(null, CookiePolicy.ACCEPT_ALL)
            override fun get(url: String) = jar.get(URI(url), emptyMap())["Cookie"]?.joinToString("; ").orEmpty()
            override fun put(url: String, cookie: String) { jar.put(URI(url), mapOf("Set-Cookie" to listOf(cookie))) }
            override fun clear() { jar.cookieStore.removeAll() }
        }
        client = StudentPortalClient(cookies, origin)
    }

    private fun login(): org.jsoup.nodes.Document {
        val challenge = client.challenge()
        return client.login(challenge, challenge.form.hidden + listOf("username" to "test-student", "password" to "test-password"))
    }

    private fun form(path: String, method: String = "POST", multipart: Boolean = false) = NativeForm(
        "$origin$path", "Form uji Student", "$origin$path", method, "$origin/mahasiswa/perubahan-biodata",
        listOf("_token" to "STUDENT_TEST_CSRF"), emptyList(), "Kirim", multipart = multipart
    )

    private fun stats() = client.page("/__stats").selectFirst("#received")!!.text()

    @Test fun loginUsesStudentFormAndRetainsTheRotatedCookieAcrossBothRedirects() {
        val challenge = client.challenge()
        assertEquals("POST", challenge.form.method)
        assertFalse(challenge.form.fields.any { it.name.contains("captcha") })
        val before = cookies.get(origin)
        val home = client.login(challenge, challenge.form.hidden + listOf("username" to "test-student", "password" to "test-password"))
        assertEquals("TEST STUDENT", StudentPortalParser.student(home).name)
        assertNotEquals(before, cookies.get(origin))
        assertTrue(client.page("/mahasiswa/krs").text().contains("MATA KULIAH UJI"))
    }

    @Test fun invalidLoginAndA200LoginPageAreRecognizedWithoutElearningCaptchaFields() {
        val challenge = client.challenge()
        assertThrows(PortalError::class.java) { client.login(challenge, challenge.form.hidden + listOf("username" to "wrong", "password" to "wrong")) }
        login()
        assertThrows(SessionExpired::class.java) { client.page("/__login-as-200") }
    }

    @Test fun semesterFormSubmitsServerQueryValuesWithoutInventingAnEndpoint() {
        login()
        val form = StudentPortalParser.page(client.page("/mahasiswa/khs-semester")).forms.single()
        val result = client.submit(form, form.hidden + listOf("semester" to "1"))
        assertTrue(result.url.endsWith("/mahasiswa/khs-semester?semester=1"))
        assertEquals("Semester 1", Jsoup.parse(result.html).selectFirst("#semester-result")!!.text())
    }

    @Test fun validationResponsePreservesStatusAndSendsTheOriginalTokenAndReferer() {
        login()
        val form = form("/mahasiswa/password")
        val fields = form.hidden + listOf("current_password" to "synthetic", "new_password" to "one", "new_password_confirmation" to "two")
        val result = client.submit(form, fields)
        assertEquals(422, result.status)
        assertTrue(StudentPortalParser.page(PortalParser.document(result.html, result.url)).notices.any { it.error })
        assertTrue(stats().contains("STUDENT_TEST_CSRF"))
        assertTrue(stats().contains(form.sourceUrl))
    }

    @Test fun multipartUploadRetainsBinaryPdfBytes() {
        login()
        val bytes = "%PDF-1.4\nstudent fixture\n".toByteArray() + byteArrayOf(0, -1, -128, 1)
        val form = form("/mahasiswa/perubahan-biodata", multipart = true)
        val result = client.submit(form, form.hidden + listOf("keterangan" to "Data sintetis"), listOf(UploadPart("ktp", "fixture.pdf", "application/pdf", bytes.size.toLong()) { bytes.inputStream() }))
        assertEquals(200, result.status)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertTrue(stats().contains(sha))
        assertTrue(stats().contains("\"name\":\"ktp\""))
    }

    @Test fun staleFormChecksStudentHomeAndNeverReplaysTheMutation() {
        login()
        val form = form("/__stale")
        val error = assertThrows(PortalError::class.java) { client.submit(form, form.hidden) }
        assertTrue(error.message!!.contains("Sesi masih aktif"))
        assertTrue(stats().contains("\"writes\":1"))
        assertEquals("TEST STUDENT", StudentPortalParser.student(client.page(StudentPortalParser.HOME)).name)
    }

    @Test fun mutationRedirectDoesNotReplayOrForwardCookiesToAnotherOrigin() {
        login()
        val replay = form("/__replay")
        assertThrows(PortalError::class.java) { client.submit(replay, replay.hidden) }
        val outside = form("/__outside")
        val result = client.submit(outside, outside.hidden)
        assertEquals("https://example.invalid/student-mutation", result.location)
        assertTrue(result.html.isEmpty())
        assertTrue(stats().contains("\"writes\":2"))
        assertThrows(PortalError::class.java) { client.submit(outside.copy(action = "https://example.invalid/send"), outside.hidden) }
    }

    @Test fun downloadStreamsAPdfAndLogoutClearsOnlyTheProvidedCookieStore() {
        login()
        var downloaded = ""
        client.downloadForm(form("/__download", "GET"), emptyList()) { name, mime, input ->
            assertEquals("khs-test.pdf", name)
            assertEquals("application/pdf", mime)
            downloaded = input.bufferedReader().readText()
        }
        assertTrue(downloaded.startsWith("%PDF-1.4"))
        client.logout()
        assertEquals("", cookies.get(origin))
        assertThrows(SessionExpired::class.java) { client.page(StudentPortalParser.HOME) }
    }

    @Test fun myBestSsoAcceptsOnlyTheObservedHttpsAuthenticationDestination() {
        login()
        assertEquals("https://elearning.bsi.ac.id/authenticate-student?token=synthetic-sso", client.myBestSso())
        client.page("/__bad-sso")
        assertThrows(PortalError::class.java) { client.myBestSso() }
        assertEquals("TEST STUDENT", StudentPortalParser.student(client.page(StudentPortalParser.HOME)).name)
    }

    @Test fun rawReadPreservesDynamicJsonAndDetectsLoginWithoutFollowingExternalRedirects() {
        login()
        val schedule = client.read("/get-jadwal-dosen/TEST-LECTURER")
        assertEquals(200, schedule.status)
        assertTrue(schedule.html.startsWith("[{\"kd_mtk\":\"TEST\""))
        assertTrue(schedule.html.contains("\"ruang\":\"TEST-ROOM\""))
        val pkl = client.read("/mahasiswa/beranda/info-pkl")
        assertEquals(200, pkl.status)
        assertTrue(pkl.html.contains("\"data\":{\"nim\":\"TEST-NIM\""))
        val outside = client.read("/__outside-read")
        assertEquals("https://example.invalid/student-read", outside.location)
        assertTrue(outside.html.isEmpty())
        assertThrows(SessionExpired::class.java) { client.read("/__login-as-200") }
    }

    @Test fun researchMemberLookupUsesJsonAndTheCurrentFormCsrfWithoutSubmittingALetter() {
        login()
        val form = form("/mahasiswa/surat-riset/template")
        val found = client.lookupResearchMember(form, "TEST-NIM")
        assertEquals(200, found.status)
        assertTrue(found.html.contains("\"success\":true"))
        assertTrue(found.html.contains("\"nama\":\"TEST STUDENT\""))
        val missing = client.lookupResearchMember(form, "MISSING\"\\\n")
        assertTrue(missing.html.contains("\"success\":false"))
        assertTrue(stats().contains("\"writes\":0"))
    }

    @Test fun loginVerifiesStudentWhenAnIntendedMyLmsPageReturnsAnExternalSsoRedirect() {
        assertThrows(SessionExpired::class.java) { client.myBestSso() }
        val home = login()
        assertEquals("TEST STUDENT", StudentPortalParser.student(home).name)
        assertTrue(client.page(StudentPortalParser.HOME).text().contains("Informasi akademik"))
        assertTrue(stats().contains("\"writes\":0"))
    }
}
