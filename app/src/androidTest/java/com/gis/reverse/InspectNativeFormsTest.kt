package com.gis.reverse

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gis.reverse.data.AndroidSessionCookies
import com.gis.reverse.data.BsiClient
import com.gis.reverse.data.PortalParser
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Reads form contracts with an existing authorized session; never submits a form. */
@RunWith(AndroidJUnit4::class)
class InspectNativeFormsTest {
    @Test fun inspectCurrentStudentForms() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("bsiInspect") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var cookies: AndroidSessionCookies
        instrumentation.runOnMainSync { cookies = AndroidSessionCookies() }
        val client = BsiClient(cookies)
        val pages = JSONObject()
        fun capture(name: String, url: String): org.jsoup.nodes.Document {
            val doc = client.page(url)
            pages.put(name, JSONObject().put("url", doc.location()).put("html", doc.outerHtml()))
            return doc
        }
        capture("dashboard", "/user/dashboard")
        capture("profile", "/profil")
        val schedule = capture("schedule", "/sch")
        val course = PortalParser.courses(schedule).first()
        course.links.firstOrNull { it.title.contains("Buka Kelas", true) }?.let { capture("class", it.url) }
        course.links.firstOrNull { it.title.contains("Diskusi", true) }?.let { capture("discussion", it.url) }
        course.links.firstOrNull { it.title.contains("Tugas", true) }?.let {
            val assignments = capture("assignments", it.url)
            PortalParser.links(PortalParser.main(assignments)).firstOrNull { link -> link.url.contains("/assignment/send/") }?.let { link -> capture("submission", link.url) }
        }
        capture("quizzes", "/exercise")
        capture("toefl", "/toefl")
        capture("substitute", "/kuliah-pengganti")
        File(instrumentation.targetContext.cacheDir, "native-inspection.json").writeText(pages.toString())
    }
}
