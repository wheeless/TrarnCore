package net.easyportallinker;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.easyportallinker.config.ConfigManager;
import net.easyportallinker.event.ClientTickHandler;
import net.easyportallinker.event.SelectionHandler;
import net.easyportallinker.portal.PortalTarget;
import net.easyportallinker.render.HudRenderer;
import net.easyportallinker.render.PortalLinkRenderer;
import org.lwjgl.glfw.GLFW;
import net.minecraft.util.Formatting;
import net.trarncore.chat.ChatChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EasyPortalLinker implements ClientModInitializer {

    public static final String MOD_ID = "easyportallinker";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** One colour per sibling mod so prefixes stay distinguishable in a shared chat log. */
    public static final ChatChannel CHAT = ChatChannel.of("EasyPortalLinker", Formatting.GOLD);

    public static KeyBinding TOGGLE_OVERLAY;
    public static KeyBinding CLEAR_SELECTION;
    public static KeyBinding LOCK_TARGET_Y;

    /** In-memory mirror of {@code config.enabled}; the render loop reads this every frame. */
    public static volatile boolean enabled = true;

    /** In-memory mirror of {@code config.selection}; the render loop reads this every frame. */
    public static volatile PortalTarget selection;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        enabled = ConfigManager.get().enabled;
        selection = ConfigManager.get().selection;

        TOGGLE_OVERLAY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.easyportallinker.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            KeyBinding.Category.MISC
        ));
        // Unbound by default so it never clashes; bind it in Options → Controls if you want it.
        CLEAR_SELECTION = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.easyportallinker.clear",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            KeyBinding.Category.MISC
        ));
        // Press to lock the target Y to your current level; sneak + press to unlock. Rebindable.
        LOCK_TARGET_Y = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.easyportallinker.lockcurrenty",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            KeyBinding.Category.MISC
        ));

        ClientTickHandler.register();
        SelectionHandler.register();
        PortalLinkRenderer.register();
        HudRenderer.register();

        LOGGER.info("EasyPortalLinker initialized");
    }

    /** Record a new selection and persist it so it survives dimension changes and restarts. */
    public static void setSelection(PortalTarget target) {
        selection = target;
        ConfigManager.get().selection = target;
        ConfigManager.save();
    }

    /** Forget the current selection. */
    public static void clearSelection() {
        selection = null;
        ConfigManager.get().selection = null;
        ConfigManager.save();
    }
}
