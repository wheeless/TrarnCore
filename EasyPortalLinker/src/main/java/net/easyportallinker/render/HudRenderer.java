package net.easyportallinker.render;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
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
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
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

    private static void renderHud(DrawContext drawContext) {
        if (!EasyPortalLinker.enabled) return;
        PortalTarget sel = EasyPortalLinker.selection;
        if (sel == null || sel.sourceDim == null) return;

        EasyPortalLinkerConfig cfg = ConfigManager.get();
        if (!cfg.showHudCoords) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        String dim = client.world.getRegistryKey().getValue().toString();

        int height = Math.max(3, sel.height);
        int idealY = LinkMath.recommendedY(sel.destDim, sel.sourceY, height);
        String text;
        if (sel.isDestDim(dim)) {
            String yPart;
            if (cfg.lockTargetY) {
                int lo = client.world.getBottomY() + 2;
                int hi = client.world.getTopYInclusive() - height;
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

        int w = client.getWindow().getScaledWidth();
        int tw = client.textRenderer.getWidth(text);
        int x = (w - tw) / 2;
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(text), x, 4, COLOR);
    }
}
