package net.trarncore;

import net.fabricmc.api.ClientModInitializer;
import net.trarncore.config.ConfigManager;
import net.trarncore.update.UpdateChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared plumbing for the Trarn Fabric mods.
 *
 * <p>This is a library, not a feature mod — it registers no keybinds, draws nothing and has no
 * config of its own. It is bundled inside each consuming mod via jar-in-jar rather than
 * installed separately, so nobody has to know it exists.
 *
 * <p><b>Scope discipline.</b> Only genuinely generic plumbing belongs here: rendering primitives,
 * config persistence, chat feedback, keybind and ModMenu boilerplate. Domain logic — claim
 * fetching, portal maths, container indexing — stays in the mod that owns it. The moment this
 * starts accumulating features it stops being a library and becomes a monolith that all five
 * mods are hostage to.
 *
 * <p><b>API compatibility.</b> Consuming mods each bundle their own copy, and Fabric loads the
 * highest version it finds. A mod built against 1.0 may therefore run against a 1.2 bundled by a
 * sibling, so changes here must be additive: add methods, do not change or remove signatures.
 */
public class TrarnCore implements ClientModInitializer {

    public static final String MOD_ID = "trarncore";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        ConfigManager.load();

        // Fabric runs library entrypoints before the mods that depend on them, so every
        // UpdateChecker.watch() call has not happened yet at this point. start() only registers
        // the tick and join listeners; the actual lookup is deferred until the player is in a
        // world, by which time registration is long finished.
        UpdateChecker.start();

        LOGGER.info("TrarnCore loaded");
    }
}
