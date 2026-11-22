package evernight.client.modules.client.Colors;

import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.PermanentModule;
import evernight.client.modules.render.Search.SearchModule;
import evernight.client.render.color.Catppuccin;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.ColorsSchemeChoice;

import java.awt.*;

public class ColorsModule extends PermanentModule {
    private static SearchModule searchModule;

    static {
        searchModule = ClientContext.getModule(SearchModule.class);
    }

    public final Setting<Color> themeColorSetting;
    public final Setting<Color> friendColorSetting;
    public final Setting<Color> enemyColorSetting;
    public final Setting<ColorsSchemeChoice> schemeSetting;
    private final Setting<Float> rainbowSpeedSetting;

    public ColorsModule() {
        super("Colors", ModuleCategory.CLIENT);
        this.rainbowSpeedSetting = this.add(new NumberSetting<>("RainbowSpeed", 1.0F, 0.1F, 10.0F));
        this.themeColorSetting = this.add(new ColorSetting("ThemeColor", Catppuccin.MAUVE));
        this.friendColorSetting = this.add(new ColorSetting("FriendColor", Catppuccin.SKY));
        this.enemyColorSetting = this.add(new ColorSetting("EnemyColor", Catppuccin.RED));
        this.schemeSetting = this.add(new EnumSetting<>("Scheme", ColorsSchemeChoice.NORMAL));
        this.schemeSetting.setChangeCallback(() -> {
            if (searchModule.isToggled()) searchModule.restartSearch();
        });
    }

    public static void bindSearchModule(ModuleRegistry registry) {
        searchModule = registry.getModule(SearchModule.class);
    }

    public float getRainbowPeriod() {
        return 1.0F / this.rainbowSpeedSetting.getValue();
    }
}