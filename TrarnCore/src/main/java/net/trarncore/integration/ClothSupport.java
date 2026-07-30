package net.trarncore.integration;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Whether Cloth Config is installed.
 *
 * <p>Checked once at class load. Consuming mods use this to keep their Cloth-based config screen
 * class from being loaded at all when Cloth is absent — see {@link ClothModMenuIntegration}.
 */
public final class ClothSupport {

    public static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("cloth-config");

    private ClothSupport() {
    }
}
