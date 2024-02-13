package io.github.beerpsi.tachiyomi.extension.all.mangaplus.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class MPRegistrationData(
    @ProtoNumber(1) val deviceSecret: String,
)
