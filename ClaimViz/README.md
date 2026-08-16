# ClaimViz

A client-side Fabric mod for Minecraft 1.21.x that visualizes GriefPrevention land claims and live player positions by reading data from a server's [SquareMap](https://modrinth.com/plugin/squaremap) web map. No server-side component required.

---

## Features

### Claim Borders
- Colored 3D border lines rendered around every nearby claim
- **Color coded by ownership:**
  - Purple - your own claims
  - Teal - admin/server claims
  - Yellow - claims you are currently standing inside (others')
  - SquareMap color - all other players' claims
- Floating owner name labels along each border edge, repeating at a configurable interval
- Configurable render distance

### Full-Screen Map
- Press **M** to open a pannable, zoomable map built from your server's SquareMap tiles
- Claim borders drawn over the tiles, colored by ownership
- Live players and your own position marked on top
- Hovering a claim shows its owner and size
- **Drag** to pan, **scroll** to zoom, **Esc** to close
- Follows you across dimensions - the map reloads when you change world

### Player Tracking
- Live player positions fetched from SquareMap and rendered in the world
- **Per-player overlays:**
  - Skin face icon projected onto the HUD at each player's world position
  - Health cross marker (color shifts green → red based on health)
  - Yaw direction tick above the health cross
  - Floating name tag billboard
- Configurable render distance (50–25,000 blocks)

### Claim Messages
- Enter/leave messages when crossing claim boundaries, color coded by claim type, written to your **local chat log** (nothing is sent to the server - see [below](#feedback-is-local-never-sent))
- Optional persistent claim bar showing which claim you're standing in. This one stays on the **action bar** deliberately: it refreshes every tick, so each write replaces the last rather than filling chat

### Xaero's Minimap Integration *(optional)*
- Player positions shown as live dots on the minimap radar
- Claim centers added as color-coded waypoints (purple = own, teal = admin, green = others)

### Keybinds

| Key | Action |
|-----|--------|
| `V` | Toggle claim border visibility |
| `P` | Toggle live player overlays |
| `M` | Open the full-screen map |

All rebindable under **Options → Controls → ClaimViz**.

---

## Requirements

| Dependency | Required | Notes |
|---|---|---|
| Fabric Loader ≥ 0.15 | Yes | |
| Fabric API | Yes | |
| Java 25 | Yes | |
| [ModMenu](https://modrinth.com/mod/modmenu) | Recommended | Required to access the config screen in-game |
| [Cloth Config](https://modrinth.com/mod/cloth-config) | Recommended | Required for the config screen UI |
| [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) | Optional | Enables player radar and claim waypoints |

[TrarnCore](../TrarnCore) is bundled inside the jar - **do not install it separately**.

Your server must have [SquareMap](https://modrinth.com/plugin/squaremap) installed and publicly accessible. ClaimViz reads claim and player data directly from SquareMap's web API - nothing is installed on the server side.

---

## Setup

1. Install ClaimViz and the recommended dependencies into your Fabric mods folder.
2. Launch the game and join your server.
3. Open **Mods → ClaimViz → Config** (requires ModMenu + Cloth Config).
4. Under **Servers**, click **+ Add New Server** and fill in:
   - **Server Address** - a substring of your server's IP (e.g. `play.example.com`)
   - **SquareMap URL** - the base URL of your server's SquareMap web map, no trailing slash (e.g. `https://map.example.com`)
5. Save. Claims and players will load automatically on your next join.

Nothing renders until a server entry matches the address you connected to - if you see no claims, that match is the first thing to check.

---

## Configuration

Most settings are per-server and configured through the in-game ModMenu screen.

| Setting | Default | Description |
|---|---|---|
| Enabled | `true` | Toggle this server entry on/off without deleting it |
| Claim Refresh Interval | `120s` | How often claim data is re-fetched from SquareMap |
| Show Claims | `true` | Render claim border lines |
| Claim Owner Labels | `true` | Show floating owner name labels on claim borders |
| Label Spacing | `12 blocks` | Distance between repeated owner labels along an edge |
| Show Players | `false` | Render live player overlays |
| Player Render Distance | `500 blocks` | Max distance at which player overlays are rendered (50–25,000) |
| Claim Enter/Leave Messages | `true` | Local chat message on claim boundary crossing |
| Persistent Claim Bar | `false` | Continuously show the current claim on the action bar |
| Xaero Waypoints | `false` | Sync claim waypoints to Xaero's Minimap |
| Map Tile Refresh | `60s` | How long a downloaded map tile is reused before being re-fetched |

Two global settings live under the General category:

| Setting | Default | Description |
|---|---|---|
| Claim Render Distance | `200 blocks` | How far claim borders are drawn in-world |
| Map Tile Budget | `128` | Max map tiles held in GPU memory at once. Raise for smoother panning, lower if VRAM is tight |

---

## Feedback is local, never sent

Claim enter/leave messages go to your chat log via `ChatHud.addMessage`, which appends straight to the client's own message list. No packet is involved, so nothing reaches the server or other players - it is not the same thing as sending a chat message.

---

## Building

> On a **fresh clone**, build [TrarnCore](../TrarnCore) once first — Loom reads the library jar at
> configuration time, so it must exist before this mod can even be configured:
> `(cd ../TrarnCore && ./gradlew build)`. After that, no ordering is needed.

Requires Java 21 (tested with Eclipse Temurin 21).

```bash
./gradlew build
```

The jar lands in `build/libs/`, and is also copied to [`../ModBuilds/`](../ModBuilds) alongside the other mods' output for easy installing.

Minecraft/Loader/Fabric versions come from [`../versions.properties`](../versions.properties), shared with the sibling mods, so a version bump is one file for all of them. [TrarnCore](../TrarnCore) is built from source automatically via a Gradle composite build - there is no publish step.

---

## Related mods

[SimDistance](../SimDistance) · [EasyPortalLinker](../EasyPortalLinker) · [ContainerUtil](../ContainerUtil) · [RSwitch](../RSwitch) · [TrustUI](../TrustUI) · [AutoRelog](../AutoRelog) — same Minecraft/Fabric target,
shared [TrarnCore](../TrarnCore) plumbing.
