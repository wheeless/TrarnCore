package net.containerutil.data;

import net.containerutil.ContainerUtil;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Owns the live {@link ContainerIndex} and its persistence lifecycle.
 *
 * <p>Saving is debounced: gameplay dirties the index constantly (every chest you open, every
 * minecart that rolls past), and writing on each change would mean hammering the disk during
 * normal play. Instead a background thread checks once a second and writes only if something
 * actually changed, and we force a synchronous flush on disconnect so quitting never loses the
 * last few chests you sorted.
 */
public final class IndexManager {

    private static final long AUTOSAVE_INTERVAL_SECONDS = 5;

    private static final ContainerIndex INDEX = new ContainerIndex();
    private static final IndexStore STORE = new JsonIndexStore();

    private static volatile String activeWorldKey;
    private static ScheduledExecutorService autosaveExecutor;

    private IndexManager() {
    }

    public static ContainerIndex index() {
        return INDEX;
    }

    public static String activeWorldKey() {
        return activeWorldKey;
    }

    /** True once a world's index has been loaded and it is safe to record into it. */
    public static boolean isActive() {
        return activeWorldKey != null;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /** Loads the index for a world. Safe to call repeatedly with the same key — it no-ops. */
    public static synchronized void openWorld(String worldKey) {
        if (worldKey == null) return;
        if (worldKey.equals(activeWorldKey)) return;

        // Switching worlds without a disconnect event (Bungee-style server hops do this).
        if (activeWorldKey != null) closeWorld();

        List<ContainerRecord> loaded = STORE.load(worldKey);
        INDEX.replaceAll(loaded);
        activeWorldKey = worldKey;
        startAutosave();
        ContainerUtil.LOGGER.info("[ContainerUtil] Index active for '{}' ({} containers)",
            worldKey, INDEX.size());
    }

    /** Flushes any pending changes and drops the in-memory index. */
    public static synchronized void closeWorld() {
        stopAutosave();
        flushNow();
        INDEX.clear();
        activeWorldKey = null;
    }

    /** Writes immediately on the calling thread if anything is dirty. Used on disconnect and shutdown. */
    public static void flushNow() {
        String key = activeWorldKey;
        if (key == null || !INDEX.isDirty()) return;
        List<ContainerRecord> snapshot = INDEX.snapshot();
        INDEX.markClean();
        STORE.save(key, snapshot);
        ContainerUtil.LOGGER.info("[ContainerUtil] Flushed {} containers for '{}'", snapshot.size(), key);
    }

    private static void startAutosave() {
        stopAutosave();
        autosaveExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "containerutil-autosave");
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, ex) ->
                ContainerUtil.LOGGER.error("[ContainerUtil] {} died unexpectedly", t.getName(), ex));
            return thread;
        });
        autosaveExecutor.scheduleAtFixedRate(() -> {
            try {
                String key = activeWorldKey;
                if (key == null || !INDEX.isDirty()) return;
                // Snapshot and clear the flag before writing: anything dirtied during the write
                // simply gets picked up by the next tick rather than being lost.
                List<ContainerRecord> snapshot = INDEX.snapshot();
                INDEX.markClean();
                STORE.save(key, snapshot);
            } catch (Exception e) {
                ContainerUtil.LOGGER.error("[ContainerUtil] Autosave failed", e);
            }
        }, AUTOSAVE_INTERVAL_SECONDS, AUTOSAVE_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private static void stopAutosave() {
        if (autosaveExecutor != null && !autosaveExecutor.isShutdown()) {
            autosaveExecutor.shutdownNow();
        }
        autosaveExecutor = null;
    }
}
