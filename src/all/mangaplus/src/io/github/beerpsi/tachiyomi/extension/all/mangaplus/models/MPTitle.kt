package io.github.beerpsi.tachiyomi.extension.all.mangaplus.models

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
data class MPTitle(
    val titleId: Int,
    val name: String,
    val author: String,
    val portraitImageUrl: String,
    val language: MPLanguage = MPLanguage.ENGLISH,
) {
    fun toSManga() = SManga.create().apply {
        url = "#/titles/$titleId"
        title = name
        author = this@MPTitle.author
        thumbnail_url = portraitImageUrl
    }
}
