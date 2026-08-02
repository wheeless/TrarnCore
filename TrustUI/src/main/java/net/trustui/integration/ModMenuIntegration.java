package net.trustui.integration;

import net.minecraft.client.gui.screen.Screen;
import net.trarncore.integration.ClothModMenuIntegration;
import net.trustui.config.TrustUIConfigScreen;

/**
 * The Cloth-present guard lives in the base class; {@code build} is only ever invoked when Cloth
 * Config is installed, so referencing the screen class here is safe.
 */
public class ModMenuIntegration extends ClothModMenuIntegration {

    @Override
    protected Screen build(Screen parent) {
        return TrustUIConfigScreen.build(parent);
    }
}
