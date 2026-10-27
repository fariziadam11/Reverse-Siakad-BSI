package com.gis.reverse

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gis.reverse.data.AndroidSessionCookies
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionCookieIsolationTest {
    @Test fun clearingOnePortalKeepsTheOtherPortalsSession() {
        val firstOrigin = "https://session-check.example.invalid"
        val secondOrigin = "https://student-check.example.invalid"
        val first = AndroidSessionCookies(firstOrigin)
        val second = AndroidSessionCookies(secondOrigin)
        try {
            first.put(firstOrigin, "fixture_session=first; Path=/; Secure; HttpOnly")
            second.put(secondOrigin, "fixture_session=second; Path=/; Secure; HttpOnly")
            assertTrue(first.get(firstOrigin).contains("fixture_session=first"))
            first.clear()
            assertFalse(first.get(firstOrigin).contains("fixture_session="))
            assertTrue(second.get(secondOrigin).contains("fixture_session=second"))
        } finally {
            first.clear()
            second.clear()
        }
    }
}
