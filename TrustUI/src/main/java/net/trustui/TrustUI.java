package net.trustui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.ChatFormatting;
import net.trarncore.chat.ChatChannel;
import net.trarncore.input.Keys;
import net.trarncore.update.UpdateChecker;
import net.trarncore.util.Guarded;
import net.trustui.config.ConfigManager;
import net.trustui.trust.TrustListReader;
import net.trustui.ui.TrustScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrustUI implements ClientModInitializer {

    public static final String MOD_ID = "trustui";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Blue — one colour per sibling mod so prefixes stay distinguishable in a shared chat log. */
    public static final ChatChannel CHAT = ChatChannel.of("TrustUI", ChatFormatting.BLUE);

    public static KeyMapping OPEN_MENU;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();

        // Unbound by default: this opens a full screen, and any default would collide with
        // something on most setups.
        OPEN_MENU = Keys.register("key.trustui.open", Keys.UNBOUND, KeyMapping.Category.MISC);

        TrustListReader.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Guarded.run(LOGGER, "TrustUI tick", () -> {
                TrustListReader.tick();
                Keys.whenPressed(OPEN_MENU, () -> {
                    if (client.getConnection() == null) {
                        CHAT.send("Not connected to a server.");
                        return;
                    }
                    client.setScreen(new TrustScreen());
                });
            });
        });

        // Any capture in flight belongs to the server we just left.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TrustListReader.clear());

        // Notify-only; see net.trarncore.update.UpdateChecker.
        UpdateChecker.watch(MOD_ID, CHAT);

        LOGGER.info("TrustUI initialized");
    }
}
