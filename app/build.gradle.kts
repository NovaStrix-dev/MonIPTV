import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.perso.moniptv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.perso.moniptv"
        minSdk = 26
        targetSdk = 34
        versionCode = 14
        versionName = "1.13"
    }

    // Clé de signature fixe : les mises à jour de l'APK s'installent par-dessus
    // l'ancienne version sans perdre la configuration ni les téléchargements.
    signingConfigs {
        create("perso") {
            storeFile = file("moniptv.jks")
            storePassword = "moniptv123"
            keyAlias = "moniptv"
            keyPassword = "moniptv123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("perso")
        }
        debug {
            signingConfig = signingConfigs.getByName("perso")
        }
    }

    // Nom du fichier APK généré : monIPTVperso.apk
    applicationVariants.all {
        outputs.all {
            (this as BaseVariantOutputImpl).outputFileName = "monIPTVperso.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Requis par le décodeur FFmpeg (media3-ffmpeg-decoder).
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    // Le lecteur passe par OkHttp (même résolution d'adresses adaptée aux VPN que le reste de l'appli).
    implementation("androidx.media3:media3-datasource-okhttp:1.3.1")
    // Décodeur audio logiciel FFmpeg (AC3, E-AC3, DTS, TrueHD…) : évite les vidéos sans son.
    // Doit rester sur la même version de Media3 que les lignes ci-dessus.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.3.1+2")

    implementation("androidx.security:security-crypto:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // DNS chiffré (Cloudflare / Google) quand le DNS du VPN bloque le serveur IPTV.
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
