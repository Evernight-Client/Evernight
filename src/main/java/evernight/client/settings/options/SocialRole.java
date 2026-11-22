package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum SocialRole implements NamedEntry {
    FRIEND("friend", "friends"),
    ENEMY("enemy", "enemies");
    private final String name;
    private final String pluralName;

    SocialRole(String name, String pluralName) {
        this.name = name;
        this.pluralName = pluralName;
    }

    public String getPluralName() {
        return this.pluralName;
    }

    @Override
    public String getName() {
        return this.name;
    }
}