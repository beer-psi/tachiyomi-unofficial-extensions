package io.github.beerpsi.tachiyomi.extension.vi.cuutruyen.dto

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import io.github.beerpsi.tachiyomi.extension.vi.cuutruyen.CuuTruyenImageInterceptor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
}

@Serializable
data class ChapterDto(
    val id: Int,
    val order: Int,
    val number: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val name: String? = null,
    val pages: List<PageDto>? = null,
) {
    fun toSChapter(mangaUrl: String) = SChapter.create().apply {
        val dto = this@ChapterDto
        url = "$mangaUrl/chapters/$id"
        name = buildString {
            append("Chương ")
            append(dto.number)

            if (!dto.name.isNullOrEmpty()) {
                append(": ")
                append(dto.name)
            }
        }
        date_upload = try {
            DATE_FORMATTER.parse(dto.createdAt)!!.time
        } catch (e: ParseException) {
            0L
        }
        chapter_number = dto.number.toFloatOrNull() ?: -1f
    }
}

@Serializable
data class PageDto(
    val id: Int,
    val order: Int,
    val width: Int?,
    val height: Int?,
    val status: String,
    @SerialName("image_url") val imageUrl: String,
    @SerialName("image_url_size") val imageUrlSize: Int,
    @SerialName("drm_data") val drmData: String,
) {
    fun toPage(): Page {
        val dto = this@PageDto

        if (dto.status != "processed") {
            val message = when (dto.status) {
                "enqueued" -> "Đang đợi xử lý hình ảnh, vui lòng chờ ít phút."
                "processing" -> "Đang xử lý hình ảnh, vui lòng chờ ít phút."
                "failed" -> "Xử lý hình ảnh thất bại."
                else -> "Hình ảnh chưa sẵn sàng."
            }

            throw Exception(message)
        }

        val url = imageUrl.toHttpUrl().newBuilder()
            .fragment("${CuuTruyenImageInterceptor.DRM_DATA_KEY}=${drmData.replace("\n", "")}")
            .build()
            .toString()
        return Page(dto.order, imageUrl = url)
    }
}
