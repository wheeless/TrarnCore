package net.blowbyblow.integration;

import net.blowbyblow.config.BlowByBlowConfigScreen;
import net.minecraft.client.gui.screens.Screen;
import net.trarncore.integration.ClothModMenuIntegration;

/**
 * ModMenu entrypoint. The base class keeps the Cloth screen class from loading when Cloth is
 * absent — see {@link ClothModMenuIntegration} for why this is a method body and not a factory.
 */
public class ModMenuIntegration extends ClothModMenuIntegration {

    @Override
    protected Screen build(Screen parent) {
        return BlowByBlowConfigScreen.build(parent);
    }
}
