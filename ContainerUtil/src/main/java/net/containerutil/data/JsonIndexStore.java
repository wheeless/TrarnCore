package net.containerutil.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.containerutil.ContainerUtil;
import net.containerutil.config.ConfigManager;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores one JSON file per world under {@code config/containerutil/index/}.
 *
 * <p>Writes go to a temp file and are then moved into place, so a crash mid-save cannot leave
 * you with a half-written index — the worst case is losing the most recent save, not the
 * whole database.
 */
public class JsonIndexStore implements IndexStore {

    /** Bump when the on-disk shape changes incompatibly; {@link #load} refuses newer versions. */
    private static final int FORMAT_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Envelope around the container list — the version field is the whole point. */
    private static class IndexFile {
        int formatVersion = FORMAT_VERSION;
        String worldKey;
        long savedAt;
        List<ContainerRecord> containers = new ArrayList<>();
    }

    private Path indexDir() {
        return ConfigManager.dataDir().resolve("index");
    }

    private Path fileFor(String worldKey) {
        return indexDir().resolve(worldKey + ".json");
    }

    @Override
    public List<ContainerRecord> load(String worldKey) {
        Path path = fileFor(worldKey);
        if (!Files.exists(path)) return new ArrayList<>();
        try {
            String json = Files.readString(path);
            IndexFile file = GSON.fromJson(json, new TypeToken<IndexFile>() {}.getType());
            if (file == null || file.containers == null) return new ArrayList<>();
            if (file.formatVersion > FORMAT_VERSION) {
                ContainerUtil.LOGGER.warn(
                    "[ContainerUtil] Index for '{}' is format v{} but this build understands v{} — "
                        + "not loading it, so a newer version's data is not clobbered.",
                    worldKey, file.formatVersion, FORMAT_VERSION);
                return new ArrayList<>();
            }
            ContainerUtil.LOGGER.info("[ContainerUtil] Loaded {} containers for '{}'",
                file.containers.size(), worldKey);
            return file.containers;
        } catch (Exception e) {
            ContainerUtil.LOGGER.error("[ContainerUtil] Failed to load index for '{}'", worldKey, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void save(String worldKey, List<ContainerRecord> records) {
        Path path = fileFor(worldKey);
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(indexDir());

            IndexFile file = new IndexFile();
            file.worldKey = worldKey;
            file.savedAt = System.currentTimeMillis();
            file.containers = records;

            Files.writeString(temp, GSON.toJson(file));
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Some filesystems (notably a few network mounts) cannot do this atomically.
                // A plain replace is still far better than writing the real file in place.
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            ContainerUtil.LOGGER.error("[ContainerUtil] Failed to save index for '{}'", worldKey, e);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // Nothing useful to do — the stale temp file is harmless and gets overwritten next save.
            }
        }
    }

    @Override
    public void delete(String worldKey) {
        try {
            Files.deleteIfExists(fileFor(worldKey));
        } catch (IOException e) {
            ContainerUtil.LOGGER.error("[ContainerUtil] Failed to delete index for '{}'", worldKey, e);
        }
    }
}
