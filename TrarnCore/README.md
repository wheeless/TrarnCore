# TrarnCore

Shared client-side plumbing for the Fabric mods in this directory: [ClaimViz](../ClaimViz),
[SimDistance](../SimDistance), [EasyPortalLinker](../EasyPortalLinker),
[ContainerUtil](../ContainerUtil), [RSwitch](../RSwitch) and [TrustUI](../TrustUI).

**Not installed separately.** It is bundled inside each mod's jar via jar-in-jar, so nobody
downloading one of those mods needs to know this exists.

## Why

Before this existed, the five mods carried five copies of the same code:

| Duplicated | Copies | Lines | Actually distinct |
| --- | --- | --- | --- |
| `ModMenuIntegration` | 5 | 120 | 2 (differed only in import order) |
| `ConfigManager` | 5 | 263 | ~1 |
| `Chat` | 5 | 213 | ~1 (prefix + colour) |
| `addLine` vertex emitter | 4 sites | — | 1 (ClaimViz had it twice) |

The line count was never the real argument though. The render layer is the most version-fragile
code in the family, and it had already drifted: ContainerUtil had a working see-through
implementation while the other four carried older hand-rolled variants. Five copies means fixing
a Minecraft rendering change five times, and the four you touch least rot quietly.

## What's in it

- **`render.Layers`** — depth-tested and see-through render layers. The see-through ones clone
  the vanilla `LINES` / `DEBUG_QUADS` pipelines with `NO_DEPTH_TEST`, keeping Mojang's shaders,
  vertex formats and blending. This is the file that breaks when Minecraft reworks rendering.
- **`render.Shapes`** — box fill/outline, lines, quads, walls, rectangles, beams, block outline
  shapes, plus `0xRRGGBB` channel helpers.
- **`render.WorldText`** — camera-facing world-space labels.
- **`chat.ChatChannel`** — prefixed, local-only chat feedback. One per mod, each with its own
  prefix colour.
- **`config.JsonConfig<T>`** + **`ValidatedConfig`** — JSON config load/save with a validation
  hook that runs after every load and before every save.
- **`input.Keys`** — keybind registration, and a `whenPressed` helper that drains the press queue
  properly.
- **`integration.ClothModMenuIntegration`** — ModMenu entrypoint base that keeps the Cloth screen
  class from loading when Cloth is absent.
- **`util.Guarded`** / **`util.ErrorThrottle`** — run tick and render work without taking the game
  down, and rate-limit errors that would otherwise recur every frame.
- **`update.UpdateChecker`** — tells the player when a newer release of an installed mod exists.
  **Notification only**, never downloads or replaces anything. One request per launch answers every
  mod, since they all live in one repo. See [the plan](../plans/update-checker.md) for why
  self-updating is deliberately not done.

## What's deliberately *not* in it

Domain logic. Claim fetching, portal maths, container indexing and inventory swapping all stay in
the mod that owns them. The failure mode for a library like this is accretion — the moment it
starts absorbing features it stops being plumbing and becomes something all five mods are hostage
to.

## How the mods consume it

Each mod's `settings.gradle` has `includeBuild('../TrarnCore')`, so Gradle substitutes the
`net.trarncore:trarncore` dependency with this project's output. That means **no publish step** —
edit the library, build any mod, and the change is picked up.

`publishToMavenLocal` still works if you ever need to consume it from outside this directory, but
note the trap that motivated the composite setup: a fixed version published to mavenLocal gets
cached by Gradle indefinitely, so a rebuilt library is silently ignored until you bump the version
or clear `~/.gradle/caches/modules-2/files-2.1/net.trarncore`.

### After changing this library, clear the remap cache

Loom keeps its own remapped copy of every mod dependency, keyed by coordinates and version. Adding
or changing anything here **without bumping `lib_version`** leaves that copy stale, and the mods
compile against the old API — reporting `package net.trarncore.x does not exist` for code that
plainly exists, or worse, building fine and failing at runtime.

`./gradlew clean` does **not** fix this: the cache lives in `.gradle/`, not `build/`. Clear it
explicitly from the repo root:

```bash
rm -rf */.gradle/loom-cache/remapped_mods
```

Only a concern during active library development. CI is unaffected, since a fresh checkout has no
`.gradle/` to go stale. Bumping `lib_version` also sidesteps it, because the cache key changes.

```gradle
// build.gradle
modImplementation "net.trarncore:trarncore:${pin('trarncore_version')}"
include "net.trarncore:trarncore:${pin('trarncore_version')}"   // jar-in-jar
```

## The access widener

TrarnCore declares one, opening exactly `RenderLayer.of`. Custom render layers need it and there
is no vanilla or Fabric API path to one. Fabric applies access wideners from nested jars, so
declaring it here opens it once for every mod that bundles this — ContainerUtil used to carry its
own and no longer needs to.

## API compatibility

Each mod bundles its own copy and Fabric loads the highest version it finds, so a mod built
against 1.0 may end up running against a 1.2 bundled by a sibling. **Keep changes additive** — add
methods, don't change or remove signatures — and rebuild every mod after a library change.

### `lib_version` must only ever go up

Because Fabric picks the highest bundled copy, the version is a **resolution key**, not a maturity
label. Lowering it means any older jar still lying in a mods folder outranks the new library and
gets loaded instead — code compiled against the newer API then dies with `NoClassDefFoundError` on
classes that plainly exist in the jar you just built.

This is not hypothetical: dropping 1.0.0 to 0.1.0 did exactly that, because a stale mod jar
bundling 1.0.0 shadowed the new 0.1.0 everywhere. Recovering from it needed 1.1.0; the
Minecraft 26.1.2 port is 1.2.0.

## Version pins

Minecraft, Loader and Fabric API versions live in [`../versions.properties`](../versions.properties),
read by every mod's `build.gradle`. A Minecraft bump is one file for the whole family. Each mod
keeps a fallback copy in its own `gradle.properties` so a project copied out of this directory
still builds.

## Build

```bash
./gradlew build
```

Requires JDK 25, matching the mods' toolchain.
