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

## Weg 2: Google Play Store

Eingerichtet, der interne Test läuft. Den Upload dorthin erledigt die Pipeline bei jedem
`v*`-Tag selbst – siehe [Automatischer Upload](#automatischer-upload-in-den-internen-test)
weiter unten. Die Schritte davor, einmalig:

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
Google-Richtlinien ändern sich regelmäßig; maßgeblich ist, was die Play Console anzeigt.

### Automatischer Upload in den internen und den geschlossenen Test

Jeder `v*`-Tag lädt das signierte AAB in die Tracks **internal** und **alpha** (geschlossener
Test) hoch, zusammen mit der `mapping.txt` – ohne die sind Abstürze in der Play Console
unlesbar, weil der Release-Build mit R8 verkleinert wird. Im geschlossenen Test erscheint die
Fassung erst nach der Prüfung durch Google. Der Schritt in die Produktion bleibt ein bewusster
Klick in der Play Console; die Pipeline veröffentlicht nichts von sich aus.

Die **Neuerungen** (Release Notes, Sprache `de` wie in der Play Console) stammen aus der Nachricht des Tags:

```bash
git tag -a v0.3.0 -m "- Wartezeiten stehen in der Liste
- Fehler beim Speichern behoben"
```

Jede Zeile wird ein Punkt der Liste. Ohne Nachricht (leichtgewichtiger Tag) nimmt die Pipeline
die Commit-Betreffe seit dem vorigen Tag – die sind ohne Umlaute und nennen auch Interna, daher
besser den Tag annotieren. Play erlaubt 500 Zeichen; was darüber hinausgeht, wird zeilenweise
abgeschnitten. Dieselbe Liste steht auch im GitHub-Release.

Der Upload läuft **nach** dem GitHub-Release. Geht bei Google etwas schief, ist das Release
trotzdem schon draußen und die Desktop-Fassung kann sich erneuern.

Dafür braucht es einmalig ein Dienstkonto:

Das Dienstkonto entsteht **in der Google Cloud Console**, nicht in der Play Console. Einen
Menüpunkt *Einstellungen → API-Zugriff* gibt es dort nicht mehr; das Entwicklerkonto muss
seit der Umstellung auch nicht mehr mit einem Cloud-Projekt verknüpft werden
([Googles Anleitung](https://developers.google.com/android-publisher/getting_started)).

1. In der **Google Cloud Console** ein Projekt anlegen oder ein vorhandenes nehmen.
2. Für dieses Projekt die **Google Play Developer API** aktivieren. Wird leicht übersehen –
   fehlt sie, scheitert der Upload später mit einer nichtssagenden Meldung.
3. *IAM & Verwaltung → Dienstkonten → **Dienstkonto erstellen***.
4. Beim angelegten Dienstkonto *Schlüssel → Schlüssel hinzufügen → Neuen Schlüssel
   erstellen → **JSON***. Diese Datei wandert gleich ins Secret.
5. In der **Play Console** unter *Nutzer und Berechtigungen → **Neue Nutzer einladen***
   die **E-Mail-Adresse des Dienstkontos** eintragen und ihr Zugriff auf **diese App**
   geben, mit dem Recht *Releases für Testspuren verwalten* (mehr braucht die Pipeline
   nicht – sie veröffentlicht bewusst nicht in die Produktion).
6. Den kompletten Inhalt der JSON-Datei als Repository-Secret hinterlegen:

| Secret | Inhalt |
| --- | --- |
| `PLAY_SERVICE_ACCOUNT_JSON` | die JSON-Schlüsseldatei des Dienstkontos, vollständig |

Fehlt das Secret, überspringt die Pipeline den Upload mit einem Hinweis im Log und baut
das GitHub-Release wie gehabt. Dasselbe gilt ohne `ANDROID_KEYSTORE_BASE64`: das AAB wäre
dann debug-signiert, und Play lehnt so eines ohnehin ab.

> **`versionCode` im Blick behalten.** Er kommt aus der Laufnummer des Workflows
> (`github.run_number`) und muss für Play streng steigen. Das passt, solange der Workflow
> `ci.yml` nicht umbenannt oder neu angelegt wird – dann beginnt die Laufnummer wieder bei 1,
> und Play weist jeden Upload ab, bis sie den höchsten bereits hochgeladenen Wert überholt hat.
> Stand v0.2.0: Laufnummer 23.

### Hinweis zur Entwickler-Verifizierung

Google hat angekündigt, dass auch außerhalb des Play Stores installierte Apps einem verifizierten
Entwickler zugeordnet sein müssen (schrittweise Einführung nach Ländern ab 2026). Sobald das für
Deutschland gilt, ist für Weg 1 eine einmalige Registrierung nötig, nicht der Play Store.
