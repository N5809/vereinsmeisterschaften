# Vollständige Dateipfade

Alle Pfade gelten relativ zum Projekt-Hauptverzeichnis mit der `pom.xml`. Der `src/`-Ordner ist vollständig.

| Pfad im Projekt | Zweck |
| --- | --- |
| `.gitignore` | Lokale Datenbank, Build-Ausgaben und IDE-Dateien von Git ausschließen |
| `pom.xml` | Maven-Abhängigkeiten und Spring-Boot-Plugin |
| `src/main/java/de/dlrg/vereinsmeisterschaft/VereinsmeisterschaftApplication.java` | Startklasse der Anwendung |
| `src/main/java/de/dlrg/vereinsmeisterschaft/config/WebConfig.java` | WebSocket-Konfiguration, Sitzungen und HTTP-Schutzheader |
| `src/main/java/de/dlrg/vereinsmeisterschaft/model/BackupModels.java` | Datentypen der versionierten JSON-Sicherung |
| `src/main/java/de/dlrg/vereinsmeisterschaft/model/Models.java` | Datentypen für Teilnehmer, Klassen, Läufe und Ranglisten |
| `src/main/java/de/dlrg/vereinsmeisterschaft/repository/RaceRepository.java` | SQL-Zugriff auf Teilnehmer, Klassen, Disziplinen und Läufe |
| `src/main/java/de/dlrg/vereinsmeisterschaft/service/AccessService.java` | Anmeldung, Rollen und Stationscodes |
| `src/main/java/de/dlrg/vereinsmeisterschaft/service/BackupService.java` | Export, Dateiprüfung, atomare Wiederherstellung und Zurücksetzen |
| `src/main/java/de/dlrg/vereinsmeisterschaft/service/ExcelService.java` | Excel-Vorlagen und Teilnehmerimport |
| `src/main/java/de/dlrg/vereinsmeisterschaft/service/RaceService.java` | Wettkampflogik, Klassenverwaltung, Zeitnahme und Fehlstart |
| `src/main/java/de/dlrg/vereinsmeisterschaft/service/ServerClock.java` | Monotone Serverzeit und Kennung der aktuellen Zeitbasis |
| `src/main/java/de/dlrg/vereinsmeisterschaft/service/StandingsCalculator.java` | Klassen- und Gruppenranglisten sowie Disziplinvergleich |
| `src/main/java/de/dlrg/vereinsmeisterschaft/web/ApiController.java` | HTTP-Schnittstellen und CSV-Exporte |
| `src/main/java/de/dlrg/vereinsmeisterschaft/web/ApiErrors.java` | Lesbare API-Fehlermeldungen |
| `src/main/java/de/dlrg/vereinsmeisterschaft/web/DataController.java` | Schnittstellen für Datensicherung und Zurücksetzen |
| `src/main/java/de/dlrg/vereinsmeisterschaft/web/RaceSocketHandler.java` | Live-Nachrichten, Zeitabgleich und Ereignisbestätigung |
| `src/main/resources/application.properties` | Port, Datenbankpfad, Uploadgrenzen und Zeitgrenzen |
| `src/main/resources/schema.sql` | Tabellen und Erweiterung bestehender Datenbanken |
| `src/main/resources/static/bahn.html` | Mobile Bahnansicht |
| `src/main/resources/static/css/app.css` | Gemeinsames Layout für Verwaltung und Stationen |
| `src/main/resources/static/index.html` | Anmeldeansicht |
| `src/main/resources/static/js/common.js` | Zeitabgleich, WebSocket-Verbindung und lokale Ereignissicherung |
| `src/main/resources/static/js/data.js` | Exportauswahl, Download, Dateiprüfung und Löschdialog |
| `src/main/resources/static/js/login.js` | Anmeldung und Auswahl der Stationsansicht |
| `src/main/resources/static/js/manager.js` | Teilnehmer, Klassen, Läufe und Ergebnisansichten bedienen |
| `src/main/resources/static/js/station.js` | Bereitschaft, Start, Stopp und Fehlstart bedienen |
| `src/main/resources/static/manager.html` | Verwaltungsansichten einschließlich Daten & Sicherung |
| `src/main/resources/static/starter.html` | Mobile Starteransicht mit Fehlstart-Button |
| `src/test/java/de/dlrg/vereinsmeisterschaft/BackupServiceTest.java` | 12 Tests einschließlich aller 31 Exportauswahlen und Rollback |
| `src/test/java/de/dlrg/vereinsmeisterschaft/RaceServiceTest.java` | 24 Tests für Wettkampflogik, Import, Löschen und Fehlstart |
| `src/test/java/de/dlrg/vereinsmeisterschaft/StandingsCalculatorTest.java` | 13 Tests für Klassen, Gruppen, Disziplinvergleich und Zeitaddition |
| `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` | Testkonfiguration ohne dynamischen Java-Agenten |

