package net.easyportallinker;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;
import net.easyportallinker.config.ConfigManager;
import net.easyportallinker.event.ClientTickHandler;
import net.easyportallinker.event.SelectionHandler;
import net.easyportallinker.portal.PortalTarget;
import net.easyportallinker.render.HudRenderer;
import net.easyportallinker.render.PortalLinkRenderer;
import org.lwjgl.glfw.GLFW;
import net.minecraft.ChatFormatting;
import net.trarncore.chat.ChatChannel;
import net.trarncore.input.Keys;
import net.trarncore.update.UpdateChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EasyPortalLinker implements ClientModInitializer {

    public static final String MOD_ID = "easyportallinker";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** One colour per sibling mod so prefixes stay distinguishable in a shared chat log. */
    public static final ChatChannel CHAT = ChatChannel.of("EasyPortalLinker", ChatFormatting.GOLD);

    public static KeyMapping TOGGLE_OVERLAY;
    public static KeyMapping CLEAR_SELECTION;
    public static KeyMapping LOCK_TARGET_Y;

    /** In-memory mirror of {@code config.enabled}; the render loop reads this every frame. */
    public static volatile boolean enabled = true;

    /** In-memory mirror of {@code config.selection}; the render loop reads this every frame. */
    public static volatile PortalTarget selection;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        enabled = ConfigManager.get().enabled;
        selection = ConfigManager.get().selection;

        TOGGLE_OVERLAY = Keys.register(MOD_ID, "key.easyportallinker.toggle", GLFW.GLFW_KEY_P);
        // Unbound by default so it never clashes; bind it in Options → Controls if you want it.
        CLEAR_SELECTION = Keys.register(MOD_ID, "key.easyportallinker.clear", Keys.UNBOUND);
        // Press to lock the target Y to your current level; sneak + press to unlock. Rebindable.
        LOCK_TARGET_Y = Keys.register(MOD_ID, "key.easyportallinker.lockcurrenty", GLFW.GLFW_KEY_K);

        ClientTickHandler.register();
        SelectionHandler.register();
        PortalLinkRenderer.register();
        HudRenderer.register();

        // Notify-only; see net.trarncore.update.UpdateChecker.
        UpdateChecker.watch(MOD_ID, CHAT);

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
