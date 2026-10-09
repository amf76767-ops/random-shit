# DIHClient

Fabric-Client für Minecraft **1.21.11**. Dieses Repo enthält die Version **6.18.1**.

## Korrektur gegenüber 5.6 (Absturz beim Start)
Schon die 5.6 stürzt beim Start ab, sobald Fabric die Mixins von DIHClient prüft (Fehlermeldung `@Mixin target type
mismatch: net.minecraft.class_310 is not an interface ... ProfilerAccessor`). Der Absturz erscheint scheinbar bei einer
anderen Mod (C2ME, libjf ...), weil die zuerst geladene Mod die Prüfung auslöst. Ursache: In `ProfilerAccessor` und
`SpawnerPieMixin` waren `@Accessor` und `@ModifyVariable` als *RuntimeInvisible* gespeichert. Mixin liest sie nur, wenn
sie *RuntimeVisible* sind, hält deshalb den Accessor für ein normales Interface-Mixin und bricht den Start ab. Der Build
setzt die Annotationen jetzt auf sichtbar und prüft jede gebaute JAR mit `tools/MixinCheck.java`.

## Neu in 5.7

### Update-Abfrage im Spiel
- Beim Start (und danach alle 6 Stunden) fragt der Client die **GitHub-Releases** dieses Repos ab.
- Gibt es eine neuere Version für **dieselbe Minecraft-Version**, erscheint auf dem Titelbildschirm ein Fenster mit
  **Update now / Later / Skip this version**. Im Spiel kommt nur ein Hinweis.
- *Update now* lädt die neue Datei in den `mods`-Ordner (Größe, SHA-256 und `fabric.mod.json` werden geprüft). Beim
  Beenden des Spiels tauscht ein kleines Skript die alte gegen die neue Datei. Danach das Spiel neu starten.
- Einstellungen: `config/dihclient/update.json` (`repo`, `checkForUpdates`, `includePrereleases`, `checkEveryHours`).
- Es werden nur `https`-Downloads von `github.com` und `*.githubusercontent.com` angenommen.

**Eine Version veröffentlichen:** GitHub-Release anlegen, Tag z. B. `v5.8.0`, und die JAR anhängen. Der Dateiname muss
zu `dihclient*.jar` passen und die Minecraft-Version enthalten, z. B. `dihclient-v5.8.0+mc1.21.11.jar`. Wer Releases in
diesem Repo anlegen darf, kann damit Code auf alle Installationen bringen. Das Repo also gut absichern.

### AutoSupervisor (Kategorie Automation, standardmäßig an)
Überwacht die anderen Automation-Module:
- **Gefahr:** bei wenig Leben oder wenn ein Spieler, der nicht dein Freund ist, nahe kommt, werden die Module pausiert.
  Sie starten von selbst wieder, wenn es einige Sekunden sicher war. Was du während der Gefahr selbst einschaltest,
  bleibt unberührt.
- **Hängt fest:** zeigt ein Modul keinen Fortschritt (Status ändert sich nicht und du bewegst dich nicht), wird es neu
  gestartet. Hilft das nicht, wird es mit einer Meldung ausgeschaltet. Module, die absichtlich warten (Status *Idle*,
  *Paused*, *Waiting*), gelten nie als festhängend.
- Alle Werte stehen als Einstellungen im ClickGUI. Welche Module überwacht werden, steht in `Watched` und `Only Pause`.

## Aufbau

| Ordner | Inhalt |
|---|---|
| `base/` | Die ursprüngliche `dihclient-v5.6.jar`, Grundlage des Builds |
| `addon/` | Neuer Code (`src/main`), Tests (`src/test`), Platzhalter für Minecraft-Klassen (`stubs`), Build (`build.sh`, `tools/Patcher.java`) |
| `dist/` | Fertig gebaute JAR |
| `reference-decompiled/` | Der Code von 5.6, dekompiliert. **Nur zum Lesen**, mit Minecraft-internen Namen (`class_310` …), lässt sich so nicht bauen |

Der Originalquelltext von 5.6 lag nicht vor. Deshalb baut `addon/build.sh` die neue JAR so: der neue Code wird gegen die
alte JAR kompiliert, und `Patcher` hängt drei Aufrufe in `DIHClient` und `ModuleManager` ein. Alles andere bleibt
unverändert.

```
cd addon && ./build.sh 5.7.0      # braucht JDK 21 und curl; lädt gson, slf4j und ASM von Maven Central
```
Der Build führt zuerst die Tests aus (`UpdateTests`, `SupervisorTests`). Sie laufen ohne Minecraft.

## Was getestet ist und was nicht
- Getestet: Versionsvergleich, Auswahl des Releases, Download-Prüfungen, Austausch-Skript (Linux), die gesamte
  Entscheidungslogik des Supervisors.
- Geprüft: Alle Minecraft-Aufrufe im neuen Code haben dieselbe Art, denselben Namen und dieselbe Signatur wie
  Aufrufe, die in der 5.6-JAR schon vorkommen. Die eingehängten Aufrufe wurden mit `javap` gegen das Original verglichen.
- **Nicht getestet:** das Spiel selbst. Minecraft und Fabric waren beim Bauen nicht erreichbar. Das Fenster, der
  Supervisor im echten Spiel und der Austausch unter Windows wurden nicht ausprobiert.

## VisualPack (Modul, Kategorie Render)
Schaltet das mitgelieferte Resourcepack `DIH Visuals` (Quelle: `resourcepack/`) per Modul-Schalter an und aus: Stone als
Tiefenschiefer, klares Wasser, bunter Glanz, kleines Totem. Beim Einschalten wird es nach `resourcepacks/DIH-Visuals.zip`
kopiert und die Ressourcen werden einmal neu geladen. Die Minecraft-Aufrufe werden per Reflexion gefunden, ein Fehler
erscheint als Meldung im Spiel. **Nicht im Spiel getestet.**

## Bewegung der Automation-Module (5.8.2)
Die Module laufen, indem sie die Bewegungstasten per Software drücken (`KeyBinding.setPressed`). Mods wie Snappy Tappy
bauen die Eingabe aus der echten Tastatur und übergehen das, der Spieler steht dann still. `InputFallbackMixin` übernimmt
solche Software-Tastendrücke wieder in die Eingabe (echte Tastendrücke bleiben unberührt). Ungetestet im Spiel.
Der Cape-Mixin hat jetzt Priorität 2000, damit das DIHClient-Cape nach Better Capes und WaveyCapes gewinnt.

Update 5.8.4: `KeyBindingSoftMixin` merkt sich in `SoftKeys`, welche Tasten DIHClient-Code per Software drückt (Aufrufer
wird per StackWalker erkannt, 1 s gültig, jede Wiederholung erneuert). `InputFallbackMixin` nutzt das statt des
Tastenzustands des Spiels, den andere Mods zurücksetzen können. Das Log zeigt `[DIH-Debug]`-Zeilen.

## AutoBuild: nur legale Platzierungen (5.9.0)
Jeder Block-Klick des Bauens (`Worker`, `BuildRuntime`) läuft durch `LegalPlace.interact`. Gesendet wird nur, wenn ein Strahl
entlang der echten Blickrichtung zuerst genau die geklickte Blockfläche trifft, höchstens 4,5 Blöcke entfernt. Nichts geht
mehr durch oder hinter Blöcke, von der Rückseite einer Fläche oder außer Reichweite. Außerdem fest abgeschaltet:
*Air Place*, *Legit Fallback*, *Legit Placement aus* und Reichweite über 4,5. Ungetestet im Spiel.

## 6.x: Aufräumen und Reparaturen
- **Entfernt:** AutoPot, MaceCombo, Step, ReverseStep, AutoReconnect, InvManager, SurvivalAlerts, MoneyHud, Xray, Chams,
  DamageNumbers, Waypoints, DIHChat, Friends, Enemies, NewChunks, BaseTraces, Watchlist, PacketBuffer und das Pausen-Menü.
  (Die Module fliegen aus Liste, Tick und Config; die Instanz bleibt für Stellen, die sie per Klasse nachschlagen.)
- **Zusammengelegt:** PacketFly ist ein Modus von Flight (*Packet*); 3x3 Pickaxe ist keine eigene Seite mehr, das Namensfeld
  steht in den Modulen mit *3x3 Pickaxe*-Schalter (`Cleanup`).
- **Neu geschrieben** (`addon/src/override`, gleiche Klassennamen, ersetzen die alten): Jesus, NoSlow (+Cobwebs, Soul Sand),
  Flight, AutoMLG (Platzierfenster 4,2 statt 2,7 Blöcke, schnelleres Abholen), AutoInvTotem (Silent-Modus ohne
  Inventar), DummyPlayer (Schwerkraft, Schaden, Rückstoß), TargetHud (letzter Treffer bleibt sichtbar), AutoLog
  (+Spieler in der Nähe), BetterTablist (neues Aussehen).
- **Performance:** ein Schalter Light / Normal / Heavy. **Profiles** heißt jetzt **Configs**.
- **GUI-Sounds:** eigene Klick- und Schalter-Sounds statt des Vanilla-Klicks (`tools/make_sounds.py`, `GuiSounds`).
- **Build:** `tools/GenStubs.py` erzeugt aus dem Bytecode der alten JAR Platzhalter für alle benutzten Minecraft-Klassen.
  Was ein neues Modul benutzt, muss darin vorkommen, sonst übersetzt es nicht. So bleibt jeder Aufruf geprüft.

### 6.2.0
- **Fullbright** ist jetzt im Resourcepack (`lightmap.fsh`, Schalter `FULLBRIGHT`), also an mit dem Modul VisualPack. Das Modul ist weg.
- **Entfernt:** SafeWalk, BoatNoClip (der Server prüft Bootsbewegungen, ein Client kann das nicht aushebeln).
- **AutoLog:** war im Kreativmodus ausgeschaltet, jetzt nur noch im Zuschauermodus.
- **InventoryMove:** im Überlebens-Inventar zählte vermutlich die Rezeptbuch-Suche als Texteingabe und schaltete das Modul ab.

