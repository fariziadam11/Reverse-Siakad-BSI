package com.gis.reverse.data

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder

object StudentPortalParser {
    const val ORIGIN = "https://students.bsi.ac.id"
    const val HOME = "/mahasiswa/beranda"

    fun isLogin(doc: Document): Boolean = doc.select("form").any {
        it.selectFirst("input[name=username]") != null && it.selectFirst("input[name=password]") != null
    }

    fun challenge(doc: Document): StudentLoginChallenge {
        val element = doc.select("form").firstOrNull {
            it.selectFirst("input[name=username]") != null && it.selectFirst("input[name=password]") != null
        } ?: throw PortalError("Form login Student V2 belum tersedia. Muat ulang halaman login.")
        val form = NativePortalParser.forms(doc.body()).firstOrNull { it.action == element.absUrl("action") }
            ?: throw PortalError("Form login Student V2 berubah. Muat ulang halaman login.")
        if (form.method != "POST" || form.hidden.none { it.first == "_token" && it.second.isNotBlank() }) {
            throw PortalError("Token login Student V2 belum tersedia. Muat ulang halaman login.")
        }
        val captcha = form.fields.firstOrNull { it.name.contains("captcha", true) }
        val prompt = captcha?.let { field ->
            val control = element.select("input[name]").firstOrNull { it.attr("name") == field.name }
            control?.parents()?.firstOrNull { it.hasClass("form-group") || it.hasClass("mb-3") }
                ?.clone()?.also { it.select("input,button,script,style").remove() }?.text()
                ?.ifBlank { field.label }.orEmpty().ifBlank { field.label }
        }.orEmpty()
        return StudentLoginChallenge(
            form.copy(title = "Masuk ke Student V2"),
            notices(doc).filter { it.error }.joinToString("\n") { it.text },
            prompt,
            element.selectFirst("#captcha-svg-container svg, .captcha svg")?.outerHtml().orEmpty()
        )
    }

    fun navigation(doc: Document): List<PortalLink> = doc.select(".app-sidebar a.side-menu__item[href]")
        .mapNotNull { link ->
            val raw = link.attr("href").trim()
            val url = link.absUrl("href")
            if (raw.isBlank() || raw.startsWith('#') || !credentialFree(raw) || !sameOrigin(url, doc.location()) || URI(url).path == "/logout") null
            else PortalLink(link.selectFirst(".side-menu__label")?.text().orEmpty().ifBlank { link.text() }, url)
                .takeIf { it.title.isNotBlank() }
        }.distinctBy { it.url }

    fun student(doc: Document): Student {
        val values = facts(main(doc))
        val name = doc.selectFirst(".student-sidebar-profile__name")?.text().orEmpty()
            .ifBlank { values.firstOrNull { it.first.equals("Nama", true) }?.second.orEmpty() }
        val nim = doc.selectFirst(".student-sidebar-profile__nim")?.text().orEmpty().replace(Regex("(?i)^NIM\\s*:\\s*"), "")
            .ifBlank { values.firstOrNull { it.first.equals("NIM", true) }?.second.orEmpty() }
        return Student(name, nim, values.firstOrNull { it.first.contains("email", true) }?.second.orEmpty())
    }

    fun page(doc: Document): NativePage {
        val main = main(doc)
        val facts = facts(main)
        val forms = NativePortalParser.forms(main).filter { form ->
            main.select("form").firstOrNull { it.absUrl("action").ifBlank { doc.location() } == form.action }
                ?.parents()?.none { it.tagName() == "table" } != false
        }.map { form ->
            val path = URI(form.action).path.orEmpty()
            form.copy(title = when {
                path.endsWith("/password") -> "Ganti password Student V2"
                path.endsWith("/khs-semester") -> "Pilih semester"
                path.endsWith("/perubahan-biodata") -> "Pengajuan perubahan biodata"
                else -> form.title
            })
        }
        val clean = main.clone()
        clean.select("script,style,form,table,.breadcrumb,.page-header-breadcrumb,.alert,[role=alert],.main-profile-social-list,dl,[hidden],.d-none").remove()
        clean.select(".modal").filter { it.selectFirst("[data-pkl-field]") != null }.forEach { it.remove() }
        val paragraphSelector = "h1,h2,h3,h4,h5,h6,p,.card-title,.card-text,li,.modal-body"
        val paragraphs = clean.select(paragraphSelector).filter { element ->
            element.select(paragraphSelector).none { it !== element }
        }.map { it.text().trim() }.filter { it.isNotBlank() && it !in listOf("×", "Tutup", "Close") }.distinct()
        val links = links(main).filter { link ->
            main.select("a[href]").firstOrNull { it.absUrl("href") == link.url }?.parents()
                ?.none { it.tagName() == "table" || it.tagName() == "form" || it.hasClass("breadcrumb") } != false
        }.toMutableList()
        main.select("iframe[src]").forEach { frame ->
            val url = frame.absUrl("src")
            if (credentialFree(frame.attr("src")) && safeUrl(url, doc.location())) links += PortalLink(frame.attr("title").ifBlank { "Buka dokumen" }, url)
        }
        return NativePage(facts, paragraphs, emptyList(), notices(doc), forms, links.distinctBy { it.url }, tables(main))
    }

