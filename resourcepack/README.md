# DIH Visuals (Resourcepack, 1.21.11, pack_format 75)

Bauen: `python3 resourcepack/build.py` -> `dist/DIH-Visuals-1.21.11.zip`. Shader prüfen: `python3 resourcepack/validate_shaders.py <entpackte Vanilla-Assets>`
(braucht `glslangValidator`). Texturen entstehen im Code, Shader sind Kopien der Vanilla-Shader mit kleinen Änderungen.

| Datei | Wirkung | Abschalten |
|---|---|---|
| `shaders/core/rendertype_clouds.fsh` | Wolken durchsichtig | `CLOUD_ALPHA = 1.0` |
| `shaders/core/sky.fsh` + `sky.vsh` | Himmel mit Farbverlauf, nachts Sternenhimmel mit Milchstraße | `SKY_GRADIENT = 0.0`, `STAR_AMOUNT = 0.0` |
| `textures/environment/celestial/moon/*.png` | Kein Mond (durchsichtig) | |
| `shaders/core/glint.fsh` | Glanz wechselt die Farbe (Item und Rüstung) | `GLINT_SPEED = 0.0` |
| `textures/block/stone.png`, `deepslate.png`, `deepslate_top.png` | Stone und Deepslate mit derselben eigenen Textur | |
| `textures/block/water_*.png` | Wasser ca. 45 % deckend, animiert | |
| `textures/environment/celestial/sun.png` | Runde Sonne mit Lichthof | |
| `textures/misc/enchanted_glint_*.png` | Cyan-violetter Glanz | |
| `models/item/totem_of_undying.json` | Totem kleiner | |
| `models/item/shield*.json` | Schild in der Ich-Ansicht tiefer | |
| `shaders/include/fog.glsl` | Weniger Nebel unter Wasser, in Lava und bei Blindheit | `CLEAR_FOG = 1.0`, `CLEAR_FOG_MIN = 0.0` |
| `textures/block/gravel.png`, `sand.png`, `dirt.png` … | Kies, Sand, Erde, Andesit, Diorit, Granit, Tuff, Ton, Netherrack, Seelensand, Schlamm als einfarbige Flächen | |
| `models/item/generated.json`, `handheld.json`, `models/block/block.json` | Kleinere Items in der Hand | |
| `textures/gui/sprites/hud/crosshair.png` | Dünnes Fadenkreuz mit Punkt | |
| `sounds.json` | Keine Höhlengeräusche, Regen und Donner leiser | |

Die Shader ändern nur die Fragment-Shader (und `sky.vsh`), alle Uniforms und Importe bleiben wie bei Vanilla.
Sie laufen ohne Iris und Beryl. Gegen GLSL 330 geprüft (wie Vanilla). Im Spiel, auch mit VulkanMod, ungetestet.
