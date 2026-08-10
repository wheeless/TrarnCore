package net.rswitch.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Cloth Config screen for RSwitch.
 * Only classloaded when cloth-config is present — ModMenuIntegration guards the load.
 */
public class RSwitchConfigScreen {

    public static Screen build(Screen parent) {
        RSwitchConfig config = ConfigManager.get();

        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("RSwitch Settings"))
            .setSavingRunnable(ConfigManager::save);

        ConfigEntryBuilder entry = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

        general.addEntry(entry
            .startBooleanToggle(Component.literal("Enabled"), config.enabled)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Master on/off. When disabled the hotkey does nothing."))
            .setSaveConsumer(value -> config.enabled = value)
            .build());

        general.addEntry(entry
            .startIntSlider(Component.literal("Rows Above"), config.rowsUp, 1, 3)
            .setDefaultValue(1)
            .setTextGetter(value -> Component.literal(switch (value) {
                case 1 -> "1 — directly above";
                case 2 -> "2 — middle row";
                default -> "3 — top row";
            }))
            .setTooltip(Component.literal("Which inventory row to swap with. 1 is the row drawn directly "
                + "above your hotbar."))
            .setSaveConsumer(value -> config.rowsUp = value)
            .build());

        general.addEntry(entry
            .startBooleanToggle(Component.literal("Play Sound"), config.playSound)
            .setDefaultValue(false)
            .setTooltip(Component.literal("Play a soft click when a swap happens."))
            .setSaveConsumer(value -> config.playSound = value)
            .build());

        general.addEntry(entry
            .startBooleanToggle(Component.literal("Show Chat Message"), config.showChatMessage)
            .setDefaultValue(false)
            .setTooltip(
                Component.literal("Name the item you just swapped to in chat, or say \"Hand cleared\" "
                    + "when you swap into an empty slot."),
                Component.literal("Local only — nothing is sent to the server."),
                Component.literal("Fires on every press, and chat keeps history, so this gets noisy fast."))
            .setSaveConsumer(value -> config.showChatMessage = value)
            .build());

        return builder.build();
    }
}
