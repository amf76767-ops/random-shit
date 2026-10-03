# DIHClient

Fabric-Client für Minecraft **1.21.11**. Dieses Repo enthält die Version **5.9.0**.

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
