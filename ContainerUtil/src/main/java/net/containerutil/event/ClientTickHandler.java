package net.containerutil.event;

import net.containerutil.ContainerUtil;
import net.containerutil.capture.ContentCapture;
import net.containerutil.config.ConfigManager;
import net.containerutil.data.ContainerRecord;
import net.containerutil.data.IndexManager;
import net.containerutil.data.WorldIdentity;
import net.containerutil.render.TrackedContainer;
import net.containerutil.scan.ContainerScanner;
import net.containerutil.search.SearchScreen;
import net.trarncore.input.Keys;
import net.trarncore.util.Guarded;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;

public class ClientTickHandler {

    /** Last world key seen, so a server hop that skips the disconnect event still swaps indexes. */
    private static String lastWorldKey;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            runGuarded("Keybind handler", () -> handleKeybinds(client));
            runGuarded("World key watcher", () -> checkWorldKey(client));
            runGuarded("Container scanner", () -> ContainerScanner.tick(client));
            runGuarded("Content capture", () -> ContentCapture.tick(client));
            runGuarded("Track watcher", () -> checkTrackArrival(client));
        });
    }

    /**
     * Each subsystem is isolated so a fault in one does not take the others down with it —
     * losing the highlights because the capture hook threw would be a poor trade.
     */
    private static void runGuarded(String label, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            ContainerUtil.LOGGER.error("[ContainerUtil] {} crashed", label, e);
        }
    }

    private static void handleKeybinds(Minecraft client) {
        while (ContainerUtil.TOGGLE_HIGHLIGHTS != null && ContainerUtil.TOGGLE_HIGHLIGHTS.consumeClick()) {
            ContainerUtil.enabled = !ContainerUtil.enabled;
            ConfigManager.get().enabled = ContainerUtil.enabled;
            ConfigManager.save();
            ContainerUtil.CHAT.send("Highlights " + (ContainerUtil.enabled ? "enabled" : "disabled"));
        }

        while (ContainerUtil.OPEN_SEARCH != null && ContainerUtil.OPEN_SEARCH.consumeClick()) {
            if (!IndexManager.isActive()) {
                ContainerUtil.CHAT.send("No index for this world yet.");
            } else {
                client.setScreen(new SearchScreen());
            }
        }

        while (ContainerUtil.CLEAR_TRACK != null && ContainerUtil.CLEAR_TRACK.consumeClick()) {
            if (TrackedContainer.isTracking()) {
                TrackedContainer.clear();
                ContainerUtil.CHAT.send("Stopped tracking.");
            }
        }
    }

    /**
     * Watches for the world identity changing without a disconnect. Proxy networks move players
     * between backend servers in place, and continuing to write into the previous world's index
     * would scatter one base's containers across two files.
     */
    private static void checkWorldKey(Minecraft client) {
        if (client.level == null) {
            lastWorldKey = null;
            return;
        }
        String key = WorldIdentity.current();
        if (key == null || key.equals(lastWorldKey)) return;

        lastWorldKey = key;
        IndexManager.openWorld(key);
        ContainerScanner.reset();
    }

    /**
     * Clears the tracked container once you have actually arrived at it.
     *
     * <p>Deliberately uses the player's real position rather than the view anchor: flying a
     * freecam over to a chest is not arriving at it, and clearing the guidance at that moment
     * would strand you once the camera snapped back to your body.
     */
    private static void checkTrackArrival(Minecraft client) {
        ContainerRecord tracked = TrackedContainer.get();
        if (tracked == null || client.player == null) return;

        int clearDistance = ConfigManager.get().trackClearDistance;
        if (clearDistance <= 0) return;

        String dim = WorldIdentity.currentDimension();
        if (dim == null || !dim.equals(tracked.dim)) return;

        double distanceSq = tracked.distanceSqTo(
            client.player.getX(), client.player.getY(), client.player.getZ());
        if (distanceSq <= (double) clearDistance * clearDistance) {
            TrackedContainer.clear();
            ContainerUtil.CHAT.send("Arrived at " + tracked.displayName()
                + " (" + tracked.coordsString() + ")", ChatFormatting.GREEN);
        }
    }
}
