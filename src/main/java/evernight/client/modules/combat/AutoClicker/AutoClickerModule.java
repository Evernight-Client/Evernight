package evernight.client.modules.combat.AutoClicker;

import com.mojang.blaze3d.platform.InputConstants.Key;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.render.WorldRenderEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.exploit.MultiTask.MultiTaskModule;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.math.MathUtils;
import evernight.mixin.minecraft.client.MinecraftAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.core.SettingLabelCondition;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class AutoClickerModule extends Module {
    private static MultiTaskModule multiTaskModule;

    static {
        multiTaskModule = ClientContext.getModule(MultiTaskModule.class);
    }

    private final Setting<ModeChoice> modeSetting;
    private final Setting<Float> cpsSetting;
    private final Setting<Float> delaySetting;
    private final Setting<Boolean> friendProtectSetting;
    private final ElapsedTimer clickTimer;
    private final List<Long> recentClickTimes;
    private float delayMultiplier;

    public AutoClickerModule() {
        super("AutoClicker", ModuleCategory.AUTO);
        this.modeSetting = this.add(new EnumSetting<>("Mode", AutoClickerModule.ModeChoice.LEFT));
        this.cpsSetting = this.add(new NumberSetting<>("CPS", 8.0F, 1.0F, 20.0F));
        this.delaySetting = this.add(new NumberSetting<>("Delay", 1.0F, 1.0F, 3.0F)
                .withUnit("x").setBoundaryLabel("Off", SettingLabelCondition.MIN));
        this.friendProtectSetting = this.add(new BooleanSetting("FriendProtect", true));
        this.clickTimer = new ElapsedTimer();
        this.delayMultiplier = 1.0F;
        this.recentClickTimes = new ArrayList<>();
    }

    public static void bindModules(ModuleRegistry registry) {
        multiTaskModule = registry.getModule(MultiTaskModule.class);
    }

    @Override
    public String getInfo() {
        int recentClickCount = this.recentClickTimes.size();
        return String.valueOf(recentClickCount);
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent.BeforeBufferSource event) {
        this.recentClickTimes.removeIf(timestamp -> System.currentTimeMillis() - timestamp >= 1000L);
        float clicksPerSecond = this.cpsSetting.getValue();
        long clickIntervalMillis = (long) (1000.0F / clicksPerSecond);
        if (!this.delaySetting.isAtMinimum()) {
            clickIntervalMillis *= (long) this.delayMultiplier;
        }
        if (this.clickTimer.hasElapsed(clickIntervalMillis)) {
            this.performClick();
            this.recentClickTimes.add(System.currentTimeMillis());
            this.clickTimer.reset();
            float upperBound = this.delaySetting.getValue();
            this.delayMultiplier = MathUtils.randomFloat(1.0F, upperBound);
        }
    }

    private void performClick() {
        if (multiTaskModule.isToggled()) {
            if (ClientAccess.evernightMinecraft.player.isUsingItem() || ClientAccess.evernightMinecraft.screen != null) {
                (this.modeSetting.getValue()).clickDirectly();
                return;
            }
        }
        Key key = (this.modeSetting.getValue()).getKeyMapping().key;
        KeyMapping.click(key);
    }

    public boolean shouldProtectFriend(Entity target) {
        if (this.isToggled()) {
            if (this.friendProtectSetting.getValue() && target instanceof Player playerTarget) {
                Player friendTarget = playerTarget;
                return ClientContext.socialRegistry.isFriendPlayer(friendTarget);
            }
        }
        return false;
    }

    public enum ModeChoice implements NamedEntry {
        LEFT("Left"),
        RIGHT("Right");
        private final String displayName;

        ModeChoice(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }

        public KeyMapping getKeyMapping() {
            return this == LEFT ? ClientAccess.evernightMinecraft.options.keyAttack
                    : ClientAccess.evernightMinecraft.options.keyUse;
        }

        public void clickDirectly() {
            if (this == LEFT) {
                ((MinecraftAccessor) ClientAccess.evernightMinecraft).attack();
            } else {
                ((MinecraftAccessor) ClientAccess.evernightMinecraft).interact();
            }
        }
    }
}