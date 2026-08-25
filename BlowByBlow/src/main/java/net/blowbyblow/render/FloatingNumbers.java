package net.blowbyblow.render;

import net.blowbyblow.BlowByBlow;
import net.blowbyblow.config.BlowByBlowConfig;
import net.blowbyblow.config.ConfigManager;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.trarncore.render.WorldText;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Damage numbers that pop off whatever was hit and drift upward.
 *
 * <p>Spawned with a small horizontal jitter, because several hits on the same mob in the same
 * second would otherwise stack into one illegible smear.
 */
public class FloatingNumbers {

    private record Popup(Vec3 origin, Component text, int argb, long spawnedAt) {
    }

    private static final Deque<Popup> POPUPS = new ArrayDeque<>();
    private static long lastError = 0;

    public static void register() {
        LevelRenderEvents.END_MAIN.register(FloatingNumbers::render);
    }

    public static void clear() {
        POPUPS.clear();
    }

    public static void spawn(Vec3 at, Component text, int rgb) {
        BlowByBlowConfig config = ConfigManager.get();
        if (!config.floatingNumbers) return;

        double jitter = 0.35;
        Vec3 origin = at.add(
            (Math.random() - 0.5) * jitter, 0, (Math.random() - 0.5) * jitter);

        POPUPS.addLast(new Popup(origin, text, 0xFF000000 | rgb, System.currentTimeMillis()));
        while (POPUPS.size() > config.maxFloatingNumbers) POPUPS.removeFirst();
    }

    private static void render(LevelRenderContext context) {
        try {
            renderInternal(context);
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            if (now - lastError > 5000) {
                lastError = now;
                BlowByBlow.LOGGER.error("[BlowByBlow] Floating number render crashed (suppressing repeats for 5s)", e);
            }
        }
    }

    private static void renderInternal(LevelRenderContext context) {
        if (POPUPS.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        MultiBufferSource consumers = context.bufferSource();
        if (consumers == null) return;

        BlowByBlowConfig config = ConfigManager.get();
        long now = System.currentTimeMillis();

        POPUPS.removeIf(popup -> now - popup.spawnedAt() > config.floatingLifetimeMillis);
        if (POPUPS.isEmpty()) return;

        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        PoseStack matrices = context.poseStack();

        boolean drewAny = false;
        for (Popup popup : POPUPS) {
            float age = (now - popup.spawnedAt()) / (float) config.floatingLifetimeMillis;
            if (age >= 1f) continue;

            // Ease the rise out so the number leaps away from the hit and settles, rather than
            // sliding at a constant speed which reads as floaty.
            double rise = config.floatingRise * (1.0 - Math.pow(1.0 - age, 3));
            int alpha = Math.clamp(Math.round((1f - age * age) * 255f), 0, 255);
            if (alpha <= 0) continue;

            Vec3 at = popup.origin().add(0, rise, 0);
            WorldText.draw(matrices, consumers, client.font, camera, popup.text(),
                at.x, at.y, at.z, cam.x, cam.y, cam.z,
                (alpha << 24) | (popup.argb() & 0x00FFFFFF),
                config.floatingScale, config.floatingSeeThrough);
            drewAny = true;
        }

        // Text must be flushed here, while the transform that billboarded it is still in effect.
        // Font picks its own layers internally so they cannot be ended by name — hence a blanket
        // flush, and only when something was actually drawn. See ../CLAUDE.md.
        if (drewAny && consumers instanceof MultiBufferSource.BufferSource imm) {
            imm.endBatch();
        }
    }
}
