package net.trustui.ui;

import net.minecraft.entity.player.SkinTextures;
import net.trustui.trust.TrustLevel;

import java.util.EnumSet;

/**
 * One row in the menu: a player, their skin, and whatever trust they hold here.
 *
 * <p>Rows come from two places that only partly overlap — the server's online player list, and
 * the names parsed out of {@code /trustlist}. Someone trusted but offline still needs a row, or
 * there would be no way to revoke their access without typing the command by hand.
 */
public final class PlayerEntry {

    private final String name;
    private final SkinTextures skin;
    private final boolean online;
    private EnumSet<TrustLevel> trust;

    public PlayerEntry(String name, SkinTextures skin, boolean online, EnumSet<TrustLevel> trust) {
        this.name = name;
        this.skin = skin;
        this.online = online;
        this.trust = trust == null ? EnumSet.noneOf(TrustLevel.class) : trust;
    }

    public String name() {
        return name;
    }

    public SkinTextures skin() {
        return skin;
    }

    public boolean isOnline() {
        return online;
    }

    public EnumSet<TrustLevel> trust() {
        return trust;
    }

    public void setTrust(EnumSet<TrustLevel> trust) {
        this.trust = trust == null ? EnumSet.noneOf(TrustLevel.class) : trust;
    }

    public boolean hasAnyTrust() {
        return !trust.isEmpty();
    }

    /**
     * The tier to show on the badge when a player holds several.
     *
     * <p>Highest wins, because that is what actually describes their access: someone with Manage
     * and Build is a manager, and labelling them "Build" would understate it.
     */
    public TrustLevel highestTrust() {
        TrustLevel best = null;
        for (TrustLevel level : TrustLevel.values()) {
            if (trust.contains(level) && (best == null || level.ordinal() > best.ordinal())) {
                best = level;
            }
        }
        return best;
    }
}
