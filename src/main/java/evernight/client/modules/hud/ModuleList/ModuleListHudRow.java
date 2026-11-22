package evernight.client.modules.hud.ModuleList;

import evernight.client.modules.Module;
import net.minecraft.network.chat.Component;
import evernight.client.modules.hud.HudTextRow;

public class ModuleListHudRow extends HudTextRow {
    private final Module module;

    public ModuleListHudRow(ModuleListModule owner, Module module) {
        super(() -> {
            String infoText = module.getInfoString();
            return Component.literal(infoText);
        }, () -> {
            return module.isToggled() && module.isDrawn() && (!module.getKeybind().isUnbound()
                    || !owner.onlyBoundSetting.getValue());
        });
        this.module = module;
    }

    public Module getModule() {
        return this.module;
    }
}