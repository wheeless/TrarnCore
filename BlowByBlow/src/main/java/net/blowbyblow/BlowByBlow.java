package net.blowbyblow;

import net.blowbyblow.combat.CombatEvent;
import net.blowbyblow.combat.CombatTracker;
import net.blowbyblow.combat.EventFormatter;
import net.blowbyblow.config.BlowByBlowConfig;
import net.blowbyblow.config.ConfigManager;
import net.blowbyblow.render.FeedRenderer;
import net.blowbyblow.render.FloatingNumbers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.trarncore.chat.ChatChannel;
import net.trarncore.hud.HudPlacementScreen;
import net.trarncore.input.Keys;
import net.trarncore.update.UpdateChecker;
import net.trarncore.util.Guarded;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class BlowByBlow implements ClientModInitializer {

    public static final String MOD_ID = "blowbyblow";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Yellow — one colour per sibling mod so prefixes stay distinguishable in a shared chat log. */
    public static final ChatChannel CHAT = ChatChannel.of("BlowByBlow", ChatFormatting.YELLOW);

    public static KeyMapping TOGGLE_FEED;
    public static KeyMapping MOVE_PANEL;

    /** In-memory mirror of {@code config.enabled}; the tick and render loops read it constantly. */
    public static volatile boolean enabled = true;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        enabled = ConfigManager.get().enabled;

        // Unbound by default. Every sensible single letter near the movement keys is taken by
        // something on a normal setup, and a combat log is not a thing you toggle mid-fight.
        TOGGLE_FEED = Keys.register(MOD_ID, "key.blowbyblow.toggle", Keys.UNBOUND);
        MOVE_PANEL = Keys.register(MOD_ID, "key.blowbyblow.move", Keys.UNBOUND);

        FeedRenderer.register();
        FloatingNumbers.register();

        ClientTickEvents.END_CLIENT_TICK.register(client ->
            Guarded.run(LOGGER, "BlowByBlow tick", () -> tick(client)));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            CombatTracker.reset();
            FeedRenderer.clear();
            FloatingNumbers.clear();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CombatTracker.reset();
            FloatingNumbers.clear();
        });

        // Notify-only; see net.trarncore.update.UpdateChecker.
        UpdateChecker.watch(MOD_ID, CHAT);

        LOGGER.info("BlowByBlow initialized");
    }

    private static void tick(Minecraft client) {
        handleKeybinds(client);
        if (!enabled) return;
        CombatTracker.tick(client, event -> publish(client, event));
    }

    private static void handleKeybinds(Minecraft client) {
        Keys.whenPressed(TOGGLE_FEED, () -> {
            enabled = !enabled;
            ConfigManager.get().enabled = enabled;
            ConfigManager.save();
            if (!enabled) {
                FeedRenderer.clear();
                FloatingNumbers.clear();
            }
            CHAT.send("Combat feed " + (enabled ? "enabled" : "disabled"));
        });

        Keys.whenPressed(MOVE_PANEL, () -> openPlacementScreen(client, null));
    }

    /** Opens the drag-to-place screen for the feed panel. */
    public static void openPlacementScreen(Minecraft client, net.minecraft.client.gui.screens.Screen parent) {
        client.setScreen(new HudPlacementScreen(parent,
            List.of(FeedRenderer.placementElement()),
            ConfigManager::save));
    }

    private static void publish(Minecraft client, CombatEvent event) {
        BlowByBlowConfig config = ConfigManager.get();
        Component line = EventFormatter.format(event, config);

        if (config.showPanel) FeedRenderer.add(line);
        if (config.showInChat) CHAT.send(line);

        if (config.floatingNumbers) spawnFloating(client, event, config);
    }

    private static void spawnFloating(Minecraft client, CombatEvent event, BlowByBlowConfig config) {
        // Only your own hits and what lands on you. Bystander numbers popping off every mob in a
        // mob farm is the fastest way to make this unusable.
        boolean outgoing = event.direction() == CombatEvent.Direction.OUTGOING;
        boolean incoming = event.direction() == CombatEvent.Direction.INCOMING;
        if (!outgoing && !incoming) return;

        Entity target = outgoing ? findVictim(client, event) : client.player;
        if (target == null) return;

        Vec3 at = target.position().add(0, target.getBbHeight() * 0.85, 0);
        float value = config.showInHearts ? event.amount() / 2f : event.amount();
        String rendered = (value == Math.rint(value))
            ? String.valueOf((int) value)
            : String.format("%.1f", value);
        if (!event.precise() && config.markInferredAmounts) rendered = "~" + rendered;

        FloatingNumbers.spawn(at, Component.literal(rendered),
            outgoing ? config.floatingOutgoingColor : config.floatingIncomingColor);
    }

    /**
     * The entity an outgoing event landed on.
     *
     * <p>Matched by display name because the event carries names rather than references — the
     * tracker deliberately holds no entity handles, so a mob that dies between the hit and the
     * next frame cannot keep itself alive through this mod.
     */
    private static Entity findVictim(Minecraft client, CombatEvent event) {
        if (client.level == null || client.player == null) return null;

        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity)) continue;
            if (!entity.getDisplayName().getString().equals(event.victim().getString())) continue;

            double distance = entity.distanceToSqr(client.player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }
}
