import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

base {
    archivesName.set("fidget")
}

val releaseKeystorePropertiesFile = rootProject.file("keystore.properties")
val releaseKeystoreProperties = Properties().apply {
    if (releaseKeystorePropertiesFile.exists()) {
        releaseKeystorePropertiesFile.inputStream().use(::load)
    }
}
val hasReleaseSigning = listOf(
    "storeFile",
    "storePassword",
    "keyAlias",
    "keyPassword",
).all(releaseKeystoreProperties::containsKey)

val releaseTaskRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("Release", ignoreCase = true)
}
if (releaseTaskRequested && !hasReleaseSigning) {
    throw GradleException(
        "Release signing is not configured. Add the ignored keystore.properties file before running release tasks.",
    )
}

android {
    namespace = "bpm.munkz.pulse_wear.os.bpm"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.6"
    }

    flavorDimensions += "device"
    productFlavors {
        create("fidgettoy") {
            dimension = "device"
            applicationId = "bpm.munkz.pulse_wear.os.fidgettoy"
            versionCode = 16
            versionName = "1.6.2"
            buildConfigField("String", "APP_EDITION", "\"fidgettoy\"")
        }
        create("fidgetphone") {
            dimension = "device"
            minSdk = 31
            applicationId = "bpm.munkz.pulse_wear.os.fidgettoy"
            versionCode = 17
            versionName = "1.6.2"
            buildConfigField("String", "APP_EDITION", "\"fidgetphone\"")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseKeystoreProperties["storeFile"] as String)
                storePassword = releaseKeystoreProperties["storePassword"] as String
                keyAlias = releaseKeystoreProperties["keyAlias"] as String
                keyPassword = releaseKeystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildToolsVersion = "36.0.0"
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.play.services.wearable)
    implementation(libs.play.billing)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation("androidx.compose.foundation:foundation")
    implementation(libs.compose.material3)

    add("fidgetphoneImplementation", libs.core.splashscreen)
    add("fidgettoyImplementation", libs.wear.watchface.complications.data.source)

    debugImplementation(libs.ui.tooling)
    testImplementation("junit:junit:4.13.2")
}

tasks.register("verifyFidgetReleasePackages") {
    group = "verification"
    description = "Builds and rejects Fidget release bundles containing legacy BPM product material."
    dependsOn("bundleFidgettoyRelease", "bundleFidgetphoneRelease")

    doLast {
        val bundles = linkedMapOf(
            "Wear" to layout.buildDirectory.file(
                "outputs/bundle/fidgettoyRelease/fidget-fidgettoy-release.aab",
            ).get().asFile,
            "Phone" to layout.buildDirectory.file(
                "outputs/bundle/fidgetphoneRelease/fidget-fidgetphone-release.aab",
            ).get().asFile,
        )
        val forbiddenEntryFragments = listOf(
            "bpm_munkz",
            "metronome",
            "tuner",
            "playlist",
            "rhythm",
            "hear_no_evil",
            "hearnoevil",
            "clock_dial",
            "tile_preview",
            "spectrum",
            "fft_lab",
        )
        val sourceOrScriptExtensions = listOf(
            ".kt",
            ".java",
            ".gradle",
            ".kts",
            ".ps1",
            ".py",
            ".sh",
            ".bat",
            ".cmd",
        )

        bundles.forEach { (device, bundle) ->
            check(bundle.isFile) { "$device release bundle was not produced: $bundle" }
            val entries = ZipFile(bundle).use { zip ->
                zip.entries().asSequence().map { it.name }.toList()
            }
            val lowerEntries = entries.map(String::lowercase)
            val embeddedModules = entries.filter { it.endsWith("/manifest/AndroidManifest.xml") }
            check(embeddedModules == listOf("base/manifest/AndroidManifest.xml")) {
                "$device bundle contains unexpected modules: $embeddedModules"
            }
            check(lowerEntries.none { it.startsWith("base/assets/") }) {
                "$device bundle unexpectedly contains packaged assets."
            }
            val forbiddenEntries = lowerEntries.filter { entry ->
                forbiddenEntryFragments.any(entry::contains)
            }
            check(forbiddenEntries.isEmpty()) {
                "$device bundle contains legacy BPM resource names: $forbiddenEntries"
            }
            val embeddedSources = lowerEntries.filter { entry ->
                sourceOrScriptExtensions.any(entry::endsWith)
            }
            check(embeddedSources.isEmpty()) {
                "$device bundle contains source or script files: $embeddedSources"
            }
            check(bundle.length() < 10L * 1024L * 1024L) {
                "$device bundle is unexpectedly large (${bundle.length()} bytes)."
            }
            logger.lifecycle(
                "$device Fidget bundle clean: ${entries.size} entries, ${bundle.length()} bytes",
            )
        }

        val forbiddenMappingSymbols = listOf(
            "MetronomeService",
            "MetronomeState",
            "PhoneMetronomeApp",
            "TunerPage",
            "TunerAudioAnalyzer",
            "TunerModels",
            "PhoneTunerApp",
            "PlaylistPage",
            "PlaylistModels",
            "PhonePlaylistApp",
            "RhythmPage",
            "RhythmModels",
            "PhoneRhythmApp",
            "PhoneProApp",
            "CascadingFftPage",
            "FftLabPage",
            "SpectrumAnalyzerPage",
            "BpmMunkzTile",
            "LatestMusic",
            "HearNoEvil",
        )
        val releaseVariants = listOf(
            Triple(
                "fidgettoyRelease",
                "processFidgettoyReleaseManifest",
                "Wear",
            ),
            Triple(
                "fidgetphoneRelease",
                "processFidgetphoneReleaseManifest",
                "Phone",
            ),
        )
        releaseVariants.forEach { (variant, manifestTask, device) ->
            val mapping = layout.buildDirectory.file(
                "outputs/mapping/$variant/mapping.txt",
            ).get().asFile
            check(mapping.isFile) { "$device R8 mapping is missing: $mapping" }
            val mappingText = mapping.readText()
            val mappingHits = forbiddenMappingSymbols.filter(mappingText::contains)
            check(mappingHits.isEmpty()) {
                "$device bundle includes legacy BPM classes: $mappingHits"
            }

            val manifest = layout.buildDirectory.file(
                "intermediates/merged_manifests/$variant/$manifestTask/AndroidManifest.xml",
            ).get().asFile
            check(manifest.isFile) { "$device merged manifest is missing: $manifest" }
            val manifestText = manifest.readText()
            check("bpm.munkz.pulse_wear.os.fidgettoy" in manifestText) {
                "$device manifest has the wrong Play application ID."
            }
            val forbiddenManifestFragments = listOf(
                "android.permission.RECORD_AUDIO",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.WAKE_LOCK",
                "MetronomeService",
                "BpmMunkzTile",
                "LatestMusic",
                "Tuner",
                "Playlist",
                "HearNoEvil",
            )
            val manifestHits = forbiddenManifestFragments.filter(manifestText::contains)
            check(manifestHits.isEmpty()) {
                "$device manifest contains legacy BPM components or permissions: $manifestHits"
            }
        }
    }
}
