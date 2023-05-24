package eu.kanade.tachiyomi.multisrc.mangahub

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

private val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

@Serializable
data class Chapter(
    val id: Int,
    val number: Float,
    val title: String,
    val slug: String,
    val date: String,
) {
    fun toSChapter(mangaSlug: String): SChapter = SChapter.create().apply {
        val chapter = this@Chapter

        val chapterNumber = chapter.number.toString().replace(".0", "")
        val slug = if (chapter.slug.isEmpty()) {
            "chapter-$chapterNumber"
        } else {
            chapter.slug
        }

        url = "/chapter/$mangaSlug/$slug#$chapterNumber"
        name = if (chapter.title.isEmpty()) {
            "Chapter $chapterNumber"
        } else {
            chapter.title
        }
        chapter_number = chapter.number
        date_upload = kotlin.runCatching {
            dateFormatter.parse(chapter.date)?.time
        }.getOrNull() ?: 0L
    }
}

@Serializable
data class ChapterListWrapper(
    val chapters: List<Chapter>,
)

@Serializable
data class ChapterListDataWrapper(
    val manga: ChapterListWrapper?,
)

@Serializable
data class ChapterListResponse(
    val data: ChapterListDataWrapper,
    val errors: List<ApiErrorMessages>? = null,
)
