package io.github.beerpsi.tachiyomi.extension.all.mangaplus.models

import kotlinx.serialization.Serializable

@Serializable
data class MPUserSubscription(
    val planType: String = "basic",
)
