package net.containerutil.search;

import net.containerutil.ContainerUtil;
import net.containerutil.config.ConfigManager;
import net.containerutil.config.ContainerUtilConfig;
import net.containerutil.data.ContainerRecord;
import net.containerutil.data.IndexManager;
import net.containerutil.data.ItemEntry;
import net.containerutil.data.WorldIdentity;
import net.containerutil.render.ContainerEspRenderer;
import net.containerutil.render.TrackedContainer;
import net.containerutil.render.ViewAnchor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The search UI: type a query, get every container holding a match, sorted nearest first.
 *
 * <p>Hovering a row opens a side panel listing the five nearest containers holding that same
 * item — the common case being "I know I have iron somewhere, which chest do I actually walk
 * to". Clicking a row starts tracking that container and closes the screen so the beam and HUD
 * arrow take over.
 */
public class SearchScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int HEADER_HEIGHT = 58;
    private static final int FOOTER_HEIGHT = 16;

    private static final int COLOR_BG = 0xE80B0B10;
    private static final int COLOR_PANEL = 0xF0141420;
    private static final int COLOR_BORDER = 0xFF2C2C3C;
    private static final int COLOR_ROW_ALT = 0x14FFFFFF;
    private static final int COLOR_ROW_HOVER = 0x28FFFFFF;
    private static final int COLOR_TEXT = 0xFFE8E8F0;
    private static final int COLOR_DIM = 0xFF8A90A0;
    private static final int COLOR_ACCENT = 0xFF00E676;
    private static final int COLOR_STALE = 0xFFFFB74D;
    private static final int COLOR_WARN = 0xFFFF7043;

    /** Remembered across openings so re-opening the screen resumes where you left off. */
    private static String lastQuery = "";

    private EditBox queryField;
    private List<SearchResult> results = new ArrayList<>();
    private int scroll = 0;
    private List<String> warnings = new ArrayList<>();

    /** Item id → cached stack, so the row renderer is not rebuilding stacks every frame. */
    private final Map<String, ItemStack> stackCache = new HashMap<>();

    private double playerX;
    private double playerY;
    private double playerZ;

    public SearchScreen() {
        super(Component.literal("ContainerUtil Search"));
    }

    @Override
    protected void init() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            net.minecraft.world.phys.Vec3 anchor = ViewAnchor.origin(client);
            playerX = anchor.x;
            playerY = anchor.y;
            playerZ = anchor.z;
        }

        int fieldWidth = Math.min(420, width - 40);
        queryField = new EditBox(font, (width - fieldWidth) / 2, 22, fieldWidth, 18,
            Component.literal("Search"));
        queryField.setMaxLength(256);
        queryField.setHint(Component.literal("item name, #tag, dim:nether, in:barrel, count>64, -exclude"));
        queryField.setValue(lastQuery);
        queryField.setResponder(text -> {
            lastQuery = text;
            runSearch();
        });
        addRenderableWidget(queryField);
        setInitialFocus(queryField);

        runSearch();
    }

    // ── Search ───────────────────────────────────────────────────────────────

    private void runSearch() {
        scroll = 0;
        if (!IndexManager.isActive()) {
            results = new ArrayList<>();
            warnings = List.of("No world index is active.");
            SearchHighlight.clear();
            return;
        }

        SearchQuery query = SearchQuery.parse(lastQuery);
        warnings = query.warnings();

        if (query.isEmpty()) {
            results = new ArrayList<>();
            SearchHighlight.clear();
            return;
        }

        List<SearchResult> found = query.run(IndexManager.index(), playerX, playerY, playerZ);
        int limit = ConfigManager.get().searchResultLimit;
        if (found.size() > limit) found = new ArrayList<>(found.subList(0, limit));

        results = found;
        if (ConfigManager.get().highlightSearchResults) {
            SearchHighlight.set(found, lastQuery);
        }
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Keep distances honest if the world moves you while the screen is open (a boat, a
        // minecart, a piston). The result *order* stays as it was when the search ran, so rows
        // do not shuffle under the cursor mid-click.
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            net.minecraft.world.phys.Vec3 anchor = ViewAnchor.origin(client);
            playerX = anchor.x;
            playerY = anchor.y;
            playerZ = anchor.z;
        }

        context.fill(0, 0, width, height, COLOR_BG);

        super.extractRenderState(context, mouseX, mouseY, delta);

        drawHeader(context);

        int listTop = HEADER_HEIGHT;
        int listBottom = height - FOOTER_HEIGHT;
        int hovered = rowAt(mouseX, mouseY, listTop, listBottom);

        drawRows(context, listTop, listBottom, hovered, mouseX, mouseY);
        drawFooter(context);

        if (hovered >= 0 && hovered < results.size()) {
            drawNearestPanel(context, results.get(hovered), mouseX, mouseY);
        }
    }

    private void drawHeader(GuiGraphicsExtractor context) {
        String title = "ContainerUtil";
        context.text(font, title, 10, 8, COLOR_TEXT, false);

        String indexInfo = IndexManager.isActive()
            ? IndexManager.index().size() + " containers indexed  ·  " + IndexManager.activeWorldKey()
            : "no world";
        int infoWidth = font.width(indexInfo);
        context.text(font, indexInfo, width - infoWidth - 10, 8, COLOR_DIM, false);

        // Summary line under the field: how many containers, and the grand total of the
        // dominant matched item across all of them.
        String summary;
        if (lastQuery.isBlank()) {
            summary = "Type to search. Enter tracks the first result.";
        } else if (results.isEmpty()) {
            summary = "No matches.";
        } else {
            summary = results.size() + (results.size() == 1 ? " container" : " containers");
            String dominantId = dominantItemId();
            if (dominantId != null) {
                int total = 0;
                for (SearchResult result : results) {
                    total += result.container().totalOf(dominantId);
                }
                if (total > 0) {
                    summary += "   ·   " + formatCount(total) + " × " + itemDisplayName(dominantId) + " total";
                }
            }
        }
        context.text(font, summary, 10, 46, COLOR_DIM, false);

        if (!warnings.isEmpty()) {
            String warning = String.join("  ", warnings);
            int warnWidth = font.width(warning);
            context.text(font, warning, width - warnWidth - 10, 46, COLOR_WARN, false);
        }

        context.fill(0, HEADER_HEIGHT - 2, width, HEADER_HEIGHT - 1, COLOR_BORDER);
    }

    private void drawRows(GuiGraphicsExtractor context, int listTop, int listBottom, int hovered, int mouseX, int mouseY) {
        if (results.isEmpty()) return;

        ContainerUtilConfig config = ConfigManager.get();
        String currentDim = WorldIdentity.currentDimension();

        int visibleRows = (listBottom - listTop) / ROW_HEIGHT;
        int first = scroll;
        int last = Math.min(results.size(), first + visibleRows);

        context.enableScissor(0, listTop, width, listBottom);

        for (int i = first; i < last; i++) {
            SearchResult result = results.get(i);
            ContainerRecord record = result.container();
            int y = listTop + (i - first) * ROW_HEIGHT;

            if (i == hovered) {
                context.fill(0, y, width, y + ROW_HEIGHT, COLOR_ROW_HOVER);
            } else if ((i & 1) == 1) {
                context.fill(0, y, width, y + ROW_HEIGHT, COLOR_ROW_ALT);
            }

            ItemEntry primary = result.primary();
            int textX = 10;

            if (primary != null && primary.id != null) {
                ItemStack stack = stackFor(primary.id);
                if (stack != null) {
                    context.item(stack, textX, y + 3);
                }
                textX += 22;
            }

            // Left: what matched. Right: where it is.
            String left = primary != null
                ? formatCount(result.matchedTotal()) + " × " + primary.name
                : record.displayName();
            if (primary != null && primary.nestedIn != null) {
                left += "  (in " + primary.nestedIn + ")";
            }
            context.text(font, left, textX, y + 3, COLOR_TEXT, false);

            StringBuilder sub = new StringBuilder(record.displayName());
            if (record.slotCount > 0) {
                sub.append("  ").append(record.usedSlots).append('/').append(record.slotCount);
            }
            if (record.isStale(config.staleAfterDays)) {
                sub.append("  · ").append(ContainerEspRenderer.formatAge(
                    System.currentTimeMillis() - record.lastScanned)).append(" old");
            }
            context.text(font, sub.toString(), textX, y + 12,
                record.isStale(config.staleAfterDays) ? COLOR_STALE : COLOR_DIM, false);

            String where = record.coordsString();
            if (currentDim != null && !currentDim.equals(record.dim)) {
                where += "  [" + record.shortDim() + "]";
            } else {
                double live = Math.sqrt(record.distanceSqTo(playerX, playerY, playerZ));
                where = (int) Math.round(live) + "m   " + where;
            }
            int whereWidth = font.width(where);
            context.text(font, where, width - whereWidth - 10, y + 7,
                i == hovered ? COLOR_ACCENT : COLOR_DIM, false);
        }

        context.disableScissor();

        drawScrollbar(context, listTop, listBottom, visibleRows);
    }

    private void drawScrollbar(GuiGraphicsExtractor context, int listTop, int listBottom, int visibleRows) {
        if (results.size() <= visibleRows) return;

        int trackHeight = listBottom - listTop;
        int thumbHeight = Math.max(16, trackHeight * visibleRows / results.size());
        int maxScroll = results.size() - visibleRows;
        int thumbY = listTop + (trackHeight - thumbHeight) * scroll / Math.max(1, maxScroll);

        context.fill(width - 4, listTop, width - 2, listBottom, 0x30FFFFFF);
        context.fill(width - 4, thumbY, width - 2, thumbY + thumbHeight, 0x90FFFFFF);
    }

    private void drawFooter(GuiGraphicsExtractor context) {
        int y = height - FOOTER_HEIGHT + 3;
        context.fill(0, height - FOOTER_HEIGHT, width, height - FOOTER_HEIGHT + 1, COLOR_BORDER);

        String left = "[Click] track   [Shift+Click] track & stay   [Enter] track first   [Esc] close";
        context.text(font, left, 10, y, COLOR_DIM, false);

        if (TrackedContainer.isTracking()) {
            String right = "tracking: " + TrackedContainer.get().displayName()
                + " (" + TrackedContainer.get().coordsString() + ")   [Del] clear";
            int rightWidth = font.width(right);
            context.text(font, right, width - rightWidth - 10, y, COLOR_ACCENT, false);
        }
    }

    /**
     * The hover panel: the five nearest containers holding the hovered item.
     *
     * <p>Uses the index's inverted item map rather than re-filtering the search results, so it
     * answers "where else is this" across the whole world even when the active query narrowed
     * things down to one dimension or container kind.
     */
    private void drawNearestPanel(GuiGraphicsExtractor context, SearchResult result, int mouseX, int mouseY) {
        ItemEntry primary = result.primary();
        if (primary == null || primary.id == null) return;

        List<ContainerRecord> nearest = IndexManager.index()
            .nearestWithItem(primary.id, playerX, playerY, playerZ, null, 5);
        if (nearest.isEmpty()) return;

        String header = "Nearest with " + primary.name;
        String totalLine = formatCount(IndexManager.index().grandTotalOf(primary.id))
            + " × across " + IndexManager.index().withItem(primary.id).size() + " containers";

        List<String> lines = new ArrayList<>();
        String currentDim = WorldIdentity.currentDimension();
        int rank = 1;
        for (ContainerRecord record : nearest) {
            String distance = currentDim != null && currentDim.equals(record.dim)
                ? (int) Math.round(Math.sqrt(record.distanceSqTo(playerX, playerY, playerZ))) + "m"
                : record.shortDim();
            lines.add(rank++ + ".  " + distance + "   " + record.coordsString()
                + "   " + record.totalOf(primary.id) + "×   " + record.displayName());
        }

        int panelWidth = Math.max(font.width(header), font.width(totalLine));
        for (String line : lines) panelWidth = Math.max(panelWidth, font.width(line));
        panelWidth += 14;

        int lineHeight = font.lineHeight + 2;
        int panelHeight = 12 + lineHeight * (lines.size() + 2);

        // Flip to the other side of the cursor rather than running off the edge.
        int x = mouseX + 14;
        if (x + panelWidth > width) x = Math.max(4, mouseX - panelWidth - 8);
        int y = Math.min(mouseY + 8, Math.max(4, height - panelHeight - 4));

        context.fill(x, y, x + panelWidth, y + panelHeight, COLOR_PANEL);
        context.fill(x, y, x + panelWidth, y + 1, COLOR_BORDER);
        context.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, COLOR_BORDER);
        context.fill(x, y, x + 1, y + panelHeight, COLOR_BORDER);
        context.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, COLOR_BORDER);

        int textY = y + 6;
        context.text(font, header, x + 7, textY, COLOR_TEXT, false);
        textY += lineHeight;
        context.text(font, totalLine, x + 7, textY, COLOR_ACCENT, false);
        textY += lineHeight;
        for (String line : lines) {
            context.text(font, line, x + 7, textY, COLOR_DIM, false);
            textY += lineHeight;
        }
    }

    // ── Input ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        int listTop = HEADER_HEIGHT;
        int listBottom = height - FOOTER_HEIGHT;
        int row = rowAt((int) click.x(), (int) click.y(), listTop, listBottom);

        if (row >= 0 && row < results.size()) {
            boolean stay = (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            track(results.get(row), !stay);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int visibleRows = (height - FOOTER_HEIGHT - HEADER_HEIGHT) / ROW_HEIGHT;
        int maxScroll = Math.max(0, results.size() - visibleRows);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(verticalAmount) * 3));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int key = input.key();

        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (!results.isEmpty()) {
                track(results.get(0), true);
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            TrackedContainer.clear();
            return true;
        }
        // Let the search key close the screen too, so the same key toggles it.
        if (ContainerUtil.OPEN_SEARCH != null && ContainerUtil.OPEN_SEARCH.matches(input)) {
            onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    private void track(SearchResult result, boolean closeScreen) {
        ItemEntry primary = result.primary();
        TrackedContainer.set(result.container(), primary != null ? primary.name : null);
        if (closeScreen) onClose();
    }

    private int rowAt(int mouseX, int mouseY, int listTop, int listBottom) {
        if (mouseY < listTop || mouseY >= listBottom) return -1;
        if (mouseX < 0 || mouseX > width) return -1;
        int index = scroll + (mouseY - listTop) / ROW_HEIGHT;
        return index < results.size() ? index : -1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** The item id appearing across the most result rows — what the totals line should count. */
    private String dominantItemId() {
        Map<String, Integer> counts = new HashMap<>();
        for (SearchResult result : results) {
            ItemEntry primary = result.primary();
            if (primary != null && primary.id != null) {
                counts.merge(primary.id, 1, Integer::sum);
            }
        }
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    private String itemDisplayName(String itemId) {
        for (SearchResult result : results) {
            ItemEntry primary = result.primary();
            if (primary != null && itemId.equals(primary.id)) return primary.name;
        }
        return itemId;
    }

    private ItemStack stackFor(String itemId) {
        return stackCache.computeIfAbsent(itemId, id -> {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null) return ItemStack.EMPTY;
            Item item = BuiltInRegistries.ITEM.getValue(identifier);
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    /** Thousands separators, because "1204" and "12040" are hard to tell apart at a glance. */
    private static String formatCount(int count) {
        return String.format("%,d", count);
    }
}
