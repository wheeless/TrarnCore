package net.easyportallinker.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.easyportallinker.EasyPortalLinker;

/**
 * Cloth Config screen for EasyPortalLinker.
 * Only classloaded when cloth-config is present — ModMenuIntegration guards the load.
 */
public class EasyPortalLinkerConfigScreen {

    public static Screen build(Screen parent) {
        EasyPortalLinkerConfig config = ConfigManager.get();

        // Captured flag: a one-shot "forget the selection" toggle handled on save.
        final boolean[] forget = { false };

        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Text.literal("EasyPortalLinker Settings"))
            .setSavingRunnable(() -> {
                ConfigManager.save();
                // Keep the in-memory mirror in sync with the saved value.
                EasyPortalLinker.enabled = config.enabled;
                if (forget[0]) {
                    EasyPortalLinker.clearSelection();
                }
            });

        ConfigEntryBuilder entry = builder.entryBuilder();

        // ── Tutorial tab (shown first so it greets you) ──────────────────────────
        ConfigCategory tutorial = builder.getOrCreateCategory(Text.literal("How to Link"));
        tutorial.addEntry(entry
            .startTextDescription(Text.literal(
                "§l§dEasyPortalLinker — reliable portal linking§r\n"
                    + "This is the bulletproof way to link Nether portals: build the counterpart portal "
                    + "exactly where the game will look for it, so it becomes the closest link target."))
            .build());
        tutorial.addEntry(entry
            .startTextDescription(Text.literal(
                "§e§lWhy it works§r\n"
                    + "Traveling scales your position by the dimensions' coordinate ratio (Overworld 1, "
                    + "Nether 8):\n"
                    + " • Overworld → Nether: X and Z ÷ 8 (floored)\n"
                    + " • Nether → Overworld: X and Z × 8\n"
                    + "Y stays the same (clamped to the destination). The game then links to the closest "
                    + "existing portal within 128 blocks of that point — so a portal built right on it wins."))
            .build());
        tutorial.addEntry(entry
            .startTextDescription(Text.literal(
                "§a§lSteps§r\n"
                    + "1. Hold a wooden shovel and right-click your portal (look at the portal, its obsidian "
                    + "frame, or stand inside it).\n"
                    + "2. Read the chat — it prints the exact counterpart coordinates and the axis.\n"
                    + "3. Travel to the other dimension. Follow the highlighted column to the X/Z, then build "
                    + "your frame onto the ghost outline at the shown Y.\n"
                    + "4. Light it. Your portal is right on the scaled point, so the link is clean — and it "
                    + "works both ways."))
            .build());
        tutorial.addEntry(entry
            .startTextDescription(Text.literal(
                "§6§lFor the most reliable link§r\n"
                    + " • Build the destination portal §obefore§r lighting or entering the source, so the game "
                    + "doesn't dig out a stray portal near the target first.\n"
                    + " • Watch for other portals within ~128 blocks of the target: a closer existing portal "
                    + "(including auto-generated ones) can hijack the link. Break strays if a link goes wrong.\n"
                    + " • Matching the axis isn't required for linking, but keeps things tidy — the guide shows it."))
            .build());
        tutorial.addEntry(entry
            .startTextDescription(Text.literal(
                "§b§lControls§r\n"
                    + " • §fP§r — toggle the guide on/off (rebindable in Options → Controls).\n"
                    + " • §fK§r — lock the target Y to your current level; §fsneak + K§r to unlock "
                    + "(back to following your feet).\n"
                    + " • §fSneak + right-click§r with the selection item — clear the selection (or bind the "
                    + "Clear Portal Selection key).\n"
                    + "The selection is remembered across dimension changes and restarts."))
            .build());

        ConfigCategory general = builder.getOrCreateCategory(Text.literal("Settings"));

        general.addEntry(entry
            .startBooleanToggle(Text.literal("Enabled"), config.enabled)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Master on/off for the portal guide. Also bound to the toggle hotkey (default: P)."))
            .setSaveConsumer(val -> config.enabled = val)
            .build()
        );
        general.addEntry(entry
            .startStrField(Text.literal("Selection Item"), config.selectionItem)
            .setDefaultValue("minecraft:wooden_shovel")
            .setTooltip(Text.literal("Item id that selects a portal on right-click. It only 'steals' the click when a portal is actually detected, so the item still works normally otherwise."))
            .setSaveConsumer(val -> config.selectionItem = val)
            .build()
        );
        general.addEntry(entry
            .startIntSlider(Text.literal("Select Reach (blocks)"), config.selectReach, 3, 12)
            .setDefaultValue(6)
            .setTooltip(Text.literal("How far the selection ray reaches when looking for a portal."))
            .setSaveConsumer(val -> config.selectReach = val)
            .build()
        );
        general.addEntry(entry
            .startBooleanToggle(Text.literal("Require Sneak To Clear"), config.requireSneakToClear)
            .setDefaultValue(true)
            .setTooltip(Text.literal("When on, sneak + right-click with the selection item clears the current selection."))
            .setSaveConsumer(val -> config.requireSneakToClear = val)
            .build()
        );
        general.addEntry(entry
            .startBooleanToggle(Text.literal("Lock Target Y"), config.lockTargetY)
            .setDefaultValue(false)
            .setTooltip(Text.literal("Pin the ghost frame's base to a fixed Y instead of tracking your feet. Handy for a Nether hub built at one consistent level. The value is clamped to the destination dimension's build range."))
            .setSaveConsumer(val -> config.lockTargetY = val)
            .build()
        );
        general.addEntry(entry
            .startIntField(Text.literal("Locked Target Y"), config.lockedTargetY)
            .setDefaultValue(120)
            .setTooltip(Text.literal("Base Y of the ghost frame when 'Lock Target Y' is on (e.g. 120 for just under the Nether roof)."))
            .setSaveConsumer(val -> config.lockedTargetY = val)
            .build()
        );
        general.addEntry(entry
            .startBooleanToggle(Text.literal("Forget Selected Portal"), false)
            .setDefaultValue(false)
            .setTooltip(Text.literal("Turn on and save to clear the currently remembered portal selection."))
            .setSaveConsumer(val -> forget[0] = val)
            .build()
        );

