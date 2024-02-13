package io.github.beerpsi.tachiyomi.extension.all.mangaplus.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class MPHomeViewV3(
    @ProtoNumber(2) val groups: List<MPUpdatedTitleV2Group>,
    @ProtoNumber(11) val userSubscription: MPUserSubscription,
)

@Serializable
data class MPUpdatedTitleV2Group(
    @ProtoNumber(1) val groupName: String,
    @ProtoNumber(2) val titleGroups: List<MPOriginalTitleGroup>,
    @ProtoNumber(3) val groupNameDays: Int = 1,
)

@Serializable
data class MPOriginalTitleGroup(
    @ProtoNumber(1) val theTitle: String,
    @ProtoNumber(3) val titles: List<MPUpdatedTitle>,
)

@Serializable
data class MPUpdatedTitle(
    @ProtoNumber(1) val title: MPTitle,
)
