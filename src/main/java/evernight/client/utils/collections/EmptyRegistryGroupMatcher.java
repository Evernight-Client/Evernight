package evernight.client.utils.collections;

import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Collections;

public class EmptyRegistryGroupMatcher implements RegistryGroupMatcher {
    @Override
    public Identifier getRegistryId() {
        return null;
    }

    @Override
    public Collection getGroupNames() {
        return Collections.emptyList();
    }

    @Override
    public Collection getGroupEntries(String groupName) {
        return Collections.emptyList();
    }
}