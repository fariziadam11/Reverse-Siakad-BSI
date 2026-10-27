package com.gis.reverse

import com.gis.reverse.data.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

class NativeFormTransportTest {
    private lateinit var client: BsiClient
    private lateinit var origin: String
    private lateinit var cookies: SessionCookies
    private val token = "_token" to "TEST_CSRF"

    @Before fun setup() {
        val configured = System.getenv("BSI_SIMULATION_ORIGIN")
        assumeTrue("Start the local Postman simulation", !configured.isNullOrBlank())
        origin = configured!!
        cookies = object : SessionCookies {
            val jar = CookieManager(null, CookiePolicy.ACCEPT_ALL)
            override fun get(url: String) = jar.get(URI(url), emptyMap())["Cookie"]?.joinToString("; ").orEmpty()
            override fun put(url: String, cookie: String) { jar.put(URI(url), mapOf("Set-Cookie" to listOf(cookie))) }
            override fun clear() { jar.cookieStore.removeAll() }
        }
        client = BsiClient(cookies, origin)
        client.login(client.challenge(), "test-student", "test-password", "AB3")
    }

    private fun received(html: String) = PortalParser.document(html).select("#received").text()
    private fun writes() = received(client.page("/__forms/stats").outerHtml())

    @Test fun urlencodedSubmissionPreservesDuplicateFieldsMethodOverrideAndReferer() {
        val before = cookies.get(origin)
        val response = client.submit("/foto-profil/update", "post", "$origin/profil", listOf(token, "_method" to "patch", "name" to "Nama & Pengujian +", "email" to "fixture@example.invalid", "choice[]" to "A", "choice[]" to "B"))
        assertEquals(200, response.status)
        assertEquals("$origin/profil", response.url)
        assertTrue(response.redirected)
        assertNotEquals(before, cookies.get(origin))
        val metadata = received(response.html)
        assertTrue(metadata.contains("[\"_method\",\"patch\"]"))
        assertTrue(metadata.contains("[\"choice[]\",\"A\"]"))
        assertTrue(metadata.contains("[\"choice[]\",\"B\"]"))
        assertTrue(metadata.contains("Nama & Pengujian +"))
        assertTrue(metadata.contains("\"referer\":\"$origin/profil\""))
    }

    @Test fun validationHtmlIsReturnedForNativeFieldErrors() {
        val response = client.submit("/foto-profil/update", "POST", "$origin/profil", listOf(token, "_method" to "patch", "email" to "invalid"))
        assertEquals(422, response.status)
        assertTrue(response.html.contains("Email tidak valid."))
        assertFalse(response.redirected)
    }

    @Test fun multipartPdfIsStreamedWithFieldsAndNoUtf8Corruption() {
        val bytes = "%PDF-1.4\nfixture\n".toByteArray() + byteArrayOf(0, 0xFF.toByte(), 1, 0x80.toByte())
        val opens = AtomicInteger()
        val response = client.submit("/tambah-diskusimhs", "POST", "$origin/form-diskusimhs/fixture-id", listOf(token, "judul" to "Fixture diskusi", "chat" to "Isi üji & data", "kd_lokal" to "FIXTURE", "kd_mtk" to "104"), listOf(UploadPart("file", "lampiran.pdf", "application/pdf", bytes.size.toLong()) { opens.incrementAndGet(); ByteArrayInputStream(bytes) }))
        assertEquals(1, opens.get())
        val metadata = received(response.html)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertTrue(metadata.contains("\"multipart\":true"))
        assertTrue(metadata.contains("\"filename\":\"lampiran.pdf\""))
        assertTrue(metadata, metadata.contains("\"sha256\":\"$digest\""))
        assertTrue(metadata.contains("Isi üji & data"))
    }

    @Test fun optionalFileFormCanStillUseMultipartWithoutAnAttachment() {
        val response = client.submit("/tambah-diskusimhs", "POST", "$origin/form-diskusimhs/fixture-id", listOf(token, "judul" to "Tanpa lampiran", "chat" to "Fixture"), multipart = true)
        assertTrue(received(response.html).contains("\"multipart\":true"))
        assertTrue(received(response.html).contains("\"files\":[]"))
    }

