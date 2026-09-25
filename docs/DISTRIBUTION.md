# Verteilung der App

Es gibt zwei Wege, die App auf die Geräte zu bringen. Sie schließen sich nicht aus: dieselbe
Codebasis und derselbe Upload-Schlüssel funktionieren für beide.

| | GitHub-Releases (APK) | Google Play Store (AAB) |
| --- | --- | --- |
| Kosten | keine | einmalig 25 USD |
| Aufwand bis zur ersten Version | ca. 10 Minuten | Tage bis Wochen (Konto, Prüfung, ggf. 14 Tage Testphase) |
| Updates | App meldet neue Versionen, Installation per Tipp | automatisch über den Play Store |
| Geeignet für | eigenes Team | öffentliche Verteilung, andere Restaurants |

Die App erkennt selbst, woher sie installiert wurde: Aus dem Play Store installierte Geräte prüfen
**nicht** GitHub auf Updates, das übernimmt der Store.

---

## Weg 1: Signierte APK über GitHub-Releases (aktuell genutzt)

### Einmalig: Signier-Schlüssel

1. Keystore erzeugen (`keytool` ist Teil jedes JDK):
   ```bash
   keytool -genkeypair -v -keystore release.jks -alias upload \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. **`release.jks` und Passwörter sicher aufbewahren** (Passwortmanager + Backup). Ohne diesen
   Schlüssel lassen sich keine Updates mehr über bestehende Installationen installieren.
3. In Text umwandeln: `base64 -w0 release.jks > release.jks.base64`
   (macOS: `base64 -i release.jks -o release.jks.base64`)
4. Secrets anlegen unter *Settings → Secrets and variables → Actions*:

   | Secret | Inhalt |
   | --- | --- |
   | `ANDROID_KEYSTORE_BASE64` | Inhalt von `release.jks.base64` |
   | `ANDROID_KEYSTORE_PASSWORD` | Keystore-Passwort |
   | `ANDROID_KEY_ALIAS` | `upload` |
   | `ANDROID_KEY_PASSWORD` | Schlüssel-Passwort |

### Neue Version veröffentlichen

```bash
git tag v1.0.0
git push origin v1.0.0
```

oder im Browser unter *Releases → Draft a new release*. Die Pipeline baut signierte APK und AAB und
hängt beide an das GitHub-Release. `versionName` kommt aus dem Tag, `versionCode` aus der Laufnummer.

### Updates auf den Geräten

- Die Release-App prüft beim Start einmal `…/releases/latest` auf GitHub und bietet eine neuere
  Version zum Download an („Später“ blendet genau diese Version aus).
- Manuell: *Einstellungen → Nach Updates suchen*.
- Neue APK einfach über die alte installieren; Anmeldung und Einstellungen bleiben erhalten.
- Voraussetzung: Das Repository ist öffentlich (die Prüfung nutzt die GitHub-API ohne Anmeldung) und
  jede Version ist mit **demselben** Keystore signiert.
- Das Ziel-Repository steht in `app/build.gradle.kts` (`UPDATE_REPO`); ein leerer Wert schaltet die
  Prüfung ab.

Hinweis: Debug-APKs aus den CI-Artefakten sind eine eigene App (Paket-ID mit `.debug`) und prüfen
nicht automatisch auf Updates.

---

## Weg 2: Google Play Store (vorgemerkt)

Noch nicht eingerichtet. Die Schritte, wenn es so weit ist:

1. **Entwicklerkonto** unter play.google.com/console anlegen: 25 USD einmalig, Identitätsprüfung.
   Als Organisation zusätzlich eine D-U-N-S-Nummer (kostenlos, dauert Tage bis Wochen).
2. **Vorher im Code klären:**
   - Paketname (`applicationId`, derzeit `io.github.rsclub22.tireservations`) ist nach dem ersten
     Upload **nicht mehr änderbar**.
   - `targetSdk` auf die Version anheben, die Google für neue Apps verlangt (steht in der Play Console).
   - Markenname „TastyIgniter“ nicht prominent im Store-Titel verwenden.
3. **Unterlagen:** Datenschutzerklärung als öffentliche URL, App-Icon 512×512, Feature Graphic
   1024×500, mindestens 2 Screenshots.
4. **Upload:** das `.aab` aus dem GitHub-Release hochladen, dabei **Play App Signing** aktivieren
   (der Keystore oben wird dann zum Upload-Schlüssel).
5. **Formulare in der Play Console:**
   - *App-Zugriff:* Google-Prüfer brauchen eine erreichbare **Demo-TastyIgniter-Instanz** mit
     Testzugang, sonst wird die App abgelehnt.
   - *Datensicherheit:* Gastdaten (Name, E-Mail, Telefon) werden nur zwischen App und eigenem Server
     übertragen, nicht an Dritte.
   - Inhaltseinstufung, Zielgruppe (Erwachsene), keine Werbung, Kategorie (z. B. Business).
6. **Testphase:** Neue private Entwicklerkonten müssen vor der Veröffentlichung einen geschlossenen
   Test mit mindestens 12 Testern über 14 Tage durchführen (Organisationskonten nicht). Der interne
   Test (bis 100 Tester, sofort) eignet sich zum Ausprobieren.
7. **Produktion:** Release einreichen; die erste Prüfung dauert meist einige Tage.
8. **Optional später:** Upload aus der CI automatisieren (Google-Cloud-Dienstkonto als Secret,
   Upload in den internen Test bei jedem `v*`-Tag).

Google-Richtlinien ändern sich regelmäßig; maßgeblich ist, was die Play Console anzeigt.

### Hinweis zur Entwickler-Verifizierung

Google hat angekündigt, dass auch außerhalb des Play Stores installierte Apps einem verifizierten
Entwickler zugeordnet sein müssen (schrittweise Einführung nach Ländern ab 2026). Sobald das für
Deutschland gilt, ist für Weg 1 eine einmalige Registrierung nötig, nicht der Play Store.

---

## Linux-Desktop-Client

Der Desktop-Client wird bei jedem `v*`-Tag als `.deb` (Debian/Ubuntu) mit an das GitHub-Release
gehängt; bei jedem Push liegt er als CI-Artefakt `desktop-deb` bereit. Installation:

```bash
sudo apt install ./ti-reservierungen_1.0.0_amd64.deb
```

Updates: neues `.deb` genauso installieren (ersetzt die alte Version). Eine Update-Prüfung wie in der
Android-App gibt es auf dem Desktop noch nicht. Ein `.rpm` (Fedora/openSUSE) lässt sich mit
`./gradlew :desktop:packageRpm` bauen (braucht `rpm-build`), ist aber noch nicht Teil der Pipeline.

