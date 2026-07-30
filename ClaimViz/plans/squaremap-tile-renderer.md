# ClaimViz Full-Screen Map Renderer — Implementation Plan

## Overview

Full-screen SquareMap tile-based map overlay, opened via keybind (`M`). Fetches PNG tiles from the server's SquareMap instance, renders them as a pannable/zoomable map, and overlays claim borders and live player positions. Self-contained in a new `net.claimviz.map` package with minimal changes to existing classes.

---

## 1. Tile Coordinate Math

### Block to Tile

At zoom level `z` (0-3), one tile covers `512 * 2^(3-z)` blocks per side:

| Zoom | Blocks/tile |
|------|-------------|
| 3 | 512 (highest detail) |
| 2 | 1024 |
| 1 | 2048 |
| 0 | 4096 |

```java
int blocksPerTile = 512 * (1 << (3 - zoom));
int tileX = Math.floorDiv(blockX, blocksPerTile);  // handles negatives correctly
int tileZ = Math.floorDiv(blockZ, blocksPerTile);
```

### Tile + Pixel Offset to Block (cursor coordinate display)

```java
int blocksPerPixel = 1 << (3 - zoom);  // 1 at z=3, 8 at z=0
int blockX = tileX * blocksPerTile + px * blocksPerPixel;
int blockZ = tileZ * blocksPerTile + pz * blocksPerPixel;
```

### Visible Tile Grid

```java
double pixelsPerBlock = 1.0 / (1 << (3 - zoom));
double halfBlocksW = (screenW / 2.0) / pixelsPerBlock;
double halfBlocksH = (screenH / 2.0) / pixelsPerBlock;
int minTileX = Math.floorDiv((int) Math.floor(centerX - halfBlocksW), blocksPerTile);
int maxTileX = Math.floorDiv((int) Math.ceil(centerX  + halfBlocksW), blocksPerTile);
// same for Z
```

### Block to Screen Position

```java
double screenX = screenCenterX + (blockX - centerX) * pixelsPerBlock;
double screenZ = screenCenterY + (blockZ - centerZ) * pixelsPerBlock;
```

Used for both tile placement and overlay positioning.

---

## 2. Async Tile Fetching

### New Class: `TileFetcher`

Package: `net.claimviz.map`

- Owns a `HttpClient` (same pattern as `SquaremapFetcher`)
- Accepts `(baseUrl, dimension, zoom, tileX, tileZ)` - returns `CompletableFuture<Optional<byte[]>>`
- `Optional.empty()` = 404/error (missing tile - valid for void/unrendered areas)
- Deduplicates in-flight requests via `ConcurrentHashMap<TileCoord, CompletableFuture<...>> inFlight`

**URL pattern:**
```
{baseUrl}/tiles/{dimension}/{zoom}/{tileX}_{tileZ}.png
```

**Deduplication:**
```java
return inFlight.computeIfAbsent(key, k -> launchFetch(baseUrl, k));
// Remove from inFlight on completion so re-fetches are possible after TTL
```

**404 handling:** Return `Optional.empty()` silently. Log at DEBUG only. Do not WARN per-tile - 404s are normal at world boundaries.

**Concurrency cap:** Limit to 8 concurrent HTTP requests. Queue excess requests rather than firing them immediately. Prioritize tiles closest to the player's position.

---

## 3. Tile TTL and Periodic Refresh

Tiles are not static - SquareMap regenerates them as players build. Each cached tile stores a `fetchedAt` timestamp. On each `render()` call, visible tiles past their TTL are queued for a background re-fetch. The old texture stays visible until the new one arrives - no flicker.

**TTL:** Configurable per-server as `mapTileRefreshSeconds` in `ServerConfig`. Default 60s. Range 15-300s.

**Re-fetch stagger:** Fire at most 2-3 re-fetches per second to avoid request spikes when many tiles expire simultaneously. Prioritize tiles closest to the player's current position.

**On re-fetch completion:** Upload new texture, swap the reference in cache. Old texture is destroyed immediately after swap.

---

## 4. GPU Texture Management

### New Class: `TileTextureCache`

Package: `net.claimviz.map`

