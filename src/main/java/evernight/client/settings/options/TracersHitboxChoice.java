package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum TracersHitboxChoice implements NamedEntry {
    HEAD("Head", 0.95F),
    BODY("Body", 0.5F),
    LEGS("Legs", 0.0F);
    private final String displayName;
    private final float heightFraction;

    TracersHitboxChoice(String displayName, float heightFraction) {
        this.displayName = displayName;
        this.heightFraction = heightFraction;
    }

    @Override
    public String getName() {
        return this.displayName;
    }

    public float getHeightFraction() {
        return this.heightFraction;
    }
}