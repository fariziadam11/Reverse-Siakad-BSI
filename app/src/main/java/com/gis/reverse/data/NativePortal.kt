package com.gis.reverse.data

data class NativeOption(val value: String, val label: String, val selected: Boolean = false, val disabled: Boolean = false)
data class NativeField(
    val name: String, val label: String, val type: String, val value: String,
    val required: Boolean = false, val readOnly: Boolean = false,
    val options: List<NativeOption> = emptyList(), val multiple: Boolean = false,
    val maxLength: Int? = null, val minLength: Int? = null,
    val accept: String = "", val maxBytes: Long? = null, val help: String = ""
)
data class NativeForm(
    val key: String, val title: String, val action: String, val method: String, val sourceUrl: String,
    val hidden: List<Pair<String, String>>, val fields: List<NativeField>,
    val submitLabel: String, val submitName: String = "", val submitValue: String = "",
    val multipart: Boolean = false, val collapsed: Boolean = false,
    val download: Boolean = false, val disabled: Boolean = false,
    val notes: List<String> = emptyList()
)
data class NativeNotice(val text: String, val error: Boolean)
data class NativePage(
    val facts: List<Pair<String, String>>, val paragraphs: List<String>, val statuses: List<String>,
    val notices: List<NativeNotice>, val forms: List<NativeForm>, val links: List<PortalLink>,
    val tables: List<PortalTable>
)
