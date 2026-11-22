package evernight.client.modules.misc;

import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.options.SocialRole;

public record SocialEntry(String name, SocialRole role) implements NamedEntry {
    @Override
    public String getName() {
        return this.name;
    }
}