### 6.3.0: CustomModel (3D-Modelle aus Blender)
Neues Modul **CustomModel** (Kategorie Fun). Es zeigt ein eigenes 3D-Modell statt des Spielermodells: nur bei dir,
bei allen Spielern in der Nähe oder als Statuen. Nur du siehst es (Client-seitig).
- **Dateien:** `.minecraft/dihclient/models/` (beim ersten Start mit Beispiel-Roboter und README). Formate: **.glb / .gltf**
  (Blender: *File → Export → glTF 2.0*, Format *glTF Binary*) und **.obj** (+ .mtl + Bild). `.blend` selbst geht nicht.
- **Unterstützt:** Meshes, Normalen, UVs, Vertex-Farben, Material-Farbe und -Bild (mehrere Bilder werden zu einem Atlas),
  Node-Hierarchie, **Skinning** (Skelett) und **Animationen** (Position/Drehung/Skalierung, linear, step, cubic spline).
  Animationen mit "idle" im Namen laufen im Stehen, "walk"/"run" beim Gehen (anders benennbar in den Einstellungen).
- **Nicht unterstützt:** komprimierte glTF (Draco/Meshopt), Morph-Targets, WebP-Texturen, mehrere UV-Sets, Licht/Kamera.
- **Einstellungen:** Model, Replace (Self / Everyone / Nobody), Height, Rotation, Glow, Animations, Range, Max Triangles;
  Aktionen *Open Models Folder*, *Next Model*, *Reload*, *Place Statue*, *Remove Statues*.
- **Technik:** `dev.dihclient.model3d` (Parser, Skelett, Atlas; reines Java, getestet mit `Model3dTests`; mit den
  Khronos-Beispielen CesiumMan und Fox ausprobiert). `glue/CustomModel` zeichnet mit der Entity-Textur-Ebene, die auch
  Pet benutzt. `CustomModelMixin` schrumpft den Vanilla-Spieler auf fast null. `Render3D` kommt mit zwei neuen Methoden
  aus `addon/src/override`. Beispielmodell: `tools/make_example_model.py`.
- **Nicht im Spiel getestet.** Das Zeichnen im echten Minecraft (Licht, Tiefe, Verstecken des Spielers) konnte hier nicht laufen.

### 6.3.1
- **CustomModel** startet mit **Tung Tung Tung Sahur** (`.obj` aus `models/make_tungtung.py`) als Standardmodell. Die drei Dateien werden bei Bedarf in `dihclient/models` kopiert.

### 6.4.0
- **AutoBuild, Smart Path** (`autobuild/BuildPilot`, Schalter *Smart Path*): Statt geradeaus zum nächsten Block zu laufen, sucht der Client
  zuerst per Dijkstra alle erreichbaren Standplätze (`nav/Nav`: Schritt, Sprung 1 Block, Diagonale, Fall bis *Max Fall*; eine 2 Block hohe Wand
  ist eine Wand) und wählt den Platz, von dem die meisten offenen Blöcke der Ebene in Reichweite *und* Sichtlinie liegen
  (`nav/StandPlanner`, kleiner Abzug für den Weg). Erreicht kein Platz einen Block, geht er zur nächsten erreichbaren Zelle und baut dort (falls an)
  einen Pfeiler. Klappt ein Platz nicht, wird er eine Weile gemieden. Wirft etwas einen Fehler, läuft wieder der alte Code. Das Modul *BuildPath*
  zeichnet die Route (grün: Platz mit Blöcken, orange: so nah wie möglich). Eingehängt per ASM (`Patcher.redirectWalk`): der zweite `walkTo`-Aufruf in
  `BuildRuntime.tick` und der in `tickReposition`.
- **Weniger ruckartig:** `HumanAim` beschleunigt und bremst jetzt (Winkelgeschwindigkeit mit Beschleunigungsgrenze, 60 % des Tempolimits),
  `RotationUtil.approachAngle` bremst vor dem Ziel. Das gilt für alle Automation-Module, die diese Hilfen benutzen.
- **Kreativ:** Startet Kreativ-Flug, während ein Automation-Modul läuft und du selbst nicht Springen gedrückt hast, wird er wieder ausgeschaltet.
- **Entfernt:** Glowstone Macro.
- **VisualPack:** jeder Effekt hat einen Schalter (Fullbright, Invisible Rain, Clear Clouds, Sky Gradient, Colour Glint, Deepslate Stone, Clear Water,
  Round Sun, Small Totem). Das Pack wird aus der Kopie in der JAR ohne die abgeschalteten Teile neu zusammengesetzt (`PackFilter`); der Dateiname
  enthält die Auswahl. Fullbright zeichnet die Lightmap jetzt einfach weiß (vorher konnte `notGamma` bei völliger Dunkelheit NaN liefern). Neu: unsichtbarer Regen.
- **Fake Time** (Misc): Tageszeit nur auf deinem Bildschirm, fest oder mit eigener Geschwindigkeit (`ClientWorld.setTime`, per Reflection).
- **Radar** und **Chunk Radar** neu gezeichnet (runde Scheibe, Sweep, Sichtkegel, Himmelsrichtungen, Pfeile für hoch/tief; Schachbrett, Kacheln, Blickrichtung).
- Nicht im Spiel getestet.

### 6.5.0
- **Scenes** (Render): ein Schalter für die Stimmung: Vanilla, Cozy, Cyber, Horror, Winter, Dreamy, Sunny. Setzt Himmels-, Wolken- und Lichtfarbe
  (neue Shader-Konstanten `LIGHT_TINT`, `SKY_MUL_H/Z`, `SKY_ADD_H/Z`, `CLOUD_MUL/ADD`, die `Look.patch` beim Zusammenbauen des Packs einträgt),
  dazu Tageszeit (Fake Time) und Wetter (Fake Weather). Schaltet VisualPack bei Bedarf ein und gibt alles beim Ausschalten zurück.
- **ModelBuild** (World): macht aus einem Modell aus `dihclient/models` ein Blockbild (`Voxelizer`: Oberfläche dicht abtasten, Farbe aus Textur ×
  Materialfarbe; `BlockPalette`: nächster Block per Farbabstand aus Beton/Wolle/Terracotta/Planken/Sand/Schnee), schreibt `.schem` nach `dihclient/schematics`
  und öffnet die AutoBuild-Vorschau. Einstellungen: Height, Palette, Block Types, Solid. Mit Tung Tung Tung Sahur bei 40 Blöcken ca. 1860 Blöcke, das Gesicht ist erkennbar.
- **Demolish** (Automation): nimmt einen AutoBuild-Bau von oben nach unten ab. Nur Blöcke des geladenen Plans, die noch genau so dastehen. Fragt einmal nach
  (zweites Einschalten innerhalb 20 s), läuft mit dem Pfad-Planer (`PathWalker`: Standplätze mit den meisten Blöcken in Reichweite und Sicht), zielt wie ein Spieler,
  nimmt das beste Werkzeug aus der Hotbar und sammelt am Ende die Drops. Ausschalten pausiert, *Stop* beendet. Der AutoSupervisor pausiert und startet es wieder.
- **Emotes** (Fun): Taste an das Modul binden. Dance, Spin, Backflip, Bow, Hop, Sleep, Wiggle, No, Faint. Alle mit DIHClient auf demselben Server sehen sie, über das
  kostenlose Relay ntfy.sh (gesendet werden Spielername, Emote und ein Hash der Serveradresse als "Raum"; *Share* und *Show Others* schalten das ab).
  Auf dem normalen Körper (Mixin am Ende von `setupTransforms`) und auf dem eigenen 3D-Modell (CustomModel). Die Pose reist im Render-State (`LivingStateMixin`).
  Das Relay ist nicht abgesichert: jemand kann den Namen eines anderen eintragen und ihn tanzen lassen. Mehr geht damit nicht.
- Nicht im Spiel getestet.

### 6.6.0
- **/give ist weg** (Einstellung versteckt und immer aus; `LegalPlace.enforce` setzt es vor jedem Tick zurück).
- **Schematic-Browser** (`SchematicBrowserScreen` aus `addon/src/override`): neuer Reiter **OPTIONS** mit den wichtigsten AutoBuild-Einstellungen
  (Schalter, Zahlen mit - / +, Auswahl wie Mode/Order/Sneak Place), die auch im Modul geändert werden. Unter den Knöpfen eine zweite Reihe
  mit Schnellschaltern: WALK, SMART PATH, PILLAR, HUMAN, RESTOCK, FIX WRONG, CLEAR AREA. Entwurf des Aussehens: `gui-mockups/browser-optionen.png`
  (kein echter Spiel-Screenshot).

### 6.7.0: Module aufgeräumt
Zusammengelegt wird ohne Eingriff in den Code der alten Module (`glue/Merge`, `Folds`, `Hubs`, `MergeBus`): das alte Modul verlässt die Liste (GUI, Config, Tasten),
bleibt aber am Leben; seine Einstellungen wandern in das neue Modul (bei gleichem Namen mit Vorsatz, zum Beispiel "Tunnel Reach"). Der `MergeBus` (unsichtbar, immer an)
tickt und zeichnet die eingebauten Module weiter. Ob so ein Teil läuft, folgt dem neuen Modul (`Link`). Alte Config-Werte werden vor dem Laden umgezogen (`ConfigMigration`,
Hook in `ConfigManager.apply`), ohne neue Werte zu überschreiben.
- **Miner** = AutoMine + Tunnel (Modus Ores / Tunnel).
- **Goto** = Goto + ElytraBot (Modus Walk / Elytra), dazu der Schalter *Safe Route* (SafeRoute).
- **Scaffold** = Scaffold + SmartBridge (Modus Classic / Smart).
- **AutoArmor** hat den Schalter *Mend* (AutoMend).
- **AutoBuild** hat die Auswahl *Source*: Schematic / MapArt / Model / Demolish, dazu den Schalter *Hotbar Refill* (AutoRestock). Der Schematic-Browser zeigt im Reiter
  OPTIONS bei MapArt / Model / Demolish die passenden Einstellungen und Knöpfe.
