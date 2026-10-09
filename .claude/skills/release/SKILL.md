---
name: release
description: Neue Version vorbereiten und veröffentlichen - Prüfungen, Versionsnummer, Tag, Pipeline beobachten
disable-model-invocation: true
---

Ein `v*`-Tag auf GitHub löst aus: signiertes APK und AAB, `.deb`, GitHub-Release und den Upload in den
internen Test bei Google Play. Das lässt sich nicht sauber zurücknehmen. Deshalb gilt:
**Tag setzen und pushen nur nach ausdrücklichem Ja des Nutzers**, auch wenn der Skill aufgerufen wurde.

## Ablauf

1. **Zustand prüfen.** `git status` muss sauber sein, Branch `main`, auf dem Stand von `origin/main`
   (`git fetch`, dann vergleichen). Sonst abbrechen und melden.
2. **Letzte Version finden.** `git tag --sort=-v:refname | head -3` und `git log <tag>..HEAD --oneline`.
   Ohne neue Commits seit dem Tag gibt es nichts zu veröffentlichen.
3. **Lokal prüfen.** `./gradlew :app:lintDebug :shared:desktopTest`. Schlägt etwas fehl, abbrechen und
   die Ausgabe zeigen.
4. **CI-Stand prüfen.** `gh run list --branch main --limit 3`. Der letzte Lauf auf `main` muss grün sein.
5. **Versionsnummer vorschlagen** (Semver, aus den Commits seit dem Tag: Fehlerbehebungen ergeben Patch,
   neue Funktionen Minor) und zusammen mit den Commit-Betreffen als Änderungsliste zeigen. Der Nutzer
   bestätigt Nummer und Liste.
6. **Stolpersteine ansprechen**, wenn sie zutreffen:
   - `versionCode` kommt aus `github.run_number`; wurde `ci.yml` umbenannt oder neu angelegt, lehnt Play
     den Upload ab (`docs/DISTRIBUTION.md`, Abschnitt zu `versionCode`).
   - Enthält der Stand Änderungen an der Desktop-Paketierung (`nativeDistributions`), ist
     `./gradlew :shared:createDistributable` einmal zu starten und das Ergebnis zu öffnen, weil `run` fehlende
     jlink-Module nicht zeigt.
   - Änderungen, die den Server oder den Tresenrechner betreffen, sind am lebenden System geprüft
     (Memory "Am lebenden System prüfen").
7. **Nach dem Ja:** `git tag vX.Y.Z` und `git push origin vX.Y.Z`. Nichts anderes pushen.
8. **Pipeline beobachten:** `gh run watch` auf den Tag-Lauf. Meldet ein Schritt Fehler, den Schritt und die
   Log-Zeilen nennen. Bei Fehlern im Play-Upload ist das GitHub-Release trotzdem schon draußen; das so sagen.
9. **Abschluss:** Link zum GitHub-Release nennen. Der Schritt in die Produktion bei Google Play bleibt ein
   Klick des Nutzers in der Play Console.

Keine Dateien ändern (die Version steckt im Tag), keine Tags löschen oder verschieben, nicht mit `--force` pushen.
