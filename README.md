# Vereinsmeisterschaft – Spring Boot / Maven

Lokale Webanwendung für **einen Verwaltungs-Laptop, einen Starter und drei Bahn-Handys**.
Die Geräte benutzen einen gemeinsamen Hotspot bzw. ein gemeinsames WLAN. Nach dem ersten
Maven-Download funktioniert die Anwendung vollständig ohne Internet und ohne externe Webdienste.

## 1. In IntelliJ öffnen

**Der einfachste Weg:** ZIP entpacken und in IntelliJ über **File → Open** die enthaltene
`pom.xml` öffnen. Als Maven-Projekt laden und ein **JDK 21** auswählen.
Maven lädt die in der `pom.xml` angegebenen Abhängigkeiten beim ersten Mal aus dem Internet.

Wenn du bereits ein leeres Spring-Boot-Projekt angelegt hast:

1. Die mitgelieferte `pom.xml` ins Projekt-Hauptverzeichnis übernehmen.
2. `src/main` und `src/test` mit ihrer Ordnerstruktur übernehmen.
3. Die automatisch erzeugte, unbenutzte Application-Klasse und deren leeren Test entfernen,
   damit es nur eine Spring-Boot-Startklasse gibt. Vorhandenen eigenen Code vorher sichern.
4. Im Maven-Fenster **Reload All Maven Projects** ausführen.
5. Unter **Project Structure → Project SDK** und gegebenenfalls **Settings → Build Tools → Maven → Runner → JRE**
   JDK 21 auswählen. Das Projekt enthält keinen Lombok-Code und benötigt kein Lombok-Plugin.

Die wichtigsten Projektwerte:

| Einstellung | Wert |
| --- | --- |
| Build-System | Maven |
| Group | `de.dlrg` |
| Artifact | `vereinsmeisterschaft` |
| Java | 21 |
| Spring Boot | 3.5.16 – bitte die mitgelieferte POM verwenden |
| Java-Basispaket | `de.dlrg.vereinsmeisterschaft` |
| Startklasse | `VereinsmeisterschaftApplication` |
| Standardport | 8080 |

Das Projekt ist auf Java 21 eingestellt. Wenn bereits JDK 24 installiert ist, kann dieses
ebenfalls Java-21-Code kompilieren und ausführen; für den unkomplizierten Einstieg bleibt
JDK 21 die hier verwendete Basis. Nicht nur eine JRE auswählen.

## 2. Starten und anmelden

In IntelliJ diese Klasse öffnen und den grünen Startpfeil an `main` anklicken:

`src/main/java/de/dlrg/vereinsmeisterschaft/VereinsmeisterschaftApplication.java`

Als **Working directory / Arbeitsverzeichnis** das Projekt-Hauptverzeichnis mit der `pom.xml`
verwenden. In der Run-Konsole erscheint:

```text
VERWALTUNG: http://localhost:8080 | Admin-Passwort: …
```

Auf dem Laptop `http://localhost:8080` öffnen, **Verwaltung am Laptop** auswählen und das
angezeigte Passwort eingeben. Das Passwort ist zufällig und wird bei jedem Serverstart neu
erzeugt. Alternativ in der IntelliJ-Run-Konfiguration die Umgebungsvariable
`VM_ADMIN_PASSWORD=dein-eigenes-passwort` setzen. Keine echten Passwörter in ein geteiltes Git-Repository schreiben.

Die Anwendung erzeugt den Ordner `data/` beim ersten Start selbst. Eine separate Datenbankinstallation
ist nicht erforderlich. Hier wird eine H2-Dateidatenbank verwendet, keine flüchtige In-Memory-Datenbank.

Alternativ bei separat installiertem Maven:

```bash
mvn clean verify
java -jar target/vereinsmeisterschaft-1.0.0.jar
```

IntelliJs eingebautes Maven genügt; `mvn` muss für den Start über IntelliJ nicht im System-PATH liegen.
Auch **Maven → Lifecycle → verify** baut das Projekt und führt die Tests aus.

## 3. Handys verbinden

