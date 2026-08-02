package net.trustui.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.trustui.trust.TrustLevel;

/**
 * Cloth Config screen for TrustUI.
 * Only classloaded when cloth-config is present — ModMenuIntegration guards the load.
 */
public class TrustUIConfigScreen {

    public static Screen build(Screen parent) {
        TrustUIConfig config = ConfigManager.get();
        config.validate();

        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Text.literal("TrustUI Settings"))
            .setSavingRunnable(ConfigManager::save);

        ConfigEntryBuilder entry = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(Text.literal("General"));

        general.addEntry(entry
            .startBooleanToggle(Text.literal("Hide Trust List From Chat"), config.hideTrustListOutput)
            .setDefaultValue(true)
            .setTooltip(
                Text.literal("Suppress the raw /trustlist output while the menu reads it."),
                Text.literal("Only lines that belong to the listing are hidden — anything else that "
                    + "arrives at the same moment still reaches you."))
            .setSaveConsumer(value -> config.hideTrustListOutput = value)
            .build());

        general.addEntry(entry
            .startBooleanToggle(Text.literal("Refresh After Changes"), config.refreshAfterChange)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Re-read the claim after granting or revoking, so the menu shows "
                + "what the server actually did rather than what was assumed."))
            .setSaveConsumer(value -> config.refreshAfterChange = value)
            .build());

        general.addEntry(entry
            .startBooleanToggle(Text.literal("Show Offline Trusted Players"), config.showOfflineTrusted)
            .setDefaultValue(true)
            .setTooltip(Text.literal("List players who hold trust but are not online, so their access "
                + "can still be removed."))
            .setSaveConsumer(value -> config.showOfflineTrusted = value)
            .build());

        general.addEntry(entry
            .startIntSlider(Text.literal("Reply Timeout (ticks)"), config.trustListTimeoutTicks, 10, 100)
            .setDefaultValue(30)
            .setTooltip(Text.literal("How long to wait for the server's reply. 20 ticks = 1 second. "
                + "Raise it on a laggy server if the menu keeps reporting no claim."))
            .setSaveConsumer(value -> config.trustListTimeoutTicks = value)
            .build());

        // ── Commands ─────────────────────────────────────────────────────────
        ConfigCategory commands = builder.getOrCreateCategory(Text.literal("Commands"));

        commands.addEntry(entry
            .startTextDescription(Text.literal("§7Command names, without the leading slash. Change "
                + "these if your server aliases GriefPrevention's commands or runs a fork that names "
                + "them differently."))
            .build());

        commands.addEntry(entry
            .startStrField(Text.literal("List Permissions"), config.trustListCommand)
            .setDefaultValue("trustlist")
            .setSaveConsumer(value -> config.trustListCommand = value)
            .build());

        for (TrustLevel level : TrustLevel.values()) {
            commands.addEntry(entry
                .startStrField(Text.literal("Grant " + level.displayName()), config.commandFor(level))
                .setDefaultValue(level.defaultCommand())
                .setSaveConsumer(value -> config.grantCommands.put(level.name(), value))
                .build());
        }

        commands.addEntry(entry
            .startStrField(Text.literal("Remove All Trust"), config.untrustCommand)
            .setDefaultValue("untrust")
            .setSaveConsumer(value -> config.untrustCommand = value)
            .build());

        commands.addEntry(entry
            .startStrField(Text.literal("Listing Header"), config.trustListHeader)
            .setDefaultValue("Explicit permissions here:")
            .setTooltip(Text.literal("Used only to hide the header line from chat. Parsing keys off "
                + "the '>' prefix, so getting this wrong costs a stray chat line, not a broken list."))
            .setSaveConsumer(value -> config.trustListHeader = value)
            .build());

        return builder.build();
    }
}
