# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A monorepo of client-side Fabric mods for Minecraft **1.21.11**, plus `TrarnCore`, the shared
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

Everything is **client-side only**. No mod requires a server-side component.

## Commands

`/usr/bin/java` is a headless JRE with no `javac`/`javap`. The JDK comes from SDKMAN, and a
non-interactive shell does not source it:

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.11-tem"
export PATH="$JAVA_HOME/bin:$PATH"
```

```bash
# fresh clone: build the library once first (see the rule below — this is not optional)
(cd TrarnCore && ./gradlew build)

# any single mod
cd ContainerUtil && ./gradlew build

# everything
for m in TrarnCore ClaimViz SimDistance EasyPortalLinker ContainerUtil RSwitch TrustUI; do
  (cd "$m" && ./gradlew build)
done
```

Each project is a **standalone Gradle build** with its own wrapper — there is no root build, so
`./gradlew` at the repo root does nothing. Every mod build also copies its jar into `ModBuilds/`.

## Architecture

Mods consume `TrarnCore` through a Gradle **composite build** (`includeBuild('../TrarnCore')` in
each `settings.gradle`) and bundle it with Loom's `include` (jar-in-jar). There is no publish step.

Minecraft/Yarn/Loader/Fabric versions are pinned once in `versions.properties`; each project keeps
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

**No mixins anywhere.** Version-volatile rendering lives in `TrarnCore/render/`; content capture
uses Fabric callbacks plus a `currentScreen` poll rather than mixing into `HandledScreen`. Keep it
that way — it is why a Minecraft bump is small.

**`VertexConsumerProvider.Immediate` builds one layer at a time.** Asking for a second layer's
buffer ends the first. Finish and flush one layer before requesting the next; never hold a quad
buffer and a line buffer simultaneously. Symptom: `IllegalStateException: Not building!`.

**Player-facing messages go to local chat, never the action bar.** Use each mod's `ChatChannel`
(`TrarnCore/chat`). One exception, deliberate: ClaimViz's persistent claim bar, which refreshes
every tick and so must overwrite itself.

## Verifying Minecraft APIs

Do not guess at Yarn mappings — they churn between versions. Check against the mapped jar:

```bash
MCJAR=~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/\
1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2/\
minecraft-merged-1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2.jar

javap -cp "$MCJAR" net.minecraft.client.gui.screen.Screen | grep -E "keyPressed|render"
```

Fabric API and Cloth Config jars are under `~/.gradle/caches/modules-2/files-2.1/`. This session's
recurring surprises: `Screen.keyPressed(KeyInput)`, `mouseClicked(Click, boolean)`,
`KeyBinding.matchesKey(KeyInput)`, `GameProfile.name()` (a record accessor, not `getName()`),
`Entity.getEntityPos()`.

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
  SimDistance red, EasyPortalLinker gold, ClaimViz light purple, TrustUI blue.
- Config classes implement `ValidatedConfig`; `validate()` runs after every load and before every
  save, and must clamp ranges and fill defaults.
- ModMenu integrations extend `ClothModMenuIntegration` so the Cloth screen class is never loaded
  when Cloth is absent.
- Ideas not yet built live in `plans/`.
