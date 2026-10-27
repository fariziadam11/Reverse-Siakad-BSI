package com.gis.reverse.data

import java.io.InputStream

data class UploadPart(
    val fieldName: String,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val openStream: () -> InputStream
)
