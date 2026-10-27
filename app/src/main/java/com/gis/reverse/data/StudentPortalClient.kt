package com.gis.reverse.data

import org.jsoup.nodes.Document
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder

class StudentPortalClient(
    private val cookies: SessionCookies,
    private val origin: String = StudentPortalParser.ORIGIN
) {
    private val transport = BsiClient(
        cookies, origin,
        authenticatedPath = StudentPortalParser.HOME,
        loginDetector = StudentPortalParser::isLogin,
        authenticatedDetector = ::authenticated
    )

    fun page(path: String): Document = transport.page(path)

    fun read(path: String): BsiClient.Response {
        val response = transport.request(path)
        if (response.location == null && StudentPortalParser.isLogin(PortalParser.document(response.html, response.url))) throw SessionExpired()
        return response
    }

    fun lookupResearchMember(form: NativeForm, nim: String): BsiClient.Response {
        val csrf = form.hidden.firstOrNull { it.first == "_token" }?.second.orEmpty()
        val escaped = buildString {
            nim.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    else -> if (character.code < 32) append("\\u%04x".format(character.code)) else append(character)
                }
            }
        }
        return transport.submitJson("/mahasiswa/get-mahasiswa-by-nim", form.sourceUrl, csrf, "{\"nim\":\"$escaped\"}")
    }

    fun myBestSso(): String {
        val response = transport.request("/mahasiswa/myLMS")
        val destination = response.location ?: run {
            if (StudentPortalParser.isLogin(PortalParser.document(response.html, response.url))) throw SessionExpired()
            throw PortalError("Tautan masuk MyBest belum tersedia. Muat ulang Student V2 lalu coba lagi.")
        }
        val target = try { URI(destination) } catch (_: Exception) {
            throw PortalError("Alamat masuk MyBest dari Student V2 tidak valid.")
        }
        val token = target.rawQuery.orEmpty().split('&').firstOrNull { it.substringBefore('=') == "token" }
            ?.substringAfter('=', "")?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        if (target.scheme != "https" || target.host != "elearning.bsi.ac.id" || target.port !in listOf(-1, 443) ||
            target.userInfo != null || target.fragment != null || target.path != "/authenticate-student" || token.isNullOrBlank()) {
            throw PortalError("Student V2 mengirim tujuan masuk MyBest yang belum didukung. Muat ulang lalu coba lagi.")
        }
        return destination
    }

    fun challenge(): StudentLoginChallenge {
        val response = transport.request("/login")
        if (response.location != null) throw PortalError("Login Student V2 diarahkan ke layanan lain. Coba lagi melalui halaman resmi.")
        return StudentPortalParser.challenge(PortalParser.document(response.html, response.url))
    }

    fun login(challenge: StudentLoginChallenge, fields: List<Pair<String, String>>): Document {
        val form = challenge.form
        val response = try {
            transport.submit(form.action, form.method, form.sourceUrl, fields)
        } catch (_: SessionExpired) {
            throw PortalError("Login Student V2 belum berhasil. Periksa NIM, password, dan kode keamanan jika diminta BSI.")
        }
        // An intended MyLMS page can redirect outside Student after successful sign-in.
        // Verify Student itself without following that external destination.
        val result = if (response.location != null) page(StudentPortalParser.HOME) else PortalParser.document(response.html, response.url)
        if (StudentPortalParser.isLogin(result)) {
            throw PortalError(StudentPortalParser.challenge(result).error.ifBlank { "Login Student V2 belum berhasil. Periksa isian Anda." })
        }
        val home = if (authenticated(result)) result else page(StudentPortalParser.HOME)
        if (!authenticated(home)) throw PortalError("Beranda mahasiswa Student V2 belum dapat diverifikasi. Muat ulang lalu coba lagi.")
        return home
    }

    fun submit(form: NativeForm, fields: List<Pair<String, String>>, files: List<UploadPart> = emptyList()): BsiClient.Response =
        transport.submit(form.action, form.method, form.sourceUrl, fields, files, form.multipart)

    fun downloadForm(form: NativeForm, fields: List<Pair<String, String>>, receive: (String, String, InputStream) -> Unit) =
        transport.downloadForm(form.action, form.method, form.sourceUrl, fields, receive)

    fun logout() {
        try {
            val doc = page(StudentPortalParser.HOME)
            val htmlForm = doc.select("form[action]").firstOrNull { it.absUrl("action").substringBefore('?').endsWith("/logout") }
                ?: throw PortalError("Form keluar Student V2 belum tersedia. Muat ulang lalu coba lagi.")
            val fields = htmlForm.select("input[name]").map { it.attr("name") to it.attr("value") }
            if (fields.none { it.first == "_token" && it.second.isNotBlank() }) throw PortalError("Token keluar Student V2 belum tersedia. Muat ulang lalu coba lagi.")
            if (!htmlForm.attr("method").equals("POST", true)) throw PortalError("Form keluar Student V2 berubah. Muat ulang lalu coba lagi.")
            transport.submit(htmlForm.absUrl("action"), "POST", doc.location(), fields)
            cookies.clear()
        } catch (_: SessionExpired) {
            cookies.clear()
        }
    }

    private fun authenticated(doc: Document): Boolean =
        !StudentPortalParser.isLogin(doc) && doc.selectFirst(".app-sidebar .student-sidebar-profile__name") != null &&
            doc.selectFirst(".app-sidebar .student-sidebar-profile__nim") != null
}
