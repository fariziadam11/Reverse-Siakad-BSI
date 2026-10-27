package com.gis.reverse

import com.gis.reverse.data.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI

class BsiClientSimulationTest {
    private lateinit var client: BsiClient
    private lateinit var cookies: SessionCookies
    private lateinit var origin: String

    @Before fun configure() {
        val configuredOrigin = System.getenv("BSI_SIMULATION_ORIGIN")
        assumeTrue("Start the local Postman simulation and set BSI_SIMULATION_ORIGIN", !configuredOrigin.isNullOrBlank())
        origin = configuredOrigin!!
        cookies = object : SessionCookies {
            val jar = CookieManager(null, CookiePolicy.ACCEPT_ALL)
            override fun get(url: String) = jar.get(URI(url), emptyMap())["Cookie"]?.joinToString("; ").orEmpty()
            override fun put(url: String, cookie: String) { jar.put(URI(url), mapOf("Set-Cookie" to listOf(cookie))) }
            override fun clear() { jar.cookieStore.removeAll() }
        }
        client = BsiClient(cookies, origin)
    }
    private fun login() = client.login(client.challenge(), "test-student", "test-password", "ab3")

    @Test fun validLoginFollowsLegacyRedirectAndRetainsRotatedCookies() {
        val challenge = client.challenge()
        val before = cookies.get(origin)
        val doc = client.login(challenge, "test-student", "test-password", "AB3")
        assertEquals("TEST STUDENT", PortalParser.student(doc).name)
        assertNotEquals(before, cookies.get(origin))
        assertEquals(2, PortalParser.courses(client.page("/sch")).size)
    }
    @Test fun wrongCaptchaCannotOpenScheduleAndCanRetryWithNewChallenge() {
        assertThrows(PortalError::class.java) { client.login(client.challenge(), "test-student", "test-password", "BAD") }
        assertThrows(SessionExpired::class.java) { client.page("/sch") }
        assertEquals("TEST STUDENT", PortalParser.student(login()).name)
    }
    @Test fun expiredSessionProducesLoginRecoverySignalAndCanResume() {
        login()
        client.request("/__expire", emptyMap())
        assertThrows(SessionExpired::class.java) { client.page("/sch") }
        login()
        assertEquals(2, PortalParser.courses(client.page("/sch")).size)
    }
    @Test fun upstreamFailureHasActionableMessageInsteadOfParsingAnErrorPage() {
        val error = assertThrows(PortalError::class.java) { client.page("/server-error") }
        assertTrue(error.message!!.contains("503"))
    }
    @Test fun crossOriginRedirectIsReturnedWithoutForwardingTheSession() {
        val response = client.request("/outside")
        assertEquals(302, response.status)
        assertEquals("https://example.invalid/authenticate?token=synthetic", response.location)
        assertTrue(response.html.isEmpty())
        assertThrows(PortalError::class.java) { client.request("https://example.invalid/login", mapOf("password" to "synthetic")) }
    }
    @Test fun csrfExpiryIsDetectedAndLogoutClearsSession() {
        assertThrows(SessionExpired::class.java) { client.login(LoginChallenge("STALE", ""), "test-student", "test-password", "AB3") }
        login()
        client.logout()
        assertEquals("", cookies.get(origin))
        assertThrows(SessionExpired::class.java) { client.page("/sch") }
    }

    @Test fun staleLoginTokenDoesNotDiscardAnAuthenticatedSession() {
        login()
        val error = assertThrows(PortalError::class.java) {
            client.login(LoginChallenge("STALE", ""), "test-student", "test-password", "AB3")
        }
        assertTrue(error.message!!.contains("Sesi masih aktif"))
        assertEquals(2, PortalParser.courses(client.page("/sch")).size)
    }

    @Test fun rejectedStaleFormKeepsLoginAndDoesNotReplayTheWrite() {
        login()
        val error = assertThrows(PortalError::class.java) {
            client.submit("/foto-profil/update", "POST", "$origin/profil", listOf("_token" to "STALE", "email" to "fixture@example.invalid"))
        }
        assertTrue(error.message!!.contains("Sesi masih aktif"))
        assertEquals(2, PortalParser.courses(client.page("/sch")).size)
        assertEquals(1, org.jsoup.Jsoup.parse(client.request("/__forms/stats").html).selectFirst("#received")!!.text().let {
            Regex("\"writes\":(\\d+)").find(it)!!.groupValues[1].toInt()
        })
    }
}
