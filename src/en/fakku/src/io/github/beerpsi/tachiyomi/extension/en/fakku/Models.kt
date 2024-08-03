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
data class ReaderResponse(
    val pages: Map<String, ReaderPage>,
    @SerialName("key_hash") val keyHash: String? = null,
    @SerialName("key_data") val keyData: String? = null,
)

@Serializable
data class GsConfigEndPopup(
    val title: String,
    val content: String,
)

@Serializable
data class GsConfig(
    val redirect: Int,
    val dark: Boolean,
    val apiRoute: String,
    val endPopup: GsConfigEndPopup? = null,
)
