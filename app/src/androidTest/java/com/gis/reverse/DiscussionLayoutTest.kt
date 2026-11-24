package com.gis.reverse

import android.view.View
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
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
class DiscussionLayoutTest {
    @Test fun composerStaysVisibleWhileCommentsScrollAndKeyboardOpens() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                MainActivity::class.java.getDeclaredField("epoch").apply { isAccessible = true; setInt(activity, getInt(activity) + 1) }
                val url = PortalParser.ORIGIN + "/form-komentarmhs/synthetic"
                val doc = PortalParser.document("""<main><div class="active-user-info"><h5>Pertemuan uji</h5></div>
                    <form action="/send-komentarmhs" method="post"><textarea name="komentar"></textarea>
                    <input type="hidden" name="id_chat" value="TEST"><button>Kirim</button></form></main>""", url)
                val page = NativePortalParser.page(doc).copy(comments = (1..35).map {
                    DiscussionComment("Penulis uji $it", "TEST_$it", "Komentar sintetis untuk memeriksa gulir.", "19:00")
                })
                val routeType = MainActivity::class.java.declaredClasses.first { it.simpleName == "Route" }
                val route = routeType.declaredConstructors.single().apply { isAccessible = true }.newInstance("native", "Komentar uji", url)
                MainActivity::class.java.getDeclaredMethod("renderNative", org.jsoup.nodes.Document::class.java, routeType, NativePage::class.java)
                    .apply { isAccessible = true }.invoke(activity, doc, route, page)
            }
            var top = 0
            onView(withTagValue(equalTo("discussion-composer"))).check { view, _ ->
                val location = IntArray(2); view.getLocationOnScreen(location); top = location[1]
            }
            onView(allOf(isAssignableFrom(ScrollView::class.java), hasSibling(withTagValue(equalTo("discussion-composer"))))).perform(swipeUp(), swipeUp())
            onView(withTagValue(equalTo("discussion-composer"))).check { view, _ ->
                val location = IntArray(2); view.getLocationOnScreen(location); assertEquals(top, location[1])
            }
            onView(withContentDescription("Kirim komentar")).perform(click())
            onView(withText("Isi komentar.")).check(matches(isDisplayed()))
            onView(withHint("Komentar *")).perform(click(), replaceText("Draf lokal, tidak dikirim."))
            onView(withContentDescription("Kirim komentar")).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                val composer = activity.findViewById<View>(android.R.id.content).findViewWithTag<View>("discussion-composer")
                val insets = androidx.core.view.ViewCompat.getRootWindowInsets(composer)!!
                assertTrue(insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()))
                val nav = MainActivity::class.java.getDeclaredField("bottom").apply { isAccessible = true }.get(activity) as View
                assertEquals(View.GONE, nav.visibility)
            }
            onView(withHint("Komentar *")).perform(closeSoftKeyboard())
            onView(withContentDescription("Kirim komentar")).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                val nav = MainActivity::class.java.getDeclaredField("bottom").apply { isAccessible = true }.get(activity) as View
                assertEquals(View.VISIBLE, nav.visibility)
            }
        }
    }
}
