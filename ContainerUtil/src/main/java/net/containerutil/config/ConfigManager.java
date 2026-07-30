package net.containerutil.config;

import net.fabricmc.loader.api.FabricLoader;
import net.trarncore.config.JsonConfig;

import java.nio.file.Path;

/**
 * Thin wrapper over TrarnCore's {@link JsonConfig} so call sites stay {@code ConfigManager.get()}.
 * Defaults and clamping happen via {@link ContainerUtilConfig}'s {@code ValidatedConfig} hook.
 */
public class ConfigManager {

    private static final JsonConfig<ContainerUtilConfig> CONFIG =
        JsonConfig.of(ContainerUtilConfig.class, "containerutil");

    public static void load() {
        CONFIG.load();
    }

    public static void save() {
        CONFIG.save();
    }

    public static ContainerUtilConfig get() {
        return CONFIG.get();
    }

    /** Root for everything ContainerUtil writes — config plus the per-world index files. */
    public static Path dataDir() {
        return CONFIG.directory();
    }
}
