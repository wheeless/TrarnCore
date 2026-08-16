package net.autorelog;

import net.autorelog.config.AutoRelogConfig;
import net.autorelog.config.ConfigManager;
import net.autorelog.reconnect.SessionTracker;
import net.autorelog.screen.DisconnectScreenHook;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.trarncore.chat.ChatChannel;
import net.trarncore.update.UpdateChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoRelog implements ClientModInitializer {

    public static final String MOD_ID = "autorelog";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Aqua — one colour per sibling mod so prefixes stay distinguishable in a shared chat log. */
    public static final ChatChannel CHAT = ChatChannel.of("AutoRelog", ChatFormatting.AQUA);

    @Override
    public void onInitializeClient() {
        ConfigManager.load();

        SessionTracker.register();
        DisconnectScreenHook.register();

        // A successful join is what makes the previous attempts irrelevant.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> DisconnectScreenHook.onJoined());

        // Notify-only; see net.trarncore.update.UpdateChecker.
        UpdateChecker.watch(MOD_ID, CHAT);

        LOGGER.info("AutoRelog initialized");
    }

    /** Shorthand for the live config, which is read on nearly every path in this mod. */
    public static AutoRelogConfig config() {
        return ConfigManager.get();
    }
}