1. Laptop und vier Handys mit demselben Hotspot verbinden.
2. In der Verwaltung **Handys verbinden** öffnen.
3. Die Anwendung zeigt die gefundenen Laptop-IPv4-Adressen und die vier Stationscodes an.
4. Auf jedem Handy die Adresse des passenden WLAN-/Hotspot-Adapters öffnen, etwa
   `http://192.168.43.123:8080`. Diese Beispieladresse durch die tatsächliche Adresse ersetzen.
5. Starter, Bahn 1, Bahn 2 oder Bahn 3 auswählen und den zugehörigen Code eingeben.

**Auf einem Handy verweist `localhost` auf das Handy selbst.** Dort die Laptop-Adresse verwenden.
Auf dem Laptop kann `ipconfig` unter Windows die IPv4-Adresse des WLAN-Adapters anzeigen.
In der Windows-Firewall eingehende Verbindungen zum Java-Prozess bzw. TCP-Port 8080 im verwendeten,
vertrauten lokalen Netz erlauben. Die Firewall nicht pauschal abschalten.

Ein Hotspot muss Kommunikation zwischen den verbundenen Geräten zulassen. Lässt sich die
Laptop-Adresse trotz korrekter IP und Firewall nicht öffnen, kann eine Geräteisolierung des
Hotspots die Ursache sein. In diesem Fall einen anderen Hotspot oder einen lokalen WLAN-Router testen.
Es wird keine Portweiterleitung ins Internet benötigt.

Je Station darf nur eine aktive WebSocket-Verbindung existieren. Ein zweiter Tab derselben Rolle
wird abgewiesen. Alten Tab schließen und gegebenenfalls nach acht Sekunden erneut laden.
Zum Testen auf einem einzigen Laptop unterschiedliche Browserprofile oder getrennte Browser
verwenden: normale Tabs teilen sich denselben Anmelde-Cookie und sind keine unabhängigen Stationen.

Die Stationscodes sind nach jedem Serverstart neu. Das lokale HTTP ist unverschlüsselt; die Anwendung
ist für ein eigenes, passwortgeschütztes Veranstaltungsnetz gedacht und nicht zur Veröffentlichung im Internet.

## 4. Teilnehmer erfassen

Unter **Teilnehmer → Excel-Vorlage** wird eine leere `.xlsx` heruntergeladen.
**Beispieldatei** liefert eine getrennte Datei mit sechs ausdrücklich erfundenen Personen.
Testdaten nur in einer Testdatenbank importieren.

| Teilnehmer-ID | Vorname | Nachname | Jahrgang | Wertung | Trainingsgruppe |
| --- | --- | --- | --- | --- | --- |
| 1001 | Mia | Beispiel | 2016 | weiblich | Hechte |
| 1002 | Leon | Muster | 2016 | männlich | Hechte |

Regeln für den Import:

- Erstes Tabellenblatt, Überschriften in Zeile 1, eine Person pro Zeile.
- `Teilnehmer-ID`, `Vorname`, `Nachname` und `Jahrgang` sind Pflichtspalten.
- `Wertung` und `Trainingsgruppe` dürfen fehlen oder leer bleiben.
- Spaltenreihenfolge darf abweichen. Die Vorlage ist der einfachste Weg.
- IDs bleiben dauerhaft unverändert; bei führenden Nullen die ID-Spalte als Text formatieren.
- Jahrgang ist eine ganze Jahreszahl; Formeln werden nicht übernommen.
- Höchstens 2000 Teilnehmerzeilen und 5 MB pro Datei.
- Der Import ergänzt neue IDs. Schon vorhandene oder doppelte IDs brechen den gesamten Import ab.
  Es findet kein stilles Überschreiben vorhandener Personen statt.
- Der Import ist atomar: entweder alle geprüften Personen oder keine.

**Wertungsklassen:** Unter der eigenen Rubrik **Wertungsklassen** eine Trainingsgruppe und eine
Wertung anlegen, z. B. `Grauwale` und `männlich`. Daraus entsteht **Grauwale – männlich**.
Bei einer einzelnen Nachmeldung wählst du die passende Klasse aus. Beim Excel-Import entstehen
fehlende Klassen automatisch aus den Spalten Trainingsgruppe und Wertung. Vorhandene Daten werden
beim Update automatisch übernommen. Leere Felder erscheinen als Ohne Trainingsgruppe bzw. Ohne Wertung.
Unbenutzte Klassen lassen sich über den Papierkorb löschen; zugeordnete Klassen bleiben erhalten.

