import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Firma leida de keystore.properties (fuera del control de versiones).
// Si el fichero no existe, el release sale SIN firmar y puedes usar
// Build > Generate Signed App Bundle, que pide el keystore a mano.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}
val hasKeystore = keystorePropsFile.exists()

android {
    namespace = "padelpulseapp2.netlify.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "padelpulseapp2.netlify.app"
        minSdk = 30
        targetSdk = 36
        // IMPORTANTE: reloj y movil comparten applicationId, asi que Google Play
        // exige versionCode DISTINTO en cada uno. El del reloj va en su propia
        // serie -el del movil x10- y siempre por encima: movil 518, reloj 5180.
        versionCode = 5200
        versionName = "5.2.2"
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
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(libs.play.services.wearable)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    // Actividad en curso: obligatoria para pasar la revision de Wear OS
    implementation("androidx.wear:wear-ongoing:1.0.0")
    // QR de "Invita a un amigo": solo el codificador, sin camara ni vistas
    implementation("com.google.zxing:core:3.5.3")
}
