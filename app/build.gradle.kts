import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Load signing config from environment (CI) or local keystore.properties (local build)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.fdzaki.adshield"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fdzaki.adshield"
        minSdk = 24
        targetSdk = 34
        versionCode = 67
        versionName = "3.29.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            val ksPathEnv = System.getenv("RELEASE_KEYSTORE_PATH")
            storeFile = file(
                ksPathEnv ?: (keystoreProperties["storeFile"] as? String ?: "release.keystore")
            )
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                ?: (keystoreProperties["storePassword"] as? String ?: "")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                ?: (keystoreProperties["keyAlias"] as? String ?: "adshield")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
                ?: (keystoreProperties["keyPassword"] as? String ?: "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // required by com.wireguard.android:tunnel
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Encrypted storage for VPN profile secrets (private keys, passwords,
    // tokens) — v3.12.0, see VpnProfileRepository.kt / PROJECT_STATE.md.
    implementation("androidx.security:security-crypto:1.1.0")

    // Room (domain log storage)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager (periodic blocklist refresh, if user supplies a URL)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Official WireGuard tunnel engine (GoBackend) — used by the optional
    // full-tunnel "VPN Tunnel (WARP)" mode, kept fully separate from the
    // lightweight DNS-only ad-block engine (AdBlockVpnService).
    implementation("com.wireguard.android:tunnel:1.0.20230706")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Local JVM unit tests (app/src/test) — no Android framework/emulator
    // needed since DnsPacket and BlocklistManager's matching logic are
    // pure Kotlin/java.net, see PROJECT_STATE.md item #13.
    testImplementation("junit:junit:4.13.2")
}
