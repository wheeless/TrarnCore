package net.blowbyblow.combat;

import net.blowbyblow.config.BlowByBlowConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Turns a {@link CombatEvent} into the line you read.
 *
 * <p>Sentence shape follows the thing this mod is imitating: subject, verb, object, amount, and
 * the instrument last — "Zombie hit you for 3 with an Iron Sword". Putting the number before the
 * weapon means the part you scan for sits in the same place on every line.
 */
public final class EventFormatter {

    private EventFormatter() {
    }

    public static Component format(CombatEvent event, BlowByBlowConfig config) {
        return switch (event.direction()) {
            case INCOMING  -> incoming(event, config);
            case OUTGOING  -> outgoing(event, config);
            case BYSTANDER -> bystander(event, config);
            case HEAL      -> heal(event, config);
        };
    }

    private static MutableComponent incoming(CombatEvent event, BlowByBlowConfig config) {
        MutableComponent line = Component.empty();

        if (event.isEnvironmental()) {
            line.append(text("You took ", ChatFormatting.GRAY))
                .append(amount(event, config, ChatFormatting.RED))
                .append(text(" from " + DamageLabels.describe(event.damageType()), ChatFormatting.GRAY));
        } else {
            line.append(name(event.attacker(), config.attackerColor))
                .append(text(event.fatal() ? " killed you" : " hit you for ", ChatFormatting.GRAY));
            if (!event.fatal()) line.append(amount(event, config, ChatFormatting.RED));
            appendWeapon(line, event, config);
        }
        return line;
    }

    private static MutableComponent outgoing(CombatEvent event, BlowByBlowConfig config) {
        MutableComponent line = Component.empty()
            .append(text("You ", ChatFormatting.GRAY));

        if (event.fatal()) {
            line.append(text("killed ", ChatFormatting.GRAY))
                .append(name(event.victim(), config.victimColor));
        } else {
            line.append(text("hit ", ChatFormatting.GRAY))
                .append(name(event.victim(), config.victimColor))
                .append(text(" for ", ChatFormatting.GRAY))
                .append(amount(event, config, ChatFormatting.GREEN));
        }
        appendWeapon(line, event, config);
        return line;
    }

    private static MutableComponent bystander(CombatEvent event, BlowByBlowConfig config) {
        MutableComponent line = Component.empty();

        if (event.isEnvironmental()) {
            line.append(name(event.victim(), config.victimColor))
                .append(text(" took ", ChatFormatting.DARK_GRAY))
                .append(amount(event, config, ChatFormatting.DARK_GRAY))
                .append(text(" from " + DamageLabels.describe(event.damageType()), ChatFormatting.DARK_GRAY));
            return line;
        }

        line.append(name(event.attacker(), ChatFormatting.DARK_GRAY.getColor()))
            .append(text(event.fatal() ? " killed " : " hit ", ChatFormatting.DARK_GRAY))
            .append(name(event.victim(), ChatFormatting.DARK_GRAY.getColor()));
        if (!event.fatal()) {
            line.append(text(" for ", ChatFormatting.DARK_GRAY))
                .append(amount(event, config, ChatFormatting.DARK_GRAY));
        }
        return line;
    }

    private static MutableComponent heal(CombatEvent event, BlowByBlowConfig config) {
        return Component.empty()
            .append(text("You healed ", ChatFormatting.GRAY))
            .append(amount(event, config, ChatFormatting.LIGHT_PURPLE));
    }

    private static void appendWeapon(MutableComponent line, CombatEvent event, BlowByBlowConfig config) {
        if (!config.showWeapons || event.weapon() == null || event.fatal()) return;
        line.append(text(" with ", ChatFormatting.DARK_GRAY))
            .append(Component.empty().append(event.weapon()).withColor(config.weaponColor));
    }

    /**
     * The number, in whichever unit the player thinks in.
     *
     * <p>Minecraft counts damage in half-hearts, which is what the game's own tooltips use, but
     * plenty of people read the health bar in hearts. Both are offered rather than one being
     * declared correct.
     */
    private static MutableComponent amount(CombatEvent event, BlowByBlowConfig config, ChatFormatting fallback) {
        float value = config.showInHearts ? event.amount() / 2f : event.amount();
        String rendered = trimZero(value) + (config.showInHearts ? "♥" : "");

        // An inferred number gets a marker so a floor is never mistaken for a measurement.
        if (!event.precise() && config.markInferredAmounts) rendered = "~" + rendered;

        return text(rendered, fallback);
    }

    /** 7.0 reads as "7"; 3.5 stays "3.5". */
    private static String trimZero(float value) {
        return value == Math.rint(value)
            ? String.valueOf((int) value)
            : String.format("%.1f", value);
    }

    private static MutableComponent text(String content, ChatFormatting color) {
        return Component.literal(content).withStyle(color);
    }

    private static MutableComponent name(Component source, int rgb) {
        return Component.empty().append(source).withColor(rgb);
    }
}
