package io.github.beerpsi.tachiyomi.extension.all.mangaplus.models

import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

private val COMPLETED_REGEX = "completado|complete|completo".toRegex()
private val HIATUS_REGEX = "on a hiatus".toRegex(RegexOption.IGNORE_CASE)
private val REEDITION_REGEX = "revival|remasterizada".toRegex()
private const val ONE_SECOND = 1000L
private const val CHAPTER_NUMBER_MINIMUM_LENGTH = 3

@Serializable
data class MPTitleDetailView(
    @ProtoNumber(1) val title: MPTitle,
    @ProtoNumber(2) val titleImageUrl: String,
    @ProtoNumber(3) val overview: String = "",
    @ProtoNumber(5) val nextTimeStamp: Int = 0,
    @ProtoNumber(7) val viewingPeriodDescription: String = "",
    @ProtoNumber(8) val nonAppearanceInfo: String = "",
    @ProtoNumber(14) val isSimulReleased: Boolean = false,
    @ProtoNumber(16) val rating: MPContentRating = MPContentRating.ALL_AGES,
    @ProtoNumber(17) val chaptersDescending: Boolean = true,
    @ProtoNumber(28) val chapterListGroup: List<MPChapterListGroup> = emptyList(),
    @ProtoNumber(32) val titleLabels: MPTitleLabels,
    @ProtoNumber(33) val userSubscription: MPUserSubscription,
    @ProtoNumber(34) val label: MPLabel? = MPLabel(MPLabelCode.WEEKLY_SHOUNEN_JUMP),
) {
    val chapterList: List<MPChapter> by lazy {
        chapterListGroup.flatMap { it.firstChapterList + it.midChapterList + it.lastChapterList }
    }

    private val isWebtoon: Boolean
        get() = chapterList.isNotEmpty() && chapterList.all { it.isVerticalOnly }

    private val isOneShot: Boolean
        get() {
            if (chapterList.size != 1) {
                return false
            }

            // XXX: Currently all MANGA Plus Creators awards are one-shot. Remove this condition
            // if there happens to be an award that is a series.
            if (label?.label == MPLabelCode.MANGA_PLUS_CREATORS) {
                return true
            }

            return chapterList.first().name.contains("one-shot", false)
        }

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
        val isReleasingNewChapters = !isReEdition && !isOneShot && !isCompleted

        if (isSimulpub && isReleasingNewChapters) {
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

        if (titleLabels.planType == "deluxe") {
            add("MANGA Plus MAX Deluxe")
        }
    }

    private val viewingDescription: String?
        get() = viewingPeriodDescription.takeIf { titleLabels.planType == "deluxe" }

    fun toSManga(intl: Intl) = title.toSManga().apply {
        description = "${overview}\n\n${viewingDescription.orEmpty()}".trim()
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
    @ProtoNumber(1) val releaseSchedule: MPReleaseSchedule = MPReleaseSchedule.DISABLED,
    @ProtoNumber(2) val isSimulpub: Boolean = false,
    @ProtoNumber(3) val planType: String = "basic",
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
    ALL_AGES,
    TEEN,
    TEEN_PLUS,
    MATURE,
}

@Serializable
data class MPChapterListGroup(
    @ProtoNumber(1) val chapterNumbers: String,
    @ProtoNumber(2) val firstChapterList: List<MPChapter> = emptyList(),
    @ProtoNumber(3) val midChapterList: List<MPChapter> = emptyList(),
    @ProtoNumber(4) val lastChapterList: List<MPChapter> = emptyList(),
)

@Serializable
data class MPChapter(
    @ProtoNumber(1) val titleId: Int,
    @ProtoNumber(2) val chapterId: Int,
    @ProtoNumber(3) val name: String,
    @ProtoNumber(4) val subTitle: String,
    @ProtoNumber(6) val startTimeStamp: Long,
    @ProtoNumber(9) val isVerticalOnly: Boolean = false,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "#/viewer/$chapterId"
        date_upload = startTimeStamp * ONE_SECOND
        chapter_number = chapterNumber
        name = if (chapter_number != -1F) {
            subTitle
        } else {
            "${this@MPChapter.name}: $subTitle"
        }
    }

    // M+ chapter titles have 5 types:
    // - #000: Normal chapter number
    // - #000-0: Chapter split into parts
    // - #000-000: Merged chapters (more than 2)
    // - #000,000: Merged chapters (list)
    // - ex: Extras
    //
    // For cases 1 and 2, we just parse them as funny numbers, i.e.
    // - #041 -> Chapter 41
    // - #041-1 -> Chapter 41.1
    //
    // For case 3 and 4, we use the first chapter as the chapter number, i.e.
    // - #001-004 -> Chapter 1
    // - #001,002 -> Chapter 1
    //
    // For extras, there's no concrete number, but ideally they should be numbered based on surrounding
    // chapters.
    private val chapterNumber: Float get() {
        if (!name.startsWith("#")) {
            return -1F
        }

        val numbers = name.removePrefix("#")

        return if (numbers.contains(",")) {
            numbers.split(",")[0].toFloat()
        } else if (numbers.contains("-")) {
            val parts = name.removePrefix("#").split("-", limit = 2)

            if (parts.size == 1 || parts[1].length >= CHAPTER_NUMBER_MINIMUM_LENGTH) {
                parts[0].toFloat()
            } else {
                "${parts[0]}.${parts[1]}".toFloat()
            }
        } else {
            numbers.toFloat()
        }
    }
}