        // ── What to draw ────────────────────────────────────────────────────────
        general.addEntry(entry
            .startBooleanToggle(Text.literal("Show Full-Height Column"), config.showColumn)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Highlight the target X/Z as a column from bedrock to build height, so you can find it from anywhere in the vertical."))
            .setSaveConsumer(val -> config.showColumn = val)
            .build()
        );
        general.addEntry(entry
            .startBooleanToggle(Text.literal("Show Ghost Frame"), config.showGhostFrame)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Draw an axis-matched outline of the exact obsidian frame and portal blocks at the recommended Y — build to the outline."))
            .setSaveConsumer(val -> config.showGhostFrame = val)
            .build()
        );
        general.addEntry(entry
            .startBooleanToggle(Text.literal("Show Floating Coords"), config.showFloatingCoords)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Show the exact target coordinates floating in the world at the target."))
            .setSaveConsumer(val -> config.showFloatingCoords = val)
            .build()
        );
        general.addEntry(entry
            .startBooleanToggle(Text.literal("Show HUD Coords"), config.showHudCoords)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Show a compact coordinate readout on the HUD while a selection is active."))
            .setSaveConsumer(val -> config.showHudCoords = val)
            .build()
        );
        general.addEntry(entry
            .startBooleanToggle(Text.literal("Highlight Selected Portal"), config.showSourceHighlight)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Outline the portal you selected while you are in its dimension, as confirmation."))
            .setSaveConsumer(val -> config.showSourceHighlight = val)
            .build()
        );
        general.addEntry(entry
            .startBooleanToggle(Text.literal("Draw Edge Lines"), config.drawEdgeLines)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Draw crisp outline edges on the column and the ghost frame."))
            .setSaveConsumer(val -> config.drawEdgeLines = val)
            .build()
        );

        // ── Target colour (three channel sliders — see SimDistance for the why) ──
        general.addEntry(entry
            .startIntSlider(Text.literal("Target Color — Red"), (config.targetColor >> 16) & 0xFF, 0, 255)
            .setDefaultValue(0xA2)
            .setSaveConsumer(val -> config.targetColor = (config.targetColor & 0x00FFFF) | ((val & 0xFF) << 16))
            .build()
        );
        general.addEntry(entry
            .startIntSlider(Text.literal("Target Color — Green"), (config.targetColor >> 8) & 0xFF, 0, 255)
            .setDefaultValue(0x4B)
            .setSaveConsumer(val -> config.targetColor = (config.targetColor & 0xFF00FF) | ((val & 0xFF) << 8))
            .build()
        );
        general.addEntry(entry
            .startIntSlider(Text.literal("Target Color — Blue"), config.targetColor & 0xFF, 0, 255)
            .setDefaultValue(0xF0)
            .setSaveConsumer(val -> config.targetColor = (config.targetColor & 0xFFFF00) | (val & 0xFF))
            .build()
        );
        general.addEntry(entry
            .startIntSlider(Text.literal("Target Opacity (%)"), config.targetOpacity, 0, 100)
            .setDefaultValue(28)
            .setSaveConsumer(val -> config.targetOpacity = val)
            .build()
        );

        // ── Source colour ────────────────────────────────────────────────────────
        general.addEntry(entry
            .startIntSlider(Text.literal("Source Color — Red"), (config.sourceColor >> 16) & 0xFF, 0, 255)
            .setDefaultValue(0x2B)
            .setSaveConsumer(val -> config.sourceColor = (config.sourceColor & 0x00FFFF) | ((val & 0xFF) << 16))
            .build()
        );
        general.addEntry(entry
            .startIntSlider(Text.literal("Source Color — Green"), (config.sourceColor >> 8) & 0xFF, 0, 255)
            .setDefaultValue(0xE0)
            .setSaveConsumer(val -> config.sourceColor = (config.sourceColor & 0xFF00FF) | ((val & 0xFF) << 8))
            .build()
        );
        general.addEntry(entry
            .startIntSlider(Text.literal("Source Color — Blue"), config.sourceColor & 0xFF, 0, 255)
            .setDefaultValue(0xC0)
            .setSaveConsumer(val -> config.sourceColor = (config.sourceColor & 0xFFFF00) | (val & 0xFF))
            .build()
        );
        general.addEntry(entry
            .startIntSlider(Text.literal("Source Opacity (%)"), config.sourceOpacity, 0, 100)
            .setDefaultValue(22)
            .setSaveConsumer(val -> config.sourceOpacity = val)
            .build()
        );

        return builder.build();
    }
}
