package io.github.beerpsi.tachiyomi.extension.ja.mangagaugau.models

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import okhttp3.HttpUrl

@Serializable
class Manga(
    @ProtoNumber(1) val titleId: Int,
    @ProtoNumber(2) val titleName: String,
    @ProtoNumber(3) val titleNameKana: String? = null,
    @ProtoNumber(4) val singleListThumbnailUrl: String,
    @ProtoNumber(5) val spreadListThumbnailUrl: String? = null,
    @ProtoNumber(6) val shortDescription: String? = null,
    @ProtoNumber(7) val campaign: String? = null,
    @ProtoNumber(8) val numberOfBookmarks: Int? = null,
    @ProtoNumber(9) val badge: Badge = Badge.NONE,
    @ProtoNumber(10) val lastUpdated: String? = null,
) {
    fun toSManga(apiUrl: HttpUrl) = SManga.create().apply {
        url = titleId.toString()
        title = titleName
        thumbnail_url = "$apiUrl${singleListThumbnailUrl.removePrefix("/")}"
    }
}

@Serializable
enum class Badge {
    NONE,
    NEW,
    UPDATE,
    UNREAD,
}
