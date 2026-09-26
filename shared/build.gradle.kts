// Die compose.*-Kuerzel sind in CMP 1.12 abgekuendigt ("specify dependency
// directly"). Sie bleiben hier trotzdem: sie tragen die Zuordnung Bibliothek ->
// Version, die JetBrains pflegt, und die ist nicht einheitlich - zu CMP 1.12.1
// gehoert Material3 1.12.0-alpha03. Von Hand eingetragen wuerde diese Alpha
// festgenagelt und bei jedem CMP-Wechsel stillschweigend falsch werden.
@file:Suppress("DEPRECATION")

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

val appVersion: String = System.getenv("VERSION_NAME") ?: "1.0.0"

kotlin {
    androidLibrary {
        namespace = "io.github.rsclub22.tireservations.shared"
        compileSdk = 37
        minSdk = 26
    }

    jvm("desktop")

    // jvmTarget laesst sich im Multiplatform-Modul nicht zentral setzen; die
    // Toolchain gilt fuer alle JVM-Ziele. 17, wie die CI (temurin 17) und :app.
    jvmToolchain(17)

    sourceSets {
        // Beide Ziele sind JVM. Der geteilte Code darf daher java.time und OkHttp
        // benutzen - in commonMain waere beides verboten, und eine Umstellung auf
        // kotlinx-datetime und Ktor waere ein Umbau von ~32 Fundstellen ohne
        // Gegenwert. Kommt spaeter ein Nicht-JVM-Ziel dazu (iOS, wasm), ist genau
        // dieses Source-Set die Liste dessen, was dann umzuziehen ist.
        val jvmCommonMain = create("jvmCommonMain") { dependsOn(commonMain.get()) }
        val jvmCommonTest = create("jvmCommonTest") { dependsOn(commonTest.get()) }

        androidMain.get().dependsOn(jvmCommonMain)
        getByName("desktopMain").dependsOn(jvmCommonMain)
        getByName("desktopTest").dependsOn(jvmCommonTest)

        jvmCommonMain.dependencies {
            // api, nicht implementation: :app baut damit seine Oberflaeche
            // (MainActivity) und braucht dieselben Compose-Typen.
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            implementation(libs.compose.material.icons.extended)

            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.navigation.compose)

            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.okio)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okhttp)
            // AppGraph baut den Client fuer beide Ziele, inklusive Log-Interceptor
            // im Debug-Build - gehoert daher hierher, nicht je Plattform.
            implementation(libs.okhttp.logging)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            // NotificationCompat fuer die Meldungen
            implementation(libs.androidx.core.ktx)
            // Nachsehen, auch wenn die App zu ist
            implementation(libs.androidx.work.runtime.ktx)
        }

        getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
            // Dispatchers.Main auf dem Desktop - viewModelScope laeuft darauf.
            implementation(libs.kotlinx.coroutines.swing)
        }

        jvmCommonTest.dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okhttp.mockwebserver)
        }

        getByName("desktopTest").dependencies {
            // Nur fuer den Fingerschub-Test: der braucht eine echte Compose-Szene,
            // in die sich ein Mausereignis mit Zeigertyp "Maus" schicken laesst.
            // Die Zuordnung Bibliothek -> Version kommt wieder von JetBrains, s.
            // Dateikopf. Laeuft ohne Bildschirm - Skia zeichnet in einen Puffer.
            implementation(compose.desktop.uiTestJUnit4)
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.github.rsclub22.tireservations.MainKt"

        // Aus diesem JDK schneidet jlink das mitgelieferte JRE. Damit entscheidet
        // es auch, welche CPU das Programm spaeter braucht - und manche
        // Distributionen bauen ihr JDK fuer x86-64-v4 (CachyOS etwa, also der
        // Entwicklungsrechner). Das Ergebnis laeuft dann nur auf Rechnern mit
        // AVX-512. Der Tresenrechner ist ein Kaby Lake ohne AVX-512 und bricht
        // mit "CPU ISA level is lower than required" ab, noch bevor die App
        // startet - kein Fenster, keine Meldung im Log.
        //
        // JPACKAGE_JAVA_HOME zeigt darum auf ein generisch gebautes JDK. Die CI
        // braucht es nicht (temurin 17 ist ohnehin baseline); ohne die Variable
        // bleibt alles wie vorher.
        System.getenv("JPACKAGE_JAVA_HOME")?.takeIf { it.isNotBlank() }?.let { javaHome = it }

        // Die Version soll nur an einer Stelle stehen. Main.kt liest sie als
        // System-Property; ohne das Argument (Start aus der IDE) meldet sie "dev"
        // und schaltet damit die Update-Pruefung aus.
        jvmArgs += "-Dapp.version=$appVersion"

        nativeDistributions {
            // jpackage baut nur fuer das System, auf dem es laeuft - deshalb die
            // Matrix in der CI. Compose Multiplatform kennt Deb/Rpm/Msi/Exe/Dmg/Pkg,
            // kein AppImage.
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "tastyigniter-reservierungen"
            packageVersion = appVersion
            description = "Tischreservierungen fuer TastyIgniter"
            vendor = "Rsclub22"

            // jpackage baut per jlink ein minimiertes JRE und nimmt nur Module mit,
            // die es selbst erkennt. Was hier fehlt, faellt erst im gepackten
            // Programm auf - der Gradle-Task "run" benutzt das volle lokale JDK und
            // laeuft auch ohne.
            modules(
                // sun.misc.Unsafe, von Compose und den Coroutines benutzt. Ohne das
                // Modul stirbt der AWT-Event-Thread beim ersten Frame.
                "jdk.unsupported",
                // TLS mit EC-Zertifikaten. Fehlt es, schlaegt jede HTTPS-Anfrage an
                // den Server fehl - und zwar erst zur Laufzeit.
                "jdk.crypto.ec",
                // Ohne die Sprachdaten kennt das minimierte JRE nur die
                // Wurzel-Locale: aus "So" wird "SUN" und aus "Sonntag" "Sunday",
                // obwohl im Code ueberall Locale.GERMAN steht.
                "jdk.localedata",
                "java.instrument",
                "java.naming",
                "java.sql",
            )

            linux {
                // jpackage leitet den Menueeintrag sonst aus packageName ab.
                menuGroup = "Office"
            }
        }
    }
}
