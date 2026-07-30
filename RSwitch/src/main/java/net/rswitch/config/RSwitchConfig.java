package net.rswitch.config;

import net.trarncore.config.ValidatedConfig;

/**
 * User settings for RSwitch. Persisted as JSON via {@link ConfigManager} and edited in-game
 * through the ModMenu / Cloth Config screen.
 */
public class RSwitchConfig implements ValidatedConfig {

    /** Master on/off. When false the hotkey does nothing. */
    public boolean enabled = true;

    /**
     * How many rows above the hotbar to swap with. {@code 1} is the row drawn directly above it,
     * which is the whole point of the mod; 2 and 3 walk further up the inventory for anyone who
     * wants a different stash row.
     */
    public int rowsUp = 1;

    /** Play a soft click on a successful swap. */
    public boolean playSound = false;

    /**
     * Name the item you just swapped to in local chat.
     *
     * <p>Off by default, and worth leaving off unless you want it: this fires on every press, and
     * chat keeps history rather than replacing itself the way the action bar did.
     */
    public boolean showChatMessage = false;

    @Override
    public void validate() {
        rowsUp = Math.max(1, Math.min(3, rowsUp));
    }
}
