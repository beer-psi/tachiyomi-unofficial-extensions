package io.github.beerpsi.tachiyomi.extension.en.fakku

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReaderPage(
    val page: Int,
    val image: String,
    val thumb: String,
)

@Serializable
data class ReaderContent(
    @SerialName("content_pages") val contentPages: String,
)

@Serializable
data class ReaderResponse(
    val content: ReaderContent,
    val pages: Map<String, ReaderPage>,
    @SerialName("key_hash") val keyHash: String? = null,
    @SerialName("key_data") val keyData: String? = null,
)
