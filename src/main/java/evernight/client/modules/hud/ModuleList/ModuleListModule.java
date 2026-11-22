package evernight.client.modules.hud.ModuleList;

import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.HudWidgetModule;
import evernight.client.modules.Module;
import evernight.client.modules.client.HUD.HUDModule;
import evernight.client.modules.client.HUD.HUDModule.ModuleSortMode;
import evernight.client.render.font.FontRenderer;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.HudAnchor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import evernight.client.modules.hud.HudTextListRenderer;

public class ModuleListModule extends HudWidgetModule {
    public final Setting<Boolean> onlyBoundSetting;
    public final Setting<ModuleSortMode> sortSetting;
    private final List<ModuleListHudRow> moduleRows;

    public ModuleListModule() {
        super("ModuleList", "arraylist");
        this.onlyBoundSetting = this.add(new BooleanSetting("OnlyBound", false));
        this.sortSetting = this.add(new EnumSetting<>("Sort", HUDModule.ModuleSortMode.LENGTH));
        this.moduleRows = new ArrayList<>();
        this.setRenderer(new HudTextListRenderer(this, this.moduleRows));
        this.getRenderer().setAnchor(HudAnchor.TOP_RIGHT);
    }

    @Override
    public void onEnable() {
        this.moduleRows.clear();
        ModuleRegistry registry = ClientContext.moduleRegistry;
        Stream<ModuleListHudRow> rowsStream = registry.getRegistry().stream()
                .map(registeredModule -> new ModuleListHudRow(this, registeredModule));
        rowsStream.forEach(this.moduleRows::add);
    }

    @EventHandler
    public void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        Setting<ModuleSortMode> sortMode = this.sortSetting;
        if (sortMode.getValue() == HUDModule.ModuleSortMode.ALPHABET) {
            this.moduleRows.sort(Comparator.comparing(row -> {
                Module module = row.getModule();
                return module.getInfoString();
            }));
        } else {
            this.moduleRows.sort(Comparator.comparingDouble(row -> {
                FontRenderer font = FontRenderer.INSTANCE;
                Module module = row.getModule();
                return -font.getWidth(module.getInfoString());
            }));
        }
    }
}