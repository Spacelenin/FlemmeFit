import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.spacelenin.flemmefit.montre"
    compileSdk = 36

    defaultConfig {
        // Même identifiant que l'app du téléphone : la montre et le téléphone pourront se parler plus tard.
        applicationId = "com.spacelenin.flemmefit"
        minSdk = 30
        targetSdk = 34
        // Numéro propre à la montre (compteur du workflow « Montre »).
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "montre.${versionCode}"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation(libs.kotlinx.serialization.json)
    // Liaison Bluetooth avec le téléphone (Wearable Data Layer).
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    // play-services tire un vieux Fragment : la version récente pour les demandes de permission.
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    // Écran basse conso poignet baissé, et « activité en cours ».
    implementation("androidx.wear:wear:1.3.0")
    implementation("androidx.wear:wear-ongoing:1.0.0")
}

/*
 * Même signature que l'app du téléphone (rotation de l'ancienne clé vers la nouvelle, voir app/build.gradle.kts) :
 * indispensable pour que la montre et le téléphone se reconnaissent plus tard.
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
            // Le nom suit celui du projet : on prend l'APK qui est là.
            val apk = layout.buildDirectory.dir("outputs/apk/release").get().asFile.listFiles { f -> f.name.endsWith(".apk") }!!.single()
            val apksigner = File(android.sdkDirectory, "build-tools/${android.buildToolsVersion}/apksigner")
            val oldKs = file("../app/debug.keystore")
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
