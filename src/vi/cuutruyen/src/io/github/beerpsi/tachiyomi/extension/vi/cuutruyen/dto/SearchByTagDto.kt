package io.github.beerpsi.tachiyomi.extension.vi.cuutruyen.dto

import kotlinx.serialization.Serializable

@Serializable
data class SearchByTagDto(
    val mangas: List<MangaDto>,
    val tag: TagDto,
)
