package net.claimviz.map;

import net.claimviz.config.ClaimVizConfig;
import net.claimviz.config.ConfigManager;
import net.claimviz.data.ClaimCache;
import net.claimviz.data.ClaimRect;
import net.claimviz.data.PlayerData;
import net.claimviz.data.PlayerFetcher;
import net.claimviz.event.ServerJoinHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class MapScreen extends Screen {

    // SquareMap tiles are always 512x512 PNG images
    private static final int TILE_IMG_SIZE = 512;

    private static final int COLOR_BACKGROUND = 0xFF111111;
    private static final int COLOR_LOADING_A  = 0xFF1A1A1A;
    private static final int COLOR_LOADING_B  = 0xFF222222;
    private static final int COLOR_MISSING    = 0xFF0D0D0D;

    private final ClaimVizConfig.ServerConfig config;
    private String dimension;
    private double centerX;
    private double centerZ;
    private int zoom = 3;

    private final TileFetcher fetcher;
    private final TileTextureCache texCache;

    // TTL re-fetch stagger: max per second
    private static final int MAX_REFETCH_PER_SECOND = 3;
    private long refetchWindowSecond = 0;
    private int refetchCountThisSecond = 0;

    public MapScreen(ClaimVizConfig.ServerConfig config, String dimension) {
        super(Text.literal("ClaimViz Map"));
        this.config = config;
        this.dimension = dimension;
        this.fetcher = new TileFetcher();
        this.texCache = new TileTextureCache(ConfigManager.get().mapTileBudget);
    }

    @Override
    public void init() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            centerX = client.player.getX();
            centerZ = client.player.getZ();
        }
        requestVisibleTiles();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        texCache.processUploadQueue();

        // Handle dimension change while map is open
        String currentDim = ServerJoinHandler.getLastDimension();
        if (currentDim != null && !currentDim.equals(dimension)) {
            dimension = currentDim;
            texCache.clear();
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                centerX = client.player.getX();
                centerZ = client.player.getZ();
            }
            requestVisibleTiles();
        }

        context.fill(0, 0, width, height, COLOR_BACKGROUND);

        int blocksPerTile = 512 * (1 << (3 - zoom));
        double pixelsPerBlock = 1.0 / (1 << (3 - zoom));
        double halfBlocksW = (width  / 2.0) / pixelsPerBlock;
        double halfBlocksH = (height / 2.0) / pixelsPerBlock;

        int minTileX = Math.floorDiv((int) Math.floor(centerX - halfBlocksW), blocksPerTile);
        int maxTileX = Math.floorDiv((int) Math.ceil (centerX + halfBlocksW), blocksPerTile);
        int minTileZ = Math.floorDiv((int) Math.floor(centerZ - halfBlocksH), blocksPerTile);
        int maxTileZ = Math.floorDiv((int) Math.ceil (centerZ + halfBlocksH), blocksPerTile);

        long nowMs = System.currentTimeMillis();
        long nowSec = nowMs / 1000;
        if (nowSec != refetchWindowSecond) {
            refetchWindowSecond = nowSec;
            refetchCountThisSecond = 0;
        }

        for (int tx = minTileX; tx <= maxTileX; tx++) {
            for (int tz = minTileZ; tz <= maxTileZ; tz++) {
                TileCoord coord = new TileCoord(dimension, zoom, tx, tz);
                TileTextureCache.CacheEntry entry = texCache.get(coord);

                // Tile top-left in screen space
                int sx = (int) Math.round(width  / 2.0 + ((double) tx * blocksPerTile - centerX) * pixelsPerBlock);
                int sz = (int) Math.round(height / 2.0 + ((double) tz * blocksPerTile - centerZ) * pixelsPerBlock);
                // Tiles are always TILE_IMG_SIZE screen pixels wide/tall at all zoom levels
                // (blocksPerTile * pixelsPerBlock = 512 * 2^(3-z) * 1/2^(3-z) = 512)
                int tilePx = TILE_IMG_SIZE;

                if (entry == null) {
                    drawCheckerboard(context, sx, sz, tilePx, (tx + tz) % 2 == 0);
                    requestTile(coord);
                } else {
                    switch (entry.state()) {
                        case LOADING -> drawCheckerboard(context, sx, sz, tilePx, (tx + tz) % 2 == 0);
                        case MISSING -> context.fill(sx, sz, sx + tilePx, sz + tilePx, COLOR_MISSING);
                        case LOADED -> {
                            context.drawTexturedQuad(entry.textureId(),
                                sx, sz, sx + tilePx, sz + tilePx, 0f, 1f, 0f, 1f);
                            // Queue re-fetch if TTL expired and we haven't hit the stagger limit
                            if (config.mapTileRefreshSeconds > 0
                                    && refetchCountThisSecond < MAX_REFETCH_PER_SECOND
                                    && nowMs - entry.fetchedAt() > config.mapTileRefreshSeconds * 1000L) {
                                refetchCountThisSecond++;
                                texCache.markLoading(coord);
                                final TileCoord fc = coord;
                                fetcher.fetch(config.squaremapUrl, fc).thenAccept(opt -> {
                                    if (opt.isPresent()) texCache.enqueueUpload(fc, opt.get());
                                    else texCache.markMissing(fc);
                                });
                            }
                        }
                    }
                }
            }
        }

        ClaimRect hovered = hoveredClaim(mouseX, mouseY);
        drawOverlays(context, pixelsPerBlock, hovered);
        drawHud(context, mouseX, mouseY);
        if (hovered != null) drawClaimTooltip(context, hovered, mouseX, mouseY);
    }

    private void drawCheckerboard(DrawContext context, int sx, int sz, int tilePx, boolean dark) {
        context.fill(sx, sz, sx + tilePx, sz + tilePx, dark ? COLOR_LOADING_A : COLOR_LOADING_B);
    }

    private void drawOverlays(DrawContext context, double pixelsPerBlock, ClaimRect hovered) {
        MinecraftClient client = MinecraftClient.getInstance();
        String playerName = client.player != null ? client.player.getGameProfile().name() : "";
        String selfUuid   = client.player != null ? client.player.getUuidAsString().replace("-", "") : "";
        double selfX = client.player != null ? client.player.getX() : 0;
        double selfZ = client.player != null ? client.player.getZ() : 0;
        double renderDistSq = (double) config.playerRenderDistance * config.playerRenderDistance;

        // ── Claim borders ──────────────────────────────────────────────────────
        for (ClaimRect claim : ClaimCache.get(dimension)) {
            int sx1 = blockToScreenX(claim.minX(), pixelsPerBlock);
            int sz1 = blockToScreenZ(claim.minZ(), pixelsPerBlock);
            int sx2 = blockToScreenX(claim.maxX(), pixelsPerBlock);
            int sz2 = blockToScreenZ(claim.maxZ(), pixelsPerBlock);

            if (sx2 < 0 || sz2 < 0 || sx1 > width || sz1 > height) continue;

            if (claim.equals(hovered)) {
                context.fill(sx1, sz1, sx2, sz2, 0x33FFFFFF);
            }

            int color = 0xFF000000 | claimColor(claim, playerName);
            if (sx2 - sx1 < 2 || sz2 - sz1 < 2) {
                context.fill(sx1, sz1, Math.max(sx1 + 1, sx2), Math.max(sz1 + 1, sz2), color);
            } else {
                context.fill(sx1,     sz1,     sx2, sz1 + 1, color); // top
                context.fill(sx1,     sz2 - 1, sx2, sz2,     color); // bottom
                context.fill(sx1,     sz1,     sx1 + 1, sz2, color); // left
                context.fill(sx2 - 1, sz1,     sx2,     sz2, color); // right
            }
        }

        // ── Other players from SquareMap data ──────────────────────────────────
        for (PlayerData pd : PlayerFetcher.getCached()) {
            if (!dimension.equals(pd.world())) continue;
            if (selfUuid.equals(pd.uuid())) continue;
            double dx = pd.x() - selfX, dz = pd.z() - selfZ;
            if (dx * dx + dz * dz > renderDistSq) continue;

            int px = blockToScreenX(pd.x(), pixelsPerBlock);
            int pz = blockToScreenZ(pd.z(), pixelsPerBlock);
            context.fill(px - 2, pz - 2, px + 2, pz + 2, 0xFFFF5555);
            context.drawText(client.textRenderer, pd.name(), px + 4, pz - 4, 0xFFFFFFFF, true);
        }

        // ── Self — always drawn from local position for accuracy ───────────────
        if (client.player != null) {
            int px = blockToScreenX(client.player.getX(), pixelsPerBlock);
            int pz = blockToScreenZ(client.player.getZ(), pixelsPerBlock);
            context.fill(px - 3, pz - 3, px + 3, pz + 3, 0xFFFFFF00);
            context.drawText(client.textRenderer, "You", px + 5, pz - 4, 0xFFFFFF00, true);
        }
    }

    private void drawHud(DrawContext context, int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        double bpp = 1 << (3 - zoom);

        // Dimension + zoom (top-left)
        String dimLabel = dimension + "  [z" + zoom + "]";
        context.drawText(client.textRenderer, dimLabel, 4, 4, 0xFFCCCCCC, true);

        // Cursor block coordinates (top-right)
        int cursorBX = (int) Math.floor(centerX + (mouseX - width  / 2.0) * bpp);
        int cursorBZ = (int) Math.floor(centerZ + (mouseY - height / 2.0) * bpp);
        String coords = cursorBX + ", " + cursorBZ;
        int tw = client.textRenderer.getWidth(coords);
        context.drawText(client.textRenderer, coords, width - tw - 4, 4, 0xFFCCCCCC, true);

        // Controls hint (bottom-center)
        String hint = "[Drag] Pan   [Scroll] Zoom   [Esc] Close";
        int hw = client.textRenderer.getWidth(hint);
        context.drawText(client.textRenderer, hint, (width - hw) / 2, height - 12, 0xFF888888, true);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        double bpp = 1 << (3 - zoom);
        centerX -= deltaX * bpp;
        centerZ -= deltaY * bpp;
        requestVisibleTiles();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double bppOld = 1 << (3 - zoom);
        double cursorBX = centerX + (mouseX - width  / 2.0) * bppOld;
        double cursorBZ = centerZ + (mouseY - height / 2.0) * bppOld;
        zoom = Math.clamp(zoom + (verticalAmount > 0 ? 1 : -1), 0, 3);
        double bppNew = 1 << (3 - zoom);
        centerX = cursorBX - (mouseX - width  / 2.0) * bppNew;
        centerZ = cursorBZ - (mouseY - height / 2.0) * bppNew;
        requestVisibleTiles();
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        texCache.clear();
    }

    private void requestVisibleTiles() {
        if (width == 0 || height == 0) return;
        int blocksPerTile = 512 * (1 << (3 - zoom));
        double pixelsPerBlock = 1.0 / (1 << (3 - zoom));
        double halfBlocksW = (width  / 2.0) / pixelsPerBlock;
        double halfBlocksH = (height / 2.0) / pixelsPerBlock;

        int minTileX = Math.floorDiv((int) Math.floor(centerX - halfBlocksW), blocksPerTile);
        int maxTileX = Math.floorDiv((int) Math.ceil (centerX + halfBlocksW), blocksPerTile);
        int minTileZ = Math.floorDiv((int) Math.floor(centerZ - halfBlocksH), blocksPerTile);
        int maxTileZ = Math.floorDiv((int) Math.ceil (centerZ + halfBlocksH), blocksPerTile);

        for (int tx = minTileX; tx <= maxTileX; tx++) {
            for (int tz = minTileZ; tz <= maxTileZ; tz++) {
                requestTile(new TileCoord(dimension, zoom, tx, tz));
            }
        }
    }

    private void requestTile(TileCoord coord) {
        if (texCache.has(coord)) return;
        texCache.markLoading(coord);
        fetcher.fetch(config.squaremapUrl, coord).thenAccept(opt -> {
            if (opt.isPresent()) texCache.enqueueUpload(coord, opt.get());
            else texCache.markMissing(coord);
        });
    }

    private int blockToScreenX(double bx, double pixelsPerBlock) {
        return (int) Math.round(width  / 2.0 + (bx - centerX) * pixelsPerBlock);
    }

    private int blockToScreenZ(double bz, double pixelsPerBlock) {
        return (int) Math.round(height / 2.0 + (bz - centerZ) * pixelsPerBlock);
    }

    private ClaimRect hoveredClaim(int mouseX, int mouseY) {
        double bpp = 1 << (3 - zoom);
        double blockX = centerX + (mouseX - width  / 2.0) * bpp;
        double blockZ = centerZ + (mouseY - height / 2.0) * bpp;
        for (ClaimRect claim : ClaimCache.get(dimension)) {
            if (claim.contains(blockX, blockZ)) return claim;
        }
        return null;
    }

    private void drawClaimTooltip(DrawContext context, ClaimRect claim, int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        String ownerLine = "Administrator".equals(claim.owner()) ? "Admin Claim" : claim.owner() + "'s Claim";
        String sizeLine  = (claim.maxX() - claim.minX()) + " × " + (claim.maxZ() - claim.minZ()) + " blocks";
        int tw = Math.max(client.textRenderer.getWidth(ownerLine), client.textRenderer.getWidth(sizeLine));
        int boxW = tw + 10;
        int boxH = 25;
        int tx = mouseX + 14;
        int tz = mouseY - 8;
        if (tx + boxW > width)  tx = mouseX - boxW - 6;
        if (tz + boxH > height) tz = height - boxH - 2;
        if (tz < 0) tz = 2;
        // Dark background with subtle border
        context.fill(tx - 3, tz - 3, tx + boxW + 3, tz + boxH + 3, 0xBF0A0A12);
        context.fill(tx - 2, tz - 2, tx + boxW + 2, tz + boxH + 2, 0xBF23233A);
        context.fill(tx - 1, tz - 1, tx + boxW + 1, tz + boxH + 1, 0xCC0A0A12);
        context.drawText(client.textRenderer, ownerLine, tx, tz + 3,  0xFFFFFFFF, false);
        context.drawText(client.textRenderer, sizeLine,  tx, tz + 14, 0xFF888899, false);
    }

    private static int claimColor(ClaimRect claim, String playerName) {
        if (playerName.equalsIgnoreCase(claim.owner())) return 0xBB44FF; // purple
        if ("Administrator".equals(claim.owner()))      return 0x00CCBB; // teal
        return claim.color() & 0xFFFFFF;
    }
}
