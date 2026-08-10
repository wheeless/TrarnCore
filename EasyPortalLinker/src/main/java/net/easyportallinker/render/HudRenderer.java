package net.easyportallinker.render;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.easyportallinker.EasyPortalLinker;
import net.easyportallinker.config.ConfigManager;
import net.easyportallinker.config.EasyPortalLinkerConfig;
import net.easyportallinker.portal.LinkMath;
import net.easyportallinker.portal.PortalTarget;

/**
 * A compact, always-legible coordinate readout at the top of the screen while a selection is
 * active — the reliable text complement to the in-world floating label.
 */
public class HudRenderer {

    private static final int COLOR = 0xFFB784F5; // light purple, full alpha

    private static long lastError = 0;

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(EasyPortalLinker.MOD_ID, "overlay"),
            (drawContext, tickCounter) -> {
            try {
                renderHud(drawContext);
            } catch (Exception e) {
                long now = System.currentTimeMillis();
                if (now - lastError > 5000) {
                    lastError = now;
                    EasyPortalLinker.LOGGER.error("[EasyPortalLinker] HUD render crashed (suppressing repeats for 5s)", e);
                }
            }
        });
    }

    private static void renderHud(GuiGraphicsExtractor drawContext) {
        if (!EasyPortalLinker.enabled) return;
        PortalTarget sel = EasyPortalLinker.selection;
        if (sel == null || sel.sourceDim == null) return;

        EasyPortalLinkerConfig cfg = ConfigManager.get();
        if (!cfg.showHudCoords) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        String dim = client.level.dimension().identifier().toString();

        int height = Math.max(3, sel.height);
        int idealY = LinkMath.recommendedY(sel.destDim, sel.sourceY, height);
        String text;
        if (sel.isDestDim(dim)) {
            String yPart;
            if (cfg.lockTargetY) {
                int lo = client.level.getMinY() + 2;
                int hi = client.level.getMaxY() - height;
                if (hi < lo) hi = lo;
                int ry = Math.max(lo, Math.min(hi, cfg.lockedTargetY));
                yPart = "  Y " + ry + " (locked)";
            } else {
                yPart = " - any Y links, ideal " + idealY;
            }
            text = "EasyPortalLinker  |  build in " + LinkMath.dimLabel(sel.destDim)
                + " at  X " + sel.destX + "  Z " + sel.destZ + "  (axis " + sel.axis + ")" + yPart;
        } else if (sel.isSourceDim(dim)) {
            if (sel.hasDestination()) {
                text = "EasyPortalLinker  |  selected  ->  " + LinkMath.dimLabel(sel.destDim)
                    + " target  X " + sel.destX + "  Z " + sel.destZ + "  (axis " + sel.axis + ")";
            } else {
                text = "EasyPortalLinker  |  selected portal has no counterpart dimension";
            }
        } else {
            return; // selection belongs to some other dimension pair
        }

        int w = client.getWindow().getGuiScaledWidth();
        int tw = client.font.width(text);
        int x = (w - tw) / 2;
        drawContext.text(client.font, Component.literal(text), x, 4, COLOR);
    }
}
