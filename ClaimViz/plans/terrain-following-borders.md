# Terrain-Following Claim Borders — Implementation Plan

## Overview

Adds terrain-hugging claim border polylines to ClaimViz. When `terrainFollowingBorders` is enabled, each claim edge is decomposed into a sequence of sampled world-surface heights and rendered as many short 3-D line segments that follow the ground. The existing flat-line path is preserved and selected at runtime by the config toggle.

Files touched:

- `ClaimVizConfig.ServerConfig` — new field
- `ClaimVizConfigScreen` — new UI entry + `copyOf` field
- `ClaimCache` — add `TerrainHeightCache.clearDimension` call on set
- NEW `TerrainHeightCache` — height cache class
- `ClaimRenderer` — polyline rendering + terrain label Y

---

## 1. Data Structure for the Terrain Height Cache

### Class: `net.claimviz.data.TerrainHeightCache`

**Key:** A composite `record EdgeKey(String dimension, int fixedCoord, boolean fixedIsX, int varyFrom, int varyTo)` uniquely identifies one edge of one claim in one dimension. `fixedIsX` distinguishes a Z-edge from an X-edge; `fixedCoord` is the constant axis value (`minZ`, `maxZ`, `minX`, or `maxX`); `varyFrom`/`varyTo` are the block-integer bounds of the varying axis.

**Stored value:** `float[] heights` — one Y value per sampled point along the edge. Index `i` corresponds to block coordinate `varyFrom + i * STEP_BLOCKS`. Length is `ceil((varyTo - varyFrom) / STEP_BLOCKS) + 1`.

**Map:** `private static final ConcurrentHashMap<EdgeKey, float[]> cache` — thread-safe because the render thread reads it and `clearDimension` is called from a background thread.

**Constants:**

```java
static final int STEP_BLOCKS = 4;    // sample every 4 blocks along an edge
static final float Y_OFFSET  = 0.1f; // nudge above surface to avoid z-fighting
```

**Public API:**

```java
// Returns cached heights or null if not yet sampled.
public static float[] get(String dim, int fixedCoord, boolean fixedIsX, int varyFrom, int varyTo)

// Replaces or inserts one edge's heights. Called from render thread only.
public static void put(String dim, int fixedCoord, boolean fixedIsX, int varyFrom, int varyTo, float[] heights)

// Drops all entries for a given dimension. Called on claim refresh and disconnect.
public static void clearDimension(String dimension)

// Drops everything. Called on disconnect.
public static void clearAll()
```

`EdgeKey` is package-private inside the class.

---

## 2. Cache Population and Invalidation

### When to populate

Height sampling happens **on the render thread** because `client.world.getTopY(...)` is not thread-safe. Sampling is lazy — in `ClaimRenderer.render()`, if `get()` returns `null` for an edge, sample it immediately and `put()` before rendering.

The first frame after a claim loads pays the sampling cost. For a typical 200-block edge at step 4 that is ~51 `getTopY` calls per edge — negligible.

Do **not** pre-warm from the background refresh executor. `getTopY` is not safe off the render thread.

### When to invalidate

| Trigger | Where to add the call | Call |
|---|---|---|
| Claim list replacement | `ClaimCache.set(dim, claims)` — after `cache.put` | `TerrainHeightCache.clearDimension(dim)` |
| Dimension change | Covered transitively by `fetchClaimsForDimension` → `ClaimCache.set` | (no separate call needed) |
| Disconnect | `ServerJoinHandler.stopAll()` — after `ClaimCache.clear()` | `TerrainHeightCache.clearAll()` |

No movement-based invalidation — heightmap data is static world terrain and does not change as the player moves.

---

## 3. Per-Edge Sampling Algorithm

### Step size

`STEP_BLOCKS = 4` — 17 points for a 64-block edge, 129 for a 512-block edge. Adequate terrain resolution at minimal cost.

### Heightmap type

```java
world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ)
```

`MOTION_BLOCKING_NO_LEAVES` rests on water and stone without floating above tree canopies. Prefer it over `WORLD_SURFACE`.

Apply `Y_OFFSET` after sampling: `heights[i] = surfaceY + Y_OFFSET`.

### Sampling helper signature

```java
// In ClaimRenderer (private static):
private static float[] sampleEdge(ClientWorld world, int fixedCoord, boolean fixedIsX, int varyFrom, int varyTo)
```

Implementation:

```java
int count = (int) Math.ceil((varyTo - varyFrom) / (float) STEP_BLOCKS) + 1;
float[] h = new float[count];
int bottom = world.getBottomY();
for (int i = 0; i < count; i++) {
    int vary = Math.min(varyFrom + i * STEP_BLOCKS, varyTo);
    int bx = fixedIsX ? fixedCoord : vary;
    int bz = fixedIsX ? vary : fixedCoord;
    int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz);
    // Unloaded chunk fallback — getTopY returns world bottom for unloaded chunks
    if (topY == bottom && MinecraftClient.getInstance().player != null) {
        topY = (int) MinecraftClient.getInstance().player.getY();
    }
    h[i] = topY + Y_OFFSET;
}
return h;
```

