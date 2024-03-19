package io.github.beerpsi.tachiyomi.extension.all.mangaplus.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
enum class MPLanguage(val lang: String, val internalLang: String) {
    ENGLISH("en", "eng"),
    SPANISH("es", "esp"),
    FRENCH("fr", "fra"),
    INDONESIAN("id", "ind"),
    PORTUGUESE_BR("pt-BR", "ptb"),
    RUSSIAN("ru", "rus"),
    THAI("th", "tha"),
    GERMAN("de", "deu"),

    @ProtoNumber(9)
    VIETNAMESE("vi", "vie"),
}
