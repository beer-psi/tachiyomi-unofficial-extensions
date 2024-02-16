package io.github.beerpsi.tachiyomi.extension.ja.mangagaugau.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class MangaViewerView(
    @ProtoNumber(1) val status: MangaViewerStatus = MangaViewerStatus.SUCCESS,
    @ProtoNumber(4) val pages: List<MangaPage>,
)

enum class MangaViewerStatus {
    SUCCESS,
    CONTENT_NOT_FOUND,
    POINT_MISMATCH,
}

@Serializable
class MangaPage(
    @ProtoNumber(1) val image: Image? = null,
)

@Serializable
class Image(
    @ProtoNumber(1) val imageUrl: String,
)
