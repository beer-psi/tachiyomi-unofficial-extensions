package eu.kanade.tachiyomi.multisrc.mangahub

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import org.jsoup.nodes.Entities

@Serializable
data class MangaListItem(
    val id: Int,
    val rank: Int,
    val title: String,
    val slug: String,
    val status: String,
    val author: String,
    val artist: String? = null,
    val description: String? = null,
    val genres: String,
    val image: String,
    val latestChapter: Float,
    val unauthFile: Boolean,
    val createdDate: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        val manga = this@MangaListItem

        url = "/manga/${manga.slug}"
        title = manga.title
        author = manga.author
        artist = manga.artist
        description = if (manga.description.isNullOrEmpty()) {
            null
        } else {
            Entities.unescape(manga.description)
        }
        genre = manga.genres
        status = when (manga.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "cancelled" -> SManga.CANCELLED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        thumbnail_url = "${MangaHub.CDN_THUMBS_URL}/${manga.image}"
    }
}

@Serializable
data class MangaList(
    val rows: List<MangaListItem>,
    val count: Int,
)

@Serializable
data class MangaListWrapper(
    val search: MangaList?,
)

@Serializable
data class MangaListResponse(
    val data: MangaListWrapper,
    val errors: List<ApiErrorMessages>? = null,
)
