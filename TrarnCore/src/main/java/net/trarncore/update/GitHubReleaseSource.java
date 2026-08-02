package net.trarncore.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.trarncore.TrarnCore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads published versions from a repository's GitHub Releases.
 *
 * <p><b>One request covers every mod.</b> All of these live in a single monorepo, so a single call
 * to {@code /releases} returns every release for every mod. Unauthenticated GitHub allows 60
 * requests an hour per IP; one call per game launch is nowhere near that.
 *
 * <p><b>Versions come from asset filenames, not tag names.</b> The repo uses two tag shapes —
 * {@code containerutil-v0.1.0} for a single mod and {@code all-v0.1.0} for a bundle — and a bundle
 * tag encodes no per-mod version at all. The attached assets always do
 * ({@code containerutil-0.1.0.jar}), so reading filenames works identically for both shapes and
 * needs no knowledge of the tagging convention. A bundle release simply contributes several mods
 * at once.
 */
public final class GitHubReleaseSource implements ReleaseSource {

    /** {@code <modid>-<version>.jar}, rejecting the {@code -sources.jar} sitting beside it. */
    private static final Pattern ASSET = Pattern.compile("^([a-z0-9_-]+?)-(\\d[\\w.+-]*)\\.jar$");

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final String repository;

    /** @param repository {@code owner/name}, e.g. {@code wheeless/TrarnCore} */
    public GitHubReleaseSource(String repository) {
        this.repository = repository;
    }

    @Override
    public Map<String, Available> latestVersions() {
        Map<String, Available> newest = new HashMap<>();
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + repository + "/releases?per_page=30"))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                // GitHub asks for a User-Agent and rejects requests without one.
                .header("User-Agent", "TrarnCore-UpdateChecker")
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // 403 here is almost always the rate limit, and 404 a private or renamed repo.
                // Neither is worth interrupting the player over.
                TrarnCore.LOGGER.debug("[update] {} returned HTTP {}", repository, response.statusCode());
                return Map.of();
            }

            JsonElement parsed = JsonParser.parseString(response.body());
            if (!parsed.isJsonArray()) return Map.of();

            for (JsonElement element : parsed.getAsJsonArray()) {
                collectRelease(element, newest);
            }
        } catch (Exception e) {
            // Offline, DNS failure, timeout, malformed response — all the same answer: say nothing.
            TrarnCore.LOGGER.debug("[update] check failed for {}: {}", repository, e.toString());
            return Map.of();
        }
        return newest;
    }

    private static void collectRelease(JsonElement element, Map<String, Available> newest) {
        if (!element.isJsonObject()) return;
        JsonObject release = element.getAsJsonObject();

        // Drafts are not published, and pre-releases are opt-in by definition.
        if (bool(release, "draft") || bool(release, "prerelease")) return;

        String url = string(release, "html_url");
        JsonElement assetsElement = release.get("assets");
        if (assetsElement == null || !assetsElement.isJsonArray()) return;
        JsonArray assets = assetsElement.getAsJsonArray();

        for (JsonElement assetElement : assets) {
            if (!assetElement.isJsonObject()) continue;
            String name = string(assetElement.getAsJsonObject(), "name");
            if (name == null) continue;

            Matcher matcher = ASSET.matcher(name);
            if (!matcher.matches()) continue;

            String modId = matcher.group(1);
            ModVersion version = ModVersion.parse(matcher.group(2));
            if (version == null) continue;

            // Releases come back newest-first, but bundle and per-mod releases interleave, so
            // take the highest version seen rather than trusting order.
            Available existing = newest.get(modId);
            if (existing == null || version.isNewerThan(existing.version())) {
                newest.put(modId, new Available(version, url));
            }
        }
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && !value.isJsonNull() && value.getAsBoolean();
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }
}
