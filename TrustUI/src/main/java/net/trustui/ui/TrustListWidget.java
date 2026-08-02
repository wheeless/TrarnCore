package net.trustui.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ElementListWidget;

/**
 * The scrolling list, built on vanilla's {@link ElementListWidget}.
 *
 * <p>Using vanilla's widget rather than drawing rows by hand is what makes this look native: the
 * panel background, borders, scrollbar, row highlighting, keyboard navigation and narration all
 * come from it, and they track any restyling Mojang does to the rest of the game's menus.
 *
 * <p>Row height is fixed, which is a real constraint — an entry cannot grow when clicked. The
 * expand behaviour is instead a second entry inserted below the player's row; see
 * {@link TrustScreen}.
 */
public class TrustListWidget extends ElementListWidget<TrustListEntry> {

    public TrustListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
        super(client, width, height, y, itemHeight);
    }

    /** Wider than the default so five action buttons fit beside a head and a name. */
    @Override
    public int getRowWidth() {
        return 300;
    }

    public void clear() {
        clearEntries();
    }

    public void add(TrustListEntry entry) {
        addEntry(entry);
    }
}
