package evernight.client.modules.misc.AntiQuit;

import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.core.Setting;

public class AntiQuitModule extends Module {
    public final Setting<Boolean> disconnectSetting;
    public final Setting<Boolean> gameCloseSetting;

    public AntiQuitModule() {
        super("AntiQuit", ModuleCategory.MISC, "antidisconnect");
        this.disconnectSetting = this.add(new BooleanSetting("Disconnect", false));
        this.gameCloseSetting = this.add(new BooleanSetting("GameClose", false));
    }
}