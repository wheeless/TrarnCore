package net.rswitch.swap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.rswitch.RSwitch;
import net.rswitch.config.ConfigManager;

/**
 * Swaps the held item with the inventory slot directly above it.
 *
 * <p>The whole operation is a single vanilla {@link ClickType#SWAP} click — the same thing
 * the game does when you press a number key while hovering a slot in your inventory. That means
 * no server-side component, nothing that looks unusual to anticheat, and empty slots are handled
 * for free: swapping into an empty slot simply empties your hand, which is the "clear my hand"
 * case.
 */
public final class InventorySwapper {

    /** Result of an attempted swap, so the caller can decide what (if anything) to tell the player. */
    public enum Result {
        SWAPPED,
        /** Both slots were empty — nothing to do, and no point sending a packet. */
        NOTHING_TO_SWAP,
        /** Not in a state where inventory clicks make sense (spectator, screen open, no world). */
        UNAVAILABLE
    }

    private InventorySwapper() {
    }

    public static Result swap(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null) return Result.UNAVAILABLE;

        // A container is open — its handler owns the slot indices and syncId, and "the slot above
        // your hand" is not a meaningful idea while you are looking at a chest.
        if (client.screen != null) return Result.UNAVAILABLE;

        // Spectators have no inventory to rearrange.
        if (player.isSpectator()) return Result.UNAVAILABLE;

        InventoryMenu handler = player.inventoryMenu;
        AbstractContainerMenu current = player.containerMenu;
        // With no screen open these are the same object. If they somehow are not, bail rather
        // than send a click against a syncId the server will reject.
        if (handler == null || current == null || current.containerId != handler.containerId) {
            return Result.UNAVAILABLE;
        }

        int hotbarSlot = player.getInventory().getSelectedSlot();
        if (!Inventory.isHotbarSlot(hotbarSlot)) return Result.UNAVAILABLE;

        int targetSlot = slotAbove(hotbarSlot, ConfigManager.get().rowsUp);

        ItemStack held = handler.getSlot(InventoryMenu.USE_ROW_SLOT_START + hotbarSlot).getItem();
        ItemStack above = handler.getSlot(targetSlot).getItem();
        if (held.isEmpty() && above.isEmpty()) return Result.NOTHING_TO_SWAP;

        // SWAP's button argument is the hotbar index to exchange with, not a mouse button.
        client.gameMode.handleContainerInput(
            handler.containerId, targetSlot, hotbarSlot, ContainerInput.SWAP, player);

        RSwitch.LOGGER.debug("[RSwitch] Swapped hotbar {} with screen slot {}", hotbarSlot, targetSlot);
        return Result.SWAPPED;
    }

    /**
     * Screen-handler slot index for the inventory slot {@code rowsUp} rows above a hotbar slot.
     *
     * <p>Worth being explicit about, because {@link InventoryMenu} slot indices are not
     * {@link Inventory} indices — the handler puts crafting and armour slots first, so the
     * main inventory sits at 9–35 and the hotbar at 36–44. The bottom inventory row, which is the
     * one drawn directly above the hotbar, is therefore 27–35. Derived from the vanilla constants
     * rather than written as literals so it follows any future re-layout.
     */
    private static int slotAbove(int hotbarSlot, int rowsUp) {
        int rows = Math.max(1, Math.min(3, rowsUp));
        return InventoryMenu.INV_SLOT_END - rows * Inventory.SELECTION_SIZE + hotbarSlot;
    }
}
