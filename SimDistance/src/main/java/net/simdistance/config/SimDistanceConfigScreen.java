package net.simdistance.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.simdistance.SimDistance;

/**
 * Cloth Config screen for SimDistance.
 * Only classloaded when cloth-config is present — ModMenuIntegration guards the load.
 */
public class SimDistanceConfigScreen {

    public static Screen build(Screen parent) {
        SimDistanceConfig config = ConfigManager.get();

        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Text.literal("SimDistance Settings"))
            .setSavingRunnable(() -> {
                ConfigManager.save();
                // Keep the render loop's in-memory flag in sync with the saved value.
                SimDistance.enabled = config.enabled;
            });

        ConfigEntryBuilder entry = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(Text.literal("General"));

        general.addEntry(entry
            .startBooleanToggle(Text.literal("Enabled"), config.enabled)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Master on/off for the simulation-distance border. Also bound to the toggle hotkey (default: G)."))
            .setSaveConsumer(val -> config.enabled = val)
            .build()
        );
        general.addEntry(entry
            .startBooleanToggle(Text.literal("Use Server Simulation Distance"), config.useServerSimulationDistance)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Draw the border at the live simulation distance reported by the server (works in singleplayer too). Turn off to use a fixed chunk radius."))
            .setSaveConsumer(val -> config.useServerSimulationDistance = val)
            .build()
        );
        general.addEntry(entry
            .startIntSlider(Text.literal("Manual Chunk Radius"), config.manualChunkRadius, 1, 32)
            .setDefaultValue(8)
            .setTooltip(Text.literal("Chunk radius used when 'Use Server Simulation Distance' is off, or as a fallback when the server reports nothing."))
            .setSaveConsumer(val -> config.manualChunkRadius = val)
            .build()
        );

        // ── Appearance ────────────────────────────────────────────────────────
        // RGB as three sliders — each save consumer masks its own channel, so order
        // doesn't matter. (Cloth's color-picker field pulls in a type that isn't on
        // the compile classpath, so we avoid it.)
        general.addEntry(entry
            .startIntSlider(Text.literal("Wall Color — Red"), (config.wallColor >> 16) & 0xFF, 0, 255)
            .setDefaultValue(0xFF)
            .setTooltip(Text.literal("Red channel of the border wall (default: 255)."))
            .setSaveConsumer(val -> config.wallColor = (config.wallColor & 0x00FFFF) | ((val & 0xFF) << 16))
            .build()
        );
        general.addEntry(entry
            .startIntSlider(Text.literal("Wall Color — Green"), (config.wallColor >> 8) & 0xFF, 0, 255)
            .setDefaultValue(0x30)
            .setTooltip(Text.literal("Green channel of the border wall (default: 48)."))
            .setSaveConsumer(val -> config.wallColor = (config.wallColor & 0xFF00FF) | ((val & 0xFF) << 8))
            .build()
        );
        general.addEntry(entry
            .startIntSlider(Text.literal("Wall Color — Blue"), config.wallColor & 0xFF, 0, 255)
            .setDefaultValue(0x30)
            .setTooltip(Text.literal("Blue channel of the border wall (default: 48)."))
            .setSaveConsumer(val -> config.wallColor = (config.wallColor & 0xFFFF00) | (val & 0xFF))
            .build()
        );
        general.addEntry(entry
            .startIntSlider(Text.literal("Wall Opacity (%)"), config.wallOpacity, 0, 100)
            .setDefaultValue(22)
            .setTooltip(Text.literal("How opaque the translucent wall is. 0 = invisible fill (use edge lines only)."))
            .setSaveConsumer(val -> config.wallOpacity = val)
            .build()
        );
        general.addEntry(entry
            .startBooleanToggle(Text.literal("Draw Edge Lines"), config.drawEdgeLines)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Draw a crisp outline along the top and bottom edges and the vertical corners of the border."))
            .setSaveConsumer(val -> config.drawEdgeLines = val)
            .build()
        );
        general.addEntry(entry
            .startBooleanToggle(Text.literal("Show Chunk Grid"), config.showChunkGrid)
            .setDefaultValue(false)
            .setTooltip(Text.literal("Draw the internal per-chunk grid on the floor within the region, at your feet level."))
            .setSaveConsumer(val -> config.showChunkGrid = val)
            .build()
        );

        // ── Height ────────────────────────────────────────────────────────────
        general.addEntry(entry
            .startBooleanToggle(Text.literal("Full World Height"), config.fullWorldHeight)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Extend the wall from the world's bottom to its top. Turn off to use a fixed height around you."))
            .setSaveConsumer(val -> config.fullWorldHeight = val)
            .build()
        );
        general.addEntry(entry
            .startIntSlider(Text.literal("Vertical Radius (blocks)"), config.verticalRadius, 8, 256)
            .setDefaultValue(48)
            .setTooltip(Text.literal("Half-height of the wall around you, used only when 'Full World Height' is off."))
            .setSaveConsumer(val -> config.verticalRadius = val)
            .build()
        );

        return builder.build();
    }
}
