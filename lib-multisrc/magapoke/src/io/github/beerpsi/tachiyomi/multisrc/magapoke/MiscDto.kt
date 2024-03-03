package io.github.beerpsi.tachiyomi.multisrc.magapoke

import kotlinx.serialization.Serializable

@Serializable
data class TabDto(
    val ranking_id: Int,
    val text: String,
)

@Serializable
data class IdDto(
    val id: Int,
)

@Serializable
data class PageDto(
    val index: Int,
    val image_url: String,
)

@Serializable
data class GenreDto(
    val genre_id: Int,
    val genre_name: String,
)
