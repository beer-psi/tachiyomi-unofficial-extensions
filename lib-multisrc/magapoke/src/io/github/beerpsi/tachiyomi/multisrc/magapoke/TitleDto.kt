package io.github.beerpsi.tachiyomi.multisrc.magapoke

import kotlinx.serialization.Serializable

@Serializable
data class TitleDto(
    val title_id: Int,
    val title_name: String,
    val banner_image_url: String,
    val thumbnail_image_url: String,
    val thumbnail_rect_image_url: String,
    val feature_image_url: String?,
    val author_text: String,
    val introduction_text: String,
    val short_introduction_text: String,
    val free_episode_update_cycle_text: String,
    val new_episode_update_cycle_text: String,
    val first_episode_id: Int,
    val magazine_category: Int,
    val publish_category: Int,
    val title_ticket_enabled: Int,
    val genre_id_list: List<Int>,
    val episode_free_updated: String?,
    val free_episode_count: Int,
    val latest_paid_episode_id: List<Int>,
    val latest_free_episode_id: Int?,
    val total_episode_count: Int,
    val episode_id_list: List<Int>,
    val author_list: List<String>,
)