**Tile state:**
```java
enum TileState { LOADING, LOADED, MISSING }
```

**Cache entry:**
```java
record CacheEntry(TileState state, Identifier textureId, long fetchedAt) {}
```

**Data structures:**
```java
// Access-ordered LinkedHashMap for LRU - render thread only
private final LinkedHashMap<TileCoord, CacheEntry> cache = new LinkedHashMap<>(256, 0.75f, true);
// Bridge from background fetch threads to render thread
private final Queue<Pair<TileCoord, byte[]>> uploadQueue = new ConcurrentLinkedQueue<>();
// Closed flag - checked before enqueuing from background threads
private volatile boolean closed = false;
```

**Texture identifier naming:**
```
Identifier.of("claimviz", "maptile/" + dimension + "/" + zoom + "/" + tileX + "_" + tileZ)
```

**`processUploadQueue()` - called at top of every `MapScreen.render()`:**
- Process at most 4 uploads per frame to avoid stutter
- `NativeImage.read(new ByteArrayInputStream(bytes))` -> `NativeImageBackedTexture` -> `TextureManager.registerTexture(id, tex)`
- Call `evictIfOverBudget()` after each upload

**LRU eviction at 256 tile budget:**
- `LinkedHashMap` access-order: first entry = least recently accessed
- On eviction: `TextureManager.destroyTexture(entry.textureId())`
- Budget configurable as `mapTileBudget` in global config, default 128, max 512

**`enqueueUpload(TileCoord, byte[])`:**
```java
public void enqueueUpload(TileCoord coord, byte[] bytes) {
    if (!closed) uploadQueue.offer(Pair.of(coord, bytes));
}
```

**`clear()` - render thread only:**
- Set `closed = true`
- Drain `uploadQueue`
- Call `TextureManager.destroyTexture()` for all loaded entries
- Clear cache and missing set

**On disconnect:** `ServerJoinHandler.stopAll()` posts to render thread:
```java
mc.execute(() -> { if (mc.currentScreen instanceof MapScreen ms) ms.close(); });
```

---

## 5. Full-Screen Map Screen

### New Class: `MapScreen extends Screen`

Package: `net.claimviz.map`

**Constructor:**
```java
public MapScreen(ClaimVizConfig.ServerConfig config, String dimension)
```

Receives snapshots of `activeConfig` and `lastDimension` at keybind press time.

**Fields:**
```java
private final ClaimVizConfig.ServerConfig config;
private String dimension;
private double centerX, centerZ;     // block coords at screen center
private int zoom = 3;                // 0=lowest detail, 3=highest
private final TileFetcher fetcher;
private final TileTextureCache texCache;
```

**`init()`:** Center on player position, call `requestVisibleTiles()`.

**`render()` sequence:**
1. `texCache.processUploadQueue()`
2. Dark background fill `0xFF111111`
3. Draw tile grid - LOADED: `context.drawTexture(RenderLayer.getGuiTextured(id), ...)`, LOADING: gray placeholder `0x44888888`, MISSING: subtle `0x22444444`
4. Claim border overlays
5. Player dot overlays
6. Dimension label (top-left)
7. Zoom level indicator
8. Cursor block coordinates (top-right)
9. Queue re-fetches for tiles past TTL

**`DrawContext.drawTexture` signature:**
```java
context.drawTexture(
    RenderLayer.getGuiTextured(identifier),
    x, y,           // screen top-left
    u, v,           // source u/v (0, 0 for full tile)
    width, height,  // draw size in screen pixels
    texWidth, texHeight  // source texture size (512, 512)
)
```

**`mouseDragged()`:**
```java
double blocksPerPixel = 1 << (3 - zoom);
centerX -= deltaX * blocksPerPixel;
centerZ -= deltaY * blocksPerPixel;
requestVisibleTiles();
```

