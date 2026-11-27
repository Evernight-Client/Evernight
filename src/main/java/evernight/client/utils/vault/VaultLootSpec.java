package evernight.client.utils.vault;

import java.util.HashMap;
import java.util.Map;

/**
 * Parsed item-sequence specification used to recognise one particular vault opening.
 *
 * <p>Format: {@code minecraft:item:count;minecraft:item:count}. An optional {@code @<16 hex>} suffix
 * after a count carries the {@link VaultComponentHash} of that stack. Specifications built by the
 * old engine (no component suffix) keep matching on item and count only, so existing workflows do
 * not change; the capture side can opt into component-exact matching one item at a time.
 */
final class VaultLootSpec {
    private static final Map<String, Integer> ITEM_INDEX = buildItemIndex();

    private final int[] item;
    private final int[] count;
    private final long[] components;
    private final boolean componentSensitive;

    private VaultLootSpec(int[] item, int[] count, long[] components, boolean componentSensitive) {
        this.item = item;
        this.count = count;
        this.components = components;
        this.componentSensitive = componentSensitive;
    }

    static VaultLootSpec parse(String spec) {
        String[] parts = spec == null || spec.isBlank() ? new String[0] : spec.split(";");
        int[] item = new int[parts.length];
        int[] count = new int[parts.length];
        long[] components = new long[parts.length];
        boolean sensitive = false;
        int size = 0;
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            String name = part;
            long componentsHash = 0L;
            int at = part.indexOf('@');
            if (at >= 0) {
                name = part.substring(0, at);
                try {
                    componentsHash = Long.parseUnsignedLong(part.substring(at + 1), 16);
                } catch (NumberFormatException malformed) {
                    componentsHash = 0L;
                }
            }
            int colon = name.lastIndexOf(':');
            if (colon <= 0) {
                continue;
            }
            Integer itemId = ITEM_INDEX.get(name.substring(0, colon));
            if (itemId == null) {
                itemId = ITEM_INDEX.get("minecraft:" + name.substring(0, colon));
            }
            if (itemId == null) {
                // An item outside the vault tables can never match; leave a sentinel that fails.
                itemId = -1;
            }
            int stackCount;
            try {
                stackCount = Integer.parseInt(name.substring(colon + 1));
            } catch (NumberFormatException malformed) {
                stackCount = 1;
            }
            item[size] = itemId;
            count[size] = stackCount;
            components[size] = componentsHash;
            sensitive |= componentsHash != 0L;
            size++;
        }
        if (size != item.length) {
            int[] trimmedItem = new int[size];
            int[] trimmedCount = new int[size];
            long[] trimmedComponents = new long[size];
            System.arraycopy(item, 0, trimmedItem, 0, size);
            System.arraycopy(count, 0, trimmedCount, 0, size);
            System.arraycopy(components, 0, trimmedComponents, 0, size);
            return new VaultLootSpec(trimmedItem, trimmedCount, trimmedComponents, sensitive);
        }
        return new VaultLootSpec(item, count, components, sensitive);
    }

    /** Item ids for a target search such as {@code minecraft:enchanted_golden_apple}. */
    static int[] targetItems(String targetSpec) {
        if (targetSpec == null || targetSpec.isBlank() || "all".equals(targetSpec)) {
            return null;
        }
        String[] parts = targetSpec.split(";");
        int[] targets = new int[parts.length];
        int size = 0;
        for (String part : parts) {
            String name = part.trim();
            if (name.isEmpty()) {
                continue;
            }
            Integer itemId = ITEM_INDEX.get(name);
            if (itemId == null) {
                itemId = ITEM_INDEX.get("minecraft:" + name);
            }
            if (itemId != null) {
                targets[size++] = itemId;
            }
        }
        if (size == 0) {
            return null;
        }
        if (size != targets.length) {
            int[] trimmed = new int[size];
            System.arraycopy(targets, 0, trimmed, 0, size);
            return trimmed;
        }
        return targets;
    }

    boolean matches(VaultLootGenerator generator) {
        int size = generator.size();
        if (size != this.item.length) {
            return false;
        }
        if (this.matches(generator, false)) {
            return true;
        }
        // Packet ejection order is the reverse of generation order; the old engine accepted both.
        return this.matches(generator, true);
    }

    private boolean matches(VaultLootGenerator generator, boolean reversed) {
        int size = this.item.length;
        for (int i = 0; i < size; i++) {
            int index = reversed ? size - 1 - i : i;
            if (this.item[i] != generator.item(index) || this.count[i] != generator.count(index)) {
                return false;
            }
            if (this.componentSensitive && this.components[i] != 0L
                    && this.components[i] != generator.components(index)) {
                return false;
            }
        }
        return true;
    }

    int size() {
        return this.item.length;
    }

    private static Map<String, Integer> buildItemIndex() {
        Map<String, Integer> index = new HashMap<>(VaultLootData.ITEM_COUNT * 2);
        for (int i = 0; i < VaultLootData.ITEM_COUNT; i++) {
            index.put(VaultLootData.ITEM_NAMES[i], i);
        }
        return index;
    }
}
