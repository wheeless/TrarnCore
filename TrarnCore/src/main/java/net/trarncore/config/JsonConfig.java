package net.trarncore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.trarncore.TrarnCore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Pretty-printed JSON config persisted to {@code config/<modId>/config.json}.
 *
 * <p>Replaces the near-identical {@code ConfigManager} each mod used to carry. Typical use is one
 * static instance per mod:
 * <pre>{@code
 * private static final JsonConfig<MyConfig> CONFIG = JsonConfig.of(MyConfig.class, "mymod");
 *
 * public static void load()      { CONFIG.load(); }
 * public static void save()      { CONFIG.save(); }
 * public static MyConfig get()   { return CONFIG.get(); }
 * }</pre>
 *
 * <p>If the config class implements {@link ValidatedConfig}, {@code validate()} runs after each
 * load and before each save.
 *
 * @param <T> the config type; needs a no-argument constructor unless a factory is supplied
 */
public final class JsonConfig<T> {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Class<T> type;
    private final String modId;
    private final Supplier<T> factory;
    private final Path directory;
    private final Path file;

    private T config;

    private JsonConfig(Class<T> type, String modId, String fileName, Supplier<T> factory) {
        this.type = type;
        this.modId = modId;
        this.factory = factory;
        this.directory = FabricLoader.getInstance().getConfigDir().resolve(modId);
        this.file = directory.resolve(fileName);
        this.config = factory.get();
    }

    public static <T> JsonConfig<T> of(Class<T> type, String modId, Supplier<T> factory) {
        return new JsonConfig<>(type, modId, "config.json", factory);
    }

    /**
     * Overload for a config stored under a name other than {@code config.json} — ClaimViz's
     * {@code servers.json}, for instance. Existing files must keep their name or the settings
     * they hold are silently orphaned.
     */
    public static <T> JsonConfig<T> of(Class<T> type, String modId, String fileName, Supplier<T> factory) {
        return new JsonConfig<>(type, modId, fileName, factory);
    }

    /** Convenience overload for config classes with a public no-argument constructor. */
    public static <T> JsonConfig<T> of(Class<T> type, String modId) {
        return of(type, modId, "config.json");
    }

    /** Convenience overload with a custom file name and a no-argument constructor. */
    public static <T> JsonConfig<T> of(Class<T> type, String modId, String fileName) {
        return new JsonConfig<>(type, modId, fileName, () -> {
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException(
                    type.getName() + " needs a public no-argument constructor, "
                        + "or use the JsonConfig.of overload that takes a factory", e);
            }
        });
    }

    public T get() {
        return config;
    }

    /** Directory this mod's config lives in — useful for mods that store more than one file. */
    public Path directory() {
        return directory;
    }

    /**
     * Reads the config, writing defaults if no file exists yet.
     *
     * <p>Never throws: a corrupt or unreadable file logs an error and falls back to defaults,
     * because failing to start over a bad config is worse than losing the settings.
     */
    public void load() {
        if (!Files.exists(file)) {
            validate();
            save();
            return;
        }
        try {
            T loaded = GSON.fromJson(Files.readString(file), type);
            config = loaded != null ? loaded : factory.get();
            TrarnCore.LOGGER.info("[{}] config loaded", modId);
        } catch (Exception e) {
            TrarnCore.LOGGER.error("[{}] failed to load config, using defaults", modId, e);
            config = factory.get();
        }
        validate();
    }

    public void save() {
        try {
            validate();
            Files.createDirectories(directory);
            Files.writeString(file, GSON.toJson(config));
        } catch (IOException e) {
            TrarnCore.LOGGER.error("[{}] failed to save config", modId, e);
        }
    }

    private void validate() {
        if (config instanceof ValidatedConfig validated) {
            try {
                validated.validate();
            } catch (Exception e) {
                TrarnCore.LOGGER.error("[{}] config validation failed", modId, e);
            }
        }
    }
}
