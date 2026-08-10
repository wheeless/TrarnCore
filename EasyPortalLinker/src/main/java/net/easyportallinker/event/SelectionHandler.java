package net.easyportallinker.event;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.easyportallinker.EasyPortalLinker;
import net.easyportallinker.config.ConfigManager;
import net.easyportallinker.config.EasyPortalLinkerConfig;
import net.easyportallinker.portal.LinkMath;
import net.easyportallinker.portal.PortalDetector;
import net.easyportallinker.portal.PortalScan;
import net.easyportallinker.portal.PortalTarget;

/**
 * Right-click with the selection item (default: wooden shovel) to select the portal you are
 * looking at or standing in. We only consume the interaction when a portal is actually detected,
 * so the item still behaves normally everywhere else — no dirt-path conflict.
 *
 * <p>Sneak + right-click clears the current selection (configurable).
 */
public class SelectionHandler {

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            return tryHandle(player, world) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        // Catch-all for when the crosshair isn't on a block (e.g. standing in a portal looking at
        // open air): the item-use event still fires.
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            return tryHandle(player, world) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
    }

    private static boolean tryHandle(Player player, Level world) {
        try {
            if (!world.isClientSide()) return false; // client-only mod; be defensive
            if (!isSelectionItem(player.getMainHandItem())) return false;

            EasyPortalLinkerConfig cfg = ConfigManager.get();

            // Sneak + item → clear the current selection.
            if (cfg.requireSneakToClear && player.isShiftKeyDown()) {
                if (EasyPortalLinker.selection != null) {
                    EasyPortalLinker.clearSelection();
                    msg("§c[EasyPortalLinker] Selection cleared");
                    return true;
                }
                return false; // nothing to clear → let the item behave normally
            }

            BlockPos portal = PortalDetector.findPortal(world, player, cfg.selectReach);
            if (portal == null) return false;

            PortalTarget target = PortalScan.scan(world, portal);
            if (target == null) return false;

            EasyPortalLinker.setSelection(target);
            announce(target);
            return true;
        } catch (Exception e) {
            EasyPortalLinker.LOGGER.error("[EasyPortalLinker] Selection failed", e);
            return false;
        }
    }

    private static boolean isSelectionItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(resolveItem(ConfigManager.get().selectionItem));
    }

    private static Item resolveItem(String id) {
        try {
            Identifier ident = Identifier.tryParse(id);
            if (ident != null) {
                Item it = BuiltInRegistries.ITEM.getValue(ident);
                if (it != Items.AIR) return it;
            }
        } catch (Exception ignored) {
            // fall through to default
        }
        return Items.WOODEN_SHOVEL;
    }

    private static void announce(PortalTarget t) {
        if (!t.hasDestination()) {
            msg("§6[EasyPortalLinker] §fSelected a portal in "
                + LinkMath.dimLabel(t.sourceDim)
                + ", but linking is only supported between the Overworld and the Nether.");
            return;
        }
        int recY = LinkMath.recommendedY(t.destDim, t.sourceY, t.height);
        msg("§6[EasyPortalLinker] §fSelected portal at §e"
            + t.sourceX + " " + t.sourceY + " " + t.sourceZ
            + "§f (axis " + t.axis + ") in " + LinkMath.dimLabel(t.sourceDim) + ".");
        msg("§fBuild the counterpart in the §d" + LinkMath.dimLabel(t.destDim)
            + "§f at §a" + t.destX + " " + recY + " " + t.destZ
            + "§f (axis " + t.axis + "). Head there and follow the highlight.");
    }

    /**
     * These already went to chat rather than the action bar, but via {@code player.sendMessage},
     * which is only local because this happens to be a client player. Going through the chat HUD
     * directly makes that guarantee explicit rather than incidental. No prefix is added — these
     * messages carry their own, and the last line is a deliberate continuation without one.
     */
    private static void msg(String s) {
        EasyPortalLinker.CHAT.sendRaw(s);
    }
}
