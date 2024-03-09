package io.github.beerpsi.tachiyomi.extension.all.smbshare.models

import kotlinx.serialization.Serializable

@Serializable
class MangaDetails(
    val title: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val status: Int? = null,
)
