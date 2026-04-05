package io.github.beerpsi.tachiyomi.extension.all.mangaplus.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoOneOf

// jp.co.comic.jump.proto.HomeViewV6OuterClass.HomeViewV6
@Serializable
data class MPHomeViewV6(
    @ProtoNumber(1) val sections: List<MPHomeViewV6Sections>,
    @ProtoNumber(4) val userSubscription: MPUserSubscription,
)

// jp.co.comic.jump.proto.HomeViewV6OuterClass.HomeViewV6.Sections
@Serializable
data class MPHomeViewV6Sections(
    @ProtoOneOf val section: Section? = null,
) {
    @Serializable
    sealed interface Section {
        @Serializable
        data class WeeklySection(@ProtoNumber(1) val weeklySection: MPHomeViewV6WeeklySection) : Section

        @Serializable
        data class RankingSection(@ProtoNumber(2) val rankingSection: MPHomeViewV6RankingSection) : Section

        @Serializable
        data class TitleListSection(@ProtoNumber(4) val titleListSection: MPHomeViewV6TitleListSection) : Section
    }
}

// jp.co.comic.jump.proto.HomeViewV6OuterClass.HomeViewV6.WeeklySection
@Serializable
data class MPHomeViewV6WeeklySection(
    @ProtoNumber(1) val contents: List<MPHomeViewV6WeeklySectionContent>,
)

// jp.co.comic.jump.proto.HomeViewV6OuterClass.HomeViewV6.WeeklySection.WeeklyContent
@Serializable
data class MPHomeViewV6WeeklySectionContent(
    // @ProtoNumber(1) val isUpdated: Boolean,
    // @ProtoNumber(2) val updatedTimeStamp: Int,
    @ProtoNumber(3) val contentItems: List<MPHomeViewV6WeeklySectionContentItem>,
)

// jp.co.comic.jump.proto.HomeViewV6OuterClass.HomeViewV6.WeeklySection.ContentItem
@Serializable
data class MPHomeViewV6WeeklySectionContentItem(
    @ProtoOneOf val content: Content? = null,
) {
    @Serializable
    sealed interface Content {
        // PRBanner: 1

        @Serializable
        data class MVBanner(@ProtoNumber(2) val mvBanner: MPHomeViewV6WeeklySectionMVBanner) : Content

        @Serializable
        data class TitleGroup(@ProtoNumber(3) val titleGroup: MPHomeViewV6WeeklySectionTitleGroup) : Content

        // CarouselBanners: 4

        @Serializable
        data class MinorLanguageBanner(@ProtoNumber(5) val minorLanguageBanner: MPHomeViewV6WeeklySectionMinorLanguageBanner) : Content
    }
}

@Serializable
data class MPHomeViewV6WeeklySectionMVBanner(
    // @ProtoNumber(1) val imageUrl: String,
    @ProtoNumber(2) val titleGroups: MPOriginalTitleGroup,
)

// jp.co.comic.jump.proto.HomeViewV6OuterClass.HomeViewV6.WeeklySection.TitleGroup
@Serializable
data class MPHomeViewV6WeeklySectionTitleGroup(
    @ProtoNumber(1) val titleGroups: List<MPOriginalTitleGroup>,
)

// jp.co.comic.jump.proto.HomeViewV6OuterClass.HomeViewV6.WeeklySection.MinorLanguageBanner
@Serializable
data class MPHomeViewV6WeeklySectionMinorLanguageBanner(
    @ProtoNumber(1) val titleGroups: List<MPOriginalTitleGroup>,
)

// jp.co.comic.jump.proto.HomeViewV6OuterClass.HomeViewV6.RankingSection
@Serializable
data class MPHomeViewV6RankingSection(
    @ProtoNumber(1) val rankingTabs: List<MPHomeViewV6RankingSectionTab>,
)

// jp.co.comic.jump.proto.HomeViewV6OuterClass.HomeViewV6.RankingSection.RankingTab
@Serializable
data class MPHomeViewV6RankingSectionTab(
    // @ProtoNumber(1) val tabType: Int,
    @ProtoNumber(2) val rankedTitles: List<MPTitleRankingGroup>,
)

// jp.co.comic.jump.proto.TitleListGroup.TitleRankingGroup
@Serializable
data class MPTitleRankingGroup(
    @ProtoNumber(1) val originalTitleId: Int,
    @ProtoNumber(2) val titles: List<MPTitle>,
    @ProtoNumber(3) val score: Int,
)

// jp.co.comic.jump.proto.HomeViewV6OuterClass.HomeViewV6.TitleListSection
@Serializable
data class MPHomeViewV6TitleListSection(
    @ProtoNumber(1) val titleList: MPTitleList,
)
