package com.gis.reverse.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object PortalParser {
    const val ORIGIN = "https://elearning.bsi.ac.id"
    fun document(html: String, url: String = ORIGIN): Document = Jsoup.parse(html, url)
    fun isLogin(doc: Document): Boolean = doc.selectFirst("form input[name=username]") != null && doc.selectFirst("input[name=captcha_answer]") != null

    fun validatePage(doc: Document) {
        val path = java.net.URI(doc.location()).path.orEmpty()
        val main = main(doc)
        val valid = when {
            path.endsWith("/user/dashboard") -> doc.select(".user-display-name").text().isNotBlank() && doc.select(".user-identifier").text().isNotBlank()
            path.endsWith("/sch") -> {
                val cards = main.select(".jadwal-card")
                if (cards.isEmpty()) Regex("(?i)(?:belum|tidak) ada jadwal|jadwal (?:kuliah )?belum tersedia").containsMatchIn(main.text())
                else courses(doc).all { it.title.isNotBlank() && it.code.isNotBlank() && it.schedule.isNotBlank() }
            }
            path.contains("/form-diskusimhs/") -> main.selectFirst(".task-list")?.let { list ->
                list.children().all { it.hasClass("task-block") && it.select(".task-name").text().isNotBlank() }
            } == true
            path.contains("/form-komentarmhs/") -> {
                val form = main.select("form[action]").firstOrNull { it.absUrl("action").substringBefore('?').endsWith("/send-komentarmhs") }
                main.selectFirst(".chat-box") != null && form != null &&
                    form.selectFirst("textarea[name=komentar]") != null &&
                    listOf("_token", "id_chat", "id").all { name -> form.selectFirst("input[name=$name]")?.attr("value")?.isNotBlank() == true } &&
                    main.select(".chat-box > li").all { it.selectFirst(".chat-text p") != null && it.selectFirst(".chat-text > .chat-name") != null && it.selectFirst(".chat-avatar .chat-name") != null }
            }
            path.contains("/learning/") -> main.selectFirst(".doc-block, table, iframe[src]") != null
            path.contains("/assignment/") && !path.contains("/assignment/send/") -> main.selectFirst("table") != null
            else -> true
        }
        if (!valid) throw PortalError("Struktur halaman e-learning berubah atau respons belum lengkap. Muat ulang; jika tetap gagal, aplikasi perlu diperbarui.")
    }

    fun challenge(doc: Document): LoginChallenge {
        val token = doc.selectFirst("input[name=_token]")?.attr("value").orEmpty()
        val svg = doc.selectFirst("#captcha-svg-container svg")?.outerHtml().orEmpty()
        if (token.isEmpty() || svg.isEmpty()) throw PortalError("Form login BSI berubah. Buka situs resmi atau coba lagi.")
        return LoginChallenge(token, svg, doc.select(".alert-danger, .text-red-500, .text-red-600, .invalid-feedback").text())
    }
    fun student(doc: Document) = Student(doc.selectFirst(".user-display-name")?.text().orEmpty(), doc.selectFirst(".user-identifier")?.text().orEmpty(), doc.selectFirst(".dropdown-user-card small")?.text().orEmpty())
    fun navigation(doc: Document): List<PortalLink> = doc.select(".sidebar-menu a[href]")
        .filter { it.attr("href") != "#" && !it.absUrl("href").endsWith("/logout") }
        .map { PortalLink(it.text(), it.absUrl("href")) }.distinctBy { it.url }

    fun courses(doc: Document): List<Course> = doc.select(".jadwal-card").map { card ->
        val info = card.select(".jadwal-info-row").associate {
            it.select(".jadwal-info-label").text().ifBlank { it.children().firstOrNull()?.text().orEmpty() }.trim().removeSuffix(":") to
                it.select(".jadwal-info-val").text().ifBlank { it.children().drop(1).joinToString(" ") { value -> value.text() } }
        }
        Course(card.selectFirst(".jadwal-title")?.text().orEmpty().ifBlank { card.selectFirst("h5")?.text().orEmpty() }, info["Kode MTK"].orEmpty(), card.select(".jadwal-kelas-pill strong").text(),
            info["Hari & Jam"].orEmpty().ifBlank { info["Waktu"].orEmpty() }, info["Dosen"].orEmpty(), info["Ruang"].orEmpty(), info["Kampus"].orEmpty(), info["SKS"].orEmpty(),
            !card.hasClass("other-day"), links(card.selectFirst(".jadwal-footer") ?: card))
    }.distinctBy { it.code + it.classroom + it.schedule }

    fun links(element: Element): List<PortalLink> = element.select("a[href]").mapNotNull {
        val raw = it.attr("href")
        val url = it.absUrl("href")
        if (raw.isBlank() || raw.startsWith("#") || !url.startsWith("https://")) null
        else PortalLink(it.text().ifBlank { it.attr("title").ifBlank { "Buka dokumen" } }, url)
    }.distinctBy { it.url }

    fun tables(doc: Document): List<PortalTable> = main(doc).select("table").filter { table -> table.parents().none { it.tagName() == "form" } }.map { table ->
        val headers = table.select("thead tr").firstOrNull()?.select("th, td")?.map { it.text() }.orEmpty()
        val pane = table.parents().firstOrNull { it.hasClass("tab-pane") }
        val tabName = pane?.id()?.let { id -> doc.select("a[href='#$id']").firstOrNull()?.text() }.orEmpty()
        val rows = table.select("tbody > tr").mapNotNull { row ->
            val cells = row.children().filter { it.tagName() == "td" }
            if (cells.isEmpty() || (cells.size == 1 && cells[0].attr("colspan").isNotBlank())) null
            else TableRow(cells.mapIndexedNotNull { index, cell ->
                val label = headers.getOrElse(index) { "Informasi ${index + 1}" }
                val copy = cell.clone().also { it.select("form, button, .dropdown-menu").remove() }.text()
                if (copy.isBlank() || label.equals("No", true) || label.equals("Aksi", true)) null else label to copy
            }, links(row), NativePortalParser.forms(row))
        }
        PortalTable(tabName, rows)
    }
    fun learning(doc: Document): LearningContent {
        val documents = main(doc).select(".doc-block").flatMap { block -> links(block).map { it.copy(title = block.select(".doc-title").text().ifBlank { it.title }) } }
        val videos = main(doc).select("iframe[src]").map { frame ->
            PortalLink(frame.parents().firstOrNull { it.hasClass("pricing-plan") }?.select("h5")?.text().orEmpty().ifBlank { "Video pembelajaran" }, frame.absUrl("src"))
        }
        return LearningContent(documents, videos, tables(doc))
    }
    fun main(doc: Document): Element = doc.selectFirst(".page-content > .main-container") ?: doc.selectFirst(".main-container") ?: doc.selectFirst("main, [role=main]") ?: doc.body()
}
