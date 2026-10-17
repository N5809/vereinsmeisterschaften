# Prüfung des Projektpakets

Stand: 17.10.2026. Die verwendeten Namen, Anmeldungen und Messwerte sind Testdaten.

## Build und automatisierte Tests

JDK 21, Maven 3.9.11 und Spring Boot 3.5.16. Maven `verify` erfolgreich; ausführbares JAR gebaut.
**49 Tests, 0 Fehler und 0 fehlgeschlagene Assertions.** Alle JavaScript-Dateien auf Syntax geprüft.

- 24 Tests der Wettkampflogik: Bereitschaft, Start/Stopp, Rollen, Ereigniswiederholung, gleichzeitige
  Stopps, Wiederverbindung, Abbruch, Fehlstart, Neustart, Excel-Import, Teilnehmerlöschung und Korrekturen.
- 13 Tests der Wertung: letzte gültige Zeit, zeitliche Reihenfolge trotz anderer Planungsnummer,
  Addition, fehlende Ergebnisse, Gruppen, Gleichstände, Klassen, gemeinsame Gruppenlisten und
  Disziplinvergleich über alle Gruppen ohne Vermischen verschiedener Disziplinen.
- 12 Tests der Sicherungsfunktionen und Wertungsklassen. Ein Test durchläuft alle **31 nicht leeren
  Checkbox-Kombinationen**, exportiert, prüft und stellt jede Auswahl wieder her.

Die Sicherungstests prüfen insbesondere:

1. Teilnehmer einschließlich Löschmarkierung und reservierter IDs, Klassen, Läufe und Zeiten vollständig wiederherstellen.
2. Export ohne Ergebnisse enthält keine Zeiten, Hinweise oder Prüfmarkierungen und keine Fehlstart-/Abbruchversuche.
3. Abhängigkeiten jeder Auswahl ergeben eine eigenständig wiederherstellbare Datei.
4. Falsche Löschbestätigung verändert nichts; vollständiges Zurücksetzen leert alle Wettkampftabellen
   und das Protokoll, erneuert den Datenbestand und lässt neue Läufe bei Nummer 1 beginnen.
5. Zurücksetzen während Bereitschaft oder laufender Messung wird abgelehnt.
6. Wiederherstellung in vorhandene Daten, auch bei nur einem Katalogeintrag, wird abgelehnt.
7. Fehlerhafte Dateien, doppelte IDs, fehlende Verweise, unbekannte Versionen und zusätzliche JSON-Daten werden abgelehnt.
8. Ein absichtlich ausgelöster Datenbankfehler während des Einlesens rollt bereits eingefügte
   Klassen und Disziplinen vollständig zurück: kein Teilbestand verbleibt.
9. Eine laufende Sicherung wird als unterbrochener Lauf wiederhergestellt, nie als aktive Uhr.
   Alte Nachrichten aus dem vorherigen Datenbestand können nicht auf sie angewendet werden.
10. Dateivorschau ist schreibgeschützt; Klassen entstehen aus Teilnehmern und benutzte Klassen sind geschützt.

## Browser, Migration und Wiederherstellung

Das gebaute JAR lief mit einer Kopie der Dateidatenbank aus der vorherigen Browserprüfung.
Es wurden fünf getrennte Chromium-Sitzungen verwendet: Verwaltung und vier Stationen.
Die Stationen verwendeten 390 × 844 Pixel und Touch-Bedienung.

Erfolgreich geprüft:

- Bestehende sechs Personen und Ergebnisse erhalten; vier passende Wertungsklassen automatisch angelegt.
- Neue Klassen über die eigene Rubrik anlegen; unbenutzte Klasse per Papierkorb entfernen.
- Sarah über die Nachmeldung einer Klasse zuordnen, einen Lauf mit Starter/Bahn schwimmen und Zeit korrigieren.
- Ergebnisansicht startet mit getrennten Klassen, z. B. Hechte – männlich und Hechte – weiblich.
- Gemeinsame Hechte-Gesamtliste kombiniert die Wertungen und erhält die richtigen Plätze.
- Freistilvergleich über sieben Personen aus drei Trainingsgruppen; Sarah steht dort auf Platz 2.
  Der Namensfilter zeigt weiterhin ihren ursprünglichen Platz. Andere Disziplinen bleiben getrennt.
- Passender CSV-Export des Disziplinvergleichs.
- Während eines aktiven Laufes ist das Zurücksetzen gesperrt, auch ein direkter API-Aufruf wird abgelehnt.
- Eine Bahn offline schalten und einen Stopp lokal speichern; Lauf abbrechen.
- Sicherungen mit sämtlichen Inhalten und mit deaktivierten Ergebnissen über den Browser herunterladen.
- Vorschau der Sicherung bei noch vorhandenen Daten anzeigen. Falsche Datei ablehnen, ohne Daten zu verändern.
- Löschdialog abbrechen: Daten erhalten. Exakte Bestätigung eingeben: alle Wettkampftabellen und Kataloge leer.
- Die bereits zuvor geprüfte Konfigurationsdatei wiederherstellen: sieben Personen und geplante Läufe,
  aber keine alten Zeiten. Das offline gewesene Handy verwirft seinen alten Stopp bei Wiederverbindung.
- Einen wiederhergestellten Lauf mit allen vier Stationen vollständig durchführen.
- Erneut zurücksetzen und vollständige Sicherung einlesen: dieselben Klassen- und Gruppenranglisten
  einschließlich Einzelzeiten und Platzierungen wie vor dem Löschen.
- Bahnrollen haben keinen Zugriff auf Klassenverwaltung, Sicherung, Dateivorschau, Wiederherstellung oder Zurücksetzen.
- Server beenden und mit derselben Datenbank neu starten: Klassen, Zähler, Ranglisten und Datenbestandskennung erhalten.
- Keine JavaScript-Seitenfehler. Klassenansicht, Ranglisten, Disziplinvergleich und Sicherungsseite visuell kontrolliert.

Der Zeitabgleich wird beim Bereitschaftssignal vor der READY-Nachricht an den Server gesendet.
Dadurch lässt sich nach einer Wiederherstellung auch unmittelbar nach dem frischen Zeitabgleich
wieder die Bereitschaft bestätigen. Dieser Ablauf wurde im Browser mit allen Stationen geprüft.

## Praktische Grenzen

Die Browserprüfung misst keine reale Zeitabweichung zwischen vier Smartphones über euren Hotspot.
Den konkreten Betrieb mit Geräten, Touchscreens, WLAN und Energiesparfunktionen weiterhin vor Ort testen.

Die JSON-Sicherung enthält die ausgewählten Anwendungsdaten. Sie ist kein binäres H2-Abbild und enthält
keine Servereinstellungen, Passwörter, Sitzungen, technischen Übertragungsbestätigungen oder alten Protokolle.
Beim Wiederherstellen entsteht ein neuer Protokolleintrag. Das konfigurierte Zeitadditionsverfahren
verwendet keine DLRG-Punktetabelle.
