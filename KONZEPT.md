# negative-tool – Konzept

Desktop-Tool zum Entkörnen gescannter Negative (JPG) und zum groben Datieren der Ergebnisse für Immich.

## Ausgangslage

- Quelle: Labor-Scans, ein Ordner pro Filmrolle, flach (keine Unterordner)
- Format: JPEG, 4288×6445 (27,6 MP), 8 Bit, Qualität 100, 4:2:0, kein EXIF, kein ICC
- Ziel: Immich, eingebunden als externe Bibliothek

## Stack

- Kotlin/JVM 2.x, JDK 21, Gradle-Wrapper
- GUI: Compose Multiplatform Desktop
- Bildverarbeitung: Bytedeco `opencv-platform` (nur `linux-x86_64`)
- EXIF: Apache Commons Imaging (verlustfreies Schreiben/Entfernen)
- Presets: `kotlinx.serialization`, JSON in `~/.config/scan-degrain/presets/`
- Paket: `.deb` via Compose `packageDeb` (inkl. Laufzeit und OpenCV-Natives)
- Native Mats konsequent per `use {}` / `PointerScope` freigeben

## App

Ein Fenster, „Ordner öffnen“ (= eine Filmrolle), drei Tabs.

### Labor

- Bildauswahl aus dem geöffneten Ordner
- Navigator (Gesamtbild) mit verschiebbarem ROI-Rahmen
- Vorschau nur auf ROI (~1024² px), Zoom 100 % / 200 %
- Schieberegler:
  - `h`
  - `hColor`
  - `templateWindowSize` (ungerade)
  - `searchWindowSize` (ungerade)
  - Blend Original ↔ Ergebnis 0–100 %
- Ansichten: Split mit verschiebbarer Trennlinie, Umschalten Original/Ergebnis
- Debounce per Coroutines (`collectLatest`), veraltete Berechnungen verwerfen
- Anzeige: Zeit pro ROI, Hochrechnung auf ganzes Bild und Ordner
- Preset speichern / laden

### Batch

- Eingabe: geöffneter Ordner
- Ausgabe: explizit gewählter Ordner (≠ Eingabe)
- Ein Preset für alle Bilder
- Pro Bild: `imread` → `fastNlMeansDenoisingColored` → Blend → `imwrite`
- JPEG Qualität 95 %, Sampling 4:4:4
- Dateiname identisch zum Original
- Schreiben in `<name>.tmp` im Ausgabeordner, danach umbenennen; übrig gebliebene `.tmp` werden überschrieben
- Kein EXIF, kein Software-Tag
- Parallelisierung nur OpenCV-intern, Bilder sequenziell
- Vorhandene Ausgabedateien überspringen
- Fortschritt, Restzeit, Abbrechen
- Fehler in der GUI anzeigen, Lauf geht weiter

### Datum

Arbeitet auf dem Ausgabeordner (degrainte JPGs).

- Links: Grid aller Fotos ohne Datum
- Rechts: Gruppenliste
  - Gruppe: Jahr (Pflicht), Monat (optional), Tag (optional)
  - Thumbnail-Leiste der zugeordneten Fotos
- Bedienung (kein Drag-and-drop):
  - Markieren (Grid und Gruppen, jeweils innerhalb eines Bereichs):
    - Klick: nur dieses Foto markieren
    - Strg-Klick: Markierung umschalten
    - Shift-Klick: Bereich vom zuletzt geklickten Foto bis hier markieren
  - Klick auf Gruppe wählt sie aus
  - Button „→ Gruppe“: markierte Fotos aus dem Grid ans Ende der ausgewählten Gruppe
  - Button „← Grid“: markierte Fotos aus ihrer Gruppe zurück ins Grid
  - Buttons „◀“ / „▶“: markierte Fotos innerhalb der Gruppe um eine Position verschieben
  - Gruppe anlegen / löschen (Fotos gehen zurück ins Grid)
  - Große Vorschau per Doppelklick / Leertaste
- Datumsberechnung:
  - Fehlender Monat → `01`, fehlender Tag → `01`
  - Basiszeit `12:00:00` UTC
  - n-tes Foto der Gruppe: Basiszeit + n × 10 s
- „Anwenden“ schreibt sofort in die Dateien, keine Projektdatei:
  - `DateTimeOriginal`, `DateTimeDigitized`, `DateTime`
  - `OffsetTimeOriginal`, `OffsetTimeDigitized`, `OffsetTime` = `+00:00`
  - Tag bereits belegt: neue Fotos ohne Warnung hinter das letzte vorhandene Foto anhängen
  - Aus Gruppe entfernt: Datums-Tags löschen
  - Keine zusätzliche Angabe der Ungenauigkeit, Änderungszeit nicht manipulieren
- Beim Öffnen:
  - EXIF aller JPGs lesen
  - Ohne Datum → Grid
  - Mit Datum → Gruppe pro Kalendertag, Reihenfolge nach Uhrzeit
- Thumbnails: `IMREAD_REDUCED_COLOR_8`, Cache nur im RAM
- Danach in Immich: externe Bibliothek neu scannen

## Allgemein

Es wird nur das geforderte implementiert, nicht mehr. Keine coolen extra Features, strikt minimal, nur was im Konzept steht, nutze die eingebaute kotlin funktionen statt selbst helfer zu bauen.