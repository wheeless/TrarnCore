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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class PlayerFetcher {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();
    private static final Gson GSON = new Gson();

    private static final AtomicReference<List<PlayerData>> cache = new AtomicReference<>(List.of());
    private static ScheduledExecutorService executor;
    private static volatile String baseUrl;
    private static volatile Consumer<List<PlayerData>> onUpdate;

    public static void setOnUpdate(Consumer<List<PlayerData>> cb) {
        onUpdate = cb;
    }

    public static void start(String url) {
        stop();
        baseUrl = url;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "claimviz-player-fetcher");
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((thread, ex) ->
                ClaimViz.LOGGER.error("[ClaimViz] {} died unexpectedly", thread.getName(), ex));
            return t;
        });
        executor.scheduleAtFixedRate(PlayerFetcher::fetchNow, 0, 1, TimeUnit.SECONDS);
    }

    public static void stop() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        executor = null;
        baseUrl = null;
        cache.set(List.of());
        onUpdate = null;
    }

    public static List<PlayerData> getCached() {
        return cache.get();
    }

    private static void fetchNow() {
        String url = baseUrl;
        if (url == null) return;
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url + "/tiles/players.json"))
            .timeout(Duration.ofSeconds(3))
            .GET()
            .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                List<PlayerData> parsed = parse(response.body());
                cache.set(parsed);
                Consumer<List<PlayerData>> cb = onUpdate;
                if (cb != null) cb.accept(parsed);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            ClaimViz.LOGGER.warn("[ClaimViz] Player fetch failed", e);
        }
    }

    private static List<PlayerData> parse(String json) {
        List<PlayerData> result = new ArrayList<>();
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            JsonArray players = root.getAsJsonArray("players");
            if (players == null) return result;
            for (JsonElement el : players) {
                JsonObject p = el.getAsJsonObject();
                result.add(new PlayerData(
                    p.get("name").getAsString(),
                    p.get("uuid").getAsString(),
                    p.get("x").getAsDouble(),
                    p.get("y").getAsDouble(),
                    p.get("z").getAsDouble(),
                    p.get("yaw").getAsFloat(),
                    p.get("health").getAsInt(),
                    p.get("armor").getAsInt(),
                    p.get("world").getAsString()
                ));
            }
        } catch (Exception e) {
            ClaimViz.LOGGER.warn("Failed to parse players.json: {}", e.getMessage());
        }
        return result;
    }
}
