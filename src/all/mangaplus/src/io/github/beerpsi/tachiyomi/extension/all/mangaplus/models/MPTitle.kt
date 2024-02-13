package io.github.beerpsi.tachiyomi.extension.all.mangaplus.models

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class MPTitle(
    @ProtoNumber(1) val titleId: Int,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val author: String,
    @ProtoNumber(4) val portraitImageUrl: String,
    @ProtoNumber(7) val language: MPLanguage = MPLanguage.ENGLISH,
) {
    fun toSManga() = SManga.create().apply {
        url = "#/titles/$titleId"
        title = name
        author = this@MPTitle.author
        thumbnail_url = portraitImageUrl
    }
}
