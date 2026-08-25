package net.blowbyblow.combat;

import java.util.Locale;
import java.util.Map;

/**
 * Readable names for Minecraft's damage ids.
 *
 * <p>{@code DamageSource.getMsgId()} returns things like {@code inFire}, {@code onFire} and
 * {@code fallingBlock} — fine as keys, poor in a sentence. Anything unmapped is prettified rather
 * than dropped, so a modded or newly added damage type still reads sensibly instead of vanishing.
 */
public final class DamageLabels {

    private DamageLabels() {
    }

    private static final Map<String, String> NAMES = Map.ofEntries(
        Map.entry("inFire", "fire"),
        Map.entry("onFire", "burning"),
        Map.entry("lava", "lava"),
        Map.entry("hotFloor", "magma"),
        Map.entry("inWall", "suffocation"),
        Map.entry("cramming", "cramming"),
        Map.entry("drown", "drowning"),
        Map.entry("starve", "starvation"),
        Map.entry("cactus", "a cactus"),
        Map.entry("fall", "the fall"),
        Map.entry("flyIntoWall", "kinetic energy"),
        Map.entry("outOfWorld", "the void"),
        Map.entry("generic", "something"),
        Map.entry("magic", "magic"),
        Map.entry("wither", "wither"),
        Map.entry("dragonBreath", "dragon breath"),
        Map.entry("dryout", "drying out"),
        Map.entry("sweetBerryBush", "a berry bush"),
        Map.entry("freeze", "freezing"),
        Map.entry("stalagmite", "a stalagmite"),
        Map.entry("fallingBlock", "a falling block"),
        Map.entry("fallingStalactite", "a stalactite"),
        Map.entry("anvil", "an anvil"),
        Map.entry("explosion", "an explosion"),
        Map.entry("explosion.player", "an explosion"),
        Map.entry("fireworks", "fireworks"),
        Map.entry("lightningBolt", "lightning"),
        Map.entry("sonic_boom", "a sonic boom"),
        Map.entry("thrown", "a thrown item"),
        Map.entry("indirectMagic", "magic"),
        Map.entry("thorns", "thorns"),
        Map.entry("badRespawnPoint", "an intentional game design"),
        Map.entry("outsideBorder", "the world border")
    );

    /** A phrase that reads after "from": {@code "You took 3 from the fall"}. */
    public static String describe(String msgId) {
        if (msgId == null || msgId.isBlank()) return "something";

        String mapped = NAMES.get(msgId);
        if (mapped != null) return mapped;

        // camelCase and dotted ids become spaced words: "fallingStalactite" -> "falling stalactite".
        String spaced = msgId.replace('.', ' ').replaceAll("(?<=[a-z])(?=[A-Z])", " ");
        return spaced.toLowerCase(Locale.ROOT);
    }
}
