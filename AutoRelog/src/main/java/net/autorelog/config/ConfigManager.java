package net.autorelog.config;

import net.trarncore.config.JsonConfig;

/**
 * Thin wrapper over TrarnCore's {@link JsonConfig} so call sites stay {@code ConfigManager.get()}.
 * Clamping and rule parsing happen via {@link AutoRelogConfig}'s {@code ValidatedConfig}
 * implementation.
 */
public class ConfigManager {

    private static final JsonConfig<AutoRelogConfig> CONFIG =
        JsonConfig.of(AutoRelogConfig.class, "autorelog");

    public static void load() {
        CONFIG.load();
    }

    public static void save() {
        CONFIG.save();
    }

    public static AutoRelogConfig get() {
        return CONFIG.get();
    }
}
