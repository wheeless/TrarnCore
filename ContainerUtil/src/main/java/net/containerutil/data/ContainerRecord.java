package net.containerutil.data;

import net.containerutil.container.ContainerKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything ContainerUtil knows about one container.
 *
 * <p>Two timestamps, because they answer different questions. {@link #lastSeen} is "the block
 * was still there when I last walked past", updated by the scanner every time the container is
 * in a loaded chunk. {@link #lastScanned} is "this is when I actually looked inside", updated
 * only when you open it. A container can be freshly seen but have year-old contents.
 */
public class ContainerRecord {

    /** {@link ContainerKind#id()}. Stored as a string so an unknown kind from a newer version degrades gracefully. */
    public String kind;

    /** Dimension registry id, e.g. {@code minecraft:overworld}. */
    public String dim;

    public int x;
    public int y;
    public int z;

    /** Set for the far half of a double chest, so both halves highlight as one box and resolve to one record. */
    public boolean hasSecondary;
    public int x2;
    public int y2;
    public int z2;

    /** Entity UUID for mobile containers (minecarts, chest boats, chested animals); {@code null} for blocks. */
    public String entityUuid;

    /** The container's own name if it was renamed in an anvil, otherwise {@code null}. */
    public String customName;

    /** A nickname you assigned from the search screen, otherwise {@code null}. */
    public String label;

    /** Epoch millis when the container was last confirmed to exist. */
    public long lastSeen;

    /** Epoch millis when the contents were last captured. {@code 0} means never opened. */
    public long lastScanned;

    public int slotCount;
    public int usedSlots;

    public List<ItemEntry> items = new ArrayList<>();

    // ── Identity ─────────────────────────────────────────────────────────────

    /** Stable key for this container within the index. */
    public String key() {
        return entityUuid != null ? entityKey(dim, entityUuid) : blockKey(dim, x, y, z);
    }

    public static String blockKey(String dim, int x, int y, int z) {
        return dim + "@" + x + "," + y + "," + z;
    }

    public static String entityKey(String dim, String uuid) {
        return dim + "@e:" + uuid;
    }

    public ContainerKind kindOrNull() {
        return ContainerKind.byId(kind);
    }

    public boolean isEntityBacked() {
        return entityUuid != null;
    }

    // ── Geometry ─────────────────────────────────────────────────────────────

    /** Centre of the container, spanning both halves for a double chest. */
    public double centerX() {
        return hasSecondary ? (x + x2) / 2.0 + 0.5 : x + 0.5;
    }

    public double centerY() {
        return hasSecondary ? (y + y2) / 2.0 + 0.5 : y + 0.5;
    }

    public double centerZ() {
        return hasSecondary ? (z + z2) / 2.0 + 0.5 : z + 0.5;
    }

    public double distanceSqTo(double px, double py, double pz) {
        double dx = centerX() - px;
        double dy = centerY() - py;
        double dz = centerZ() - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Horizontal distance only, ignoring Y.
     *
     * <p>This is what a chunk radius actually means: chunks load as full columns from bedrock to
     * build limit, so a container's depth has no bearing on whether its chunk is loaded. Culling
     * render distance on 3D distance makes deep containers vanish for no reason the player can see.
     */
    public double horizontalDistanceSqTo(double px, double pz) {
        double dx = centerX() - px;
        double dz = centerZ() - pz;
        return dx * dx + dz * dz;
    }

    // ── State ────────────────────────────────────────────────────────────────

    /** True if we have never looked inside. */
    public boolean isUnopened() {
        return lastScanned == 0;
    }

    /** Fraction of slots in use, 0..1. Returns 0 for containers we have never opened. */
    public float fullness() {
        if (slotCount <= 0) return 0f;
        return Math.max(0f, Math.min(1f, usedSlots / (float) slotCount));
    }

    /** True once the recorded contents are older than {@code staleAfterDays}. Never stale if the setting is 0. */
    public boolean isStale(int staleAfterDays) {
        if (staleAfterDays <= 0 || lastScanned == 0) return false;
        long ageMs = System.currentTimeMillis() - lastScanned;
        return ageMs > staleAfterDays * 86_400_000L;
    }

    /** Total count of a given registry id across this container, including stacks nested in shulkers. */
    public int totalOf(String itemId) {
        int total = 0;
        for (ItemEntry entry : items) {
            if (itemId.equals(entry.id)) total += entry.count;
        }
        return total;
    }

    /** Best human-readable name: your nickname, else the anvil name, else the container type. */
    public String displayName() {
        if (label != null && !label.isBlank()) return label;
        if (customName != null && !customName.isBlank()) return customName;
        ContainerKind k = kindOrNull();
        return k != null ? k.displayName() : "Container";
    }

    public String coordsString() {
        return x + ", " + y + ", " + z;
    }

    /** Compact dimension label for UI: {@code minecraft:the_nether} → {@code the_nether}. */
    public String shortDim() {
        if (dim == null) return "";
        int colon = dim.indexOf(':');
        return colon < 0 ? dim : dim.substring(colon + 1);
    }
}
