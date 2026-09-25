import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// Linux desktop client (Compose Multiplatform). Uses the same core as the Android app (:shared).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3.desktop)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// jpackage only accepts numeric versions (MAJOR.MINOR.PATCH), e.g. from the release tag.
val packageVersionName: String = (System.getenv("VERSION_NAME") ?: "1.0.0")
    .removePrefix("v").substringBefore('-')
    .takeIf { Regex("""\d+\.\d+\.\d+""").matches(it) } ?: "1.0.0"

compose.desktop {
    application {
        mainClass = "io.github.rsclub22.tireservations.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8")

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "ti-reservierungen"
            packageVersion = packageVersionName
            description = "Reservierungen für TastyIgniter verwalten"
            vendor = "Rsclub22"
            // Not detected automatically by jpackage: OkHttp/TLS needs the first four,
            // jdk.localedata provides German date names ("Freitag", "September").
            modules("java.logging", "java.naming", "jdk.crypto.ec", "jdk.unsupported", "jdk.localedata")
            linux {
                menuGroup = "Office"
                shortcut = true
                debMaintainer = "noreply@users.noreply.github.com"
                appCategory = "Office"
            }
        }
    }
}
