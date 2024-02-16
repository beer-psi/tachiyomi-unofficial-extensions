package io.github.beerpsi.tachiyomi.extension.ja.mangagaugau.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class MangaListView(
    @ProtoNumber(1) val titles: List<Manga>,
)
