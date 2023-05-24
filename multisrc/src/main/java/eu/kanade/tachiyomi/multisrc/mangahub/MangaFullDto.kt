package eu.kanade.tachiyomi.multisrc.mangahub

import kotlinx.serialization.Serializable

@Serializable
data class MangaFullWrapper(
    val manga: MangaListItem?,
)

@Serializable
data class MangaFullResponse(
    val data: MangaFullWrapper,
    val errors: List<ApiErrorMessages>? = null,
)
