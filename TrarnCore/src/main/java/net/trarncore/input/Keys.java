package net.trarncore.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

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
