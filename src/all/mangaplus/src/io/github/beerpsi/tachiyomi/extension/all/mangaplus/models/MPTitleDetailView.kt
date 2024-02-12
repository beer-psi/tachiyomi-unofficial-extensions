package io.github.beerpsi.tachiyomi.extension.all.mangaplus.models

import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val COMPLETED_REGEX = "completado|complete|completo".toRegex()
private val HIATUS_REGEX = "on a hiatus".toRegex(RegexOption.IGNORE_CASE)
private val REEDITION_REGEX = "revival|remasterizada".toRegex()

@Serializable
data class MPTitleDetailView(
    val titleDetailView: MPTitleDetailViewInner,
)

@Serializable
data class MPTitleDetailViewInner(
    val title: MPTitle,
    val titleImageUrl: String,
    val overview: String? = null,
    val nextTimeStamp: Int = 0,
    val viewingPeriodDescription: String = "",
    val nonAppearanceInfo: String = "",
    val chapterListGroup: List<MPChapterListGroup> = emptyList(),
    val isSimulReleased: Boolean = false,
    val chaptersDescending: Boolean = true,
    val titleLabels: MPTitleLabels,
    val userSubscription: MPUserSubscription,
    val rating: MPContentRating = MPContentRating.ALL_AGES,
    val label: MPLabel? = MPLabel(MPLabelCode.WEEKLY_SHOUNEN_JUMP),
) {
    val chapterList: List<MPChapter> by lazy {
        chapterListGroup.flatMap { it.firstChapterList + it.midChapterList + it.lastChapterList }
    }

    private val isWebtoon: Boolean
        get() = chapterList.isNotEmpty() && chapterList.all { it.isVerticalOnly }

    private val isOneShot: Boolean
        get() = chapterList.size == 1 && chapterList.firstOrNull()
            ?.name?.equals("one-shot", true) == true

    private val isReEdition: Boolean
        get() = viewingPeriodDescription.contains(REEDITION_REGEX)

    private val isCompleted: Boolean
        get() = nonAppearanceInfo.contains(COMPLETED_REGEX) || isOneShot ||
            titleLabels.releaseSchedule == MPReleaseSchedule.COMPLETED ||
            titleLabels.releaseSchedule == MPReleaseSchedule.DISABLED

    private val isSimulpub: Boolean
        get() = isSimulReleased || titleLabels.isSimulpub

    private val isOnHiatus: Boolean
        get() = nonAppearanceInfo.contains(HIATUS_REGEX)

    private fun createGenres(intl: Intl): List<String> = buildList {
        if (isSimulpub && !isReEdition && !isOneShot && !isCompleted) {
            add("Simulrelease")
        }

        if (isOneShot) {
            add("One-shot")
        }

        if (isReEdition) {
            add("Re-edition")
        }

        if (isWebtoon) {
            add("Webtoon")
        }

        if (label?.magazine != null) {
            add(intl.format("serialization", label.magazine))
        }

        if (!isCompleted) {
            val scheduleLabel = intl["schedule_" + titleLabels.releaseSchedule.toString().lowercase()]
            add(intl.format("schedule", scheduleLabel))
        }

        val ratingLabel = intl["rating_" + rating.toString().lowercase()]
        add(intl.format("rating", ratingLabel))
    }

    fun toSManga(intl: Intl) = title.toSManga().apply {
        description = "${overview.orEmpty()}\n\n${viewingPeriodDescription.takeIf { !isCompleted }.orEmpty()}".trim()
        genre = createGenres(intl).joinToString()
        status = when {
            isCompleted -> SManga.COMPLETED
            isOnHiatus -> SManga.ON_HIATUS
            else -> SManga.ONGOING
        }
    }
}

@Serializable
data class MPTitleLabels(
    val releaseSchedule: MPReleaseSchedule = MPReleaseSchedule.DISABLED,
    val isSimulpub: Boolean = false,
    val planType: String = "basic",
)

@Serializable
enum class MPReleaseSchedule {
    DISABLED,
    EVERYDAY,
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    BIMONTHLY,
    TRIMONTHLY,
    OTHER,
    COMPLETED,
}

@Serializable
enum class MPContentRating {
    @SerialName("ALLAGE")
    ALL_AGES,
    TEEN,

    @SerialName("TEENPLUS")
    TEEN_PLUS,
    MATURE,
}

@Serializable
data class MPLabel(val label: MPLabelCode? = MPLabelCode.WEEKLY_SHOUNEN_JUMP) {
    val magazine: String?
        get() = when (label) {
            MPLabelCode.WEEKLY_SHOUNEN_JUMP -> "Weekly Shounen Jump"
            MPLabelCode.JUMP_SQUARE -> "Jump SQ."
            MPLabelCode.V_JUMP -> "V Jump"
            MPLabelCode.SHOUNEN_JUMP_GIGA -> "Shounen Jump GIGA"
            MPLabelCode.WEEKLY_YOUNG_JUMP -> "Weekly Young Jump"
            MPLabelCode.TONARI_NO_YOUNG_JUMP -> "Tonari no Young Jump"
            MPLabelCode.SHOUNEN_JUMP_PLUS -> "Shounen Jump+"
            MPLabelCode.MANGA_PLUS_CREATORS -> "MANGA Plus Creators"
            MPLabelCode.SAIKYOU_JUMP -> "Saikyou Jump"
            else -> null
        }
}

@Serializable
enum class MPLabelCode {
    @SerialName("CREATORS")
    MANGA_PLUS_CREATORS,

    @SerialName("GIGA")
    SHOUNEN_JUMP_GIGA,

    @SerialName("J_PLUS")
    SHOUNEN_JUMP_PLUS,

    OTHERS,

    REVIVAL,

    @SerialName("SKJ")
    SAIKYOU_JUMP,

    @SerialName("SQ")
    JUMP_SQUARE,

    @SerialName("TYJ")
    TONARI_NO_YOUNG_JUMP,

    @SerialName("VJ")
    V_JUMP,

    @SerialName("YJ")
    WEEKLY_YOUNG_JUMP,

    @SerialName("WSJ")
    WEEKLY_SHOUNEN_JUMP,
}

@Serializable
data class MPChapterListGroup(
    val chapterNumbers: String,
    val firstChapterList: List<MPChapter> = emptyList(),
    val midChapterList: List<MPChapter> = emptyList(),
    val lastChapterList: List<MPChapter> = emptyList(),
)

@Serializable
data class MPChapter(
    val titleId: Int,
    val chapterId: Int,
    val name: String,
    val subTitle: String,
    val startTimeStamp: Long,
    val isVerticalOnly: Boolean = false,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "#/viewer/$chapterId"
        date_upload = startTimeStamp * 1000L

        val maybeChapterNumber = this@MPChapter.name.removePrefix("#").toFloatOrNull()

        if (maybeChapterNumber != null) {
            chapter_number = maybeChapterNumber
            name = subTitle
        } else {
            name = "${this@MPChapter.name}: $subTitle"
        }
    }
}
