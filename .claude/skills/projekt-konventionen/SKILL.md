---
name: projekt-konventionen
description: Hintergrundwissen zu Source-Sets, Plattformgrenzen, Tests und Stil dieses KMP-Projekts; beim Schreiben oder Ändern von Code unter shared/ und app/ anwenden
user-invocable: false
---

## Wohin gehört neuer Code?

| Art | Ort |
| --- | --- |
| API-Client, Mapper, Datenmodelle, ViewModels, Compose-UI | `shared/src/jvmCommonMain/kotlin/io/github/rsclub22/tireservations/` (`data/`, `ui/`) |
| Android-spezifisch (Hintergrund-Nachsehen mit WorkManager, Play-Store-Erkennung) | `shared/src/androidMain` (`hintergrund/`, `data/`); Plattformschnittstellen als `actual` zu `platform/Platform.kt` |
| Desktop-spezifisch (Druck, Selbsterneuerung, `main()`) | `shared/src/desktopMain`, ebenfalls als `actual` |
| Manifest, Ressourcen, `MainActivity`, `ReservationsApp` | `app/src/main` |
| Tests | `shared/src/jvmCommonTest` (plattformneutral) oder `desktopTest` (braucht Desktop-Klassen oder Compose-Testszene) |

`jvmCommonMain` darf `java.time` und OkHttp nutzen, aber weder `android.*` noch `java.awt`/`javax.swing`.
Wer das braucht, legt ein `expect` in `platform/` an und implementiert es je Ziel.

## Tests schreiben

- JUnit 4. HTTP wird mit `MockWebServer` nachgestellt, Vorbild: `TastyIgniterApiTest`, `InternApiTest`.
- Coroutinen mit `kotlinx-coroutines-test`.
- Ausführen: `./gradlew :shared:desktopTest --tests '*Klassenname'`.
- Regeln der Server-Seite (Pflichtfelder der beiden Request-Klassen) prüft man schreibfrei, ohne am lebenden
  Server etwas anzulegen; siehe Memory "API-Pflichtfelder".

## Stil

- Deutsch in Kommentaren, Texten und Commits. Commit-Betreffe ohne Umlaute.
- Kommentare nur dort, wo ein Grund oder eine Falle steht; Ton und Dichte der Nachbardateien übernehmen.
- Neue Bibliotheken nur über `gradle/libs.versions.toml`. Gepinnte Versionen tragen dort einen Kommentar
  mit dem Grund; nicht ohne Prüfung anheben.
- Fehler vom Server dem Nutzer verständlich am Feld oder über der Liste zeigen, nicht verschlucken
  (Vorbild: Status-Fallback bei 5xx, `docs/status-fallback-report.md`).

## Vor dem Abschluss

`./gradlew :app:lintDebug :shared:desktopTest` grün. Bei Änderungen an Paketierung oder am Verhalten
gegenüber dem Server zusätzlich am echten System prüfen, nicht nur per Test.
