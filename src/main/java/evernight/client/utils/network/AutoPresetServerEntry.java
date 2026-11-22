package evernight.client.utils.network;

import java.util.HashMap;
import java.util.Map;

public final class AutoPresetServerEntry {
    private final Map presets = new HashMap();
    private final String server;
    private String kitName;

    public AutoPresetServerEntry(String server) {
        this.server = server;
    }

    public Map getPresets() {
        return this.presets;
    }

    public String getKitName() {
        return this.kitName;
    }

    public void setKitName(String kitName) {
        this.kitName = kitName;
    }

    public boolean hasKit() {
        return this.kitName != null;
    }

    public String getServer() {
        return this.server;
    }
}