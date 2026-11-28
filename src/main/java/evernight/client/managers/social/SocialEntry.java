package evernight.client.managers.social;

import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.options.SocialRole;

public record SocialEntry(String name, SocialRole role) implements NamedEntry {
    @Override
    public String getName() {
        return this.name;
    }
}