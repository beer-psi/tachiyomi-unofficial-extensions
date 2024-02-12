package io.github.beerpsi.tachiyomi.extension.all.mangaplus.models

import kotlinx.serialization.Serializable

@Serializable
data class MPResponse<T>(
    val success: T? = null,
    val error: ErrorResult? = null,
)

@Serializable
data class ErrorResult(
    val action: ErrorAction = ErrorAction.DEFAULT,
    val englishPopup: ErrorPopup,
    val popups: List<ErrorPopup>,
)

@Serializable
data class ErrorPopup(
    val subject: String,
    val body: String,
    val language: MPLanguage = MPLanguage.ENGLISH,
)

@Serializable
enum class ErrorAction {
    DEFAULT,
    UNAUTHORIZED,
    MAINTENANCE,
    GEOIP_BLOCKING,
}
