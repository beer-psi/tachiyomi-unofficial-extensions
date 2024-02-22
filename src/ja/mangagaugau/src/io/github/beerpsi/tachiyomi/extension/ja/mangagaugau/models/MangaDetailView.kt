package io.github.beerpsi.tachiyomi.extension.ja.mangagaugau.models

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import okhttp3.HttpUrl

@Serializable
class MangaDetailView(
    @ProtoNumber(1) val status: Status = Status.SUCCESS,
    @ProtoNumber(2) val userPoint: UserPoint,
    @ProtoNumber(3) val titleName: String,
    @ProtoNumber(4) val authorName: String,
    @ProtoNumber(5) val nextUpdateInfo: String,
    @ProtoNumber(6) val mainThumbnailUrl: String,
    @ProtoNumber(7) val label: Tag,
    @ProtoNumber(9) val chapters: List<Chapter>,
    @ProtoNumber(15) val isWebtoon: Boolean = false,
    @ProtoNumber(16) val campaignInfo: String? = null,
) {
    fun toSManga(apiUrl: HttpUrl) = SManga.create().apply {
        title = titleName
        author = authorName
        description = buildString {
            if (campaignInfo != null) {
                appendLine(campaignInfo)
            }
            append(nextUpdateInfo)
        }
        genre = buildList {
            add(label.name)

            if (isWebtoon) {
                add("Webtoon")
            }
        }.joinToString()
        thumbnail_url = "$apiUrl${mainThumbnailUrl.removePrefix("/")}"
    }
}

enum class Status {
    SUCCESS,
    CONTENT_NOT_FOUND,
}

@Serializable
class Tag(
    @ProtoNumber(1) val id: Int,
    @ProtoNumber(2) val name: String,
)
