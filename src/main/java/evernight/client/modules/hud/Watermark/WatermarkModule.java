package evernight.client.modules.hud.Watermark;

import evernight.client.ClientAccess;
import evernight.client.EvernightClient;
import evernight.client.modules.HudWidgetModule;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.StringSetting;
import evernight.client.settings.options.HudAnchor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.time.LocalDate;
import java.time.Month;
import evernight.client.modules.hud.HudTextListRenderer;
import evernight.client.modules.hud.HudTextRow;

public class WatermarkModule extends HudWidgetModule {
    private static boolean aprilFoolsDay;

    static {
        aprilFoolsDay = false;
        LocalDate today = LocalDate.now();
        aprilFoolsDay = today.getMonth() == Month.APRIL && today.getDayOfMonth() == 1;
    }

    public final Setting<Boolean> versionColorSetting;
    private final Setting<String> textSetting;

    public WatermarkModule() {
        super("Watermark");
        this.versionColorSetting = this.add(new BooleanSetting("VersionColor", true));
        this.textSetting = this.add(new StringSetting("Text", "Evernight"));
        HudTextRow textRow = new HudTextRow(this::buildWatermarkText, () -> true);
        this.setRenderer(new HudTextListRenderer(this, textRow));
        this.getRenderer().setAnchor(HudAnchor.TOP_LEFT);
        this.setToggled(true);
    }

    private String getVersionSuffix() {
        return " v" + ClientAccess.getVersion();
    }

    public String getVersionText() {
        String version = ClientAccess.getVersion();
        return "v" + version;
    }

    private Component buildWatermarkText() {
        if (aprilFoolsDay) {
            String aprilFoolsText = EvernightClient.NAME + " v" + EvernightClient.VERSION;
            return Component.literal(aprilFoolsText);
        }
        String versionSuffix = this.getVersionSuffix();
        MutableComponent versionComponent = Component.literal(versionSuffix);
        Setting<Boolean> versionColorOption = this.versionColorSetting;
        Boolean useWhiteVersion = versionColorOption.getValue();
        if (useWhiteVersion) {
            ChatFormatting versionColor = ChatFormatting.WHITE;
            versionComponent.withStyle(versionColor);
        }
        String configuredText = this.textSetting.getValue();
        String trimmedText = configuredText.trim();
        MutableComponent watermark = Component.literal(trimmedText);
        return watermark.append(versionComponent);
    }
}