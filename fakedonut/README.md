# FakeDonut 0.2.0

Fabric-Mod (1.21.11) für Singleplayer oder eigenen Server, um den DIH-Client gegen einen "Donut-ähnlichen" Server zu testen. Nicht im Spiel getestet.

## Was sie macht

- **Auktionshaus**: `/ah` (GUI, Seiten, Sortierung, Kaufbestätigung), `/ah sell <preis>` (Item in der Hand), `/sell` (Items in Kiste legen und zum Fixpreis verkaufen), `/bal`. NPC-Angebote werden automatisch nachgefüllt, Angebote laufen ab.
- **Fake-Basen, zufällig über die Karte**: Jeder Overworld-Chunk, der geladen wird (auch schon besuchte), würfelt deterministisch (Chance `baseChance`). Trifft es, wird eine Meeresstelle in diesem Chunk gesucht (Tiefe ab 6, Boden Sand/Kies/Erde/Ton), Kelp gepflanzt (voll ausgewachsen, age 25) und darunter auf Y `baseY` (-40, unter dem Deepslate) eine Base gebaut. Ohne Kelp keine Base. Die Base ist ein Stash aus `config/fakedonut/stashes/*.litematic`; liegt dort nichts, wird ein eingebauter Raum mit Kisten, Öfen, Hoppern, Ender-Kiste und optional Spawner benutzt. Kisten, Fässer und Spawner in Stashes werden mit Zufalls-Loot bzw. Zufalls-Mob gefüllt (Inhalte aus der Datei werden nicht übernommen).
- **Anti-ESP nach Tiefe**: Alles unter Y `hideBelowY` (0, also im Deepslate) wird für Spieler darüber nicht übertragen: Der Server schickt diese Chunk-Abschnitte als reinen Deepslate, ohne Höhlen, Erze, Geoden, Basen und ohne die Block-Entities (Kisten, Spawner). Geht ein Spieler unter `hideBelowY`, bekommt er die echten Daten für die Chunks im Radius `revealChunks` (2, also 5×5 Chunks) um sich. Verlässt er den Radius oder geht wieder hoch, wird der Bereich wieder versteckt.
- `/fakedonut status|list|base|antixray <on|off>`: `base` baut sofort Kelp + Base im aktuellen Chunk, `list` zeigt die Koordinaten.

## Stashes (eigene Schematics)

Lege `.litematic`-Dateien (z. B. von Abfielder) nach `config/fakedonut/stashes/` und starte Welt/Server neu. Es wird zufällig eine davon pro Base gewählt. Der Stash wird mit seiner Unterkante auf Y `baseY` gesetzt und muss vollständig unter Y 0 bleiben (Höhe ≤ 40 bei `baseY` -40). Andere Formate (`.schem`, `.nbt`) werden noch nicht gelesen. Ich habe keine Dateien von Abfielder, du musst sie selbst hineinlegen.

## Config

`config/fakedonut/config.json` (baseChance, baseMinKelp, baseY, seaMinDepth, kelpPerBase, spawnerChance, antiXray, hideBelowY, revealChunks, startBalance, minListings, listingHours). Daten: `bases.json`, `auction.json`, `balances.json` im selben Ordner.

## Grenzen

Lichtdaten werden nicht verändert, ein Cheat-Client könnte darüber Höhlen erahnen. Es gibt Basen nur im Meer (wegen des Kelps). Beim Wechsel der Welt passen `bases.json` und die Welt nicht zusammen, lösche dann `bases.json`.

## Bauen

`./build.sh 0.2.0` (braucht /tmp/mc-int.jar, siehe addon/tools/README-mc-int.txt, und die Fabric-API-Jars in /tmp/fd/libs).
