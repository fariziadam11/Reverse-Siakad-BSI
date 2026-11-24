package com.gis.reverse

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gis.reverse.data.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class InspectDiscussionTempTest {
    @Test fun inspect() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var cookies: AndroidSessionCookies
        instrumentation.runOnMainSync { cookies = AndroidSessionCookies() }
        val client = BsiClient(cookies)
        val courses = PortalParser.courses(client.page("/sch"))
        val discussion = courses.flatMap { it.links }.filter { it.title.contains("Diskusi", true) }.asSequence().map { client.page(it.url) }.first { PortalParser.links(PortalParser.main(it)).any { link -> link.title.contains("Komentar", true) } }
        val link = PortalParser.links(PortalParser.main(discussion)).first { it.title.contains("Komentar", true) }
        val comments = client.page(link.url)
        File(instrumentation.targetContext.cacheDir, "discussion-inspection.html").writeText(PortalParser.main(comments).outerHtml())
    }
}
