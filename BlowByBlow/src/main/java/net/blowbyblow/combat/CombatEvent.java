package net.blowbyblow.combat;

import net.minecraft.network.chat.Component;

/**
 * One thing that happened in a fight.
 *
 * @param direction who did what to whom, from your point of view
 * @param amount    health points, in half-hearts as the game counts them
 * @param victim    display name of whatever took the damage
 * @param attacker  display name of whatever dealt it, or null for environmental damage
 * @param weapon    the attacker's held item at the time, or null when empty-handed or unknown
 * @param damageType Minecraft's own id for the damage, e.g. {@code fall}, {@code mob}, {@code lava}
 * @param fatal     the victim's health reached zero
 * @param precise   the amount is authoritative rather than inferred; see {@link CombatTracker}
 */
public record CombatEvent(Direction direction, float amount, Component victim, Component attacker,
                          Component weapon, String damageType, boolean fatal, boolean precise) {

    public enum Direction {
        /** Something happened to you. */
        INCOMING,
        /** You did something to something else. */
        OUTGOING,
        /** Two other parties, near enough for you to watch. */
        BYSTANDER,
        /** You regained health. */
        HEAL
    }

    public boolean isEnvironmental() {
        return attacker == null;
    }
}