`Math.min(..., varyTo)` ensures the last sample lands exactly on the corner regardless of edge length divisibility.

---

## 4. Rendering the Polyline

### Strategy: adjacent-segment polyline (no vertical walls)

Connect consecutive sampled points as tilted 3-D segments — `point[i]` to `point[i+1]`. No vertical wall segments. Walls obscure terrain and look noisy at cliff edges.

### New rendering helper

```java
private static void addTerrainEdge(VertexConsumer vc, Matrix4f mat,
                                   int fixedCoord, boolean fixedIsX,
                                   int varyFrom, int varyTo,
                                   float[] heights, float r, float g, float b)
```

Loop `i` from `0` to `heights.length - 2`:

```java
int vary0 = Math.min(varyFrom + i * STEP_BLOCKS, varyTo);
int vary1 = Math.min(varyFrom + (i + 1) * STEP_BLOCKS, varyTo);
float x1, z1, x2, z2;
if (fixedIsX) {
    x1 = fixedCoord; z1 = vary0;
    x2 = fixedCoord; z2 = vary1;
} else {
    x1 = vary0; z1 = fixedCoord;
    x2 = vary1; z2 = fixedCoord;
}
addLine(vc, mat, x1, heights[i], z1, x2, heights[i + 1], z2, r, g, b);
```

`addLine` is unchanged — its normal-normalization handles 3-D diagonal segments correctly.

### Cache resolution helper

```java
private static float[] resolveEdge(ClientWorld world, String dim,
                                   int fixedCoord, boolean fixedIsX,
                                   int varyFrom, int varyTo) {
    float[] h = TerrainHeightCache.get(dim, fixedCoord, fixedIsX, varyFrom, varyTo);
    if (h == null) {
        h = sampleEdge(world, fixedCoord, fixedIsX, varyFrom, varyTo);
        TerrainHeightCache.put(dim, fixedCoord, fixedIsX, varyFrom, varyTo, h);
    }
    return h;
}
```

### Draw method

```java
private static void drawTerrainBorder(VertexConsumer vc, Matrix4f mat,
                                      ClaimRect claim, ClientWorld world, String dim,
                                      float r, float g, float b) {
    // North (z=minZ, x varies): fixedIsX=false, fixedCoord=minZ
    float[] h = resolveEdge(world, dim, claim.minZ(), false, claim.minX(), claim.maxX());
    addTerrainEdge(vc, mat, claim.minZ(), false, claim.minX(), claim.maxX(), h, r, g, b);
    // South (z=maxZ, x varies)
    h = resolveEdge(world, dim, claim.maxZ(), false, claim.minX(), claim.maxX());
    addTerrainEdge(vc, mat, claim.maxZ(), false, claim.minX(), claim.maxX(), h, r, g, b);
    // West (x=minX, z varies): fixedIsX=true, fixedCoord=minX
    h = resolveEdge(world, dim, claim.minX(), true, claim.minZ(), claim.maxZ());
    addTerrainEdge(vc, mat, claim.minX(), true, claim.minZ(), claim.maxZ(), h, r, g, b);
    // East (x=maxX, z varies)
    h = resolveEdge(world, dim, claim.maxX(), true, claim.minZ(), claim.maxZ());
    addTerrainEdge(vc, mat, claim.maxX(), true, claim.minZ(), claim.maxZ(), h, r, g, b);
}
```

### Integration in `ClaimRenderer.render()`

Replace the four `addLine` calls in the claim loop with:

```java
if (cfg.terrainFollowingBorders) {
    drawTerrainBorder(vc, mat, claim, client.world, dim, r, g, b);
} else {
    addLine(vc, mat, x1, y, z1, x2, y, z1, r, g, b);
    addLine(vc, mat, x2, y, z1, x2, y, z2, r, g, b);
    addLine(vc, mat, x2, y, z2, x1, y, z2, r, g, b);
    addLine(vc, mat, x1, y, z2, x1, y, z1, r, g, b);
}
```

---

## 5. Label Y for Terrain-Following Mode

Per-label position sampling (one `getTopY` call per label placement, not cached). Only ~20–40 calls across all nearby claims per frame — negligible.

Add helper:

```java
private static float terrainLabelY(ClientWorld world, float wx, float wz) {
    int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) wx, (int) wz);
    int bottom = world.getBottomY();
    if (top == bottom && MinecraftClient.getInstance().player != null) {
        top = (int) MinecraftClient.getInstance().player.getY();
    }
    return top + Y_OFFSET + LABEL_HEIGHT;
}
```

Update `placeLabelsOnEdge` signature to accept `boolean terrainMode` and `ClientWorld world`. When `terrainMode` is true each `placeLabel` call uses `terrainLabelY(world, wx, wz)` instead of the constant `labelY`. Flat mode is unchanged.

---

## 6. Config Changes

