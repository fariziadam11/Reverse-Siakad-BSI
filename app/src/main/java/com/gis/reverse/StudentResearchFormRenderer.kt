package com.gis.reverse

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.text.InputType
import android.widget.LinearLayout
import android.widget.TextView
import com.gis.reverse.data.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.UUID

class StudentResearchFormRenderer(
    private val activity: Activity,
    private val state: NativeFormState,
    private val lookup: (NativeForm, String, (Map<String, String>) -> Unit) -> Unit,
    private val pickFile: (NativeForm, NativeField) -> Unit,
    private val submit: (NativeForm, List<Pair<String, String>>, Map<String, Uri>) -> Unit
) {
    private val memberNames = listOf("nim[]", "nama[]", "prodi[]", "hp[]", "jenjang[]")
    private val navy = Color.rgb(30, 58, 138)

    fun render(parent: LinearLayout, original: NativeForm) {
        val wrapper = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        parent.addView(wrapper)
        val members = state.researchMembers.getOrPut(original.key) { mutableListOf("owner") }
        val draft = state.draft(original.key)
        fun key(member: String, name: String) = "member:$member|$name"
        fun button(title: String, action: () -> Unit) {
            wrapper.addView(MaterialButton(activity).apply {
                text = title; isAllCaps = false; maxLines = 4; minHeight = dp(52)
                setTextColor(Color.WHITE); backgroundTintList = ColorStateList.valueOf(navy)
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        fun redraw() {
            wrapper.removeAllViews()
            wrapper.addView(TextView(activity).apply {
                text = "Anggota pertama berasal dari akun Anda. Tambahkan anggota melalui pencarian NIM sebelum mengirim surat."
                textSize = 14f; setTextColor(Color.rgb(71, 85, 105)); setPadding(0, dp(12), 0, dp(12))
            })
            button("Tambah anggota surat") {
                val box = TextInputLayout(activity).apply { hint = "NIM anggota"; setPadding(dp(20), dp(12), dp(20), 0) }
                val input = TextInputEditText(box.context).apply { inputType = InputType.TYPE_CLASS_NUMBER; minHeight = dp(56) }
                box.addView(input)
                val dialog = MaterialAlertDialogBuilder(activity).setTitle("Cari anggota surat")
                    .setView(box).setNegativeButton("Batal", null).setPositiveButton("Cari", null).create()
                dialog.setOnShowListener {
                    dialog.getButton(-1).setOnClickListener {
                        val nim = input.text.toString().trim()
                        if (nim.isBlank()) { box.error = "Isi NIM anggota."; return@setOnClickListener }
                        dialog.dismiss()
                        lookup(original, nim) { data ->
                            val id = UUID.randomUUID().toString()
                            members += id
                            memberNames.forEach { name ->
                                val value = if (name == "nim[]") nim else data[name.removeSuffix("[]")].orEmpty()
                                draft.values[key(id, name)] = listOf(value)
                            }
                            redraw()
                        }
                    }
                }
                dialog.show()
            }
            members.drop(1).toList().forEachIndexed { index, id ->
                val nim = draft.values[key(id, "nim[]")]?.firstOrNull().orEmpty()
                button("Hapus anggota ${index + 2}: $nim") {
                    members.remove(id)
                    memberNames.forEach { draft.values.remove(key(id, it)) }
                    redraw()
                }
            }
            val common = original.fields.filter { it.name !in memberNames }
            val fields = common + members.flatMapIndexed { index, id ->
                memberNames.mapNotNull { name ->
                    val field = original.fields.firstOrNull { it.name == name } ?: return@mapNotNull null
                    field.copy(name = key(id, name), label = "${field.label} (anggota ${index + 1})",
                        value = if (id == "owner") field.value else "", readOnly = name != "hp[]", required = name != "hp[]")
                }
            }
            NativeFormRenderer(activity, state, pickFile) { _, values, files ->
                if (values.firstOrNull { it.first == "jenis_pelaksanaan" }?.second == "Berkelompok" && members.size < 2) {
                    MaterialAlertDialogBuilder(activity).setTitle("Tambahkan anggota surat")
                        .setMessage("Pengajuan berkelompok memerlukan minimal satu anggota tambahan sesuai form BSI.")
                        .setPositiveButton("Tutup", null).show()
                    return@NativeFormRenderer
                }
                submit(original, values.map { (name, value) -> (if (name.startsWith("member:")) name.substringAfter('|') else name) to value }, files)
            }.render(wrapper, original.copy(fields = fields))
        }
        redraw()
    }

    private fun dp(value: Int) = (activity.resources.displayMetrics.density * value).toInt()
}
