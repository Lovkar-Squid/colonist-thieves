# Building Colonist Thieves

Built **without Gradle**, the same way Voyager and Colonist Errands are: plain `javac` against the
real mod jars, then `jar`. It compiles in a couple of seconds and needs nothing but a JDK 21.

## 1. The dependency jars

`libs/` is not part of the repository - the jars are not ours to redistribute. Bring your own from a
working 1.21.1 instance:

- `minecolonies-1.1.13xx-1.21.1.jar` and its own dependencies: `structurize`, `blockui`,
  `domum-ornamentum`, `multipiston`
- NeoForge: `neoforge-21.1.x-universal.jar`, `neoforge-21.1.x-client.jar`, and from its libraries
  folder `loader-*.jar`, `bus-*.jar`, `sponge-mixin-*.jar`, `modlauncher-*.jar`,
  `securejarhandler-*.jar`, `earlydisplay-*.jar`
- the Minecraft client jar (official/mojmap names) plus the usual libraries: `guava`, `gson`,
  `fastutil`, `datafixerupper`, `brigadier`, `authlib`, `joml`, `netty-*`, `commons-*`,
  `log4j-api`, `slf4j-api`, JetBrains `annotations`

`stubs/` holds two compile-only stubs (`net.neoforged.api.distmarker.Dist` and `OnlyIn`) for
annotations that exist at runtime but are awkward on the compile path. Source is in `stubsrc/`:

```bash
javac -encoding UTF-8 --release 21 -cp "libs/*" -d stubs $(find stubsrc -name '*.java')
```

## 2. Build

```bash
./build.sh
```

which is exactly:

```bash
javac -encoding UTF-8 --release 21 -proc:none -cp "stubs:libs/*" -d build $(find src -name '*.java')
jar cf colonist_thieves-<version>.jar -C build . -C resources .
```

The version lives in `resources/META-INF/neoforge.mods.toml`.

## 3. The blueprints

`tools/` regenerates the Hideout's five blueprints from Python - no in-game building, no schematic
editor. Needs `nbtlib`, `Pillow`, `numpy` and `scipy`
(`pip install --break-system-packages nbtlib Pillow numpy scipy`).

```bash
python3 tools/check_hideout.py    # judges the design: nothing floating, everything reachable
python3 tools/build_hideout.py    # writes resources/blueprints/.../hideout1-5.blueprint
```

`check_hideout.py` validates every block id against the blockstate files in the real jars rather
than a hand-written list, so a block that does not exist in this MineColonies version cannot reach
a blueprint.

## 4. Headless test

`/root/nfserver/warthief.sh` on the build rig puts this mod, Colonies at War and MineColonies into
a fresh world and boots a server. What it is looking for: both mods load, the structure packs are
picked up, the loot tables parse (MineColonies throws its whole datapack away over one unknown item
id), and `/thieves here` answers.

## A note on version ranges

An optional dependency declared `versionRange="[0.1,)"` **rejects** `0.1.0-alpha.1`: a pre-release
sorts below its own release, and the server then refuses to start. Use `[0.0.1,)` while either mod
is in alpha.
