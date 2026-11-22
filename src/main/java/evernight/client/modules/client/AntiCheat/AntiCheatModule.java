package evernight.client.modules.client.AntiCheat;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.network.ConnectScreenConnectEvent;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.bus.EventBus;
import evernight.client.integrations.ProtocolCompatibility;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.PermanentModule;
import evernight.client.modules.movement.Velocity.VelocityModule;
import evernight.client.modules.player.SpeedMine.SpeedMineModule;
import evernight.client.render.text.Translations;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.AntiCheatMiningChoice;
import evernight.client.settings.options.AntiCheatPlacementsChoice;
import evernight.client.settings.options.AntiCheatRotationsChoice;
import evernight.client.settings.options.AntiCheatStrictDirectionChoice;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.utils.entity.PlayerStateTracker;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.network.PacketSender;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class AntiCheatModule extends PermanentModule {
    public static final float ROTATION_JITTER_STEP;
    public static SpeedMineModule speedMineModule;
    public static VelocityModule velocityModule;

    static {
        speedMineModule = ClientContext.getModule(SpeedMineModule.class);
        velocityModule = ClientContext.getModule(VelocityModule.class);
        double exponent = 3.0D;
        double base = 0.2F;
        double basePower = Math.pow(base, exponent);
        double scaledPower = basePower * 8.0D;
        double rotationStep = scaledPower * 0.15D;
        ROTATION_JITTER_STEP = (float) (rotationStep - 0.001D);
    }

    public final Setting<Boolean> value2b2tSetting;
    public final Setting<Boolean> noServerSlotSetting;
    public final Setting<AntiCheatStrictDirectionChoice> strictDirectionSetting;
    public final Setting<AntiCheatMiningChoice> miningModeSetting;
    public final Setting<AntiCheatPlacementsChoice> placementsSetting;
    public final Setting<Boolean> movementSyncSetting;
    public final Setting<AntiCheatRotationsChoice> rotationsSetting;
    public final Setting<Boolean> swingSetting;
    public final Setting<Boolean> placeSetting;
    public final Setting<Boolean> hitSetting;
    public final Setting<Boolean> miningSwingSetting;
    public final Setting<Boolean> assumeBlockBreakingSetting;
    private final Setting<Boolean> limiterSetting;
    private final AntiCheatMovementHandler movementHandler;
    private final PacketRateLimiter packetRateLimiter;
    protected boolean movementYawOffsetEnabled;
    private boolean miningStartedWithoutGrimStrict;
    private boolean silentRotationWarningShown;
    private boolean wasFallFlying;
    private int sneakResyncTicks;

    public AntiCheatModule() {
        super("AntiCheat", ModuleCategory.CLIENT);
        this.value2b2tSetting = this.add(new BooleanSetting("2b2t", false));
        Predicate limiterVisibility = ignored -> this.value2b2tSetting.getValue();
        this.limiterSetting = this.add(new BooleanSetting("Limiter", false, limiterVisibility));
        this.noServerSlotSetting = this.add(new BooleanSetting("NoServerSlot", true));
        this.strictDirectionSetting = this.add(new EnumSetting<>("StrictDirection", AntiCheatStrictDirectionChoice.GRIM));
        this.miningModeSetting = this.add(new EnumSetting<>("Mining", AntiCheatMiningChoice.VANILLA));
        this.placementsSetting = this.add(new EnumSetting<>("Placements", AntiCheatPlacementsChoice.PAPER));
        this.movementSyncSetting = this.add(new BooleanSetting("MovementSync", true).markVisibilityDependency());
        AntiCheatRotationsChoice rotationsDefault = AntiCheatRotationsChoice.SILENT;
        EnumSetting<AntiCheatRotationsChoice> rotationsOption = new EnumSetting<>(("Rotations"), rotationsDefault);
        this.rotationsSetting = this.add(rotationsOption.dependsOn(this.movementSyncSetting));
        this.swingSetting = this.add(new BooleanSetting("Swing", true).markVisibilityDependency());
        this.placeSetting = this.add(new BooleanSetting("Place", true).dependsOn(this.swingSetting));
        this.hitSetting = this.add(new BooleanSetting("Hit", true).dependsOn(this.swingSetting));
        this.miningSwingSetting = this.add(new BooleanSetting("Mining", false).dependsOn(this.swingSetting));
        Predicate assumeBreakingVisibility = ignored -> false;
        this.assumeBlockBreakingSetting = this.add(new BooleanSetting("AssumeBlockBreaking", true, assumeBreakingVisibility));
        this.movementHandler = new AntiCheatMovementHandler(this);
        this.packetRateLimiter = new PacketRateLimiter();
        EventBus eventBus = ClientAccess.eventBus;
        eventBus.subscribe(this);
        this.rotationsSetting.setChangeCallback(this::warnSilentRotationsCompatibility);
        this.movementSyncSetting.setChangeCallback(this::warnSilentRotationsCompatibility);
        Setting<AntiCheatMiningChoice> callbackSetting = this.miningModeSetting;
        Runnable callback = this::warnGrimStrictRequiresReconnect;
        callbackSetting.setChangeCallback(callback);
        this.miningSwingSetting.setConfigName(("MiningSwing"));
    }

    private static BlockPos sourcePosition(ServerboundPlayerActionPacket packet) {
        return packet.getPos();
    }

    private static Direction sourceDirection(ServerboundPlayerActionPacket packet) {
        return packet.getDirection();
    }

    public static void bindReferencedModules(ModuleRegistry modules) {
        speedMineModule = modules.getModule(SpeedMineModule.class);
        velocityModule = modules.getModule(VelocityModule.class);
    }

    public boolean is2b2tEnabled() {
        return this.value2b2tSetting.getValue();
    }

    public boolean isMovementSyncEnabled() {
        return this.movementSyncSetting.getValue();
    }

    public boolean isPacketLimiterActive() {
        return this.is2b2tEnabled() && this.isMovementSyncEnabled() && this.limiterSetting.getValue();
    }

    public int getRecentPacketCount() {
        PacketRateLimiter limiter = this.packetRateLimiter;
        PacketRateLimiter globalLimiter = this.packetRateLimiter;
        return limiter.countRecentPackets(globalLimiter.getGlobalWindow());
    }

    public int getPacketLimit() {
        return this.packetRateLimiter.getGlobalWindow().limit();
    }

    public AntiCheatMovementHandler getMovementHandler() {
        return this.movementHandler;
    }

    public boolean isPaperPlacement() {
        return this.placementsSetting.getValue() == AntiCheatPlacementsChoice.PAPER;
    }

    public boolean isPacketLimitReached() {
        if (this.isPacketLimiterActive()) {
            PacketRateLimiter limiter = this.packetRateLimiter;
            PacketRateLimiter globalLimiter = this.packetRateLimiter;
            return limiter.isLimitReached(globalLimiter.getGlobalWindow());
        }
        return false;
    }

    private boolean shouldResyncSneakingAfterFlight() {
        if (!this.movementSyncSetting.getValue() || this.is2b2tEnabled()) {
            return false;
        }
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (player.isShiftKeyDown()) {
            return false;
        }
        return this.wasFallFlying && !PlayerStateUtils.isFallFlying();
    }

    private void warnGrimStrictRequiresReconnect() {
        if (!this.isWorldUnavailable()) {
            if (this.is2b2tEnabled()) {
                Setting<AntiCheatMiningChoice> miningMode = this.miningModeSetting;
                if (miningMode.getValue() == AntiCheatMiningChoice.GRIM_STRICT) {
                    if (!this.miningStartedWithoutGrimStrict || ClientAccess.evernightMinecraft.isLocalServer()) {
                        return;
                    }
                    Component message = Component.literal(Translations.ui("modules.AntiCheat.GrimStrictReconnect"));
                    MessageSignature signature = ChatUtils.DEFAULT_SIGNATURE;
                    ChatUtils.MessagePriority priority = ChatUtils.MessagePriority.HIGH;
                    ChatUtils.printChat(message, signature, priority);
                }
            }
        }
    }

    private void warnSilentRotationsCompatibility() {
        if (this.rotationsSetting.getValue() != AntiCheatRotationsChoice.SILENT
                || !this.movementSyncSetting.getValue() || ClientAccess.evernightMinecraft.isLocalServer()) {
            return;
        }
        if (ProtocolCompatibility.getTargetVersion().olderEq_1_20_6() || this.silentRotationWarningShown) {
            return;
        }
        String message = Translations.ui("modules.AntiCheat.SilentRotationsWarning");
        ChatUtils.printChat(Component.literal(message), ChatUtils.DEFAULT_SIGNATURE, ChatUtils.MessagePriority.HIGH);
        this.silentRotationWarningShown = true;
    }

    public boolean shouldCancelPacket(Packet packet) {
        return this.packetRateLimiter.shouldCancelPacket(packet);
    }

    private void reverseBundleContents(ItemStack stack) {
        if (stack != null) {
            if (!stack.isEmpty()) {
                DataComponentType<BundleContents> bundleType = DataComponents.BUNDLE_CONTENTS;
                BundleContents bundleContents = stack.get(bundleType);
                DataComponentType<ItemContainerContents> containerType = DataComponents.CONTAINER;
                ItemContainerContents containerContents = stack.get(containerType);
                if (bundleContents != null) {
                    List<ItemStack> items = bundleContents.items;
                    Consumer<ItemStack> action = this::reverseBundleContents;
                    items.forEach(action);
                    Stream<ItemStack> itemCopies = bundleContents.itemCopyStream();
                    List<ItemStack> copiedItems = itemCopies.toList();
                    List<ItemStack> reversedItems = copiedItems.reversed();
                    DataComponentType<BundleContents> replacementType = DataComponents.BUNDLE_CONTENTS;
                    BundleContents replacement = new BundleContents(reversedItems);
                    stack.set(replacementType, replacement);
                } else if (containerContents != null) {
                    net.minecraft.core.NonNullList<ItemStack> items = containerContents.items;
                    Consumer<ItemStack> action = this::reverseBundleContents;
                    items.forEach(action);
                }
            }
        }
    }

    @EventHandler
    private void onLocalPlayerTick(LocalPlayerPreTickEvent event) {
        if (this.shouldResyncSneakingAfterFlight()) {
            this.sneakResyncTicks = 3;
        }
        if (this.sneakResyncTicks > 0) {
            this.sneakResyncTicks--;
            if (this.sneakResyncTicks == 0) {
                PlayerStateTracker playerStateSnapshot = ClientContext.playerStateTracker;
                playerStateSnapshot.setSneaking(true);
                playerStateSnapshot = ClientContext.playerStateTracker;
                playerStateSnapshot.setSneaking(false);
            }
        }
        this.wasFallFlying = PlayerStateUtils.isFallFlying();
    }

    private void handleMiningStartPacket(ServerboundPlayerActionPacket startPacket) {
        Setting<AntiCheatMiningChoice> miningMode = this.miningModeSetting;
        if (miningMode.getValue() == AntiCheatMiningChoice.GRIM_STRICT) {
            int sequence = PacketSender.nextPredictionSequence();
            ServerboundPlayerActionPacket stopPacket = new ServerboundPlayerActionPacket(
                    Action.STOP_DESTROY_BLOCK, sourcePosition(startPacket), sourceDirection(startPacket), sequence);
            PacketSender.markImportant(startPacket);
            PacketSender.markImportant(stopPacket);
            PacketSender.send(stopPacket);
        } else {
            this.miningStartedWithoutGrimStrict = true;
        }
    }

    @EventHandler(priority = -100)
    private void onMiningPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundPlayerActionPacket actionPacket) {
            if (!event.isCancelled()) {
                if (actionPacket.getAction() == Action.START_DESTROY_BLOCK) {
                    this.handleMiningStartPacket(actionPacket);
                }
                if (actionPacket.getAction() == Action.STOP_DESTROY_BLOCK) {
                    Setting<AntiCheatMiningChoice> miningMode = this.miningModeSetting;
                    if (miningMode.getValue() == AntiCheatMiningChoice.GRIM) {
                        ThreadLocalRandom randomSnapshot = ThreadLocalRandom.current();
                        int randomOffset = randomSnapshot.nextInt((50));
                        int offset = randomOffset + 500;
                        Action action = Action.ABORT_DESTROY_BLOCK;
                        BlockPos startPosition = actionPacket.getPos();
                        BlockPos offsetPosition = startPosition.offset(0, offset, 0);
                        Direction abortDirection = actionPacket.getDirection();
                        PacketSender.sendPlayerAction(action, offsetPosition, abortDirection);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onConnect(ConnectScreenConnectEvent event) {
        this.miningStartedWithoutGrimStrict = false;
        this.silentRotationWarningShown = false;
        ClientContext.taskScheduler.scheduleRotationTask(this::warnSilentRotationsCompatibility, 5);
    }

    @EventHandler(priority = 100)
    private void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundPlayerActionPacket actionPacket) {
            if (actionPacket.getAction() == Action.START_DESTROY_BLOCK) {
                Setting<AntiCheatMiningChoice> miningMode = this.miningModeSetting;
                if (miningMode.getValue() == AntiCheatMiningChoice.GRIM_STRICT) {
                    Packet importantPacket = event.getPacket();
                    PacketSender.markImportant(importantPacket);
                }
            }
        }
        if (this.is2b2tEnabled()) {
            Setting<Boolean> packetLimiterSetting = this.limiterSetting;
            Boolean limiterEnabled = packetLimiterSetting.getValue();
            if (limiterEnabled) {
                PacketRateLimiter limiterSnapshot = this.packetRateLimiter;
                Packet packet = event.getPacket();
                if (limiterSnapshot.shouldCancelPacket(packet)) {
                    event.cancel();
                }
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!this.is2b2tEnabled()) {
            return;
        }
        if (event.getPacket() instanceof ClientboundContainerClosePacket closePacket) {
            if (closePacket.getContainerId() == 0) {
                event.cancel();
            }
        }
        if (event.getPacket() instanceof ClientboundContainerSetContentPacket contentsPacket) {
            List<ItemStack> inventoryItems = contentsPacket.items();
            Consumer<ItemStack> action = this::reverseBundleContents;
            inventoryItems.forEach(action);
            ItemStack carriedItem = contentsPacket.carriedItem();
            this.reverseBundleContents(carriedItem);
        }
        if (event.getPacket() instanceof ClientboundContainerSetSlotPacket slotPacket) {
            ItemStack slotItem = slotPacket.getItem();
            this.reverseBundleContents(slotItem);
        }
        if (event.getPacket() instanceof ClientboundSetPlayerInventoryPacket(int slot, ItemStack contents1)) {
            this.reverseBundleContents(contents1);
        }
        if (event.getPacket() instanceof ClientboundSetCursorItemPacket(ItemStack contents)) {
            this.reverseBundleContents(contents);
        }
    }
}