package evernight.client.managers.player;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.player.CrystalPlacementEvent;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.bus.EventBus;
import evernight.client.modules.client.AntiCheat.AntiCheatModule;
import evernight.client.modules.render.ViewModel.ViewModelModule;
import evernight.client.modules.render.ViewModel.ViewModelModule.HandChoice;
import evernight.client.settings.core.Setting;
import evernight.client.utils.combat.BoundingBoxUtils;
import evernight.client.utils.entity.SwingAction;
import evernight.client.utils.network.PacketSender;
import evernight.mixin.minecraft.entity.LivingEntityAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SwingInteractionManager implements ClientAccess {
    private static final AntiCheatModule antiCheat;
    private static final ViewModelModule viewModelModule;

    static {
        antiCheat = ClientContext.getModule(AntiCheatModule.class);
        viewModelModule = ClientContext.getModule(ViewModelModule.class);
    }

    private final List<PendingCrystalPlacement> pendingCrystals;

    public SwingInteractionManager() {
        List<PendingCrystalPlacement> pending = new ArrayList<>(initialCrystalCapacity());
        this.pendingCrystals = Collections.synchronizedList(pending);
        EventBus eventBus = ClientAccess.eventBus;
        eventBus.subscribe(this);
    }

    private static int initialCrystalCapacity() {
        return 10;
    }

    private static BlockPos capturePlacementPosition(PendingCrystalPlacement pending) {
        return pending.position();
    }

    public boolean tryTrackCrystalPlacement(BlockPos position) {
        if (this.isCrystalPlacementLimited()) {
            return false;
        }
        this.pendingCrystals.add(new PendingCrystalPlacement(position, System.currentTimeMillis() + 300L));
        ClientAccess.eventBus.post(new CrystalPlacementEvent(position));
        return true;
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        this.pendingCrystals.removeIf(pending -> {
            long expiresAt = pending.expiresAtMillis();
            return expiresAt <= System.currentTimeMillis();
        });
    }

    public void swing(InteractionHand hand, SwingAction action, boolean packetOnly) {
        AntiCheatModule antiCheatSnapshot = antiCheat;
        if (!action.isEnabled(antiCheatSnapshot)) {
            if (!packetOnly) {
                this.swingClientSide(hand);
            }
        } else {
            if (packetOnly) {
                PacketSender.sendSwing(hand);
            } else {
                LocalPlayer player = ClientAccess.evernightMinecraft.player;
                player.swing(hand);
            }
        }
    }

    public void swing(InteractionHand hand, SwingAction action) {
        boolean packetOnly = false;
        this.swing(hand, action, packetOnly);
    }

    public void swingPacket(InteractionHand hand, SwingAction action) {
        boolean packetOnly = true;
        this.swing(hand, action, packetOnly);
    }

    public void swingClientSide(InteractionHand hand) {
        if (ClientAccess.evernightMinecraft.player.swinging) {
            int swingTime = ClientAccess.evernightMinecraft.player.swingTime;
            int swingDuration = ((LivingEntityAccessor) ClientAccess.evernightMinecraft.player)
                    .getSwingDuration();
            if (swingTime < swingDuration / 2 && ClientAccess.evernightMinecraft.player.swingTime >= 0) {
                return;
            }
        }
        ClientAccess.evernightMinecraft.player.swingTime = -1;
        ClientAccess.evernightMinecraft.player.swinging = true;
        ViewModelModule moduleSnapshot = viewModelModule;
        if (moduleSnapshot.isToggled()) {
            Setting<HandChoice> handSetting = viewModelModule.swingHandSetting;
            HandChoice handChoice = handSetting.getValue();
            if (handChoice.getHand() != null) {
                LocalPlayer player = ClientAccess.evernightMinecraft.player;
                player.swingingArm = handChoice.getHand();
                return;
            }
        }
        ClientAccess.evernightMinecraft.player.swingingArm = hand;
    }

    public boolean isCrystalPlacementLimited() {
        AntiCheatModule antiCheatSnapshot = antiCheat;
        if (!antiCheatSnapshot.isPaperPlacement()) {
            return false;
        }
        int pendingCount = this.pendingCrystals.size();
        return pendingCount >= 9;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        PacketEvent.Receive packetEvent = event;
        if (packetEvent.getPacket() instanceof ClientboundAddEntityPacket packet) {
            if (packet.getType() == EntityType.END_CRYSTAL) {
                EntityType spawnTypeSnapshot = packet.getType();
                double spawnXSnapshot = packet.getX();
                double spawnYSnapshot = packet.getY();
                double spawnZ = packet.getZ();
                AABB spawnBoundsSnapshot = spawnTypeSnapshot.getSpawnAABB(spawnXSnapshot, spawnYSnapshot, spawnZ);
                double inset = -BoundingBoxUtils.blockCornerInset;
                AABB crystalBounds = spawnBoundsSnapshot.inflate(inset);
                BlockableEventLoop<?> executor = ClientAccess.evernightMinecraft;
                Runnable removePlacement = () -> this.pendingCrystals.removeIf(pending -> {
                    AABB placementBounds = new AABB(capturePlacementPosition(pending));
                    return crystalBounds.intersects(placementBounds);
                });
                executor.execute(removePlacement);
            }
        }
    }

    private record PendingCrystalPlacement(BlockPos position, long expiresAtMillis) {
    }
}