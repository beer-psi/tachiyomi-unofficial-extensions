import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("com.android.library")
    kotlin("android")
    id("kotlinx-serialization")
    id("io.gitlab.arturbosch.detekt")
}

android {
    compileSdk = AndroidConfig.compileSdk

    defaultConfig {
        minSdk = AndroidConfig.minSdk
    }

    namespace = "eu.kanade.tachiyomi.multisrc.${project.name}"

    sourceSets {
        named("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.setSrcDirs(listOf("src"))
            res.setSrcDirs(listOf("res"))
            assets.setSrcDirs(listOf("assets"))
        }
    }

    buildFeatures {
        resValues = false
        shaders = false
    }

    kotlinOptions {
        freeCompilerArgs += "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
    }
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    autoCorrect = false
    ignoreFailures = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

tasks.withType<Detekt>().configureEach {
    include("**/*.kt")
    exclude("**/resources/**", "**/build/**", "**/generated/**", "**/*.kts")
    reports {
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
    }
}

repositories {
    mavenCentral()
}

// TODO: use versionCatalogs.named("libs") in Gradle 8.5
val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies {
    compileOnly(libs.findBundle("common").get())
}

tasks {
    preBuild {
        dependsOn(detekt)
    }
}
