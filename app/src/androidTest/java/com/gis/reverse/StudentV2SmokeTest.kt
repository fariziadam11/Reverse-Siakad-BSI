package com.gis.reverse

import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebView
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gis.reverse.data.*
import org.hamcrest.Matchers.startsWith
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StudentV2SmokeTest {
    private fun ready(assertion: () -> Unit) {
        val until = System.currentTimeMillis() + 45_000
        var last: Throwable? = null
        while (System.currentTimeMillis() < until) {
            try { assertion(); return }
            catch (error: AssertionError) { last = error }
            catch (error: androidx.test.espresso.NoMatchingViewException) { last = error }
            Thread.sleep(250)
        }
        throw AssertionError("Student V2 screen did not become ready", last)
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(400)
        val directory = File(instrumentation.targetContext.externalCacheDir, "verification-student").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun menu(title: String) {
        onView(withText("Menu Student V2")).perform(scrollTo(), click())
        onView(withText(title)).perform(scrollTo(), click())
        ready { onView(withText("Menu Student V2")).check(matches(isDisplayed())) }
    }

    @Test fun liveStudentReadsNativePagesAndUsesOfficialMyBestSso() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val user = args.getString("studentUser").orEmpty()
        val password = args.getString("studentPassword").orEmpty()
        val existing = args.getString("studentSessionOnly") == "true"
        assumeTrue(existing || (user.isNotBlank() && password.isNotBlank()))
        val client = StudentPortalClient(AndroidSessionCookies(StudentPortalParser.ORIGIN))
        if (!existing) {
            val challenge = client.challenge()
            assumeTrue("Conditional CAPTCHA must be entered by the user", challenge.form.fields.none { it.name.contains("captcha", true) })
            client.login(challenge, challenge.form.hidden + listOf("username" to user, "password" to password))
        }
        val home = client.page(StudentPortalParser.HOME)
        assertTrue(StudentPortalParser.navigation(home).isNotEmpty())
        val prefs = instrumentation.targetContext.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
        prefs.edit().putString("portal", "student").commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            ready { onView(withText("Menu Student V2")).check(matches(isDisplayed())) }
            capture("home")
            menu("Kartu Rencana Studi")
            onView(withText(startsWith("Total SKS yang Diambil:"))).perform(scrollTo()).check(matches(isDisplayed()))
            capture("krs")
            onView(isAssignableFrom(WebView::class.java)).check(doesNotExist())
            menu("KHS Semester")
            onView(isAssignableFrom(Spinner::class.java)).perform(scrollTo()).check(matches(isDisplayed()))
            capture("semester")
            menu("Data Pribadi Mahasiswa")
            onView(withText(startsWith("NIM:"))).perform(scrollTo()).check(matches(isDisplayed()))
            capture("profile")
            scenario.recreate()
            ready { onView(withText("Menu Student V2")).check(matches(isDisplayed())) }
            onView(withText(startsWith("NIM:"))).perform(scrollTo()).check(matches(isDisplayed()))
            menu("Nilai Murni UTS")
            onView(withText(startsWith("Nilai Murni UTS dapat dilihat"))).check(matches(isDisplayed()))
            capture("gated-grade")
            menu("Jadwal Mengajar Dosen")
            ready { onView(withHint(startsWith("Cari pada "))).check(matches(isDisplayed())) }
            scenario.onActivity { activity ->
                fun firstSchedule(view: android.view.View): android.widget.Button? {
                    if (view is android.widget.Button && view.text.toString() == "Lihat jadwal dosen") return view
                    if (view is android.view.ViewGroup) for (index in 0 until view.childCount) firstSchedule(view.getChildAt(index))?.let { return it }
                    return null
                }
                assertNotNull(firstSchedule(activity.window.decorView))
                firstSchedule(activity.window.decorView)!!.performClick()
            }
            ready { onView(withText("Jadwal dosen")).check(matches(isDisplayed())) }
            capture("lecturer-schedule")
            menu("Buat Surat Riset/Magang (Offline)")
            onView(withText("Tambah anggota surat")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(org.hamcrest.Matchers.allOf(withText("Kirim Pengajuan"), isAssignableFrom(android.widget.Button::class.java))).perform(scrollTo(), click())
            onView(withText("Isi jabatan tujuan.")).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            capture("research-validation")
            onView(withText("Menu Student V2")).perform(scrollTo(), click())
            onView(withText("Masuk ke MyBest melalui BSI")).perform(scrollTo(), click())
            ready { onView(withText("Selamat datang")).check(matches(isDisplayed())) }
            capture("mybest-sso")
            onView(withText("Kode keamanan")).check(doesNotExist())
            onView(isAssignableFrom(WebView::class.java)).check(doesNotExist())
        }
    }
}
