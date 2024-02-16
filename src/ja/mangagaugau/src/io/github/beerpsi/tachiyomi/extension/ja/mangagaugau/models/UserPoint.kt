package io.github.beerpsi.tachiyomi.extension.ja.mangagaugau.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class UserPoint(
    @ProtoNumber(1) val free: Int = 0,
    @ProtoNumber(2) val event: Int = 0,
    @ProtoNumber(3) val paid: Int = 0,
    @ProtoNumber(4) val gauPotions: List<GauPotion>,
)

@Serializable
class GauPotion(
    @ProtoNumber(1) val point: Int = 0,
    @ProtoNumber(2) val count: Int = 0,
    @ProtoNumber(3) val expireTime: String? = null,
)
