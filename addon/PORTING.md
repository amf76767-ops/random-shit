# Porting Anubis modules into DIHClient (working notes)

Source of the modules: Anubis Client 0.9.8 (GPL-3.0, author 4ldenz, the user's own client). Two readable copies exist:

* `/tmp/deob/anubis-int-src/` – decompiled with the **real intermediary names** (`class_310`, `method_1551`, `field_1724`). Same
  files and line layout as the Yarn copy. This is what you copy from: the Minecraft calls compile as they are.
* `/tmp/deob/anubis-src/` – the same code with Yarn names (`MinecraftClient`, `getPlayer`). Read this one to understand the code.
  (A few static fields such as `Items.field_8288` stay unmapped in it; look them up in the intermediary copy / `javap`.)

Real Minecraft 1.21.11 in intermediary names: `/tmp/mc-int.jar` (unpacked in `/tmp/mcx`). `javap -p -cp /tmp/mc-int.jar net.minecraft.class_1799`
lists a class. Yarn names → intermediary: `/tmp/deob/yarn.tsv` (lines `METHOD<TAB>owner<TAB>method_123<TAB>yarnName<TAB>desc`).
Use `grep -P "\tgetOffHandStack\t" /tmp/deob/yarn.tsv`.

## Rules
* New code goes into `addon/src/main/java/dev/dihclient/port/<yourarea>/` (package `dev.dihclient.port.<yourarea>`).
  Mixins go into `addon/src/main/java/dev/dihclient/mixin/port/` and their **file name must end in `Mixin`** (accessor interfaces too).
  The patcher lists every `*Mixin` class of that folder in the mixin config by itself. Mixin targets use intermediary names as strings
  (no refmap): `@Inject(method = "method_10770", ...)`. Prefer `@Accessor("field_x")` / `@Invoker("method_x")` interfaces to reflection.
* Do **not** edit shared files: `AddonHooks`, `Patcher.java`, `build.sh`, `Cleanup`, `Folds`, the module registry. Do **not** run `build.sh`
  (it owns the shared `build/` folder; other people work in parallel). Check your code with `addon/tools/quick.sh <files...>`.
  If you need a stub for a library class (authlib, brigadier, DFU), add it under `addon/stubs/...` (Java source, signatures only).
* A module is a class `extends dev.dihclient.module.Module` with a public no-arg constructor
  `super("Name", Category.X, "one sentence")`. Settings: `this.bool/dbl/integer/mode/color/ids/text/action(...)` (see
  `reference-decompiled/dev/dihclient/module/Module.java` and `setting/*`; `.visibleWhen(() -> ..)`, `.onChange(..)`). Module name and setting
  names are shown in the GUI, keep them like in Anubis. Lifecycle: `onEnable/onDisable/onTick/onRender2D(class_332,float)/
  onRender3D(Render3D)/onPacketSend(class_2596)` (return true = cancel) /`onWorldChange`. Look at modules in `reference-decompiled/dev/dihclient/modules/*`
  and at `addon/src/main/java/dev/dihclient/glue/FakeTime.java` for style. 3D drawing: `addon/src/override/java/dev/dihclient/render/Render3D.java`.
* Anubis events → DIH: `TickEvent`→`onTick`, `Render3DEvent`→`onRender3D`, `Render2DEvent`→`onRender2D`, `PacketSendEvent`→`onPacketSend`,
  `PacketReceiveNettyEvent`/`PacketReceiveEvent`/`PacketAppliedEvent` → `dev.dihclient.port.PacketBus` (read it; register in `onEnable`,
  `PacketBus.remove(listener)` in `onDisable`; keep one listener object per module). Other Anubis events (key, mouse, chat send, block update,
  entity add/remove, attack) have no DIH bus: write a small mixin or poll in `onTick`, whichever is simpler and robust.
* Anubis framework classes (`ClientModule`, its settings, `EventBus`, `ModuleRegistry`, HUD helpers, config stores) are **not** ported:
  replace them with the DIH equivalents. Files the Anubis modules write: use `FabricLoader.getInstance().getConfigDir().resolve("dihclient")`.
* Keep the behaviour of the Anubis module, drop only what needs Anubis-only infrastructure (say what you dropped). No network calls beyond
  what the Anubis module already does; say if there are any. Everything must fail soft: wrap risky things, log with `DIHClient.LOG`, never crash the game.
* Comments in English, short, like the surrounding code ("why", not "what"). First line of each ported file's class comment:
  `Ported from Anubis Client 0.9.8 (GPL-3.0).`
* Pure logic (math, parsers, state machines) gets a unit test in `addon/src/test/java/dev/dihclient/<Name>Tests.java` (a `main` that throws on
  failure, like the existing ones; run it yourself with javac/java against `build/classes`). Tell me the class name so I add it to `build.sh`.
* We cannot start Minecraft here. Do not claim in-game behaviour is verified. Compile + unit tests + careful reading is the bar.

## Report back (short)
Files created, module classes (name, Category), settings that were dropped or changed, shared files I must touch (registration lines, removals),
tests to add to build.sh, anything doubtful.
