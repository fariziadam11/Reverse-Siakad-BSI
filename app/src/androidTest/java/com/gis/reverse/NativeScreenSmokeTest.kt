package com.gis.reverse

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack as espressoBack
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Existing-session, read-only UI checks. All submit clicks here fail local validation. */
@RunWith(AndroidJUnit4::class)
class NativeScreenSmokeTest {
    private fun awaitText(value: String) {
        val until = System.currentTimeMillis() + 45_000
        var last: Throwable? = null
        while (System.currentTimeMillis() < until) {
            try { onView(withText(value)).check(matches(withEffectiveVisibility(Visibility.VISIBLE))); return }
            catch (error: AssertionError) { last = error }
            catch (error: androidx.test.espresso.NoMatchingViewException) { last = error }
            Thread.sleep(250)
        }
        throw AssertionError("Native page not ready: $value", last)
    }
    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); Thread.sleep(400)
        val directory = File(instrumentation.targetContext.externalCacheDir, "verification-native").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun openEnglish(action: String) {
        onView(withId(R.id.nav_schedule)).perform(click())
        awaitText("BAHASA INGGRIS I")
        onView(allOf(withText(action), hasSibling(withText("BAHASA INGGRIS I")))).perform(scrollTo(), click())
    }
    @Test fun classesDiscussionsAssignmentsAndProfileUseNativeControls() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("bsiSessionOnly") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val external = instrumentation.addMonitor(IntentFilter(Intent.ACTION_VIEW), Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                awaitText("Selamat datang")
                openEnglish("Buka Kelas")
                awaitText("Belum Mulai")
                onView(withText("Belum Mulai")).perform(scrollTo())
                onView(isAssignableFrom(WebView::class.java)).check(doesNotExist())
                capture("class")
                onView(withText("Penilaian dosen")).perform(scrollTo(), click())
                onView(withText("Menguasai Materi yang Diajarkan (F3-1) *")).perform(scrollTo())
                onView(allOf(withText("Sangat Setuju"), isDescendantOfA(withTagValue(equalTo("native-field:cf31"))))).perform(scrollTo(), click())
                onView(allOf(withText("Sangat Setuju"), isDescendantOfA(withTagValue(equalTo("native-field:cf31"))))).check(matches(isChecked()))
                openEnglish("Diskusi")
                awaitText("Buat diskusi")
                onView(withText("Buat diskusi")).perform(scrollTo(), click())
                onView(withText("Kirim diskusi")).perform(scrollTo(), click())
                onView(withText("Isi judul diskusi.")).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                onView(withHint("Judul Diskusi *")).perform(scrollTo(), replaceText("Native UI draft"), closeSoftKeyboard())
                onView(withHint("Isi Form Diskusi *")).perform(scrollTo(), replaceText("Draft only; never submitted."), closeSoftKeyboard())
                val pickerFilter = IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addDataType("*/*")
                }
                val picker = instrumentation.addMonitor(pickerFilter, Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
                try {
                    onView(withText("Pilih berkas")).perform(scrollTo(), click())
                    assertEquals(1, picker.hits)
                } finally { instrumentation.removeMonitor(picker) }
                scenario.recreate()
                awaitText("Buat diskusi")
                onView(withText("Buat diskusi")).perform(scrollTo(), click())
                onView(withHint("Judul Diskusi *")).perform(scrollTo()).check(matches(withText("Native UI draft")))
                capture("discussion")
                openEnglish("Tugas")
                awaitText("Data Penugasan")
                onView(allOf(withText("Kerjakan"), hasSibling(withText("Meeting 1 Introduction")))).perform(scrollTo(), click())
                awaitText("Kirim Tugas")
                onView(withHint("Link Tugas")).perform(scrollTo(), replaceText("invalid link"), closeSoftKeyboard())
                onView(withText("Kirim Tugas")).perform(scrollTo(), click())
                onView(withText("Masukkan tautan http atau https yang valid.")).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                capture("submission")
                onView(withId(R.id.nav_account)).perform(click())
                onView(withText("Edit profil dan password")).perform(scrollTo(), click())
                awaitText("Simpan Profil")
                onView(withHint("Nama Lengkap *")).perform(scrollTo(), replaceText(""), closeSoftKeyboard())
                onView(withText("Simpan Profil")).perform(scrollTo(), click())
                onView(withText("Isi nama lengkap.")).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                onView(withText("Ubah password")).perform(scrollTo(), click())
                onView(withText("Update Password")).perform(scrollTo(), click())
                onView(withText("Isi password lama.")).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                onView(isAssignableFrom(WebView::class.java)).check(doesNotExist())
                capture("profile")
                onView(withId(R.id.nav_services)).perform(click())
                onView(withText("Student V2")).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                onView(withText("Ujian")).check(doesNotExist())
                assertEquals("No browser redirects during native portal flows", 0, external.hits)
            }
        } finally { instrumentation.removeMonitor(external) }
    }
}