Einzelne Nachmeldungen lassen sich in der Teilnehmeransicht erfassen. Eine nachträgliche Bearbeitung
von Namen und Stammdaten ist in dieser Grundversion noch nicht enthalten.

**Teilnehmer löschen:** In jeder Tabellenzeile steht rechts ein Papierkorb. Nach der Bestätigung
verschwindet die Person aus der Teilnehmerliste und der Auswahl für neue Läufe.

- Die Person wird aus allen noch geplanten Läufen entfernt; andere Bahnen behalten ihre Nummer.
  Ist ein geplanter Lauf dadurch leer, wird er als abgebrochen markiert.
- Ist die Person einem aktivierten oder laufenden Lauf zugeordnet, wird das Löschen gesperrt.
  Diesen Lauf zuerst abschließen oder abbrechen.
- Bereits gemessene Zeiten, historische Laufdaten und das Änderungsprotokoll bleiben erhalten.
  Das Löschen entfernt die Anmeldung, ist also keine vollständige Löschung aller personenbezogenen Daten.
- Die Teilnehmer-ID bleibt reserviert. Für eine erneute Anmeldung eine neue ID verwenden.
- Beim Kopieren eines älteren Laufes werden gelöschte Teilnehmer ausgelassen. Sind alle gelöscht,
  kann der Lauf nicht mehr kopiert werden.

Die Änderung wird protokolliert. Bereits angelegte Läufe enthalten zusätzlich eine Kopie der
Teilnehmerdaten, damit historische Ergebnisse nachvollziehbar bleiben.

**Vorhandenes Projekt aktualisieren:** Server beenden und den vollständigen `src/`-Ordner aus
diesem Paket übernehmen. Die `pom.xml` hat sich gegenüber der vorherigen Version nicht geändert.
Eigene Änderungen innerhalb von `src/` (z. B. in `application.properties`) vorher sichern und
anschließend wieder übernehmen. Den vorhandenen Ordner `data/` behalten und dasselbe
Arbeitsverzeichnis verwenden. Tabellen und Spalten werden beim Start automatisch ergänzt;
vorhandene Teilnehmer, Zeiten und Löschmarkierungen bleiben erhalten. Danach auf Laptop und Handys
neu anmelden und die Seiten neu laden (am Laptop gegebenenfalls Strg + F5).
Alle bereits in Läufen verwendeten Disziplinnamen und die Wertungsklassen vorhandener Teilnehmer werden automatisch übernommen.
Falls du eine eigene `application.properties` verwendest: Die neuen Uploadgrenzen sind 25 MB pro
Datei und 26 MB pro Anfrage; Excel bleibt auf 5 MB, Sicherungsdateien auf 20 MB begrenzt.

## 5. Einen Lauf durchführen

1. Unter **Disziplinen** die gewünschten Disziplinen mit Strecke anlegen, z. B. `50 m Freistil`.
   Unter **Läufe** anschließend die Disziplin aus dem Auswahlfeld wählen. Für eine vollständige
   Gesamtwertung alle vorgesehenen Disziplinen einer Wertungsklasse vorab in Läufen einplanen.
2. Optional eine Bezeichnung vergeben und die Trainingsgruppe als Auswahlfilter setzen.
3. Ein bis drei Bahnen mit Teilnehmern besetzen. Freie Bahnen bleiben leer.
4. **Lauf anlegen**, anschließend beim geplanten Lauf **Aktivieren** anklicken.
5. Die drei Bahnansichten zeigen jeweils ihren Schwimmer. Starter und besetzte Bahnen bestätigen
   nach erfolgreichem Zeitabgleich **Bereitschaft**.
