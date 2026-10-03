DIHClient - CustomModel
=======================

Put your 3D models in this folder, turn on the module "CustomModel" and pick the file in its settings.

From Blender
------------
File > Export > glTF 2.0 (.glb)
  - Format: glTF Binary (.glb)
  - Include: keep "Animations" ticked if you have a walk / idle animation
  - Textures are packed into the .glb, nothing else to copy.
Name your animations "Idle" and "Walk" and they play automatically when you stand or move.
The model should look along -Y in Blender (the usual front view) and stand on the ground at Z = 0.
(A .blend file itself can't be read.)

.obj also works (with its .mtl and picture next to it), but has no animation.
Not supported: compressed glTF (Draco), morph targets, WebP textures.

Tips
----
- "Height" scales the model to that many blocks (player = 1.8).
- "Rotation" turns it if it faces the wrong way.
- "Place Statue" leaves a copy of the model where you stand.
- Models with more than "Max Triangles" are thinned out to keep the game smooth. Keep them under ~50k triangles.
- Only you see it. Other players and the server don't know about it.
