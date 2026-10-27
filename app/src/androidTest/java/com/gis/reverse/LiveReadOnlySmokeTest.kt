package com.gis.reverse

import android.graphics.Bitmap
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack as espressoBack
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gis.reverse.data.LoginChallenge
import org.hamcrest.Matchers.allOf
import org.jsoup.Jsoup
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in, read-only live smoke test. Supply credentials transiently as runner arguments. */
@RunWith(AndroidJUnit4::class)
class LiveReadOnlySmokeTest {
    private fun waitUntil(assertion: () -> Unit) {
        val until = System.currentTimeMillis() + 60_000
        var last: Throwable? = null
        while (System.currentTimeMillis() < until) {
            try { assertion(); return } catch (error: AssertionError) { last = error }
            catch (error: androidx.test.espresso.NoMatchingViewException) { last = error }
            Thread.sleep(250)
        }
        throw AssertionError("Read-only page did not become ready", last)
    }
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(400)
        val directory = File(instrumentation.targetContext.externalCacheDir, "verification").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    @Test fun signInAndReadNativeStudentPages() {
        val arguments = InstrumentationRegistry.getArguments()
        val username = arguments.getString("bsiNim").orEmpty()
        val password = arguments.getString("bsiPassword").orEmpty()
        val sessionOnly = arguments.getString("bsiSessionOnly") == "true"
        assumeTrue("Opt-in live test requires transient credentials and CAPTCHA authorization, or an existing authorized session", sessionOnly || (username.isNotBlank() && password.isNotBlank()))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            if (!sessionOnly) {
            waitUntil { onView(withText("Masuk ke akun")).check(matches(isEnabled())) }
            var answer = ""
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("challenge").apply { isAccessible = true }
                val challenge = field.get(activity) as LoginChallenge
                // This is test-only and requires action-time CAPTCHA approval from the user.
                answer = Jsoup.parse(challenge.svg).select("text").joinToString("") { it.text() }
            }
            check(answer.length == 3)
            onView(withHint("NIP / NIM")).perform(scrollTo(), replaceText(username), closeSoftKeyboard())
            onView(withHint("Password")).perform(scrollTo(), replaceText(password), closeSoftKeyboard())
            onView(withHint("3 karakter pada gambar")).perform(scrollTo(), replaceText(answer), closeSoftKeyboard())
            onView(withText("Masuk ke akun")).perform(scrollTo(), click())
            }
            waitUntil { onView(withText("Selamat datang")).check(matches(isDisplayed())) }
            screenshot("dashboard")
            onView(withId(R.id.nav_schedule)).perform(click())
            waitUntil { onView(withText("BAHASA INGGRIS I")).check(matches(isDisplayed())) }
            screenshot("schedule")
            onView(allOf(withText("Materi"), hasSibling(withText("BAHASA INGGRIS I")))).perform(scrollTo(), click())
            waitUntil { onView(withText("Dokumen pembelajaran")).check(matches(isDisplayed())) }
            screenshot("materials")
            espressoBack()
            waitUntil { onView(withText("BAHASA INGGRIS I")).check(matches(isDisplayed())) }
            onView(allOf(withText("Tugas"), hasSibling(withText("BAHASA INGGRIS I")))).perform(scrollTo(), click())
            waitUntil { onView(withText("Data Penugasan")).check(matches(isDisplayed())) }
            screenshot("assignments")
            onView(withId(R.id.nav_services)).perform(click())
            onView(withText("Akses menu yang tersedia pada akun BSI Anda.")).check(matches(isDisplayed()))
            screenshot("services")
            onView(withId(R.id.nav_account)).perform(click())
            onView(allOf(withText("Akun saya"), isDescendantOfA(isAssignableFrom(ScrollView::class.java)))).check(matches(isDisplayed()))
            screenshot("account")
            onView(withId(R.id.nav_home)).perform(click())
            waitUntil { onView(withText("Selamat datang")).check(matches(isDisplayed())) }
        }
    }
}
