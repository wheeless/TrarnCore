package net.blowbyblow.config;

import net.trarncore.config.JsonConfig;

/**
 * Thin wrapper over TrarnCore's {@link JsonConfig} so call sites stay {@code ConfigManager.get()}.
 * Clamping happens via {@link BlowByBlowConfig}'s {@code ValidatedConfig} implementation.
 */
public class ConfigManager {

    private static final JsonConfig<BlowByBlowConfig> CONFIG =
        JsonConfig.of(BlowByBlowConfig.class, "blowbyblow");

    public static void load() {
        CONFIG.load();
    }

    public static void save() {
        CONFIG.save();
    }

    public static BlowByBlowConfig get() {
        return CONFIG.get();
    }
}
