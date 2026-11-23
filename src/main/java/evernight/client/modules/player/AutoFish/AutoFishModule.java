package evernight.client.modules.player.AutoFish;

import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.combat.BlockRaycastClassifier;
import evernight.client.utils.combat.BlockRaycastContext;
import evernight.client.utils.combat.BlockRaycastOverride;
import evernight.client.utils.combat.RaycastUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class AutoFishModule extends Module {
    private final Setting<Boolean> waterSetting;
    private final Setting<Float> recastDelaySetting;
    private final ElapsedTimer castTimer;
    private boolean nearbySplashDetected;

    public AutoFishModule() {
        super("AutoFish", ModuleCategory.AUTO);
        Boolean defaultWater = false;
        this.waterSetting = this.add(new BooleanSetting("Water", defaultWater));
        this.recastDelaySetting = this.add(new NumberSetting<>("RecastDelay", 2.0F, 1.0F, 5.0F).withUnit("s"));
        this.castTimer = new ElapsedTimer();
    }

    @Override
    public void onEnable() {
        this.castTimer.reset();
    }

    private void castIfNoHook(InteractionHand hand) {
        if (ClientAccess.evernightMinecraft.player.fishing == null) {
            this.useRod(hand);
        }
        this.nearbySplashDetected = false;
    }

    private void useRod(InteractionHand hand) {
        MultiPlayerGameMode gameMode = ClientAccess.evernightMinecraft.gameMode;
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        gameMode.useItem(player, hand);
        ClientAccess.evernightMinecraft.player.swing(hand);
        this.castTimer.reset();
    }

    @EventHandler
    private void updateFishing(LocalPlayerPreTickEvent event) {
        ItemStack capturedOffhand = ClientAccess.evernightMinecraft.player.getOffhandItem();
        Item offhandRod = Items.FISHING_ROD;
        InteractionHand rodHand = capturedOffhand.is(offhandRod) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        Item rod = Items.FISHING_ROD;
        LocalPlayer holdingPlayer = ClientAccess.evernightMinecraft.player;
        if (!holdingPlayer.isHolding(rod)) {
            return;
        }
        if (this.nearbySplashDetected) {
            this.castIfNoHook(rodHand);
        }
        if ((this.waterSetting.getValue()) && this.isWaterTargetMissing()) {
            return;
        }
        ElapsedTimer capturedFallbackTimer = this.castTimer;
        TimeUnit fallbackUnit = TimeUnit.MINUTES;
        if (!capturedFallbackTimer.hasElapsed(1.0D, fallbackUnit)) {
            if (ClientAccess.evernightMinecraft.player.fishing != null) {
                return;
            }
            ElapsedTimer capturedRecastTimer = this.castTimer;
            double recastSeconds = (this.recastDelaySetting.getValue()).floatValue();
            TimeUnit recastUnit = TimeUnit.SECONDS;
            if (!capturedRecastTimer.hasElapsed(recastSeconds, recastUnit)) {
                return;
            }
        }
        this.useRod(rodHand);
    }

    @EventHandler
    private void detectNearbySplash(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundSoundPacket soundPacket) {
            var sound = soundPacket.getSound();
            Predicate<ResourceKey<SoundEvent>> splashSound = key -> key.identifier().toString()
                    .equalsIgnoreCase("minecraft:entity.fishing_bobber.splash");
            if (sound.is(splashSound) && ClientAccess.evernightMinecraft.player.fishing != null) {
                Vec3 capturedSoundPosition = new Vec3(soundPacket.getX(), soundPacket.getY(), soundPacket.getZ());
                Vec3 hookPosition = ClientAccess.evernightMinecraft.player.fishing.position();
                if (capturedSoundPosition.distanceTo(hookPosition) <= 1.0D) {
                    this.nearbySplashDetected = true;
                }
            }
        }
    }

    private boolean isWaterTargetMissing() {
        Vec3 lookDirection = ClientAccess.evernightMinecraft.player.getLookAngle();
        Vec3 eyePosition = ClientAccess.evernightMinecraft.player.getEyePosition();
        BlockRaycastContext.Builder builder = new BlockRaycastContext.Builder(
                eyePosition, eyePosition.add(lookDirection.scale(8.0D)));
        BlockRaycastClassifier waterClassifier = (position, blockState) -> {
            Block waterBlock = Blocks.WATER;
            return blockState.is(waterBlock) ? BlockRaycastOverride.SOLID : BlockRaycastOverride.NONE;
        };
        builder.withClassifier(waterClassifier);
        BlockRaycastContext context = builder.build();
        if (!(RaycastUtils.raycast(context) instanceof BlockHitResult hit) || hit.getType() == Type.MISS) {
            return true;
        }
        ClientLevel capturedLevel = ClientAccess.evernightMinecraft.level;
        BlockPos hitPosition = hit.getBlockPos();
        BlockState state = capturedLevel.getBlockState(hitPosition);
        Block water = Blocks.WATER;
        return !state.is(water);
    }
}