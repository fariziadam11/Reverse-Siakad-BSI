package com.gis.reverse.data

data class StudentLoginChallenge(
    val form: NativeForm,
    val error: String = "",
    val captchaPrompt: String = "",
    val captchaSvg: String = ""
)
