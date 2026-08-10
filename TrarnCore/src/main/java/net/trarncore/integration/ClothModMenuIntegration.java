package net.trarncore.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;

/**
 * ModMenu entrypoint base for a mod whose config screen is built with Cloth Config.
 *
 * <p>Collapses the identical 24-line integration class every mod carried down to:
 * <pre>{@code
 * public class ModMenuIntegration extends ClothModMenuIntegration {
 *     @Override
 *     protected Screen build(Screen parent) {
 *         return MyConfigScreen.build(parent);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Why {@code build} is abstract rather than a factory passed in.</b> Cloth is an optional
 * dependency, so the config screen class must never be loaded when Cloth is absent or the JVM
 * throws {@code NoClassDefFoundError}. Handing us a {@code MyConfigScreen::build} method
 * reference would resolve that class the moment the reference is created. Keeping it as a method
 * body means the reference inside it is only resolved when the method actually runs, which
 * happens solely when the player opens the screen — and that requires Cloth to be present.
 */
public abstract class ClothModMenuIntegration implements ModMenuApi {

    /** Builds the config screen. Only ever invoked when Cloth Config is installed. */
    protected abstract Screen build(Screen parent);

    @Override
    public final ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Without Cloth the settings button is greyed out rather than broken.
        if (!ClothSupport.PRESENT) return parent -> null;
        return this::build;
    }
}