6. Sobald alle erforderlichen Stationen bereit und verbunden sind, wird der Startbutton freigegeben.
7. Der Starter pfeift und drückt gleichzeitig **START**. Der Laptop bestätigt den Start.
8. Jede Bahn drückt beim Anschlag einmal **STOPP**. Der Tastendruck wird auf dem Handy erfasst.
9. Die Bahn zeigt an, ob die Zeit noch lokal wartet oder am Laptop gespeichert wurde.
10. Sind alle besetzten Bahnen abgeschlossen, ist der Lauf **Beendet**. Die Verwaltung aktiviert
    bewusst den nächsten Lauf.

Nicht gestartete / nicht beendete Schwimmer und Disqualifikationen werden über
**Ergebnisse → Einzelzeiten / Korrekturen → Korrigieren** als DNS / DNF / DSQ eingetragen. Eine Zeitkorrektur benötigt einen Grund.
Das Protokoll enthält alte und neue Werte. Die ursprünglichen rohen Zeitstempel bleiben erhalten.

**Fehlstart direkt beim Starter:** Nach dem Start erscheint der Button **Fehlstart · Lauf wiederholen**.
Ein Druck markiert den bisherigen Lauf als **Fehlstart** und aktiviert sofort eine Wiederholung
mit derselben Disziplin, Bezeichnung und Bahnbelegung. Der Manager muss nichts kopieren.
Alle Uhren stehen wieder auf null; Starter und besetzte Bahnen bestätigen erneut **Bereitschaft**,
dann kann der Starter wieder START drücken.

Der Fehlstart bleibt in der Historie erhalten und zählt nicht zur Gesamtwertung. Bereits eingegangene
Stopps bleiben dort nachvollziehbar. Auf einem Handy noch wartende Ereignisse des Fehlstarts werden
beim Empfang der Wiederholung entfernt. Eine verspätete Stoppnachricht kann die neue Messung nicht
verändern, weil jeder Versuch eine eigene Lauf-ID hat. Eine wiederholt übertragene Fehlstart-Nachricht
erzeugt keine zweite Wiederholung. Der Fehlstart-Button benötigt eine Verbindung zum Laptop und ist
auch nach einem sehr schnell beendeten aktuellen Lauf noch verfügbar, solange kein anderer Lauf
aktiviert wurde. Vor dem Start oder für ältere Läufe kann der Starter ihn nicht verwenden.

Unabhängig davon kann die Verwaltung unter **Läufe → Abbrechen** einen aktiven Lauf abbrechen und
bei Bedarf über **Kopieren** einen neuen Lauf anlegen. Kopien müssen dann in der Verwaltung aktiviert werden.

**Ein laufender Lauf darf höchstens 30 Minuten dauern.** Diese Grenze ist in `application.properties`
über `app.max-race-seconds` einstellbar. Die Bereitschaft muss nach zwei Minuten Warten erneut bestätigt werden.

## 6. Zeitmessung und Grenzen

Die Handys synchronisieren ihre lokale monotone Browserzeit durch mehrere WebSocket-Nachrichten
mit einer monotonen Serverzeit. Aus vier Zeitstempeln werden Umlaufzeit (RTT) und Zeitversatz geschätzt.
Es wird die Probe mit der kleinsten RTT aus einem kurzen Zeitfenster verwendet. Beim Bestätigen der
Bereitschaft wird der Zeitversatz für diesen Lauf eingefroren.

Start und Stopp werden bei `pointerdown` auf dem jeweiligen Gerät erfasst, nicht erst beim Eintreffen
der Nachricht auf dem Laptop. Die Anzeige wird lokal aus derselben Startreferenz berechnet.
`crypto.getRandomValues` erzeugt Ereignis-IDs auch auf einer normalen HTTP-LAN-Adresse.

Für die Startfreigabe sind mindestens drei lokale Abgleichproben, ein aktueller Verbindungsstatus
und eine RTT von höchstens 100 ms vorgesehen. Der Grenzwert steht in `app.max-roundtrip-ms`.
Die angezeigte RTT und der gespeicherte Abgleichindikator sind **keine garantierte Messgenauigkeit**.
Asymmetrische Funkwege, Browser-/Touch-Verzögerungen, Uhrdrift und menschliche Reaktion bleiben.
Die Anwendung garantiert weder gleichzeitig gezeichnete Bildschirmframes noch Hundertstelsekundengenauigkeit.

