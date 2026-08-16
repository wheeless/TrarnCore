# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A monorepo of client-side Fabric mods for Minecraft **26.1.2**, plus `TrarnCore`, the shared
library they all bundle. Published under **Trarn** at `github.com/wheeless/TrarnCore`.

| Project | Purpose |
| --- | --- |
| `TrarnCore/` | Shared library: render layers/primitives, chat, JSON config, keybinds, ModMenu glue, update checker |
| `ClaimViz/` | GriefPrevention claims + live players from a SquareMap web map; full-screen map |
| `ContainerUtil/` | Container ESP, searchable content index, track-and-navigate |
| `EasyPortalLinker/` | Nether portal counterpart placement |
| `SimDistance/` | Simulation-distance border walls |
| `RSwitch/` | Swap held item with the inventory slot above |
| `TrustUI/` | Social-menu-style GriefPrevention trust management |
| `AutoRelog/` | Rule-driven auto-reconnect, classified by why the session ended |

Everything is **client-side only**. No mod requires a server-side component.

## Commands

`/usr/bin/java` is a headless JRE with no `javac`/`javap`. The JDK comes from SDKMAN, and a
non-interactive shell does not source it:

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.3-tem"
export PATH="$JAVA_HOME/bin:$PATH"
```

```bash
# fresh clone: build the library once first (see the rule below — this is not optional)
(cd TrarnCore && ./gradlew build)

# any single mod
cd ContainerUtil && ./gradlew build

# everything
for m in TrarnCore ClaimViz SimDistance EasyPortalLinker ContainerUtil RSwitch TrustUI AutoRelog; do
  (cd "$m" && ./gradlew build)
