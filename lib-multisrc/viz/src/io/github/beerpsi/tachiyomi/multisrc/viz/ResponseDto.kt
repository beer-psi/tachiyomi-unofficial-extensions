package io.github.beerpsi.tachiyomi.multisrc.viz

import kotlinx.serialization.Serializable

@Serializable
data class OkDto(
    val ok: Int,
)

@Serializable
data class AuthResponseDto(
    val ok: Int,
    val archive_info: OkDto,
)

@Serializable
data class StoreResponseDto(
    val ok: Int,
    val data: List<VizDataObject>,
)

@Serializable
data class DataResponseDto(
    val ok: Int,
    val data: String,
)

@Serializable
data class MetadataResponseDto(
    val ok: Int,
    val metadata: String,
)

@Serializable
data class LoginResponseDto(
    val ok: Int,
    val login: String? = null,
    val user_id: Int? = null,
    val session_id: String? = null,
    val trust_user_id_token: String? = null,
    val trust_user_jwt: String? = null,
    val firebase_auth_jwt: String? = null,
)

@Serializable
data class EntitledResponseDto(
    val subscription_info: SubscriptionInfoDto,
)
