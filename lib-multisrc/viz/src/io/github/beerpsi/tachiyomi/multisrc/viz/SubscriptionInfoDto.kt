package io.github.beerpsi.tachiyomi.multisrc.viz

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionInfoDto(
    val is_auto_renew: String,
    val valid_from: String?,
    val valid_to: String?,
    val payment_type: String?,

    val vm_is_auto_renew: String,
    val vm_valid_from: String?,
    val vm_valid_to: String?,
    val vm_payment_type: String?,
)