Die Anzeige verwendet Minuten, Sekunden und Hundertstel; intern werden Zeiten in Millisekunden
gespeichert. Für die Anzeige werden Hundertstel abgeschnitten. Diese Darstellungsentscheidung ersetzt
keine gegebenenfalls vorgeschriebene Wettkampf-Rundungsregel.

Während eines Laufes:

- Laptop am Strom betreiben und Ruhezustand ausschalten.
- Handyseite im Vordergrund lassen; Display, Browser und Gerät nicht sperren.
- Energiesparmodus und automatische Bildschirmsperre passend einstellen.
- Die Schaltfläche **Bildschirm wach halten** nutzt die Wake-Lock-API, sofern Browser und HTTPS
  das erlauben. Bei normalem HTTP im LAN stattdessen die Geräteeinstellungen verwenden.
- Wird die Seite während des Laufes ausgeblendet oder eine Pause/Uhrauffälligkeit erkannt, wird
  ein anschließender Stopp als **Prüfen** markiert. Solche Erkennungen sind nicht lückenlos.

Vor dem Vereinswettkampf die konkrete Kombination aus Laptop, Hotspot und Handys testen, einschließlich
nasser Finger und schlechter Funkverbindung. Bei der ersten Veranstaltung zusätzlich Ersatzstoppuhren verwenden.
Die Anwendung ist eine selbst entwickelte Handzeitnahmehilfe; ein Testlauf ersetzt keine Prüfung ihrer Eignung
für eure konkrete Ausschreibung.

## 7. Funkabbrüche, Neuladen und Neustarts

**Kurzer Funkabbruch bei weiter geöffneter Seite:** Eine bereits vorbereitete Bahn kann weiter stoppen.
Das Ereignis wird vor der Übertragung in `localStorage` abgelegt, bis der Server es bestätigt.
Nach Wiederverbindung erfolgt eine erneute Übertragung. Dieselbe Ereignis-ID wird im Server nur einmal angewendet.
Ein anderer zweiter Stopp kann einen bereits gespeicherten Wert nicht überschreiben.

**Seite neu geladen oder Browser geschlossen:** Der eingefrorene monotone Zeitabgleich des laufenden
Laufes geht verloren. Die Seite sperrt deshalb einen neuen automatischen Stopp für diesen Lauf.
Schon lokal gespeicherte Ereignisse bleiben bei normalem Browserspeicher erhalten und werden erneut übertragen.
Privatmodus, gelöschter Webseitenspeicher oder ein Browser-/Gerätefehler können auch lokale Sicherungen löschen.

**Der Server lehnt ein Ereignis ab:** Es bleibt auf dem Handy als prüfpflichtige lokale Sicherung sichtbar,
einschließlich der bereits berechneten Stopptime in Millisekunden. In der Verwaltung kann die Zeit nach
Prüfung manuell übernommen werden. Erst danach **Nach Prüfung entfernen** anklicken.

**Serverneustart:** Fertige Ergebnisse bleiben erhalten. Laufende Läufe werden **Unterbrochen**, die
Startvorbereitung wird wieder **Geplant**. Es wird keine Messung automatisch mit einer neuen Serveruhr fortgesetzt.
Neu anmelden, unterbrochene Ergebnisse prüfen und nötigenfalls einen neuen Lauf anlegen/kopieren.
Noch offene Stopps eines alten Serverstarts werden nicht stillschweigend auf die neue Zeitbasis übertragen.

## 8. Ergebnisse, Zurücksetzen und Sicherung

### Ergebnisansichten

Unter **Ergebnisse → Gesamtwertung → Ergebnisansicht** stehen drei Ansichten bereit:

| Ansicht | Inhalt |
| --- | --- |
| **Nach Wertungsklassen** (Standard) | Je Kombination aus Trainingsgruppe und Wertung eine Rangliste, z. B. Grauwale – männlich |
| **Gesamtliste je Trainingsgruppe** | Alle Wertungen einer Gruppe zusammen, z. B. alle Grauwale |
| **Disziplinvergleich · alle Trainingsgruppen** | Je Disziplin eine gemeinsame Liste über alle Gruppen und Wertungen, schnellste Zeit zuerst |

