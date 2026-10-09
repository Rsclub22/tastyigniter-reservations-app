# TastyIgniter Reservierungen

Kotlin-Multiplatform-App (Android und Linux-Desktop) für das Restaurant-Team. Sie spricht direkt mit der
REST-API von TastyIgniter (Erweiterung `igniter.api`) und mit der Intern-API des Pakets `reservationcontrol`.
Funktionsumfang, Endpunkte und CI/CD stehen in `README.md`, die Verteilung in `docs/DISTRIBUTION.md`.

## Befehle

```bash
./gradlew :shared:desktopTest          # alle Unit-Tests (auch die in jvmCommonTest)
./gradlew :app:lintDebug               # Android Lint
./gradlew :app:assembleDebug           # Debug-APK
./gradlew :shared:run                  # Desktop-Fassung starten
./gradlew :shared:createDistributable  # Desktop-Programm mit mitgeliefertem JRE
```

Ein einzelner Test: `./gradlew :shared:desktopTest --tests '*InternApiTest'`.
CI führt `:app:lintDebug :shared:desktopTest` aus; beides muss vor dem Push grün sein.

## Aufbau

- `:shared` ist die KMP-Bibliothek mit beiden Zielen, `:app` nur die Android-Hülle (Manifest, `res/`, `MainActivity`).
  Grund: AGP 9 verbietet `com.android.application` und das Multiplatform-Plugin im selben Modul.
- Fast der gesamte Code liegt in `shared/src/jvmCommonMain` (nicht `commonMain`): er nutzt `java.time` und OkHttp.
  Dort **keine Android- oder Swing-Klassen**. Plattformspezifisches gehört per `expect`/`actual` nach
  `androidMain` bzw. `desktopMain`.
- Tests liegen in `jvmCommonTest` und laufen über das Desktop-Ziel. Das Android-Ziel hat keinen Test-Quellsatz.
- Versionen nur in `gradle/libs.versions.toml`. Die Kommentare dort erklären, warum etwas gepinnt ist
  (z. B. material-icons-extended auf 1.7.3); vor dem Anheben lesen.

## Konventionen

- Kommentare, Commit-Nachrichten und Texte in der App sind deutsch. Commits sind kurze Sätze im Indikativ
  oder Imperativ ohne Präfix, die das *Warum* nennen ("Reservierungen ohne Status nachladen, wenn der Server
  mit 5xx antwortet"). Umlaute in Commit-Betreffs weglassen (ae/ue/oe).
- Kommentare erklären Gründe und Fallen, nicht was der Code tut. Den vorhandenen Ton übernehmen.
- Neue Verhaltensänderung an API-Client, Mappern oder Validierung bekommt einen Test; die Tests laufen
  gegen `MockWebServer`, nicht gegen ein echtes System.

## Fallen

- **Mitgeliefertes JRE:** Manche JDKs (CachyOS) sind für x86-64-v4 gebaut. Ein damit geschnittenes JRE startet
  auf dem Tresenrechner (Kaby Lake, kein AVX-512) ohne Fenster und ohne Log. Für Desktop-Pakete, die dort laufen
  sollen, `JPACKAGE_JAVA_HOME` auf ein generisch gebautes JDK setzen.
- **jlink-Module:** Fehlt in `nativeDistributions.modules(...)` ein Modul, fällt das erst im gepackten
  Programm auf. `:shared:run` benutzt das volle JDK und verrät es nicht.
- **`versionCode`** kommt aus `github.run_number` und muss für Play streng steigen. Workflow `ci.yml` nicht
  umbenennen.
- **Grüne Tests beweisen nicht, dass es beim Gast ankommt.** Änderungen, die Server, Mails oder den
  Tresenrechner betreffen, am lebenden System prüfen (siehe Memory).
- Release-Signierung (`*.jks`, `keystore.properties`) und `local.properties` nie anfassen oder committen.

## Release

Nur auf ausdrückliche Anweisung: Skill `/release`. Ein `v*`-Tag löst Signierung, GitHub-Release und den
Upload in den internen Play-Test aus; nichts davon ist leicht zurückzunehmen.
