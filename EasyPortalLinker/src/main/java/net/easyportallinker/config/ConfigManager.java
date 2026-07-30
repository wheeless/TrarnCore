package net.easyportallinker.config;

import net.trarncore.config.JsonConfig;

/**
 * Thin wrapper over TrarnCore's {@link JsonConfig} so call sites stay {@code ConfigManager.get()}.
 */
public class ConfigManager {

    private static final JsonConfig<EasyPortalLinkerConfig> CONFIG =
        JsonConfig.of(EasyPortalLinkerConfig.class, "easyportallinker");

    public static void load() {
        CONFIG.load();
    }

    public static void save() {
        CONFIG.save();
    }

    public static EasyPortalLinkerConfig get() {
        return CONFIG.get();
    }
}
