package com.gis.reverse

import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gis.reverse.data.*
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudentResearchFormTest {
    private fun activateControl() = object : androidx.test.espresso.ViewAction {
        override fun getConstraints() = isAssignableFrom(android.widget.Button::class.java)
        override fun getDescription() = "Activate the native form control"
        override fun perform(ui: androidx.test.espresso.UiController, view: View) { view.performClick(); ui.loopMainThreadUntilIdle() }
    }

    @Test fun oneDayAndMultipleDayLettersSubmitOnlyTheActiveDateAndTimeControls() {
        var submitted: List<Pair<String, String>>? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                MainActivity::class.java.getDeclaredField("epoch").apply { isAccessible = true; setInt(activity, getInt(activity) + 1) }
                val parent = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                activity.setContentView(ScrollView(activity).apply { addView(parent) })
                val url = StudentPortalParser.ORIGIN + "/mahasiswa/surat-riset-tugas"
                val form = NativeForm("synthetic-duration", "Surat tugas", url, "POST", url, emptyList(), listOf(
                    NativeField("lama_pelaksanaan", "Durasi", "radio", "", true, options = listOf(NativeOption("1", "Satu hari", true), NativeOption("2", "Beberapa hari"))),
                    NativeField("tanggal_awal", "Tanggal Awal", "date", "2026-10-27", true),
                    NativeField("tanggal_akhir", "Tanggal Akhir", "date", "2026-10-28", true),
                    NativeField("jam_awal", "Jam Awal", "time", "08:00"),
                    NativeField("jam_akhir", "Jam Akhir", "time", "09:00")
                ), "Kirim durasi uji")
                NativeFormRenderer(activity, NativeFormState(), { _, _ -> }, { _, fields, _ -> submitted = fields }).render(parent, form)
            }
            onView(withText("Kirim durasi uji")).perform(activateControl())
            assertTrue(submitted!!.contains("jam_awal" to "08:00"))
            assertFalse(submitted!!.any { it.first == "tanggal_akhir" })
            onView(withText("Beberapa hari")).perform(click())
            onView(withText("Kirim durasi uji")).perform(activateControl())
            assertTrue(submitted!!.contains("tanggal_akhir" to "2026-10-28"))
            assertFalse(submitted!!.any { it.first.startsWith("jam_") })
        }
    }
    @Test fun conditionalResearchFieldsAndRepeatedMembersPreserveServerFieldNames() {
        var submitted: List<Pair<String, String>>? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                MainActivity::class.java.getDeclaredField("epoch").apply { isAccessible = true; setInt(activity, getInt(activity) + 1) }
                val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 50, 30, 50) }
                val scroll = ScrollView(activity).apply { addView(content) }
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
                    val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.ime())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                    androidx.core.view.WindowInsetsCompat.CONSUMED
                }
                activity.setContentView(scroll)
                androidx.core.view.ViewCompat.requestApplyInsets(scroll)
                val url = StudentPortalParser.ORIGIN + "/mahasiswa/surat-riset/template"
                val form = NativeForm("synthetic-research", "Surat riset", url, "POST", url, listOf("_token" to "TEST_CSRF"), listOf(
                    NativeField("jenis_surat", "Jenis Surat", "select", "", true, options = listOf(NativeOption("", "Pilih jenis"), NativeOption("magang_pkl", "Magang/PKL"), NativeOption("riset", "Izin Riset"))),
                    NativeField("waktu_pelaksanaan", "Waktu Pelaksanaan", "text", "", true, true),
                    NativeField("judul_skripsi", "Judul Skripsi", "text", ""),
                    NativeField("nim[]", "NIM", "text", "111", readOnly = true),
                    NativeField("nama[]", "Nama", "text", "TEST OWNER", readOnly = true),
                    NativeField("prodi[]", "Program Studi", "text", "TEST PROGRAM", readOnly = true),
                    NativeField("hp[]", "No HP", "text", ""),
                    NativeField("jenjang[]", "Jenjang", "text", "TEST DEGREE", readOnly = true)
                ), "Kirim surat uji")
                StudentResearchFormRenderer(activity, NativeFormState(), { _, nim, done ->
                    assertEquals("222", nim)
                    done(mapOf("nama" to "TEST MEMBER", "prodi" to "TEST PROGRAM", "hp" to "", "jenjang" to "TEST DEGREE"))
                }, { _, _ -> }, { _, fields, _ -> submitted = fields }).render(content, form)
            }
            onView(withText("Tambah anggota surat")).perform(scrollTo(), click())
            onView(withHint("NIM anggota")).perform(replaceText("222"), closeSoftKeyboard())
            onView(withText("Cari")).perform(click())
            onView(isAssignableFrom(Spinner::class.java)).perform(scrollTo(), click())
            onData(allOf(`is`(instanceOf(String::class.java)), `is`("Izin Riset"))).perform(click())
            onView(withHint("Waktu Pelaksanaan *")).perform(scrollTo()).check(matches(withText("1-3")))
            onView(withText("Kirim surat uji")).perform(activateControl())
            assertNull(submitted)
            scenario.onActivity { activity ->
                fun errors(view: View): List<String> =
                    (if (view is com.google.android.material.textfield.TextInputLayout) listOfNotNull(view.error?.toString()) else emptyList()) +
                        (if (view is android.view.ViewGroup) (0 until view.childCount).flatMap { errors(view.getChildAt(it)) } else emptyList())
                val messages = errors(activity.window.decorView)
                assertTrue("Expected research title validation; actual errors: $messages", messages.contains("Isi judul skripsi."))
            }
            onView(withHint("Judul Skripsi")).perform(scrollTo(), replaceText("Synthetic research"), closeSoftKeyboard())
            onView(withText("Kirim surat uji")).perform(activateControl())
            assertEquals(listOf("111", "222"), submitted!!.filter { it.first == "nim[]" }.map { it.second })
            assertEquals(listOf("TEST OWNER", "TEST MEMBER"), submitted!!.filter { it.first == "nama[]" }.map { it.second })
            assertTrue(submitted!!.contains("waktu_pelaksanaan" to "1-3"))
            assertTrue(submitted!!.contains("_token" to "TEST_CSRF"))
            assertFalse(submitted!!.any { it.first.startsWith("member:") })
        }
    }
}
