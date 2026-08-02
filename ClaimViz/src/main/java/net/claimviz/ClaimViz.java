package net.claimviz;

import net.claimviz.config.ConfigManager;
import net.claimviz.event.ServerJoinHandler;
import net.claimviz.render.ClaimRenderer;
import net.claimviz.render.PlayerRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.minecraft.util.Formatting;
import net.trarncore.chat.ChatChannel;
import net.trarncore.update.UpdateChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClaimViz implements ClientModInitializer {

    public static final String MOD_ID = "claimviz";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** One colour per sibling mod so prefixes stay distinguishable in a shared chat log. */
    public static final ChatChannel CHAT = ChatChannel.of("ClaimViz", Formatting.LIGHT_PURPLE);

    public static KeyBinding TOGGLE_CLAIMS;
    public static KeyBinding TOGGLE_PLAYERS;
    public static KeyBinding OPEN_MAP;

    public static volatile boolean showClaims = true;
    public static volatile boolean showPlayers = true;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();

        KeyBinding.Category category = KeyBinding.Category.MISC;

        TOGGLE_CLAIMS = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.claimviz.toggle_claims",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            category
        ));
        TOGGLE_PLAYERS = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.claimviz.toggle_players",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            category
        ));
        OPEN_MAP = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.claimviz.open_map",
            InputUtil.Type.KEYSYM,
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
