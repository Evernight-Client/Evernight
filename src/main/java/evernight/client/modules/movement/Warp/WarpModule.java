package evernight.client.modules.movement.Warp;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.player.LocalPlayerSendPositionEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.managers.player.TickRateController;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.options.WarpRechargeChoice;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;
import evernight.client.modules.movement.FakeLag.FakeLagModule;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class WarpModule extends Module {
    private static FakeLagModule fakeLag;
    public final Setting<Integer> boostSetting;
    public final Setting<Integer> chargeSetting;
    protected final Setting<ModeChoice> modeSetting;
    protected final Setting<Integer> chargeSpeedSetting;
    protected final Setting<WarpRechargeChoice> rechargeSetting;
    protected final Setting<Boolean> fakeLagSetting;
    protected final Setting<Boolean> disableGroundSetting;
    protected final Setting<Boolean> autoDisableSetting;
    protected final WarpRechargeController rechargeController;
    protected boolean receivedCorrection;
    protected volatile int plainCharge;

    public WarpModule() {
        super("Warp", ModuleCategory.MOVEMENT, "tickshift");
        this.modeSetting = this.add(new EnumSetting<>("Mode", WarpModule.ModeChoice.PLAIN));
        this.boostSetting = this.add(new NumberSetting<>("Boost", 2, 1, 50));
        this.chargeSetting = this.add(new NumberSetting<>("Charge", 20, 1, 100));
        Predicate<Object> chargeSpeedVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.PLAIN;
        this.chargeSpeedSetting = this.add(new NumberSetting<>("ChargeSpeed", 1, 1, 4, chargeSpeedVisibility));
        Predicate<Object> rechargeVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.PLAIN;
        this.rechargeSetting = this.add(new EnumSetting<>("Recharge", WarpRechargeChoice.NONE, rechargeVisibility));
        this.fakeLagSetting = this.add(new BooleanSetting("FakeLag", false).markVisibilityDependency());
        this.disableGroundSetting = this.add(new BooleanSetting("DisableGround", false).dependsOn(this.fakeLagSetting));
        this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", false));
        this.rechargeController = new WarpRechargeController(this);
    }

    public static void bindReferencedModules(ModuleRegistry registry) {
        fakeLag = registry.getModule(FakeLagModule.class);
    }

    public int getCharge() {
        return this.modeSetting.getValue() == WarpModule.ModeChoice.ALTERNATIVE
                ? this.rechargeController.pendingPongs.size() : this.plainCharge;
    }

    private void updatePlainWarp() {
        if (!this.hasDisplacement()) {
            ClientContext.tickRateController.releaseMultiplier(this);
            this.setFakeLagEnabled(false);
            this.receivedCorrection = false;
        } else if (this.plainCharge == 0 || this.receivedCorrection) {
            ClientContext.tickRateController.releaseMultiplier(this);
            if (this.autoDisableSetting.getValue()) {
                this.toggle();
            }
            this.setFakeLagEnabled(false);
        } else if (this.plainCharge > 0) {
            if (this.fakeLagSetting.getValue()) {
                boolean wasFakeLagEnabled = fakeLag.isToggled();
                LocalPlayer groundPlayer = ClientAccess.evernightMinecraft.player;
                boolean enableFakeLag = true;
                if (!groundPlayer.onGround()) {
                    Boolean requireGround = this.disableGroundSetting.getValue();
                    enableFakeLag = !requireGround;
                }
                this.setFakeLagEnabled(enableFakeLag);
                if (!wasFakeLagEnabled) {
                    return;
                }
            }
            TickRateController boostControllerSnapshot = ClientContext.tickRateController;
            float boost = (this.boostSetting.getValue()).intValue();
            boostControllerSnapshot.requestMultiplier(this, boost);
        }
    }

    protected boolean hasMovementInputOrDisplacement() {
        if (ClientAccess.evernightMinecraft.player.input.getMoveVector().length() > 0.0F) {
            return true;
        }
        if (ClientAccess.evernightMinecraft.player.getX() - ClientAccess.evernightMinecraft.player.xo != 0.0) {
            return true;
        }
        if (ClientAccess.evernightMinecraft.player.getY() - ClientAccess.evernightMinecraft.player.yo != 0.0) {
            return true;
        }
        return ClientAccess.evernightMinecraft.player.getZ() - ClientAccess.evernightMinecraft.player.zo != 0.0;
    }

    private void updateAlternativeWarp() {
        if (!this.rechargeController.pendingPongs.isEmpty()) {
            if (this.hasMovementInputOrDisplacement()) {
                TickRateController.PlayerTicks boostControllerSnapshot = ClientContext.playerTickController;
                float boost = (this.boostSetting.getValue()).intValue();
                boostControllerSnapshot.requestMultiplier(this, boost);
                ClientCommonPacketListenerImpl connection = ClientAccess.evernightMinecraft.player.connection;
                Packet<?> packet = this.rechargeController.pendingPongs.poll();
                connection.send(packet);
                return;
            }
        }
        if (!this.rechargeController.pendingPongs.isEmpty() && !this.receivedCorrection) {
            ClientContext.playerTickController.releaseMultiplier(this);
        } else {
            ClientContext.playerTickController.releaseMultiplier(this);
            if (this.autoDisableSetting.getValue()) {
                this.toggle();
            }
        }
    }

    @Override
    public String getInfo() {
        return String.valueOf(this.getCharge());
    }

    @Override
    public void onEnable() {
        this.receivedCorrection = false;
        if (this.rechargeSetting.getValue() == WarpRechargeChoice.INSTANT) {
            Integer instantCharge = this.chargeSetting.getValue();
            this.plainCharge = instantCharge;
        }
    }

    @Override
    public void onDisable() {
        this.setFakeLagEnabled(false);
        if (this.modeSetting.getValue() == WarpModule.ModeChoice.ALTERNATIVE && !this.isWorldUnavailable()) {
            try {
                while (!this.rechargeController.pendingPongs.isEmpty()) {
                    ClientCommonPacketListenerImpl connection = ClientAccess.evernightMinecraft.player.connection;
                    Packet<?> packet = this.rechargeController.pendingPongs.poll();
                    connection.send(packet);
                }
            } catch (Exception ignored) {
            }
            this.rechargeController.pendingPongs.clear();
        }
    }

    private void setFakeLagEnabled(boolean enabled) {
        if (this.fakeLagSetting.getValue() && ClientAccess.evernightMinecraft.player != null) {
            fakeLag.setToggled(enabled);
        }
    }

    boolean hasDisplacement() {
        Vec3 positionSnapshot = ClientAccess.evernightMinecraft.player.position();
        double previousX = ClientAccess.evernightMinecraft.player.xo;
        double previousY = ClientAccess.evernightMinecraft.player.yo;
        double previousZ = ClientAccess.evernightMinecraft.player.zo;
        return positionSnapshot.distanceToSqr(previousX, previousY, previousZ) > 0.0;
    }

    @EventHandler
    private void onIncomingPacket(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            this.receivedCorrection = true;
        }
    }

    @EventHandler
    private void onSendPosition(LocalPlayerSendPositionEvent event) {
        if (EventStage.Pre == event.getStage()) {
            if (this.modeSetting.getValue() == WarpModule.ModeChoice.ALTERNATIVE) {
                this.updateAlternativeWarp();
            } else {
                this.updatePlainWarp();
            }
        }
    }

    public enum ModeChoice implements NamedEntry {
        PLAIN("Plain"),
        ALTERNATIVE("Alternative");
        private final String displayName;

        ModeChoice(String name) {
            this.displayName = name;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }
}