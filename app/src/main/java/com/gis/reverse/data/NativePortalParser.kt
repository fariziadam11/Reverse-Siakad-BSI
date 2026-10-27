package com.gis.reverse.data

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

object NativePortalParser {
    fun page(doc: Document): NativePage {
        val main = PortalParser.main(doc)
        val forms = forms(main).filter { form ->
            main.select("form").firstOrNull { it.absUrl("action").ifBlank { doc.location() } == form.action }
                ?.parents()?.none { it.tagName() == "table" } != false
        }
        val facts = main.select(".stats-detail").mapNotNull {
            val label = it.select("p").text()
            val value = it.select("h5").text()
            if (label.isBlank() || value.isBlank()) null else label to value
        }
        val clean = main.clone()
        clean.select("script, style, form, .modal, table, .nav-tabs, .alert, [role=alert], .stats-detail, .doc-block").remove()
        val paragraphs = clean.select("h1, h2, h3, h4, h5, h6, p, .task-list > *, .rule-text").map { it.text() }
            .filter { it.isNotBlank() && it != "×" && it !in facts.map { fact -> fact.second } }
            .distinct()
        val statuses = clean.select("button:not([type=submit])").filter { !it.hasAttr("data-toggle") && !it.hasAttr("data-dismiss") }
            .map { it.text() }.filter { it.isNotBlank() }.distinct()
        val linkCopy = main.clone().also { it.select("form, .modal").remove() }
        return NativePage(facts, paragraphs, statuses, notices(doc), forms, PortalParser.links(linkCopy), PortalParser.tables(doc))
    }

    fun notices(doc: Document): List<NativeNotice> = doc.select(".alert, [role=alert], .flash-tambah, .flash-error, .invalid-feedback, .text-red-500, .text-red-600").mapNotNull {
        val content = it.attr("data-flashdata").ifBlank { it.attr("data-flasherror") }.ifBlank { it.text() }.trim()
        if (content.isBlank()) null else NativeNotice(content, it.hasClass("alert-danger") || it.hasClass("flash-error") || it.hasClass("invalid-feedback") || it.className().contains("text-red"))
    }.distinctBy { it.text }