### `ClaimVizConfig.ServerConfig` — new field

Add after `xaeroWaypointsEnabled`:

```java
/** [EXPERIMENTAL] Render claim borders following terrain elevation rather than at a fixed Y. CPU-intensive — may cause frame drops. */
public boolean terrainFollowingBorders = false;
```

Default `false` preserves current behavior for existing users.

### `ClaimVizConfigScreen.copyOf()` — copy the new field

```java
copy.terrainFollowingBorders = src.terrainFollowingBorders;
```

**Do not forget this.** Omitting it causes the screen to silently reset the field to `false` on every save.

### `ClaimVizConfigScreen.addServerFields()` — new UI entry

Add after the Xaero Waypoints entry:

```java
sub.add(entry
    .startBooleanToggle(Text.literal("[EXPERIMENTAL] Terrain-Following Borders"), slot.terrainFollowingBorders)
    .setDefaultValue(false)
    .setTooltip(Text.literal(
        "[EXPERIMENTAL] Render claim borders that follow the ground elevation instead of floating at a fixed height. " +
        "Requires a powerful CPU — may cause frame drops on slower machines. " +
        "Disable if you experience stuttering."
    ))
    .setSaveConsumer(val -> slot.terrainFollowingBorders = val)
    .build()
);
```

The label prefix `[EXPERIMENTAL]` makes the warning visible in the collapsed subcategory list without the user needing to hover for the tooltip.

---

## 7. Implementation Order

| Step | What | Risk |
|---|---|---|
| 1 | Add `terrainFollowingBorders` field, `copyOf`, UI toggle — no behavior change | Zero |
| 2 | Create `TerrainHeightCache` class, no callers yet — compile check only | Zero |
| 3 | Wire `clearDimension` in `ClaimCache.set`, `clearAll` in `stopAll` | Low — defensive only |
| 4 | Add `sampleEdge`, `resolveEdge`, `addTerrainEdge`, `drawTerrainBorder` to `ClaimRenderer` — private, no callers | Zero |
| 5 | Wire `if (cfg.terrainFollowingBorders)` branch in render loop — test in game | Medium — verify flat toggle still works |
| 6 | Update label Y in terrain mode — update `placeLabelsOnEdge` signature | Low |
| 7 | Edge-case hardening (nether ceiling, chunk fallback improvements) | Low |

---

## 8. Edge Cases and Gotchas

### Unloaded chunks return `world.getBottomY()`

`getTopY` returns the world minimum Y (-64 in 1.21) for any column in an unloaded chunk. Detect and fall back to `player.getY()`. The edge re-samples correctly on the next claim refresh once the chunk loads. A v2 improvement: after sampling, scan for values within 1 block of `getBottomY()` and store a sentinel `float[0]` so those specific points re-sample each frame until valid data arrives.

### Nether ceiling

In `the_nether`, `MOTION_BLOCKING_NO_LEAVES` returns Y ≈ 128 (above the bedrock ceiling) for most columns — borders will float at ceiling height and be useless. Simple fix for v1: if the dimension key contains `"nether"`, skip terrain mode and render flat regardless of the toggle. Add a note to the tooltip.

### End void

Over void in `the_end`, `getTopY` returns `getBottomY()`. The same unloaded-chunk fallback (`player.getY()`) handles this gracefully. Over End islands it works correctly.

### Performance at high claim density

50 nearby claims × 4 edges × ~51 samples = ~10,200 `getTopY` calls on first frame. `getTopY` is an O(1) heightmap array lookup with no world generation. This completes in well under 1 ms. Subsequent frames are cache hits only. No special throttling needed for v1.

If a first-frame spike is ever profiled as problematic: cap to N edges sampled per frame (e.g. 20), store a `float[0]` sentinel for pending edges, and skip rendering them until filled.

### Thread safety of `resolveEdge`

`resolveEdge` runs on the render thread. `clearDimension` runs on the background executor. `ConcurrentHashMap` makes this safe — worst case is a harmless double-sample if a clear races with a put. No corruption possible.

### `copyOf` omission is a silent data-loss bug

The cloth-config screen deep-copies `ServerConfig` on open and writes back on save. Any field missing from `copyOf` silently resets to its default on every save. Always add new fields to `copyOf` in the same commit that adds them to `ServerConfig`.

---

## Critical Files

| File | Change |
|---|---|
| `src/.../render/ClaimRenderer.java` | Polyline rendering, `sampleEdge`, `resolveEdge`, terrain label Y |
| `src/.../config/ClaimVizConfig.java` | `terrainFollowingBorders` field |
| `src/.../config/ClaimVizConfigScreen.java` | UI toggle + `copyOf` |
| `src/.../data/ClaimCache.java` | Add `TerrainHeightCache.clearDimension` call |
| `src/.../event/ServerJoinHandler.java` | Add `TerrainHeightCache.clearAll` in `stopAll` |
| NEW `src/.../data/TerrainHeightCache.java` | Cache class |
