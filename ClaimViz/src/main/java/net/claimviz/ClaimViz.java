package net.claimviz;

import net.claimviz.config.ConfigManager;
import net.claimviz.event.ServerJoinHandler;
import net.claimviz.render.ClaimRenderer;
import net.claimviz.render.PlayerRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import net.minecraft.ChatFormatting;
import net.trarncore.chat.ChatChannel;
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

        KeyMapping.Category category = KeyMapping.Category.MISC;

        TOGGLE_CLAIMS = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.claimviz.toggle_claims",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            category
        ));
        TOGGLE_PLAYERS = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.claimviz.toggle_players",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            category
        ));
        OPEN_MAP = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.claimviz.open_map",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            category
        ));

        ServerJoinHandler.register();
        ClaimRenderer.register();
        PlayerRenderer.register();

        // Notify-only; see net.trarncore.update.UpdateChecker.
        UpdateChecker.watch(MOD_ID, CHAT);

        LOGGER.info("ClaimViz initialized");
    }
}
