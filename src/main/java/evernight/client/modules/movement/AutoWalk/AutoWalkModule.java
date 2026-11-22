package evernight.client.modules.movement.AutoWalk;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.audio.BuiltinSound;
import evernight.client.audio.SoundClip;
import evernight.client.events.player.AbstractClientPlayerTickEvent;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.player.SpeedMine.SpeedMineModule;
import evernight.client.render.text.Translations;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.IdentifierSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.utils.collections.NamedIdentifier;
import evernight.client.utils.combat.ExtraBreakTarget;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class AutoWalkModule extends Module {
    private static SpeedMineModule speedMineModule;

    static {
        ClientAccess.eventBus.subscribeStatic(AutoWalkModule.class);
    }

    private final Setting<Boolean> autoJumpSetting;
    private final Setting<Boolean> stuckNotifySetting;
    private final Setting<NamedIdentifier> soundSetting;
    private final Setting<Float> volumeSetting;
    private final Setting<Boolean> pauseSetting;
    private final Setting<Boolean> miningSetting;
    private final Setting<Boolean> eatingSetting;
    private int stuckTicks;

    public AutoWalkModule() {
        super("AutoWalk", ModuleCategory.MOVEMENT);
        this.autoJumpSetting = this.add(new BooleanSetting("AutoJump", false));
        this.stuckNotifySetting = this.add(new BooleanSetting("StuckNotify", false).markVisibilityDependency());
        this.soundSetting = this.add(new IdentifierSetting("Sound", BuiltinSound.WARNING.getIdentifier()).dependsOn(this.stuckNotifySetting));
        this.volumeSetting = this.add(new NumberSetting<>("Volume", 1.0F, 0.0F, 1.0F).dependsOn(this.stuckNotifySetting));
        this.pauseSetting = this.add(new BooleanSetting("Pause", false).markAsGroup());
        this.miningSetting = this.add(new BooleanSetting("Mining", false).dependsOn(this.pauseSetting));
        this.eatingSetting = this.add(new BooleanSetting("Eating", false).dependsOn(this.pauseSetting));
    }

    public static void bindReferencedModules(ModuleRegistry registry) {
        speedMineModule = registry.getModule(SpeedMineModule.class);
    }

    private boolean isMining() {
        if (!speedMineModule.isToggled()) {
            return ClientAccess.evernightMinecraft.gameMode.isDestroying();
        }
        BlockPos miningPosition = speedMineModule.getMiningPos();
        ExtraBreakTarget extraTarget = speedMineModule.getExtraBreakTarget();
        return WorldUtils.canMineBlock(miningPosition)
                || extraTarget != null && WorldUtils.canMineBlock(extraTarget.getPosition());
    }

    private boolean shouldPauseForAction() {
        return this.eatingSetting.getValue() && ClientAccess.evernightMinecraft.player.isUsingItem()
                || this.miningSetting.getValue() && this.isMining();
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        if (this.shouldPauseForAction()) {
            return;
        }
        double verticalDistance = Math.abs(ClientAccess.evernightMinecraft.player.getY() - ClientAccess.evernightMinecraft.player.yo);
        if (ClientContext.movementSpeedTracker.getCurrentSpeed() != 0.0 || !(verticalDistance < 0.2D)) {
            this.stuckTicks = 0;
            return;
        }
        if (this.stuckTicks == 5 && this.stuckNotifySetting.getValue()) {
            ChatUtils.printChat(Component.literal(Translations.ui("modules.AutoWalk.YouAreStuck")),
                    ChatUtils.moduleSignature(this), ChatUtils.MessagePriority.HIGH);
            SoundClip sound = ClientContext.soundManager.getSound(this.soundSetting.getValue());
            sound.play(this.volumeSetting.getValue());
        }
        this.stuckTicks++;
    }

    @EventHandler
    private void onAbstractClientPlayerTickEvent(AbstractClientPlayerTickEvent event) {
        if (!this.shouldPauseForAction()) {
            ClientContext.inputOverrides.requestForwardMovement(1.0F);
            ClientContext.inputOverrides.requestForward(true);
            if (this.autoJumpSetting.getValue()) {
                ClientContext.inputOverrides.requestJump(true);
            }
        }
    }
}