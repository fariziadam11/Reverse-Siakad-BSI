package com.gis.reverse

import android.webkit.CookieManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {
    private fun waitUntil(timeout: Long = 45_000, assertion: () -> Unit) {
        val until = System.currentTimeMillis() + timeout
        var last: Throwable? = null
        while (System.currentTimeMillis() < until) {
            try { assertion(); return } catch (error: AssertionError) { last = error }
            catch (error: androidx.test.espresso.NoMatchingViewException) { last = error }
            Thread.sleep(250)
        }
        throw AssertionError("Login UI did not become ready", last)
    }

    @Test fun publicLoginRendersValidatesRefreshesAndSurvivesRotation() {
        val cleared = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CookieManager.getInstance().removeAllCookies { cleared.countDown() }
        }
        check(cleared.await(10, TimeUnit.SECONDS))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil { onView(withText("Masuk ke MyBest")).check(matches(isDisplayed())) }
            waitUntil { onView(withText("Masuk ke akun")).check(matches(isEnabled())) }
            onView(withText("Masuk ke akun")).perform(scrollTo(), click())
            onView(withText("Isi NIP atau NIM.")).check(matches(isDisplayed()))
            onView(withHint("NIP / NIM")).perform(scrollTo(), replaceText("123"), closeSoftKeyboard())
            onView(withText("Masuk ke akun")).perform(scrollTo(), click())
            onView(withText("Isi password.")).check(matches(isDisplayed()))
            onView(withText("Ganti kode keamanan")).perform(scrollTo(), click())
            waitUntil { onView(withText("Masuk ke akun")).check(matches(isEnabled())) }
            onView(withText("Ingat akun pada perangkat ini")).perform(scrollTo(), click())
            onView(withText("Ingat akun pada perangkat ini")).check(matches(isChecked()))
            onView(withText("Ingat akun pada perangkat ini")).perform(click())
            scenario.recreate()
            waitUntil { onView(withText("Masuk ke MyBest")).check(matches(isDisplayed())) }
            waitUntil { onView(withText("Masuk ke akun")).check(matches(isEnabled())) }
            onView(withText("Ingat akun pada perangkat ini")).check(matches(isNotChecked()))
        }
    }
}
