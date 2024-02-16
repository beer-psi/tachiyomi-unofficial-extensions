package io.github.beerpsi.tachiyomi.extension.ja.mangagaugau.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class HomeView(
    @ProtoNumber(4) val updatedTitles: List<Manga>,
    @ProtoNumber(6) val rankings: List<Ranking>,
)

@Serializable
class Ranking(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val titles: List<Manga>,
)
