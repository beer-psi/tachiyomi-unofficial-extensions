package io.github.beerpsi.tachiyomi.multisrc.viz

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

@Serializable(with = VizDataObjectSerializer::class)
abstract class VizDataObject

object VizDataObjectSerializer : JsonContentPolymorphicSerializer<VizDataObject>(VizDataObject::class) {
    override fun selectDeserializer(element: JsonElement) = when {
        "manga" in element.jsonObject -> MangaWrapperDto.serializer()
        "manga_series" in element.jsonObject -> MangaSeriesWrapperDto.serializer()
        "featured_section_series_id" in element.jsonObject -> FeaturedSectionSeriesIdDto.serializer()
        "featured_section_series" in element.jsonObject -> FeaturedSectionSeriesDto.serializer()
        "featured_section_title" in element.jsonObject -> FeaturedSectionTitleDto.serializer()
        "featured_chapter_offset_start" in element.jsonObject -> FeaturedChapterOffsetStartDto.serializer()
        "featured_chapter_offset_end" in element.jsonObject -> FeaturedChapterOffsetEndDto.serializer()
        else -> throw Exception("Unknown VizDataObject type")
    }
}
