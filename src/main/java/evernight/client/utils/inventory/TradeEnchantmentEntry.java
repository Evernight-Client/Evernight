package evernight.client.utils.inventory;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public final class TradeEnchantmentEntry {
    @SerializedName("name")
    private final String name;
    @SerializedName("lvl")
    private final int level;

    public TradeEnchantmentEntry(String name, int level) {
        this.name = name;
        this.level = level;
    }

    public String name() {
        return this.name;
    }

    public int level() {
        return this.level;
    }

    @Override
    public String toString() {
        return "TradeEnchantmentEntry[name=" + this.name + ", level=" + this.level + "]";
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(this.name) + this.level;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TradeEnchantmentEntry entry)) {
            return false;
        }
        return this.level == entry.level && Objects.equals(this.name, entry.name);
    }

    @SerializedName("name")
    public String getName() {
        return this.name;
    }

    @SerializedName("lvl")
    public int getLevel() {
        return this.level;
    }
}