Im Disziplinvergleich kannst du eine einzelne Disziplin wählen oder alle Disziplinen als getrennte
Listen anzeigen. So lässt sich z. B. Sarahs Platz unter allen Freistilschwimmern anzeigen. Freistil und
Puppenstaffel stehen immer in getrennten Listen. Das Suchfeld findet Personen, Gruppen und Disziplinen;
angezeigte Plätze bleiben die Plätze der vollständigen Rangliste, auch wenn du nur eine Person suchst.

Die Summenwertung addiert die Einzelzeiten; die kleinste vollständige Gesamtzeit gewinnt.

- Für jede Disziplin zählt pro Person die **letzte gültige Zeit**. Maßgeblich ist die Startzeit des
  Versuches; bei identischen oder fehlenden Startzeiten entscheidet die höhere Laufnummer.
- Ein gültiger Versuch gehört zu einem **beendeten** Lauf, hat Status **OK**, eine positive Zeit und
  keine offene Prüfmarkierung. Ein späterer ungültiger oder noch offener Versuch verdrängt keine
  bereits vorhandene gültige Zeit. Manuelle Korrekturen wirken sofort auf alle Ansichten.
- Fehlstarts und abgebrochene Läufe werden ausgeschlossen. Laufende und unterbrochene Läufe liefern
  erst nach vollständigem Abschluss gültige Zeiten. DNS, DNF und DSQ liefern keine gültige Zeit.
- Die erforderlichen Disziplinen ergeben sich aus den bereits geplanten, aktivierten, laufenden,
  beendeten und unterbrochenen Läufen der jeweiligen Klasse bzw. Gruppe. Alle vorgesehenen
  Disziplinen vorab einplanen. Nur im Katalog vorhandene Disziplinen ohne passenden Lauf zählen noch nicht.
- Bei unterschiedlichen Disziplinprogrammen kann jemand innerhalb seiner Klasse vollständig sein,
  in der gemeinsamen Gruppenliste aber noch Zeiten vermissen. Summen verschiedener Programme werden
  nicht als gleichwertige Gesamtleistungen behandelt. Der Disziplinvergleich ist davon unabhängig.
- Unvollständige Personen stehen ohne Platzierung unten; vorhandene Zeiten erscheinen als Teilzeit.
  Im Disziplinvergleich erscheinen nur Personen mit einer gültigen Zeit der jeweiligen Disziplin.
- Zeitgleichheit in Millisekunden ergibt gleiche Plätze, z. B. **1, 1, 3**. Deshalb werden Einzelzeiten
  und Summen in diesen Ansichten mit drei Nachkommastellen angezeigt.
- Es gibt keine zusätzliche automatische Trennung nach Jahrgang. Maßgeblich sind die erfasste
  Trainingsgruppe und Wertung. Gelöschte Anmeldungen sind von den Ranglisten ausgeschlossen;
  historische Einzelzeiten bleiben erhalten.
- Die Summenrangliste ist vorläufig, solange Disziplinen fehlen oder weitere Läufe geplant werden.

**Ansicht als CSV** exportiert die gewählte Klassen-/Gruppenansicht oder den Disziplinvergleich.
Eine ausgewählte Disziplin wird beim Export berücksichtigt. Das Suchfeld schränkt die CSV-Datei nicht ein.
Unvollständige Summenwertungen haben im Export keinen Platz und keine Gesamtzeit.

Unter **Einzelzeiten / Korrekturen** bleiben alle Läufe und Versuche sichtbar. **Einzelzeiten als CSV**
exportiert auch ungültige Versuche einschließlich Laufstatus und Prüfhinweisen zur Nachvollziehbarkeit.
CSV-Dateien verwenden UTF-8 mit BOM und Semikolons; sie sind für Tabellenkalkulationen gedacht.
Zum Wiederherstellen bitte den nachfolgenden JSON-Sicherungsexport verwenden.

### Konfiguration und Daten exportieren

