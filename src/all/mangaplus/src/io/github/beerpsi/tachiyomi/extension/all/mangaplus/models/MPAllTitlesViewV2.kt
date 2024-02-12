package io.github.beerpsi.tachiyomi.extension.all.mangaplus.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MPAllTitlesViewV2(
    val allTitlesViewV2: MPAllTitlesViewV2Inner,
)

@Serializable
data class MPAllTitlesViewV2Inner(
    @SerialName("AllTitlesGroup") val allTitlesGroup: List<MPAllTitlesGroup>,
)

@Serializable
data class MPAllTitlesGroup(
    val theTitle: String,
    val titles: List<MPTitle>,
)