- **Entfernt:** AutoTrade, StashSorter (die Aufgabe `scan/sort/deposit` der TaskQueue benutzt die Engine weiter, aber ohne eigenes Modul).
- **FlipFinder heißt jetzt AutoFlipper** (der alte Name findet das Modul weiter, die Einstellungen ziehen um).
- Nicht im Spiel getestet. Die Namen und Reihenfolgen in der Config ändern sich; bei Problemen alte `config.json` zurücksetzen.

### 6.8.1
- Die GUI-Themen Larp und DIH aus 6.8.0 sind wieder draußen (der Auftrag waren nur Entwürfe). Die Bilder liegen weiter in `gui-mockups/` (`thema-*.png`, `themen-uebersicht.png`).

### 6.9.0: Module aus einem anderen Client
Aus einem anderen Client (GPL-3.0) nach DIH übernommen. Alles ist nur gebaut und mit Tests der reinen Logik geprüft, **nicht im Spiel getestet**.
- **Crystal Aura** und **Crystal Optimizer** (Combat). Crystal Aura pausiert, solange Crystal Macro an ist, und schont DIH-Freunde.
- **Spear Swap** (Lunge-Speer, die Modul-Taste ist die Lunge-Taste), **FastXP**, **Hover Totem** (Combat).
- **Spawner Protect** (Misc): baut Spawner in die Enderkiste ab, wenn die Spieler-Abbau-Schwelle erreicht ist. Webhook raus, Meldungen laufen über die DIH-Benachrichtigungen.
- **Player Bypass** (Basefinding) ersetzt das alte Modul gleichen Namens (Licht-Daten statt Aktivitäts-Alarm; alte Einstellungen werden verworfen). **Prime Chunk Finder** neu.
- **Anti Vanish**, **Staff List**, **No Inv Leak** (Misc). Staff List nutzt eine fest eingebaute Namensliste und macht keine Netzwerkaufrufe. No Inv Leak: "Stream Only" geht nur unter Windows 10 (2004+), sonst ersetzt es nur Items im Spiel.
- Technik: `dev.dihclient.port.PacketBus` (empfangene Pakete), neue Mixins in `mixin/port/`, Bau gegen das echte Minecraft (`tools/README-mc-int.txt`), Notizen in `addon/PORTING.md`.

### 6.9.1: Accounts-Menü
- Der **Accounts**-Knopf im Titelbildschirm tat seit 1.21.9 nichts (der Klick war an der falschen Klasse eingehängt). Jetzt geht er wieder auf und öffnet ein Menü wie bei Meteor: Liste der gespeicherten Logins, **Add account** zeigt die Login-Arten.
- Login-Arten: **Microsoft** (Code auf microsoft.com/link), **Cracked** (nur ein Name, mit Zufallsname; nur für Offline-Mode-Server und Einzelspieler), **Session token**, **Refresh token**. **Original** springt zurück auf den Account, mit dem das Spiel gestartet wurde. Doppelklick auf einen Eintrag loggt ein.
- Der Wechsel funktionierte in 1.21.11 nie (der Session-Konstruktor hat 5 statt 6 Parameter). Jetzt werden neben der Session auch die Chat-Signaturschlüssel, der Report-Kontext und die Social-Interactions neu gebaut, sonst kickt ein Online-Server mit "Invalid signature for profile public key". Nicht im Spiel getestet.
- TheAltening ist nicht dabei: es braucht einen eigenen Auth-Server für das Spiel und lässt sich ohne Test nicht sicher einbauen.

### 6.10.0: Config-Dock, Spotify HUD, Discord Presence
Nicht im Spiel getestet.
- **Config-Dock** statt Modul "Profiles": im DIH-GUI (Rechts-Shift) sitzt unten in der Mitte der Reiter "Configs". Er klappt nach oben auf: beliebig viele Configs mit Namen (speichern, laden, überschreiben, umbenennen, löschen), Doppelklick lädt. Die alten Slots 1 bis 3 erscheinen als "Slot n". "Share code", "Import code" und "Import all" sind jetzt hier. Pro Config lassen sich Server eintragen (**+ This server** oder **+ Address…**); beim Beitritt lädt der Client dann die Config automatisch. Ein Server gehört zu einer Config, bei mehreren Treffern gewinnt der genauere Servername (play.example.net vor example.net). Die Liste steht in `dihclient/configs.json`.
- **Spotify HUD** (Client): Titel, Künstler, Cover, Fortschritt und synchronisierte Songtexte. Nur Windows (Medien-Sitzung von Windows, Ersatz: Fenstertitel von Spotify). Netzwerk: nur `lrclib.net` für die Texte (Titel, Künstler, Album, Dauer); Schalter "Lyrics Online". Cache in `config/dihclient/lyrics-cache`. Auf Linux und Mac tut das Modul nichts.
- **Discord Presence** (Client): zeigt "Playing Minecraft" in deinem Discord-Status, nur über den lokalen Discord-Kanal. Server-Adresse standardmäßig **aus**. Dafür brauchst du eine eigene Discord-Anwendung (discord.com/developers, New Application): ID und Bildname in den Einstellungen eintragen. Ohne ID zeigt das Modul nichts an.

### 6.11.0: Donut-Module und alles auf Englisch
Nicht im Spiel getestet. Aus einem anderen Client (GPL-3.0) nach DIH übernommen; DIH hat keine Donut-Kategorie, die Module liegen in der passenden DIH-Kategorie.
- **Movement:** Donut NoFall, Trident Boost, Air Stuck.
- **Player / Misc:** Donut Speed Mine, Anti Trap, Auto Relog, Riptide, Trident Util, Fake Stats (Name und Werte leer = unverändert; der Werbetext als Standard ist raus), Player Detection (Alarme laufen über Discord Alarm).
- **Render / Basefinding:** Spawner Nametags, Amethyst Bypass (Methoden Bypass / Light / ANBS+ Scan).
- Auf Servern mit Anti-Cheat (z. B. Grim) sind **Donut NoFall, Trident Util, Trident Boost, Air Stuck und Donut Speed Mine** vermutlich erkennbar. Riptide und Auto Relog sind unauffälliger. Fake Stats und Amethyst Bypass senden nichts.
- **Alles auf Englisch:** die letzten deutschen Meldungen (VisualPack, Debug-Zeilen, Pack-Beschreibung) sind übersetzt, die deutsche Sprachdatei `de_de.json` wird nicht mehr mitgeliefert. Diese README bleibt deutsch.

### 6.12.0: Eigene Kategorie "Donut"
Die Donut-Module haben jetzt ein eigenes Fenster im GUI (neben Basefinding): Air Stuck, Amethyst Bypass, Anti Trap, Anti Vanish, Auto Relog, Donut NoFall, Donut Speed Mine, Fake Stats, Player Detection, Riptide, Spawner Nametags, Spawner Protect, Staff List, Trident Boost, Trident Util. Modul-IDs, gespeicherte Einstellungen und Tasten bleiben gleich (die Kategorie wird nicht gespeichert). Das Fenster bekommt beim ersten Öffnen einen freien Platz rechts neben den anderen. Nicht im Spiel getestet.

### 6.13.0: Module aufgeräumt (zweite Runde)
Nicht im Spiel getestet.
- **Auto Relog** ist jetzt ein Schalter "Relog" in **Auto Log** (mit eigener Y-Höhe und Verzögerung).
- **Hover Totem** ist weg: **Auto Inv Totem** kann das schon (Totem im offenen Inventar per Hover und Taste, plus Hotbar-Slot).
- **Air Stuck** ist ein Typ von **Blink**: Einstellung "Type" = Blink oder Freeze. Der Name und die gespeicherten Einstellungen von Blink bleiben.
- **Storage ESP** und **Block Notifier** sind Schalter ("Storage", "Notify") in **Block ESP**.
- **Entfernt:** AutoWalk, Disco Mode, Drunk Mode und BowAimbot. BowAimbot sollte den Pfeil steuerbar machen; das geht nicht, weil ein abgeschossener Pfeil allein vom Server berechnet wird.
- Gespeicherte Einstellungen und Tasten der zusammengelegten Module werden übernommen.

### 6.14.0: Packet Log + Spawner Reader
Aus einem Meteor-Addon übernommen (`addon/src/main/java/dev/dihclient/port/packets/`), neu geschrieben für die DIH-Modul-API.
- **Packet Log** (Misc): liest gesendete und empfangene Pakete und gibt sie in Chat und `latest.log` aus. Nur lesen, nie ändern.
  Filter *All / Whitelist / Blacklist*; die Paketliste ist ein Text mit Paket-IDs, Teile reichen (`move` trifft alle Move-Pakete).
  Chatzeilen werden gesammelt und pro Tick nur wenige ausgegeben. Feldnamen erscheinen mit den Intermediary-Namen des laufenden Spiels.
- **Spawner Reader** (Basefinding): meldet Spawner unter *Max Y* aus Chunk-, Block- und Section-Paketen, je einmal, und
  zeichnet Chunk-Rahmen oder Strahl. Block-Entity-Pakete haben keinen eigenen Schalter mehr (der Chunk-Scan deckt sie ab).
- Test: `PacketLogTests` (Listen-Logik). **Nicht gebaut und nicht im Spiel getestet**: in diesem Archiv fehlen `base/dihclient-v5.6.jar`
  und `/tmp/mc-int.jar`, ohne die `build.sh` nicht läuft.

