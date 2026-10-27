import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.spacelenin.flemmefit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.spacelenin.flemmefit"
        minSdk = 26
        targetSdk = 35
        // En CI, chaque build a un numéro croissant : les APK s'installent par-dessus sans perdre les données.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0.${versionCode}"
    }

    signingConfigs {
        // Clé commitée volontairement (app perso, non publiée sur le Play Store) : la signature reste
        // identique d'un build à l'autre, donc les mises à jour s'installent sans désinstaller.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    sourceSets {
        getByName("main").assets.srcDir("../examples")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}

/*
 * Rotation de clé (schéma de signature APK v3). L'ancienne clé, publique car commitée, passe la main à une
 * nouvelle clé gardée dans les secrets GitHub : Android 9+ accepte la mise à jour sans réinstaller, puis
 * ne fait plus confiance qu'à la nouvelle clé (retour à l'ancienne refusé). Les versions plus anciennes
 * d'Android vérifient toujours l'ancienne signature (v1/v2). Sans les secrets (build local), rien ne change.
 */
val newKeystore: String? = System.getenv("FLEMMEFIT_KEYSTORE_B64")?.takeIf { it.isNotBlank() }
if (newKeystore != null) {
    val signWithNewKey = tasks.register("signWithNewKey") {
        doLast {
            val work = layout.buildDirectory.dir("rotation").get().asFile.apply { mkdirs() }
            val newKs = File(work, "new.keystore").apply {
                writeBytes(Base64.getMimeDecoder().decode(newKeystore))
            }
            val lineage = File(work, "lineage.bin")
            val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
            val apksigner = File(android.sdkDirectory, "build-tools/${android.buildToolsVersion}/apksigner")
            val oldKs = file("debug.keystore")
            val oldSigner = listOf("--ks", oldKs.path, "--ks-pass", "pass:android", "--ks-key-alias", "androiddebugkey", "--key-pass", "pass:android")
            val newSigner = listOf(
                "--ks", newKs.path, "--ks-pass", "env:FLEMMEFIT_KEYSTORE_PASSWORD",
                "--ks-key-alias", System.getenv("FLEMMEFIT_KEY_ALIAS") ?: "flemmefit",
                "--key-pass", "env:FLEMMEFIT_KEYSTORE_PASSWORD",
            )

            fun run(vararg args: String) {
                val process = ProcessBuilder(listOf(apksigner.path) + args).redirectErrorStream(true)
                    .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null"))).start()
                val output = process.inputStream.bufferedReader().readText()
                logger.lifecycle(output)
                check(process.waitFor() == 0) { "apksigner ${args.first()} a échoué :\n$output" }
            }
            lineage.delete()
            run(*(listOf("rotate", "--out", lineage.path, "--old-signer") + oldSigner + listOf("--set-rollback", "false", "--new-signer") + newSigner).toTypedArray())
            run(*(listOf("sign") + oldSigner + listOf("--next-signer") + newSigner + listOf("--lineage", lineage.path, "--rotation-min-sdk-version", "28", apk.path)).toTypedArray())
            run("verify", "--print-certs", "--verbose", apk.path)
            newKs.delete()
        }
    }
    tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(signWithNewKey) }
}
