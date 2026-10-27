package com.gis.reverse

import android.net.Uri
import androidx.lifecycle.ViewModel
import java.io.File

class NativeFormState : ViewModel() {
    data class Draft(val values: MutableMap<String, List<String>> = mutableMapOf(), val files: MutableMap<String, Uri> = mutableMapOf())
    data class PendingFile(val formKey: String, val fieldName: String)
    data class PendingExport(val file: File, val name: String)
    val drafts = mutableMapOf<String, Draft>()
    val researchMembers = mutableMapOf<String, MutableList<String>>()
    var pendingFile: PendingFile? = null
    var pendingExport: PendingExport? = null
    var pendingWrite = false
    fun draft(key: String) = drafts.getOrPut(key) { Draft() }
    override fun onCleared() { pendingExport?.file?.delete(); drafts.clear() }
}
