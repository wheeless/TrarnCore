package net.rswitch.event;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.rswitch.RSwitch;
import net.rswitch.config.ConfigManager;
import net.rswitch.config.RSwitchConfig;
import net.rswitch.swap.InventorySwapper;
import net.trarncore.input.Keys;
import net.trarncore.util.Guarded;

public class ClientTickHandler {

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client ->
            Guarded.run(RSwitch.LOGGER, "RSwitch keybind handler", () -> handleKeybinds(client)));
    }

    private static void handleKeybinds(MinecraftClient client) {
        Keys.whenPressed(RSwitch.SWAP, () -> {
            RSwitchConfig config = ConfigManager.get();
            if (!config.enabled) return;

            if (InventorySwapper.swap(client) != InventorySwapper.Result.SWAPPED) return;

            if (config.playSound && client.player != null) {
                // Deliberately quiet and client-side only — this is a confirmation, not an event.
                client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25f, 1.8f);
            }
            if (config.showChatMessage && client.player != null) {
                ItemStack held = client.player.getInventory().getSelectedStack();
                RSwitch.CHAT.send(held.isEmpty()
                    ? Text.literal("Hand cleared").formatted(Formatting.GRAY)
                    : held.getName().copy());
            }
        });
    }
}
