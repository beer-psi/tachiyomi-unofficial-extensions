package io.github.beerpsi.tachiyomi.multisrc.magapoke

import kotlinx.serialization.Serializable

@Serializable
data class EpisodeDto(
    val episode_id: Int,
    val episode_name: String,
    val index: Int,
    val is_viewed: Int,
    val is_viewed_last_page: Int,
    val thumbnail_image_url: String,
    val web_thumbnail_image_url: String,
    val badge: Int,
    val rental_finish_time: String?,
    val rental_rest_time: String?,
    val point: Int,
    val bonus_point: Int,
    val use_status: Int?,
    val featured_text: String,
    val first_page_image_url: String,
    val magazine_id: Int?,
    val magazine_name: String?,
    val title_id: Int,
    val viewing_direction: Int,
    val ticket_rental_enabled: Int,
    val comic_volume: Int?,
    val short_introduction_text: String,
    val view_bulk_buy: Int,
    val comment_num: Int,
    val start_time: String? = null,
)
