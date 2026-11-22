package evernight.client.modules.movement.Step;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.world.HoleManager;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.managers.player.TickRateController;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.font.FontRenderer;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.network.PacketSender;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.player.StepHeightEvent;
import evernight.client.modules.movement.HoleSnap.HoleSnapModule;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class StepModule extends Module {
    private static HoleSnapModule holeSnap;
    final Setting<ModeChoice> modeSetting;
    final Setting<Boolean> useTimerSetting;
    private final Setting<Float> heightSetting;
    private final Setting<Boolean> safeDisableSetting;
    private final Setting<Float> timerSpeedSetting;
    private boolean usingStepTimer;
    private int stepCount;

    public StepModule() {
        super("Step", ModuleCategory.MOVEMENT);
        this.modeSetting = this.add(new EnumSetting<>("Mode", StepModule.ModeChoice.VANILLA));
        this.heightSetting = this.add(new NumberSetting<>("Height", 2.0F, 1.0F, 4.0F).withUnit("m"));
        this.safeDisableSetting = this.add(new BooleanSetting("SafeDisable", false));
        Predicate<Object> timerVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.NORMAL;
        this.useTimerSetting = this.add(new BooleanSetting("UseTimer", true, timerVisibility).markVisibilityDependency());
        Predicate<Object> timerSpeedVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.NORMAL && this.useTimerSetting.isExpanded();
        this.timerSpeedSetting = this.add(new NumberSetting<>("TimerSpeed", 0.3F, 0.01F, 1.0F, timerSpeedVisibility));
        this.stepCount = 0;
    }

    public static void bindReferencedModules(ModuleRegistry registry) {
        holeSnap = registry.getModule(HoleSnapModule.class);
    }

    @Override
    public String getInfo() {
        return FontRenderer.capitalize(this.modeSetting.getValue());
    }

    @Override
    public void onDisable() {
        this.usingStepTimer = false;
        this.stepCount = 0;
    }

    @EventHandler(priority = -100)
    private void onLocalPlayerPreTickEvent(LocalPlayerPreTickEvent event) {
        if (this.stepCount <= 0) {
            return;
        }
        HoleManager holes = ClientContext.holeManager;
        BlockPos position = PlayerStateUtils.getLocalRoundedBlockPosition();
        if (!holes.intersectsHole(position)) {
            return;
        }
        if (this.safeDisableSetting.getValue()) {
            this.disable();
        }
    }

    @EventHandler
    private void onStepHeight(StepHeightEvent event) {
        if (holeSnap.shouldPauseStep()) {
            return;
        }
        if (!ClientAccess.evernightMinecraft.player.onGround() || PlayerStateUtils.getTrackedFallDistance() > 0.0F
                || !ClientAccess.evernightMinecraft.player.verticalCollision) {
            TickRateController timer = ClientContext.tickRateController;
            timer.releaseMultiplier(this);
            return;
        }
        if (EventStage.Pre == event.getStage()) {
            Setting<Float> height = this.heightSetting;
            float requestedHeight = height.getValue();
            event.setHeight(requestedHeight);
        }
        if (event.getStage() != EventStage.Post) {
            return;
        }
        double actualHeight = event.getHeight();
        if (actualHeight <= 0.6D) {
            TickRateController timer = ClientContext.tickRateController;
            timer.releaseMultiplier(this);
            return;
        }
        if (this.modeSetting.getValue() == StepModule.ModeChoice.VANILLA) {
            this.stepCount++;
            return;
        }
        ArrayList<Double> offsets = new ArrayList<>();
        if (this.useTimerSetting.getValue()) {
            if (!PlayerStateUtils.isInCobweb(ClientAccess.evernightMinecraft.player)) {
                TickRateController timer = ClientContext.tickRateController;
                float requestedSpeed = this.timerSpeedSetting.getValue();
                timer.requestMultiplier(this, requestedSpeed);
                this.usingStepTimer = true;
            }
        }
        this.stepCount++;
        float height = event.getHeight();
        if (height <= 1.0F) {
            offsets.addAll(List.of(0.42D * height, 0.75D * height));
        } else if (height <= 1.5D) {
            offsets.addAll(List.of(0.42D, 0.75D, 1.0D, 1.16D, 1.23D, 1.2D));
        } else if (height <= this.heightSetting.getValue()) {
            offsets.addAll(List.of(0.42D, 0.78D, 0.63D, 0.51D, 0.9D, 1.21D, 1.45D, 1.43D));
        }
        if (offsets.isEmpty()) {
            return;
        }
        for (Double offset : offsets) {
            double x = ClientAccess.evernightMinecraft.player.getX();
            double baseY = ClientAccess.evernightMinecraft.player.getY();
            double y = baseY + offset;
            double z = ClientAccess.evernightMinecraft.player.getZ();
            boolean onGround = ClientAccess.evernightMinecraft.player.onGround();
            PacketSender.sendPosition(x, y, z, onGround);
        }
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        this.usingStepTimer = false;
    }

    public boolean isUsingTimer() {
        return this.usingStepTimer;
    }

    public enum ModeChoice implements NamedEntry {
        VANILLA("Vanilla"),
        NORMAL("Normal");
        private final String displayName;

        ModeChoice(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }
}