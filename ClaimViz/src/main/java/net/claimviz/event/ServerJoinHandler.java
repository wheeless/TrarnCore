package net.claimviz.event;

import net.claimviz.ClaimViz;
import net.claimviz.config.ClaimVizConfig;
import net.claimviz.config.ConfigManager;
import net.claimviz.data.ClaimCache;
import net.claimviz.data.ClaimRect;
import net.claimviz.data.PlayerFetcher;
import net.claimviz.data.SquaremapFetcher;
import net.claimviz.integration.XaeroIntegration;
import net.claimviz.map.MapScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerJoinHandler {

    private static volatile ClaimVizConfig.ServerConfig activeConfig;
    private static volatile String lastDimension;
    private static volatile ClaimRect lastInsideClaim;
    private static ScheduledExecutorService claimRefreshExecutor;

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            String address = client.getCurrentServerEntry() != null
                ? client.getCurrentServerEntry().address
                : "";
            Optional<ClaimVizConfig.ServerConfig> cfg = ConfigManager.getForServer(address);
            if (cfg.isEmpty()) {
                ClaimViz.LOGGER.info("ClaimViz: no config entry matches server '{}'", address);
                return;
            }
            activeConfig = cfg.get();
            lastDimension = null;
            ClaimViz.LOGGER.info("ClaimViz active for '{}'", address);

            if (activeConfig.showPlayers) {
                PlayerFetcher.start(activeConfig.squaremapUrl);
                PlayerFetcher.setOnUpdate(XaeroIntegration::syncPlayerPositions);
            }
            scheduleClaimRefresh();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            stopAll();
            XaeroIntegration.clearWaypoints();
            XaeroIntegration.clearTrackedPlayers();
            client.execute(() -> {
                if (client.currentScreen instanceof MapScreen ms) ms.close();
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                handleKeybinds(client);
            } catch (Exception e) {
                ClaimViz.LOGGER.error("[ClaimViz] Keybind tick handler crashed", e);
            }
            try {
                handleDimensionChange(client);
            } catch (Exception e) {
                ClaimViz.LOGGER.error("[ClaimViz] Dimension change handler crashed", e);
            }
            try {
                handleClaimMessages(client);
            } catch (Exception e) {
                ClaimViz.LOGGER.error("[ClaimViz] Claim message handler crashed", e);
            }
        });
    }

    private static void handleKeybinds(MinecraftClient client) {
        while (ClaimViz.TOGGLE_CLAIMS != null && ClaimViz.TOGGLE_CLAIMS.wasPressed()) {
            ClaimViz.showClaims = !ClaimViz.showClaims;
            ClaimViz.CHAT.send("Claims " + (ClaimViz.showClaims ? "enabled" : "disabled"));
        }
        while (ClaimViz.TOGGLE_PLAYERS != null && ClaimViz.TOGGLE_PLAYERS.wasPressed()) {
            ClaimViz.showPlayers = !ClaimViz.showPlayers;
            ClaimViz.CHAT.send("Players " + (ClaimViz.showPlayers ? "enabled" : "disabled"));
        }
        while (ClaimViz.OPEN_MAP != null && ClaimViz.OPEN_MAP.wasPressed()) {
            if (activeConfig != null && lastDimension != null) {
                client.setScreen(new MapScreen(activeConfig, lastDimension));
            } else {
                ClaimViz.CHAT.send("No config for this server.");
            }
        }
    }

    private static void handleDimensionChange(MinecraftClient client) {
        if (activeConfig == null || client.player == null) return;
        String dim = toDimensionKey(client.player.getEntityWorld().getRegistryKey().getValue().toString());
        if (!dim.equals(lastDimension)) {
            lastDimension = dim;
            lastInsideClaim = null; // suppress spurious "left" message on dimension change
            fetchClaimsForDimension(dim);
        }
    }

    private static void handleClaimMessages(MinecraftClient client) {
        var cfg = activeConfig;
        if (cfg == null || client.player == null) return;
        String dim = lastDimension;
        if (dim == null) return;

        double px = client.player.getX();
        double pz = client.player.getZ();

        ClaimRect current = ClaimCache.get(dim).stream()
            .filter(c -> c.contains(px, pz))
            .findFirst()
            .orElse(null);

        if (cfg.showClaimMessages && !sameClaimBounds(current, lastInsideClaim)) {
            String name = client.player.getGameProfile().name();
            if (lastInsideClaim != null) {
                ClaimViz.CHAT.send(leaveMessage(lastInsideClaim, name));
            }
            if (current != null) {
                ClaimViz.CHAT.send(enterMessage(current, name));
            }
        }

        // Deliberately still the action bar, and the one message in the mod that should stay
        // there: this fires every tick while you stand in a claim, so each write replaces the
        // last. In chat it would be twenty lines a second.
        if (cfg.persistentClaimBar && current != null) {
            String name = client.player.getGameProfile().name();
            client.player.sendMessage(Text.literal(persistentLabel(current, name)), true);
        }

        lastInsideClaim = current;
    }

    private static boolean sameClaimBounds(ClaimRect a, ClaimRect b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.minX() == b.minX() && a.maxX() == b.maxX()
            && a.minZ() == b.minZ() && a.maxZ() == b.maxZ();
    }

    private static String enterMessage(ClaimRect claim, String playerName) {
        if (playerName.equalsIgnoreCase(claim.owner())) return "§dYou have entered your claim.";
        if ("Administrator".equals(claim.owner()))      return "§bYou have entered an Admin claim.";
        return "§aYou have entered " + claim.owner() + "'s claim.";
    }

    private static String leaveMessage(ClaimRect claim, String playerName) {
        if (playerName.equalsIgnoreCase(claim.owner())) return "§dYou have left your claim.";
        if ("Administrator".equals(claim.owner()))      return "§7You have left an Admin claim.";
        return "§7You have left " + claim.owner() + "'s claim.";
    }

    private static String persistentLabel(ClaimRect claim, String playerName) {
        if (playerName.equalsIgnoreCase(claim.owner())) return "§d⚑ Your claim";
        if ("Administrator".equals(claim.owner()))      return "§b⚑ Admin claim";
        return "§a⚑ " + claim.owner() + "'s claim";
    }

    private static void scheduleClaimRefresh() {
        stopClaimRefresh();
        claimRefreshExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "claimviz-claim-refresh");
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((thread, ex) ->
                ClaimViz.LOGGER.error("[ClaimViz] {} died unexpectedly", thread.getName(), ex));
            return t;
        });
        // The initial fetch is triggered by the dimension-change tick handler on the first tick.
        // This executor only handles periodic re-fetches after that.
        int interval = activeConfig.claimRefreshIntervalSeconds;
        claimRefreshExecutor.scheduleAtFixedRate(() -> {
            String dim = lastDimension;
            if (dim != null) fetchClaimsForDimension(dim);
        }, interval, interval, TimeUnit.SECONDS);
    }

    private static void fetchClaimsForDimension(String squaremapDim) {
        ClaimVizConfig.ServerConfig cfg = activeConfig;
        if (cfg == null) return;
        SquaremapFetcher.fetch(cfg.squaremapUrl, squaremapDim)
            .thenAccept(claims -> {
                ClaimCache.set(squaremapDim, claims);
                ClaimViz.LOGGER.info("ClaimViz: loaded {} claims for {}", claims.size(), squaremapDim);
                if (cfg.xaeroWaypointsEnabled) {
                    XaeroIntegration.syncClaimWaypoints(claims);
                }
            });
    }

    /** Converts "minecraft:overworld" → "minecraft_overworld" for SquareMap endpoint paths. */
    public static String toDimensionKey(String registryKey) {
        return registryKey.replace(":", "_").replace("/", "_");
    }

    private static void stopClaimRefresh() {
        if (claimRefreshExecutor != null && !claimRefreshExecutor.isShutdown()) {
            claimRefreshExecutor.shutdownNow();
        }
        claimRefreshExecutor = null;
    }

    public static void stopAll() {
        stopClaimRefresh();
        PlayerFetcher.stop();
        ClaimCache.clear();
        activeConfig = null;
        lastDimension = null;
        lastInsideClaim = null;
    }

    /** Re-fetches activeConfig from the current config file contents. Call after saving settings in-game. */
    public static void reloadActiveConfig() {
        if (activeConfig == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getCurrentServerEntry() == null) return;
        ConfigManager.getForServer(client.getCurrentServerEntry().address)
            .ifPresent(cfg -> activeConfig = cfg);
    }

    public static ClaimVizConfig.ServerConfig getActiveConfig() {
        return activeConfig;
    }

    public static String getLastDimension() {
        return lastDimension;
    }
}
