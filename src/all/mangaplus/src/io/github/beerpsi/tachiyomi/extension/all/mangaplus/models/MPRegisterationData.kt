package io.github.beerpsi.tachiyomi.extension.all.mangaplus.models

import kotlinx.serialization.Serializable

@Serializable
data class MPRegisterationData(
    val registerationData: MPRegisterationDataInner,
)

@Serializable
data class MPRegisterationDataInner(
    val deviceSecret: String,
)
