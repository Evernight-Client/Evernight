package evernight.client.modules.client.Settings;

import evernight.client.audio.BuiltinSound;
import evernight.client.gui.screens.preset.PresetScreen;
import evernight.client.managers.registry.CommandRegistry;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.PermanentModule;
import evernight.client.utils.collections.NamedIdentifier;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.IdentifierSetting;
import evernight.client.settings.types.StringSetting;

public class SettingsModule extends PermanentModule {
    public final Setting<String> prefixSetting;
    private final Setting<NamedIdentifier> startupSetting;
    private final Setting<Boolean> presetsSetting;

    public SettingsModule() {
        super("Settings", ModuleCategory.CLIENT);
        this.prefixSetting = this.add(new StringSetting(("Prefix"), ";"));
        this.startupSetting = this.add(new IdentifierSetting(("Startup"), BuiltinSound.BLIP.getIdentifier()));
        this.presetsSetting = this.add(new BooleanSetting(("Presets"), true));
        this.prefixSetting.setChangeCallback(() -> {
            String prefixValue = this.prefixSetting.getValue();
            updateCommandPrefix(this, prefixValue);
        });
        this.presetsSetting.setChangeCallback(() -> {
            if (!this.presetsSetting.getValue()) {
                (new PresetScreen()).open();
                this.presetsSetting.setValue(true);
            }
        });
        this.prefixSetting.setExcludedFromConfig(true);
    }

    private static void updateCommandPrefix(SettingsModule owner, String prefixValue) {
        if (!prefixValue.equals(CommandRegistry.getPrefix())) {
            String updatedPrefix = owner.prefixSetting.getValue();
            CommandRegistry.setPrefix(updatedPrefix);
        }
    }

    public NamedIdentifier getStartupSound() {
        return this.startupSetting.getValue();
    }

    public enum MultiplierOption implements NamedEntry {
        NONE("None", 0),
        X2("X2", 2),
        X4("X4", 4),
        X8("X8", 8);
        private final String displayName;
        private final int multiplier;

        MultiplierOption(String displayName, int multiplier) {
            this.displayName = displayName;
            this.multiplier = multiplier;
        }

        @Override
        public String getName() {
            return this.displayName;
        }

        public int getMultiplier() {
            return this.multiplier;
        }
    }
}