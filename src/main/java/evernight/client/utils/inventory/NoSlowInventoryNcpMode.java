package evernight.client.utils.inventory;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventStage;
import evernight.client.events.player.InventoryActionEvent;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.exploit.MultiTask.MultiTaskModule;
import evernight.client.modules.movement.NoSlow.NoSlowModule;
import evernight.client.settings.options.MultiTaskModeChoice;
import evernight.client.settings.options.NoSlowModeChoice;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.entity.PlayerStateTracker;
import evernight.client.utils.movement.MovementPhysics;
import evernight.client.utils.network.PacketSender;
import evernight.mixin.minecraft.network.ServerboundMovePlayerPacketAccessor;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;

public class NoSlowInventoryNcpMode extends NoSlowInventoryMode {
    private static MultiTaskModule multiTask;

    static {
        multiTask = ClientContext.getModule(MultiTaskModule.class);
    }

    private final ElapsedTimer airborneTimer = new ElapsedTimer();
    private boolean groundSpoofPending;
    private boolean positionOffsetSent;

    public NoSlowInventoryNcpMode(NoSlowModule noSlow) {
        super(noSlow);
    }

    public static void bindReferencedModules(ModuleRegistry registry) {
        multiTask = registry.getModule(MultiTaskModule.class);
    }

    @Override
    public void onInventoryAction(InventoryActionEvent event) {
        Boolean guiMoveEnabled = this.noSlow.guiMoveSetting.getValue();
        if (!guiMoveEnabled) return;
        NoSlowModule guiModule = this.noSlow;
        if (!guiModule.isGuiMoveAllowed()) return;
        if (event.getStage() == EventStage.Pre) {
            boolean releaseItem = true;
            if (multiTask.getMode() == MultiTaskModeChoice.ALL) {
                releaseItem = this.noSlow.modeSetting.getValue() == NoSlowModeChoice.CONSTANTIAM;
            }
            if (releaseItem && !this.noSlow.releasedUsingItemThisTick) {
                MultiPlayerGameMode gameMode = ClientAccess.evernightMinecraft.gameMode;
                LocalPlayer releasePlayer = ClientAccess.evernightMinecraft.player;
                gameMode.releaseUsingItem(releasePlayer);
                this.noSlow.releasedUsingItemThisTick = true;
            }
            if (!MovementPhysics.isLocalPlayerMoving()) return;
            if (ClientContext.playerStateTracker.getGroundedTicks() > 1 && !this.positionOffsetSent) {
                double x = ClientAccess.evernightMinecraft.player.getX();
                double offsetY = ClientAccess.evernightMinecraft.player.getY() + 0.0626D;
                double z = ClientAccess.evernightMinecraft.player.getZ();
                PacketSender.sendPosition(x, offsetY, z, false);
                this.positionOffsetSent = true;
                this.groundSpoofPending = true;
            }
            LocalPlayer sprintPlayer = ClientAccess.evernightMinecraft.player;
            if (sprintPlayer.isSprinting()) {
                ServerboundPlayerCommandPacket stopSprinting = new ServerboundPlayerCommandPacket(ClientAccess.evernightMinecraft.player, Action.STOP_SPRINTING);
                PacketSender.send(stopSprinting);
            }
            LocalPlayer shiftPlayer = ClientAccess.evernightMinecraft.player;
            if (shiftPlayer.isShiftKeyDown()) {
                PlayerStateTracker shiftState = ClientContext.playerStateTracker;
                shiftState.setSneaking(false);
            }
        } else if (event.getStage() == EventStage.Post && MovementPhysics.isLocalPlayerMoving()) {
            LocalPlayer sprintPlayer = ClientAccess.evernightMinecraft.player;
            if (sprintPlayer.isSprinting()) {
                ServerboundPlayerCommandPacket startSprinting = new ServerboundPlayerCommandPacket(ClientAccess.evernightMinecraft.player, Action.START_SPRINTING);
                PacketSender.send(startSprinting);
            }
            LocalPlayer shiftPlayer = ClientAccess.evernightMinecraft.player;
            if (shiftPlayer.isShiftKeyDown()) {
                PlayerStateTracker shiftState = ClientContext.playerStateTracker;
                shiftState.setSneaking(true);
            }
        }
    }

    @Override
    public void onTickStart(LocalPlayerPreTickEvent event) {
        LocalPlayer groundPlayer = ClientAccess.evernightMinecraft.player;
        if (!groundPlayer.onGround()) {
            this.airborneTimer.reset();
        }
        this.positionOffsetSent = false;
    }

    @Override
    public void onPacketSend(PacketEvent.Send event) {
        if (!(event.getPacket() instanceof ServerboundMovePlayerPacket packet)) return;
        ServerboundMovePlayerPacketAccessor accessor = (ServerboundMovePlayerPacketAccessor) packet;
        Boolean guiMoveEnabled = this.noSlow.guiMoveSetting.getValue();
        if (!guiMoveEnabled || !this.groundSpoofPending) return;
        if (ClientAccess.evernightMinecraft.player.isHandsBusy()) return;
        LocalPlayer groundPlayer = ClientAccess.evernightMinecraft.player;
        if (!groundPlayer.onGround()) return;
        ElapsedTimer timer = this.airborneTimer;
        if (!timer.hasElapsed(200L)) return;
        if (ClientAccess.evernightMinecraft.player.isFallFlying()) return;
        accessor.setOnGround(false);
        this.groundSpoofPending = false;
    }
}