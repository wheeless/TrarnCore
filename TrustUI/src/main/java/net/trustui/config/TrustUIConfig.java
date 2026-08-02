package net.trustui.config;

import net.trarncore.config.ValidatedConfig;
import net.trustui.trust.TrustLevel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User settings for TrustUI.
 *
 * <p>Command names are configurable rather than hardcoded because this mod drives a server-side
 * plugin it cannot see. Servers alias GriefPrevention's commands, run forks, or wrap them in a
 * permissions plugin, and a hardcoded {@code /trust} would simply fail with no way to fix it short
 * of a rebuild.
 */
public class TrustUIConfig implements ValidatedConfig {

    /** Command that lists the current claim's permissions, without the leading slash. */
    public String trustListCommand = "trustlist";

    /** Command that removes all of a player's trust, without the leading slash. */
    public String untrustCommand = "untrust";

    /**
     * Tier → the command that grants it, without the leading slash. Missing entries fall back to
     * {@link TrustLevel#defaultCommand()}.
     */
    public Map<String, String> grantCommands = new LinkedHashMap<>();

    /**
     * The first line GriefPrevention prints before the tier lines. Used only to hide it from chat;
     * parsing keys off the {@code >} prefix, so getting this wrong costs you a stray chat line
     * rather than a broken listing.
     */
    public String trustListHeader = "Explicit permissions here:";

    /** Hide the raw listing from chat while the menu reads it. */
    public boolean hideTrustListOutput = true;

    /** How long to gather the reply, in ticks. 20 = one second. */
    public int trustListTimeoutTicks = 30;

    /** Re-read the claim after granting or revoking, so the menu reflects what the server did. */
    public boolean refreshAfterChange = true;

    /** Show players who hold trust but are offline, so their access can still be removed. */
    public boolean showOfflineTrusted = true;

    public String commandFor(TrustLevel level) {
        String configured = grantCommands.get(level.name());
        return configured == null || configured.isBlank() ? level.defaultCommand() : configured;
    }

    @Override
    public void validate() {
        if (grantCommands == null) grantCommands = new LinkedHashMap<>();
        for (TrustLevel level : TrustLevel.values()) {
            grantCommands.putIfAbsent(level.name(), level.defaultCommand());
        }
        if (trustListCommand == null || trustListCommand.isBlank()) trustListCommand = "trustlist";
        if (untrustCommand == null || untrustCommand.isBlank()) untrustCommand = "untrust";
        if (trustListHeader == null) trustListHeader = "";
        // Below ~10 ticks the reply routinely arrives after the window shuts; above a few seconds
        // the menu just feels broken.
        trustListTimeoutTicks = Math.max(10, Math.min(100, trustListTimeoutTicks));
    }
}
