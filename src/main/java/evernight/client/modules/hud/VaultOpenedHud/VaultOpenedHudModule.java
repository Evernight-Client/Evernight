package evernight.client.modules.hud.VaultOpenedHud;

import evernight.client.modules.HudWidgetModule;
import evernight.client.modules.hud.HudTextListRenderer;
import evernight.client.modules.hud.HudTextRow;
import evernight.client.modules.render.VaultESP.VaultOpenedStore;
import evernight.client.render.text.Translations;
import evernight.client.settings.options.HudAnchor;
import net.minecraft.network.chat.Component;

/**
 * HUD widget showing how many ominous vaults were recorded as opened. The record is global, so the
 * count does not depend on the current server or dimension.
 */
public class VaultOpenedHudModule extends HudWidgetModule {
    public VaultOpenedHudModule() {
        super("VaultOpenedHud");
        this.setRenderer(new HudTextListRenderer(this, new HudTextRow(this::buildText, () -> true)));
        this.getRenderer().setAnchor(HudAnchor.TOP_LEFT);
    }

    private Component buildText() {
        int count = VaultOpenedStore.shared().count();
        return Component.literal(Translations.format("modules.VaultOpenedHud.Count", count));
    }
}
