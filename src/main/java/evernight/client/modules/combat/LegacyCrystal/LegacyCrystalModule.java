package evernight.client.modules.combat.LegacyCrystal;

import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.network.MultiPlayerGameModeUseItemOnEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.network.InteractPacketUtils;
import evernight.client.utils.world.BlockInteractionUtils;
import evernight.mixin.minecraft.client.MinecraftAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import java.util.HashMap;
import java.util.Map;

public class LegacyCrystalModule extends Module {
    private final Setting<Integer> delaySetting;
    private final Setting<Boolean> optimizerSetting;
    private final Map<BlockPos, Long> placementTimes;
    private final ElapsedTimer attackTimer;
    private long attackDelayMillis;

    public LegacyCrystalModule() {
        super("LegacyCrystal", ModuleCategory.COMBAT);
        this.delaySetting = this.add(new NumberSetting<>("Delay", 150, 0, 250));
        this.optimizerSetting = this.add(new BooleanSetting("Optimizer", false));
        this.placementTimes = new HashMap<>();
        this.attackTimer = new ElapsedTimer();
        this.attackDelayMillis = 150L;
    }

    @EventHandler
    private void onLocalPlayerTick(LocalPlayerPreTickEvent event) {
        synchronized (this.placementTimes) {
            this.placementTimes.entrySet().removeIf(entry -> {
                Long placedAt = entry.getValue();
                long expiryTime = placedAt;
                expiryTime += 15000L;
                return expiryTime < System.currentTimeMillis();
            });
        }
        if (ClientAccess.evernightMinecraft.hitResult instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            long delay = this.attackDelayMillis;
            if (this.attackTimer.hasElapsed(delay) && entity instanceof EndCrystal) {
                if (this.placementTimes.containsKey(entity.blockPosition().below())) {
                    ((MinecraftAccessor) ClientAccess.evernightMinecraft).attack();
                    double randomDelay = Math.random();
                    Integer maximumDelay = this.delaySetting.getValue();
                    this.attackDelayMillis = (long) (randomDelay * maximumDelay.intValue());
                    this.attackTimer.reset();
                }
            }
        }
    }

    @EventHandler
    private void onUseItemOn(MultiPlayerGameModeUseItemOnEvent event) {
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        BlockHitResult hit = event.getHitResult();
        BlockPos hitPosition = hit.getBlockPos();
        BlockPos abovePosition = hitPosition.above(2);
        BlockState aboveState = level.getBlockState(abovePosition);
        if (aboveState.isAir()) {
            BlockHitResult placementHit = event.getHitResult();
            BlockPos placementPosition = placementHit.getBlockPos();
            boolean canPlaceCrystal = BlockInteractionUtils.canPlaceCrystal(placementPosition, false, false, true, false, false, false);
            ItemStack mainHand = ClientAccess.evernightMinecraft.player.getMainHandItem();
            DataComponentType<?> weaponComponent = DataComponents.WEAPON;
            if (mainHand.has(weaponComponent)) {
                Item item = canPlaceCrystal ? Items.END_CRYSTAL : Items.OBSIDIAN;
                int slot = InventoryUtils.findHotbarSlot(item);
                InventoryUtils.selectHotbarSlotForPlacement(slot);
            } else {
                ItemStack heldStack = ClientAccess.evernightMinecraft.player.getMainHandItem();
                Item obsidian = Items.OBSIDIAN;
                if (heldStack.is(obsidian) && canPlaceCrystal) {
                    Item crystal = Items.END_CRYSTAL;
                    int slot = InventoryUtils.findHotbarSlot(crystal);
                    InventoryUtils.selectHotbarSlot(slot);
                }
            }
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundUseItemOnPacket useItem) {
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            InteractionHand hand = useItem.getHand();
            ItemStack heldStack = player.getItemInHand(hand);
            Item crystal = Items.END_CRYSTAL;
            if (heldStack.is(crystal)) {
                Map<BlockPos, Long> placements = this.placementTimes;
                BlockHitResult hit = useItem.getHitResult();
                BlockPos position = hit.getBlockPos();
                placements.compute(position, (ignoredKey, ignoredValue) -> System.currentTimeMillis());
            }
        }
        if (event.getPacket() instanceof ServerboundInteractPacket interaction) {
            if (this.optimizerSetting.getValue()) {
                if (InteractPacketUtils.getAction(interaction) == InteractPacketUtils.Action.ATTACK) {
                    if (InteractPacketUtils.getEntity(interaction) instanceof EndCrystal) {
                        Entity crystal = InteractPacketUtils.getEntity(interaction);
                        RemovalReason reason = RemovalReason.KILLED;
                        crystal.setRemoved(reason);
                    }
                }
            }
        }
    }
}