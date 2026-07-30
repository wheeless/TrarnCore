package net.claimviz.map;

import net.claimviz.ClaimViz;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TileTextureCache {

    public enum TileState { LOADING, LOADED, MISSING }

    public record CacheEntry(TileState state, Identifier textureId, long fetchedAt) {}

    private static final int MAX_UPLOADS_PER_FRAME = 4;

    private final int budget;
    private final LinkedHashMap<TileCoord, CacheEntry> cache;
    private final ConcurrentLinkedQueue<Map.Entry<TileCoord, byte[]>> uploadQueue =
        new ConcurrentLinkedQueue<>();
    private volatile boolean closed = false;

    public TileTextureCache(int budget) {
        this.budget = Math.max(16, budget);
        this.cache = new LinkedHashMap<>(this.budget * 2, 0.75f, true);
    }

    public boolean has(TileCoord coord) {
        return cache.containsKey(coord);
    }

    public CacheEntry get(TileCoord coord) {
        return cache.get(coord);
    }

    public void markLoading(TileCoord coord) {
        cache.put(coord, new CacheEntry(TileState.LOADING, null, System.currentTimeMillis()));
    }

    public void markMissing(TileCoord coord) {
        cache.put(coord, new CacheEntry(TileState.MISSING, null, System.currentTimeMillis()));
    }

    public void enqueueUpload(TileCoord coord, byte[] bytes) {
        if (!closed) uploadQueue.offer(Map.entry(coord, bytes));
    }

    /** Must be called on the render thread — top of every MapScreen.render(). */
    public void processUploadQueue() {
        MinecraftClient client = MinecraftClient.getInstance();
        int processed = 0;
        while (processed < MAX_UPLOADS_PER_FRAME) {
            Map.Entry<TileCoord, byte[]> pending = uploadQueue.poll();
            if (pending == null) break;
            if (closed) break;
            TileCoord coord = pending.getKey();
            byte[] bytes = pending.getValue();
            try {
                NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
                NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "claimviz:" + coord, img);
                Identifier id = Identifier.of("claimviz",
                    "maptile/" + coord.dimension() + "/" + coord.zoom()
                        + "/" + coord.tileX() + "_" + coord.tileZ());
                client.getTextureManager().registerTexture(id, tex);

                CacheEntry old = cache.get(coord);
                if (old != null && old.textureId() != null) {
                    client.getTextureManager().destroyTexture(old.textureId());
                }
                cache.put(coord, new CacheEntry(TileState.LOADED, id, System.currentTimeMillis()));
                evictIfOverBudget(client);
                processed++;
            } catch (Exception e) {
                ClaimViz.LOGGER.warn("[ClaimViz] Failed to upload tile texture for {}", coord, e);
                cache.put(coord, new CacheEntry(TileState.MISSING, null, System.currentTimeMillis()));
            }
        }
    }

    private void evictIfOverBudget(MinecraftClient client) {
        var it = cache.entrySet().iterator();
        while (cache.size() > budget && it.hasNext()) {
            CacheEntry entry = it.next().getValue();
            it.remove();
            if (entry.textureId() != null) {
                client.getTextureManager().destroyTexture(entry.textureId());
            }
        }
    }

    /** Destroy all GPU textures. Must be called on the render thread. */
    public void clear() {
        closed = true;
        uploadQueue.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        for (CacheEntry entry : cache.values()) {
            if (entry.textureId() != null) {
                client.getTextureManager().destroyTexture(entry.textureId());
            }
        }
        cache.clear();
    }
}
