package net.containerutil.search;

import net.containerutil.config.ConfigManager;
import net.containerutil.container.ContainerKind;
import net.containerutil.data.ContainerIndex;
import net.containerutil.data.ContainerRecord;
import net.containerutil.data.ItemEntry;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The ContainerUtil query language.
 *
 * <pre>
 *   iron ingot            free text — matches an item's display name, registry id or path
 *   "oak log"             quoted, so the space is part of one term rather than two terms
 *   #logs                 item tag (namespace optional: #logs == #minecraft:logs)
 *   item:minecraft:stone  exact registry id
 *   enchant:mending       stacks carrying a named enchantment (books included)
 *   has:enchant           any enchanted stack
 *   dim:nether            restrict to a dimension
 *   in:barrel             restrict to a container kind (also type: / kind:)
 *   label:overflow        match a nickname you assigned, or an anvil-renamed container
 *   is:empty              also: full, stale, unopened, opened, labeled, double, mobile
 *   count&gt;64             also &lt; &gt;= &lt;= = against the total matched in that container
 *   -cobblestone          exclusion (also !cobblestone)
 * </pre>
 *
 * <p>Terms combine with AND. Exclusions reject the whole container rather than just hiding the
 * stack, because the useful question is "which chests have iron and <em>no</em> cobble", not
 * "show me those chests with the cobble line removed".
 */
public final class SearchQuery {

    private final List<Predicate<ContainerRecord>> containerFilters = new ArrayList<>();
    private final List<Predicate<ItemEntry>> itemFilters = new ArrayList<>();
    private final List<Predicate<ItemEntry>> itemExclusions = new ArrayList<>();

    private String countOp;
    private int countValue;

    private final List<String> warnings = new ArrayList<>();
    private boolean hasItemCriteria = false;
    private boolean empty = true;

