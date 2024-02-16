package io.github.beerpsi.tachiyomi.extension.vi.cuutruyen.dto

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthorDto(
    val name: String,
)

@Serializable
data class TeamDto(
    val id: Int,
    val name: String,
    val description: String,
)

@Serializable
data class TagDto(
    val name: String,
    val slug: String,
    @SerialName("tagging_count") val taggingCount: Int,
)

@Serializable
data class MangaDto(
    val id: Int,
    val name: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("cover_mobile_url") val coverMobileUrl: String? = null,

    val author: AuthorDto? = null,
    @SerialName("author_name") val authorName: String? = null,

    val description: String? = null,
    val team: TeamDto? = null,

    val tags: List<TagDto>? = null,
) {
    fun toSManga(coverQuality: String? = null): SManga = SManga.create().apply {
        val dto = this@MangaDto

        url = "/mangas/${dto.id}"
        title = dto.name ?: ""
        author = dto.author?.name ?: dto.authorName
        description = buildString {
            if (dto.team != null) {
                append("Nhóm dịch: ")
                appendLine(dto.team.name)
                appendLine()
            }

            append(dto.description ?: "")
        }

        thumbnail_url = when (coverQuality) {
            "cover_mobile_url" -> dto.coverMobileUrl
            else -> dto.coverUrl
        }
        dto.tags?.map { it.name }?.let {
            genre = it.joinToString()
            status = when {
                it.contains("đang tiến hành") -> SManga.ONGOING
                it.contains("đã hoàn thành") -> SManga.COMPLETED
                it.contains("tạm ngưng") -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }
}
