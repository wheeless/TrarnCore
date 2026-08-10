package net.simdistance.event;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.simdistance.SimDistance;
import net.simdistance.config.ConfigManager;

public class ClientTickHandler {

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                handleKeybinds();
            } catch (Exception e) {
                SimDistance.LOGGER.error("[SimDistance] Keybind tick handler crashed", e);
            }
        });
    }

    private static void handleKeybinds() {
        while (SimDistance.TOGGLE_BORDER != null && SimDistance.TOGGLE_BORDER.consumeClick()) {
            SimDistance.enabled = !SimDistance.enabled;
            ConfigManager.get().enabled = SimDistance.enabled;
            ConfigManager.save();
            SimDistance.CHAT.send("Border " + (SimDistance.enabled ? "enabled" : "disabled"));
        }
    }
}