Unter **Daten & Sicherung** die gewünschten Inhalte mit Checkboxen auswählen:
**Teilnehmer, Läufe, Ergebnisse, Disziplinen und Wertungsklassen**. Danach **Sicherung herunterladen**.
Die Datei trägt einen Namen wie `vereinsmeisterschaft-20261017-130000.json` und darf höchstens 20 MB groß sein.

Abhängigkeiten werden automatisch mit ausgewählt und sind dann ausgegraut:
Ergebnisse benötigen Läufe; Läufe benötigen Teilnehmer und Disziplinen; Teilnehmer benötigen
Wertungsklassen. Entferne zuerst das Häkchen beim übergeordneten Inhalt, um eine Abhängigkeit abzuwählen.
Du kannst z. B. ausschließlich Disziplinen oder ausschließlich Wertungsklassen sichern.

**Vorbereiteter Wettkampf ohne Testzeiten:** Das Häkchen bei **Ergebnisse** entfernen und die anderen
Inhalte ausgewählt lassen. Alle geeigneten Läufe werden ohne Start-/Stoppzeiten, Notizen oder
Prüfmarkierungen als **Geplant** gesichert. Fehlstart- und abgebrochene Versuche werden dabei ausgelassen;
gelöschte Anmeldungen werden nicht erneut auf Bahnen eingeteilt. Wiederholte reguläre Läufe sind jeweils
weiterhin eigene geplante Läufe – vor dem Wettkampf bei der Planung berücksichtigen.

**Mit Ergebnissen:** Alle fünf Inhalte auswählen. Zeiten, Ergebnisstatus, Hinweise, historische
Versuche und gelöschte Teilnehmerdatensätze samt reservierten IDs bleiben erhalten.
Ein bei der Sicherung laufender Versuch wird als **Unterbrochen**, ein aktivierter Lauf als **Geplant**
gesichert. Eine laufende Zeitmessung wird bei der Wiederherstellung nie automatisch fortgesetzt.

Gesichert werden die ausgewählten Anwendungsdaten. Servereinstellungen, Passwörter, Stationscodes,
Sitzungen, das Änderungsprotokoll und technische Übertragungsbestätigungen gehören nicht zur Datei.
Eine Wiederherstellung erzeugt einen neuen Protokolleintrag. Die JSON-Datei enthält personenbezogene Daten.

### Alle Wettkampfdaten löschen

1. Falls du den Stand behalten möchtest, zuerst eine Sicherung herunterladen.
2. Einen aktivierten oder laufenden Lauf abschließen oder unter **Läufe → Abbrechen** abbrechen.
3. **Daten & Sicherung → Alles löschen …** wählen.
4. Im Dialog exakt **ALLES LÖSCHEN** eingeben und **Jetzt alles löschen** drücken.

Damit werden alle Teilnehmer einschließlich archivierter Anmeldungen, Läufe, Ergebnisse,
Disziplinen, Wertungsklassen und das Protokoll entfernt. Neue Läufe beginnen wieder mit Laufnummer 1.
Die Anwendung läuft weiter; der Ordner `data/` muss nicht angefasst werden. Heruntergeladene Sicherungen
bleiben auf deinem Gerät. Für ein Update des Programms brauchst du diesen Löschvorgang nicht.

Die Stationen wechseln auf „Warte auf Lauf“. Alte lokale Ereignisse werden nach Erkennen des neuen
Datenbestands verworfen, auch nach Wiederverbindung eines zuvor offline gewesenen Handys.
Das ist vom normalen Serverneustart verschieden: Dort bleiben lokale Sicherungen zur Prüfung erhalten.

### Sicherung wiederherstellen

1. Unter **Daten & Sicherung → Sicherung wiederherstellen** eine exportierte JSON-Datei auswählen.
2. **Datei prüfen** zeigt Erstellungszeit und enthaltene Datensätze an. Diese Prüfung verändert nichts
   und ist bereits vor dem Löschen der aktuellen Daten möglich.
3. Die Wiederherstellung benötigt einen **leeren Datenbestand**. Auch vorhandene Katalogeinträge
   zählen als Daten. Vorhandene Inhalte gegebenenfalls sichern und über **Alles löschen** zurücksetzen.
