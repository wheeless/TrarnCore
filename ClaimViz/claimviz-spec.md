# ClaimViz — Fabric Mod Spec

## Overview

A client-side Fabric mod that fetches GriefPrevention claim data from a **SquareMap** JSON endpoint and renders claim boundaries as colored lines in the Minecraft world. Optionally integrates with Xaero's Minimap for waypoints, and supports live player position rendering via the players endpoint.

No server-side component required. Just a SquareMap URL in the config.

> **Note:** This mod targets SquareMap (not Dynmap). The endpoint paths and marker format are SquareMap-specific. SquareMap is widely deployed, making this broadly useful.

---

## Target Environment

- **Mod loader:** Fabric
- **Minecraft version:** 1.21.11
- **Java:** 21+
- **Side:** Client-only
- **Hard dependencies:** Fabric API
- **Soft dependencies:** Xaero's Minimap (optional integration — graceful no-op if absent)

---

## Endpoints

All endpoints are relative to the configured `squaremapUrl` base URL.

### Claim Markers (per dimension)

```
GET {squaremapUrl}/tiles/minecraft_overworld/markers.json
GET {squaremapUrl}/tiles/minecraft_the_nether/markers.json
GET {squaremapUrl}/tiles/minecraft_the_end/markers.json
```

Fetch the appropriate one based on the player's current dimension. Refresh on the configured interval (default 300s).

### Live Player Positions

```
GET {squaremapUrl}/tiles/players.json
```

Refresh every **1 second**. Returns current online player positions across all dimensions.

---

## Marker Data Format

### markers.json Structure

The response is a JSON array of layer objects. Filter for `"id": "griefprevention"`. Its `markers` array contains claim rectangles:

```json
[
  {
    "id": "griefprevention",
    "name": "Claims",
    "markers": [
      {
        "type": "rectangle",
        "fillColor": "#00ff00",
        "color": "#00ff00",
        "fillOpacity": 0.15,
        "popup": "Claim Owner: <span style=\"font-weight:bold;\">PlayerName</span>",
        "points": [
          { "x": 100, "z": 200 },
          { "x": 150, "z": 250 }
        ]
      },
      {
        "type": "rectangle",
        "fillColor": "#0000ff",
        "popup": "<span style=\"font-weight:bold;\">Administrator Claim</span><br/>Trust: ...",
        "points": [
          { "x": -500, "z": 300 },
          { "x": -450, "z": 350 }
        ]
      }
    ]
  }
]
```

### Claim Key Fields

| Field | Notes |
|---|---|
| `type` | Only process `"rectangle"` — ignore other types |
| `fillColor` | `#00ff00` = player claim, `#0000ff` = admin claim |
| `points[0]` | One corner `{x, z}` |
| `points[1]` | Opposite corner `{x, z}` |
| `popup` | HTML string with owner name |

### Owner Name Parsing

- Player claim: extract from `Claim Owner: <span ...>NAME</span>`
- Admin claim: `popup` begins with `<span ...>Administrator Claim</span>` — display as `"Administrator"`
- Use regex: `<span[^>]*>([^<]+)</span>` on first match after `Claim Owner: ` for player claims

### Coordinate Note

`points[0]` and `points[1]` may not be in min/max order. Always:
```java
int minX = Math.min(p0.x, p1.x);
int maxX = Math.max(p0.x, p1.x);
int minZ = Math.min(p0.z, p1.z);
int maxZ = Math.max(p0.z, p1.z);
```

Coordinates map **1:1** to Minecraft world XZ — no transformation needed.

---

## players.json Structure

```json
{
  "max": 8,
  "players": [
    {
      "world": "minecraft_overworld",
      "armor": 12,
      "name": "Trarn",
      "x": -1256,
      "y": 107,
      "health": 20,
      "z": 44598,
      "uuid": "33b710986b7e410689ead608aaaac3ac",
      "yaw": -162
    }
  ]
}
```

| Field | Notes |
|---|---|
| `name` | Display name |
| `x`, `y`, `z` | World coordinates |
| `world` | Dimension: `minecraft_overworld`, `minecraft_the_nether`, `minecraft_the_end` |
| `yaw` | Facing direction in degrees |
| `health` | 0–20 |
| `armor` | 0–20 |
| `uuid` | Player UUID (no dashes) |

---

## Configuration

### File Location

```
.minecraft/config/claimviz/servers.json
```

### Format

```json
{
  "servers": [
    {
      "serverAddress": "play.example.net",
      "squaremapUrl": "https://map.example.net",
      "enabled": true,
      "claimRefreshIntervalSeconds": 300,
      "showClaims": true,
      "showPlayers": true,
      "showClaimMessages": true,
      "xaeroWaypointsEnabled": true
    }
  ]
}
```

| Field | Default | Notes |
|---|---|---|
| `serverAddress` | — | Matched against current server IP on join |
| `squaremapUrl` | — | Base URL, no trailing slash |
| `enabled` | `true` | Master toggle for this server entry |
| `claimRefreshIntervalSeconds` | `300` | How often to re-fetch markers |
| `showClaims` | `true` | Toggle claim border rendering |
| `showPlayers` | `true` | Toggle live player rendering |
| `showClaimMessages` | `true` | Action bar messages on claim enter/leave |
| `xaeroWaypointsEnabled` | `true` | Add claims as Xaero waypoints if mod present |

---

## Rendering — Claim Borders

### Hook

`WorldRenderEvents.AFTER_TRANSLUCENT` (Fabric API)

### Per-Frame Logic

1. Get player's current XZ position and dimension
2. Filter cached claims for current dimension only
3. Further filter to claims within **200 blocks** of player XZ (at least one corner in range)
4. For each: draw 4 line segments forming the rectangle perimeter

### Render Height

