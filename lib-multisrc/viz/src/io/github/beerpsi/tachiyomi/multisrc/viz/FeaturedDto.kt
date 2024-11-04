package io.github.beerpsi.tachiyomi.multisrc.viz

import kotlinx.serialization.Serializable

@Serializable
data class FeaturedSectionSeriesIdDto(
    val featured_section_series_id: String?,
) : VizDataObject()

@Serializable
data class FeaturedSectionSeriesDto(
    val featured_section_series: String?,
) : VizDataObject()

@Serializable
data class FeaturedSectionTitleDto(
    val featured_section_title: String?,
) : VizDataObject()

@Serializable
data class FeaturedChapterOffsetStartDto(
    val featured_chapter_offset_start: Float,
) : VizDataObject()

@Serializable
data class FeaturedChapterOffsetEndDto(
    val featured_chapter_offset_end: Float,
) : VizDataObject()
