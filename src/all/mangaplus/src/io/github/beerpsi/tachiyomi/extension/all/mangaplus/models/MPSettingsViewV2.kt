package io.github.beerpsi.tachiyomi.extension.all.mangaplus.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class MPSettingsViewV2(
    @ProtoNumber(6) val userSubscription: MPUserSubscription,
)
