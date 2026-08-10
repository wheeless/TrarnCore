package net.containerutil;

import net.containerutil.capture.ContentCapture;
import net.containerutil.config.ConfigManager;
import net.containerutil.event.ClientTickHandler;
import net.containerutil.event.WorldJoinHandler;
import net.containerutil.render.ContainerEspRenderer;
import net.containerutil.render.HudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.ChatFormatting;
import net.trarncore.chat.ChatChannel;
import net.trarncore.input.Keys;
import net.trarncore.update.UpdateChecker;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContainerUtil implements ClientModInitializer {

    public static final String MOD_ID = "containerutil";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Dark aqua — one colour per sibling mod so prefixes stay distinguishable in a shared chat log. */
    public static final ChatChannel CHAT = ChatChannel.of("ContainerUtil", ChatFormatting.DARK_AQUA);

    public static KeyMapping TOGGLE_HIGHLIGHTS;
    public static KeyMapping OPEN_SEARCH;
    public static KeyMapping CLEAR_TRACK;

    /** In-memory mirror of {@code config.enabled}; the render loop reads this every frame. */
    public static volatile boolean enabled = true;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        enabled = ConfigManager.get().enabled;

        // Numpad 3 rather than the main-row 3: the top-row number keys are vanilla hotbar
        // slots, and a toggle that also swaps your held item every time you press it is a
        // bug report waiting to happen. Rebindable in Options → Controls.
        TOGGLE_HIGHLIGHTS = Keys.register(MOD_ID, "key.containerutil.toggle", GLFW.GLFW_KEY_KP_3);

        // Unbound by default — the search screen is a deliberate action, and picking a default
        // key here would collide with something on most people's setups.
        OPEN_SEARCH = Keys.register(MOD_ID, "key.containerutil.search", Keys.UNBOUND);

        CLEAR_TRACK = Keys.register(MOD_ID, "key.containerutil.clear_track", Keys.UNBOUND);

        ContentCapture.register();
        WorldJoinHandler.register();
        ClientTickHandler.register();
        ContainerEspRenderer.register();
        HudRenderer.register();

        // Notify-only; see net.trarncore.update.UpdateChecker.
        UpdateChecker.watch(MOD_ID, CHAT);

        LOGGER.info("ContainerUtil initialized");
    }
}
