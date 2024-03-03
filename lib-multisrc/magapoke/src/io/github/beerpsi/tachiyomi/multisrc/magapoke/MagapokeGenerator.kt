package io.github.beerpsi.tachiyomi.multisrc.magapoke

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MagapokeGenerator : ThemeSourceGenerator {

    override val themePkg = "magapoke"

    override val themeClass = "Magapoke"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("マガポケ", "https://mgpk-api.magazinepocket.com", "ja", className = "ShonenMagazinePocket"),
        SingleLang("K Manga", "https://api.kmanga.kodansha.com", "en"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MagapokeGenerator().createAll()
        }
    }
}
