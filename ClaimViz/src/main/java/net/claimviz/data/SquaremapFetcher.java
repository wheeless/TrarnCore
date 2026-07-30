package net.claimviz.data;

import com.google.gson.*;
import net.claimviz.ClaimViz;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SquaremapFetcher {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private static final Gson GSON = new Gson();

    private static final Pattern OWNER_PATTERN =
        Pattern.compile("Claim Owner:\\s*<span[^>]*>([^<]+)</span>");
    private static final Pattern SPAN_PATTERN =
        Pattern.compile("<span[^>]*>([^<]+)</span>");

    /**
     * Fetches and parses GriefPrevention claim markers for the given SquareMap dimension.
     *
     * @param baseUrl   SquareMap base URL, no trailing slash
     * @param dimension SquareMap dimension key, e.g. "minecraft_overworld"
     */
    public static CompletableFuture<List<ClaimRect>> fetch(String baseUrl, String dimension) {
        String url = baseUrl + "/tiles/" + dimension + "/markers.json";
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() != 200) {
                    ClaimViz.LOGGER.warn("markers.json for {} returned HTTP {}", dimension, response.statusCode());
                    return List.<ClaimRect>of();
                }
                return parse(response.body(), dimension);
            })
            .exceptionally(ex -> {
                ClaimViz.LOGGER.warn("Failed to fetch markers for {}: {}", dimension, ex.getMessage());
                return List.of();
            });
    }

    private static List<ClaimRect> parse(String json, String dimension) {
        List<ClaimRect> result = new ArrayList<>();
        try {
            JsonArray layers = GSON.fromJson(json, JsonArray.class);
            for (JsonElement layerEl : layers) {
                JsonObject layer = layerEl.getAsJsonObject();
                JsonElement idEl = layer.get("id");
                if (idEl == null || !"griefprevention".equals(idEl.getAsString())) continue;

                JsonArray markers = layer.getAsJsonArray("markers");
                if (markers == null) continue;

                for (JsonElement markerEl : markers) {
                    JsonObject marker = markerEl.getAsJsonObject();
                    JsonElement typeEl = marker.get("type");
                    if (typeEl == null || !"rectangle".equals(typeEl.getAsString())) continue;

                    JsonArray points = marker.getAsJsonArray("points");
                    if (points == null || points.size() < 2) continue;

                    JsonObject p0 = points.get(0).getAsJsonObject();
                    JsonObject p1 = points.get(1).getAsJsonObject();

                    int minX = Math.min(p0.get("x").getAsInt(), p1.get("x").getAsInt());
                    int maxX = Math.max(p0.get("x").getAsInt(), p1.get("x").getAsInt());
                    int minZ = Math.min(p0.get("z").getAsInt(), p1.get("z").getAsInt());
                    int maxZ = Math.max(p0.get("z").getAsInt(), p1.get("z").getAsInt());

                    String popup = marker.has("popup") ? marker.get("popup").getAsString() : "";
                    String fillColor = marker.has("fillColor") ? marker.get("fillColor").getAsString() : "#00ff00";

                    int color = parseHexColor(fillColor);
                    String owner = parseOwner(popup);

                    result.add(new ClaimRect(minX, maxX, minZ, maxZ, color, owner, dimension));
                }
            }
        } catch (Exception e) {
            ClaimViz.LOGGER.warn("Failed to parse markers.json: {}", e.getMessage());
        }
        return result;
    }

    private static int parseHexColor(String hex) {
        try {
            String stripped = hex.startsWith("#") ? hex.substring(1) : hex;
            return 0xFF000000 | Integer.parseInt(stripped, 16);
        } catch (NumberFormatException e) {
            return 0xFF00FF00;
        }
    }

    private static String parseOwner(String popup) {
        if (popup.contains("Administrator Claim")) return "Administrator";
        Matcher m = OWNER_PATTERN.matcher(popup);
        if (m.find()) return m.group(1);
        // Fallback: first span content
        Matcher fallback = SPAN_PATTERN.matcher(popup);
        if (fallback.find()) return fallback.group(1);
        return "Unknown";
    }
}