### 6.15.0: Zoom
Aus einem anderen Client (GPL-3.0). Nicht im Spiel getestet.
- **Zoom** (Render): Taste halten (Standard **C**, im GUI änderbar) zoomt hinein, das Mausrad ändert den Zoom, die Maus dreht langsamer je näher du bist. Einstellungen: Zoom, Smoothness, Scroll, Lower Sensitivity, Cinematic Camera. Das Modul hat keinen An/Aus-Schalter, die Taste wirkt nur, wenn kein Bildschirm offen ist.

### 6.16.0: AutoBuild plant die ganze Route und hat weniger Optionen
Nicht im Spiel getestet.
- **Plan First** (neu, standardmäßig an): Sobald ein Build startet, wird er kurz angehalten und der ganze Weg über alle Ebenen berechnet: die wenigsten Standplätze, von denen die meisten Blöcke erreichbar sind (Reichweite und Sichtlinie), in der kürzesten Reihenfolge. Fertige Ebenen zählen dabei als Boden für die nächsten. Danach folgt der Build diesem Plan; was der Plan nicht abdeckt (übersprungene Blöcke), löst wie vorher der eigene Algorithmus. Eine Meldung zeigt: Stops, Laufweg, geschätzte Zeit und wie viele Blöcke nur mit Pillar oder Gerüst erreichbar sind.
- **Plan Route** (Knopf): berechnet die Route des geladenen Builds sofort und zeichnet sie in der Welt (ein Punkt je Standplatz, Linien dazwischen, Farbe je Ebene).
- **Weniger Optionen im Modulmenü:** AutoBuild zeigt dort nur noch die Hauptoptionen (Source, Mode, Order, From/To Layer, Reach, Walk, Smart Path, Plan First, Hotbar Refill, Render, Progress HUD und die Hauptknöpfe). Alles andere steht im **OPTIONS-Reiter des Schematic-Browsers** (Knopf "More Options" im Menü öffnet ihn direkt). Die gespeicherten Werte bleiben.

### 6.16.1: AutoBuild springt nicht mehr dauernd gegen Wände
Nicht im Spiel getestet.
- Der "Smart Path" gab nach **einem** Fehler für die ganze Sitzung auf und überließ das Laufen der alten Methode (die gegen Wände springt). Jetzt gibt es einen neuen Versuch nach 10 Sekunden (nach 5 Fehlern hintereinander bleibt die alte Methode).
- Findet die Suche keinen Weg, sucht sie danach eine Minute lang viel weiter (Radius 64, 45 000 Knoten statt 28 und 9 000), bevor die alte Methode übernimmt.
- Die alte Methode darf eine Wand mit Stützblöcken überklettern. Springt sie nur dagegen (keine Blöcke zum Klettern oder nach 2 Sekunden immer noch fest), bleibt der Spieler stehen und meldet "Blocked by a wall: no way around found" (auch in `latest.log`), statt endlos zu springen.
- Findet die Suche gar kein Ziel, sucht sie nicht mehr in jedem Tick neu, sondern wartet 15 Ticks.

### 6.17.0: Natürliche Blockänderungen und Team-Codes
Nicht im Spiel getestet.
- **Natürliche Änderungen zählen nicht als falsch:** Wird ein geplanter Dirt-Block von selbst zu Gras, Podzol oder Myzel, gilt er als fertig; "Fix Wrong Blocks" baut ihn nicht mehr ab. Gleiches gilt für Ackerland, das austrocknet, Kelp und Ranken, die wachsen, Stiele, die sich anheften, Korallen, die absterben, und Kupfer, das oxidiert (nicht bei gewachstem Kupfer). Umgekehrt (Gras geplant, Erde in der Welt) bleibt es falsch.
- **Team-Codes** (AutoBuild-Menü: **Team Code**, **Create Team**, **Join Team**, **Leave Team**):
  - *Create Team*: Schematic im Browser wählen und die Vorschau mit Enter starten, dann *Create Team*. Es erscheint ein Code (zum Beispiel `K7QD-3MWP`) und wird in die Zwischenablage kopiert.
  - Auf den anderen Accounts (Alts) den Code unter *Team Code* eintragen und *Join Team* drücken. Sie bauen dasselbe Schematic an derselben Stelle.
  - Die Arbeit wird in Streifen geteilt (einer pro Mitglied, entlang der längeren Seite, alle Ebenen). Ist ein Mitglied mit seinem Streifen fertig, baut es den Rest des ganzen Schematics mit. Kommt jemand dazu oder geht, werden die Streifen neu aufgeteilt.
  - Die Spiele finden sich über einen Ordner im Benutzerverzeichnis (`~/.dihclient/teams`). **Es geht nur auf demselben Computer** und es wird nichts über das Netzwerk gesendet. Beitreten geht nur auf demselben Server und in derselben Dimension, und die Schematic-Datei muss dieselbe sein (Prüfsumme).

### 6.18.0: Glass-Theme und neue GUI-Sounds
Nicht im Spiel getestet.
- **Theme "Glass"** (ClickGUI → Theme, beide Layouts): durchsichtige dunkle Flächen über dem weichgezeichneten Spiel, weißer Text, runde Ecken, weiche Blau-Violett-Akzente, sehr leichter Schleier. Mit **Blur** an sieht es am besten aus (Blur steht in den ClickGUI-Einstellungen).
- **Sound Set** (neu, ClickGUI): **Glass** (Standard) mit einem kurzen Glas-Tipp beim Klicken, einem steigenden Zweiklang beim Einschalten und einem fallenden beim Ausschalten, oder **Soft** (die alten Sounds). Die Klänge entstehen im Code (`addon/tools/make_sounds.py`): Glas hat Obertöne, die keine ganzzahligen Vielfachen sind, daher der "gläserne" Klang. "Click Sound" und "Click Pitch" gelten für beide Sets.

### 6.18.1: Absturz beim Start behoben (6.17.0 und 6.18.0)
- **6.17.0 und 6.18.0 stürzen beim Start ab** (`VerifyError: Expecting a stackmap frame at branch target`): Der Patch für "Dirt zu Gras ist nicht falsch" hat einen Sprung in `BuildRuntime.statusOf` eingebaut, dem die Java-Prüfung ein "Stack-Map-Frame" abverlangt, und das fehlte. 6.18.1 bringt das Frame mit.
- **Damit das nicht wieder unbemerkt passiert:** `build.sh` prüft jetzt jede Klasse der fertigen Jar so, wie es die JVM beim Spielstart tut (`tools/VerifyClasses.java`; die alte 6.18.0 fällt dabei genau mit diesem Fehler durch), und der Patcher bricht ab, wenn ein von ihm eingefügter Sprung kein Frame hat.

### 6.19.0: Neue Glass-GUI (nach dem Screenshot)
Nicht im Spiel getestet.
- **ClickGUI → Theme → Glass** öffnet jetzt ein eigenes Layout statt der alten Fenster: kein Fenster, nur durchsichtige Karten über dem weichgezeichneten Spiel, weißer Text.
  - **Links:** eine Karte pro Modul (Name, weißer Schalter, Zahnrad). Klick = Modul wählen, Schalter = ein/aus, Rechtsklick = ein/aus, Mittelklick = Taste belegen.
  - **Rechts:** Titel des gewählten Moduls, Trennlinie, darunter die Einstellungen als Zeilen (graues Symbol, weißer Name, rundes Feld rechts, zum Beispiel `Inter`). Enum = Klick wechselt (Rechtsklick zurück), Zahl = Feld mit Füllung, ziehen, Farbe = Feld mit Farbwähler, Text = Feld zum Tippen, Liste = öffnet den Ziel-Auswahlbildschirm. Ganz oben stehen **Keybind** und **Toast**.
  - **Oben:** Kategorien als Reiter und das Suchfeld (Tippen sucht auch ohne Klick). Unten rechts **HUD Editor**.
  - Config-Leiste (Rechts-Shift-Panel unten) und Sounds gehen weiter. **Blur** und **GUI Scale** in den ClickGUI-Einstellungen gelten auch hier. Das Setting **Layout** (Modern/Meteor) gilt für Glass nicht; die anderen Themes bleiben wie sie waren.

### 6.19.1: Glass-GUI Klicks getauscht, AutoBuild wechselt den Platz
Nicht im Spiel getestet.
- **Glass-GUI:** Linksklick auf eine Karte schaltet das Modul ein/aus, Rechtsklick öffnet die Einstellungen (wie in den anderen GUIs). Mittelklick belegt weiter die Taste, der Schalter und das Zahnrad bleiben.
- **AutoBuild:** Bleibt der Bau länger an einer Stelle hängen (über 7 Sekunden im Umkreis von 3,5 Blöcken ohne neuen fertigen Block, oder 45 Sekunden ohne Fortschritt), werden die offenen Blöcke im Umkreis von 6 Blöcken für eine Weile zurückgestellt. Er baut zuerst den Rest der Ebene und kommt später (nach 1 Minute, bei jedem weiteren Mal länger) zu den zurückgestellten Blöcken zurück. Bleibt nichts anderes übrig, versucht er sie sofort wieder. Die Meldung im Status lautet "Too long at one spot: moving on", und im `latest.log` steht, wie viele Blöcke es waren. Auch die alte Laufmethode zielt in dieser Zeit auf den nächsten nicht zurückgestellten Block.

### 6.20.0: Nur noch drei GUI-Stile
Nicht im Spiel getestet.
- Die Themes **Classic, Neon Circuit, Frost Glass und Pixel Forge** sind entfernt, ebenso das Setting **Theme**. Es bleibt ein Setting **ClickGUI → Style** mit drei Werten: **Modern** (ein Fenster mit Seitenleiste), **Meteor** (ein Panel pro Kategorie) und **Glass** (die neue durchsichtige GUI). Modern und Meteor haben die normalen Farben mit dem einstellbaren **Accent**.
- Alte Configs werden übernommen: Das alte Layout (Modern/Meteor) wird zum Style, und war als Theme Glass gewählt, wird der Style Glass.

