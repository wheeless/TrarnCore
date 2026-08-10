package net.claimviz;

import net.claimviz.config.ConfigManager;
import net.claimviz.event.ServerJoinHandler;
import net.claimviz.render.ClaimRenderer;
import net.claimviz.render.PlayerRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import net.minecraft.ChatFormatting;
import net.trarncore.chat.ChatChannel;
import net.trarncore.input.Keys;
import net.trarncore.update.UpdateChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClaimViz implements ClientModInitializer {

    public static final String MOD_ID = "claimviz";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** One colour per sibling mod so prefixes stay distinguishable in a shared chat log. */
    public static final ChatChannel CHAT = ChatChannel.of("ClaimViz", ChatFormatting.LIGHT_PURPLE);

    public static KeyMapping TOGGLE_CLAIMS;
    public static KeyMapping TOGGLE_PLAYERS;
    public static KeyMapping OPEN_MAP;

    public static volatile boolean showClaims = true;
    public static volatile boolean showPlayers = true;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();

        TOGGLE_CLAIMS = Keys.register(MOD_ID, "key.claimviz.toggle_claims", GLFW.GLFW_KEY_V);
        TOGGLE_PLAYERS = Keys.register(MOD_ID, "key.claimviz.toggle_players", GLFW.GLFW_KEY_P);
        OPEN_MAP = Keys.register(MOD_ID, "key.claimviz.open_map", GLFW.GLFW_KEY_M);

        ServerJoinHandler.register();
        ClaimRenderer.register();
        PlayerRenderer.register();

        // Notify-only; see net.trarncore.update.UpdateChecker.
        UpdateChecker.watch(MOD_ID, CHAT);

        LOGGER.info("ClaimViz initialized");
    }
}
