package com.gis.reverse.data

data class LoginChallenge(val csrf: String, val svg: String, val error: String = "")
data class Student(val name: String, val identifier: String, val email: String)
data class PortalLink(val title: String, val url: String)
data class Course(
    val title: String, val code: String, val classroom: String, val schedule: String,
    val lecturer: String, val room: String, val campus: String, val credits: String,
    val today: Boolean, val links: List<PortalLink>
)
data class TableRow(val fields: List<Pair<String, String>>, val links: List<PortalLink>, val forms: List<NativeForm> = emptyList())
data class PortalTable(val title: String, val rows: List<TableRow>)
data class LearningContent(val documents: List<PortalLink>, val videos: List<PortalLink>, val tables: List<PortalTable>)
class SessionExpired : Exception("Sesi berakhir. Silakan masuk kembali.")
class PortalError(message: String) : Exception(message)
