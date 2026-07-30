package net.containerutil.container;

/**
 * Coarse grouping used to lay out the colour pickers in the config screen and to
 * give related kinds neighbouring default hues. Purely cosmetic — every
 * {@link ContainerKind} still carries its own independently overridable colour.
 */
public enum ContainerFamily {

    STORAGE("Storage"),
    REDSTONE("Redstone I/O"),
    SMELTING("Smelting"),
    UTILITY("Utility"),
    MOBILE("Mobile");

    private final String displayName;

    ContainerFamily(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
