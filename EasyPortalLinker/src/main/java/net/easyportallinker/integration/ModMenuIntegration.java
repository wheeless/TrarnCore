package net.easyportallinker.integration;

import net.easyportallinker.config.EasyPortalLinkerConfigScreen;
import net.minecraft.client.gui.screens.Screen;
import net.trarncore.integration.ClothModMenuIntegration;

/**
 * The Cloth-present guard lives in the base class; {@code build} is only ever invoked when Cloth
 * Config is installed, so referencing the screen class here is safe.
 */
public class ModMenuIntegration extends ClothModMenuIntegration {

    @Override
    protected Screen build(Screen parent) {
        return EasyPortalLinkerConfigScreen.build(parent);
    }
}