### 8.0.0: Weicher statt pixelig, Configs-Leiste in Glass
Nicht im Spiel getestet.
- **Runde Ecken sind jetzt geglättet** (vorher gestuft). Das gilt für alle Karten, Felder, Schalter, Rahmen und Schatten im GUI und im HUD.
- **Schrift:** GUI und HUD zeichnen den Text in der glatten Schrift **Inter** (Regular und Fett; Lizenz SIL OFL, `assets/dihclient/font/OFL-Inter.txt`) statt in der Pixelschrift des Spiels. Zeichen, die Inter nicht hat (zum Beispiel ⚙ oder ▲), kommen weiter aus der normalen Schrift. Wenn die Schrift bei dir falsch aussieht oder verrutscht: **ClickGUI → Smooth Font** ausschalten, dann ist alles wie vorher.
- **Configs-Leiste unten** hat im Style **Glass** jetzt auch durchsichtige Karten, Felder und Knöpfe. In Modern und Meteor bleibt sie wie sie war.
- Versionsnummer ab jetzt 8.x.

### 8.1.0: Lag Finder, Spear Swap repariert, BetterTablist in Glass, Namen entfernt
Nicht im Spiel getestet (die Logik des Lag Finders ist mit Tests geprüft).
- **Lag Finder** (neues Modul, Kategorie Basefinding): Findet Orte, an denen der Server laggt, obwohl dort nichts los ist. Das kann eine große versteckte Basis oder Farm unter dem Boden sein, denn ein Server tickt nur, was in der Nähe eines Spielers liegt.
  - Er misst die TPS des Servers aus der Spielzeit, die der Server jede Sekunde schickt, und merkt sie sich an dem Ort, an dem du stehst. Benachbarte Bereiche mit deutlich weniger TPS als sonst werden zu einer **Lag-Zone** (Meldung, Säule und Beschriftung in der Welt, auf Wunsch eine Linie dorthin).
  - Proben in einer Menschenmenge (mehr als 25 Wesen in 48 Blöcken), kurz nach einem Teleport oder beim schnellen Fliegen zählen nicht, weil das Lag dort einen offensichtlichen Grund hat.
  - Einstellungen: Lag Threshold, Min Samples (Sekunden pro Bereich), Crowd Limit, Ignore Fast Travel, Notify, Markers, Tracers, Color. Knöpfe: Show Zones, Clear. Das Modul zeigt die aktuelle TPS in der Modulliste.
  - Grenzen: Der Client sieht nur die TPS des ganzen Servers, nicht den Ort des Lags. Die Zone ist deshalb die Schätzung, wo du standest, als die TPS fielen, und kein genauer Punkt. Eine Hintergrundlast des Servers kann das Bild verwischen, darum braucht es mehrere Sekunden pro Bereich und ein bisschen Herumlaufen.
- **Spear Swap:** Hat jetzt keinen Schalter mehr und reagiert nur auf seine Taste (vorher musste das Modul zusätzlich eingeschaltet sein, sonst passierte nichts). Ein Speer ohne Lunge wird auch benutzt, außer **Lunge Only** ist an. Kann die Taste nichts tun, sagt eine Meldung warum (kein Speer in der Hotbar, zu wenig Hunger zum Sprinten, im Wasser, Speer noch nicht bereit, ...).
- **BetterTablist** ist im Style Glass durchsichtig wie der Rest.
- **Namen entfernt:** Der Name des Ursprungs-Clients steht nirgends mehr (Texte, Quellcode-Kommentare, README, Arbeitsnotizen). Die Lizenzangabe (GPL-3.0) bleibt in den Kopfzeilen. **Discord Presence** zeigte bisher den Namen des fremden Clients als Spielname an: Die Standard-ID und das Standard-Bild sind jetzt leer, das Modul braucht eine eigene Discord-Anwendungs-ID (discord.com/developers) und zeigt sonst nichts.

### 8.1.1: Weniger Last durch die glatte Darstellung
Nicht im Spiel getestet, ich konnte die FPS hier nicht messen. Ich vermute die glatten Ecken von 8.0.0 als Ursache, denn jede runde Ecke besteht aus vielen kleinen Flächen.
- Schatten nutzen wieder die einfachen, gestuften Ecken (sie sind durchsichtig, da sieht man den Unterschied nicht).
- Glatte Ecken haben pro Bild ein Budget (ca. 3000 kleine Flächen); ist es verbraucht, werden die restlichen Ecken einfach gezeichnet. Kaum sichtbare Randpunkte werden weggelassen.
- Text wird nur einmal aufbereitet und dann wiederverwendet (vorher jedes Bild neu).
- Neues Setting **ClickGUI → Smooth Corners**. Aus = die Ecken der alten Version; **Smooth Font** aus = die Pixelschrift. Mit beiden Schaltern lässt sich herausfinden, was bei dir bremst.

### 8.2.0: AutoBuild springt nicht mehr gegen Wände, Stützen und Problemstellen im Plan
Nicht im Spiel getestet (der Route-Planer ist mit Tests geprüft).
- **Kein Springen vor 2 Block hohen Wänden:** Der neue Weg springt nur noch bei echten 1-Block-Stufen. Steht eine Wand (Füße und Kopf blockiert) vor ihm, springt er nicht, gibt diesen Standplatz sofort auf und sucht einen anderen. Die alte Laufmethode springt dort auch nicht mehr: Mit **Pillar** an darf sie neben der Wand einen Turm bauen, sonst bleibt sie stehen und meldet "Blocked by a wall".
- **Stützen im Plan:** Beim Planen der Route (Plan First / Plan Route) erkennt er Blöcke, die in der Luft schweben (sie berühren weder die Welt noch eine fertige Ebene noch einen anderen Block, der etwas berührt). Für jede schwebende Gruppe plant er eine Stützsäule unter dem tiefsten Block bis zum Boden ein (höchstens 6 Blöcke). Die Standplätze werden so gewählt, dass auch die Stützen erreichbar sind.
- **Probleme in der Vorschau:** Nach dem Planen zeigt die Vorschau geplante Stützen **gelb** und Problemblöcke **rot** (durch Wände sichtbar). Problemblöcke sind Blöcke, die von keinem Standplatz erreichbar sind, und schwebende Teile ohne Boden für eine Stütze. Die Meldung nach dem Planen nennt beide Zahlen.

### 8.2.1: Hopper werden wieder platziert
Nicht im Spiel getestet.
- **Ursache:** Ein Hopper zeigt in den Block, an den er gesetzt wird. Ein Hopper, der in eine Kiste oder einen anderen Hopper zeigen soll, muss also an diese Kiste oder diesen Hopper geklickt werden. Mit **Sneak Place = Off** (Standard) hat die Platzier-Suche anklickbare Blöcke (Kisten, Hopper, Öfen, Türen ...) nie benutzt, weil der Klick sie öffnen würde. Solche Hopper hatten deshalb keinen gültigen Klick und wurden übersprungen. Silent Rotate ändert daran nichts.
- **Fix:** Sneak Place **Off** verhält sich jetzt wie **When Needed**: Nur für Klicks auf anklickbare Blöcke wird kurz geschlichen, alles andere bleibt wie vorher.

### 8.3.0: Chunk-Finder schneller
Nicht im Spiel getestet, ich konnte hier nicht messen.
- **Alle Block-Finder** (Block ESP, Xray, Spawner Finder, Sus/Tuff Chunk Finder, Block Notifier, Base Traces): Der alte Scanner hat alle geladenen Chunks immer wieder komplett durchsucht und nie aufgehört. Jetzt wird jeder Chunk einmal gescannt. Danach ändert eine Blockänderung nur genau diese eine Stelle in den Ergebnissen. Ein neu geladener Chunk wird gescannt, und einmal pro Minute gibt es zur Sicherheit einen langsamen Durchgang. Die Liste aller Funde wird zwischengespeichert, bis sich etwas ändert.
- **Prime Chunk Finder:** Merkt und vergleicht nur noch die Abschnitte unter Y 0 (dort sucht er; vorher alle 24 Abschnitte jedes Chunks). Unveränderte Abschnitte werden mit einem schnellen Gesamtvergleich übersprungen statt Byte für Byte. Beim Einschalten liest er nur Chunks in Sichtweite (vorher fest 65 × 65 Chunks). Die angezeigten Chunks werden zweimal pro Sekunde statt in jedem Tick neu bestimmt.
- **Player Bypass:** Der Licht-Tracker (läuft immer mit) liest nur noch die tiefen Abschnitte, auf die es ankommt (unter Y 62). Die Markierungen werden viermal pro Sekunde statt in jedem Bild neu berechnet.

### 8.4.0: Sternenhimmel, Shader-Modul, Hut folgt dem Körper
Nicht im Spiel getestet (die Shader sind mit glslangValidator gegen GLSL 330 geprüft).
- **VisualPack – Sternenhimmel ohne Mond:** Nachts zeichnet der Himmel-Shader jetzt viele weiche Sterne in zwei Größen, leicht bläulich und warm gefärbt, dazu einen schwachen violetten Streifen wie die Milchstraße. Zum Horizont hin werden sie ausgeblendet, tagsüber sind sie weg. Der Mond ist durchsichtig (neuer Schalter **No Moon**). Die Sterne hängen am Schalter **Sky Gradient**.
- **Neues Modul Shader** (Render): eigener Glanz auf Werkzeugen, Waffen, Rüstung und allem Verzauberten. **Style**: Aurora, Rainbow, Fire, Ice, Gold, Toxic, Galaxy, Crimson; **Strength** = Helligkeit. **Tools** / **Armor**: Diese glänzen auch ohne Verzauberung. Der Stil wird als kleines Resourcepack erzeugt, das über allen anderen liegt. Ein Wechsel lädt die Ressourcen einmal neu.
- **Hats folgen dem Körper:** Beim Fliegen mit der Elytra, beim Schwimmen und Kriechen kippt Minecraft das Spielermodell nach vorne. Der Hut kippt jetzt genauso mit und bleibt auf dem Kopf, statt über der kleinen Hitbox zu schweben.