**`mouseScrolled()` - re-center on cursor:**
```java
double bppOld = 1 << (3 - zoom);
double cursorBX = centerX + (mouseX - width / 2.0) * bppOld;
double cursorBZ = centerZ + (mouseY - height / 2.0) * bppOld;
zoom = Math.clamp(zoom + (verticalAmount > 0 ? 1 : -1), 0, 3);
double bppNew = 1 << (3 - zoom);
centerX = cursorBX - (mouseX - width / 2.0) * bppNew;
centerZ = cursorBZ - (mouseY - height / 2.0) * bppNew;
requestVisibleTiles();
```

**`shouldPauseGame()`:** `return false;`

**`close()`:** `texCache.clear(); super.close();`

**`requestVisibleTiles()`:** Iterates visible tile grid, skips tiles already in cache (LOADING/LOADED/MISSING), fires `fetcher.fetchTile()` for the rest. On completion: `texCache.enqueueUpload()` or `texCache.markMissing()`. Throttle: skip if fewer than 2 frames since last request.

---

## 6. Overlays

### Coordinate helpers (in `MapScreen`):
```java
private double blockToScreenX(double bx) { return width  / 2.0 + (bx - centerX) / (1 << (3 - zoom)); }
private double blockToScreenZ(double bz) { return height / 2.0 + (bz - centerZ) / (1 << (3 - zoom)); }
```

### Claim Borders
- Read `ClaimCache.get(dimension)` every frame (atomic reference, no lock needed)
- Convert corners to screen space; skip if fully off-screen
- Draw 2px colored outline via four `context.fill()` calls
- Minimum size guard: if rect < 2px in either dimension, draw 1px dot
- Colors: reuse `ClaimRenderer` color logic (purple = own, teal = admin, SquareMap color = others)

### Player Dots
- Read `PlayerFetcher.getCached()`, filter by `dimension`
- 4x4 filled dot at player's screen position
- Name label below dot
- Self: distinct 6x6 yellow dot + "You" label
- Respect `config.playerRenderDistance` - skip players outside range

### Coordinate Display
```java
double bpp = 1 << (3 - zoom);
int cursorBX = (int) Math.floor(centerX + (mouseX - width / 2.0) * bpp);
int cursorBZ = (int) Math.floor(centerZ + (mouseY - height / 2.0) * bpp);
// Draw at top-right corner
```

---

## 7. Config Changes

### New keybind in `ClaimViz.onInitializeClient()`:
```java
public static KeyBinding OPEN_MAP = KeyBindingHelper.registerKeyBinding(new KeyBinding(
    "key.claimviz.open_map",
    InputUtil.Type.KEYSYM,
    GLFW.GLFW_KEY_M,
    "category.claimviz"
));
```

### Handler in `ServerJoinHandler.handleKeybinds()`:
```java
while (ClaimViz.OPEN_MAP != null && ClaimViz.OPEN_MAP.wasPressed()) {
    if (activeConfig == null || lastDimension == null) return;
    client.setScreen(new MapScreen(activeConfig, lastDimension));
}
```

### New `ServerConfig` fields:
```java
/** Tile refresh interval in seconds. 0 = no refresh. */
public int mapTileRefreshSeconds = 60;
```

### Global config:
```java
/** Max tiles held in GPU memory. Lower if experiencing memory pressure. */
public int mapTileBudget = 128;
```

### `en_us.json` addition:
```json
"key.claimviz.open_map": "Open Map"
```

---

## 8. New Classes and Files

| Class | Package | Responsibility |
|---|---|---|
| `TileCoord` | `net.claimviz.map` | Immutable `record(String dimension, int zoom, int tileX, int tileZ)` - HashMap key |
| `TileFetcher` | `net.claimviz.map` | Async HTTP PNG fetching with in-flight deduplication and concurrency cap |
| `TileTextureCache` | `net.claimviz.map` | GPU texture lifecycle - upload queue, LRU eviction, TTL tracking |
| `MapScreen` | `net.claimviz.map` | `Screen` subclass - full map UI, pan/zoom, tile rendering, overlays |

**Modified files (additions only):**
- `ClaimViz.java` - `OPEN_MAP` keybind field + registration
- `ServerJoinHandler.java` - keybind handler, map screen close on disconnect
- `ClaimVizConfig.java` - `mapTileRefreshSeconds` in `ServerConfig`, `mapTileBudget` in global config
- `ClaimVizConfigScreen.java` - slider for refresh interval and tile budget
- `assets/claimviz/lang/en_us.json` - keybind translation key

