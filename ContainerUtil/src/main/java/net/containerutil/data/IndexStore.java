package net.containerutil.data;

import java.util.List;

/**
 * Persistence backend for the container index.
 *
 * <p>Kept as an interface with a deliberately coarse load/save shape so the whole storage
 * layer can be swapped without touching anything else. {@link JsonIndexStore} is the default;
 * a SQLite-backed implementation would slot in here if an index ever outgrows a single file.
 */
public interface IndexStore {

    /** Loads every container recorded for a world. Returns an empty list if nothing is stored yet. */
    List<ContainerRecord> load(String worldKey);

    /** Writes the full set of containers for a world, replacing whatever was there. */
    void save(String worldKey, List<ContainerRecord> records);

    /** Deletes a world's stored index entirely. */
    void delete(String worldKey);
}