4. Exakt **WIEDERHERSTELLEN** eingeben und **Sicherung wiederherstellen** drücken.
5. Gesicherte geplante Läufe können danach regulär aktiviert werden; alle Stationen bestätigen erneut Bereitschaft.

Die Datei wird vor der Übernahme vollständig geprüft. Unbekannte Versionen, ungültige Zeiten,
doppelte IDs und fehlende Verknüpfungen werden abgelehnt. Das Einlesen erfolgt atomar: Bei einem Fehler
wird kein Teilbestand angelegt. Vorhandene Daten werden nicht zusammengeführt oder überschrieben.
Alte Handynachrichten können auch bei denselben wiederhergestellten Lauf-IDs keine Messung fortsetzen.

Die lokale H2-Datenbank liegt weiterhin in `data/wettkampf.mv.db`, relativ zum Arbeitsverzeichnis.
Der vorhandene Datenordner bleibt bei Programmupdates erhalten. Falls nach dem Start unerwartet keine
Daten sichtbar sind, das Arbeitsverzeichnis kontrollieren. Für Entwicklung und Tests eine getrennte
Testkopie nutzen.

## 9. Dateien und spätere Änderungen

`DATEIPFADE.md` enthält die vollständige Zuordnung jeder gelieferten Datei zum Projektpfad.
`ALLE_PROJEKTDATEIEN.md` enthält außerdem den gesamten Text-Quellcode mit Pfad über jedem Codeblock,
falls du einzelne Dateien kopieren möchtest.

| Was ändern? | Datei |
| --- | --- |
| Farben, Abstände, große Handybuttons | `src/main/resources/static/css/app.css` |
| Verwaltung / Teilnehmer / Läufe / Ergebnisse | `static/manager.html` und `static/js/manager.js` unter `src/main/resources/` |
| Starter-/Bahnansicht | `static/starter.html`, `static/bahn.html`, `static/js/station.js` |
| Zeitabgleich, lokale Sicherung, WebSocket-Client | `static/js/common.js` |
| Wettkampflogik, Statuswechsel, Zeitvalidierung | `service/RaceService.java` unter dem Java-Basispaket |
| Excel-Spalten und Import | `service/ExcelService.java` |
| Java-Datentypen / JSON-Struktur | `model/Models.java` und `model/BackupModels.java` |
| Sicherung, Zurücksetzen und Wiederherstellung | `service/BackupService.java`, `web/DataController.java`, `static/js/data.js` |
| Tabellen und SQL-Zugriffe | `schema.sql` und `repository/RaceRepository.java` |
| Port / Datenpfad / Zeitgrenzen | `application.properties` |
| Versionsnummern / Maven-Abhängigkeiten | `pom.xml` |

`CREATE TABLE IF NOT EXISTS` in `schema.sql` legt Tabellen nur an. Änderungen an bereits vorhandenen
Spalten benötigen eine gezielte Datenbankmigration. Die hier ergänzte Löschmarkierung wird durch
`ALTER TABLE ... ADD COLUMN IF NOT EXISTS` automatisch nachgerüstet. Ebenso wird die Verknüpfung
eines Wiederholungslaufes mit seinem Fehlstart ergänzt; der Disziplinkatalog wird neu angelegt. Vorhandene Wettkampfdaten nicht einfach löschen.

## 10. Technische Referenzen

- [Spring Boot 3.5 – System Requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [Spring WebSocket API, Thread-Sicherheit und Same-Origin](https://docs.spring.io/spring-framework/reference/web/websocket/server.html)
- [Spring Boot – SQL-Datenbanken](https://docs.spring.io/spring-boot/3.5/reference/data/sql.html)
- [Apache POI – Release 5.5.1](https://poi.apache.org/download.html)
- [MDN – performance.now](https://developer.mozilla.org/en-US/docs/Web/API/Performance/now)
- [MDN – Screen Wake Lock API](https://developer.mozilla.org/en-US/docs/Web/API/Screen_Wake_Lock_API)
- [RFC 5905 – Prinzip von Zeitversatz und Umlaufzeit](https://www.rfc-editor.org/rfc/rfc5905)

Stand des Projektpakets: 17.10.2026.
