# Rückfall ohne Status bei 5xx

## Geändert
- `TastyIgniterApi`: `reservationPage()` und `reservation(id)` gehen über `getWithStatusFallback()`.
  Bei HTTP 5xx genau eine Wiederholung mit `include=tables,location`; scheitert sie auch, wird der
  **erste** Fehler geworfen. 4xx, Netzfehler (Code 0) werden nicht wiederholt.
- `reservations()` liefert jetzt `ReservationList(items, statusMissing)` (neu in `Models.kt`).
  Bei mehreren Seiten: eine zurückgefallene Seite setzt das Flag für den ganzen Abruf (Tagesansicht:
  alle tatsächlich geladenen Seiten, auch die der Binärsuche).
- `ReservationListViewModel`/`ListState.statusMissing`; `ReservationListScreen`: eine Zeile in Fehlerfarbe
  über der Zusammenfassung („Status konnte nicht geladen werden – bestätigte und ausstehende
  Reservierungen sind nicht zu unterscheiden.“). Nicht wegklickbar. `ReservationRepository` blieb
  unverändert (der abgeleitete Rückgabetyp reicht das Ergebnis durch).
- README: Hinweis zum Verhalten.

## Tests
`TastyIgniterApiTest` (8 neu, 3 bestehende auf `.items` angepasst), `StatusHinweisTest` (neu, desktopTest;
echte Kette Viewmodel → Repository → API gegen MockWebServer, echter DataStore).

## Bruchbeweise (je eine Änderung, rot, zurückgenommen)
| Bruch | Rot |
| --- | --- |
| `status` wird im Retry nicht entfernt | retries-without-status, single-reservation-retry, multi-page, VM-Flag |
| Retry bei jedem Fehler statt nur 5xx | 401, 422, 404 (je „exactly one request“) |
| Fehler des Retry statt des ersten geworfen | „surfaces the original error“ |
| Retry gibt Flag `false` | retries-…-reports-it, multi-page, VM-Flag |
| Flag der No-Date-Seiten nicht gesammelt | multi-page, retries-…-reports-it |
| Viewmodel ignoriert das Flag | VM-Test „flag is set“ |
| Erfolg meldet Flag `true` | „successful request … not flagged“, VM „flag stays clear“ |
| zweiter Retry (Schleife) | „surfaces the original error … does not loop“ (3 statt 2 Requests) |

## Nicht getestet
- Die Zeile selbst (Compose) – kein UI-Test; nur Zustand geprüft. Nicht auf einem Gerät angesehen.
- Echter TastyIgniter-Server mit `status_id = 0`; nur ein nachgestellter 500.
- Die Zeile auf einem Gerät ansehen und einen echten Server mit `status_id = 0` ausprobieren: liegt beim Auftraggeber mit dem Restaurant, bewusst nicht von mir.

## Überraschungen
- Der Retry ist auch bei 5xx mit HTML-Antwort (kein JSON) aktiv, weil `send` dort ebenfalls den 5xx-Code trägt.

## Nachtrag: Detailansicht
- `reservation(id)` liefert `LoadedReservation(reservation, statusMissing)`; `DetailState.statusMissing` wird in
  `load()` und nach `changeStatus()` gesetzt. Die Bearbeiten-Ansicht nimmt `.reservation` und ignoriert das Flag.
- Der Text steht nur noch einmal: Composable `StatusFehltHinweis()` in `ui/components/Common.kt`, von Liste
  und Detail benutzt.
- Neue Tests: 500→200 am Einzelendpunkt (zweiter Request ohne `status`, mit `tables`/`location`), Flag gesetzt/frei,
  kein Retry bei 401 und 404; `StatusHinweisTest`: Detail-Viewmodel-Flag gesetzt/frei.

| Bruch | Rot |
| --- | --- |
| `status` im Retry behalten | single-retry, detail-flag-set |
| Retry bei jedem Fehler | single 401, single 404 (+ Listen-Tests) |
| API verwirft Flag (`false`) | single-retry, detail-flag-set |
| API meldet immer `true` | single-not-flagged, detail-flag-clear |
| Detail-Viewmodel ignoriert Flag | detail-flag-set |

Nicht durch Test gedeckt: dass der Detailschirm die Zeile bei gesetztem Flag wirklich zeichnet
(`if (state.statusMissing) StatusFehltHinweis()` – nur der Zustand ist geprüft, wie bei der Liste).
