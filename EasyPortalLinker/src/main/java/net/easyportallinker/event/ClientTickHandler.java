package net.easyportallinker.event;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.easyportallinker.EasyPortalLinker;
import net.easyportallinker.config.ConfigManager;
import net.easyportallinker.scan.PortalFinder;

public class ClientTickHandler {

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                handleKeybinds(client);
            } catch (Exception e) {
                EasyPortalLinker.LOGGER.error("[EasyPortalLinker] Keybind tick handler crashed", e);
            }
            try {
                trackDimension(client);
                PortalFinder.tick(client);
            } catch (Exception e) {
                EasyPortalLinker.LOGGER.error("[EasyPortalLinker] Portal sweep crashed", e);
            }
        });
    }

    /** Last dimension seen, so the sweep can be thrown away when it stops describing anywhere. */
    private static String lastDimension;

    /**
     * Portals found in the Overworld mean nothing in the Nether, and the sweep publishes world
     * coordinates with no dimension attached — so a dimension change has to clear it rather than
     * let stale boxes hang in the air until the next full sweep replaces them.
     */
    private static void trackDimension(Minecraft client) {
        String dimension = client.level == null
            ? null
            : client.level.dimension().identifier().toString();

        if (dimension == null) {
            if (lastDimension != null) {
                lastDimension = null;
                PortalFinder.reset();
            }
            return;
        }
        if (!dimension.equals(lastDimension)) {
            lastDimension = dimension;
            PortalFinder.reset();
        }
    }

    private static void handleKeybinds(Minecraft client) {
        while (EasyPortalLinker.TOGGLE_PORTAL_ESP != null && EasyPortalLinker.TOGGLE_PORTAL_ESP.consumeClick()) {
            EasyPortalLinker.portalEsp = !EasyPortalLinker.portalEsp;
            ConfigManager.get().portalEsp = EasyPortalLinker.portalEsp;
            ConfigManager.save();
            if (!EasyPortalLinker.portalEsp) PortalFinder.reset();
            EasyPortalLinker.CHAT.send("Portal highlights "
                + (EasyPortalLinker.portalEsp ? "enabled" : "disabled"));
        }

        while (EasyPortalLinker.TOGGLE_OVERLAY != null && EasyPortalLinker.TOGGLE_OVERLAY.consumeClick()) {
            EasyPortalLinker.enabled = !EasyPortalLinker.enabled;
            ConfigManager.get().enabled = EasyPortalLinker.enabled;
            ConfigManager.save();
            EasyPortalLinker.CHAT.send("Guide " + (EasyPortalLinker.enabled ? "enabled" : "disabled"));
        }

        while (EasyPortalLinker.CLEAR_SELECTION != null && EasyPortalLinker.CLEAR_SELECTION.consumeClick()) {
            boolean had = EasyPortalLinker.selection != null;
            EasyPortalLinker.clearSelection();
            EasyPortalLinker.CHAT.send(had ? "Selection cleared" : "No selection to clear");
        }

        while (EasyPortalLinker.LOCK_TARGET_Y != null && EasyPortalLinker.LOCK_TARGET_Y.consumeClick()) {
            if (client.player == null) continue;
            if (client.player.isShiftKeyDown()) {
                ConfigManager.get().lockTargetY = false;
                ConfigManager.save();
                EasyPortalLinker.CHAT.send("Target Y unlocked — frame follows your feet");
            } else {
                int y = Mth.floor(client.player.getY());
                ConfigManager.get().lockTargetY = true;
                ConfigManager.get().lockedTargetY = y;
                ConfigManager.save();
                EasyPortalLinker.CHAT.send("Target Y locked to " + y + "  (sneak + key to unlock)");
            }
        }
    }
}
