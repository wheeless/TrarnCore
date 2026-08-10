# ContainerUtil

A client-side Fabric mod that **highlights every storage container around you in a distinct
colour, remembers what you put in them, and lets you search your whole base for an item** —
then walks you to it with a beam and a direction arrow.

Client-side only — nothing is installed server-side, and the index lives on your machine.

## Requirements

| Dependency | Required | Notes |
| --- | --- | --- |
| Fabric Loader ≥ 0.15 | Yes | |
| Fabric API | Yes | |
| Java 25 | Yes | |
| [ModMenu](https://modrinth.com/mod/modmenu) | Recommended | Needed to reach the config screen and the in-game query reference |
| [Cloth Config](https://modrinth.com/mod/cloth-config) | Recommended | Needed for the config screen UI |

[TrarnCore](../TrarnCore) is bundled inside the jar — **do not install it separately**.

## What it does

**Highlights.** Every container in a configurable chunk radius — measured *horizontally*, since
chunks load as full columns, so a chest at bedrock is highlighted just like one at your feet —
gets a coloured box. There is an optional vertical limit if you want one, off by default. Twenty-three
container kinds are recognised — including copper chests (all eight oxidation and waxed
variants) and shelves (all twelve woods) — grouped into five colour families so a wall of chests
reads as "storage" at a glance while a trapped chest or ender chest is unmistakably not one of
them. Every kind's colour and visibility is individually overridable.

**Fill level at a glance.** Once you have opened a container, its box opacity tracks how full it
is — an empty chest is barely there, a full one is solid. Containers you have never opened render
at a flat dimmer opacity rather than pretending to be empty.

**Content index.** Open a container and its contents are recorded. Shulker boxes are indexed
*through*: an item three levels deep in a shulker inside a chest is findable, and the result tells
you which shulker it is in. Ender chest captures apply to every ender chest, since they all show
the same inventory. Shelves and chiseled bookshelves have no GUI to open, so they are read
directly from the block entity during scanning — the client is already sent their contents in
order to render the items sitting on them.

**Search.** A full query language (below), results sorted nearest-first, with running totals.
Hovering a result shows the five nearest containers holding that same item with their coordinates
in order. Clicking one starts tracking it.

**Tracking.** The tracked container gets a vertical beam and a HUD readout with an eight-way
direction arrow and live distance. It clears itself when you arrive.

**Peek.** Look at an indexed container from up to 12 blocks away and its last-known contents
appear in a side panel — no need to open it.

**Freecam-friendly.** An **Anchor To Camera** toggle switches everything that measures distance —
the render cull, labels, the peek raycast, the HUD arrow, search result distances — from your
body to the camera, so highlights follow where you are viewing from instead of staying clustered
around the body you left behind. No integration with any particular freecam mod is needed, since
they all work by moving the game camera. Chunk scanning and index pruning stay player-anchored
regardless (a freecam does not load chunks, and pruning deletes data so it may only run where the
chunk is certainly loaded), as does tracking arrival — flying a camera to a chest is not arriving
at it.

**Staleness and pruning.** Every capture is timestamped. Contents older than the configured
threshold are flagged in results and on labels. Containers that are demonstrably gone — you are
standing next to where one used to be and the block is not there — are dropped from the index.
The pruner deliberately only fires within a short radius where the chunk is certainly loaded, so
walking past the edge of render distance never erases anything.

## Controls

- **Numpad 3** — toggle highlights. (Numpad rather than the main-row `3`, which is a vanilla
  hotbar slot; a toggle that also swapped your held item would be miserable.)
- **Search screen** — unbound by default; bind it in Options → Controls → *ContainerUtil*.
- **Clear tracking** — unbound by default.
- **ModMenu → ContainerUtil → Settings** — five categories: General, Colours, Indexing,
  Search & Tracking, and **Query Help** — a full in-game reference for the search language with
  worked examples, so nobody has to come back here to look up syntax. (Requires Cloth Config,
  like the sibling mods.)

In the search screen: **Click** tracks and closes, **Shift+Click** tracks and stays,
**Enter** tracks the first result, **Del** clears tracking, **Esc** closes.

## Query language

```
iron ingot            free text — display name, registry id or path
"oak log"             quoted, so the space is one term rather than two
#logs                 item tag (namespace optional: #logs == #minecraft:logs)
item:minecraft:stone  exact registry id
enchant:mending       stacks carrying a named enchantment (books included)
has:enchant           any enchanted stack        (also has:nested)
dim:nether            restrict to a dimension
in:barrel             restrict to a container kind (also type: / kind:)
                      partial match — in:chest covers trapped/ender/copper/minecart
                      chests, in:copper or in:trapped narrows it down
label:overflow        your nickname, or an anvil-renamed container
is:empty              also: full, partial, stale, unopened, opened,
                            labeled, double, mobile
count>64              also < >= <= = against the total matched per container
-cobblestone          exclusion (also !cobblestone)
```

Terms combine with AND. Exclusions reject the **whole container**, not just the line — so
`iron -cobblestone` means "chests that have iron and no cobble", which is the question people
actually ask.

The same reference is available in-game under **ModMenu → ContainerUtil → Settings → Query
Help**, with a couple of dozen worked examples.

## Feedback is local, never sent

Status messages ("Highlights enabled", "Arrived at …") go to your chat log via
`ChatHud.addMessage`, which appends straight to the client's own message list. No packet is
involved, so nothing reaches the server or other players — it is not the same thing as sending a
chat message. (`ChatHud` contains no networking code at all; sending chat goes through
`ClientPlayNetworkHandler.sendChatMessage`, which this mod never calls.)

Chat rather than the action bar on purpose: the action bar is a single slot that servers,
scoreboards and other mods all write to, so anything put there tends to be overwritten a tick
later — or to overwrite something you wanted to read. Chat is a log, so several sources coexist
and you can scroll back to something you missed.

## Storage

One JSON file per world under `config/containerutil/index/`, keyed by save folder name
(singleplayer) or server address (multiplayer), with the two namespaced apart so a save called
`example.com` can never collide with the server of the same name. Writes are debounced and go
through a temp file plus atomic move, so a crash mid-save costs you the last few seconds at
worst, never the database.

JSON rather than SQLite on purpose: search needs distance sorting and display-name matching,
both of which pull the whole index into memory anyway, so SQLite's lazy-load advantage
evaporates and you would be paying a ~12 MB jar-in-jar with native extraction for nothing. The
storage layer sits behind `IndexStore`, so swapping it later is a drop-in.

## Build

> On a **fresh clone**, build [TrarnCore](../TrarnCore) once first — Loom reads the library jar at
> configuration time, so it must exist before this mod can even be configured:
> `(cd ../TrarnCore && ./gradlew build)`. After that, no ordering is needed.

```bash
./gradlew build
```

The jar lands in `build/libs/`, and is also copied to [`../ModBuilds/`](../ModBuilds) alongside the
other mods' output for easy installing. Requires JDK 25.

Minecraft/Loader/Fabric versions come from [`../versions.properties`](../versions.properties),
shared with the sibling mods. [TrarnCore](../TrarnCore) is built from source automatically via a
Gradle composite build — there is no publish step.

## Updating to a new Minecraft version

Built for 26.1.2, but structured so the bump is small:

- **No mixins.** Content capture joins Fabric's `UseBlockCallback` / `UseEntityCallback` to a
  per-tick poll of `client.currentScreen` instead of mixing into `HandledScreen`. Mixins are what
  break hardest across versions.
- **No deprecated Fabric API.** The HUD uses `HudElementRegistry`, not the deprecated
  `HudRenderCallback` — deprecated API is the first thing removed in a major bump.
- **Version-volatile render calls live in [TrarnCore](../TrarnCore)**, not here. This mod draws
  through `Layers`, `Shapes` and `WorldText` and never imports `RenderLayers`, touches a
  `VertexConsumer`, or resolves a `VoxelShape` itself. When Minecraft reworks rendering, the fix
  happens once in the library and every sibling mod gets it.
- **The access widener lives there too** — TrarnCore opens `RenderLayer.of`, which is what makes
  see-through highlights possible (no vanilla layer pairs `NO_DEPTH_TEST` with a lines or quads
  vertex format, and `RenderLayer` has no public constructor). Fabric applies access wideners from
  nested jars, so bundling TrarnCore is enough; this mod declares none of its own.
- **No hardcoded registry ids** where a registry or class check works. Container kinds resolve
  from `Blocks` constants and `instanceof`, so all seventeen shulker colours are one case, all
  eight copper chests are another, and new variants of an existing class come along for free.
- **Per-kind settings live in maps** keyed by a stable string id, so a container type added in a
  future version does not invalidate anyone's config, and an unknown kind read from an old index
  degrades gracefully instead of throwing.
- The on-disk index carries a `formatVersion` and refuses to load anything newer than it
  understands, so downgrading never clobbers a newer index.

## Roadmap

- Container nicknames editable from the search screen (the `label` field and `label:` query are
  already wired; only the edit UI is missing).
- Export/import an index as JSON or CSV, for spreadsheets or sharing with base-mates.
- Snapshot diffing — "what changed in this chest since last week".
- Region and container-kind ignore lists.
- Xaero's Minimap waypoints on a search hit (the reflection bridge already exists in ClaimViz).

## Related mods

[ClaimViz](../ClaimViz) · [SimDistance](../SimDistance) · [EasyPortalLinker](../EasyPortalLinker) ·
[RSwitch](../RSwitch) — same Minecraft/Fabric target, shared [TrarnCore](../TrarnCore)
plumbing.
