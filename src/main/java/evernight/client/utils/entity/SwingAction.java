package evernight.client.utils.entity;

import evernight.client.modules.client.AntiCheat.AntiCheatModule;
import evernight.client.settings.core.Setting;

public enum SwingAction {
    INTERACT, MINING, HIT;

    public boolean isEnabled(AntiCheatModule antiCheat) {
        Setting<Boolean> swingSetting = antiCheat.swingSetting;
        Boolean swingEnabled = swingSetting.getValue();
        if (!swingEnabled) {
            return false;
        } else if (this == MINING) {
            Setting<Boolean> miningSetting = antiCheat.miningSwingSetting;
            Boolean miningEnabled = miningSetting.getValue();
            return miningEnabled;
        } else if (this == HIT) {
            Setting<Boolean> hitSetting = antiCheat.hitSetting;
            Boolean hitEnabled = hitSetting.getValue();
            return hitEnabled;
        } else {
            Setting<Boolean> placeSetting = antiCheat.placeSetting;
            Boolean placeEnabled = placeSetting.getValue();
            return placeEnabled && this == INTERACT;
        }
    }
}