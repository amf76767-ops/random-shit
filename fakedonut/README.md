# FakeDonut 0.1.0

Fabric-Mod (1.21.11) für Singleplayer oder eigenen Server, um den DIH-Client gegen einen "Donut-ähnlichen" Server zu testen. Nicht im Spiel getestet.

## Was sie macht

- **Auktionshaus**: `/ah` (GUI, Seiten, Sortierung, Kaufbestätigung), `/ah sell <preis>` (Item in der Hand), `/sell` (Items in Kiste legen und zum Fixpreis verkaufen), `/bal`. NPC-Angebote werden automatisch nachgefüllt, Angebote laufen ab.
- **Fake-Basen**: Beim Generieren eines Overworld-Chunks wird (Chance `baseChance`) eine Stelle im Meer gesucht (Tiefe ab 6, Boden Sand/Kies/Erde/Ton). Dort wird Kelp gepflanzt (voll ausgewachsen, age 25), erst danach entsteht darunter auf Y `baseY` (-40, unter dem Deepslate) eine Base mit Kisten, Fässern, Öfen, Hoppern, Ender-Kiste, Loot und optional einem Spawner. Ohne Kelp keine Base.
- **Anti-ESP**: Der Chunk-Datenstrom wird per Mixin verändert. Unter Y `hideBelowY` (8) werden Base-Räume, Erze und Container, die komplett von festen Blöcken umgeben sind, sowie Geoden (Amethyst, Calcit, glatter Basalt und die Luft darin) durch Deepslate/Stein ersetzt. Block-Entities dieser Blöcke werden nicht mitgesendet. Echte Blöcke werden per Block-Update gesendet, sobald ein Spieler näher als `proximityRadius` kommt oder in der Nähe ein Block abgebaut wird.
- `/fakedonut status|list|base|antixray <on|off>`: `base` baut sofort Kelp + Base im aktuellen Chunk, `list` zeigt die Koordinaten.

## Config

`config/fakedonut/config.json` (baseChance, baseMinKelp, baseY, seaMinDepth, kelpPerBase, spawnerChance, antiXray, hideBelowY, hideClosedRooms, breakRevealRadius, proximityRadius, startBalance, minListings, listingHours). Daten: `bases.json`, `auction.json`, `balances.json` im selben Ordner.

## Grenzen

Lichtdaten werden nicht verändert. Basen entstehen nur in neu generierten Chunks. Chunks, die du schon besucht hast, bekommen keine Basis (außer mit `/fakedonut base`).

## Bauen

`./build.sh 0.1.0` (braucht /tmp/mc-int.jar, siehe addon/tools/README-mc-int.txt, und die Fabric-API-Jars in /tmp/fd/libs).
