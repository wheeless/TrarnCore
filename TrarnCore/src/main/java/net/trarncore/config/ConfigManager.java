package net.trarncore.config;

/**
 * TrarnCore's own config, at {@code config/trarncore/config.json}.
 *
 * <p>The library holds no gameplay settings — this exists only for the update checker's
 * "already mentioned this version" bookkeeping and its on/off switch.
 */
public class ConfigManager {

    private static final JsonConfig<TrarnCoreConfig> CONFIG =
        JsonConfig.of(TrarnCoreConfig.class, "trarncore");

    public static void load() {
        CONFIG.load();
    }

    public static void save() {
        CONFIG.save();
    }

    public static TrarnCoreConfig get() {
        return CONFIG.get();
    }
}
