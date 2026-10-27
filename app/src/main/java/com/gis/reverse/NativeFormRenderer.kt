package com.gis.reverse

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.*
import com.gis.reverse.data.NativeField
import com.gis.reverse.data.NativeForm
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class NativeFormRenderer(
    private val activity: Activity,
    private val state: NativeFormState,
    private val pickFile: (NativeForm, NativeField) -> Unit,
    private val submit: (NativeForm, List<Pair<String, String>>, Map<String, Uri>) -> Unit
) {
    private val navy = Color.rgb(30, 58, 138)
    private val ink = Color.rgb(15, 23, 42)
    private val muted = Color.rgb(71, 85, 105)
    private val errorColor = Color.rgb(153, 27, 27)
    private data class Binding(val values: () -> List<String>, val validate: () -> Boolean)

    fun render(parent: LinearLayout, form: NativeForm) {
        if (form.download) {
            button(parent, form.submitLabel) { if (!form.disabled) submit(form, form.hidden, emptyMap()) }.isEnabled = !form.disabled
            return
        }
        val wrapper = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        parent.addView(wrapper, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        if (form.collapsed || form.title == "Ubah password") {
            button(wrapper, form.title) { body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
            body.visibility = View.GONE
        } else label(wrapper, form.title, 20)
        wrapper.addView(body)
        val draft = state.draft(form.key)
        val bindings = mutableMapOf<String, Binding>()
        val textFields = mutableMapOf<String, TextInputEditText>()
        val textLayouts = mutableMapOf<String, TextInputLayout>()
        val fieldLabels = mutableMapOf<String, TextView>()
        val enabledFields = mutableMapOf<String, Boolean>()
        fun updateDuration() {
            if (Uri.parse(form.action).path != "/mahasiswa/surat-riset-tugas") return
            val single = bindings["lama_pelaksanaan"]?.values()?.firstOrNull() == "1"
            listOf("tanggal_akhir" to !single, "jam_awal" to single, "jam_akhir" to single).forEach { (name, enabled) ->
                enabledFields[name] = enabled
                textLayouts[name]?.visibility = if (enabled) View.VISIBLE else View.GONE
                fieldLabels[name]?.visibility = if (enabled) View.VISIBLE else View.GONE
                if (!enabled) textLayouts[name]?.error = null
            }
        }
        form.notes.filter { note -> form.fields.none { it.help.contains(note) } }.forEach { note -> label(body, note, 14, muted) }
        form.fields.forEach { field ->
            val saved = draft.values[field.name]
            when (field.type) {
                "file" -> {
                    label(body, field.label + if (field.required) " *" else "", 15)
                    val fileName = draft.files[field.name]?.let { nameOf(it) }.orEmpty()
                    label(body, fileName.ifBlank { "Belum ada berkas dipilih." }, 14, muted)
                    if (field.help.isNotBlank()) label(body, field.help, 13, muted)
                    val error = label(body, "", 13, errorColor).apply { visibility = View.GONE }
                    button(body, if (fileName.isBlank()) "Pilih berkas" else "Ganti berkas") { pickFile(form, field) }
                    if (fileName.isNotBlank()) button(body, "Hapus pilihan berkas") {
                        draft.files.remove(field.name)
                        (wrapper.parent as? LinearLayout)?.let { container -> val index = container.indexOfChild(wrapper); container.removeView(wrapper); render(container, form); val replacement = container.getChildAt(container.childCount - 1); container.removeView(replacement); container.addView(replacement, index) }
                    }
                    bindings[field.name] = Binding({ emptyList() }, {
                        val selected = draft.files[field.name]
                        val message = when {
                            field.required && selected == null -> "Pilih ${field.label.lowercase()}."
                            selected != null && field.maxBytes != null && sizeOf(selected)?.let { it > field.maxBytes } == true -> "Ukuran berkas melebihi batas pada form BSI."
                            else -> ""
                        }
                        error.text = message; error.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
                        message.isBlank()
                    })
                }
                "radio", "checkbox", "select" -> {
                    label(body, field.label + if (field.required) " *" else "", 15)
                    val error = label(body, "", 13, errorColor).apply { visibility = View.GONE }
                    val selected = saved?.toMutableSet() ?: field.options.filter { it.selected }.map { it.value }.toMutableSet()
                    val read: () -> List<String>
                    if (field.type == "radio") {
                        val group = RadioGroup(activity).apply { orientation = RadioGroup.VERTICAL; tag = "native-field:${field.name}" }
                        field.options.forEach { option ->
                            val choice = MaterialRadioButton(activity).apply {
                                id = View.generateViewId(); text = option.label; tag = option.value
                                setTextColor(ink); minHeight = dp(48); isEnabled = !field.readOnly && !option.disabled
                            }
                            group.addView(choice)
                            if (option.value in selected) group.check(choice.id)
                        }
                        body.addView(group)
                        read = { group.findViewById<View>(group.checkedRadioButtonId)?.tag?.toString()?.let { listOf(it) }.orEmpty() }
                        group.setOnCheckedChangeListener { _, _ -> draft.values[field.name] = read(); updateDuration() }
                    } else if (field.type == "checkbox" || field.multiple) {
                        if (field.options.size > 30) {
                            val summary = label(body, "${selected.size} dipilih", 14, muted)
                            button(body, "Pilih ${field.label.lowercase()}") {
                                val container = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
                                val search = android.widget.EditText(activity).apply { hint = "Cari ${field.label.lowercase()}"; minHeight = dp(56); isSingleLine = true }
                                container.addView(search)
                                val scroll = ScrollView(activity)
                                val choices = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                                scroll.addView(choices)
                                container.addView(scroll, LinearLayout.LayoutParams(-1, dp(320)))
                                fun renderChoices() {
                                    choices.removeAllViews()
                                    val filtered = field.options.filter { it.label.contains(search.text.toString(), true) }
                                    label(choices, "${minOf(40, filtered.size)} dari ${filtered.size} pilihan. Persempit pencarian untuk pilihan lainnya.", 13, muted)
                                    filtered.take(40).forEach { option ->
                                        choices.addView(MaterialCheckBox(activity).apply {
                                            text = option.label; setTextColor(ink); minHeight = dp(48)
                                            isChecked = option.value in selected; isEnabled = !option.disabled && !field.readOnly
                                            setOnCheckedChangeListener { _, checked ->
                                                if (checked) selected.add(option.value) else selected.remove(option.value)
                                                draft.values[field.name] = selected.toList()
                                                summary.text = "${selected.size} dipilih"
                                            }
                                        })
                                    }
                                }
                                search.addTextChangedListener(object : TextWatcher {
                                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderChoices() }
                                    override fun afterTextChanged(s: Editable?) = Unit
                                })
                                renderChoices()
                                com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle(field.label).setView(container).setPositiveButton("Selesai", null).show()
                            }
                            read = { selected.toList() }
                        } else {
                        val choices = field.options.map { option ->
                            MaterialCheckBox(activity).apply {
                                text = option.label; tag = option.value; isChecked = option.value in selected
                                setTextColor(ink); minHeight = dp(48); isEnabled = !field.readOnly && !option.disabled
                                body.addView(this)
                            }
                        }
                        read = { choices.filter { it.isChecked }.map { it.tag.toString() } }
                        choices.forEach { it.setOnCheckedChangeListener { _, _ -> draft.values[field.name] = read() } }
                        }
                    } else {
                        val spinner = Spinner(activity).apply { minimumHeight = dp(52); isEnabled = !field.readOnly }
                        spinner.adapter = object : ArrayAdapter<String>(activity, android.R.layout.simple_spinner_dropdown_item, field.options.map { it.label }) {
                            override fun isEnabled(position: Int) = !field.options[position].disabled
                        }
                        val position = field.options.indexOfFirst { it.value in selected }.takeIf { it >= 0 }
                            ?: field.options.indexOfFirst { !it.disabled }.coerceAtLeast(0)
                        if (field.options.isNotEmpty()) spinner.setSelection(position)
                        body.addView(spinner, LinearLayout.LayoutParams(-1, -2))
                        read = { field.options.getOrNull(spinner.selectedItemPosition)?.value?.let { listOf(it) }.orEmpty() }
                        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                                draft.values[field.name] = read()
                                if (Uri.parse(form.action).path == "/mahasiswa/surat-riset/template" && field.name == "jenis_surat") {
                                    val selected = read().firstOrNull()
                                    textFields["waktu_pelaksanaan"]?.setText(when (selected) { "magang_pkl" -> "3-6"; "riset" -> "1-3"; else -> "" })
                                    textLayouts["judul_skripsi"]?.visibility = if (selected == "riset") View.VISIBLE else View.GONE
                                    fieldLabels["judul_skripsi"]?.visibility = if (selected == "riset") View.VISIBLE else View.GONE
                                }
                            }
                            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                        }
                    }
                    bindings[field.name] = Binding(read, {
                        val valid = !field.required || read().any { it.isNotBlank() }
                        error.text = "Pilih ${field.label.lowercase()}."; error.visibility = if (valid) View.GONE else View.VISIBLE
                        valid
                    })
                }
                else -> {
                    val fullLabel = (if (field.name == "password_confirmation") "Konfirmasi password" else field.label) + if (field.required) " *" else ""
                    val largeText = activity.resources.configuration.fontScale >= 1.5f
                    if (largeText) fieldLabels[field.name] = label(body, fullLabel, 15)
                    val box = TextInputLayout(activity).apply {
                        hint = fullLabel
                        isExpandedHintEnabled = false
                        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE; boxBackgroundColor = Color.WHITE
                        boxStrokeColor = navy; defaultHintTextColor = ColorStateList.valueOf(muted)
                        setBoxCornerRadii(dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat())
                    }
                    val edit = TextInputEditText(box.context).apply {
                        inputType = when (field.type) {
                            "password" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                            "email" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                            "url" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                            "number" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                            "tel" -> InputType.TYPE_CLASS_PHONE
                            "textarea" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                            else -> InputType.TYPE_CLASS_TEXT
                        }
                        isSingleLine = field.type != "textarea"
                        if (field.type == "textarea") { minLines = 4; gravity = android.view.Gravity.TOP }
                        minHeight = dp(56); textSize = 16f; setTextColor(ink)
                        setText(if (field.type == "password") "" else saved?.firstOrNull() ?: field.value)
                        isSaveEnabled = false
                        if (largeText) contentDescription = fullLabel
                        if (field.readOnly) {
                            keyListener = null; isFocusable = false
                            if (largeText) isSingleLine = false
                        }
                        field.maxLength?.let { filters = arrayOf(InputFilter.LengthFilter(it)) }
                    }
                    if (field.type in listOf("date", "time") && !field.readOnly) {
                        edit.keyListener = null
                        edit.isFocusable = true
                        edit.setOnClickListener {
                            val now = java.util.Calendar.getInstance()
                            if (field.type == "date") {
                                val parts = edit.text.toString().split('-').mapNotNull { it.toIntOrNull() }
                                android.app.DatePickerDialog(activity, { _, year, month, day ->
                                    edit.setText(String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day))
                                }, parts.getOrNull(0) ?: now.get(java.util.Calendar.YEAR), parts.getOrNull(1)?.minus(1) ?: now.get(java.util.Calendar.MONTH), parts.getOrNull(2) ?: now.get(java.util.Calendar.DAY_OF_MONTH)).show()
                            } else {
                                val parts = edit.text.toString().split(':').mapNotNull { it.toIntOrNull() }
                                android.app.TimePickerDialog(activity, { _, hour, minute ->
                                    edit.setText(String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute))
                                }, parts.getOrNull(0) ?: now.get(java.util.Calendar.HOUR_OF_DAY), parts.getOrNull(1) ?: 0, true).show()
                            }
                        }
                    }
                    if (field.type == "password") box.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
                    box.addView(edit, LinearLayout.LayoutParams(-1, -2))
                    if (largeText) { box.isHintEnabled = false; edit.hint = null }
                    textFields[field.name] = edit
                    textLayouts[field.name] = box
                    body.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
                    if (field.help.isNotBlank()) box.helperText = field.help
                    val read = { listOf(edit.text.toString()) }
                    if (field.type != "password") edit.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { draft.values[field.name] = read() }
                        override fun afterTextChanged(s: Editable?) = Unit
                    })
                    bindings[field.name] = Binding(read, {
                        val value = edit.text.toString()
                        val dynamicDuration = Uri.parse(form.action).path == "/mahasiswa/surat-riset-tugas" && field.name in listOf("tanggal_akhir", "jam_awal", "jam_akhir")
                        val message = when {
                            enabledFields[field.name] == false -> ""
                            (if (Uri.parse(form.action).path == "/mahasiswa/surat-riset/template" && field.name == "judul_skripsi") bindings["jenis_surat"]?.values()?.firstOrNull() == "riset" else field.required || dynamicDuration) && value.isBlank() -> "Isi ${field.label.lowercase()}."
                            field.type == "email" && value.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(value).matches() -> "Masukkan alamat email yang valid."
                            field.type == "url" && value.isNotBlank() && !validUrl(value) -> "Masukkan tautan http atau https yang valid."
                            field.minLength != null && value.isNotEmpty() && value.length < field.minLength -> "Minimal ${field.minLength} karakter."
                            else -> ""
                        }
                        box.error = message.ifBlank { null }
                        message.isBlank()
                    })
                }
            }
        }
        updateDuration()
        val formError = label(body, "", 14, errorColor).apply { visibility = View.GONE }
        button(body, form.submitLabel) {
            var valid = true
            bindings.values.forEach { if (!it.validate()) valid = false }
            val editable = bindings.filter { enabledFields[it.key] != false }.flatMap { (name, binding) -> binding.values().map { name to it } }
            if (Uri.parse(form.action).path == "/mahasiswa/surat-riset-tugas") {
                val values = editable.toMap()
                val start = values["tanggal_awal"].orEmpty()
                val end = values["tanggal_akhir"].orEmpty()
                val invalid = end.isNotBlank() && start.isNotBlank() && end <= start
                formError.text = if (invalid) "Tanggal akhir harus setelah tanggal awal kegiatan." else ""
                formError.visibility = if (invalid) View.VISIBLE else View.GONE
                if (invalid) valid = false
            }
            if (Uri.parse(form.action).path == "/profil/update") {
                val values = editable.toMap()
                val password = values["password"].orEmpty()
                val message = when {
                    values["current_password"].isNullOrBlank() || password.isBlank() || values["password_confirmation"].isNullOrBlank() -> ""
                    password.length < 8 || !password.any { it.isUpperCase() } || !password.any { it.isLowerCase() } || !password.any { it.isDigit() } || !password.any { !it.isLetterOrDigit() && !it.isWhitespace() } -> "Password baru harus minimal 8 karakter dengan huruf besar, huruf kecil, angka, dan simbol."
                    password != values["password_confirmation"] -> "Konfirmasi password belum cocok."
                    else -> ""
                }
                formError.text = message; formError.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
                if (message.isNotBlank()) valid = false
            }
            if (Uri.parse(form.action).path == "/mahasiswa/password") {
                val values = editable.toMap()
                val mismatch = !values["new_password"].isNullOrBlank() && !values["new_password_confirmation"].isNullOrBlank() &&
                    values["new_password"] != values["new_password_confirmation"]
                formError.text = if (mismatch) "Konfirmasi password belum cocok." else ""
                formError.visibility = if (mismatch) View.VISIBLE else View.GONE
                if (mismatch) valid = false
            }
            if (valid) {
                val values = form.hidden + editable + if (form.submitName.isNotBlank()) listOf(form.submitName to form.submitValue) else emptyList()
                submit(form, values, draft.files.toMap())
            }
        }.isEnabled = !form.disabled
    }

    private fun validUrl(value: String): Boolean = try { val uri = Uri.parse(value.trim()); uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && !value.contains(Regex("\\s")) } catch (_: Exception) { false }
    private fun nameOf(uri: Uri): String = try { activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Berkas dipilih" } catch (_: Exception) { "Berkas dipilih" }
    private fun sizeOf(uri: Uri): Long? = try { activity.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null } } catch (_: Exception) { null }
    private fun label(parent: LinearLayout, value: String, size: Int, color: Int = ink): TextView = TextView(activity).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); setLineSpacing(dp(3).toFloat(), 1f)
        if (size >= 15) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(8), 0, dp(4)); parent.addView(this)
    }
    private fun button(parent: LinearLayout, title: String, action: () -> Unit): MaterialButton = MaterialButton(activity).apply {
        text = title; isAllCaps = false; maxLines = 5; minHeight = dp(52); cornerRadius = dp(8)
        backgroundTintList = ColorStateList.valueOf(navy); setTextColor(Color.WHITE)
        setOnClickListener { action() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }
    private fun dp(value: Int) = (activity.resources.displayMetrics.density * value).toInt()
}
