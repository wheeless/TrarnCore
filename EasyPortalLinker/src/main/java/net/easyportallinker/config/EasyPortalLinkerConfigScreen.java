package net.easyportallinker.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
            .setTitle(Component.literal("EasyPortalLinker Settings"))
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
        ConfigCategory tutorial = builder.getOrCreateCategory(Component.literal("How to Link"));
        tutorial.addEntry(entry
            .startTextDescription(Component.literal(
                "§l§dEasyPortalLinker — reliable portal linking§r\n"
                    + "This is the bulletproof way to link Nether portals: build the counterpart portal "
                    + "exactly where the game will look for it, so it becomes the closest link target."))
            .build());
        tutorial.addEntry(entry
            .startTextDescription(Component.literal(
                "§e§lWhy it works§r\n"
                    + "Traveling scales your position by the dimensions' coordinate ratio (Overworld 1, "
                    + "Nether 8):\n"
                    + " • Overworld → Nether: X and Z ÷ 8 (floored)\n"
                    + " • Nether → Overworld: X and Z × 8\n"
                    + "Y stays the same (clamped to the destination). The game then links to the closest "
                    + "existing portal within 128 blocks of that point — so a portal built right on it wins."))
            .build());
        tutorial.addEntry(entry
            .startTextDescription(Component.literal(
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
            .startTextDescription(Component.literal(
                "§6§lFor the most reliable link§r\n"
                    + " • Build the destination portal §obefore§r lighting or entering the source, so the game "
                    + "doesn't dig out a stray portal near the target first.\n"
                    + " • Watch for other portals within ~128 blocks of the target: a closer existing portal "
                    + "(including auto-generated ones) can hijack the link. Break strays if a link goes wrong.\n"
                    + " • Matching the axis isn't required for linking, but keeps things tidy — the guide shows it."))
            .build());
        tutorial.addEntry(entry
            .startTextDescription(Component.literal(
                "§b§lControls§r\n"
                    + " • §fP§r — toggle the guide on/off (rebindable in Options → Controls).\n"
                    + " • §fK§r — lock the target Y to your current level; §fsneak + K§r to unlock "
                    + "(back to following your feet).\n"
                    + " • §fSneak + right-click§r with the selection item — clear the selection (or bind the "
                    + "Clear Portal Selection key).\n"
                    + "The selection is remembered across dimension changes and restarts."))
            .build());

        ConfigCategory general = builder.getOrCreateCategory(Component.literal("Settings"));

        general.addEntry(entry
            .startBooleanToggle(Component.literal("Enabled"), config.enabled)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Master on/off for the portal guide. Also bound to the toggle hotkey (default: P)."))
            .setSaveConsumer(val -> config.enabled = val)
            .build()
        );
        general.addEntry(entry
            .startStrField(Component.literal("Selection Item"), config.selectionItem)
            .setDefaultValue("minecraft:wooden_shovel")
            .setTooltip(Component.literal("Item id that selects a portal on right-click. It only 'steals' the click when a portal is actually detected, so the item still works normally otherwise."))
            .setSaveConsumer(val -> config.selectionItem = val)
            .build()
        );
        general.addEntry(entry
            .startIntSlider(Component.literal("Select Reach (blocks)"), config.selectReach, 3, 12)
            .setDefaultValue(6)
            .setTooltip(Component.literal("How far the selection ray reaches when looking for a portal."))
            .setSaveConsumer(val -> config.selectReach = val)
            .build()
        );
        general.addEntry(entry
            .startBooleanToggle(Component.literal("Require Sneak To Clear"), config.requireSneakToClear)
            .setDefaultValue(true)
            .setTooltip(Component.literal("When on, sneak + right-click with the selection item clears the current selection."))
            .setSaveConsumer(val -> config.requireSneakToClear = val)
            .build()
        );
        general.addEntry(entry
            .startBooleanToggle(Component.literal("Lock Target Y"), config.lockTargetY)
            .setDefaultValue(false)
            .setTooltip(Component.literal("Pin the ghost frame's base to a fixed Y instead of tracking your feet. Handy for a Nether hub built at one consistent level. The value is clamped to the destination dimension's build range."))
            .setSaveConsumer(val -> config.lockTargetY = val)
            .build()
        );
        general.addEntry(entry
            .startIntField(Component.literal("Locked Target Y"), config.lockedTargetY)
            .setDefaultValue(120)
            .setTooltip(Component.literal("Base Y of the ghost frame when 'Lock Target Y' is on (e.g. 120 for just under the Nether roof)."))
            .setSaveConsumer(val -> config.lockedTargetY = val)
            .build()
        );
        general.addEntry(entry
            .startBooleanToggle(Component.literal("Forget Selected Portal"), false)
            .setDefaultValue(false)
            .setTooltip(Component.literal("Turn on and save to clear the currently remembered portal selection."))
            .setSaveConsumer(val -> forget[0] = val)
            .build()
        );

        // ── What to draw ────────────────────────────────────────────────────────
        general.addEntry(entry
            .startBooleanToggle(Component.literal("Show Full-Height Column"), config.showColumn)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Highlight the target X/Z as a column from bedrock to build height, so you can find it from anywhere in the vertical."))
            .setSaveConsumer(val -> config.showColumn = val)
            .build()
        );
        general.addEntry(entry
            .startBooleanToggle(Component.literal("Show Ghost Frame"), config.showGhostFrame)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Draw an axis-matched outline of the exact obsidian frame and portal blocks at the recommended Y — build to the outline."))
            .setSaveConsumer(val -> config.showGhostFrame = val)
            .build()
        );
        general.addEntry(entry
            .startBooleanToggle(Component.literal("Show Floating Coords"), config.showFloatingCoords)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Show the exact target coordinates floating in the world at the target."))
            .setSaveConsumer(val -> config.showFloatingCoords = val)
            .build()
        );
        general.addEntry(entry
            .startBooleanToggle(Component.literal("Show HUD Coords"), config.showHudCoords)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Show a compact coordinate readout on the HUD while a selection is active."))
            .setSaveConsumer(val -> config.showHudCoords = val)
            .build()
        );
        general.addEntry(entry
            .startBooleanToggle(Component.literal("Highlight Selected Portal"), config.showSourceHighlight)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Outline the portal you selected while you are in its dimension, as confirmation."))
            .setSaveConsumer(val -> config.showSourceHighlight = val)
            .build()
        );
        general.addEntry(entry
            .startBooleanToggle(Component.literal("Draw Edge Lines"), config.drawEdgeLines)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Draw crisp outline edges on the column and the ghost frame."))
            .setSaveConsumer(val -> config.drawEdgeLines = val)
            .build()
        );

        // ── Target colour (three channel sliders — see SimDistance for the why) ──
        general.addEntry(entry
            .startIntSlider(Component.literal("Target Color — Red"), (config.targetColor >> 16) & 0xFF, 0, 255)
            .setDefaultValue(0xA2)
            .setSaveConsumer(val -> config.targetColor = (config.targetColor & 0x00FFFF) | ((val & 0xFF) << 16))
            .build()
        );
        general.addEntry(entry
            .startIntSlider(Component.literal("Target Color — Green"), (config.targetColor >> 8) & 0xFF, 0, 255)
            .setDefaultValue(0x4B)
            .setSaveConsumer(val -> config.targetColor = (config.targetColor & 0xFF00FF) | ((val & 0xFF) << 8))
            .build()
        );
        general.addEntry(entry
            .startIntSlider(Component.literal("Target Color — Blue"), config.targetColor & 0xFF, 0, 255)
            .setDefaultValue(0xF0)
            .setSaveConsumer(val -> config.targetColor = (config.targetColor & 0xFFFF00) | (val & 0xFF))
            .build()
        );
        general.addEntry(entry
            .startIntSlider(Component.literal("Target Opacity (%)"), config.targetOpacity, 0, 100)
            .setDefaultValue(28)
            .setSaveConsumer(val -> config.targetOpacity = val)
            .build()
        );

        // ── Source colour ────────────────────────────────────────────────────────
        general.addEntry(entry
            .startIntSlider(Component.literal("Source Color — Red"), (config.sourceColor >> 16) & 0xFF, 0, 255)
            .setDefaultValue(0x2B)
            .setSaveConsumer(val -> config.sourceColor = (config.sourceColor & 0x00FFFF) | ((val & 0xFF) << 16))
            .build()
        );
        general.addEntry(entry
            .startIntSlider(Component.literal("Source Color — Green"), (config.sourceColor >> 8) & 0xFF, 0, 255)
            .setDefaultValue(0xE0)
            .setSaveConsumer(val -> config.sourceColor = (config.sourceColor & 0xFF00FF) | ((val & 0xFF) << 8))
            .build()
        );
        general.addEntry(entry
            .startIntSlider(Component.literal("Source Color — Blue"), config.sourceColor & 0xFF, 0, 255)
            .setDefaultValue(0xC0)
            .setSaveConsumer(val -> config.sourceColor = (config.sourceColor & 0xFFFF00) | (val & 0xFF))
            .build()
        );
        general.addEntry(entry
            .startIntSlider(Component.literal("Source Opacity (%)"), config.sourceOpacity, 0, 100)
            .setDefaultValue(22)
            .setSaveConsumer(val -> config.sourceOpacity = val)
            .build()
        );

        portalHighlights(builder, entry, config);

        return builder.build();
    }

    // ── Portal highlights ─────────────────────────────────────────────────────

    private static void portalHighlights(ConfigBuilder builder, ConfigEntryBuilder entry,
                                         EasyPortalLinkerConfig config) {
        ConfigCategory esp = builder.getOrCreateCategory(Component.literal("Highlights"));

        esp.addEntry(entry
            .startTextDescription(Component.literal(
                "Highlights every nether portal around you, the way ContainerUtil highlights "
                + "containers. Toggled in-game with the Portal Highlights key (O by default).")
                .withStyle(ChatFormatting.GRAY))
            .build());

        esp.addEntry(entry
            .startBooleanToggle(Component.literal("Enabled"), config.portalEsp)
            .setDefaultValue(false)
            .setTooltip(Component.literal("Also bound to a hotkey — this and the key are the same switch."))
            .setSaveConsumer(value -> {
                config.portalEsp = value;
                EasyPortalLinker.portalEsp = value;
            })
            .build());

        esp.addEntry(entry
            .startColorField(Component.literal("Portal Colour"), config.portalEspColor)
            .setDefaultValue(0xA24BF0)
            .setTooltip(Component.literal("Colour for lit, working portals."))
            .setSaveConsumer(value -> config.portalEspColor = value)
            .build());

        esp.addEntry(entry
            .startIntSlider(Component.literal("Portal Opacity"), config.portalEspOpacity, 0, 100)
            .setDefaultValue(30)
            .setTextGetter(value -> Component.literal(value == 0 ? "outline only" : value + "%"))
            .setSaveConsumer(value -> config.portalEspOpacity = value)
            .build());

        esp.addEntry(entry
            .startBooleanToggle(Component.literal("Detect Unlit Frames"), config.detectUnlitFrames)
            .setDefaultValue(false)
            .setTooltip(
                Component.literal("Also highlight complete frames nobody has lit."),
                Component.literal("Detection is exact — it asks Minecraft the same question flint"),
                Component.literal("and steel does, so what lights up is what would light."),
                Component.literal("Costs more than finding lit portals: obsidian is common enough")
                    .withStyle(ChatFormatting.YELLOW),
                Component.literal("that the chunk-palette shortcut skips far fewer sections.")
                    .withStyle(ChatFormatting.YELLOW))
            .setSaveConsumer(value -> config.detectUnlitFrames = value)
            .build());

        esp.addEntry(entry
            .startColorField(Component.literal("Unlit Frame Colour"), config.unlitFrameColor)
            .setDefaultValue(0x6A3FA0)
            .setTooltip(Component.literal("Duller purple by default, so lit and unlit read as the "
                + "same family while still being told apart."))
            .setSaveConsumer(value -> config.unlitFrameColor = value)
            .build());

        esp.addEntry(entry
            .startIntSlider(Component.literal("Unlit Frame Opacity"), config.unlitFrameOpacity, 0, 100)
            .setDefaultValue(18)
            .setTextGetter(value -> Component.literal(value == 0 ? "outline only" : value + "%"))
            .setSaveConsumer(value -> config.unlitFrameOpacity = value)
            .build());

        esp.addEntry(entry
            .startIntSlider(Component.literal("Unlit Frame Radius"), config.unlitFrameChunkRadius, 1, 16)
            .setDefaultValue(4)
            .setTextGetter(value -> Component.literal(value + " chunks"))
            .setTooltip(Component.literal("Kept smaller than the main radius, since unlit frames are "
                + "the expensive half of the sweep."))
            .setSaveConsumer(value -> config.unlitFrameChunkRadius = value)
            .build());

        esp.addEntry(entry
            .startIntSlider(Component.literal("Radius"), config.portalEspChunkRadius, 1, 32)
            .setDefaultValue(8)
            .setTextGetter(value -> Component.literal(value + " chunks"))
            .setTooltip(Component.literal("Horizontal only. Chunks load as full columns, so a portal "
                + "at bedrock two chunks away is as loaded as one at your feet."))
            .setSaveConsumer(value -> config.portalEspChunkRadius = value)
            .build());

        esp.addEntry(entry
            .startIntSlider(Component.literal("Vertical Limit"), config.portalEspVerticalLimit, 0, 320)
            .setDefaultValue(0)
            .setTextGetter(value -> Component.literal(value == 0 ? "whole column" : "±" + value + " blocks"))
            .setTooltip(Component.literal("Hide portals further than this above or below you. "
                + "0 shows the whole column."))
            .setSaveConsumer(value -> config.portalEspVerticalLimit = value)
            .build());

        esp.addEntry(entry
            .startIntField(Component.literal("Max Highlights"), config.maxRenderedPortals)
            .setDefaultValue(64)
            .setMin(1).setMax(512)
            .setTooltip(Component.literal("Hard cap on boxes drawn at once. The nearest survive."))
            .setSaveConsumer(value -> config.maxRenderedPortals = value)
            .build());

        esp.addEntry(entry
            .startBooleanToggle(Component.literal("See Through Walls"), config.portalEspSeeThrough)
            .setDefaultValue(true)
            .setSaveConsumer(value -> config.portalEspSeeThrough = value)
            .build());

        esp.addEntry(entry
            .startBooleanToggle(Component.literal("Draw Fill"), config.portalEspFill)
            .setDefaultValue(true)
            .setSaveConsumer(value -> config.portalEspFill = value)
            .build());

        esp.addEntry(entry
            .startBooleanToggle(Component.literal("Draw Outline"), config.portalEspOutline)
            .setDefaultValue(true)
            .setSaveConsumer(value -> config.portalEspOutline = value)
            .build());
    }
}
