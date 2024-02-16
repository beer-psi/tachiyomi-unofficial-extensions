package io.github.beerpsi.tachiyomi.extension.ja.mangagaugau.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class SearchView(
    @ProtoNumber(1) val words: List<String>,
    @ProtoNumber(2) val titles: List<Manga>,
)
