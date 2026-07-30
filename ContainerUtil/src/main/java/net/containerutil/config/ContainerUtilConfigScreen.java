package net.containerutil.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.containerutil.ContainerUtil;
import net.containerutil.container.ContainerFamily;
import net.containerutil.container.ContainerKind;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Cloth Config screen for ContainerUtil.
 * Only classloaded when cloth-config is present — ModMenuIntegration guards the load.
 */
public class ContainerUtilConfigScreen {

    public static Screen build(Screen parent) {
        ContainerUtilConfig config = ConfigManager.get();
        config.fillDefaults();

        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Text.literal("ContainerUtil Settings"))
            .setSavingRunnable(() -> {
                ConfigManager.save();
                // Keep the render loop's in-memory flag in sync with the saved value.
                ContainerUtil.enabled = config.enabled;
            });

        ConfigEntryBuilder entry = builder.entryBuilder();

        buildGeneral(builder, entry, config);
        buildColors(builder, entry, config);
        buildIndexing(builder, entry, config);
        buildSearchAndTracking(builder, entry, config);
        buildQueryHelp(builder, entry);

        return builder.build();
    }

    // ── General ──────────────────────────────────────────────────────────────

    private static void buildGeneral(ConfigBuilder builder, ConfigEntryBuilder entry, ContainerUtilConfig config) {
        ConfigCategory general = builder.getOrCreateCategory(Text.literal("General"));

        general.addEntry(entry
            .startBooleanToggle(Text.literal("Enabled"), config.enabled)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Master on/off for container highlights. Also bound to the toggle hotkey (default: Numpad 3)."))
            .setSaveConsumer(value -> config.enabled = value)
            .build());

        general.addEntry(entry
            .startIntSlider(Text.literal("Render Distance (chunks)"), config.renderChunkRadius, 1, 32)
            .setDefaultValue(8)
            .setTooltip(Text.literal("Containers within this many chunks of you are highlighted, measured "
                + "horizontally. Depth is not limited — a chunk loads as a full column, so a chest at "
                + "bedrock is highlighted just like one at your feet."))
            .setSaveConsumer(value -> config.renderChunkRadius = value)
            .build());

        general.addEntry(entry
            .startBooleanToggle(Text.literal("Anchor To Camera"), config.anchorToCamera)
            .setDefaultValue(false)
            .setTooltip(
                Text.literal("Measure distances from the camera instead of your body."),
                Text.literal("Turn this on when using a freecam: highlights, labels and the peek panel "
                    + "then follow where you are viewing from, rather than staying clustered around "
                    + "the body you left behind."),
                Text.literal("Tracking arrival still uses your real position — you have not reached a "
                    + "chest just because your camera flew to it."))
            .setSaveConsumer(value -> config.anchorToCamera = value)
            .build());

        general.addEntry(entry
            .startIntSlider(Text.literal("Vertical Limit (blocks)"), config.verticalRenderLimit, 0, 512)
            .setDefaultValue(0)
            .setTextGetter(value -> Text.literal(value == 0 ? "No limit" : value + " blocks"))
            .setTooltip(Text.literal("Optionally hide highlights more than this far above or below you. "
                + "0 means no limit, which matches the full height of a loaded chunk."))
            .setSaveConsumer(value -> config.verticalRenderLimit = value)
            .build());

        general.addEntry(entry
            .startIntField(Text.literal("Max Highlights On Screen"), config.maxRenderedContainers)
            .setDefaultValue(512)
            .setMin(16).setMax(8192)
            .setTooltip(Text.literal("Hard cap, nearest first. Lower this if a large storage hall costs you frames."))
            .setSaveConsumer(value -> config.maxRenderedContainers = value)
            .build());

        general.addEntry(entry
            .startBooleanToggle(Text.literal("Draw Filled Boxes"), config.drawFilled)
            .setDefaultValue(true)
            .setSaveConsumer(value -> config.drawFilled = value)
            .build());

        general.addEntry(entry
            .startBooleanToggle(Text.literal("Draw Outlines"), config.drawOutline)
            .setDefaultValue(true)
            .setSaveConsumer(value -> config.drawOutline = value)
            .build());

        general.addEntry(entry
            .startBooleanToggle(Text.literal("See Through Walls"), config.seeThrough)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Draw highlights through terrain. Turn off to hide them behind blocks like normal geometry."))
            .setSaveConsumer(value -> config.seeThrough = value)
            .build());

        general.addEntry(entry
            .startIntSlider(Text.literal("Outline Width"), (int) (config.outlineWidth * 10), 5, 80)
            .setDefaultValue(20)
            .setTextGetter(value -> Text.literal(String.format("%.1f", value / 10f)))
            .setTooltip(Text.literal("Thickness of the box outlines."))
            .setSaveConsumer(value -> config.outlineWidth = value / 10f)
            .build());

        general.addEntry(entry
            .startBooleanToggle(Text.literal("Fill Opacity Follows Fullness"), config.fillScalesWithFullness)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Fuller containers render more opaque, so an empty chest is visibly faint. Requires having opened the container at least once."))
            .setSaveConsumer(value -> config.fillScalesWithFullness = value)
            .build());

        general.addEntry(entry
            .startIntSlider(Text.literal("Fill Opacity — Empty (%)"), config.minFillOpacity, 0, 100)
            .setDefaultValue(6)
            .setSaveConsumer(value -> config.minFillOpacity = value)
            .build());

        general.addEntry(entry
            .startIntSlider(Text.literal("Fill Opacity — Full (%)"), config.maxFillOpacity, 0, 100)
            .setDefaultValue(45)
            .setSaveConsumer(value -> config.maxFillOpacity = value)
            .build());

        general.addEntry(entry
            .startIntSlider(Text.literal("Fill Opacity — Unopened (%)"), config.baseFillOpacity, 0, 100)
            .setDefaultValue(18)
            .setTooltip(Text.literal("Used for containers you have never looked inside, where there is no fullness to show."))
            .setSaveConsumer(value -> config.baseFillOpacity = value)
            .build());

        general.addEntry(entry
            .startBooleanToggle(Text.literal("Show Labels"), config.showLabels)
            .setDefaultValue(true)
            .setSaveConsumer(value -> config.showLabels = value)
            .build());

        general.addEntry(entry
            .startIntSlider(Text.literal("Label Distance (blocks)"), config.labelMaxDistance, 4, 256)
            .setDefaultValue(48)
            .setSaveConsumer(value -> config.labelMaxDistance = value)
            .build());

        general.addEntry(entry
            .startBooleanToggle(Text.literal("Show Slot Counts On Labels"), config.showFillCounts)
            .setDefaultValue(true)
            .setSaveConsumer(value -> config.showFillCounts = value)
            .build());

        general.addEntry(entry
            .startBooleanToggle(Text.literal("Show Contents Age On Labels"), config.showLastSeenAge)
            .setDefaultValue(false)
            .setTooltip(Text.literal("Always show how long ago contents were recorded. Stale containers show it regardless."))
            .setSaveConsumer(value -> config.showLastSeenAge = value)
            .build());
    }

    // ── Colours ──────────────────────────────────────────────────────────────

    /**
     * One colour picker and one visibility toggle per container kind, grouped by family.
     * Twenty-three kinds is far too many for a flat list, and the families map onto how you
     * actually think about them.
     */
    private static void buildColors(ConfigBuilder builder, ConfigEntryBuilder entry, ContainerUtilConfig config) {
        ConfigCategory colors = builder.getOrCreateCategory(Text.literal("Colours"));

        for (ContainerFamily family : ContainerFamily.values()) {
            SubCategoryBuilder group = entry.startSubCategory(Text.literal(family.displayName()))
                .setExpanded(family == ContainerFamily.STORAGE);

            for (ContainerKind kind : ContainerKind.values()) {
                if (kind.family() != family) continue;

                group.add(entry
                    .startColorField(Text.literal(kind.displayName()), config.colorOf(kind))
                    .setDefaultValue(kind.defaultColor())
                    .setAlphaMode(false)
                    .setTooltip(Text.literal("Highlight colour for " + kind.displayName() + "."))
                    .setSaveConsumer(value -> config.setColorOf(kind, value))
                    .build());

                group.add(entry
                    .startBooleanToggle(Text.literal("  ↳ Show " + kind.displayName()), config.isKindEnabled(kind))
                    .setDefaultValue(true)
                    .setSaveConsumer(value -> config.setKindEnabled(kind, value))
                    .build());
            }

            colors.addEntry(group.build());
        }
    }

    // ── Indexing ─────────────────────────────────────────────────────────────

    private static void buildIndexing(ConfigBuilder builder, ConfigEntryBuilder entry, ContainerUtilConfig config) {
        ConfigCategory indexing = builder.getOrCreateCategory(Text.literal("Indexing"));

        indexing.addEntry(entry
            .startBooleanToggle(Text.literal("Record Container Contents"), config.indexingEnabled)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Save what is inside a container when you open it. Turning this off leaves the existing index intact."))
            .setSaveConsumer(value -> config.indexingEnabled = value)
            .build());

        indexing.addEntry(entry
            .startIntSlider(Text.literal("Mark Stale After (days)"), config.staleAfterDays, 0, 90)
            .setDefaultValue(14)
            .setTooltip(Text.literal("Recorded contents older than this are flagged in search results and labels. 0 disables staleness entirely."))
            .setSaveConsumer(value -> config.staleAfterDays = value)
            .build());

        indexing.addEntry(entry
            .startBooleanToggle(Text.literal("Dim Never-Opened Containers"), config.dimUnopened)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Draw containers whose contents are unknown fainter than indexed ones."))
            .setSaveConsumer(value -> config.dimUnopened = value)
            .build());

        indexing.addEntry(entry
            .startBooleanToggle(Text.literal("Auto-Prune Missing Containers"), config.autoPrune)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Forget a container once you are standing near where it used to be and it is demonstrably gone."))
            .setSaveConsumer(value -> config.autoPrune = value)
            .build());

        indexing.addEntry(entry
            .startIntSlider(Text.literal("Prune Radius (blocks)"), config.pruneRadius, 4, 128)
            .setDefaultValue(24)
            .setTooltip(Text.literal("Only prune within this range, where the chunk is certainly loaded and the reading can be trusted."))
            .setSaveConsumer(value -> config.pruneRadius = value)
            .build());

        indexing.addEntry(entry
            .startBooleanToggle(Text.literal("Peek At Contents"), config.peekEnabled)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Show a container's last-known contents when you look at it, without opening it."))
            .setSaveConsumer(value -> config.peekEnabled = value)
            .build());

        indexing.addEntry(entry
            .startIntSlider(Text.literal("Peek Distance (blocks)"), config.peekDistance, 2, 64)
            .setDefaultValue(12)
            .setSaveConsumer(value -> config.peekDistance = value)
            .build());

        indexing.addEntry(entry
            .startIntSlider(Text.literal("Peek Max Lines"), config.peekMaxLines, 1, 54)
            .setDefaultValue(10)
            .setSaveConsumer(value -> config.peekMaxLines = value)
            .build());
    }

    // ── Query help ───────────────────────────────────────────────────────────

    /**
     * A read-only reference for the search query language.
     *
     * <p>Lives in the config screen because that is where people go looking when they want to
     * know what a mod can do, and a query language nobody can discover is a query language
     * nobody uses. Everything here is {@code startTextDescription}, so nothing is editable and
     * nothing is saved.
     */
    private static void buildQueryHelp(ConfigBuilder builder, ConfigEntryBuilder entry) {
        ConfigCategory help = builder.getOrCreateCategory(Text.literal("Query Help"));

        text(help, entry, "§fContainerUtil Search");
        text(help, entry, "§7Bind a key to §fOpen Container Search§7 in Options → Controls → ContainerUtil, "
            + "then type a query. Containers are indexed as you open them, so the search only knows about "
            + "containers you have actually looked inside at least once.");
        text(help, entry, "§7Every term §fnarrows§7 the results — terms combine with AND. Results are sorted "
            + "nearest-first, and hovering one shows the five closest containers holding that same item.");

        // ── Item names ───────────────────────────────────────────────────────
        SubCategoryBuilder names = entry.startSubCategory(Text.literal("1 · Finding items by name"))
            .setExpanded(true);
        text(names, entry, "§7Plain text matches an item's display name, its registry id, or its short path.");
        text(names, entry, "§a  diamond§7 — anything with \"diamond\" in the name: Diamond, Diamond Block, "
            + "Diamond Sword, Diamond Ore.");
        text(names, entry, "§a  iron ingot§7 — two separate terms. A stack must match §fboth§7, so this finds "
            + "Iron Ingot but not a plain Iron Block.");
        text(names, entry, "§a  \"iron ingot\"§7 — quoted, so it is one term matched as a whole phrase. Use this "
            + "when the word order matters.");
        text(names, entry, "§a  minecraft:redstone§7 — a full registry id works as plain text too.");
        text(names, entry, "§7Matching ignores case, and underscores count as spaces — §asoul sand§7 finds "
            + "§fminecraft:soul_sand§7.");
        help.addEntry(names.build());

        // ── Item filters ─────────────────────────────────────────────────────
        SubCategoryBuilder items = entry.startSubCategory(Text.literal("2 · Item filters"));
        text(items, entry, "§b  #tag§7 — item tag membership. The namespace is optional.");
        text(items, entry, "§a    #logs§7   §a#minecraft:planks§7   §a#wool§7   §a#coals§7   §a#swords§7   "
            + "§a#diamond_ores§7");
        text(items, entry, "§b  item:<id>§7 — exact registry id, no partial matching. Use this when a name "
            + "search is too broad.");
        text(items, entry, "§a    item:minecraft:stone§7 — Stone only, not Stone Bricks or Stone Slab.");
        text(items, entry, "§b  enchant:<name>§7 — stacks carrying a named enchantment. Enchanted books count.");
        text(items, entry, "§a    enchant:mending§7   §a enchant:silk_touch§7   §a enchant:fortune§7");
        text(items, entry, "§b  has:enchant§7 — any enchanted stack, whatever the enchantment.");
        text(items, entry, "§b  has:nested§7 — stacks that are inside a shulker box rather than loose.");
        text(items, entry, "§7Shulker boxes are indexed §fthrough§7: an item inside a shulker inside a chest is "
            + "findable, and the result tells you which shulker it is in.");
        help.addEntry(items.build());

        // ── Container filters ────────────────────────────────────────────────
        SubCategoryBuilder containers = entry.startSubCategory(Text.literal("3 · Container filters"));
        text(containers, entry, "§b  dim:<name>§7 — restrict to a dimension.");
        text(containers, entry, "§a    dim:overworld§7   §a dim:nether§7   §a dim:end§7");
        text(containers, entry, "§b  in:<kind>§7 — restrict to a container type. §ftype:§7 and §fkind:§7 also work.");
        text(containers, entry, "§a    in:chest§7   §a in:barrel§7   §a in:shulker§7   §a in:hopper§7   "
            + "§a in:furnace§7   §a in:ender§7   §a in:copper§7   §a in:shelf§7");
        text(containers, entry, "§7This is a §fpartial§7 match, which is usually what you want: §ain:chest§7 also "
            + "covers trapped, ender, copper and minecart chests, while §ain:trapped§7 or §ain:copper§7 narrows "
            + "it down. §ain:furnace§7 picks up blast furnaces too, but not smokers.");
        text(containers, entry, "§b  label:<text>§7 — a nickname you assigned, or a container renamed in an anvil. "
            + "§fname:§7 also works.");
        text(containers, entry, "§b  is:<state>§7 — container state:");
        text(containers, entry, "§a    is:empty§7 opened and holding nothing   §a is:full§7 every slot used   "
            + "§a is:partial§7 some room left");
        text(containers, entry, "§a    is:unopened§7 never looked inside   §a is:opened§7 contents known");
        text(containers, entry, "§a    is:stale§7 contents older than your staleness setting");
        text(containers, entry, "§a    is:labeled§7 has a nickname or anvil name   §a is:double§7 double chests   "
            + "§a is:mobile§7 minecarts, boats, chested animals");
        text(containers, entry, "§7Note §ais:empty§7 only matches containers you have opened. For ones you have "
            + "never checked, use §ais:unopened§7.");
        help.addEntry(containers.build());

        // ── Amounts ──────────────────────────────────────────────────────────
        SubCategoryBuilder amounts = entry.startSubCategory(Text.literal("4 · Amounts"));
        text(amounts, entry, "§b  count<op><number>§7 — compares against the §ftotal of the matched items§7 in "
            + "each container, not the container's slot count.");
        text(amounts, entry, "§a    count>64§7   §a count>=1000§7   §a count<10§7   §a count<=64§7   §a count=1§7");
        text(amounts, entry, "§7So §airon ingot count>256§7 finds chests holding more than four stacks of iron — "
            + "useful for locating your main stockpile rather than every stray ingot.");
        help.addEntry(amounts.build());

        // ── Exclusions ───────────────────────────────────────────────────────
        SubCategoryBuilder exclude = entry.startSubCategory(Text.literal("5 · Excluding things"));
        text(exclude, entry, "§b  -term§7 or §b!term§7 — negation. Works on any term type.");
        text(exclude, entry, "§7On an §fitem§7 term this rejects the §fwhole container§7, not just that line. "
            + "§airon -cobblestone§7 means \"containers holding iron and §fno§7 cobblestone\" — which is "
            + "usually the question people actually mean to ask.");
        text(exclude, entry, "§7On a §fcontainer§7 term it simply excludes those containers:");
        text(exclude, entry, "§a    -in:hopper§7 skip hoppers   §a -dim:nether§7 skip the Nether   "
            + "§a -is:stale§7 only fresh records");
        help.addEntry(exclude.build());

        // ── Examples ─────────────────────────────────────────────────────────
        SubCategoryBuilder examples = entry.startSubCategory(Text.literal("6 · Examples"))
            .setExpanded(true);

        text(examples, entry, "§f· Everyday lookups");
        text(examples, entry, "§a  redstone§7 — every container with redstone in it.");
        text(examples, entry, "§a  \"ender pearl\" count>=16§7 — a stack or more of pearls, for a trip.");
        text(examples, entry, "§a  #logs count>128§7 — where the bulk wood is, any type.");
        text(examples, entry, "§a  enchant:mending§7 — every Mending item you own, tools and books alike.");
        text(examples, entry, "§a  item:minecraft:gunpowder count>64§7 — more than a stack of gunpowder.");

        text(examples, entry, "§f· Finding space");
        text(examples, entry, "§a  in:barrel is:empty§7 — an empty barrel to dump loot into.");
        text(examples, entry, "§a  in:chest is:partial dim:overworld§7 — Overworld chests with room left.");
        text(examples, entry, "§a  is:full in:chest§7 — chests that have no room, for a sorting pass.");

        text(examples, entry, "§f· Auditing the index");
        text(examples, entry, "§a  is:unopened§7 — containers you have never looked inside. Good for a fresh base.");
        text(examples, entry, "§a  is:stale in:chest§7 — chests whose recorded contents are getting old.");
        text(examples, entry, "§a  is:labeled§7 — everything you have named, as a map of your storage.");
        text(examples, entry, "§a  has:nested§7 — containers holding packed shulkers.");

        text(examples, entry, "§f· Narrowing down");
        text(examples, entry, "§a  iron -cobblestone§7 — iron, but not in the chest that is also your cobble dump.");
        text(examples, entry, "§a  diamond -in:furnace -in:hopper§7 — diamonds in real storage, not in machinery.");
        text(examples, entry, "§a  dim:nether #logs§7 — wood stored on the Nether side.");
        text(examples, entry, "§a  potion -is:stale§7 — potions, only where the record is still trustworthy.");
        text(examples, entry, "§a  label:overflow count>0§7 — what is sitting in your overflow chests.");
        text(examples, entry, "§a  in:shulker enchant:fortune§7 — a Fortune tool packed away in a shulker.");
        text(examples, entry, "§a  in:copper is:partial§7 — copper chests with room left in them.");
        text(examples, entry, "§a  in:shelf§7 — everything on display, since shelves are read without opening.");
        text(examples, entry, "§a  \"gold ingot\" dim:overworld count>=64 -in:hopper§7 — four filters at once.");
        help.addEntry(examples.build());

        // ── Screen controls ──────────────────────────────────────────────────
        SubCategoryBuilder controls = entry.startSubCategory(Text.literal("7 · Search screen controls"));
        text(controls, entry, "§b  Click§7 — track that container and close the screen. A beam and a direction "
            + "arrow guide you to it.");
        text(controls, entry, "§b  Shift + Click§7 — track it but keep the screen open.");
        text(controls, entry, "§b  Enter§7 — track the first (nearest) result.");
        text(controls, entry, "§b  Hover§7 — show the five nearest containers holding that item, with coordinates, "
            + "in order.");
        text(controls, entry, "§b  Delete§7 — stop tracking.");
        text(controls, entry, "§b  Scroll§7 — move through results.   §bEsc§7 — close.");
        help.addEntry(controls.build());
    }

    /** A non-interactive line of help text. */
    private static void text(ConfigCategory category, ConfigEntryBuilder entry, String line) {
        category.addEntry(entry.startTextDescription(Text.literal(line)).build());
    }

    private static void text(SubCategoryBuilder group, ConfigEntryBuilder entry, String line) {
        group.add(entry.startTextDescription(Text.literal(line)).build());
    }

    // ── Search & tracking ────────────────────────────────────────────────────

    private static void buildSearchAndTracking(ConfigBuilder builder, ConfigEntryBuilder entry,
                                               ContainerUtilConfig config) {
        ConfigCategory search = builder.getOrCreateCategory(Text.literal("Search & Tracking"));

        search.addEntry(entry
            .startBooleanToggle(Text.literal("Highlight Search Matches"), config.highlightSearchResults)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Containers matching your last search are drawn in the highlight colour instead of their type colour."))
            .setSaveConsumer(value -> config.highlightSearchResults = value)
            .build());

        search.addEntry(entry
            .startColorField(Text.literal("Search Match Colour"), config.searchHighlightColor)
            .setDefaultValue(0xFFFFFF)
            .setAlphaMode(false)
            .setSaveConsumer(value -> config.searchHighlightColor = value & 0xFFFFFF)
            .build());

        search.addEntry(entry
            .startIntField(Text.literal("Max Search Results"), config.searchResultLimit)
            .setDefaultValue(200)
            .setMin(10).setMax(2000)
            .setSaveConsumer(value -> config.searchResultLimit = value)
            .build());

        search.addEntry(entry
            .startBooleanToggle(Text.literal("Tracking Beam"), config.trackBeam)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Draw a vertical beam on the container you are being guided to."))
            .setSaveConsumer(value -> config.trackBeam = value)
            .build());

        search.addEntry(entry
            .startBooleanToggle(Text.literal("Tracking HUD"), config.trackHud)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Show the direction arrow and distance readout while tracking."))
            .setSaveConsumer(value -> config.trackHud = value)
            .build());

        search.addEntry(entry
            .startColorField(Text.literal("Tracking Colour"), config.trackColor)
            .setDefaultValue(0x00E676)
            .setAlphaMode(false)
            .setSaveConsumer(value -> config.trackColor = value & 0xFFFFFF)
            .build());

        search.addEntry(entry
            .startIntSlider(Text.literal("Stop Tracking Within (blocks)"), config.trackClearDistance, 0, 64)
            .setDefaultValue(3)
            .setTooltip(Text.literal("Clear the tracked container once you get this close. 0 keeps it until you clear it yourself."))
            .setSaveConsumer(value -> config.trackClearDistance = value)
            .build());
    }
}
