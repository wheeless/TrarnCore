package net.trarncore.input;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Keybind registration and consumption.
 *
 * <p>The consumption helper exists because {@link KeyBinding#wasPressed()} is a queue, not a
 * flag: it must be drained in a loop or presses buffer up and fire late. Every mod here had the
 * same {@code while (KEY.wasPressed())} shape written out by hand.
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
    public static KeyBinding register(String translationKey, int glfwKey, KeyBinding.Category category) {
        return KeyBindingHelper.registerKeyBinding(
            new KeyBinding(translationKey, InputUtil.Type.KEYSYM, glfwKey, category));
    }

    /** No default key. Use for actions where any default would collide on someone's setup. */
    public static final int UNBOUND = GLFW.GLFW_KEY_UNKNOWN;

    /**
     * Runs {@code action} once per queued press.
     *
     * <p>Null-safe on the binding, so a mod whose init failed part-way does not also crash every
     * tick on a missing keybind.
     */
    public static void whenPressed(KeyBinding binding, Runnable action) {
        if (binding == null) return;
        while (binding.wasPressed()) {
            action.run();
        }
    }
}
