# DIH Visuals (Resourcepack, 1.21.11, pack_format 75)

Bauen: `python3 resourcepack/build.py` -> `dist/DIH-Visuals-1.21.11.zip`. Shader prüfen: `python3 resourcepack/validate_shaders.py <entpackte Vanilla-Assets>`
(braucht `glslangValidator`). Texturen entstehen im Code, Shader sind Kopien der Vanilla-Shader mit kleinen Änderungen.

| Datei | Wirkung | Abschalten |
|---|---|---|
| `shaders/core/rendertype_clouds.fsh` | Wolken durchsichtig | `CLOUD_ALPHA = 1.0` |
| `shaders/core/sky.fsh` + `sky.vsh` | Himmel mit Farbverlauf, nachts Sternenhimmel mit Milchstraße | `SKY_GRADIENT = 0.0`, `STAR_AMOUNT = 0.0` |
| `textures/environment/celestial/moon/*.png` | Kein Mond (durchsichtig) | |
| `shaders/core/glint.fsh` | Glanz wechselt die Farbe (Item und Rüstung) | `GLINT_SPEED = 0.0` |
| `textures/block/stone.png` | Stone als Tiefenschiefer (eigene Textur) | |
| `textures/block/water_*.png` | Wasser ca. 45 % deckend, animiert | |
| `textures/environment/celestial/sun.png` | Runde Sonne mit Lichthof | |
| `textures/misc/enchanted_glint_*.png` | Cyan-violetter Glanz | |
| `models/item/totem_of_undying.json` | Totem kleiner | |

Die Shader ändern nur die Fragment-Shader (und `sky.vsh`), alle Uniforms und Importe bleiben wie bei Vanilla.
Sie laufen ohne Iris und Beryl. Gegen GLSL 330 geprüft (wie Vanilla). Im Spiel, auch mit VulkanMod, ungetestet.
