package evernight.client.modules.misc.AutoReconnect;

import evernight.client.ClientContext;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;

public class AutoReconnectModule extends Module {
    public final Setting<Integer> minutesSetting;
    public final Setting<Float> secondsSetting;

    public AutoReconnectModule() {
        super("AutoReconnect", ModuleCategory.MISC);
        this.minutesSetting = this.add(new NumberSetting<>("Minutes", 0, 0, 15).withUnit("m"));
        this.secondsSetting = this.add(new NumberSetting<>("Seconds", 5.0F, 0.0F, 59.0F).withUnit("s"));
        this.secondsSetting.setConfigName("Delay");
        this.setDrawn(false);
    }

    /**
     * Marks the current disconnect as an automatic logout and switches this module off, so the
     * disconnected screen does not reconnect the player after an intentional logout.
     */
    public static void suppressForAutoLogout() {
        ClientContext.serverStateManager.autoLogged = true;
        AutoReconnectModule autoReconnect = ClientContext.getModule(AutoReconnectModule.class);
        if (autoReconnect != null) {
            autoReconnect.disable();
        }
    }
}