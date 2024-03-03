package io.github.beerpsi.tachiyomi.multisrc.viz

import kotlinx.serialization.Serializable

@Serializable
data class MangaWrapperDto(
    val manga: MangaDto,
) : VizDataObject()

@Serializable
data class MangaDto(
    val id: Int,
    val manga_series_common_id: Int,
    val published: Boolean,
    val publication_date: String?,
    val updated_at: String,
    val created_at: String,
    val series_vanityurl: String,
    val numpages: Int,
    val epoch_pub_date: Int?,
    val epoch_exp_date: Int?,
    val volume: Int?,
    val chapter: String?,
    val thumburl: String?,
)

@Serializable
data class MangaSeriesWrapperDto(
    val manga_series: MangaSeriesDto,
) : VizDataObject()

@Serializable
data class MangaSeriesDto(
    val id: Int,
    val title: String,
    val chapter_latest_pub_date: Int,
    val tagline: String?,
    val synopsis: String,
    val vanityurl: String,
    val show_chapter: Boolean,
    val latest_author: String?,
    val title_sort: String,
    val link_img_url: String,
    val num_chapters_free: Int,
)
