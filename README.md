# TastyIgniter Reservierungen

App für das Restaurant-Team, um Tischreservierungen in [TastyIgniter](https://tastyigniter.com)
zu verwalten – auf **Android** und auf dem **Linux-Desktop**, mit demselben Funktionsumfang: Reservierungen des Tages ansehen, telefonische Reservierungen selbst eintragen,
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
- Update-Hinweis bei neuen GitHub-Releases (nicht bei Installation aus dem Play Store);
  am Telefon gegen die APK im Release, auf dem Desktop gegen das .deb-Paket
- Material 3, Dark Mode, dynamische Farben (nur Android 12+; der Desktop nutzt die
  festen Farben des Themes)

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

> Hinweis: Pflichtfelder beim Anlegen sind **Vorname, Nachname und Telefon**. Die E-Mail-Adresse ist
> optional und wird ohne Eingabe gar nicht erst mitgeschickt. Verlangt eine Installation sie doch, zeigt
> die App die Fehlermeldung des Servers am Feld an.

## Installation

**Android:** Fertige APKs gibt es unter **Releases** (signiert) bzw. bei jedem CI-Lauf als
Artefakt `app-debug-apk` (Debug-Build, lässt sich parallel zur Release-Version
installieren). Die Release-App meldet neue Versionen selbst
(*Einstellungen → Nach Updates suchen*).

**Desktop:** Unter **Releases** liegt ein `.deb` für Debian/Ubuntu, bei jedem CI-Lauf
auch als Artefakt `desktop-deb`. Auf Arch-Systemen (kein `dpkg`) stattdessen selbst
bauen – `./gradlew :shared:createDistributable` legt unter
`shared/build/compose/binaries/main/app/` ein eigenständiges Verzeichnis mit Starter und
mitgeliefertem JRE ab.

Beide Vertriebswege (GitHub-Releases und Google Play Store) sind in
[docs/DISTRIBUTION.md](docs/DISTRIBUTION.md) Schritt für Schritt beschrieben.

## Entwicklung

- Kotlin Multiplatform, Compose Multiplatform, Material 3, OkHttp,
  kotlinx.serialization, DataStore
- `minSdk` 26 (Android 8.0), `targetSdk` 36, `compileSdk` 37
- JDK 17 und Android SDK erforderlich

```bash
./gradlew :app:assembleDebug           # Debug-APK: app/build/outputs/apk/debug/
./gradlew :shared:desktopTest          # Unit-Tests (JSON-Parsing, API-Client, Validierung)
./gradlew :app:lintDebug               # Android Lint
./gradlew :shared:run                  # Desktop-Fassung starten
./gradlew :shared:createDistributable  # eigenständiges Desktop-Programm bauen
./gradlew :shared:packageDeb           # .deb (braucht dpkg-deb, also Debian/Ubuntu)
```

Projektstruktur:

```
shared/src/
├── jvmCommonMain/    fast der ganze Code: data/, ui/, AppGraph, platform/ (expect)
├── jvmCommonTest/    die Unit-Tests
├── androidMain/      actual-Implementierungen für Android
└── desktopMain/      actual-Implementierungen + main() für den Desktop

app/src/main/         nur die Android-Anwendung: Manifest, res/, MainActivity,
                      ReservationsApp
```

Zwei Dinge sind erklärungsbedürftig:

**Warum `jvmCommonMain` und nicht `commonMain`?** Der geteilte Code benutzt `java.time`
(rund 30 Fundstellen) und OkHttp – beides gibt es in `commonMain` nicht. Da sowohl
Android als auch Desktop auf der JVM laufen, liegt der Code in einem Zwischen-Source-Set,
von dem beide Ziele abhängen. Eine Umstellung auf `kotlinx-datetime` und Ktor wäre erst
nötig, wenn ein Nicht-JVM-Ziel dazukommt (iOS, wasm); `jvmCommonMain` ist dann genau die
Liste dessen, was umzuziehen ist.

**Warum zwei Module?** Seit AGP 9 dürfen `com.android.application` und das
Multiplatform-Plugin nicht mehr im selben Modul stehen. `:shared` ist daher eine
KMP-Bibliothek (`com.android.kotlin.multiplatform.library`) mit beiden Zielen, `:app` nur
noch die Android-Anwendungshülle darum.

## CI/CD-Pipeline

`.github/workflows/ci.yml`:

- **Jeder Push / Pull Request**: Lint, Unit-Tests, Debug-APK bauen; zusätzlich das
  Desktop-Paket. APK, `.deb` und Berichte werden als Artefakte hochgeladen.
- **Tag `v*`** (z. B. `v1.0.0`): signiertes Release-APK, -AAB und das `.deb` bauen und als
  GitHub-Release veröffentlichen. `versionName` kommt aus dem Tag, `versionCode` aus der
  Laufnummer.

Der Desktop-Job läuft als Matrix mit bisher einem Eintrag (`ubuntu-latest` → `.deb`).
`jpackage` baut ausschließlich für das System, auf dem es läuft; Windows und macOS
brauchen daher eigene Runner und sind in der Matrix auskommentiert vorgemerkt.

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