Zusätzlich: `README.md` (Bedienung), `ALLE_PROJEKTDATEIEN.md` (gesamter Quellcode) und `TESTBERICHT.md`.

## Vorhandenes Projekt aktualisieren

Anwendung stoppen, eigene Änderungen in `src/` sichern und den vollständigen `src/`-Ordner aus diesem
Paket übernehmen. `pom.xml` bleibt unverändert. Den vorhandenen Ordner `data/` behalten; Tabellen und
Wertungsklassen werden beim nächsten Start automatisch ergänzt. Anschließend Anwendung starten und
die Seiten auf Laptop und Handys neu laden (am Laptop gegebenenfalls Strg + F5).

Bei eigener `application.properties` die neuen Multipart-Grenzen 25 MB / 26 MB übernehmen.

Seit der vorherigen ZIP wurden diese Dateien ergänzt oder geändert:

| Pfad im Projekt | Stand |
| --- | --- |
| `src/main/java/de/dlrg/vereinsmeisterschaft/model/BackupModels.java` | Neu |
| `src/main/java/de/dlrg/vereinsmeisterschaft/model/Models.java` | Geändert |
| `src/main/java/de/dlrg/vereinsmeisterschaft/repository/RaceRepository.java` | Geändert |
| `src/main/java/de/dlrg/vereinsmeisterschaft/service/BackupService.java` | Neu |
| `src/main/java/de/dlrg/vereinsmeisterschaft/service/RaceService.java` | Geändert |
| `src/main/java/de/dlrg/vereinsmeisterschaft/service/ServerClock.java` | Geändert |
| `src/main/java/de/dlrg/vereinsmeisterschaft/service/StandingsCalculator.java` | Geändert |
| `src/main/java/de/dlrg/vereinsmeisterschaft/web/ApiController.java` | Geändert |
| `src/main/java/de/dlrg/vereinsmeisterschaft/web/ApiErrors.java` | Geändert |
| `src/main/java/de/dlrg/vereinsmeisterschaft/web/DataController.java` | Neu |
| `src/main/resources/application.properties` | Geändert |
| `src/main/resources/schema.sql` | Geändert |
| `src/main/resources/static/css/app.css` | Geändert |
| `src/main/resources/static/js/common.js` | Geändert |
| `src/main/resources/static/js/data.js` | Neu |
| `src/main/resources/static/js/manager.js` | Geändert |
| `src/main/resources/static/manager.html` | Geändert |
| `src/test/java/de/dlrg/vereinsmeisterschaft/BackupServiceTest.java` | Neu |
| `src/test/java/de/dlrg/vereinsmeisterschaft/RaceServiceTest.java` | Geändert |
| `src/test/java/de/dlrg/vereinsmeisterschaft/StandingsCalculatorTest.java` | Geändert |

Für Zurücksetzen oder Wiederherstellung dient jetzt die Programmseite **Daten & Sicherung**.