    private SearchQuery() {
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    public static SearchQuery parse(String raw) {
        SearchQuery query = new SearchQuery();
        if (raw == null || raw.isBlank()) return query;

        for (String token : tokenize(raw)) {
            if (token.isBlank()) continue;
            query.empty = false;

            boolean negated = false;
            String body = token;
            if (body.length() > 1 && (body.charAt(0) == '-' || body.charAt(0) == '!')) {
                negated = true;
                body = body.substring(1);
            }
            query.parseToken(body, negated);
        }
        return query;
    }

    private void parseToken(String token, boolean negated) {
        String lower = token.toLowerCase(Locale.ROOT);

        // count>64 and friends. Checked first: it is the one operator that is not colon-delimited.
        if (lower.startsWith("count")) {
            String rest = token.substring("count".length());
            String op = null;
            if (rest.startsWith(">=") || rest.startsWith("<=")) op = rest.substring(0, 2);
            else if (rest.startsWith(">") || rest.startsWith("<") || rest.startsWith("=")) op = rest.substring(0, 1);
            if (op != null) {
                try {
                    countValue = Integer.parseInt(rest.substring(op.length()).trim());
                    countOp = op;
                } catch (NumberFormatException e) {
                    warnings.add("Not a number: " + token);
                }
                return;
            }
        }

        if (token.startsWith("#")) {
            addItemFilter(tagPredicate(token.substring(1)), negated);
            return;
        }

        int colon = token.indexOf(':');
        if (colon > 0) {
            String key = lower.substring(0, colon);
            String value = token.substring(colon + 1);
            if (parseKeyed(key, value, negated)) return;
            // Not a keyword we know — almost certainly a registry id like minecraft:iron_ingot,
            // so fall through and treat the whole token as free text.
        }

        addItemFilter(textPredicate(token), negated);
    }

    /** Returns false if the key is not one of ours, so the caller can fall back to free text. */
    private boolean parseKeyed(String key, String value, boolean negated) {
        String lowerValue = value.toLowerCase(Locale.ROOT);
        switch (key) {
            case "dim", "dimension", "d" -> {
                addContainerFilter(record -> {
                    String dim = record.dim == null ? "" : record.dim.toLowerCase(Locale.ROOT);
                    return dim.contains(lowerValue);
                }, negated);
                return true;
            }
            case "in", "type", "kind" -> {
                addContainerFilter(record -> {
                    ContainerKind kind = record.kindOrNull();
                    if (kind == null) return false;
                    return kind.id().contains(lowerValue)
                        || kind.displayName().toLowerCase(Locale.ROOT).contains(lowerValue);
                }, negated);
                return true;
            }
            case "label", "name" -> {
                addContainerFilter(record -> {
                    String label = record.label == null ? "" : record.label.toLowerCase(Locale.ROOT);
                    String custom = record.customName == null ? "" : record.customName.toLowerCase(Locale.ROOT);
                    return label.contains(lowerValue) || custom.contains(lowerValue);
                }, negated);
                return true;
            }
            case "is" -> {
                Predicate<ContainerRecord> predicate = statePredicate(lowerValue);
                if (predicate == null) {
                    warnings.add("Unknown is: value '" + value + "'");
                    return true;
                }
                addContainerFilter(predicate, negated);
                return true;
            }
            case "item", "id" -> {
                addItemFilter(entry -> entry.id != null
                    && entry.id.toLowerCase(Locale.ROOT).equals(normaliseId(lowerValue)), negated);
                return true;
            }
            case "enchant", "enchantment", "ench" -> {
                addItemFilter(entry -> {
                    if (!entry.hasEnchants()) return false;
                    for (String id : entry.enchants) {
                        if (id.toLowerCase(Locale.ROOT).contains(lowerValue)) return true;
                    }
                    return false;
                }, negated);
                return true;
            }
            case "has" -> {
                if (lowerValue.startsWith("ench")) {
                    addItemFilter(ItemEntry::hasEnchants, negated);
                } else if (lowerValue.startsWith("nest") || lowerValue.startsWith("shulk")) {
                    addItemFilter(entry -> entry.nestedIn != null, negated);
                } else {
                    warnings.add("Unknown has: value '" + value + "'");
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static Predicate<ContainerRecord> statePredicate(String value) {
        int staleDays = ConfigManager.get().staleAfterDays;
        return switch (value) {
            case "empty" -> record -> !record.isUnopened() && record.usedSlots == 0;
            case "full" -> record -> record.slotCount > 0 && record.usedSlots >= record.slotCount;
            case "partial" -> record -> record.usedSlots > 0 && record.usedSlots < record.slotCount;
            case "stale" -> record -> record.isStale(staleDays);
            case "unopened", "unknown" -> ContainerRecord::isUnopened;
            case "opened", "known" -> record -> !record.isUnopened();
            case "labeled", "labelled", "named" -> record ->
                (record.label != null && !record.label.isBlank())
                    || (record.customName != null && !record.customName.isBlank());
            case "double" -> record -> record.hasSecondary;
            case "mobile", "entity" -> ContainerRecord::isEntityBacked;
            default -> null;
        };
    }

    private static String normaliseId(String value) {
        return value.contains(":") ? value : "minecraft:" + value;
    }

    private static Predicate<ItemEntry> textPredicate(String needle) {
        String lower = needle.toLowerCase(Locale.ROOT);
        return entry -> {
            if (entry.name != null && entry.name.toLowerCase(Locale.ROOT).contains(lower)) return true;
            if (entry.id != null && entry.id.toLowerCase(Locale.ROOT).contains(lower)) return true;
            // So "iron ingot" finds minecraft:iron_ingot without the user typing the underscore.
            return entry.path().replace('_', ' ').contains(lower);
        };
    }

    /**
     * Tag membership, memoised per item id.
     *
     * <p>Resolving a registry entry and walking its tags for every stack in a large index is
     * the one genuinely expensive predicate here, and item ids repeat constantly across
     * containers — so the cache turns thousands of lookups into a few hundred.
     */
    private static Predicate<ItemEntry> tagPredicate(String rawTag) {
        Identifier tagId = Identifier.tryParse(rawTag.contains(":") ? rawTag : "minecraft:" + rawTag);
        if (tagId == null) return entry -> false;
        TagKey<Item> tag = TagKey.of(RegistryKeys.ITEM, tagId);
        Map<String, Boolean> cache = new HashMap<>();

        return entry -> {
            if (entry.id == null) return false;
            return cache.computeIfAbsent(entry.id, id -> {
                Identifier itemId = Identifier.tryParse(id);
                if (itemId == null) return false;
                return Registries.ITEM.getEntry(itemId)
                    .map((RegistryEntry.Reference<Item> ref) -> ref.isIn(tag))
                    .orElse(false);
            });
        };
    }

    private void addContainerFilter(Predicate<ContainerRecord> predicate, boolean negated) {
        containerFilters.add(negated ? predicate.negate() : predicate);
    }

    private void addItemFilter(Predicate<ItemEntry> predicate, boolean negated) {
        hasItemCriteria = true;
        if (negated) {
            itemExclusions.add(predicate);
        } else {
            itemFilters.add(predicate);
        }
    }

    /** Splits on whitespace, treating double-quoted runs as single tokens. */
    private static List<String> tokenize(String raw) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : raw.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes && Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    out.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) out.add(current.toString());
        return out;
    }

    // ── Execution ────────────────────────────────────────────────────────────

    public boolean isEmpty() {
        return empty;
    }

    public List<String> warnings() {
        return warnings;
    }

    /** Runs the query over the whole index, returning results sorted nearest-first. */
    public List<SearchResult> run(ContainerIndex index, double px, double py, double pz) {
        List<SearchResult> results = new ArrayList<>();

        for (ContainerRecord record : index.all()) {
            if (!passesContainerFilters(record)) continue;

            List<ItemEntry> matched = new ArrayList<>();
            int total = 0;
            boolean excluded = false;

            if (record.items != null) {
                for (ItemEntry entry : record.items) {
                    if (matchesAny(itemExclusions, entry)) {
                        excluded = true;
                        break;
                    }
                    if (matchesAll(itemFilters, entry)) {
                        matched.add(entry);
                        total += entry.count;
                    }
                }
            }
            if (excluded) continue;

            // Positive item criteria that nothing satisfied means this container is not a result.
            // A query of only exclusions ("-cobblestone") still matches, with an empty match list:
            // the question there is which containers *lack* something.
            if (!itemFilters.isEmpty() && matched.isEmpty()) continue;

            if (!passesCount(total)) continue;

            results.add(new SearchResult(record, matched, total, record.distanceSqTo(px, py, pz)));
        }

        results.sort((a, b) -> Double.compare(a.distanceSq(), b.distanceSq()));
        return results;
    }

    private boolean passesContainerFilters(ContainerRecord record) {
        for (Predicate<ContainerRecord> filter : containerFilters) {
            if (!filter.test(record)) return false;
        }
        return true;
    }

    private boolean passesCount(int total) {
        if (countOp == null) return true;
        return switch (countOp) {
            case ">" -> total > countValue;
            case "<" -> total < countValue;
            case ">=" -> total >= countValue;
            case "<=" -> total <= countValue;
            case "=" -> total == countValue;
            default -> true;
        };
    }

    private static boolean matchesAll(List<Predicate<ItemEntry>> filters, ItemEntry entry) {
        for (Predicate<ItemEntry> filter : filters) {
            if (!filter.test(entry)) return false;
        }
        return true;
    }

    private static boolean matchesAny(List<Predicate<ItemEntry>> filters, ItemEntry entry) {
        for (Predicate<ItemEntry> filter : filters) {
            if (filter.test(entry)) return true;
        }
        return false;
    }
}
