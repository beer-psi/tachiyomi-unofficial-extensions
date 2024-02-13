package io.github.beerpsi.tachiyomi.extension.all.mangaplus.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class MPLabel(
    @ProtoNumber(1) val label: MPLabelCode? = MPLabelCode.WEEKLY_SHOUNEN_JUMP,
) {
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
            MPLabelCode.MANGA_MEE -> "Manga Mee"
            else -> null
        }
}

@Serializable
enum class MPLabelCode {
    WEEKLY_SHOUNEN_JUMP,
    JUMP_SQUARE,
    V_JUMP,
    WEEKLY_YOUNG_JUMP,
    SHOUNEN_JUMP_PLUS,
    REVIVAL,
    MANGA_PLUS_CREATORS,
    MANGA_MEE,
    TONARI_NO_YOUNG_JUMP,
    OTHERS,
    SAIKYOU_JUMP,
    SHOUNEN_JUMP_GIGA,
}
