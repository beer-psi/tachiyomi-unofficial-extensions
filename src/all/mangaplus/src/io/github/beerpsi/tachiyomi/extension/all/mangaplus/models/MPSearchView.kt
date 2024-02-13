package io.github.beerpsi.tachiyomi.extension.all.mangaplus.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class MPSearchView(
    @ProtoNumber(2) val allTags: List<MPTag>,
    @ProtoNumber(3) val allTitlesGroup: List<MPAllTitlesGroup>,
)

@Serializable
data class MPAllTitlesGroup(
    @ProtoNumber(1) val theTitle: String,
    @ProtoNumber(2) val titles: List<MPTitle>,
    @ProtoNumber(3) val tags: List<MPTag> = emptyList(),
    @ProtoNumber(4) val label: MPLabel? = MPLabel(MPLabelCode.WEEKLY_SHOUNEN_JUMP),
    @ProtoNumber(5) val nextChapterStartTimestamp: Int,
)

@Serializable
data class MPTag(
    @ProtoNumber(1) val tag: String,
    @ProtoNumber(2) val slug: String,
)
