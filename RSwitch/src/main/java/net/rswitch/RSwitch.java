package net.rswitch;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.ChatFormatting;
import net.rswitch.config.ConfigManager;
import net.rswitch.event.ClientTickHandler;
import net.trarncore.chat.ChatChannel;
import net.trarncore.input.Keys;
import net.trarncore.update.UpdateChecker;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RSwitch implements ClientModInitializer {

    public static final String MOD_ID = "rswitch";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Green — one colour per sibling mod so prefixes stay distinguishable in a shared chat log. */
    public static final ChatChannel CHAT = ChatChannel.of("RSwitch", ChatFormatting.GREEN);

    public static KeyMapping SWAP;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();

        // R is unbound in vanilla, so this is a free key on a default setup.
        SWAP = Keys.register(MOD_ID, "key.rswitch.swap", GLFW.GLFW_KEY_R);

        ClientTickHandler.register();

        // Notify-only; see net.trarncore.update.UpdateChecker.
        UpdateChecker.watch(MOD_ID, CHAT);

        LOGGER.info("RSwitch initialized");
    }
}
