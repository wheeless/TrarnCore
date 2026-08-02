package net.simdistance;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.simdistance.config.ConfigManager;
import net.simdistance.event.ClientTickHandler;
import net.simdistance.render.SimDistanceRenderer;
import org.lwjgl.glfw.GLFW;
import net.minecraft.util.Formatting;
import net.trarncore.chat.ChatChannel;
import net.trarncore.update.UpdateChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimDistance implements ClientModInitializer {

    public static final String MOD_ID = "simdistance";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** One colour per sibling mod so prefixes stay distinguishable in a shared chat log. */
    public static final ChatChannel CHAT = ChatChannel.of("SimDistance", Formatting.RED);

    public static KeyBinding TOGGLE_BORDER;

    /** In-memory mirror of {@code config.enabled}; the render loop reads this every frame. */
    public static volatile boolean enabled = true;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        enabled = ConfigManager.get().enabled;

        TOGGLE_BORDER = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.simdistance.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            KeyBinding.Category.MISC
        ));

        ClientTickHandler.register();
        SimDistanceRenderer.register();

        // Notify-only; see net.trarncore.update.UpdateChecker.
        UpdateChecker.watch(MOD_ID, CHAT);

        LOGGER.info("SimDistance initialized");
    }
}
