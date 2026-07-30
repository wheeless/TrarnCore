package net.easyportallinker.event;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.easyportallinker.EasyPortalLinker;
import net.easyportallinker.config.ConfigManager;

public class ClientTickHandler {

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                handleKeybinds(client);
            } catch (Exception e) {
                EasyPortalLinker.LOGGER.error("[EasyPortalLinker] Keybind tick handler crashed", e);
            }
        });
    }

    private static void handleKeybinds(MinecraftClient client) {
        while (EasyPortalLinker.TOGGLE_OVERLAY != null && EasyPortalLinker.TOGGLE_OVERLAY.wasPressed()) {
            EasyPortalLinker.enabled = !EasyPortalLinker.enabled;
            ConfigManager.get().enabled = EasyPortalLinker.enabled;
            ConfigManager.save();
            EasyPortalLinker.CHAT.send("Guide " + (EasyPortalLinker.enabled ? "enabled" : "disabled"));
        }

        while (EasyPortalLinker.CLEAR_SELECTION != null && EasyPortalLinker.CLEAR_SELECTION.wasPressed()) {
            boolean had = EasyPortalLinker.selection != null;
            EasyPortalLinker.clearSelection();
            EasyPortalLinker.CHAT.send(had ? "Selection cleared" : "No selection to clear");
        }

        while (EasyPortalLinker.LOCK_TARGET_Y != null && EasyPortalLinker.LOCK_TARGET_Y.wasPressed()) {
            if (client.player == null) continue;
            if (client.player.isSneaking()) {
                ConfigManager.get().lockTargetY = false;
                ConfigManager.save();
                EasyPortalLinker.CHAT.send("Target Y unlocked — frame follows your feet");
            } else {
                int y = MathHelper.floor(client.player.getY());
                ConfigManager.get().lockTargetY = true;
                ConfigManager.get().lockedTargetY = y;
                ConfigManager.save();
                EasyPortalLinker.CHAT.send("Target Y locked to " + y + "  (sneak + key to unlock)");
            }
        }
    }
}
