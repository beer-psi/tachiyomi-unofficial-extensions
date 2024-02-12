package io.github.beerpsi.tachiyomi.extension.all.mangaplus.models

import kotlinx.serialization.Serializable

@Serializable
data class MPSettingsViewV2(
    val settingsViewV2: MPSettingsViewV2Inner,
)

@Serializable
class MPSettingsViewV2Inner(
    val userSubscription: MPUserSubscription,
)