    fun forms(root: Element): List<NativeForm> = root.select("form").filter { !it.attr("action").endsWith("/logout") }.mapIndexedNotNull { index, form ->
        val action = form.absUrl("action").ifBlank { form.ownerDocument()?.location().orEmpty() }
        val source = form.ownerDocument()?.location().orEmpty()
        if (!isAllowed(action, source)) return@mapIndexedNotNull null
        val controls = form.select("input[name], textarea[name], select[name]").filter { !it.hasAttr("disabled") && it.parents().none { parent -> parent.tagName() == "fieldset" && parent.hasAttr("disabled") } }
        val hidden = controls.filter { it.attr("type").equals("hidden", true) }.map { it.attr("name") to it.attr("value") }
        val visible = controls.filter { !it.attr("type").lowercase().matches(Regex("hidden|submit|reset|button|image")) }
        val fields = mutableListOf<NativeField>()
        val grouped = mutableSetOf<String>()
        visible.forEach { input ->
            val name = input.attr("name")
            val type = when (input.tagName()) { "textarea" -> "textarea"; "select" -> "select"; else -> input.attr("type").lowercase().ifBlank { "text" } }
            if ((type == "radio" || type == "checkbox") && !grouped.add(name)) return@forEach
            val group = if (type == "radio" || type == "checkbox") visible.filter { it.attr("name") == name } else listOf(input)
            val label = label(input, form, type == "radio")
            val context = input.parents().firstOrNull { it.hasClass("form-group") } ?: form
            val help = context.select("code, small, .form-text").text()
            val fileHelp = if (type == "file") form.select("code, small, .form-text").text() else help
            val size = Regex("(?:max(?:imal)?|maks(?:imal)?)\\s*:?\\s*(\\d+(?:[.,]\\d+)?)\\s*(MB|KB)", RegexOption.IGNORE_CASE).find(fileHelp)
            val maxBytes = size?.let { (it.groupValues[1].replace(',', '.').toDouble() * if (it.groupValues[2].equals("MB", true)) 1024 * 1024 else 1024).toLong() }
            val options = when (type) {
                "radio", "checkbox" -> group.map { option -> NativeOption(option.attr("value").ifBlank { "on" }, optionLabel(option, form), option.hasAttr("checked"), option.hasAttr("disabled")) }
                "select" -> input.select("option").map { option -> NativeOption(option.attr("value").ifBlank { if (option.hasAttr("value")) "" else option.text() }, option.text(), option.hasAttr("selected"), option.hasAttr("disabled")) }
                else -> emptyList()
            }
            val required = group.any { it.hasAttr("required") } || label.contains('*')
            val accept = input.attr("accept").ifBlank { if (type == "file" && fileHelp.contains("PDF", true)) "application/pdf" else "" }
            fields += NativeField(name, label.replace("*", "").trim().ifBlank { input.attr("placeholder").ifBlank { name } }, type,
                if (type == "textarea") input.wholeText() else input.attr("value"), required, input.hasAttr("readonly"), options,
                input.hasAttr("multiple"), input.attr("maxlength").toIntOrNull(), input.attr("minlength").toIntOrNull(), accept, maxBytes, fileHelp)
        }
        val submit = form.selectFirst("button[type=submit], input[type=submit], button:not([type])")
            ?: form.parents().firstOrNull { it.hasClass("modal-content") }?.selectFirst(".modal-footer button[type=submit]")
        val path = URI(action).path.orEmpty()
        val title = when {
            path == "/foto-profil/update" -> "Edit profil"
            path == "/profil/update" -> "Ubah password"
            path.contains("tambah-diskusi") -> "Buat diskusi"
            path == "/assignment" -> "Kirim tugas"
            path.contains("kuisoner") -> "Penilaian dosen"
            path.contains("download") -> "Unduh lampiran"
            else -> form.parents().firstOrNull { it.hasClass("modal-content") }?.select(".modal-title")?.text().orEmpty()
                .ifBlank { form.parents().firstOrNull { it.hasClass("card") }?.select(".card-header h5, .card-header h4")?.text().orEmpty() }
                .ifBlank { submit?.text().orEmpty().ifBlank { "Form BSI" } }
        }
        val isDownload = path.contains("download", true)
        val submitLabel = when {
            path.contains("tambah-diskusi") -> "Kirim diskusi"
            isDownload -> "Unduh lampiran"
            else -> submit?.text()?.ifBlank { submit.attr("value") }.orEmpty().ifBlank { if (path.contains("absen", true)) "Kirim presensi" else "Kirim" }
        }
        val notes = form.select("code, .form-text, .pwd-text").map { it.text() }.filter { it.isNotBlank() }.distinct()
        if (hidden.isEmpty() && fields.isEmpty()) return@mapIndexedNotNull null
        val identity = hidden.filter { it.first != "_token" && it.first != "_method" }.joinToString { it.first + "=" + it.second }
        NativeForm("$source|$action|$identity|$index", title, action, form.attr("method").uppercase().ifBlank { "GET" }, source, hidden, fields,
            submitLabel, submit?.attr("name").orEmpty(), submit?.attr("value").orEmpty(), form.attr("enctype").contains("multipart", true),
            form.parents().any { it.hasClass("modal") }, isDownload, submit?.hasAttr("disabled") == true, notes)
    }

    private fun isAllowed(action: String, source: String): Boolean = try {
        val a = URI(action); val b = URI(source)
        a.scheme == b.scheme && a.host == b.host && a.port == b.port && a.userInfo == null
    } catch (_: Exception) { false }

    private fun label(input: Element, form: Element, radio: Boolean): String {
        if (radio) input.parents().firstOrNull { it.tagName() == "tr" }?.selectFirst("td")?.text()?.takeIf { it.isNotBlank() }?.let { return it }
        input.parents().firstOrNull { it.tagName() == "label" }?.text()?.takeIf { it.isNotBlank() }?.let { return it }
        input.previousElementSibling()?.takeIf { it.tagName() == "label" }?.text()?.let { return it }
        val local = input.parents().takeWhile { it !== form }.firstOrNull { it.hasClass("form-group") || it.hasClass("mb-8") || it.hasClass("mb-3") }
        local?.selectFirst("label")?.text()?.let { return it }
        val id = input.id()
        if (id.isNotBlank()) form.select("label").firstOrNull { it.attr("for") == id }?.text()?.let { return it }
        return input.attr("placeholder").ifBlank { input.attr("name") }
    }
    private fun optionLabel(input: Element, form: Element): String {
        input.parents().firstOrNull { it.tagName() == "label" }?.text()?.takeIf { it.isNotBlank() }?.let { return it }
        val id = input.id()
        if (id.isNotBlank()) form.select("label[for]").firstOrNull { it.attr("for") == id }?.text()?.takeIf { it.isNotBlank() }?.let { return it }
        input.nextElementSibling()?.takeIf { it.tagName() == "label" }?.text()?.takeIf { it.isNotBlank() }?.let { return it }
        val td = input.parents().firstOrNull { it.tagName() == "td" }
        if (td != null) {
            val table = td.parents().firstOrNull { it.tagName() == "table" }
            val labels = table?.select("thead tr")?.firstOrNull()?.select("th,td")
            labels?.getOrNull(td.elementSiblingIndex())?.text()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return label(input, form, false).replace("*", "").trim().ifBlank { input.attr("value") }
    }
}
