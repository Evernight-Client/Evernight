package evernight.client.modules.render.NoBob;

import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import net.minecraft.client.player.LocalPlayer;

public class NoBobModule extends Module {
    public final Setting<Float> multiplierSetting;

    public NoBobModule() {
        super("NoBob", ModuleCategory.RENDER);
        multiplierSetting = add(new NumberSetting<>("Multiplier", 0.0F, 0.0F, 1.0F));
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        if (isWorldUnavailable()) return;
        Float multiplier = multiplierSetting.getValue();
        if (multiplier <= 0.0F) {
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            player.avatarState().walkDist = 0.0F;
        }
    }
}