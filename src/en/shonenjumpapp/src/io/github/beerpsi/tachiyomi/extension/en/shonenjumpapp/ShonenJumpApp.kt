package io.github.beerpsi.tachiyomi.extension.en.shonenjumpapp

import io.github.beerpsi.tachiyomi.multisrc.viz.Viz

@Suppress("MagicNumber")
class ShonenJumpApp : Viz("Shonen Jump (App)", "https://www.viz.com/shonenjump", "en") {

    override val androidAppPackageName = "com.viz.wsj.android"

    override val subscriptionInfoPrefix = ""

    override val vizAppId = 3
}
