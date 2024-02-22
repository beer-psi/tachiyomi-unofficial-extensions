package io.github.beerpsi.tachiyomi.extension.ja.mangagaugau.models

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import java.text.SimpleDateFormat
import java.util.Locale

private val DATE_FORMAT = SimpleDateFormat("yyyy/MM/dd", Locale.ROOT)
private val CHAPTER_NUMBER_REGEX = Regex("""^第(\d+)話\((\d+)\)$""")

@Serializable
class Chapter(
    @ProtoNumber(1) val id: Int,
    @ProtoNumber(2) val mainName: String,
    @ProtoNumber(3) val subName: String? = null,
    @ProtoNumber(8) val datePublished: String,
) {
    fun toSChapter() = SChapter.create().apply {
        url = id.toString()
        name = buildString {
            append(mainName)

            if (subName != null) {
                append("「")
                append(subName)
                append("」")
            }
        }
        chapter_number = CHAPTER_NUMBER_REGEX.matchEntire(mainName)?.let { m ->
            "${m.groupValues[1]}.${m.groupValues[2]}".toFloat()
        } ?: -1F
        date_upload = try {
            DATE_FORMAT.parse(datePublished)!!.time
        } catch (_: Exception) {
            0L
        }
    }
}
