# TastyIgniter Reservierungen (Android)

Android-App für das Restaurant-Team, um Tischreservierungen in [TastyIgniter](https://tastyigniter.com)
zu verwalten: Reservierungen des Tages ansehen, telefonische Reservierungen selbst eintragen,
bearbeiten, bestätigen/stornieren und löschen.

Die App spricht direkt mit der REST-API von TastyIgniter (Erweiterung **igniter.api**). Es wird kein
eigener Server benötigt.

## Funktionen

- **Anmeldung** mit dem TastyIgniter-Admin-Zugang (Mitarbeiter) – optional auch als Kunde
- **Tagesansicht**: Reservierungen je Tag, vor/zurück blättern, Datum wählen, Summe der Gäste
- **Filter** nach Standort und Status, **Suche** nach Name, E-Mail, Telefon oder Reservierungsnummer
- **Neue Reservierung** anlegen: Standort, Datum, Uhrzeit, Personen, Dauer, Gastdaten, Anmerkungen,
  Tischauswahl (mit Kapazitätsprüfung) und Anfangsstatus
- **Bearbeiten** und **Löschen** bestehender Reservierungen
- **Status ändern** (z. B. Ausstehend → Bestätigt / Storniert) mit Kommentar und optionaler
  E-Mail-Benachrichtigung an den Gast
- Gast direkt **anrufen** oder **per E-Mail** kontaktieren
- Standard-Standort für Betriebe mit mehreren Filialen
- Material 3, Dark Mode, dynamische Farben (Android 12+)

## Voraussetzungen in TastyIgniter

1. Die Erweiterung **API** (`igniter.api`) installieren und aktivieren
   (*System → Erweiterungen*).
2. Unter *Tools → APIs* sicherstellen, dass die Ressourcen **Reservations**, **Locations**,
   **Tables** und **Status** aktiv sind.
3. Einen Mitarbeiter-Zugang verwenden, der Reservierungen verwalten darf. Die App meldet sich über
   `POST /api/token` mit `is_admin=true` an und nutzt das zurückgegebene Token.
4. Die Seite sollte per **HTTPS** erreichbar sein. HTTP ist nur für Tests im lokalen Netz gedacht;
   die App zeigt dafür eine Warnung an.

Verwendete Endpunkte:

| Aktion | Endpunkt |
| --- | --- |
| Anmelden | `POST /api/token` |
| Benutzer abrufen | `GET /api/token/user` |
| Reservierungen (Filter `location`, `status`, `search`, `dateTimeFilter`) | `GET /api/reservations` |
| Reservierung anlegen / ändern / löschen | `POST`, `PATCH`, `DELETE /api/reservations/{id}` |
| Status ändern | `PATCH /api/reservations/{id}/status` |
| Stammdaten | `GET /api/locations`, `/api/tables`, `/api/status` |

> Hinweis: Die API verlangt beim Anlegen **Vorname, Nachname, E-Mail und Telefon**. Für Gäste ohne
> E-Mail-Adresse muss eine Platzhalter-Adresse eingetragen werden.

## Installation

Fertige APKs gibt es unter **Releases** (signiert) bzw. bei jedem CI-Lauf als Artefakt
`app-debug-apk` (Debug-Build, lässt sich parallel zur Release-Version installieren).

## Entwicklung

- Kotlin, Jetpack Compose, Material 3, OkHttp, kotlinx.serialization, DataStore
- `minSdk` 26 (Android 8.0), `targetSdk` 36
- JDK 17+ und Android SDK erforderlich

```bash
./gradlew assembleDebug          # Debug-APK: app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # Unit-Tests (JSON-Parsing, API-Client, Validierung)
./gradlew lintDebug              # Android Lint
```

Projektstruktur:

```
app/src/main/java/io/github/rsclub22/tireservations/
├── data/        API-Client, JSON:API-Parsing, Einstellungen, Repository
└── ui/          Screens: login, list, detail, edit, settings
```

## CI/CD-Pipeline

`.github/workflows/android.yml`:

- **Jeder Push / Pull Request**: Lint, Unit-Tests, Debug-APK bauen; APK und Berichte werden als
  Artefakte hochgeladen.
- **Tag `v*`** (z. B. `v1.0.0`): signiertes Release-APK und -AAB bauen und als GitHub-Release
  veröffentlichen. `versionName` kommt aus dem Tag, `versionCode` aus der Laufnummer.

Dependabot hält Gradle-Abhängigkeiten und Actions aktuell.

### Signier-Schlüssel einrichten

Einmalig einen Upload-Schlüssel erzeugen (sicher aufbewahren – ohne ihn sind keine Updates möglich):

```bash
keytool -genkeypair -v -keystore release.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks > release.jks.base64
```

Dann im Repository unter *Settings → Secrets and variables → Actions* anlegen:

| Secret | Inhalt |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Inhalt von `release.jks.base64` |
| `ANDROID_KEYSTORE_PASSWORD` | Passwort des Keystores |
| `ANDROID_KEY_ALIAS` | `upload` |
| `ANDROID_KEY_PASSWORD` | Passwort des Schlüssels |

Ohne diese Secrets wird das Release nur mit einem temporären Debug-Schlüssel signiert (mit Warnung
im Log); solche Builds lassen sich nicht als Update übereinander installieren.

Release auslösen:

```bash
git tag v1.0.0
git push origin v1.0.0
```
