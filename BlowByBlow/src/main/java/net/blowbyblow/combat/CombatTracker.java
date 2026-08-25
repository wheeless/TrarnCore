package net.blowbyblow.combat;

import net.blowbyblow.BlowByBlow;
import net.blowbyblow.config.BlowByBlowConfig;
import net.blowbyblow.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Watches health and turns the changes into combat events.
 *
 * <p><b>How this knows anything.</b> The server sends the client a damage event for every entity
 * it can see, and Minecraft stores it on the entity as {@code lastDamageSource} — so <em>who</em>
 * hit <em>what</em>, and with which damage type, comes from the game rather than from guesswork.
 * What that packet does not carry is the number, so the amount is the drop in the entity's synced
 * health between ticks.
 *
 * <p>That split is why events carry a {@code precise} flag. Your own health is sent to you
 * exactly, so incoming damage is authoritative. Another entity's health is synced tracked data:
 * accurate when it arrives, but it can coalesce two quick hits into one observation and it cannot
 * see overkill — a mob on 2 hearts hit for 10 reads as 2. Outgoing numbers are therefore honest
 * about being a floor, not a measurement.
 *
 * <p>No mixins: a tick handler polling health does the same job as hooking the damage packet, and
 * survives Minecraft moving the packet around.
 */
public final class CombatTracker {

    private CombatTracker() {
    }

    /** Health last seen, per entity id. */
    private static final Map<Integer, Float> lastHealth = new HashMap<>();

    /** Ids seen this tick, so entities that left tracking range can be dropped. */
    private static final Set<Integer> seenThisTick = new HashSet<>();

    private static float lastPlayerHealth = Float.NaN;

    /** Below this a change is rounding noise from regeneration ticks, not a hit worth a line. */
    private static final float EPSILON = 0.05f;

    public static void reset() {
        lastHealth.clear();
        seenThisTick.clear();
        lastPlayerHealth = Float.NaN;
    }

    public static void tick(Minecraft client, Consumer<CombatEvent> sink) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            reset();
            return;
        }

        BlowByBlowConfig config = ConfigManager.get();

        trackSelf(player, config, sink);
        trackOthers(client, player, config, sink);
    }

    // ── What happened to me ──────────────────────────────────────────────────

    private static void trackSelf(LocalPlayer player, BlowByBlowConfig config, Consumer<CombatEvent> sink) {
        float health = player.getHealth();

        if (Float.isNaN(lastPlayerHealth)) {
            lastPlayerHealth = health;
            return;
        }
        float delta = lastPlayerHealth - health;
        lastPlayerHealth = health;

        if (delta > EPSILON) {
            DamageSource source = player.getLastDamageSource();
            sink.accept(new CombatEvent(
                CombatEvent.Direction.INCOMING, delta,
                player.getDisplayName(),
                attackerName(source),
                weaponOf(source),
                source == null ? "generic" : source.getMsgId(),
                health <= 0f,
                true));   // your own health is sent to you exactly
        } else if (config.showHealing && delta < -EPSILON) {
            sink.accept(new CombatEvent(
                CombatEvent.Direction.HEAL, -delta,
                player.getDisplayName(), null, null, "heal", false, true));
        }
    }

    // ── What I did, and what happened nearby ─────────────────────────────────

    private static void trackOthers(Minecraft client, LocalPlayer player,
                                    BlowByBlowConfig config, Consumer<CombatEvent> sink) {
        seenThisTick.clear();
        double rangeSq = (double) config.trackRadius * config.trackRadius;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity == player) continue;
            if (entity.distanceToSqr(player) > rangeSq) continue;

            int id = entity.getId();
            seenThisTick.add(id);

            float health = living.getHealth();
            Float previous = lastHealth.put(id, health);
            if (previous == null) continue;

            float delta = previous - health;
            if (delta <= EPSILON) continue;

            DamageSource source = living.getLastDamageSource();
            Entity attacker = source == null ? null : source.getEntity();
            boolean byMe = attacker == player;

            if (!byMe && !config.showBystanders) continue;

            sink.accept(new CombatEvent(
                byMe ? CombatEvent.Direction.OUTGOING : CombatEvent.Direction.BYSTANDER,
                delta,
                living.getDisplayName(),
                byMe ? player.getDisplayName() : attackerName(source),
                byMe ? itemName(player.getMainHandItem()) : weaponOf(source),
                source == null ? "generic" : source.getMsgId(),
                health <= 0f,
                false));  // inferred from synced health; blind to overkill
        }

        // Entities that walked out of range or died keep their last health forever otherwise, and
        // a re-used entity id would then produce a bogus delta the moment it came back.
        lastHealth.keySet().retainAll(seenThisTick);
    }

    // ── Naming ───────────────────────────────────────────────────────────────

    private static Component attackerName(DamageSource source) {
        if (source == null) return null;

        Entity direct = source.getEntity();
        if (direct != null) return direct.getDisplayName();

        // An arrow with no shooter still has a direct entity worth naming.
        Entity projectile = source.getDirectEntity();
        return projectile == null ? null : projectile.getDisplayName();
    }

    /** What the attacker was holding, or null if it was empty-handed, unknown or not a creature. */
    private static Component weaponOf(DamageSource source) {
        if (source == null) return null;

        // The direct entity is the arrow or fireball; the weapon belongs to whoever fired it.
        if (source.getEntity() instanceof LivingEntity living) {
            return itemName(living.getMainHandItem());
        }
        return null;
    }

    private static Component itemName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return stack.getHoverName();
    }

    /** Logged once on join so a confused report can be traced without turning on debug logging. */
    public static void logCapabilities() {
        BlowByBlow.LOGGER.debug("[BlowByBlow] tracking started; incoming damage is exact, "
            + "outgoing is inferred from synced health");
    }
}