### 8.5.0: Amethyst Bypass steckt jetzt im Sus ChunkFinder
Nicht im Spiel getestet.
- Das Modul **Amethyst Bypass** ist aus der Liste entfernt. Sein Erkennen ersetzt den Amethyst-Teil von **Sus ChunkFinder**. Der Schalter heißt weiter **Amethyst** (nicht umbenannt).
- Ist Amethyst an, zählt Sus ChunkFinder keine sichtbaren Amethyst-Cluster mehr (Anti-Xray versteckt sie), sondern nimmt die Funde des Bypass: **Bypass** = 1 Punkt pro Chunk mit Geode, **Light** / **ANBS+ Scan** = 1/4 Punkt pro verstecktem Knospen-/Glühpunkt (höchstens 4 Punkte pro Chunk). Chunks, die nur Amethyst haben, werden auch markiert.
- Die Einstellungen des alten Moduls (**Method**, **Min Cells**, **Tracer**) stehen jetzt im Sus ChunkFinder unter Amethyst und werden aus alten Configs übernommen. Die Markierungen der Geoden werden weiter gezeichnet, solange Sus ChunkFinder und Amethyst an sind.

### 8.6.0: Frame Profiler, Build Log, Netherite Finder schneller
Nicht im Spiel getestet.
- **Frame Profiler** (neues Modul, Client, standardmäßig aus): misst, wie viel Zeit jedes Modul pro Sekunde braucht (Tick, 2D- und 3D-Zeichnen), und zeigt die größten in einem Overlay mit Anteil in Prozent und Millisekunden pro Aufruf (t = Tick, 2d, 3d) und den FPS. Knopf **Report** schreibt die Top 15 ins Log und zeigt die Top 5. Gemessen wird nur die Zeit in den Modulen, nicht das Zeichnen des Spiels selbst: Brauchen die Module zusammen wenig und die FPS sind trotzdem niedrig, liegt es an etwas anderem (Shader, Sichtweite ...). Ist das Modul aus, wird nichts gemessen (pro Aufruf eine einzige Prüfung).
- **Build Log** (AutoBuild-Knopf **Build Log**): Eine kurze Geschichte dessen, was AutoBuild getan hat: neue Wege mit Ziel, "stuck"-Aufgaben, Wände, zurückgestellte Blöcke, wann die alte Laufmethode übernimmt, alle 10 Sekunden ein Statuszeile mit Position, Ebene und Fortschritt. Der Knopf zeigt die letzten Zeilen und kopiert die letzten 60. Die ganze Datei ist `build-log.txt` im Config-Ordner (wird bei jedem Spielstart auf die letzten 2000 Zeilen gekürzt).
- **Netherite Finder:** Der Anti-Xray-Bypass im Modus **Area** hat in jedem Tick über 2000 Stellen neu berechnet und dabei tausende Objekte erzeugt. Die Liste der Stellen wird jetzt nur neu gebaut, wenn du in eine andere 5er-Zelle gehst, und dann abgearbeitet. Das Verhalten ist sonst gleich.

### 8.7.0: Netherite Finder scannt im Hintergrund
Nicht im Spiel getestet.
- Das Lesen der Chunk-Blöcke (die schwere Arbeit) läuft jetzt auf einem eigenen Hintergrund-Thread (`DIHClient-NetheriteScan`, mit etwas niedrigerer Priorität). Der Spiel-Thread bewertet nur noch die fertigen Chunks: freiliegend oder versteckt, Fake-Erze, Gruppen, Meldungen.
- Es sind höchstens 12 Chunks gleichzeitig unterwegs, die nächsten zuerst. Ergebnisse aus einer anderen Welt oder von vor einem Rescan werden verworfen.
- **Sicherheitsnetz:** Liest der Hintergrund-Thread einen Chunk, der sich gerade ändert, und es kommt ein Fehler, wird dieser Chunk noch einmal auf dem Spiel-Thread gescannt. Beim Ausschalten des Moduls endet der Thread.
- **Scan Speed** bestimmt jetzt, wie viele fertige Chunks pro Tick bewertet werden (bisher: wie viele gescannt wurden).

### 8.8.0: Kein Installer mehr, MP3-Decoder im Mod
Nicht im Spiel getestet.
- **Updater:** Der Installer ist ganz entfernt. Das Spiel lädt keine Updates mehr herunter und startet keine Skripte zum Austauschen. Gibt es eine neue Version, zeigt das Update-Fenster (wie bisher auf dem Titelbildschirm) die Neuigkeiten und die Knöpfe **Open GitHub** (öffnet die Release-Seite im Browser, nur Adressen auf github.com), **Later** und **Skip this version**. Die neue Jar legst du selbst in den mods-Ordner. Übrig gebliebene `.part`- und `.jar.update`-Dateien eines älteren Updaters werden beim Start gelöscht.
- **Radio:** Der MP3-Decoder `jlayer-1.0.1.jar` (JLayer, LGPL-2.1, SHA-1 `2bfef7a5a4c9af2184ff74b460b6d7d24349b98a`, identisch mit der Datei auf Maven Central) steckt jetzt im Mod unter `dihclient/jlayer-1.0.1.jar`. Beim ersten Benutzen des Radios wird er in `dihclient/libs` kopiert (die Prüfsumme wird dabei gegen den fest eingetragenen Wert geprüft) und von dort geladen. Es wird nichts mehr heruntergeladen. Die Radio-Streams selbst kommen weiter aus dem Netz.
- Damit startet der Client keine Programme mehr und lädt keinen Code aus dem Netz.

### 8.8.1: Keine Jar-in-Jar mehr, kein eigener Class Loader
Nicht im Spiel getestet.
- Die Jar von 8.8.0 enthielt eine zweite Jar (den MP3-Decoder), die das Radio auf die Platte kopiert und über einen eigenen `URLClassLoader` geladen hat. Genau dieses Muster (eingebettete Jar auspacken und laden) erkennen Virenscanner als "Loader / Stager". Jetzt liegen die Klassen des Decoders (`javazoom.jl.*`, unverändert) direkt in der Mod-Jar und werden vom normalen Class Loader des Spiels geladen. Es gibt weder eine zweite Jar noch einen `URLClassLoader` noch irgendeinen Aufruf zum Starten von Programmen im Code. Lizenzhinweis: `dihclient/licenses/JLayer.txt` (LGPL-2.1).

### 8.9.0: Build-Wächter, Code ohne Kommentare
Nicht im Spiel getestet.
- **Build-Wächter (AutoBuild → Guard):** Pausiert den Bau, wenn es nicht sicher ist, und macht weiter, wenn es wieder sicher ist. Gründe: ein Spieler, der kein Freund ist, kommt näher als **Guard Range** (Standard 32 Blöcke), deine Gesundheit fällt auf **Guard Hearts** (Standard 4 Herzen) oder weniger, oder der Server läuft zwei Sekunden lang unter **Guard TPS** (Standard 12). Mit **Guard Resume** (Standard 5 Sekunden) legst du fest, wie lange es sicher sein muss, bevor es weitergeht. Pausierst du selbst, bleibt deine Pause bestehen. Die Gründe stehen im Build Log. Die Feineinstellungen sind im OPTIONS-Reiter des Schematic-Browsers, im Menü steht nur der Schalter **Guard**. Im Kreativmodus gilt der Gesundheitsgrund nicht.
- **Kein Kommentar mehr im Code:** Alle Kommentare (`//`, `/* */`, `#`) sind aus den Quelldateien des Add-ons, der Tests und der Hilfsskripte entfernt. Geblieben ist die Zeile "Ported from an open-source client (GPL-3.0)" in den übernommenen Dateien. Der Code selbst ist unverändert.

### 8.9.1: HUD viel schneller
Nicht im Spiel getestet.
- **Ursache:** Das HUD zeichnete hunderte 1-Pixel-Rechtecke pro Bild (weiche Ecken, Radar-Kreise, Radar-Sweep, Chunk-Raster). Jedes Rechteck ist beim Rendern ein eigener Zeichenaufruf, deshalb fielen die FPS von 300 auf 80.
- **Weiche Ecken** gibt es nur noch, wenn ein Menü offen ist. Im HUD sind die Ecken gestuft (wenige Rechtecke). Das Budget für weiche Ecken ist kleiner.
- **Radar:** Scheiben werden zeilenweise zusammengefasst, die Kreise haben weniger Punkte, der Sweep hat etwa ein Fünftel der Rechtecke.
- **Chunk-Radar:** Statt eines Schachbretts aus einem Rechteck pro Chunk gibt es eine Fläche und Gitterlinien.
- **Verlauf-Balken** (Akzentleisten) nutzen weniger Streifen.

