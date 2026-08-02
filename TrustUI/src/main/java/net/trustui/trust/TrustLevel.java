package net.trustui.trust;

/**
 * GriefPrevention's permission tiers.
 *
 * <p>The colours match what GriefPrevention itself prints in the legend at the bottom of
 * {@code /trustlist}, so the badges in the UI read the same as the chat output people are used to.
 */
public enum TrustLevel {

    /** Doors, buttons, beds. */
    ACCESS("Access", "accesstrust", 0x5555FF),

    /** Chests and other containers, plus everything Access allows. */
    CONTAINER("Containers", "containertrust", 0x55FF55),

    /** Place and break blocks — GriefPrevention's plain {@code /trust}. */
    BUILD("Build", "trust", 0xFFFF55),

    /** Can grant trust to others. GriefPrevention calls this "Manage" in its output. */
    PERMISSION("Manage", "permissiontrust", 0xFFAA00);

    /**
     * The order the four lines appear in {@code /trustlist} output.
     *
     * <p>This is the whole basis of parsing: GriefPrevention prints one line per tier with no
     * label on it, always in this order, and documents the mapping only through the colour legend
     * on the trailing line. Position is what identifies a tier — see {@link TrustListParser}.
     */
    public static final TrustLevel[] TRUSTLIST_ORDER = {PERMISSION, BUILD, CONTAINER, ACCESS};

    private final String displayName;
    private final String command;
    private final int color;

    TrustLevel(String displayName, String command, int color) {
        this.displayName = displayName;
        this.command = command;
        this.color = color;
    }

    /** Label shown on badges and buttons — matches GriefPrevention's own wording. */
    public String displayName() {
        return displayName;
    }

    /** Default GriefPrevention command that grants this tier, without the leading slash. */
    public String defaultCommand() {
        return command;
    }

    /** 0xRRGGBB, matching GriefPrevention's legend colours. */
    public int color() {
        return color;
    }
}
