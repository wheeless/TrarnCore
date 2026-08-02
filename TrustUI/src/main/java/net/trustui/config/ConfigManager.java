package net.trustui.config;

import net.trarncore.config.JsonConfig;

/**
 * Thin wrapper over TrarnCore's {@link JsonConfig} so call sites stay {@code ConfigManager.get()}.
 */
public class ConfigManager {

    private static final JsonConfig<TrustUIConfig> CONFIG =
        JsonConfig.of(TrustUIConfig.class, "trustui");

    public static void load() {
        CONFIG.load();
    }

    public static void save() {
        CONFIG.save();
    }

    public static TrustUIConfig get() {
        return CONFIG.get();
    }
}
