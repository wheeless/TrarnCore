# TrarnCore

A monorepo of client-side [Fabric](https://fabricmc.net/) mods for Minecraft **26.1.2**, plus the
shared library they are all built on.

Everything here is **client-side only**. No mod in this repo requires a server-side component, and
none of them changes anything on a server you connect to.

> The repository is named after **TrarnCore**, the shared library, which is also the name of one of
> the folders inside it. The library is bundled into each mod's jar — it is never installed
> separately.

---

## The mods

| Mod | What it does | |
| --- | --- | --- |
| **[ClaimViz](ClaimViz)** | Visualizes GriefPrevention land claims and live player positions by reading a server's [SquareMap](https://modrinth.com/plugin/squaremap) web map. Includes a full-screen pannable map and optional Xaero's Minimap integration. | [README](ClaimViz/README.md) |
| **[ContainerUtil](ContainerUtil)** | Highlights every storage container around you in a distinct colour, remembers what you put in them, and lets you search your whole base for an item — then walks you to it with a beam and a direction arrow. | [README](ContainerUtil/README.md) |
| **[EasyPortalLinker](EasyPortalLinker)** | Makes Nether-portal linking foolproof. Select a portal with a wooden shovel and see exactly where its counterpart must go, as a ghost frame you build to. | [README](EasyPortalLinker/README.md) |
| **[SimDistance](SimDistance)** | Draws a translucent wall at the edge of your simulation-distance region, so you can see which chunks are actually being ticked — the thing that decides whether your farms run. | [README](SimDistance/README.md) |
| **[TrustUI](TrustUI)** | A social-menu-style screen for managing GriefPrevention claim trust. Shows online players with their heads, marks who already has permissions in the claim you are standing in, and grants or revokes access with a click. | [README](TrustUI/README.md) |
| **[RSwitch](RSwitch)** | One key swaps the item in your hand with the inventory slot directly above it. Works with an empty slot too, so it doubles as an instant way to clear your hand. | [README](RSwitch/README.md) |

### The library

| | | |
| --- | --- | --- |
| **[TrarnCore](TrarnCore)** | Shared plumbing: render layers and primitives, local chat feedback, JSON config, keybind helpers, ModMenu/Cloth glue, error throttling. Bundled into every mod via jar-in-jar. | [README](TrarnCore/README.md) |

It exists because the mods had accumulated a copy each of the same `ConfigManager`, `Chat` and
`ModMenuIntegration`, and had reimplemented the same vertex-emitting code four times. More
importantly, the render layer — the most version-fragile code here — had already drifted between
them. One copy means a Minecraft rendering change is fixed once.

---

## Building

Requires **JDK 25**. Each project is a standalone Gradle build with its own wrapper.

> **Build TrarnCore first on a fresh clone.** Loom inspects a `modImplementation` dependency's jar
> at *configuration* time, before any task runs — so the library jar has to already exist on disk
> or every mod fails with `NoSuchFileException: trarncore-*.jar`. Gradle cannot resolve this for
> you with a task dependency, because the read happens before the task graph executes.

```bash
# once, after cloning
cd TrarnCore && ./gradlew build && cd ..

# then any mod, any time
cd ContainerUtil && ./gradlew build
```

Or build everything:

```bash
(cd TrarnCore && ./gradlew build)
for m in ClaimViz SimDistance EasyPortalLinker ContainerUtil RSwitch TrustUI; do
  (cd "$m" && ./gradlew build)
done
```

After that first library build, day-to-day work needs no special ordering: the mods consume
TrarnCore through a Gradle **composite build** (`includeBuild('../TrarnCore')`), so editing the
library and rebuilding any mod picks the change up immediately. There is no publish step.

### Where the jars go

Every mod build also copies its finished jar into **[`ModBuilds/`](ModBuilds)**, so all six sit in
one folder ready to drop into a Minecraft instance. Sources jars and the library are deliberately
excluded — see [that folder's README](ModBuilds/README.md).

Install all six together. They share the bundled library, and mixing a jar built before a library
change with jars built after it is the one combination that produces a `NoClassDefFoundError` at
startup.

---

## Versions

Minecraft, Loader and Fabric API versions are pinned once in
**[`versions.properties`](versions.properties)** and read by every project's `build.gradle`. A
Minecraft bump is a one-file edit for the whole repo.

Each project keeps a fallback copy in its own `gradle.properties`, so a project copied out of this
directory still builds on its own.

Mod versions are per-project, in each `gradle.properties`.

---

## Releasing

A bare `v1.2.3` tag would be ambiguous in a monorepo, so tags name what they release.
[`release.yml`](.github/workflows/release.yml) supports two shapes.

### Everything at once

```bash
git tag all-v0.1.0
git push --tags
```

Builds all six mods and publishes **one** GitHub Release with all six jars attached. Each mod is
built at its own configured version — the version in the tag is only the release label. That is
deliberate: mod versions legitimately differ (ClaimViz is on `0.0.x` while the rest are on `0.1.x`),
and forcing them all to one number would misreport what you actually shipped.

### A single project

```bash
git tag containerutil-v0.1.0
git push --tags
```

Builds just that project, **at the version in the tag** — overriding `gradle.properties`, so the
tag is the single action that cuts the release, with nothing to remember to commit first.

Valid ids: `claimviz`, `containerutil`, `easyportallinker`, `simdistance`, `rswitch`,
`trustui`, `trarncore`, or `all`. Anything else fails the workflow with a clear message rather than silently building
nothing.

TrarnCore is excluded from `all-v*` on purpose — it is bundled inside every mod jar and is never
installed separately, so it has no artifact worth attaching. Release it on its own if you ever want
a tagged reference point for the library.

## CI

[`ci.yml`](.github/workflows/ci.yml) builds all seven projects as a matrix on every push and PR to
`main`, so each gets its own pass/fail in the checks list. Mod jars are uploaded as build
artifacts with 90-day retention.

---

## Requirements

| Dependency | Required | Notes |
| --- | --- | --- |
| Fabric Loader ≥ 0.15 | Yes | |
| Fabric API | Yes | |
| Java 25 | Yes | Both to build and to run |
| [ModMenu](https://modrinth.com/mod/modmenu) | Recommended | Needed to reach any mod's config screen |
| [Cloth Config](https://modrinth.com/mod/cloth-config) | Recommended | Needed for the config screen UI |

Individual mods have their own optional integrations — ClaimViz can talk to Xaero's Minimap, for
instance. See each mod's README.

---

## Repo layout

```
.
├─ .github/workflows/     CI and release workflows
├─ versions.properties    Shared Minecraft/Loader/Fabric pins
├─ ModBuilds/             Built jars, collected from every mod (generated)
├─ TrarnCore/             Shared library, bundled into each mod
├─ ClaimViz/
├─ ContainerUtil/
├─ EasyPortalLinker/
├─ SimDistance/
├─ RSwitch/
└─ TrustUI/
```

## License

MIT.
