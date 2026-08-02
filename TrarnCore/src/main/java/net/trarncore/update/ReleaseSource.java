package net.trarncore.update;

import java.util.Map;

/**
 * Where published versions are looked up.
 *
 * <p>An interface with one implementation today ({@link GitHubReleaseSource}) because the GitHub
 * one is explicitly a stopgap: once these mods are on Modrinth, its API is version-aware per
 * project <em>and</em> per Minecraft version, which is strictly better than reading filenames off
 * release assets. Keeping the lookup behind this boundary means that swap replaces one class
 * rather than threading through the checker.
 */
public interface ReleaseSource {

    /**
     * Returns the newest published version of each mod it knows about, keyed by mod id.
     *
     * <p>Implementations must not throw: a checker that crashes the game because GitHub was
     * briefly unreachable is far worse than one that silently says nothing. Return an empty map
     * on any failure.
     */
    Map<String, Available> latestVersions();

    /**
     * @param version    newest published version
     * @param releaseUrl page a human can open to get it
     */
    record Available(ModVersion version, String releaseUrl) {
    }
}