    private fun main(doc: Document): Element = doc.selectFirst(".app-content") ?: doc.selectFirst("main") ?: doc.body()

    private fun facts(root: Element): List<Pair<String, String>> {
        val visible = root.clone().also { copy ->
            copy.select(".modal").filter { it.selectFirst("[data-pkl-field]") != null }.forEach { it.remove() }
            copy.select("[hidden],.d-none").remove()
        }
        val facts = mutableListOf<Pair<String, String>>()
        visible.select(".main-profile-social-list .media-body").forEach { item ->
            val label = item.children().firstOrNull()?.text().orEmpty()
            val value = item.children().drop(1).joinToString(" ") { it.text() }
            if (label.isNotBlank() && value.isNotBlank()) facts += label to value
        }
        visible.select("input[readonly],input[disabled],select[disabled],textarea[readonly],textarea[disabled],input:not([name])").forEach { input ->
            if (input.attr("type").lowercase() in listOf("password", "hidden", "button", "submit", "checkbox", "radio")) return@forEach
            val id = input.id()
            val label = visible.select("label").firstOrNull { id.isNotBlank() && it.attr("for") == id }?.text()
                ?: input.parents().firstOrNull { it.hasClass("row") || it.hasClass("form-group") }?.selectFirst("label")?.text().orEmpty()
            val value = when (input.tagName()) {
                "select" -> input.select("option[selected]").text().ifBlank { input.selectFirst("option")?.text().orEmpty() }
                "textarea" -> input.text()
                else -> input.attr("value")
            }.replace(Regex("\\s+"), " ").trim()
            if (label.isNotBlank() && value.isNotBlank()) facts += label.trim().trimEnd(':') to value
        }
        visible.select("dl dt").forEach { term ->
            val value = term.nextElementSibling()?.takeIf { it.tagName() == "dd" }?.text().orEmpty()
            if (term.text().isNotBlank() && value.isNotBlank()) facts += term.text().trimEnd(':') to value
        }
        return facts.distinct()
    }

    private fun tables(root: Element): List<PortalTable> = root.select("table").filter { table ->
        table.parents().none { it.tagName() == "form" || it.tagName() == "table" }
    }.map { table ->
        val headers = tableHeaders(table)
        val rows = table.select("tbody > tr, tfoot > tr").filter { row -> row.parents().firstOrNull { it.tagName() == "table" } === table }.mapNotNull { row ->
            val cells = row.children().filter { it.tagName() == "td" || it.tagName() == "th" }
            if (cells.isEmpty()) return@mapNotNull null
            val rowLinks = (links(row) + lecturerScheduleLinks(row)).distinctBy { it.url }
            val fields = mutableListOf<Pair<String, String>>()
            val texts = cells.map { cell ->
                cell.clone().also { it.select("form,button,.dropdown-menu").remove() }.text()
            }
            if (cells.any { (it.attr("colspan").toIntOrNull() ?: 1) > 1 }) {
                val nonempty = texts.filter { it.isNotBlank() }
                if (nonempty.size == 2) fields += nonempty[0] to nonempty[1]
                else if (cells.size == 3 && texts[1].isNotBlank() && texts[2].isNotBlank()) {
                    val labels = lines(cells[1]); val values = lines(cells[2])
                    if (texts[0].isNotBlank()) fields += "Informasi" to texts[0]
                    if (labels.size == values.size) fields += labels.zip(values) else fields += texts[1] to texts[2]
                } else nonempty.forEach { fields += "Informasi" to it }
            } else if (headers.isEmpty() && texts.size in 2..3 && texts.first().isNotBlank()) {
                fields += texts.first().trimEnd(':') to texts.drop(1).filter { it.isNotBlank() && it != ":" }.joinToString(" ")
            } else {
                var column = 0
                cells.forEachIndexed { index, cell ->
                    val header = headers.getOrElse(column) { "Informasi ${column + 1}" }
                    val value = texts[index]
                    if (value.isNotBlank() && !header.trimEnd('.').equals("No", true) && !header.equals("Aksi", true)) fields += header to value
                    column += cell.attr("colspan").toIntOrNull() ?: 1
                }
            }
            val forms = NativePortalParser.forms(row)
            if (fields.isEmpty() && rowLinks.isEmpty() && forms.isEmpty()) null else TableRow(fields, rowLinks, forms)
        }
        val title = table.selectFirst("caption")?.text().orEmpty().ifBlank {
            table.parents().firstOrNull { it.hasClass("card") }?.selectFirst(".card-header .card-title,.card-header h5,.card-header h4")?.text().orEmpty()
        }
        PortalTable(title, rows)
    }