---

## 9. Integration Points

| Need | Source |
|---|---|
| SquareMap base URL | `config.squaremapUrl` (passed to constructor) |
| Current dimension | `lastDimension` from `ServerJoinHandler` (passed to constructor, updated live on dimension change) |
| Claim data | `ClaimCache.get(dimension)` - direct call, thread-safe |
| Player positions | `PlayerFetcher.getCached()` - direct call, thread-safe `AtomicReference` |
| Player render distance | `config.playerRenderDistance` |

---

## 10. Implementation Order

### Phase 1 - Skeleton map with checkerboard placeholders
1. Create `TileCoord` record
2. Create `MapScreen` - tile grid math, checkerboard fill (no HTTP)
3. Drag-to-pan, scroll-to-zoom with cursor re-centering
4. Cursor coordinate display
5. Register `OPEN_MAP` keybind, wire in `ServerJoinHandler`
6. Verify open/close/pan/zoom works in game

### Phase 2 - Real tile fetching (no GPU yet)
1. Create `TileFetcher` with deduplication
2. Create `TileTextureCache` skeleton - state tracking only, no actual textures
3. Wire `requestVisibleTiles()`, verify correct URLs logged
4. Verify tile coordinates match browser map

### Phase 3 - GPU texture upload
1. Implement `processUploadQueue()` with `NativeImage` + `NativeImageBackedTexture`
2. Implement LRU eviction
3. Implement `clear()` with texture destruction
4. Switch render to `context.drawTexture()` for LOADED tiles
5. Wire disconnect close handler

### Phase 4 - Tile TTL refresh
1. Add `fetchedAt` to `CacheEntry`
2. Queue re-fetches for expired visible tiles in `render()`
3. Implement stagger limiter (2-3 re-fetches/sec)
4. Swap texture on re-fetch completion

### Phase 5 - Overlays
1. Claim border outlines from `ClaimCache`
2. Player dots from `PlayerFetcher`
3. Self position marker
4. Off-screen culling

### Phase 6 - Polish
1. Dimension change detection in `render()` - flush and re-center
2. "No config" guard
3. Config screen entries for `mapTileRefreshSeconds` and `mapTileBudget`
4. HTTP concurrency cap
5. Performance profiling - tune uploads-per-frame

---

## 11. Edge Cases and Gotchas

### 404 Tiles
Normal for void/ocean/unrendered chunks. Store as MISSING in cache, participate in LRU so they can be retried after eviction. No WARN log per tile.

### Dimension Change While Map Open
Detect in `render()` by comparing `ServerJoinHandler.getLastDimension()` against stored `dimension`. On mismatch: flush `texCache`, re-center on player, update `dimension`, call `requestVisibleTiles()`.

### Disconnect While Map Open
`ServerJoinHandler.stopAll()` posts `mc.execute(() -> { if (mc.currentScreen instanceof MapScreen ms) ms.close(); })`. Render-thread safe.

### In-Flight Requests After Close
`HttpClient` futures cannot be hard-cancelled. The `closed` flag on `TileTextureCache` causes `enqueueUpload()` to no-op, discarding results from stale futures silently.

### Nether Coordinates
SquareMap tiles use native Nether block coordinates with no 1:8 scaling. No special handling needed - tile math is identical across dimensions. Cross-dimension coordinate comparison is a v2 display feature only.

### Texture Memory
256 tiles worst case = ~268 MB uncompressed. Default budget of 128 tiles = ~134 MB. Budget is configurable. Lower zoom levels naturally require fewer tiles (larger coverage per tile) so the budget is self-limiting during zoom-out.

### Large Admin Claims
Claims spanning hundreds of tiles render correctly - border math uses block coords directly, no tile iteration needed.

### `RenderLayer` for Tile Drawing
Use `RenderLayer.getGuiTextured(identifier)` with `context.drawTexture(...)`. Do NOT use `DrawContext.drawTexturedQuad()` - that is for HUD overlays with different coordinate semantics.