done
```

Each project is a **standalone Gradle build** with its own wrapper — there is no root build, so
`./gradlew` at the repo root does nothing. Every mod build also copies its jar into `ModBuilds/`.

## Architecture

Mods consume `TrarnCore` through a Gradle **composite build** (`includeBuild('../TrarnCore')` in
each `settings.gradle`) and bundle it with Loom's `include` (jar-in-jar). There is no publish step.

Minecraft/Loader/Fabric versions are pinned once in `versions.properties`; each project keeps
a fallback copy in its own `gradle.properties` so it still builds if copied out.

## Rules that are not obvious

These were each learned by breaking something. Violating them produces confusing failures, not
clear ones.

**Build `TrarnCore` before any mod on a fresh clone.** Loom inspects a `modImplementation`
dependency's jar at *configuration* time, before the task graph runs, so the library jar must
already exist on disk. A Gradle task dependency cannot fix this. Symptom:
`NoSuchFileException: trarncore-*.jar`.

**After changing `TrarnCore`, clear Loom's remap cache.** It keys remapped copies by coordinates
and version, so edits without a version bump leave mods compiling against the old API.
`./gradlew clean` does **not** fix it — the cache is in `.gradle/`, not `build/`:

```bash
rm -rf */.gradle/loom-cache/remapped_mods
```

Symptom: `package net.trarncore.x does not exist` for code that plainly exists.

**`lib_version` must only ever increase.** Fabric loads the *highest* bundled copy of a jar-in-jar
library, so the version is a resolution key, not a maturity label. Lowering it lets a stale mod jar
shadow the new library everywhere. Symptom: `NoClassDefFoundError` on classes that are in the jar
you just built.

**Keep the `TrarnCore` API additive.** Each mod bundles its own copy and the highest wins, so a mod
built against 1.0 may run against 1.2 from a sibling. Add methods; do not change or remove
signatures. Rebuild every mod after a library change.

**Never use `mavenLocal()` for TrarnCore.** A fixed version published there is cached by Gradle
indefinitely, so library edits are silently ignored. The composite build exists to avoid this.

**Domain logic stays out of TrarnCore.** Claim fetching, portal maths, container indexing,
inventory swapping and trust parsing belong to the mod that owns them. The library is plumbing.

**Access wideners, never mixins.** TrarnCore opens `RenderType.create`; AutoRelog opens
`DisconnectedScreen.details`. Both are one line and fail loudly at load if the target is renamed.
A widener belongs to whichever project needs it — only genuinely shared plumbing goes in TrarnCore.

**No mixins anywhere.** Version-volatile rendering lives in `TrarnCore/render/`; content capture
uses Fabric callbacks plus a `currentScreen` poll rather than mixing into `HandledScreen`. Keep it
that way — it is why a Minecraft bump is small.

**`VertexConsumerProvider.Immediate` builds one layer at a time.** Asking for a second layer's
buffer ends the first. Finish and flush one layer before requesting the next; never hold a quad
buffer and a line buffer simultaneously. Symptom: `IllegalStateException: Not building!`.

**Keybinds register through `Keys.register(MOD_ID, key, glfwKey)`.** That files them under the
mod's own category in Options → Controls. Vanilla's `KeyMapping.Category` constants are shared, so
`MISC` puts every sibling's binds in one undifferentiated list. The category needs a
`key.category.<modid>.main` entry in the mod's lang file or the screen shows the raw key.
`Category.register` also throws on a duplicate id rather than returning the existing one — hence
the cache in `Keys`; never call it directly.

**Flush world text before the render event returns.** `Font.drawInBatch` buffers into layers it
picks internally, so they cannot be ended by name — call `endBatch()` (no argument) after the
labels, while the transform that billboarded them is still in effect. Relying on the level
renderer to drain them worked on 1.21.11 and does not on 26.x: the leftover batch is flushed under
a different transform, and the labels swing around their anchor as the camera turns. Guard the
flush on having actually drawn something, so it does not drain layers the world renderer means to
draw itself.

**Player-facing messages go to local chat, never the action bar.** Use each mod's `ChatChannel`
(`TrarnCore/chat`). One exception, deliberate: ClaimViz's persistent claim bar, which refreshes
every tick and so must overwrite itself.

## Verifying Minecraft APIs

**Minecraft 26.x ships unobfuscated** — there is no Yarn and no intermediary; the jar carries
Mojang's own names. Never guess at a name; check the jar:

```bash
# the vanilla client jar, as downloaded by the launcher
javap -cp <path-to-26.1.2-client.jar> net.minecraft.client.gui.screens.Screen | grep -E "keyPressed"
```

The full Yarn -> Mojang translation table, and every trap found while porting, is in
[`plans/port-to-26.md`](plans/port-to-26.md). Read it before touching Minecraft API in this repo.

Two traps worth repeating here:

- **`RenderLayer` still exists** in 26.x as an entity feature layer — completely unrelated to the
  old Yarn `RenderLayer`, which is now `RenderType`. A careless rename lands on the wrong one and
  still compiles.
- **Never blind find-and-replace type names.** Renaming `Hand` -> `InteractionHand` inside a string
  literal turned the user-visible message "Hand cleared" into "InteractionHand cleared". Any
  scripted translation must skip string literals.

## Releasing

Tags name what they release; a bare `v1.2.3` is ambiguous here.

```bash
git tag containerutil-v0.1.0   # one project, built at the tag's version
git tag all-v0.2.0             # every mod at its own version, one release, all jars
git push origin <tag>
```

**Push at most three tags per push.** GitHub does not create tag push events beyond that, so the
workflows silently never run. Push them individually, or use `all-v*`.

## Conventions

- Java packages are `net.<modid>`; mod ids are lowercase, display names and repo paths PascalCase.
- Each mod has one `ChatChannel` with its own prefix colour: ContainerUtil dark aqua, RSwitch green,
  SimDistance red, EasyPortalLinker gold, ClaimViz light purple, TrustUI blue, AutoRelog aqua.
- Config classes implement `ValidatedConfig`; `validate()` runs after every load and before every
  save, and must clamp ranges and fill defaults.
- ModMenu integrations extend `ClothModMenuIntegration` so the Cloth screen class is never loaded
  when Cloth is absent.
- Ideas not yet built live in `plans/`.
