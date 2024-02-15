package io.github.beerpsi.tachiyomi.extension.all.mangaplus.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class MPResponse(
    @ProtoNumber(1) val success: MPSuccessResult? = null,
    @ProtoNumber(2) val error: MPErrorResult? = null,
)

@Serializable
data class MPSuccessResult(
    @ProtoNumber(2) val registrationData: MPRegistrationData? = null,
    @ProtoNumber(8) val titleDetailView: MPTitleDetailView? = null,
    @ProtoNumber(10) val mangaViewer: MPMangaViewer? = null,
    @ProtoNumber(24) val homeViewV3: MPHomeViewV3? = null,
    @ProtoNumber(26) val settingsViewV2: MPSettingsViewV2? = null,
    @ProtoNumber(35) val searchView: MPSearchView? = null,
)

@Serializable
data class MPErrorResult(
    @ProtoNumber(1) val action: MPErrorAction = MPErrorAction.DEFAULT,
    @ProtoNumber(2) val englishPopup: MPErrorPopup,
    @ProtoNumber(5) val popups: List<MPErrorPopup>,
)

@Serializable
data class MPErrorPopup(
    @ProtoNumber(1) val subject: String,
    @ProtoNumber(2) val body: String,
    @ProtoNumber(6) val language: MPLanguage = MPLanguage.ENGLISH,
)

@Serializable
enum class MPErrorAction {
    DEFAULT,
    UNAUTHORIZED,
    MAINTENANCE,
    GEOIP_BLOCKING,
}
