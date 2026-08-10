# EasyPortalLinker

A tiny client-side Fabric mod that makes Nether-portal linking foolproof. Point a **wooden
shovel** at a portal in one dimension and EasyPortalLinker shows you **exactly** where its
counterpart must go in the other dimension:

- a **full-height translucent column** at the target X/Z, from bedrock to build height, so you
  can spot the location from anywhere in the vertical;
- an **axis-matched ghost outline** of the exact obsidian frame and portal blocks at the
  recommended Y — literally build to the outline;
- the **exact coordinates**, both floating in the world and on the HUD.

Build to the highlight and the game links to your portal instead of digging a fresh one out of
the rock. Works **both ways** — select in the Overworld to see the Nether spot, or select in the
Nether to see the Overworld spot.

Client-side only — nothing is installed server-side, and you still build the portal by hand. The
mod only shows you where.

## Why it works

Traveling between the Overworld and the Nether multiplies your horizontal position by the ratio
of the dimensions' coordinate scales (Overworld 1, Nether 8):

- Overworld → Nether: `x, z` divided by 8 (floored)
- Nether → Overworld: `x, z` multiplied by 8

`Y` stays the same (clamped into the destination's build range). The game then links to the
closest existing portal within 128 blocks of that scaled point — so building your counterpart
right on the scaled point makes it the closest candidate and gives a clean, predictable link.
EasyPortalLinker just does that math for you and paints the answer in the world.

## How to use

1. Hold a **wooden shovel** and **right-click your portal** — look at the purple portal, at its
   obsidian frame, or just stand inside it. A chat message reports the source coords, the axis,
   and the computed counterpart coordinates.
   - It only "steals" the right-click when a portal is actually detected, so the shovel still
     makes dirt paths and everything else normally.
2. Travel to the other dimension. The column, ghost frame, and coordinates are waiting for you.
3. Build the frame to the outline and light it.

The selection is remembered across dimension changes **and** restarts.

## Controls

- **P** — toggle the guide on/off (rebindable in Options → Controls → *EasyPortalLinker*).
- **K** — lock the target Y to your current level; **sneak + K** to unlock (back to following your
  feet). Rebindable.
- **Clear Portal Selection** — unbound by default; bind it if you like. You can also
  **sneak + right-click** with the selection item to clear.
- **ModMenu → EasyPortalLinker → Settings** — selection item, reach, colours, opacity, the
  target-Y lock (below), and which overlays to draw (column / ghost frame / floating coords / HUD /
  source highlight). Requires Cloth Config, like the sibling mods.

### Target Y

By default the ghost frame's base sits at **your feet and follows you up and down**, so it's always
in open air where you can see it (a fixed height would be buried in the Nether's rock). Turn on
**Lock Target Y** and set **Locked Target Y** to pin the frame to a fixed level instead — handy for
a Nether hub built at one consistent Y (e.g. 120, just under the roof). Either way the horizontal
X/Z is what makes the link; any reasonable Y works.

## Requirements

| Dependency | Required | Notes |
| --- | --- | --- |
| Fabric Loader ≥ 0.15 | Yes | |
| Fabric API | Yes | |
| Java 25 | Yes | |
| [ModMenu](https://modrinth.com/mod/modmenu) | Recommended | Needed to reach the config screen |
| [Cloth Config](https://modrinth.com/mod/cloth-config) | Recommended | Needed for the config screen UI |

[TrarnCore](../TrarnCore) is bundled inside the jar — **do not install it separately**.

## Feedback is local, never sent

Selection messages go to your chat log via `ChatHud.addMessage`, which appends straight to the
client's own message list. No packet is involved, so nothing reaches the server or other players —
it is not the same thing as sending a chat message.

## Build

> On a **fresh clone**, build [TrarnCore](../TrarnCore) once first — Loom reads the library jar at
> configuration time, so it must exist before this mod can even be configured:
> `(cd ../TrarnCore && ./gradlew build)`. After that, no ordering is needed.

```bash
./gradlew build
```

The jar lands in `build/libs/`, and is also copied to [`../ModBuilds/`](../ModBuilds) alongside the
other mods' output for easy installing. Requires JDK 25.

## Upgrading Minecraft version

Version pins live in [`../versions.properties`](../versions.properties), shared with every mod in
this directory — bump `minecraft_version`, `loader_version` and `fabric_version`
(and the soft-dep versions) to the builds listed at <https://fabricmc.net/develop/>, and the whole
family follows. Each mod keeps a fallback copy in its own `gradle.properties` so a project copied
out of this directory still builds.

Minecraft 26.x ships unobfuscated, so the source targets Mojang's own names directly — there is no
Yarn and no intermediary. See [`../plans/port-to-26.md`](../plans/port-to-26.md). Where rendering does break across versions, the fix belongs in
[TrarnCore](../TrarnCore)'s `render` package, which this mod draws through — one fix covers every
sibling.

## Notes / limitations

- Client-side only; no server install required. Nothing is changed server-side — you still build
  the portal by hand, the mod only shows you where.
- Linking is supported between the Overworld and the Nether. Selecting a portal in another
  dimension reports that it has no counterpart.
- The recommended Y is the source portal's Y clamped clear of the Nether's bedrock floor and
  roof; the full-height column is there precisely so you can choose a different Y if you prefer.

## Related mods

[ClaimViz](../ClaimViz) · [SimDistance](../SimDistance) · [ContainerUtil](../ContainerUtil) ·
[RSwitch](../RSwitch) — same Minecraft/Fabric target, shared [TrarnCore](../TrarnCore)
plumbing.
