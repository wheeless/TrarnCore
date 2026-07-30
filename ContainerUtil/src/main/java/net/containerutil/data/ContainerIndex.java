package net.containerutil.data;

import net.containerutil.container.ContainerKind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory container index for the world you are currently in.
 *
 * <p>Holds every known container keyed by position (or entity UUID), plus an inverted
 * item-id → container-keys map. Search itself runs a full predicate pass over the records —
 * the query language is too expressive to serve from an index — but the inverted map makes
 * the hot path of "which containers hold exactly this item" free, which is what the search
 * screen's hover panel hits on every frame.
 *
 * <p>Reads happen on the render thread and writes on the client thread (in vanilla these are
 * the same thread, but that is not a guarantee worth betting on), and the save executor takes
 * snapshots off-thread, so the maps are concurrent throughout.
 */
public class ContainerIndex {

    private final Map<String, ContainerRecord> records = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> itemToKeys = new ConcurrentHashMap<>();

    private volatile boolean dirty = false;

    // ── Bulk ─────────────────────────────────────────────────────────────────

    /** Replaces the entire index — used when a world is loaded from disk. */
    public void replaceAll(Collection<ContainerRecord> loaded) {
        records.clear();
        itemToKeys.clear();
        for (ContainerRecord record : loaded) {
            if (record == null || record.dim == null) continue;
            if (record.items == null) record.items = new ArrayList<>();
            records.put(record.key(), record);
            indexItems(record);
        }
        dirty = false;
    }

    public void clear() {
        records.clear();
        itemToKeys.clear();
        dirty = false;
    }

    /** Stable copy for the save thread. */
    public List<ContainerRecord> snapshot() {
        return new ArrayList<>(records.values());
    }

    public int size() {
        return records.size();
    }

    public Collection<ContainerRecord> all() {
        return Collections.unmodifiableCollection(records.values());
    }

    public ContainerRecord get(String key) {
        return records.get(key);
    }

    // ── Sightings ────────────────────────────────────────────────────────────

    /**
     * Records that a block container exists at this position, creating it if new. Contents are
     * untouched — walking past a chest tells you it is there, not what is in it.
     *
     * @param secondary far half of a double chest, or {@code null}
     */
    public ContainerRecord upsertBlockSighting(ContainerKind kind, String dim,
                                               int x, int y, int z, int[] secondary) {
        String key = ContainerRecord.blockKey(dim, x, y, z);
        ContainerRecord record = records.get(key);
        if (record == null) {
            record = new ContainerRecord();
            record.dim = dim;
            record.x = x;
            record.y = y;
            record.z = z;
            records.put(key, record);
            dirty = true;
        }
        if (!kind.id().equals(record.kind)) {
            record.kind = kind.id();
            dirty = true;
        }
        boolean wantsSecondary = secondary != null;
        if (wantsSecondary != record.hasSecondary
            || (wantsSecondary && (record.x2 != secondary[0] || record.y2 != secondary[1] || record.z2 != secondary[2]))) {
            record.hasSecondary = wantsSecondary;
            if (wantsSecondary) {
                record.x2 = secondary[0];
                record.y2 = secondary[1];
                record.z2 = secondary[2];
            }
            dirty = true;
        }
        record.lastSeen = System.currentTimeMillis();
        return record;
    }

    /** Same as {@link #upsertBlockSighting} for a mobile container, which is keyed by UUID and whose position drifts. */
    public ContainerRecord upsertEntitySighting(ContainerKind kind, String dim, String uuid,
                                                int x, int y, int z) {
        String key = ContainerRecord.entityKey(dim, uuid);
        ContainerRecord record = records.get(key);
        if (record == null) {
            record = new ContainerRecord();
            record.dim = dim;
            record.entityUuid = uuid;
            records.put(key, record);
            dirty = true;
        }
        if (!kind.id().equals(record.kind)) {
            record.kind = kind.id();
            dirty = true;
        }
        // Position changes constantly for a moving minecart; track it but do not mark the index
        // dirty for it, or a rolling minecart would trigger a disk write every tick.
        record.x = x;
        record.y = y;
        record.z = z;
        record.lastSeen = System.currentTimeMillis();
        return record;
    }

    // ── Contents ─────────────────────────────────────────────────────────────

    /** Replaces a container's recorded contents. Called when you close its screen. */
    public void recordContents(ContainerRecord record, List<ItemEntry> items,
                               int slotCount, int usedSlots, String customName) {
        unindexItems(record);
        record.items = items != null ? items : new ArrayList<>();
        record.slotCount = slotCount;
        record.usedSlots = usedSlots;
        record.customName = customName;
        record.lastScanned = System.currentTimeMillis();
        record.lastSeen = record.lastScanned;
        indexItems(record);
        dirty = true;
    }

    public void setLabel(ContainerRecord record, String label) {
        record.label = (label == null || label.isBlank()) ? null : label;
        dirty = true;
    }

    public void remove(String key) {
        ContainerRecord record = records.remove(key);
        if (record != null) {
            unindexItems(record);
            dirty = true;
        }
    }

    // ── Inverted lookup ──────────────────────────────────────────────────────

    /** Containers holding at least one of the given registry id. Cheap — this is the indexed path. */
    public List<ContainerRecord> withItem(String itemId) {
        Set<String> keys = itemToKeys.get(itemId);
        if (keys == null || keys.isEmpty()) return List.of();
        List<ContainerRecord> out = new ArrayList<>(keys.size());
        for (String key : keys) {
            ContainerRecord record = records.get(key);
            if (record != null) out.add(record);
        }
        return out;
    }

    /**
     * The {@code n} containers nearest the given point that hold this item, nearest first.
     * This is what the search screen's hover panel shows.
     */
    public List<ContainerRecord> nearestWithItem(String itemId, double px, double py, double pz,
                                                 String dimFilter, int n) {
        List<ContainerRecord> candidates = new ArrayList<>(withItem(itemId));
        if (dimFilter != null) {
            candidates.removeIf(record -> !dimFilter.equals(record.dim));
        }
        candidates.sort((a, b) -> Double.compare(a.distanceSqTo(px, py, pz), b.distanceSqTo(px, py, pz)));
        return candidates.size() <= n ? candidates : new ArrayList<>(candidates.subList(0, n));
    }

    /** Total count of an item across every indexed container. */
    public int grandTotalOf(String itemId) {
        int total = 0;
        for (ContainerRecord record : withItem(itemId)) {
            total += record.totalOf(itemId);
        }
        return total;
    }

    private void indexItems(ContainerRecord record) {
        if (record.items == null) return;
        String key = record.key();
        for (ItemEntry entry : record.items) {
            if (entry == null || entry.id == null) continue;
            itemToKeys.computeIfAbsent(entry.id, id -> ConcurrentHashMap.newKeySet()).add(key);
        }
    }

    private void unindexItems(ContainerRecord record) {
        if (record.items == null) return;
        String key = record.key();
        for (ItemEntry entry : record.items) {
            if (entry == null || entry.id == null) continue;
            Set<String> keys = itemToKeys.get(entry.id);
            if (keys != null) {
                keys.remove(key);
                if (keys.isEmpty()) itemToKeys.remove(entry.id);
            }
        }
    }

    // ── Dirty tracking ───────────────────────────────────────────────────────

    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        dirty = false;
    }

    public void markDirty() {
        dirty = true;
    }
}