    private fun tableHeaders(table: Element): List<String> {
        val grid = mutableListOf<MutableList<String?>>()
        table.select("thead > tr").forEachIndexed { y, row ->
            while (grid.size <= y) grid.add(mutableListOf())
            var x = 0
            row.children().filter { it.tagName() == "th" || it.tagName() == "td" }.forEach { cell ->
                while (grid[y].getOrNull(x) != null) x++
                val width = (cell.attr("colspan").toIntOrNull() ?: 1).coerceIn(1, 100)
                val height = (cell.attr("rowspan").toIntOrNull() ?: 1).coerceIn(1, 100)
                for (dy in 0 until height) {
                    while (grid.size <= y + dy) grid.add(mutableListOf())
                    for (dx in 0 until width) {
                        while (grid[y + dy].size <= x + dx) grid[y + dy].add(null)
                        grid[y + dy][x + dx] = cell.text()
                    }
                }
                x += width
            }
        }
        return (0 until (grid.maxOfOrNull { it.size } ?: 0)).map { column ->
            grid.mapNotNull { it.getOrNull(column) }.filter { it.isNotBlank() }.distinct().joinToString(" / ")
        }
    }

    private fun lines(cell: Element): List<String> = cell.clone().also { copy ->
        copy.select("br,hr").forEach { it.before("\n") }
    }.wholeText().lines().map { it.trim() }.filter { it.isNotBlank() }

    private fun lecturerScheduleLinks(row: Element): List<PortalLink> {
        val source = row.ownerDocument()?.location().orEmpty()
        return row.select(".lihat-jadwal[data-kd]").mapNotNull { button ->
            val code = button.attr("data-kd")
            if (code.isBlank() || button.hasAttr("disabled") || button.attr("aria-disabled") == "true") return@mapNotNull null
            val encoded = URLEncoder.encode(code, "UTF-8").replace("+", "%20")
            val url = runCatching { URI(source).resolve("/get-jadwal-dosen/$encoded").toASCIIString() }.getOrNull()
                ?: return@mapNotNull null
            if (!sameOrigin(url, source)) null else PortalLink("Lihat jadwal dosen", url)
        }
    }

    private fun links(root: Element): List<PortalLink> = root.select("a[href]").mapNotNull { link ->
        val raw = link.attr("href").trim()
        val url = link.absUrl("href")
        if (raw.isBlank() || raw.startsWith('#') || !credentialFree(raw) || !safeUrl(url, root.ownerDocument()?.location().orEmpty()) || link.hasClass("disabled") || link.attr("aria-disabled") == "true") null
        else PortalLink(link.text().ifBlank { link.attr("title").ifBlank { "Buka dokumen" } }, url)
    }.distinctBy { it.url }

    fun notices(doc: Document): List<NativeNotice> {
        val messages = NativePortalParser.notices(doc).toMutableList()
        doc.select(".alert-danger,.alert-warning").forEach { alert ->
            if (alert.text().isNotBlank()) messages += NativeNotice(alert.text(), true)
        }
        doc.select("script:not([src])").forEach { script ->
            val source = script.data()
            if (!source.contains("Swal.fire") && !source.contains("sessionAlerts.push")) return@forEach
            val error = Regex("(?:icon|type)\\s*:\\s*['\"](?:error|warning)['\"]").containsMatchIn(source)
            Regex("\\btext\\s*:\\s*('(?:\\\\.|[^'\\\\])*'|\"(?:\\\\.|[^\"\\\\])*\")").findAll(source).forEach { match ->
                val value = jsString(match.groupValues[1])
                if (value.isNotBlank()) messages += NativeNotice(value, error)
            }
        }
        return messages.distinctBy { it.text }
    }

    private fun jsString(quoted: String): String {
        val text = quoted.substring(1, quoted.length - 1)
        return Regex("\\\\(?:u[0-9a-fA-F]{4}|[\\s\\S])").replace(text) { match ->
            when (val code = match.value.substring(1)) {
                "n" -> "\n"
                "r" -> "\r"
                "t" -> "\t"
                else -> if (code.startsWith('u') && code.length == 5) code.drop(1).toInt(16).toChar().toString() else code
            }
        }
    }

    private fun safeUrl(url: String, source: String): Boolean = runCatching {
        val uri = URI(url)
        !uri.host.isNullOrBlank() && uri.userInfo == null &&
            (uri.scheme == "https" || (uri.scheme == "http" && sameOrigin(url, source)))
    }.getOrDefault(false)

    private fun credentialFree(raw: String): Boolean = runCatching { URI(raw.trim()).rawUserInfo == null }.getOrDefault(false)

    private fun sameOrigin(url: String, source: String): Boolean = runCatching {
        val a = URI(url); val b = URI(source)
        a.scheme in listOf("https", "http") && a.scheme == b.scheme && a.host == b.host && a.port == b.port && a.userInfo == null
    }.getOrDefault(false)
}
