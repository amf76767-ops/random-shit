# DIH Visuals (Resourcepack, 1.21.11)

Bauen: `python3 resourcepack/build.py` -> `dist/DIH-Visuals-1.21.11.zip`. Alle Texturen entstehen im Code, nichts Fremdes drin.

| Datei | Wirkung |
|---|---|
| `textures/block/stone.png` | Stone sieht aus wie Tiefenschiefer (eigene Textur, nicht die von Barebones) |
| `textures/block/water_still/flow.png` + `.mcmeta` | Wasser mit Alpha ca. 0.45, animiert |
| `textures/misc/enchanted_glint_item/armor.png` | Cyan-violetter Glanz |
| `models/item/totem_of_undying.json` | Totem kleiner (Item-Rahmen, evtl. Pop-Animation) |

Nicht drin: eigene Shader. Sie müssen auf den Vanilla-Shadern von 1.21.11 aufbauen, die hier nicht abrufbar waren.
Ungetestet im Spiel: `pack_format 75`, Wassertransparenz, Totem-Größe.
