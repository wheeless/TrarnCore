package net.blowbyblow.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.blowbyblow.BlowByBlow;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Cloth Config screen for BlowByBlow.
 * Only classloaded when cloth-config is present — ModMenuIntegration guards the load.
 */
public class BlowByBlowConfigScreen {

    public static Screen build(Screen parent) {
        BlowByBlowConfig config = ConfigManager.get();

        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("BlowByBlow Settings"))
            .setSavingRunnable(ConfigManager::save);

        ConfigEntryBuilder entry = builder.entryBuilder();

        general(builder, entry, config, parent);
        appearance(builder, entry, config);
        content(builder, entry, config);
        floating(builder, entry, config);
        accuracy(builder, entry);

        return builder.build();
    }

    // ── General ───────────────────────────────────────────────────────────────

    private static void general(ConfigBuilder builder, ConfigEntryBuilder entry,
                                BlowByBlowConfig config, Screen parent) {
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

        general.addEntry(entry
            .startBooleanToggle(Component.literal("Enabled"), config.enabled)
            .setDefaultValue(true)
            .setSaveConsumer(value -> {
                config.enabled = value;
                BlowByBlow.enabled = value;
            })
            .build());

        general.addEntry(entry
            .startBooleanToggle(Component.literal("Show Panel"), config.showPanel)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Draw the feed as its own HUD panel."))
            .setSaveConsumer(value -> config.showPanel = value)
            .build());

        general.addEntry(entry
            .startBooleanToggle(Component.literal("Show In Chat"), config.showInChat)
            .setDefaultValue(false)
            .setTooltip(
                Component.literal("Also send every line to local chat. Local only — nothing is sent"),
                Component.literal("to the server."),
                Component.literal("Off by default: a fight is a line per hit, and chat keeps history,")
                    .withStyle(ChatFormatting.YELLOW),
                Component.literal("so leaving this on buries anything the server actually said.")
                    .withStyle(ChatFormatting.YELLOW))
            .setSaveConsumer(value -> config.showInChat = value)
            .build());

        general.addEntry(entry
            .startTextDescription(Component.literal(
                "Panel position is set by dragging, not by typing coordinates — press the button "
                + "below, or bind \"Move Combat Feed\" in Options → Controls.")
                .withStyle(ChatFormatting.GRAY))
            .build());

        // Cloth has no "button" entry, so this is a boolean that acts on being switched on and
        // never stores anything. Clunky, but it puts the placement screen one click from the
        // settings rather than requiring a keybind nobody has bound yet.
        general.addEntry(entry
            .startBooleanToggle(Component.literal("Move Panel  →"), false)
            .setDefaultValue(false)
            .setTooltip(Component.literal("Switch on and click Save to open the drag-to-place screen."))
            .setSaveConsumer(value -> {
                if (value) {
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> BlowByBlow.openPlacementScreen(client, null));
                }
            })
            .build());
    }

    // ── Appearance ────────────────────────────────────────────────────────────

    private static void appearance(ConfigBuilder builder, ConfigEntryBuilder entry, BlowByBlowConfig config) {
        ConfigCategory look = builder.getOrCreateCategory(Component.literal("Appearance"));

        look.addEntry(entry
            .startIntSlider(Component.literal("Max Lines"), config.maxLines, 1, 30)
            .setDefaultValue(8)
            .setSaveConsumer(value -> config.maxLines = value)
            .build());

        look.addEntry(entry
            .startIntSlider(Component.literal("Hold"), config.holdSeconds, 1, 60)
            .setDefaultValue(8)
            .setTextGetter(value -> Component.literal(value + "s"))
            .setTooltip(Component.literal("How long a line stays fully visible before fading."))
            .setSaveConsumer(value -> config.holdSeconds = value)
            .build());

        look.addEntry(entry
            .startIntSlider(Component.literal("Fade"), config.fadeSeconds, 0, 15)
            .setDefaultValue(1)
            .setTextGetter(value -> Component.literal(value == 0 ? "instant" : value + "s"))
            .setSaveConsumer(value -> config.fadeSeconds = value)
            .build());

        look.addEntry(entry
            .startBooleanToggle(Component.literal("Newest At Bottom"), config.newestAtBottom)
            .setDefaultValue(true)
            .setTooltip(
                Component.literal("Chat-style, growing upward. Off puts the newest line at the top."),
                Component.literal("Match this to where you put the panel, or it appears to crawl"),
                Component.literal("across the screen as it fills."))
            .setSaveConsumer(value -> config.newestAtBottom = value)
            .build());

        look.addEntry(entry
            .startAlphaColorField(Component.literal("Panel Background"), config.panelBackground)
            .setDefaultValue(0x50101014)
            .setTooltip(Component.literal("Fully transparent draws no panel, just the text."))
            .setSaveConsumer(value -> config.panelBackground = value)
            .build());

        look.addEntry(entry
            .startIntSlider(Component.literal("Padding"), config.padding, 0, 20)
            .setDefaultValue(4)
            .setSaveConsumer(value -> config.padding = value)
            .build());

        look.addEntry(entry
            .startIntSlider(Component.literal("Line Spacing"), config.lineSpacing, 0, 10)
            .setDefaultValue(1)
            .setSaveConsumer(value -> config.lineSpacing = value)
            .build());

        look.addEntry(entry
            .startBooleanToggle(Component.literal("Text Shadow"), config.textShadow)
            .setDefaultValue(true)
            .setSaveConsumer(value -> config.textShadow = value)
            .build());

        look.addEntry(entry
            .startColorField(Component.literal("Attacker Name Colour"), config.attackerColor)
            .setDefaultValue(0xFF7043)
            .setSaveConsumer(value -> config.attackerColor = value)
            .build());

        look.addEntry(entry
            .startColorField(Component.literal("Victim Name Colour"), config.victimColor)
            .setDefaultValue(0xFFD54F)
            .setSaveConsumer(value -> config.victimColor = value)
            .build());

        look.addEntry(entry
            .startColorField(Component.literal("Weapon Colour"), config.weaponColor)
            .setDefaultValue(0x90A4AE)
            .setSaveConsumer(value -> config.weaponColor = value)
            .build());
    }

    // ── What to log ───────────────────────────────────────────────────────────

    private static void content(ConfigBuilder builder, ConfigEntryBuilder entry, BlowByBlowConfig config) {
        ConfigCategory what = builder.getOrCreateCategory(Component.literal("What To Log"));

        what.addEntry(entry
            .startBooleanToggle(Component.literal("Name Weapons"), config.showWeapons)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Append \"with an Iron Sword\" where the weapon is known."))
            .setSaveConsumer(value -> config.showWeapons = value)
            .build());

        what.addEntry(entry
            .startBooleanToggle(Component.literal("Show Healing"), config.showHealing)
            .setDefaultValue(false)
            .setTooltip(Component.literal("Log your own healing. Natural regeneration makes this "
                + "chatty when you are simply well fed."))
            .setSaveConsumer(value -> config.showHealing = value)
            .build());

        what.addEntry(entry
            .startBooleanToggle(Component.literal("Show Nearby Fights"), config.showBystanders)
            .setDefaultValue(false)
            .setTooltip(
                Component.literal("Log damage between two other parties near you."),
                Component.literal("Off by default — near a mob farm or a busy server this is")
                    .withStyle(ChatFormatting.YELLOW),
                Component.literal("thousands of lines you did not ask for.").withStyle(ChatFormatting.YELLOW))
            .setSaveConsumer(value -> config.showBystanders = value)
            .build());

        what.addEntry(entry
            .startIntSlider(Component.literal("Track Radius"), config.trackRadius, 4, 128)
            .setDefaultValue(32)
            .setTextGetter(value -> Component.literal(value + " blocks"))
            .setTooltip(Component.literal("How far out to watch other entities' health."))
            .setSaveConsumer(value -> config.trackRadius = value)
            .build());

        what.addEntry(entry
            .startBooleanToggle(Component.literal("Show Hearts"), config.showInHearts)
            .setDefaultValue(true)
            .setTooltip(
                Component.literal("Hearts (3♥) rather than the half-heart points the game counts in (6)."),
                Component.literal("The game's own tooltips use points; the health bar shows hearts."))
            .setSaveConsumer(value -> config.showInHearts = value)
            .build());
    }

    // ── Floating numbers ──────────────────────────────────────────────────────

    private static void floating(ConfigBuilder builder, ConfigEntryBuilder entry, BlowByBlowConfig config) {
        ConfigCategory pop = builder.getOrCreateCategory(Component.literal("Floating Numbers"));

        pop.addEntry(entry
            .startBooleanToggle(Component.literal("Enabled"), config.floatingNumbers)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Pop the number off whatever was hit."))
            .setSaveConsumer(value -> config.floatingNumbers = value)
            .build());

        pop.addEntry(entry
            .startIntSlider(Component.literal("Lifetime"), config.floatingLifetimeMillis, 200, 5000)
            .setDefaultValue(1200)
            .setTextGetter(value -> Component.literal(value + "ms"))
            .setSaveConsumer(value -> config.floatingLifetimeMillis = value)
            .build());

        pop.addEntry(entry
            .startFloatField(Component.literal("Rise"), config.floatingRise)
            .setDefaultValue(0.9f)
            .setMin(0f).setMax(6f)
            .setTooltip(Component.literal("How far the number drifts upward, in blocks."))
            .setSaveConsumer(value -> config.floatingRise = value)
            .build());

        pop.addEntry(entry
            .startFloatField(Component.literal("Scale"), config.floatingScale)
            .setDefaultValue(0.025f)
            .setMin(0.005f).setMax(0.2f)
            .setSaveConsumer(value -> config.floatingScale = value)
            .build());

        pop.addEntry(entry
            .startBooleanToggle(Component.literal("See Through Walls"), config.floatingSeeThrough)
            .setDefaultValue(false)
            .setSaveConsumer(value -> config.floatingSeeThrough = value)
            .build());

        pop.addEntry(entry
            .startIntSlider(Component.literal("Max On Screen"), config.maxFloatingNumbers, 1, 200)
            .setDefaultValue(40)
            .setSaveConsumer(value -> config.maxFloatingNumbers = value)
            .build());

        pop.addEntry(entry
            .startColorField(Component.literal("Outgoing Colour"), config.floatingOutgoingColor)
            .setDefaultValue(0xFFFFFF)
            .setSaveConsumer(value -> config.floatingOutgoingColor = value)
            .build());

        pop.addEntry(entry
            .startColorField(Component.literal("Incoming Colour"), config.floatingIncomingColor)
            .setDefaultValue(0xFF5252)
            .setSaveConsumer(value -> config.floatingIncomingColor = value)
            .build());
    }

    // ── Accuracy note ─────────────────────────────────────────────────────────

    private static void accuracy(ConfigBuilder builder, ConfigEntryBuilder entry) {
        ConfigCategory why = builder.getOrCreateCategory(Component.literal("Accuracy"));
        BlowByBlowConfig config = ConfigManager.get();

        text(why, entry, "What this mod can and cannot know", ChatFormatting.GOLD);
        text(why, entry, " ", ChatFormatting.WHITE);
        text(why, entry, "Who hit what, and with which damage type, comes from the game. The server",
            ChatFormatting.GRAY);
        text(why, entry, "sends a damage event for everything you can see and Minecraft records it",
            ChatFormatting.GRAY);
        text(why, entry, "on the entity, so attribution is not guesswork.", ChatFormatting.GRAY);
        text(why, entry, " ", ChatFormatting.WHITE);
        text(why, entry, "The number is not in that packet. Amounts come from watching health.",
            ChatFormatting.GRAY);
        text(why, entry, " ", ChatFormatting.WHITE);
        text(why, entry, "Damage to you is exact — your health is sent to you precisely.",
            ChatFormatting.GREEN);
        text(why, entry, "Damage you deal is a floor. Another entity's health is synced tracked",
            ChatFormatting.YELLOW);
        text(why, entry, "data: accurate when it arrives, but blind to overkill. A mob on 2 hearts",
            ChatFormatting.YELLOW);
        text(why, entry, "hit for 10 reports 2, because 2 is all the health there was to lose.",
            ChatFormatting.YELLOW);
        text(why, entry, " ", ChatFormatting.WHITE);
        text(why, entry, "That is why inferred numbers are marked with ~, and why this is a log",
            ChatFormatting.GRAY);
        text(why, entry, "rather than a damage meter: every line is an observation, whereas a DPS",
            ChatFormatting.GRAY);
        text(why, entry, "total would accumulate that error and state it as fact.", ChatFormatting.GRAY);

        why.addEntry(entry
            .startBooleanToggle(Component.literal("Mark Inferred Amounts With ~"), config.markInferredAmounts)
            .setDefaultValue(true)
            .setSaveConsumer(value -> config.markInferredAmounts = value)
            .build());
    }

    private static void text(ConfigCategory category, ConfigEntryBuilder entry, String line, ChatFormatting color) {
        category.addEntry(entry.startTextDescription(Component.literal(line).withStyle(color)).build());
    }
}