Draw borders at the **player's current Y** so they're always visible regardless of terrain. Alternatively render a vertical "wall" (top and bottom lines at Y±2 and connecting verticals) for claims that are hard to see flat.

### Line Colors

| Claim Type | Color |
|---|---|
| Player claim | Bright green `#00FF00` |
| Admin claim | Bright blue `#0000FF` |
| Claim player is **currently inside** | Yellow `#FFFF00` |

### Line Rendering (1.21.x Fabric)

Use `RenderSystem` + `Tesselator` immediate mode with `DefaultVertexFormat.POSITION_COLOR` and `GL_LINES`. Disable depth test so borders are visible through terrain. Pattern follows vanilla's `DebugRenderer` approach.

---

## Rendering — Live Players

### Per-Second Fetch

Poll `players.json` every 1 second off the main thread. Store as `Map<String, PlayerData>` keyed by UUID.

### In-World Display

For each player in the same dimension as the local player (excluding self by UUID):
- Render a **colored marker** (small colored box or cross) at their XYZ
- Render their **name** as floating text above the marker
- Color by health: green → yellow → red based on `health` value
- Show a small directional indicator based on `yaw`

### Keybind Toggle

Separate toggle from claims — default `P` for players overlay.

---

## Xaero's Minimap Integration (Soft Dependency)

Only activate if Xaero's Minimap mod is detected at runtime via `FabricLoader.getInstance().isModLoaded("xaerominimap")`.

### Claim Waypoints

When claims are fetched, add each claim's center point as a Xaero waypoint:
- Name: owner name (or `"Admin Claim"`)
- Color: matches claim color (green/blue)
- Tagged with a `claimviz` prefix so they can be bulk-removed on disconnect or refresh

Use Xaero's public waypoint API — do not mixin into Xaero internals.

### Player Waypoints

Optionally add live players as temporary waypoints that update position each second. Tagged separately from claim waypoints.

### Cleanup

Remove all `claimviz`-tagged waypoints on server disconnect.

---

## HTTP Fetching

Use `java.net.http.HttpClient` (Java 21+). All requests off the main thread via `CompletableFuture`. Thread-safe cache handoff via `AtomicReference`.

### Failure Handling

| Condition | Behavior |
|---|---|
| Timeout / connection refused | Log warning, retry next interval |
| Non-200 response | Log warning, keep existing cache |
| Malformed JSON | Log warning, keep existing cache |
| Server not in config | Do nothing |

---

## In-Game Controls

| Keybind | Default | Action |
|---|---|---|
| Toggle claims | `V` | Show/hide claim borders |
| Toggle players | `P` | Show/hide live player markers |

Both configurable via standard Fabric keybind API.

---

## Project Structure

```
claimviz/
├── src/main/java/net/claimviz/
│   ├── ClaimViz.java                   # Mod initializer, keybind registration
│   ├── config/
│   │   ├── ClaimVizConfig.java         # Config data classes (records)
│   │   └── ConfigManager.java          # Load/save JSON config via Gson
│   ├── data/
│   │   ├── ClaimRect.java              # Parsed claim record
│   │   ├── PlayerData.java             # Parsed player record
│   │   ├── SquaremapFetcher.java       # HTTP fetch + JSON parse for markers
│   │   └── PlayerFetcher.java          # HTTP fetch + JSON parse for players (1s poll)
│   ├── render/
│   │   ├── ClaimRenderer.java          # Claim border rendering
│   │   └── PlayerRenderer.java         # Live player marker rendering
│   ├── integration/
│   │   └── XaeroIntegration.java       # Soft-dep Xaero waypoint bridge
│   └── event/
│       └── ServerJoinHandler.java      # Triggers fetches on join/leave, cleanup
├── src/main/resources/
│   ├── fabric.mod.json
│   └── assets/claimviz/lang/en_us.json # Keybind display names
└── build.gradle
```

---

## Data Classes

### ClaimRect

```java
public record ClaimRect(
    int minX, int maxX,
    int minZ, int maxZ,
    int color,        // packed ARGB
    String owner,     // display name
    String dimension  // "minecraft_overworld" etc.
) {
    public boolean isNear(double px, double pz, double threshold) {
        return px >= minX - threshold && px <= maxX + threshold
            && pz >= minZ - threshold && pz <= maxZ + threshold;
    }

    public boolean contains(double px, double pz) {
        return px >= minX && px <= maxX && pz >= minZ && pz <= maxZ;
    }

    public double centerX() { return (minX + maxX) / 2.0; }
    public double centerZ() { return (minZ + maxZ) / 2.0; }
}
```

### PlayerData

```java
public record PlayerData(
    String name,
    String uuid,
    double x, double y, double z,
    float yaw,
    int health,
    int armor,
    String world
) {}
```

---

## Implementation Notes

- Filter marker layer by `"id": "griefprevention"` — other layers (Spawn, World Border, Banners) must be ignored
- The three dimension endpoints are independent — fetch only the one matching the player's current dimension; re-fetch when the player changes dimension
- Player fetch at 1s interval should use a separate scheduled executor from the claim fetcher
- Self-exclusion for player rendering: compare `uuid` from `players.json` against local player's UUID via `MinecraftClient.getInstance().player.getUuid()`
- Xaero integration must be entirely in a separate class loaded only when the mod is present — never reference Xaero classes directly from core code (use reflection or a loaded-check guard)

---

## Stretch Goals (Post-MVP)

- [ ] `/claimviz reload` command to force re-fetch markers
- [ ] Per-server color overrides in config
- [ ] Owner name floating text above claim border when nearby
- [ ] Subclaim detection (rectangle fully inside another) rendered with a different style
- [ ] Health/armor display on player markers as a small HUD element
- [ ] SquareMap world list auto-discovery via `/tiles/worlds.json` if available
