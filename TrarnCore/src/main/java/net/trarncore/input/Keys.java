package net.trarncore.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keybind registration and consumption.
 *
 * <p>The consumption helper exists because {@link KeyMapping#consumeClick()} is a queue, not a
 * flag: it must be drained in a loop or presses buffer up and fire late. Every mod here had the
 * same {@code while (KEY.consumeClick())} shape written out by hand.
 */
public final class Keys {

    private Keys() {
    }

    private static final Map<String, KeyMapping.Category> CATEGORIES = new ConcurrentHashMap<>();

    /**
     * The controls-screen category a mod's keybinds are listed under, created on first use.
     *
     * <p>Prefer this to {@link KeyMapping.Category#MISC}. Vanilla's categories are shared, so
     * every mod that uses one dumps its binds into the same undifferentiated list — with several
     * siblings installed there is no way to tell whose bind is whose, or to find one you want to
     * rebind.
     *
     * <p>The label resolves to {@code key.category.<modId>.main}, which the mod's own lang file
     * must define; without it the screen shows the raw key.
     *
     * <p>Cached because {@link KeyMapping.Category#register} throws on a duplicate id rather than
     * returning the existing category — an entrypoint that runs twice would otherwise take the
     * game down at startup.
     */
    public static KeyMapping.Category category(String modId) {
        return CATEGORIES.computeIfAbsent(modId, id ->
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(id, "main")));
    }

    /**
     * Registers a keybind under the mod's own category.
     *
     * @param modId          the mod id, used for both the category and its lang key
     * @param translationKey e.g. {@code "key.mymod.toggle"}, matched in the mod's lang file
     * @param glfwKey        a {@code GLFW.GLFW_KEY_*} constant, or {@link #UNBOUND}
     */
    public static KeyMapping register(String modId, String translationKey, int glfwKey) {
        return register(translationKey, glfwKey, category(modId));
    }

    /**
     * Registers a keybind.
     *
     * @param translationKey e.g. {@code "key.mymod.toggle"}, matched in the mod's lang file
     * @param glfwKey        a {@code GLFW.GLFW_KEY_*} constant, or {@link #UNBOUND}
     */
    public static KeyMapping register(String translationKey, int glfwKey, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(
            new KeyMapping(translationKey, InputConstants.Type.KEYSYM, glfwKey, category));
    }

    /** No default key. Use for actions where any default would collide on someone's setup. */
    public static final int UNBOUND = GLFW.GLFW_KEY_UNKNOWN;

    /**
     * Runs {@code action} once per queued press.
     *
     * <p>Null-safe on the binding, so a mod whose init failed part-way does not also crash every
     * tick on a missing keybind.
     */
    public static void whenPressed(KeyMapping binding, Runnable action) {
        if (binding == null) return;
        while (binding.consumeClick()) {
            action.run();
        }
    }
}
