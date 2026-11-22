package evernight.client.utils.collections;

import evernight.client.utils.inventory.ItemRegistryGroupMatcher;
import evernight.client.utils.world.BlockRegistryGroupMatcher;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public interface RegistryGroupMatcher {
    List MATCHERS = createDefaultMatchers();
    EmptyRegistryGroupMatcher EMPTY = new EmptyRegistryGroupMatcher();

    static RegistryGroupMatcher forRegistry(Registry registry) {
        Iterator matchers = MATCHERS.iterator();
        while (true) {
            if (!matchers.hasNext()) {
                return EMPTY;
            }
            RegistryGroupMatcher matcher = (RegistryGroupMatcher) matchers.next();
            ResourceKey registryKey = registry.key();
            Identifier registryId = registryKey.identifier();
            if (registryId.equals(matcher.getRegistryId())) {
                return matcher;
            }
        }
    }

    private static List createDefaultMatchers() {
        BlockRegistryGroupMatcher blocks = new BlockRegistryGroupMatcher();
        ItemRegistryGroupMatcher items = new ItemRegistryGroupMatcher();
        return List.of(blocks, items);
    }

    Identifier getRegistryId();

    Collection getGroupNames();

    Collection getGroupEntries(String groupName);
}