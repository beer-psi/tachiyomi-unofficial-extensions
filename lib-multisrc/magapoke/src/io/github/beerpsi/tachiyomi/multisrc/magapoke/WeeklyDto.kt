package io.github.beerpsi.tachiyomi.multisrc.magapoke

import kotlinx.serialization.Serializable

@Serializable
data class WeeklyDto(
    val title_id_list: List<Int>,
    val weekday_index: Int,
    val feature_title_id: Int,
    val bonus_point_title_id: List<Int>,
    val popular_title_id_list: List<Int>,
    val new_title_id_list: List<Int>,
)