    @Test fun postRedirectNeverReplaysMutationAndCrossOriginNeverReceivesFiles() {
        assertThrows(PortalError::class.java) { client.submit("/__forms/replay", "POST", "$origin/profil", listOf(token)) }
        assertEquals("{\"writes\":1}", writes())
        val response = client.submit("/__forms/outside", "POST", "$origin/profil", listOf(token))
        assertEquals("https://example.invalid/mutation", response.location)
        assertEquals("{\"writes\":2}", writes())
        val opens = AtomicInteger()
        assertThrows(PortalError::class.java) {
            client.submit("https://example.invalid/upload", "POST", "$origin/profil", listOf(token), listOf(UploadPart("file", "a.pdf", "application/pdf", 1) { opens.incrementAndGet(); ByteArrayInputStream(byteArrayOf(1)) }))
        }
        assertEquals(0, opens.get())
    }

    @Test fun staleCsrfPreservesAuthenticationAndServerFailureDoesNotRetryWrites() {
        val stale = assertThrows(PortalError::class.java) {
            client.submit("/assignment", "POST", "$origin/assignment/send/fixture-id", listOf("_token" to "STALE"))
        }
        assertTrue(stale.message!!.contains("Sesi masih aktif"))
        assertTrue(stale.message!!.contains("Muat ulang halaman"))
        assertEquals("TEST STUDENT", PortalParser.student(client.page("/user/dashboard")).name)
        val refreshedForm = client.page("/assignment/send/fixture-id")
        assertEquals("TEST_CSRF", refreshedForm.selectFirst("input[name=_token]")!!.attr("value"))
        assertFalse(PortalParser.isLogin(refreshedForm))
        assertEquals("{\"writes\":1}", writes())
        val error = assertThrows(PortalError::class.java) { client.submit("/__forms/server-error", "POST", "$origin/profil", listOf(token)) }
        assertTrue(error.message!!.contains("belum dapat dipastikan"))
        assertEquals("{\"writes\":2}", writes())
    }

    @Test fun postDownloadPreservesBinaryAndSanitizesServerFilename() {
        var name = ""; var mime = ""; var data = byteArrayOf()
        client.downloadForm("/download-file-tugas", "POST", "$origin/assignment/fixture-id", listOf(token, "id" to "fixture-id", "file" to "fixture.pdf")) { filename, contentType, input -> name = filename; mime = contentType; data = input.readBytes() }
        assertEquals("nilai.pdf", name)
        assertEquals("application/pdf", mime)
        assertArrayEquals("%PDF-1.4\nfixture\n".toByteArray() + byteArrayOf(0, 0xFF.toByte(), 1, 0x80.toByte()), data)
    }

    @Test fun getDownloadEncodesDuplicateQueriesAndRfc5987Filename() {
        var filename = ""
        client.downloadForm("/__forms/download", "GET", "$origin/profil", listOf("choice" to "a+b", "choice" to "c&d")) { name, _, input -> filename = name; input.readBytes() }
        assertEquals("Materi A+B.pdf", filename)
    }

    @Test fun htmlLoginIsNeverSavedAsAnAttachmentEvenWithBinaryContentType() {
        for (file in listOf("login", "disguised-login")) {
            var called = false
            assertThrows(SessionExpired::class.java) {
                client.downloadForm("/download-file-tugas", "POST", "$origin/assignment/fixture-id", listOf(token, "id" to "fixture-id", "file" to file)) { _, _, _ -> called = true }
            }
            assertFalse(called)
        }
    }

    @Test fun expiredSessionCannotSubmitOrSaveAnAttachment() {
        client.request("/__expire", emptyMap())
        assertThrows(SessionExpired::class.java) { client.submit("/assignment", "POST", "$origin/assignment/send/fixture-id", listOf(token, "isi" to "https://example.invalid/fixture")) }
        var called = false
        assertThrows(SessionExpired::class.java) { client.downloadForm("/download-file-tugas", "POST", "$origin/assignment/fixture-id", listOf(token)) { _, _, _ -> called = true } }
        assertFalse(called)
    }
}