### 8.10.0: Nur noch Meteor-Stil, Shader auf Hüten
Nicht im Spiel getestet.
- **Nur Meteor-Stil:** Die Stile Modern und Glass sind entfernt. Die Einstellung **Style** im ClickGUI gibt es nicht mehr, der Knopf "Modern Layout" unten ist weg, die Glass-Oberfläche ist gelöscht. Alte Configs mit Modern oder Glass öffnen automatisch das Meteor-GUI.
- **Meteor-GUI verbessert:** Die Kopfzeilen der Kategorien haben einen weißen Glanz oben und eine Akzentlinie unten, die von einer Farbe in die nächste übergeht. Eingeschaltete Module haben statt einer flachen Fläche einen Farbverlauf, der nach rechts ausläuft. Die Verläufe bestehen aus wenigen Streifen, damit die FPS nicht leiden.
- **Shader (Modul Shader):** Neuer Schalter **Hats**. Hüte bekommen einen bewegten Glanz in den Farben des gewählten Stils (Aurora, Galaxy, Fire und so weiter), mit hellen Wellen, die über den Hut laufen. **Strength** gilt auch dafür. Schwerter, Werkzeuge und Rüstung glänzen wie bisher über **Tools** und **Armor**.

### 8.11.0: Mehr und schönere Shader
Nicht im Spiel getestet.
- **8 neue Stile** im Modul Shader: Ocean, Sunset, Emerald, Void, Holo, Plasma, Sakura und Electric (jetzt 16 Stile).
- **Schöneres Aussehen:** Alle Stile haben drei Farbstufen statt zwei, und die Textur hat weiche Wolken (kachelbares Rauschen) unter den Streifen und Funken.
- **Eigener Glanz-Shader:** Der Glanz ändert die Farbe nicht mehr durch den Farbkreis (vorher haben sich alle Stile durchgefärbt und sahen ähnlich aus). Stattdessen funkeln die Zellen, der Glanz pulsiert, eine zweite verschobene Schicht gibt Tiefe, und Holo und Rainbow haben einen Farbsaum an den Kanten. Jeder Stil hat eigene Werte (Void und Electric funkeln stärker, Fire pulsiert).
- Die Hüte-Einstellung nutzt die neuen Farben auch.

### 8.11.1: Sus ChunkFinder ausgetauscht
Nicht im Spiel getestet.
- **Sus ChunkFinder** ist durch die Version aus der Jar `dihclient-v8.9.1mc1.21.11-krypton-suschunkfinder.jar` ersetzt. Ich habe die Jar geprüft: Nur diese eine Klasse unterscheidet sich von unserer 8.9.1, sie ist nicht verschleiert, und sie enthält keine Netzwerk-, Datei-, Prozess- oder Reflection-Aufrufe. Ich habe die Klasse in lesbaren Quelltext zurückübersetzt und aus dem Quelltext gebaut, die fremde Datei steckt nicht in der Jar.
- **Neu:** Simulation Distance (Chunks im Umkreis eines wachsenden Chunks gelten als simuliert und werden nicht markiert), Cave Vines, Bamboo, Bee Nest, Rotated Deepslate (Y 0 bis 60), Scan Speed (Chunks pro Tick).
- **Weg gegenüber unserer Version:** Cocoa, Highlight Blocks, die Punktewertung mit Sensitivity-Formel und der inkrementelle Block-Scanner. Sensitivity ist jetzt die Mindestzahl gewachsener Dinge (1 bis 20). Alte gespeicherte Werte für Cocoa und Highlight Blocks werden ignoriert. Der Amethyst-Bypass liefert weiter Geoden-Daten.

### 8.12.0: Dao Finder, Freund statt Kasten
Nicht im Spiel getestet.
- **Dao Finder (Basefinding):** Sucht versteckte Basen über ihr Licht. Ein Server kann Blöcke unter dem Deepslate verstecken (Anti-Xray), schickt aber oft die echten Lichtdaten mit. Der Finder liest die Blocklicht-Daten jedes geladenen Chunks und zählt Stellen, an denen ein fester Block Licht hat, was ohne Lampe oder Fackel im Gestein nicht vorkommt. Hat ein Chunk so viele Stellen wie **Sensitivity** (Standard 3), wird er markiert. Das Label zeigt die Zahl und die Höhe.
  - Einstellungen: Min Y und Max Y (Standard -64 bis 0, also die Deepslate-Schicht), Sensitivity, Max Chunks, Scan Speed (Chunks pro Tick, wichtig auf großen Servern), Ignore Spawn (Chunks um 0, 0, die nie markiert werden), Keep Range (weiter entfernte Marken werden vergessen), Color.
  - Jeder Chunk wird zweimal geprüft (nach 1,5 und 7 Sekunden), weil die Lichtdaten nach dem Chunk ankommen.
  - Ob Donut die Lichtdaten wirklich mitsendet, weiß ich nicht. Ohne sie findet der Dao Finder nichts.
- **Freund statt Kasten:** Alle Chunk-Marker haben die neue Einstellung **Friend**. Ist sie an, steht über dem markierten Chunk Tung Tung Tung Sahur (7 Blöcke hoch, dreht sich zu dir und wippt) statt des farbigen Kastens. Er steht an der Oberfläche, damit man ihn sieht, auch wenn der Fund tief im Boden liegt. Das Label steht über ihm. Bei **Sus ChunkFinder** und **Dao Finder** ist Friend standardmäßig an, bei den anderen Findern aus. Das Modell stammt aus `dihclient/models` und braucht das Modul CustomModel nicht.

### 8.12.1: Freund nur im Dao Finder
Nicht im Spiel getestet.
- Der Tung-Tung-Tung-Sahur-Marker aus 8.12.0 ist wieder entfernt. Sus ChunkFinder und alle anderen Finder zeigen wie vorher ihre Kästen.
- **Dao Finder → Marker:** `Box` (der normale Kasten) oder `Friend` (Standard). Bei `Friend` steht über dem markierten Chunk ein Bild deines Freundes (6 Blöcke groß, dreht sich zu dir und wippt) an der Oberfläche, das Label darüber. Die Einstellung gibt es nur im Dao Finder.
- Das Bild liegt nur in der gebauten Jar, nicht im Repo (`addon/src/resources/assets/dihclient/friend/` steht in der `.gitignore` und fehlt im Source-Zip). Wer ein anderes Bild will, legt eine Datei `friend.png` nach `.minecraft/dihclient/`, die hat Vorrang.

### 8.12.2: Alter Sus ChunkFinder zurück
Nicht im Spiel getestet.
- Der Sus ChunkFinder aus 8.11.1 (Krypton-Variante) ist wieder entfernt. Es ist wieder unser Sus ChunkFinder mit Punktewertung, Cocoa, Highlight Blocks und dem inkrementellen Block-Scanner (Stand 8.11.0). Der Dao Finder und die übrigen Änderungen bleiben.

### 8.13.0: Fast Break, No Break Delay, Sus ChunkFinder zum Testen
Nicht im Spiel getestet.
- **Fast Break** (World): Blöcke gehen schneller kaputt. **Mode:** `Normal` (Abbaugeschwindigkeit mal **Modifier**, 1,0 bis 5,0), `Haste` (wie ein Haste-Effekt der Stufe **Haste Level**, jede Stufe +20 Prozent) und `Damage` (der Block ist fertig, sobald der Fortschritt **Damage** erreicht, z. B. 0,7). **Safe Limit** (Standard an) hält die Geschwindigkeit in dem, was ein normaler Server annimmt (etwa bis 1,4-fach, Damage nicht unter 0,7); ohne Safe Limit kommen abgebaute Blöcke oft zurück. **Only On Ground** wirkt nur am Boden. **No Break Delay** (Standard an) entfernt die Pause zwischen zwei Blöcken.
- **No Break Delay** (World): eigenes Modul, das nur die Pause nach jedem abgebauten Block entfernt. **Delay** stellt die Pause in Ticks ein (0 bis 5, Spiel: 5).
- **Sus ChunkFinder:** Neue Einstellung **Kelp Min Age** (Standard 25). Kelp aus der Weltgenerierung ist 20 bis 23 alt, nur Kelp, der mit einem Spieler in der Nähe gewachsen ist, hat 25. Zum Testen auf 0 stellen: Dann zählt jeder Kelp, und in jedem Kelpwald sollte eine Markierung erscheinen. Kommt dann nichts, ist der Scan kaputt, kommt etwas, gibt es auf deinem Server einfach keinen Kelp mit Alter 25. Im Info-Fenster des Moduls stehen jetzt gescannte Chunks, wartende Chunks und gefundene Blöcke.

### 8.14.0: 8 neue dunkle Themes, schwebende GUI
Nicht im Spiel getestet. Die GUI wurde mit dem echten Code in einem kleinen Java2D-Vorschauprogramm gezeichnet (`addon/tools/preview/`, Bilder: `gui-mockups/8.14-themes-vorschau.png`); Minecraft selbst war dabei nicht beteiligt.
- **Themes:** ClickGUI → **Theme** hat jetzt 8 dunkle Themes: **Obsidian** (fast schwarz, Silber), **Graphite** (weiches Dunkelgrau, Stahl), **Onyx** (reines Schwarz, Lavendel, sehr runde Ecken), **Ember** (warmes Anthrazit, Kupfer), **Midnight** (tiefes Blauschwarz, Indigo), **Moss** (Grünschwarz, Salbei), **Plum** (Auberginenschwarz, Malve), **Abyss** (dunkles Petrol, Meergrün). Alle Akzente sind gedämpft; kein helles Weiß, kein Neon, kein Eis. Jedes Theme hat eigene Rundung, Schattenstärke, Kopfzeilen-Stil (Linie, Tönung oder Punkt) und Modul-Markierung (Fläche oder Balken). Theme und Farben gelten auch im HUD.
- **Alte Themes weg:** Classic, Neon Circuit, Frost Glass und Pixel Forge (Reste der alten Jar) sowie die Glass-Farbtabelle sind entfernt. Alte Configs mit diesen Namen fallen auf Obsidian zurück.
- **Eigene Akzentfarben:** **Custom Accent** (Standard aus) schaltet **Accent** und **Accent 2** wieder frei; sonst kommen die Akzente vom Theme. **Color Mode** (Static, Gradient, Rainbow) gilt weiter.
- **Schwebende Karten:** Jede Kategorie ist eine frei schwebende, abgerundete Karte mit weichem Schatten, feinem Rand und Abstand zwischen den Zeilen. Der Schatten ist mehrlagig und weich statt gestuft; die Rundungen sind im GUI geglättet (größeres Budget als im HUD). Die Karte, die du gerade ziehst, hebt sich ab (stärkerer Schatten).
- **Animationen:** Beim Öffnen blenden die Karten nacheinander ein und gleiten hoch, beim Schließen blenden sie aus (rund 0,17 s). Hover auf Zeilen, Kopfzeilen und Knöpfen ist weich, die Einstellungen eines Moduls klappen jetzt animiert auf und zu, Schalter schieben sich, und ein Themewechsel blendet alle Farben in rund 0,4 s über. Tooltips blenden ein und aus. Mit **Animations** aus ist alles sofort da, wie vorher.
- **Unten:** Neuer Knopf mit dem Namen des Themes. Linksklick = nächstes Theme, Rechtsklick = vorheriges. Knöpfe und Suchfeld sind jetzt schwebende Pillen mit Schatten.
- **Bauen:** `addon/tools/patch-gui.sh <alte.jar> <neue.jar> <version>` baut nur die GUI-Klassen neu (Skin, Theme, ClickGui, Gfx, MeteorGuiScreen, HudStyle) und tauscht sie in eine vorhandene Jar. Kein Mixin und keine anderen Klassen werden angefasst; `MixinCheck` meldet keine Probleme.

