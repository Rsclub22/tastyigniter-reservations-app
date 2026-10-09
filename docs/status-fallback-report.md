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
- Detailansicht zeigt bei Rückfall keinen Hinweis (Flag nur für die Liste verlangt); `statusName` ist dort null.

## Überraschungen
- Ein Rückfall bei der Einzelreservierung ist stumm, siehe oben.
- Der Retry ist auch bei 5xx mit HTML-Antwort (kein JSON) aktiv, weil `send` dort ebenfalls den 5xx-Code trägt.
