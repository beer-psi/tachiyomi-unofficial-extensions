package io.github.beerpsi.tachiyomi.multisrc.magapoke

import kotlinx.serialization.Serializable

@Serializable
data class MangapokeResponse(
    val status: String,
    val response_code: Int,
    val error_message: String,
)

@Serializable
data class RegisterResponse(
    val status: String,
    val response_code: Int,
    val error_message: String,
    val user_id: Int,
    val hash_key: String,
    val login_status: Int,
    val email: String?,
    val charge_type: Int,
    val gender: String?,
    val push_notification_notice: Int,
    val original_uuid: String,
)

@Serializable
data class RankingResponse(
    val status: String,
    val response_code: Int,
    val error_message: String,

    val tab_list: List<TabDto>,
    val ranking_title_list: List<IdDto>,
)

@Serializable
data class TitleListResponse(
    val status: String,
    val response_code: Int,
    val error_message: String,

    val title_list: List<TitleDto>,
)

@Serializable
data class WeeklyTitleResponse(
    val status: String,
    val response_code: Int,
    val error_message: String,

    val today_weekday_index: Int,
    val weekly_list: List<WeeklyDto>,
)

@Serializable
data class EpisodeListResponse(
    val status: String,
    val response_code: Int,
    val error_message: String,

    val episode_list: List<EpisodeDto>,
)

@Serializable
data class EpisodeViewerResponse(
    val status: String,
    val response_code: Int,
    val error_message: String,

    val page_list: List<PageDto>,
)

@Serializable
data class GenreListResponse(
    val status: String,
    val response_code: Int,
    val error_message: String,

    val genre_list: List<GenreDto>,
)
