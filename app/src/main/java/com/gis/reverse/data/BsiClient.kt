package com.gis.reverse.data

import org.jsoup.nodes.Document
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.net.URLDecoder
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

interface SessionCookies {
    fun get(url: String): String
    fun put(url: String, cookie: String)
    fun clear()
}

class BsiClient(
    private val cookies: SessionCookies,
    private val origin: String = PortalParser.ORIGIN,
    private val authenticatedPath: String = "/user/dashboard",
    private val loginDetector: (Document) -> Boolean = PortalParser::isLogin,
    private val authenticatedDetector: (Document) -> Boolean = { it.selectFirst(".user-display-name") != null }
) {
    data class Response(
        val status: Int,
        val html: String,
        val url: String,
        val location: String?,
        val redirected: Boolean = false
    )

    fun submit(
        action: String,
        method: String,
        sourceUrl: String,
        fields: List<Pair<String, String>>,
        files: List<UploadPart> = emptyList(),
        multipart: Boolean = false
    ): Response = exchangeForm(action, method, sourceUrl, fields, files, multipart) { connection, url, redirected ->
        val status = connection.responseCode
        val body = readText(if (status >= 400) connection.errorStream else connection.inputStream)
        val doc = PortalParser.document(body, url)
        if (loginDetector(doc)) throw SessionExpired()
        Response(status, body, url, null, redirected)
    }

    fun submitJson(action: String, sourceUrl: String, csrf: String, json: String): Response {
        requireHeaderValue(csrf)
        if (csrf.isBlank()) throw PortalError("Token form belum tersedia. Muat ulang halaman.")
        return exchangeForm(action, "POST", sourceUrl, emptyList(), emptyList(), false, json, csrf) { connection, url, redirected ->
            val body = readText(if (connection.responseCode >= 400) connection.errorStream else connection.inputStream)
            if (loginDetector(PortalParser.document(body, url))) throw SessionExpired()
            Response(connection.responseCode, body, url, null, redirected)
        }
    }

    fun downloadForm(
        action: String,
        method: String,
        sourceUrl: String,
        fields: List<Pair<String, String>>,
        receive: (fileName: String, mimeType: String, input: InputStream) -> Unit
    ) {
        val response = exchangeForm(action, method, sourceUrl, fields, emptyList(), false) { connection, url, redirected ->
            val contentType = connection.contentType.orEmpty().substringBefore(';').trim().lowercase()
            if (connection.responseCode >= 400 || contentType.contains("html") || contentType.contains("json")) {
                val text = readText(if (connection.responseCode >= 400) connection.errorStream else connection.inputStream)
                if (loginDetector(PortalParser.document(text, url))) throw SessionExpired()
                throw PortalError("BSI belum mengirim berkas unduhan. Periksa pesan pada form lalu coba lagi.")
            }
            connection.inputStream.buffered().use { stream ->
                stream.mark(8192)
                val probe = ByteArray(8192)
                val count = stream.read(probe)
                stream.reset()
                val start = if (count > 0) String(probe, 0, count, Charsets.UTF_8).trimStart().lowercase() else ""
                if (start.startsWith("<!doctype html") || start.startsWith("<html") || start.startsWith("<form")) {
                    val text = readText(stream)
                    if (loginDetector(PortalParser.document(text, url))) throw SessionExpired()
                    throw PortalError("Respons BSI berupa halaman, bukan berkas unduhan.")
                }
                receive(downloadName(connection.getHeaderField("Content-Disposition"), contentType), contentType.ifBlank { "application/octet-stream" }, stream)
            }
            Response(connection.responseCode, "", url, null, redirected)
        }
        if (response.location != null) throw PortalError("Unduhan diarahkan ke layanan lain. Sesi tidak diteruskan.")
    }

    private fun exchangeForm(
        action: String,
        method: String,
        sourceUrl: String,
        fields: List<Pair<String, String>>,
        files: List<UploadPart>,
        multipart: Boolean,
        jsonBody: String? = null,
        jsonCsrf: String? = null,
        receive: (HttpURLConnection, String, Boolean) -> Response
    ): Response {
        var current = resolve(action)
        val referer = resolve(sourceUrl).substringBefore('#')
        if (!sameOrigin(referer)) throw PortalError("Halaman asal form tidak sesuai dengan server BSI.")
        var verb = method.uppercase()
        if (verb != "GET" && verb != "POST") throw PortalError("Metode form belum didukung. Muat ulang halaman BSI.")
        if (verb == "GET" && files.isNotEmpty()) throw PortalError("Berkas hanya dapat dikirim melalui form POST.")
        fields.forEach { (name, _) -> requireHeaderValue(name) }
        files.forEach {
            requireHeaderValue(it.fieldName)
            if (it.size > MAX_UPLOAD_BYTES) throw PortalError("Berkas terlalu besar untuk dikirim dari aplikasi.")
        }
        if (verb == "GET" && fields.isNotEmpty()) {
            val encoded = encodeFields(fields)
            current = current.substringBefore('#') + (if (current.substringBefore('#').contains('?')) "&" else "?") + encoded
        }
        var redirected = false
        repeat(8) {
            if (!sameOrigin(current)) throw PortalError("Form diarahkan ke layanan lain. Data tidak dikirim.")
            val connection = URL(current).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 20_000
                connection.readTimeout = 25_000
                connection.requestMethod = verb
                connection.setRequestProperty("User-Agent", "MyBestAndroid/1.0 (Android; student portal)")
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json,*/*")
                connection.setRequestProperty("Cookie", cookies.get(current))
                connection.setRequestProperty("Referer", referer)
                if (verb == "POST") {
                    val base = URI(origin)
                    connection.setRequestProperty("Origin", "${base.scheme}://${base.rawAuthority}")
                    connection.doOutput = true
                    if (jsonBody != null) {
                        val payload = jsonBody.toByteArray(Charsets.UTF_8)
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.setRequestProperty("Accept", "application/json")
                        connection.setRequestProperty("X-CSRF-TOKEN", jsonCsrf)
                        connection.setFixedLengthStreamingMode(payload.size)
                        connection.outputStream.use { it.write(payload) }
                    } else if (multipart || files.isNotEmpty()) {
                        val boundary = "MyBest-${UUID.randomUUID()}"
                        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                        connection.setChunkedStreamingMode(64 * 1024)
                        connection.outputStream.use { writeMultipart(it, boundary, fields, files) }
                    } else {
                        val payload = encodeFields(fields).toByteArray(Charsets.UTF_8)
                        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        connection.setFixedLengthStreamingMode(payload.size)
                        connection.outputStream.use { it.write(payload) }
                    }
                }
                val status = connection.responseCode
                connection.headerFields.filterKeys { it?.equals("Set-Cookie", true) == true }
                    .values.flatten().forEach { cookies.put(current, it) }
                checkSessionStatus(status)
                val location = connection.getHeaderField("Location")
                if (status in 300..399 && location != null) {
                    var next = URI(current).resolve(location).toString()
                    if (next.startsWith("http://elearning.bsi.ac.id/")) next = next.replaceFirst("http:", "https:")
                    if (!sameOrigin(next)) return Response(status, "", current, next, redirected)
                    if (URI(next).path == URI(resolve("/login")).path) throw SessionExpired()
                    // Re-sending a write can duplicate attendance, discussion posts or uploads.
                    if (verb == "POST" && (status == 307 || status == 308)) throw PortalError("BSI meminta pengiriman ulang. Periksa data terbaru sebelum mengirim lagi.")
                    current = next
                    verb = "GET"
                    redirected = true
                } else {
                    if (status >= 500) throw PortalError("Server BSI bermasalah (HTTP $status). Status pengiriman belum dapat dipastikan; muat ulang data sebelum mengirim lagi.")
                    if (status >= 400 && status != 422) throw PortalError("Permintaan form ditolak BSI (HTTP $status). Muat ulang halaman untuk memperoleh form terbaru.")
                    return receive(connection, current, redirected)
                }
            } finally { connection.disconnect() }
        }
        throw PortalError("Pengalihan form berulang. Muat ulang data sebelum mengirim lagi.")
    }

    private fun resolve(path: String): String = if (path.startsWith("/") && !path.startsWith("//")) origin.trimEnd('/') + path else URI(origin).resolve(path).toString()

    private fun encodeFields(fields: List<Pair<String, String>>) = fields.joinToString("&") { (name, value) ->
        "${URLEncoder.encode(name, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
    }

    private fun requireHeaderValue(value: String) {
        if (value.any { it == '\r' || it == '\n' || it == '\u0000' }) throw PortalError("Nama field form tidak valid.")
    }

    private fun writeMultipart(output: OutputStream, boundary: String, fields: List<Pair<String, String>>, files: List<UploadPart>) {
        fun write(text: String) { output.write(text.toByteArray(Charsets.UTF_8)) }
        fun quoted(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
        fields.forEach { (name, value) ->
            write("--$boundary\r\nContent-Disposition: form-data; name=\"${quoted(name)}\"\r\n\r\n$value\r\n")
        }
        files.forEach { file ->
            val mime = file.mimeType.takeIf { it.matches(Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+")) } ?: "application/octet-stream"
            val name = safeFilename(file.fileName, "lampiran")
            write("--$boundary\r\nContent-Disposition: form-data; name=\"${quoted(file.fieldName)}\"; filename=\"${quoted(name)}\"\r\nContent-Type: $mime\r\n\r\n")
            file.openStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_UPLOAD_BYTES || (file.size >= 0 && total > file.size)) throw PortalError("Ukuran berkas berubah atau melebihi batas unggahan. Pilih kembali berkas.")
                    output.write(buffer, 0, count)
                }
                if (file.size >= 0 && total != file.size) throw PortalError("Berkas belum terbaca lengkap. Pilih kembali berkas sebelum mengirim.")
            }
            write("\r\n")
        }
        write("--$boundary--\r\n")
    }

    private fun readText(input: InputStream?): String = input?.bufferedReader(Charsets.UTF_8)?.use { reader ->
        val text = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            text.append(buffer, 0, count)
            if (text.length > 4_000_000) throw PortalError("Respons BSI terlalu besar untuk ditampilkan.")
        }
        text.toString()
    }.orEmpty()

    private fun downloadName(disposition: String?, mime: String): String {
        val extended = disposition?.let { Regex("filename\\*\\s*=\\s*(?:UTF-8'')?([^;]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.trim()?.trim('"') }
        val decoded = extended?.let { runCatching { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.getOrNull() }
        val simple = disposition?.let { Regex("filename\\s*=\\s*(?:\"([^\"]*)\"|([^;]+))", RegexOption.IGNORE_CASE).find(it) }
            ?.let { it.groupValues[1].ifBlank { it.groupValues[2].trim() } }
        val fallback = if (mime == "application/pdf") "lampiran.pdf" else "lampiran"
        return safeFilename(decoded ?: simple ?: fallback, fallback)
    }

    private fun safeFilename(value: String, fallback: String): String = value.substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[\\p{Cntrl}:*?\"<>|]"), "_").trim().trim('.').take(160).ifBlank { fallback }

    private companion object { const val MAX_UPLOAD_BYTES = 100L * 1024 * 1024 }

    fun request(path: String, fields: Map<String, String>? = null): Response = request(path, fields, true)

    private fun request(path: String, fields: Map<String, String>?, checkCsrfSession: Boolean): Response {
        var current = if (path.startsWith("/") && !path.startsWith("//")) origin.trimEnd('/') + path else URI(origin).resolve(path).toString()
        var payload = fields
        repeat(8) {
            if (!sameOrigin(current)) throw PortalError("Tautan ini menuju layanan lain. Buka melalui halaman resmi.")
            val connection = URL(current).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 20_000
                connection.readTimeout = 25_000
                connection.setRequestProperty("User-Agent", "MyBestAndroid/1.0 (Android; student portal)")
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json")
                connection.setRequestProperty("Cookie", cookies.get(current))
                if (payload != null) {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    connection.setRequestProperty("Origin", origin)
                    connection.setRequestProperty("Referer", "$origin/login")
                    val encoded = payload!!.entries.joinToString("&") { (key, value) ->
                        "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
                    }.toByteArray(Charsets.UTF_8)
                    connection.outputStream.use { stream -> stream.write(encoded) }
                }
                val status = connection.responseCode
                connection.headerFields.filterKeys { it?.equals("Set-Cookie", true) == true }
                    .values.flatten().forEach { cookies.put(current, it) }
                if (checkCsrfSession) checkSessionStatus(status)
                else if (status == 401 || status == 419) throw SessionExpired()
                val location = connection.getHeaderField("Location")
                if (status in 300..399 && location != null) {
                    var destination = URI(current).resolve(location).toString()
                    // The legacy /dashboard redirect points to HTTP even after a valid HTTPS login.
                    if (destination.startsWith("http://elearning.bsi.ac.id/")) destination = destination.replaceFirst("http:", "https:")
                    if (!sameOrigin(destination)) return Response(status, "", current, destination)
                    if (URI(destination).path == "/login" && fields == null && URI(current).path != "/login") throw SessionExpired()
                    current = destination
                    if (status != 307 && status != 308) payload = null
                } else {
                    val input = if (status >= 400) connection.errorStream else connection.inputStream
                    val body = input?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                        val text = StringBuilder()
                        val buffer = CharArray(8192)
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            text.append(buffer, 0, count)
                            if (text.length > 4_000_000) throw PortalError("Halaman terlalu besar. Buka situs resmi untuk melanjutkan.")
                        }
                        text.toString()
                    }.orEmpty()
                    if (status >= 500) throw PortalError("Server BSI sedang bermasalah (HTTP $status). Coba lagi nanti.")
                    if (status >= 400) throw PortalError("Halaman tidak dapat dibuka (HTTP $status). Muat ulang jadwal Anda.")
                    return Response(status, body, current, null)
                }
            } finally { connection.disconnect() }
        }
        throw PortalError("Pengalihan halaman berulang. Coba masuk kembali.")
    }

    fun page(path: String): Document {
        val response = request(path)
        if (response.location != null) throw PortalError("Layanan ini dibuka melalui halaman resmi.")
        val doc = PortalParser.document(response.html, response.url)
        if (loginDetector(doc)) throw SessionExpired()
        return doc
    }

    private fun checkSessionStatus(status: Int) {
        if (status == 401) throw SessionExpired()
        if (status != 419) return
        // A rotated form token can expire while the authenticated session is still valid.
        val active = try {
            val response = request(authenticatedPath, null, false)
            val doc = PortalParser.document(response.html, response.url)
            if (response.location != null) {
                throw PortalError("Token form berubah. Sesi belum dapat diperiksa. Muat ulang halaman sebelum mengirim kembali.")
            }
            if (loginDetector(doc)) false
            else if (authenticatedDetector(doc)) true
            else throw PortalError("Dashboard belum dapat memastikan sesi.")
        } catch (error: SessionExpired) {
            false
        } catch (error: Exception) {
            throw PortalError("Token form berubah. Sesi belum dapat diperiksa. Muat ulang halaman sebelum mengirim kembali.")
        }
        if (!active) throw SessionExpired()
        throw PortalError("Sesi masih aktif, tetapi token form sudah berubah. Muat ulang halaman lalu periksa isian sebelum mengirim kembali.")
    }
    fun challenge(): LoginChallenge {
        val response = request("/login")
        return PortalParser.challenge(PortalParser.document(response.html, response.url))
    }
    fun login(challenge: LoginChallenge, username: String, password: String, captcha: String): Document {
        val response = request("/login", mapOf("_token" to challenge.csrf, "username" to username, "password" to password, "captcha_answer" to captcha.trim().uppercase()))
        val result = PortalParser.document(response.html, response.url)
        if (PortalParser.isLogin(result)) throw PortalError(PortalParser.challenge(result).error.ifBlank { "Login belum berhasil. Periksa NIM, password, dan kode keamanan." })
        val dashboard = page("/user/dashboard")
        if (dashboard.selectFirst(".user-display-name") == null) throw PortalError("Dashboard mahasiswa belum tersedia. Buka situs resmi untuk memeriksa akun.")
        return dashboard
    }
    fun logout() {
        val doc = page("/user/dashboard")
        val token = doc.selectFirst("input[name=_token]")?.attr("value").orEmpty()
        if (token.isBlank()) throw PortalError("Token keluar tidak tersedia. Coba lagi.")
        request("/logout", mapOf("_token" to token))
        cookies.clear()
    }
    private fun sameOrigin(url: String): Boolean {
        val base = URI(origin)
        val candidate = URI(url)
        return candidate.scheme == base.scheme && candidate.host == base.host && candidate.port == base.port && candidate.userInfo == null
    }
}
