package com.gis.reverse

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.PictureDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.os.Environment
import android.provider.OpenableColumns
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.ViewModelProvider
import com.caverock.androidsvg.SVG
import com.gis.reverse.data.*
import com.gis.reverse.data.TableRow
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.jsoup.nodes.Document
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val navy = Color.rgb(30, 58, 138)
    private val ink = Color.rgb(15, 23, 42)
    private val muted = Color.rgb(71, 85, 105)
    private val canvas = Color.rgb(245, 247, 251)
    private val purple = Color.rgb(91, 33, 182)
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var client: BsiClient
    private lateinit var studentClient: StudentPortalClient
    private lateinit var vault: CredentialVault
    private lateinit var root: LinearLayout
    private lateinit var body: FrameLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var bottom: BottomNavigationView
    private var epoch = 0
    private var student = Student("", "", "")
    private var courses: List<Course> = emptyList()
    private var navigation: List<PortalLink> = emptyList()
    private var dashboard: Document? = null
    private var route = Route("home", "Beranda", "/user/dashboard")
    private var pendingRoute: Route? = null
    private var pendingStudentRoute: Route? = null
    private var studentLoginVisible = false
    private var continueMyBestAfterStudentLogin = false
    private var studentNavigation: List<PortalLink> = emptyList()
    private var internshipFacts: List<Pair<String, String>> = emptyList()
    private var challenge: LoginChallenge? = null
    private lateinit var formState: NativeFormState
    private var submitting = false
    private var loggingIn = false
    private val history = ArrayDeque<Route>()
    private var pendingDownload: Array<String>? = null

    private data class Route(val kind: String, val title: String, val url: String)

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val pending = formState.pendingFile
        formState.pendingFile = null
        if (uri != null && pending != null) {
            formState.draft(pending.formKey).files[pending.fieldName] = uri
            if (!loggingIn) navigate(route, false)
        }
    }
    private val exportPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val export = formState.pendingExport
        formState.pendingExport = null
        val destination = result.data?.data
        if (export != null && destination != null && result.resultCode == Activity.RESULT_OK) {
            task("Menyimpan berkas", {
                try { contentResolver.openOutputStream(destination)?.use { output -> export.file.inputStream().use { it.copyTo(output) } }
                    ?: throw PortalError("Lokasi penyimpanan tidak dapat dibuka.")
                } finally { export.file.delete() }
            }, { toast("Berkas berhasil disimpan.") }, { error -> toast(friendly(error)) })
        } else export?.file?.delete()
    }
    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingDownload
        pendingDownload = null
        if (granted && pending != null) download(pending[0], pending[1], pending[2], pending[3])
        else toast("Izin penyimpanan diperlukan untuk menyimpan berkas pada versi Android ini.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        vault = CredentialVault(applicationContext)
        client = BsiClient(AndroidSessionCookies())
        studentClient = StudentPortalClient(AndroidSessionCookies(StudentPortalParser.ORIGIN))
        formState = ViewModelProvider(this)[NativeFormState::class.java]
        if (formState.pendingWrite) {
            formState.pendingWrite = false
            toast("Permintaan sebelumnya terputus. Periksa data terbaru di BSI sebelum mengirim kembali.")
        }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(canvas) }
        setContentView(root)
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = true
        if (Build.VERSION.SDK_INT >= 27) {
            window.navigationBarColor = Color.WHITE
            WindowCompat.getInsetsController(window, root).isAppearanceLightNavigationBars = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        toolbar = MaterialToolbar(this).apply {
            setBackgroundColor(Color.WHITE); setTitleTextColor(ink); setSubtitleTextColor(muted)
            title = "MyBest"; subtitle = "Universitas Bina Sarana Informatika"
        }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, -2))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true; indeterminateTintList = ColorStateList.valueOf(navy); visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))
        status = text("", 13, muted).apply { setPadding(dp(20), dp(6), dp(20), dp(6)); visibility = View.GONE }
        root.addView(status)
        body = FrameLayout(this)
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        bottom = BottomNavigationView(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.WHITE)
            menu.add(0, R.id.nav_home, 0, "Beranda").setIcon(R.drawable.ic_home)
            menu.add(0, R.id.nav_schedule, 1, "Jadwal").setIcon(R.drawable.ic_calendar)
            menu.add(0, R.id.nav_services, 2, "Layanan").setIcon(R.drawable.ic_book)
            menu.add(0, R.id.nav_account, 3, "Akun").setIcon(R.drawable.ic_account)
            labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED
            itemIconTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(navy, muted))
            itemTextColor = itemIconTintList
            setOnItemSelectedListener { item ->
                if (submitting) return@setOnItemSelectedListener false
                val next = when (item.itemId) {
                    R.id.nav_schedule -> Route("schedule", "Jadwal kuliah", "/sch")
                    R.id.nav_services -> Route("services", "Layanan mahasiswa", "/user/dashboard")
                    R.id.nav_account -> Route("profile", "Akun saya", "/profil")
                    else -> Route("home", "Beranda", "/user/dashboard")
                }
                history.clear(); navigate(next, false); true
            }
        }
        root.addView(bottom, LinearLayout.LayoutParams(-1, -2))
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (submitting) { toast("Pengiriman sedang diproses. Tunggu respons BSI."); return }
                when {
                    history.isNotEmpty() -> navigate(history.removeLast(), false)
                    route.kind != "home" && !loggingIn -> { bottom.selectedItemId = R.id.nav_home }
                    else -> finish()
                }
            }
        })
        val restore = savedInstanceState?.getString("kind")?.let {
            Route(it, savedInstanceState.getString("title").orEmpty(), savedInstanceState.getString("url").orEmpty())
        }
        continueMyBestAfterStudentLogin = savedInstanceState?.getBoolean("studentSso") ?: false
        val lastPortal = getPreferences(MODE_PRIVATE).getString("portal", "mybest")
        if (restore?.kind == "student" || (restore == null && lastPortal == "student")) {
            navigate(restore ?: Route("student", "Student V2", StudentPortalParser.HOME), false)
            return
        }
        loadingScreen("Memeriksa sesi BSI…")
        task("Memeriksa sesi BSI", { client.page("/user/dashboard") }, { doc ->
            acceptDashboard(doc)
            navigate(restore ?: route, false)
        }, { error ->
            if (error is SessionExpired) recoverMyBest(restore ?: route, automatic = true) else showError(error) { recreate() }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val current = if (route.kind == "student" || studentLoginVisible) pendingStudentRoute ?: route else pendingRoute ?: route
        outState.putString("kind", current.kind); outState.putString("title", current.title); outState.putString("url", current.url)
        outState.putBoolean("studentSso", continueMyBestAfterStudentLogin)
        super.onSaveInstanceState(outState)
    }

    private fun acceptDashboard(doc: Document) {
        dashboard = doc
        student = PortalParser.student(doc)
        navigation = PortalParser.navigation(doc)
        loggingIn = false
        studentLoginVisible = false
    }

    private fun navigate(next: Route, remember: Boolean = true) {
        if (submitting) return
        epoch++
        progress.visibility = View.GONE
        status.visibility = View.GONE
        if (remember && !loggingIn && next != route) history.addLast(route)
        route = next
        loggingIn = false
        studentLoginVisible = false
        getPreferences(MODE_PRIVATE).edit().putString("portal", if (next.kind == "student") "student" else "mybest").apply()
        toolbar.title = if (next.kind == "student") "Student V2" else "MyBest"
        toolbar.subtitle = next.title
        toolbar.menu.clear()
        toolbar.menu.add("Muat ulang").setIcon(R.drawable.ic_refresh).setShowAsAction(2)
        toolbar.menu.add("Keluar")
        toolbar.menu.add(if (next.kind == "student") "MyBest" else "Student V2")
        toolbar.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Keluar" -> if (route.kind == "student") confirmStudentLogout() else confirmLogout()
                "Student V2" -> openStudent()
                "MyBest" -> recoverMyBest(Route("home", "Beranda", "/user/dashboard"))
                else -> navigate(route, false)
            }
            true
        }
        toolbar.navigationIcon = if (history.isEmpty()) null else AppCompatResources.getDrawable(this, R.drawable.ic_back)
        toolbar.setNavigationContentDescription("Kembali")
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        bottom.visibility = if (next.kind == "public" || next.kind == "student") View.GONE else View.VISIBLE
        when (next.kind) {
            "student" -> loadStudent(next)
            "home" -> loadHome()
            "schedule" -> loadSchedule()
            "services" -> showServices()
            "profile" -> showProfile()
            "materials" -> loadMaterials(next)
            "tables" -> loadTables(next)
            "login" -> showLogin()
            "native", "public", "web" -> loadNative(next)
            else -> loadSchedule()
        }
    }

    private fun loadHome() {
        loadingScreen("Memuat beranda dan jadwal…")
        task("Memuat beranda", { client.page("/user/dashboard") to client.page("/sch") }, { (doc, schedule) ->
            acceptDashboard(doc)
            courses = PortalParser.courses(schedule)
            val content = content()
            val welcome = panel(content, navy)
            welcome.addView(text("Selamat datang", 14, Color.WHITE))
            welcome.addView(text(student.name.ifBlank { "Mahasiswa BSI" }, 25, Color.WHITE, true))
            welcome.addView(text(student.identifier, 14, Color.WHITE))
            heading(content, "Perkuliahan hari ini")
            val today = courses.filter { it.today }
            if (today.isEmpty()) empty(content, "Tidak ada jadwal kuliah hari ini", "Jadwal untuk hari lainnya tersedia di tab Jadwal.")
            else today.forEach { courseCard(content, it) }
            action(content, "Lihat semua jadwal") { bottom.selectedItemId = R.id.nav_schedule }
            heading(content, "Panduan mahasiswa")
            val guides = PortalParser.main(doc).select(".panduan-item[href]").map {
                PortalLink(it.select(".font-weight-bold").text().ifBlank { "Panduan mahasiswa" }, it.absUrl("href"))
            }
            guides.forEach { link -> action(content, link.title, false) { openLink(link) } }
            val rules = PortalParser.main(doc).select(".rule-text")
            if (rules.isNotEmpty()) {
                heading(content, "Aturan ujian")
                val box = panel(content)
                rules.forEachIndexed { index, rule -> box.addView(text("${index + 1}. ${rule.text()}", 14, muted).also { it.setPadding(0, dp(6), 0, dp(6)) }) }
            }
        })
    }

    private fun loadSchedule() {
        loadingScreen("Memuat jadwal kuliah…")
        task("Memuat jadwal", { client.page(route.url) }, { doc ->
            courses = PortalParser.courses(doc)
            val content = content()
            heading(content, route.title, "Jadwal dan tautan kelas diambil langsung dari BSI.")
            val search = input(content, "Cari mata kuliah atau hari", InputType.TYPE_CLASS_TEXT).second
            val list = column()
            content.addView(list)
            fun render(query: String) {
                list.removeAllViews()
                val filtered = courses.filter { "${it.title} ${it.schedule} ${it.lecturer}".contains(query, true) }
                if (filtered.isEmpty()) empty(list, if (courses.isEmpty()) "Belum ada jadwal" else "Mata kuliah tidak ditemukan", "Muat ulang atau ubah kata pencarian Anda.")
                filtered.sortedByDescending { it.today }.forEach { courseCard(list, it) }
            }
            search.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render(s.toString()) }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
            render("")
        })
    }

    private fun courseCard(parent: LinearLayout, course: Course) {
        val card = panel(parent)
        card.addView(text(if (course.today) "Hari ini · ${course.schedule}" else course.schedule, 14, navy, true))
        card.addView(text(course.title, 20, ink, true).also { it.setPadding(0, dp(8), 0, dp(8)) })
        card.addView(text(listOf(course.classroom, course.credits).filter { it.isNotBlank() }.joinToString(" · "), 14, muted))
        card.addView(text(listOf("Dosen ${course.lecturer}", course.room, course.campus).filter { it.isNotBlank() }.joinToString(" · "), 14, muted))
        course.links.forEach { link -> action(card, link.title, link.title.contains("Buka Kelas", true)) { openLink(link.copy(title = "${link.title} · ${course.title}")) } }
    }

    private fun loadMaterials(next: Route) {
        loadingScreen("Memuat materi pembelajaran…")
        task("Memuat materi", { client.page(next.url) }, { doc ->
            val data = PortalParser.learning(doc)
            val content = content()
            heading(content, next.title)
            heading(content, "Dokumen pembelajaran")
            if (data.documents.isEmpty()) empty(content, "Belum ada dokumen", "Materi yang tersedia di BSI akan muncul di sini.")
            data.documents.forEach { link -> action(content, "Unduh ${link.title}", false) { openLink(link) } }
            heading(content, "Video pembelajaran")
            if (data.videos.isEmpty()) empty(content, "Belum ada video", "Dosen belum menambahkan video pada halaman ini.")
            data.videos.forEach { link -> action(content, link.title, false) { openExternal(link.url) } }
            data.tables.forEach { renderTable(content, it, "Belum ada materi tambahan") }
        })
    }

    private fun loadTables(next: Route) {
        loadingScreen("Memuat ${next.title}…")
        task("Memuat data BSI", { client.page(next.url) }, { doc ->
            val content = content()
            heading(content, next.title)
            val tables = PortalParser.tables(doc)
            if (tables.isEmpty()) empty(content, "Belum ada data yang dapat ditampilkan", "Muat ulang untuk memeriksa data terbaru dari BSI.")
            tables.forEach { renderTable(content, it, "Belum ada data pada bagian ini") }
        })
    }

    private fun renderTable(parent: LinearLayout, table: PortalTable, emptyTitle: String) {
        if (table.title.isNotBlank()) heading(parent, table.title)
        if (table.rows.isEmpty()) { empty(parent, emptyTitle, "Data ditampilkan sesuai halaman BSI saat ini."); return }
        fun row(parent: LinearLayout, row: TableRow) {
            val box = panel(parent)
            val title = row.fields.firstOrNull { it.first.contains("Judul", true) || it.first.contains("Mata", true) }
            title?.let { box.addView(text(it.second, 19, ink, true)) }
            row.fields.filter { it != title }.forEach { (key, value) ->
                box.addView(text("$key: $value", 14, muted).also { it.setPadding(0, dp(4), 0, dp(4)) })
            }
            row.links.forEach { link -> action(box, link.title, false) { openLink(link) } }
            row.forms.forEach { renderForm(box, it) }
        }
        if (table.rows.size <= 40) { table.rows.forEach { row(parent, it) }; return }
        val search = input(parent, "Cari pada ${table.title.ifBlank { "tabel" }}", InputType.TYPE_CLASS_TEXT).second
        val summary = text("", 14, muted)
        parent.addView(summary)
        val list = column()
        parent.addView(list)
        var shown = 30
        val more = action(parent, "Tampilkan berikutnya", false) { }
        fun render() {
            val filtered = table.rows.filter { item -> item.fields.any { (label, value) -> "$label $value".contains(search.text.toString(), true) } }
            summary.text = "${minOf(shown, filtered.size)} dari ${filtered.size} baris"
            list.removeAllViews()
            filtered.take(shown).forEach { row(list, it) }
            more.visibility = if (filtered.size > shown) View.VISIBLE else View.GONE
        }
        more.setOnClickListener { shown += 30; render() }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { shown = 30; render() }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        render()
    }

    private fun showServices() {
        val content = content()
        heading(content, "Layanan mahasiswa", "Akses menu yang tersedia pada akun BSI Anda.")
        action(content, "Student V2") { openStudent() }
        navigation.filter { !it.url.endsWith("/user/dashboard") && !it.url.endsWith("/profil") && Uri.parse(it.url).path !in listOf("/student-2", "/halaman-ujian") }.forEach { link ->
            action(content, link.title, false) { openLink(link) }
        }
        if (navigation.isEmpty()) action(content, "Muat menu BSI") { bottom.selectedItemId = R.id.nav_home }
    }

    private fun openStudent(signInToMyBest: Boolean = false) {
        continueMyBestAfterStudentLogin = signInToMyBest
        navigate(Route("student", "Student V2", StudentPortalParser.HOME))
    }

    private fun loadStudent(next: Route) {
        if (Uri.parse(next.url).path.orEmpty().startsWith("/get-jadwal-dosen/")) { loadLecturer(next); return }
        loadingScreen("Memuat ${next.title}…")
        task("Memuat Student V2", {
            val response = studentClient.read(next.url)
            val doc = if (response.location == null) PortalParser.document(response.html, response.url) else {
                val destination = java.net.URI(response.location)
                if (destination.scheme != "https" || destination.host.isNullOrBlank() || destination.rawUserInfo != null) throw PortalError("Tujuan layanan belum dapat dibuka dari aplikasi.")
                PortalParser.document("<main></main>", response.url).also { page ->
                    val main = page.selectFirst("main")!!
                    main.appendElement("p").text("BSI mengarahkan layanan ini ke ${destination.host}.")
                    main.appendElement("a").attr("href", response.location).text("Buka layanan ${destination.host}")
                }
            }
            val facts = if (Uri.parse(doc.location()).path == StudentPortalParser.HOME) {
                val path = doc.selectFirst("#infoPklCard[data-url]")?.absUrl("data-url").orEmpty()
                if (path.isBlank()) emptyList() else try {
                    val response = studentClient.read(path)
                    if (response.status != 200 || response.location != null) emptyList() else {
                        val data = org.json.JSONObject(response.html).optJSONObject("data")
                        listOf("nim" to "NIM", "nm_mhs" to "Nama", "kd_lokal" to "Kelas", "nm_dosen" to "Dosen pembimbing", "kd_dosen" to "Kode dosen", "info" to "Informasi PKL")
                            .mapNotNull { (key, label) -> data?.optString(key)?.takeIf { it.isNotBlank() && it != "null" }?.let { label to it } }
                    }
                } catch (_: Exception) { emptyList() }
            } else emptyList()
            Triple(doc, StudentPortalParser.page(doc), facts)
        }, { (doc, page, facts) ->
            internshipFacts = facts
            StudentPortalParser.navigation(doc).takeIf { it.isNotEmpty() }?.let { studentNavigation = it }
            if (continueMyBestAfterStudentLogin) {
                continueMyBestAfterStudentLogin = false
                recoverMyBest(pendingRoute ?: Route("home", "Beranda", "/user/dashboard"))
            } else renderNative(doc, next, page)
        }, { error -> if (error is SessionExpired) studentExpired(next) else showError(error) { navigate(next, false) } })
    }

    private fun loadLecturer(next: Route) {
        loadingScreen("Memuat jadwal dosen…")
        task("Memuat jadwal dosen", {
            val response = studentClient.read(next.url)
            if (response.location != null) throw PortalError("Jadwal dosen diarahkan ke layanan lain.")
            val entries = org.json.JSONArray(response.html)
            val labels = listOf("nm_mtk" to "Mata Kuliah", "kd_mtk" to "Kode", "sks" to "SKS", "hari" to "Hari", "jam" to "Jam", "ruang" to "Ruang")
            PortalTable("Jadwal dosen", (0 until entries.length()).map { index ->
                val entry = entries.getJSONObject(index)
                TableRow(labels.map { (key, label) -> label to entry.optString(key, "-") }, emptyList())
            })
        }, { table ->
            val content = content()
            heading(content, next.title)
            action(content, "Menu Student V2", false) { showStudentMenu() }
            renderTable(content, table, "Belum ada jadwal dosen")
        })
    }

    private fun showStudentMenu() {
        epoch++
        val content = content()
        heading(content, "Menu Student V2", "Layanan yang tersedia pada akun Anda.")
        action(content, "Beranda Student V2", false) { navigate(Route("student", "Student V2", StudentPortalParser.HOME)) }
        action(content, "Masuk ke MyBest melalui BSI") { recoverMyBest(Route("home", "Beranda", "/user/dashboard")) }
        val search = input(content, "Cari layanan akademik", InputType.TYPE_CLASS_TEXT).second
        val list = column()
        content.addView(list)
        fun render(query: String) {
            list.removeAllViews()
            val links = studentNavigation.filter { it.title.contains(query, true) && Uri.parse(it.url).path != "/mahasiswa/myLMS" }
            if (links.isEmpty()) empty(list, "Layanan tidak ditemukan", "Ubah pencarian atau muat ulang halaman Student V2.")
            links.forEach { link -> action(list, link.title, false) { openLink(link) } }
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render(s.toString()) }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        render("")
    }

    private fun studentExpired(target: Route = route) {
        pendingStudentRoute = target
        showStudentLogin()
    }

    private fun showStudentLogin(message: String = "Masuk dengan akun Student V2 BSI Anda.") {
        epoch++
        loggingIn = true
        studentLoginVisible = true
        getPreferences(MODE_PRIVATE).edit().putString("portal", "student").apply()
        toolbar.title = "Student V2"; toolbar.subtitle = "Login mahasiswa"
        toolbar.menu.clear(); toolbar.navigationIcon = null
        bottom.visibility = View.GONE
        loadingScreen("Memuat form login Student V2…")
        task("Memuat login Student V2", { studentClient.challenge() }, { current ->
            val content = content()
            heading(content, "Masuk ke Student V2", "Universitas Bina Sarana Informatika")
            content.addView(text(message, 14, muted))
            if (current.error.isNotBlank()) content.addView(text(current.error, 14, Color.rgb(153, 27, 27)))
            if (current.captchaPrompt.isNotBlank()) content.addView(text(current.captchaPrompt, 16, ink))
            if (current.captchaSvg.isNotBlank()) {
                val image = ImageView(this).apply {
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    contentDescription = "Kode keamanan Student V2"
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                try { image.setImageDrawable(PictureDrawable(SVG.getFromString(current.captchaSvg).renderToPicture())) }
                catch (_: Exception) { content.addView(text("Gambar kode keamanan belum dapat ditampilkan. Muat ulang form.", 14, muted)) }
                content.addView(image, LinearLayout.LayoutParams(-1, dp(72)))
            }
            NativeFormRenderer(this, formState, { _, _ -> }, { form, fields, _ ->
                if (submitting) return@NativeFormRenderer
                submitting = true
                task("Memverifikasi akun Student V2", { studentClient.login(current, fields) }, { doc ->
                    submitting = false; loggingIn = false; studentLoginVisible = false
                    formState.drafts.remove(form.key)
                    studentNavigation = StudentPortalParser.navigation(doc)
                    if (continueMyBestAfterStudentLogin) {
                        continueMyBestAfterStudentLogin = false
                        recoverMyBest(pendingRoute ?: Route("home", "Beranda", "/user/dashboard"))
                    } else {
                        val next = pendingStudentRoute ?: Route("student", "Student V2", StudentPortalParser.HOME)
                        pendingStudentRoute = null
                        navigate(next, false)
                    }
                }, { error -> submitting = false; showStudentLogin(friendly(error)) })
            }).render(content, current.form)
            action(content, "Muat ulang form login", false) { if (!submitting) showStudentLogin() }
            action(content, "Login e-learning MyBest", false) { if (!submitting) { continueMyBestAfterStudentLogin = false; showLogin() } }
        }, { error -> showError(error) { showStudentLogin() } })
    }

    private fun recoverMyBest(target: Route, automatic: Boolean = false) {
        pendingRoute = target
        if (automatic && getPreferences(MODE_PRIVATE).getBoolean("manualMyBestLogout", false)) {
            showLogin()
            return
        }
        epoch++
        loggingIn = true
        studentLoginVisible = false
        loadingScreen("Memeriksa akses MyBest melalui Student V2…")
        task("Memeriksa SSO BSI", {
            val link = studentClient.myBestSso()
            client.page(link)
            val doc = client.page("/user/dashboard")
            if (doc.selectFirst(".user-display-name") == null) throw SessionExpired()
            doc
        }, { doc ->
            acceptDashboard(doc)
            getPreferences(MODE_PRIVATE).edit().putBoolean("manualMyBestLogout", false).apply()
            pendingRoute = null
            navigate(target, false)
        }, { error ->
            showLogin(if (error is SessionExpired) "Sesi MyBest berakhir. Masuk lewat Student V2 atau isi login e-learning untuk melanjutkan."
                else "Akses MyBest melalui Student V2 belum berhasil. ${friendly(error)}")
        })
    }

    private fun confirmStudentLogout() {
        MaterialAlertDialogBuilder(this).setTitle("Keluar dari Student V2?")
            .setMessage("Sesi Student V2 pada perangkat ini akan diakhiri.")
            .setNegativeButton("Batal", null).setPositiveButton("Keluar") { _, _ ->
                task("Mengakhiri sesi Student V2", { studentClient.logout() }, {
                    studentNavigation = emptyList(); pendingStudentRoute = null; history.clear()
                    formState.drafts.keys.filter { it.startsWith(StudentPortalParser.ORIGIN) }.forEach { formState.drafts.remove(it) }
                    showStudentLogin("Anda sudah keluar dari Student V2.")
                }, { error -> if (error is SessionExpired) showStudentLogin("Sesi Student V2 sudah berakhir.") else showError(error) { confirmStudentLogout() } })
            }.show()
    }

    private fun submitReadForm(form: NativeForm, values: List<Pair<String, String>>) {
        if (submitting) return
        val next = route
        task("Memuat pilihan Anda", {
            if (Uri.parse(form.action).host == "students.bsi.ac.id") studentClient.submit(form, values)
            else client.submit(form.action, form.method, form.sourceUrl, values)
        }, { response ->
            if (response.location != null) { toast("Halaman diarahkan ke layanan lain."); return@task }
            val doc = PortalParser.document(response.html, response.url)
            route = next.copy(url = response.url)
            renderNative(doc, route)
        })
    }

    private fun downloadStudent(link: PortalLink) {
        val form = NativeForm("download:${link.url}", link.title, link.url, "GET", StudentPortalParser.ORIGIN + StudentPortalParser.HOME,
            emptyList(), emptyList(), "Unduh", "", "", false, false, true, false, emptyList())
        downloadForm(form, emptyList())
    }

    private fun showProfile() {
        val content = content()
        heading(content, "Akun saya")
        val box = panel(content)
        box.addView(text(student.name, 23, ink, true))
        box.addView(text(student.identifier, 16, muted))
        box.addView(text(student.email, 15, muted))
        heading(content, "Sesi dan login")
        content.addView(text("Sesi dipakai kembali saat aplikasi dibuka. Jika sesi BSI berakhir, Anda akan diarahkan ke login dengan tujuan sebelumnya tetap tersimpan. Kode keamanan baru perlu diisi saat diminta BSI.", 15, muted))
        val saved = vault.read() != null
        content.addView(text(if (saved) "Akun diingat pada perangkat ini." else "Password tidak disimpan pada perangkat ini.", 14, muted).also { it.setPadding(0, dp(12), 0, 0) })
        if (saved) action(content, "Hapus akun tersimpan", false) { vault.clear(); showProfile() }
        action(content, "Edit profil dan password", false) { navigate(Route("native", "Pengaturan profil", "/profil")) }
        action(content, "Keluar dari akun", false) { confirmLogout() }
    }

    private fun openLink(link: PortalLink) {
        val uri = Uri.parse(link.url)
        if (uri.scheme == "https" && uri.host == "students.bsi.ac.id") {
            if (isDownload(uri.path.orEmpty()) || uri.path == "/mahasiswa/cetak-khs-pdf") {
                downloadStudent(link)
            } else if (uri.path == "/mahasiswa/myLMS") {
                recoverMyBest(Route("home", "Beranda", "/user/dashboard"))
            } else navigate(Route("student", link.title, link.url))
            return
        }
        if (uri.host != "elearning.bsi.ac.id") {
            if (isDownload(uri.path.orEmpty())) download(link.url, "", "") else openExternal(link.url)
            return
        }
        val path = uri.path.orEmpty()
        if (path == "/student-2") { openStudent(); return }
        if (path == "/halaman-ujian") { toast("Layanan ini belum diaktifkan."); return }
        val kind = when {
            path == "/sch" || path == "/kuliah-pengganti" -> "schedule"
            path.startsWith("/learning/") -> "materials"
            path.startsWith("/assignment/") && !path.startsWith("/assignment/send/") -> "tables"
            path == "/exercise" || path == "/toefl" -> "tables"
            isDownload(path) -> { download(link.url, "", ""); return }
            else -> "native"
        }
        val open = { navigate(Route(kind, link.title, link.url)) }
        if (link.title.trim().matches(Regex("(?i)^(absen|hadir|hapus|delete)(\\s.*)?$"))) {
            MaterialAlertDialogBuilder(this).setTitle(link.title).setMessage("Lanjutkan tindakan ini pada akun BSI Anda?")
                .setNegativeButton("Batal", null).setPositiveButton("Lanjutkan") { _, _ -> performLinkAction(link) }.show()
        } else open()
    }

    private fun isDownload(path: String): Boolean = path.lowercase().matches(Regex(".*\\.(pdf|zip|docx?|xlsx?|pptx?|mp[34])$"))

    private fun showLogin(message: String = "Masuk dengan akun e-learning BSI Anda.", username: String = "", password: String = "") {
        epoch++
        loggingIn = true
        studentLoginVisible = false
        route = pendingRoute ?: Route("home", "Beranda", "/user/dashboard")
        getPreferences(MODE_PRIVATE).edit().putString("portal", "mybest").apply()
        challenge = null
        toolbar.title = "MyBest"; toolbar.subtitle = "Login mahasiswa"
        toolbar.menu.clear(); toolbar.navigationIcon = null
        bottom.visibility = View.GONE
        val content = content()
        val logo = ImageView(this).apply { setImageResource(R.drawable.mybest_logo); scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "MyBest BSI" }
        content.addView(logo, LinearLayout.LayoutParams(-1, dp(90)).apply { bottomMargin = dp(16) })
        heading(content, "Masuk ke MyBest", "E-learning Universitas Bina Sarana Informatika")
        val notice = text(message, 14, muted)
        content.addView(notice)
        action(content, "Masuk MyBest lewat Student V2", false) { openStudent(true) }
        action(content, "Buka portal Student V2", false) { openStudent() }
        val saved = vault.read()
        val (nimLayout, nim) = input(content, "NIP / NIM", InputType.TYPE_CLASS_NUMBER)
        nim.setText(username.ifBlank { saved?.first.orEmpty() })
        val (passwordLayout, pass) = input(content, "Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        passwordLayout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        pass.setText(password.ifBlank { saved?.second.orEmpty() })
        pass.isSaveEnabled = false
        heading(content, "Kode keamanan")
        val image = ImageView(this).apply {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            contentDescription = "Gambar kode keamanan BSI. Isi tiga karakter yang terlihat pada gambar."
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        content.addView(image, LinearLayout.LayoutParams(-1, dp(72)))
        val (codeLayout, code) = input(content, "3 karakter pada gambar", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        code.filters = arrayOf(InputFilter.LengthFilter(3)); code.isSaveEnabled = false
        val refresh = action(content, "Ganti kode keamanan", false) { }
        val remember = MaterialCheckBox(this).apply { text = "Ingat akun pada perangkat ini"; isChecked = saved != null; minHeight = dp(48); setTextColor(ink) }
        content.addView(remember)
        content.addView(text("Jika dipilih, NIM dan password disimpan terenkripsi untuk mengisi login berikutnya. CAPTCHA tetap diisi saat diminta BSI.", 13, muted))
        val submit = action(content, "Masuk ke akun") { }
        submit.backgroundTintList = ColorStateList.valueOf(purple)
        submit.isEnabled = false
        fun refreshChallenge() {
            submit.isEnabled = false; refresh.isEnabled = false; code.setText("")
            task("Memuat kode keamanan", { client.challenge() }, { next ->
                challenge = next
                try { image.setImageDrawable(PictureDrawable(SVG.getFromString(next.svg).renderToPicture())) }
                catch (_: Exception) { notice.text = "Gambar kode keamanan gagal ditampilkan. Coba lagi atau gunakan situs resmi."; refresh.isEnabled = true; return@task }
                submit.isEnabled = true; refresh.isEnabled = true
            }, { error -> notice.text = friendly(error); refresh.isEnabled = true })
        }
        refresh.setOnClickListener { refreshChallenge() }
        submit.setOnClickListener {
            nimLayout.error = null; passwordLayout.error = null; codeLayout.error = null
            val user = nim.text.toString().trim()
            val secret = pass.text.toString()
            val answer = code.text.toString().trim()
            when {
                user.isBlank() -> { nimLayout.error = "Isi NIP atau NIM."; nim.requestFocus() }
                secret.isBlank() -> { passwordLayout.error = "Isi password."; pass.requestFocus() }
                answer.length != 3 -> { codeLayout.error = "Isi tiga karakter pada gambar."; code.requestFocus() }
                challenge != null -> {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(code.windowToken, 0)
                    val current = challenge!!
                    val saveCredentials = remember.isChecked
                    submit.isEnabled = false; refresh.isEnabled = false
                    task("Memverifikasi akun BSI", {
                        val doc = client.login(current, user, secret, answer)
                        if (saveCredentials) vault.save(user, secret) else vault.clear()
                        doc
                    }, { doc ->
                        pass.setText(""); code.setText("")
                        acceptDashboard(doc)
                        getPreferences(MODE_PRIVATE).edit().putBoolean("manualMyBestLogout", false).apply()
                        val target = pendingRoute ?: Route("home", "Beranda", "/user/dashboard")
                        pendingRoute = null; history.clear(); navigate(target, false)
                    }, { error -> notice.text = friendly(error); refreshChallenge() })
                }
            }
        }
        action(content, "Lupa password?", false) {
            history.addLast(Route("login", "Login mahasiswa", "/login"))
            navigate(Route("public", "Lupa password", "/forgot-password"), false)
        }
        refreshChallenge()
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this).setTitle("Keluar dari MyBest?")
            .setMessage("Sesi BSI dan akun yang diingat pada perangkat ini akan dihapus.")
            .setNegativeButton("Batal", null).setPositiveButton("Keluar") { _, _ ->
                task("Mengakhiri sesi", { client.logout(); vault.clear() }, {
                    getPreferences(MODE_PRIVATE).edit().putBoolean("manualMyBestLogout", true).apply()
                    dashboard = null; courses = emptyList(); student = Student("", "", "")
                    navigation = emptyList(); history.clear(); pendingRoute = null; showLogin("Anda sudah keluar.")
                }, { error ->
                    if (error is SessionExpired) {
                        getPreferences(MODE_PRIVATE).edit().putBoolean("manualMyBestLogout", true).apply()
                        worker.execute { clientCookiesClear() }
                        vault.clear(); history.clear(); pendingRoute = null; showLogin("Sesi sudah berakhir.")
                    } else showError(error) { confirmLogout() }
                })
            }.show()
    }
    private fun clientCookiesClear() { AndroidSessionCookies().clear() }

    private fun loadNative(next: Route) {
        loadingScreen("Memuat ${next.title}…")
        task("Memuat data BSI", { client.page(next.url) }, { doc -> renderNative(doc, next) })
    }

    private fun renderNative(doc: Document, next: Route, parsed: NativePage? = null) {
        val page = parsed ?: if (next.kind == "student") StudentPortalParser.page(doc) else NativePortalParser.page(doc)
        val content = content()
        heading(content, next.title)
        if (next.kind == "student") action(content, "Menu Student V2", false) { showStudentMenu() }
        if (next.kind == "student" && Uri.parse(next.url).path == StudentPortalParser.HOME && internshipFacts.isNotEmpty()) {
            heading(content, "Informasi PKL")
            val box = panel(content)
            internshipFacts.forEach { (key, value) -> box.addView(text("$key: $value", 15, ink)) }
        }
        page.notices.forEach { notice -> content.addView(text(notice.text, 14, if (notice.error) Color.rgb(153, 27, 27) else muted)) }
        if (page.facts.isNotEmpty()) {
            val box = panel(content)
            page.facts.forEach { (label, value) -> box.addView(text("$label: $value", 15, ink)) }
        }
        page.statuses.forEach { value -> panel(content).addView(text(value, 17, navy, true)) }
        page.paragraphs.forEach { value -> content.addView(text(value, 15, muted).also { it.setPadding(0, dp(8), 0, dp(8)) }) }
        page.tables.forEach { renderTable(content, it, "Belum ada data pada bagian ini") }
        page.links.forEach { link -> action(content, link.title, false) { openLink(link) } }
        if (Uri.parse(next.url).path.orEmpty().contains("diskusi") && page.links.isEmpty() && page.paragraphs.size <= 1) {
            empty(content, "Belum ada diskusi", "Anda dapat membuat topik diskusi melalui form di bawah.")
        }
        page.forms.forEach { form -> renderForm(panel(content), form) }
        if (page.forms.isEmpty() && page.tables.isEmpty() && page.paragraphs.isEmpty() && page.facts.isEmpty() && page.notices.isEmpty()) {
            empty(content, "Belum ada informasi pada halaman ini", "Muat ulang untuk memeriksa data terbaru dari BSI.")
        }
    }

    private fun renderForm(parent: LinearLayout, form: NativeForm) {
        if (Uri.parse(form.action).path == "/mahasiswa/surat-riset/template" && Uri.parse(form.action).host == "students.bsi.ac.id") {
            StudentResearchFormRenderer(this, formState, { original, nim, done ->
                task("Mencari anggota surat", {
                    val response = studentClient.lookupResearchMember(original, nim)
                    if (response.location != null) throw PortalError("Pencarian anggota diarahkan ke layanan lain.")
                    val json = org.json.JSONObject(response.html)
                    if (!json.optBoolean("success")) throw PortalError("Mahasiswa tidak ditemukan. Periksa NIM anggota.")
                    val data = json.getJSONObject("data")
                    val fields = listOf("nama", "prodi", "hp", "jenjang").associateWith { data.optString(it).takeUnless { value -> value == "null" }.orEmpty() }
                    if (listOf("nama", "prodi", "jenjang").any { fields[it].isNullOrBlank() }) throw PortalError("Data anggota dari BSI belum lengkap. Periksa NIM lalu coba lagi.")
                    fields
                }, { done(it) }, { error -> if (error is SessionExpired) studentExpired() else toast(friendly(error)) })
            }, { _, _ -> }, { selected, values, files -> confirmForm(selected, values, files) }).render(parent, form)
            return
        }
        NativeFormRenderer(this, formState, pick@{ selectedForm, field ->
            if (submitting) return@pick
            formState.pendingFile = NativeFormState.PendingFile(selectedForm.key, field.name)
            val types = field.accept.split(',').mapNotNull { value ->
                val type = value.trim()
                when { type == ".pdf" -> "application/pdf"; type.startsWith(".") -> android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(type.removePrefix(".")); type.contains('/') -> type; else -> null }
            }.ifEmpty { listOf("*/*") }.toTypedArray()
            try { filePicker.launch(types) } catch (_: ActivityNotFoundException) { formState.pendingFile = null; toast("Pemilih berkas tidak tersedia.") }
        }, { selectedForm, values, files ->
            if (selectedForm.download) downloadForm(selectedForm, values)
            else if (selectedForm.method.equals("GET", true)) submitReadForm(selectedForm, values)
            else confirmForm(selectedForm, values, files)
        }).render(parent, form)
    }

    private fun confirmForm(form: NativeForm, values: List<Pair<String, String>>, files: Map<String, Uri>) {
        if (submitting) return
        MaterialAlertDialogBuilder(this).setTitle(form.submitLabel)
            .setMessage("Kirim ${form.title.lowercase()} ke akun BSI Anda?")
            .setNegativeButton("Batal", null).setPositiveButton("Kirim") { _, _ ->
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken, 0)
                submitting = true
                formState.pendingWrite = true
                val previous = route
                val studentForm = Uri.parse(form.action).host == "students.bsi.ac.id"
                val changesPassword = Uri.parse(form.action).path in listOf("/profil/update", "/mahasiswa/password")
                task("Mengirim ${form.title.lowercase()}", {
                    val staged = mutableListOf<File>()
                    try {
                        val uploads = files.map { (name, uri) -> stageUpload(form.fields.first { it.name == name }, uri, staged) }
                        if (studentForm) studentClient.submit(form, values, uploads)
                        else client.submit(form.action, form.method, form.sourceUrl, values, uploads, form.multipart)
                    } finally { staged.forEach { it.delete() } }
                }, { response ->
                    submitting = false
                    formState.pendingWrite = false
                    if (changesPassword && !studentForm) vault.clear()
                    if (response.location != null) {
                        MaterialAlertDialogBuilder(this).setTitle("Respons BSI")
                            .setMessage("BSI mengarahkan respons ke layanan lain. Periksa data terbaru sebelum mengirim kembali.")
                            .setPositiveButton("Tutup", null).show()
                        return@task
                    }
                    val doc = PortalParser.document(response.html, response.url)
                    val errors = response.status == 422 || (if (studentForm) StudentPortalParser.notices(doc) else NativePortalParser.notices(doc)).any { it.error }
                    if (!errors) formState.drafts.remove(form.key)
                    if (doc.selectFirst(".user-display-name") != null) acceptDashboard(doc)
                    route = if (response.redirected) previous.copy(kind = if (studentForm) "student" else "native", url = response.url) else previous
                    renderNative(doc, route)
                    toast(if (errors) "Periksa isian yang ditolak BSI." else "Respons BSI diterima. Periksa data terbaru pada halaman ini.")
                }, { error ->
                    submitting = false
                    formState.pendingWrite = false
                    if (changesPassword && !studentForm) vault.clear()
                    if (error is SessionExpired) { if (studentForm) studentExpired(previous) else expired(previous) }
                    else MaterialAlertDialogBuilder(this).setTitle("Status pengiriman belum dipastikan")
                        .setMessage(friendly(error) + "\nMuat ulang data sebelum mengirim kembali agar tidak terjadi pengiriman ganda.")
                        .setPositiveButton("Tutup", null).show()
                })
            }.show()
    }

    private fun stageUpload(field: NativeField, uri: Uri, staged: MutableList<File>): UploadPart {
        var name = "lampiran"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) name = cursor.getString(0) ?: name }
        val file = File.createTempFile("bsi-upload-", ".tmp", cacheDir)
        staged += file
        val source = contentResolver.openInputStream(uri) ?: throw PortalError("Berkas tidak dapat dibuka. Pilih berkas kembali.")
        source.use { input -> file.outputStream().use { output ->
            val buffer = ByteArray(8192)
            var size = 0L
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val count = input.read(buffer)
                if (count < 0) break
                size += count
                if (field.maxBytes != null && size > field.maxBytes) throw PortalError("Ukuran ${field.label.lowercase()} melebihi batas pada form BSI.")
                output.write(buffer, 0, count)
            }
        } }
        if (field.accept.contains("pdf", true)) {
            val signature = ByteArray(5)
            file.inputStream().use { it.read(signature) }
            if (String(signature, Charsets.US_ASCII) != "%PDF-") throw PortalError("Lampiran harus berupa berkas PDF yang valid.")
        }
        val mime = contentResolver.getType(uri) ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
        return UploadPart(field.name, name, mime, file.length()) { file.inputStream() }
    }

    private fun downloadForm(form: NativeForm, values: List<Pair<String, String>>) {
        if (submitting) return
        submitting = true
        task("Mengunduh lampiran", {
            var downloaded: NativeFormState.PendingExport? = null
            try {
                val receive: (String, String, java.io.InputStream) -> Unit = { name, _, input ->
                    val file = File.createTempFile("bsi-download-", ".tmp", cacheDir)
                    downloaded = NativeFormState.PendingExport(file, name)
                    file.outputStream().use { input.copyTo(it) }
                }
                if (Uri.parse(form.action).host == "students.bsi.ac.id") studentClient.downloadForm(form, values, receive)
                else client.downloadForm(form.action, form.method, form.sourceUrl, values, receive)
                downloaded ?: throw PortalError("BSI belum mengembalikan berkas.")
            } catch (error: Exception) { downloaded?.file?.delete(); throw error }
        }, { downloaded ->
            submitting = false
            formState.pendingExport?.file?.delete()
            formState.pendingExport = downloaded
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, downloaded.name)
            try { exportPicker.launch(intent) } catch (_: ActivityNotFoundException) { downloaded.file.delete(); formState.pendingExport = null; toast("Pemilih lokasi simpan tidak tersedia.") }
        }, { error -> submitting = false; if (error is SessionExpired) { if (route.kind == "student") studentExpired() else expired() } else toast(friendly(error)) })
    }

    private fun performLinkAction(link: PortalLink) {
        if (submitting) return
        submitting = true
        formState.pendingWrite = true
        val previous = route
        task("Memproses tindakan BSI", { client.page(link.url) }, { doc ->
            submitting = false
            formState.pendingWrite = false
            renderNative(doc, previous)
        }, { error ->
            submitting = false
            formState.pendingWrite = false
            if (error is SessionExpired) expired(previous)
            else MaterialAlertDialogBuilder(this).setTitle("Status tindakan belum dipastikan").setMessage(friendly(error) + "\nMuat ulang data sebelum mencoba kembali.").setPositiveButton("Tutup", null).show()
        })
    }

    private fun expired(target: Route = route) {
        if (loggingIn) return
        recoverMyBest(target, automatic = true)
    }

    private fun download(url: String, disposition: String, mimeType: String, agent: String = "") {
        val uri = Uri.parse(url)
        if (uri.scheme != "https") { toast("Unduhan harus memakai HTTPS."); return }
        if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = arrayOf(url, disposition, mimeType, agent)
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        try {
            val filename = URLUtil.guessFileName(url, disposition, mimeType).replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            val request = DownloadManager.Request(uri).setTitle(filename)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            if (uri.host == "elearning.bsi.ac.id") CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
            if (agent.isNotBlank()) request.addRequestHeader("User-Agent", agent)
            if (mimeType.isNotBlank()) request.setMimeType(mimeType)
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            toast("Unduhan dimulai. Lihat folder Download.")
        } catch (_: Exception) { toast("Unduhan belum dapat dimulai. Muat ulang halaman lalu coba lagi.") }
    }

    private fun openExternal(url: String) {
        val uri = Uri.parse(url)
        if (uri.host == "elearning.bsi.ac.id") { openLink(PortalLink("MyBest", url)); return }
        if (uri.scheme != "https" && uri.scheme != "mailto" && uri.scheme != "tel") { toast("Tautan tidak didukung."); return }
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (_: ActivityNotFoundException) { toast("Tidak ada aplikasi untuk membuka tautan ini.") }
    }

    private fun <T> task(label: String, block: () -> T, success: (T) -> Unit, failure: ((Exception) -> Unit)? = null) {
        val token = epoch
        progress.visibility = View.VISIBLE; status.text = label; status.visibility = View.VISIBLE
        worker.execute {
            try {
                val result = block()
                runOnUiThread {
                    if (isDestroyed || token != epoch) return@runOnUiThread
                    progress.visibility = View.GONE; status.visibility = View.GONE
                    success(result)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (isDestroyed || token != epoch) return@runOnUiThread
                    progress.visibility = View.GONE; status.visibility = View.GONE
                    if (failure != null) failure(error)
                    else if (error is SessionExpired) { if (route.kind == "student") studentExpired() else expired() }
                    else showError(error) { navigate(route, false) }
                }
            }
        }
    }

    private fun friendly(error: Exception): String = when (error) {
        is UnknownHostException -> "Tidak dapat terhubung ke BSI. Periksa koneksi internet, lalu coba lagi."
        is SocketTimeoutException -> "BSI belum merespons. Periksa koneksi dan coba lagi."
        is PortalError, is SessionExpired -> error.message.orEmpty()
        else -> "Permintaan belum berhasil. Periksa koneksi atau coba lagi nanti."
    }

    private fun showError(error: Exception, retry: () -> Unit) {
        val content = content()
        heading(content, "Halaman belum dapat dimuat")
        content.addView(text(friendly(error), 16, muted))
        action(content, "Coba lagi") { retry() }
    }

    private fun loadingScreen(message: String) {
        val content = content()
        content.addView(text(message, 17, muted).also { it.setPadding(0, dp(40), 0, 0) })
    }

    private fun content(): LinearLayout {
        body.removeAllViews()
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        body.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val frame = FrameLayout(this)
        scroll.addView(frame, FrameLayout.LayoutParams(-1, -2))
        val content = column().apply { setPadding(dp(20), dp(20), dp(20), dp(28)) }
        val width = minOf(resources.displayMetrics.widthPixels - root.paddingLeft - root.paddingRight, dp(720))
        frame.addView(content, FrameLayout.LayoutParams(width, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        return content
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun text(value: String, size: Int = 16, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(), 1f)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private fun heading(parent: LinearLayout, title: String, subtitle: String = "") {
        parent.addView(text(title, 23, ink, true).apply { setPadding(0, dp(20), 0, dp(8)); ViewCompat.setAccessibilityHeading(this, true) })
        if (subtitle.isNotBlank()) parent.addView(text(subtitle, 14, muted))
    }
    private fun panel(parent: LinearLayout, color: Int = Color.WHITE): LinearLayout {
        val box = column().apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable().apply { setColor(color); cornerRadius = dp(12).toFloat() }
        }
        parent.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        return box
    }
    private fun empty(parent: LinearLayout, title: String, explanation: String) {
        val box = panel(parent)
        box.addView(text(title, 17, ink, true))
        box.addView(text(explanation, 14, muted).also { it.setPadding(0, dp(6), 0, 0) })
    }
    private fun action(parent: LinearLayout, label: String, primary: Boolean = true, click: () -> Unit): MaterialButton {
        val button = MaterialButton(this, null, if (primary) com.google.android.material.R.attr.materialButtonStyle else com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label; isAllCaps = false; textSize = 14f; minHeight = dp(52); minimumHeight = dp(52)
            cornerRadius = dp(8); maxLines = 5
            insetTop = dp(4); insetBottom = dp(4)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            if (primary) { backgroundTintList = ColorStateList.valueOf(navy); setTextColor(Color.WHITE) }
            else { setTextColor(navy); strokeColor = ColorStateList.valueOf(navy) }
            setOnClickListener { click() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return button
    }
    private fun input(parent: LinearLayout, label: String, type: Int): Pair<TextInputLayout, TextInputEditText> {
        val box = TextInputLayout(this).apply {
            hint = label; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxBackgroundColor = Color.WHITE
            boxStrokeColor = navy; defaultHintTextColor = ColorStateList.valueOf(muted)
            setBoxCornerRadii(dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat())
        }
        val edit = TextInputEditText(box.context).apply { inputType = type; setTextColor(ink); textSize = 16f; minHeight = dp(56); isSingleLine = true }
        box.addView(edit, LinearLayout.LayoutParams(-1, -2))
        parent.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        return box to edit
    }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    override fun onDestroy() { epoch++; worker.shutdownNow(); super.onDestroy() }
}
