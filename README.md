# DIHClient

Fabric-Client für Minecraft **1.21.11**. Dieses Repo enthält die Version **6.4.0**.

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

