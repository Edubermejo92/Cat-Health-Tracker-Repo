import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firma leida de keystore.properties (fuera del control de versiones).
// DEBE ser el MISMO keystore que el del reloj: comparten applicationId
// y Google Play rechaza artefactos del mismo paquete firmados con claves distintas.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}
val hasKeystore = keystorePropsFile.exists()

android {
    namespace = "padelpulseapp2.netlify.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "padelpulseapp2.netlify.app"
        minSdk = 25
        targetSdk = 35
        // Serie propia del movil. Debe ser DISTINTO al del reloj (serie 1xxx).
        versionCode = 506
        versionName = "5.0.6"
    }

    signingConfigs {
        create("release") {
            if (hasKeystore) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        jvmToolchain(11)
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("androidx.webkit:webkit:1.12.1")
}

val copyLogos = tasks.register("copyLogos") {
    doLast {
        val sourceIcon = file("src/main/ic_launcher-playstore.png")
        val resDir = file("src/main/res")
        if (sourceIcon.exists()) {
            listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi").forEach { d ->
                val dir = resDir.resolve("mipmap-$d")
                dir.mkdirs()
                sourceIcon.copyTo(dir.resolve("ic_launcher.png"), overwrite = true)
                sourceIcon.copyTo(dir.resolve("ic_launcher_round.png"), overwrite = true)
            }
            resDir.resolve("drawable").mkdirs()
            sourceIcon.copyTo(resDir.resolve("drawable/logo.png"), overwrite = true)
        }
    }
}
tasks.named("preBuild") { dependsOn(copyLogos) }
