package io.github.beerpsi.tachiyomi.extension.all.mangaplus.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class MPMangaViewer(
    @ProtoNumber(1) val pages: List<MPPage>,
    @ProtoNumber(9) val titleId: Int,
)

@Serializable
data class MPPage(
    @ProtoNumber(1) val mangaPage: MangaPage? = null,
)

@Serializable
data class MangaPage(
    @ProtoNumber(1) val imageUrl: String,
    @ProtoNumber(2) val width: Int,
    @ProtoNumber(3) val height: Int,
    @ProtoNumber(4) val type: PageType = PageType.SINGLE,
    @ProtoNumber(5) val encryptionKey: String? = null,
)

@Serializable
enum class PageType {
    SINGLE,
    LEFT,
    RIGHT,
    DOUBLE,
}
