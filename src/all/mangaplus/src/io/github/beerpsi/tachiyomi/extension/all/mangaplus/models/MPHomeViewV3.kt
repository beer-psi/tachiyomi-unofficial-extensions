package io.github.beerpsi.tachiyomi.extension.all.mangaplus.models

import kotlinx.serialization.Serializable

@Serializable
data class MPHomeViewV3(
    val homeViewV3: MPHomeViewV3Inner,
)

@Serializable
data class MPHomeViewV3Inner(
    val groups: List<MPUpdatedTitleV2Group>,
)

@Serializable
data class MPUpdatedTitleV2Group(
    val groupName: String,
    val groupNameDays: Int = 1,
    val titleGroups: List<MPOriginalTitleGroup>,
)

@Serializable
data class MPOriginalTitleGroup(
    val theTitle: String,
    val titles: List<MPUpdatedTitle>,
)

@Serializable
data class MPUpdatedTitle(
    val title: MPTitle,
)
