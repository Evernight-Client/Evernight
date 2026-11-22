package evernight.client.modules.render.ViewModel;

import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.network.PacketEvent;
import evernight.client.gui.screens.viewmodel.ViewModelEditorScreen;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class ViewModelModule extends Module {
    public final Setting<Boolean> instantSwapSetting;
    public final Setting<Boolean> eatingSetting;
    public final Setting<Boolean> lighterSetting;
    public final Setting<Boolean> armSetting;
    public final Setting<Float> mainXSetting;
    public final Setting<Float> mainYSetting;
    public final Setting<Float> mainZSetting;
    public final Setting<Float> mainScaleXSetting;
    public final Setting<Float> mainScaleYSetting;
    public final Setting<Float> mainScaleZSetting;
    public final Setting<Float> mainRotateXSetting;
    public final Setting<Float> mainRotateYSetting;
    public final Setting<Float> mainRotateZSetting;
    public final Setting<Float> offXSetting;
    public final Setting<Float> offYSetting;
    public final Setting<Float> offZSetting;
    public final Setting<Float> offScaleXSetting;
    public final Setting<Float> offScaleYSetting;
    public final Setting<Float> offScaleZSetting;
    public final Setting<Float> offRotateXSetting;
    public final Setting<Float> offRotateYSetting;
    public final Setting<Float> offRotateZSetting;
    public final Setting<Boolean> miscSetting;
    public final Setting<Float> eatMultiplierSetting;
    public final Setting<Boolean> noSwaySetting;
    public final Setting<Boolean> noTridentAnimSetting;
    public final Setting<Boolean> swingProgressSetting;
    public final Setting<Boolean> staticSetting;
    public final Setting<Float> mainHandSwingProgressAmountSetting;
    public final Setting<Float> offHandSwingProgressAmountSetting;
    public final Setting<Boolean> viewModelFovSetting;
    public final Setting<Integer> fovAmountSetting;
    public final Setting<HandChoice> swingHandSetting;
    public final Setting<TypeChoice> swingTypeSetting;
    public final Setting<Boolean> swingShakeSetting;
    public final Setting<Double> swingSpeedSetting;
    private final Setting<Boolean> adjustSetting;
    private final Setting<Boolean> mainHandSetting;
    private final Setting<Boolean> offHandSetting;
    private final Setting<Boolean> mainHandSwingSetting;
    private final Setting<Boolean> offHandSwingSetting;
    private final Setting<Boolean> swingSetting;
    private final Vector3f scaleVector;
    private long nextShakeTimeMillis;
    private int shakeTicksRemaining;

    public ViewModelModule() {
        super("ViewModel", ModuleCategory.RENDER);
        this.adjustSetting = this.add(new BooleanSetting("Adjust", false));
        this.instantSwapSetting = this.add(new BooleanSetting("InstantSwap", false));
        this.eatingSetting = this.add(new BooleanSetting("Eating", true));
        this.lighterSetting = this.add(new BooleanSetting("Lighter", false));
        this.mainHandSetting = this.add(new BooleanSetting("MainHand", true).markAsGroup().markVisibilityDependency());
        this.armSetting = this.add(new BooleanSetting("Arm", false).dependsOn(this.mainHandSetting));
        this.mainXSetting = this.add(new NumberSetting<>("MainX", 0.0F, -1.0F, 1.0F).dependsOn(this.mainHandSetting));
        this.mainYSetting = this.add(new NumberSetting<>("MainY", 0.0F, -1.0F, 1.0F).dependsOn(this.mainHandSetting));
        this.mainZSetting = this.add(new NumberSetting<>("MainZ", 0.0F, -1.0F, 1.0F).dependsOn(this.mainHandSetting));
        this.mainScaleXSetting = this.add(new NumberSetting<>("MainScaleX", 1.0F, 0.0F, 2.0F).dependsOn(this.mainHandSetting));
        this.mainScaleYSetting = this.add(new NumberSetting<>("MainScaleY", 1.0F, 0.0F, 2.0F).dependsOn(this.mainHandSetting));
        this.mainScaleZSetting = this.add(new NumberSetting<>("MainScaleZ", 1.0F, 0.0F, 2.0F).dependsOn(this.mainHandSetting));
        this.mainRotateXSetting = this.add(new NumberSetting<>("MainRotateX", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.mainHandSetting));
        this.mainRotateYSetting = this.add(new NumberSetting<>("MainRotateY", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.mainHandSetting));
        this.mainRotateZSetting = this.add(new NumberSetting<>("MainRotateZ", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.mainHandSetting));
        this.offHandSetting = this.add(new BooleanSetting("OffHand", true).markAsGroup().markVisibilityDependency());
        this.offXSetting = this.add(new NumberSetting<>("OffX", 0.0F, -1.0F, 1.0F).dependsOn(this.offHandSetting));
        this.offYSetting = this.add(new NumberSetting<>("OffY", 0.0F, -1.0F, 1.0F).dependsOn(this.offHandSetting));
        this.offZSetting = this.add(new NumberSetting<>("OffZ", 0.0F, -1.0F, 1.0F).dependsOn(this.offHandSetting));
        this.offScaleXSetting = this.add(new NumberSetting<>("OffScaleX", 1.0F, 0.0F, 2.0F).dependsOn(this.offHandSetting));
        this.offScaleYSetting = this.add(new NumberSetting<>("OffScaleY", 1.0F, 0.0F, 2.0F).dependsOn(this.offHandSetting));
        this.offScaleZSetting = this.add(new NumberSetting<>("OffScaleZ", 1.0F, 0.0F, 2.0F).dependsOn(this.offHandSetting));
        this.offRotateXSetting = this.add(new NumberSetting<>("OffRotateX", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.offHandSetting));
        this.offRotateYSetting = this.add(new NumberSetting<>("OffRotateY", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.offHandSetting));
        this.offRotateZSetting = this.add(new NumberSetting<>("OffRotateZ", 0.0F, 0.0F, 360.0F).withUnit("°").dependsOn(this.offHandSetting));
        this.swingSetting = this.add(new BooleanSetting("Swing", false).markAsGroup().markVisibilityDependency());
        this.swingHandSetting = this.add(new EnumSetting<>("Hand", HandChoice.VANILLA).dependsOn(this.swingSetting));
        this.swingTypeSetting = this.add(new EnumSetting<>("Type", TypeChoice.VANILLA).dependsOn(this.swingSetting));
        Predicate<Boolean> shakeVisibility = ignored -> this.swingTypeSetting.getValue() == TypeChoice.ONE_TWELVE;
        this.swingShakeSetting = this.add(new BooleanSetting("Shake", false, shakeVisibility).dependsOn(this.swingSetting));
        this.swingSpeedSetting = this.add(new NumberSetting<Double>("Speed", 1.0, 0.1, 3.0).dependsOn(this.swingSetting));
        this.swingProgressSetting = this.add(new BooleanSetting("SwingProgress", false).markVisibilityDependency().dependsOn(this.swingSetting));
        this.staticSetting = this.add(new BooleanSetting("Static", true).dependsOn(this.swingSetting, this.swingProgressSetting));
        this.mainHandSwingSetting = this.add(new BooleanSetting("MainHand", false).dependsOn(this.swingSetting, this.swingProgressSetting));
        this.mainHandSwingProgressAmountSetting = this.add(new NumberSetting<>("SwingProgressAmount", 1.0F, 0.0F, 1.0F).dependsOn(this.swingSetting, this.swingProgressSetting, this.mainHandSwingSetting));
        this.offHandSwingSetting = this.add(new BooleanSetting("OffHand", false).dependsOn(this.swingSetting, this.swingProgressSetting));
        this.offHandSwingProgressAmountSetting = this.add(new NumberSetting<>("SwingProgressAmount", 1.0F, 0.0F, 1.0F).dependsOn(this.swingSetting, this.swingProgressSetting, this.offHandSwingSetting));
        this.miscSetting = this.add(new BooleanSetting("Misc", false).markAsGroup().markVisibilityDependency());
        this.eatMultiplierSetting = this.add(new NumberSetting<>("EatMultiplier", 1.0F, 0.0F, 1.0F).dependsOn(this.miscSetting));
        this.noSwaySetting = this.add(new BooleanSetting("NoSway", false).dependsOn(this.miscSetting));
        this.noTridentAnimSetting = this.add(new BooleanSetting("NoTridentAnim", false).dependsOn(this.miscSetting));
        this.viewModelFovSetting = this.add(new BooleanSetting("ViewModelFov", false).markVisibilityDependency().dependsOn(this.miscSetting));
        this.fovAmountSetting = this.add(new NumberSetting<>("FovAmount", 90, 70, 180).dependsOn(this.miscSetting, this.viewModelFovSetting));
        this.scaleVector = new Vector3f(1.0F);
        this.mainHandSwingSetting.setConfigName("SwingMainHand");
        this.offHandSwingSetting.setConfigName("SwingOffHand");
        this.offHandSwingProgressAmountSetting.setConfigName("SwingProgressAmountOffHand");
        this.swingHandSetting.setConfigName("SwingHand");
        this.swingTypeSetting.setConfigName("SwingType");
        this.swingShakeSetting.setConfigName("SwingShake");
        this.swingSpeedSetting.setConfigName("SwingSpeed");
        Setting<Boolean> callbackSetting = this.adjustSetting;
        Runnable callback = () -> {
            Boolean openAdjust = this.adjustSetting.getValue();
            if (openAdjust) {
                Minecraft minecraft = ClientAccess.evernightMinecraft;
                ViewModelEditorScreen screen = new ViewModelEditorScreen(this);
                minecraft.setScreen(screen);
                this.adjustSetting.setValue(false);
            }
        };
        callbackSetting.setChangeCallback(callback);
    }

    public Vector3f getScale(InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            Vector3f mainScale = this.scaleVector;
            float mainX = this.mainScaleXSetting.getValue();
            float mainY = this.mainScaleYSetting.getValue();
            float mainZ = this.mainScaleZSetting.getValue();
            return mainScale.set(mainX, mainY, mainZ);
        } else {
            Vector3f offScale = this.scaleVector;
            float offX = this.offScaleXSetting.getValue();
            float offY = this.offScaleYSetting.getValue();
            float offZ = this.offScaleZSetting.getValue();
            return offScale.set(offX, offY, offZ);
        }
    }

    public float applySwingProgressOverride(InteractionHand hand, float originalProgress) {
        if (!this.isToggled() || !this.swingProgressSetting.getValue()) {
            return originalProgress;
        }
        if (hand == InteractionHand.MAIN_HAND && this.mainHandSwingSetting.getValue()) {
            boolean mainStatic = this.staticSetting.getValue();
            float mainProgress = mainStatic ? 0.0F : originalProgress;
            float mainMinimum = this.mainHandSwingProgressAmountSetting.getValue();
            return Math.max(mainProgress, mainMinimum);
        }
        if (hand == InteractionHand.OFF_HAND && this.offHandSwingSetting.getValue()) {
            boolean offStatic = this.staticSetting.getValue();
            float offProgress = offStatic ? 0.0F : originalProgress;
            float offMinimum = this.offHandSwingProgressAmountSetting.getValue();
            return Math.max(offProgress, offMinimum);
        }
        return originalProgress;
    }

    /**
     * Consumes one queued offhand shake tick when the 1.12 swing type jitter is active. The ticks are
     * queued by {@link #onSwingPacketSent(PacketEvent.Send)} on outgoing swing packets.
     */
    public boolean consumeShakeTick() {
        if (this.shakeTicksRemaining > 0) {
            this.shakeTicksRemaining--;
            return true;
        }
        return false;
    }

    @EventHandler
    private void onSwingPacketSent(PacketEvent.Send event) {
        if (!(event.getPacket() instanceof ServerboundSwingPacket)) {
            return;
        }
        if (System.currentTimeMillis() <= this.nextShakeTimeMillis) {
            return;
        }
        long nextShakeTime = System.currentTimeMillis();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        nextShakeTime += random.nextInt(350);
        this.nextShakeTimeMillis = nextShakeTime + 150L;
        this.shakeTicksRemaining = 3;
    }

    public enum HandChoice implements NamedEntry {
        VANILLA("Vanilla"),
        MAINHAND("Mainhand", InteractionHand.MAIN_HAND),
        OFFHAND("Offhand", InteractionHand.OFF_HAND),
        PACKET("Packet");
        private final InteractionHand hand;
        private final String displayName;

        HandChoice(String displayName, InteractionHand hand) {
            this.displayName = displayName;
            this.hand = hand;
        }

        HandChoice(String displayName) {
            this(displayName, null);
        }

        public InteractionHand getHand() {
            return this.hand;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }

    public enum TypeChoice implements NamedEntry {
        VANILLA("Vanilla"),
        ONE_TWELVE("1.12"),
        ONE_EIGHT("1.8");
        private final String displayName;

        TypeChoice(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }
}