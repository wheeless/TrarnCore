package net.trarncore.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The library's own settings.
 *
 * <p>TrarnCore has no features of its own and deliberately holds no gameplay config — this exists
 * solely because the update checker needs somewhere to persist "already told you about this
 * version", which is what stops it nagging on every single launch.
 */
public class TrarnCoreConfig implements ValidatedConfig {

    /** Check GitHub for newer releases of the installed mods. The only network access anything here makes. */
    public boolean checkForUpdates = true;

    /**
     * {@code owner/name} of the repository releases are read from. Configurable so a repo move
     * does not need a rebuild.
     */
    public String updateRepository = "wheeless/TrarnCore";

    /**
     * Mod id → the version we last mentioned. A newer release is announced once; after that it
     * stays quiet until an even newer one appears.
     */
    public Map<String, String> lastNotifiedVersion = new LinkedHashMap<>();

    @Override
    public void validate() {
        if (updateRepository == null || updateRepository.isBlank()) {
            updateRepository = "wheeless/TrarnCore";
        }
        if (lastNotifiedVersion == null) {
            lastNotifiedVersion = new LinkedHashMap<>();
        }
    }
}
