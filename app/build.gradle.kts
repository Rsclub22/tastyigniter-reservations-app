plugins {
    // AGP 9 bringt die Kotlin-Unterstuetzung selbst mit; org.jetbrains.kotlin.android
    // wird dadurch abgelehnt.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is configured through environment variables (set by CI from
// repository secrets). Without them the release build falls back to debug signing.
val releaseKeystore: String? = System.getenv("ANDROID_KEYSTORE_FILE")

android {
    namespace = "io.github.rsclub22.tireservations"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.rsclub22.tireservations"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"

        // GitHub repository whose releases the app checks for updates ("" disables the check,
        // e.g. for a Play Store only build). Play Store installs never check GitHub.
        buildConfigField("String", "UPDATE_REPO", "\"Rsclub22/tastyigniter-reservations-app\"")
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = false
        disable += listOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

dependencies {
    // Traegt den gesamten Code; :app ist nur die Anwendungshuelle darum.
    // Compose kommt von dort als api-Abhaengigkeit mit.
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
