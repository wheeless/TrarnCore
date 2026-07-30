package net.claimviz.map;

import net.claimviz.ClaimViz;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class TileFetcher {

    private static final int MAX_CONCURRENT = 8;

    private final HttpClient http;
    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT);
    private final ConcurrentHashMap<TileCoord, CompletableFuture<Optional<byte[]>>> inFlight =
        new ConcurrentHashMap<>();

    public TileFetcher() {
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    public CompletableFuture<Optional<byte[]>> fetch(String baseUrl, TileCoord coord) {
        return inFlight.computeIfAbsent(coord, k -> launch(baseUrl, k)
            .whenComplete((r, ex) -> inFlight.remove(k)));
    }

    private CompletableFuture<Optional<byte[]>> launch(String baseUrl, TileCoord coord) {
        String url = baseUrl + "/tiles/" + coord.dimension() + "/" + coord.zoom()
            + "/" + coord.tileX() + "_" + coord.tileZ() + ".png";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        return CompletableFuture.supplyAsync(() -> {
            try {
                semaphore.acquire();
                try {
                    HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    if (response.statusCode() == 200) {
                        return Optional.of(response.body());
                    } else if (response.statusCode() != 404) {
                        ClaimViz.LOGGER.warn("[ClaimViz] Tile {}/{}/{}_{} returned HTTP {}",
                            coord.dimension(), coord.zoom(), coord.tileX(), coord.tileZ(),
                            response.statusCode());
                    }
                    return Optional.<byte[]>empty();
                } finally {
                    semaphore.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.<byte[]>empty();
            } catch (Exception e) {
                ClaimViz.LOGGER.warn("[ClaimViz] Tile fetch failed for {}/{}/{}_{}: {}",
                    coord.dimension(), coord.zoom(), coord.tileX(), coord.tileZ(), e.getMessage());
                return Optional.<byte[]>empty();
            }
        });
    }
}