### 8.15.0: No Ghost Blocks, Click Attack
Nicht im Spiel getestet. Baut auf 8.14.0 (Themes, schwebendes ClickGUI) auf.
- **No Ghost Blocks** (World): **Breaking** (Standard an) lässt einen abgebauten Block erst verschwinden, wenn der Server es sagt, damit keine Geisterblöcke entstehen. **Resync** fragt nach jedem Abbau (nach **Resync Delay** Ticks) beim Server nach dem echten Block.
- **Click Attack** (Combat, Standard an): Alle Module, die einen Angriff senden (Kill Aura, Trigger Bot, Crystal Aura, Crystal Macro, Surround, Mace Combo und so weiter), lösen jetzt einen Linksklick des Spiels aus statt selbst den Angriff zu schicken. Das Spiel schwingt, setzt den Cooldown zurück und sendet das Paket wie bei einem echten Klick, was die Lag-Backs beheben soll. Der Klick zielt auf das Ziel des Moduls, nicht auf das Fadenkreuz. Schaltest du das Modul aus, gilt wieder das alte Verhalten.

### 8.16.0: AutoHunter gräbt bei markierten Chunks nach unten
Nicht im Spiel getestet.
- **Dig Down** (AutoHunter, Standard an): Wird ein Chunk markiert (Sus ChunkFinder, Dao Finder, Base Traces und so weiter), geht der AutoHunter zur Mitte des Chunks und gräbt mit Goto bis zur Höhe der Markierung nach unten. Der Dao Finder liefert die genaue Höhe, bei den anderen Findern gilt **Dig Y** (Standard -40, die Base-Schicht). Dann pausiert er mit der Meldung, wo er ist. **Resume** setzt die Suche fort. Wird er beim Graben pausiert (zu wenig Leben, Spieler in der Nähe), macht Resume mit dem Graben weiter. Findet er sich nach drei Versuchen nicht durch, pausiert er mit "dig blocked". Goto gräbt nie neben Lava und nicht durch Kisten oder Türen.
- **Human Mode** (Standard an): Beim Start stellt er Goto auf menschliches Zielen (Human Rotations an, Randomness mindestens 35, Drehgeschwindigkeit höchstens 35). Vor jedem Abschnitt und vor dem Graben wartet er zufällig kurz ("Looking around"), und nach der Landung kommt eine zufällige Zusatzverzögerung dazu.
- Mit Dig Down aus gilt wieder das alte Verhalten (bei einem Fund pausieren).

### 8.17.0: Spinbot
Nicht im Spiel getestet.
- **Spinbot** (Render): Dreht nur dein Spielermodell auf deinem Bildschirm (Dritte-Person-Ansicht, Freecam, Spiegel). Deine Kamera und deine echte Blickrichtung bleiben unverändert, es wird nichts an den Server gesendet. **Mode:** `Spin` (dreht sich mit **Speed** Grad pro Tick), `Jitter` (springt alle **Jitter Interval** Ticks um **Jitter Angle** nach links und rechts) und `Backwards` (schaut entgegengesetzt). **Spin Head:** Kopf dreht mit. Aus = der Kopf schaut weiter, wohin du schaust. **Pitch:** Kopf auf dem Modell nach unten, oben oder wechselnd.
- Hinweis: Andere Spieler sehen dein Modell nur dann gedreht, wenn der Server deine Rotation weitergibt. Dieses Modul ändert deine Rotation für andere nicht. Es wirkt nur auf deinem Bildschirm.

### 8.18.0: Spinbot auch serverseitig
Nicht im Spiel getestet.
- **Spinbot → Server Side** (Standard an): Deine Bewegungspakete tragen jetzt die drehende Blickrichtung, und wenn du stehst, schickt das Modul jeden Tick ein eigenes Rotationspaket. Andere Spieler sehen deinen Kopf drehen und den Körper hinterherdrehen. Deine Kamera bleibt, wo sie ist. Beim Ausschalten bekommt der Server einmal deine echte Blickrichtung.
- **Real While Using** (Standard an): Solange die Benutzen-Taste gedrückt ist (werfen, essen, schießen), bekommt der Server deine echte Rotation, damit Perlen und Pfeile dahin fliegen, wohin du zielst.
- Achtung: Serverseitiges Drehen fällt Anticheats (Aim- und Rotations-Prüfungen) schnell auf und ist auf vielen Servern verboten, auf Donut nach den Regeln wohl auch. Mit Server Side aus bleibt es rein optisch.

### 8.19.0: Ghost Blocks, Ghost Items (nur auf deinem Bildschirm)
Nicht im Spiel getestet.
- **Ghost Blocks** (World): Rechtsklick setzt einen Block nur bei dir (den gehaltenen Block oder den aus **Block**), Linksklick nimmt ihn wieder weg. Der Server und andere Spieler sehen nichts, und jedes Block-Update vom Server ersetzt ihn wieder. **Clear All** und Ausschalten stellen alles wieder her.
- **Ghost Items** (Misc): **Give** legt ein Item (**Item**, **Count**) in einen Hotbar-Slot (**Slot**, 0 = gehaltener Slot), nur bei dir. Der Server kennt es nicht, man kann es nicht benutzen, droppen, verkaufen oder geben, und das nächste Inventar-Update vom Server entfernt es. **Clear** stellt den alten Inhalt wieder her.

### 8.20.0: Fehler behoben, keine neuen Module
Nicht im Spiel getestet.
- **Spear Swap:** Machte nichts, wenn das Modul in der Config als aus gespeichert war (es lässt sich nicht einschalten, und ausgeschaltet läuft sein Tick nicht). Die Lunge-Taste schaltet es jetzt selbst ein.
- **Click Attack** ist kein eigenes Modul mehr, sondern eine Option **Click Attack** (Standard an) in KillAura, TriggerBot, Crystal Aura, Crystal Macro, MaceCombo, Surround, Shield Breaker, AutoClicker, Anchor Macro und Criticals. Sie wirkt nur auf Angriffe, die dieses Modul gerade auslöst. Deine eigenen Klicks laufen wie im normalen Spiel (vorher wurden sie doppelt verarbeitet).
- **Flight:** Neue Option **No Fall Damage** (Standard an). Beim Fliegen sagt der Client dem Server, dass du auf dem Boden stehst, und setzt die Fallhöhe zurück, damit Runterfliegen mit Shift keinen Schaden mehr macht.
- **Momentum:** Neuer Modus **Normal** mit **Multiplier** (1,0 bis 5,0): deine normale Geh- und Sprintgeschwindigkeit mal Multiplier, über den Bewegungs-Attributwert des Spiels, damit Beschleunigen und Bremsen normal bleiben. Beim Fliegen oder Gleiten ist er aus.
- **ClickGUI (Right Shift) laggt nicht mehr so:** Seit 8.14.0 durfte die GUI pro Bild bis zu 24.000 einzelne Pixel für weiche Ecken zeichnen. Das Budget ist wieder 2.500. Wenn es noch ruckelt: **Blur** im ClickGUI aus.
- **Schnelleres Joinen:** Der Licht-Tracker von **Player Bypass** lief bei jedem Chunk- und Lichtpaket mit und lud beim Weltwechsel seine Datei, auch wenn das Modul aus war. Jetzt nur noch, wenn Player Bypass an ist.

### 8.20.1: Ohne aktive Module passiert nichts
Nicht im Spiel getestet.
- **Emotes:** Hört erst dann auf dem Relay (ntfy.sh) mit, wenn du in dieser Sitzung selbst ein Emote abgespielt hast. Vorher gibt es keine Verbindung.
- **Automation Supervisor:** Arbeitet nur, wenn eines der überwachten Automations-Module an ist oder noch ein pausiertes Modul wartet.
- **Spear Swap:** Wird beim Start nicht mehr eingeschaltet, erst wenn du die Lunge-Taste drückst.
- **Build Guard:** Wertet die Zeit-Pakete (TPS) nur aus, wenn AutoBuild an ist.
- **Player Bypass:** Der Licht-Tracker läuft nur, wenn das Modul an ist (seit 8.20.0).
- Was weiter von selbst läuft: die Update-Prüfung auf GitHub beim Titelbildschirm, der Discord-Webhook (bleibt wie gewünscht unverändert) und die Grundfunktionen des Clients (HUD, Benachrichtigungen, Tastenbelegung). Die übrigen Hooks prüfen nur kurz, ob ihr Modul an ist.
