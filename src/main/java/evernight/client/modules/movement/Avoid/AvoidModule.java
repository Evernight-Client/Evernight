package evernight.client.modules.movement.Avoid;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.player.AbstractClientPlayerTickEvent;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.managers.player.TickRateController;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.font.FontRenderer;
import evernight.client.utils.entity.PlayerStateUtils;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class AvoidModule extends Module {
    private final Setting<Boolean> unloadSetting;
    private final Setting<Float> timerSetting;
    private final Setting<Boolean> voidSetting;
    private final Setting<ModeChoice> modeSetting;
    private final Setting<Integer> heightSetting;

    public AvoidModule() {
        super("Avoid", ModuleCategory.MOVEMENT, "AntiVoid");
        this.unloadSetting = this.add(new BooleanSetting("Unload", false).markVisibilityDependency());
        this.timerSetting = this.add(new NumberSetting<>("Timer", 0.25F, 0.1F, 0.5F).dependsOn(this.unloadSetting));
        this.voidSetting = this.add(new BooleanSetting("Void", true).markVisibilityDependency());
        this.modeSetting = this.add(new EnumSetting<>("Mode", AvoidModule.ModeChoice.CANCEL).dependsOn(this.voidSetting));
        this.heightSetting = this.add(new NumberSetting<>("Height", 30, 1, 100).withUnit("m").dependsOn(this.voidSetting));
        this.setDrawn(false);
    }

    @Override
    public String getInfo() {
        if (!this.voidSetting.getValue()) {
            return null;
        }
        return FontRenderer.capitalize(this.modeSetting.getValue());
    }

    @Override
    public void onDisable() {
        ClientContext.tickRateController.releaseMultiplier(this);
    }

    @EventHandler
    private void onClientPlayerTick(AbstractClientPlayerTickEvent event) {
        if (this.unloadSetting.getValue() && !this.areNearbyChunksLoaded()) {
            TickRateController timer = ClientContext.tickRateController;
            Float multiplier = this.timerSetting.getValue();
            timer.requestMultiplier(this, multiplier);
        } else {
            TickRateController timer = ClientContext.tickRateController;
            timer.releaseMultiplier(this);
        }
    }

    private boolean areNearbyChunksLoaded() {
        ClientChunkCache chunks = ClientAccess.evernightMinecraft.level.getChunkSource();
        ChunkPos playerChunk = ClientAccess.evernightMinecraft.player.chunkPosition();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (!chunks.hasChunk(playerChunk.x + x, playerChunk.z + z)) {
                    return false;
                }
            }
        }
        return true;
    }

    @EventHandler
    private void onPlayerTick(LocalPlayerTickEvent event) {
        if (this.voidSetting.getValue()) {
            this.preventVoidFall();
        }
    }

    private void preventVoidFall() {
        if (ClientAccess.evernightMinecraft.player.onGround()) {
            return;
        }
        if (PlayerStateUtils.getTrackedFallDistance() <= 0.0F) {
            return;
        }
        ClientLevel rayLevel = ClientAccess.evernightMinecraft.level;
        ClipBlockStateContext rayContext = new ClipBlockStateContext(
                ClientAccess.evernightMinecraft.player.position(),
                new Vec3(ClientAccess.evernightMinecraft.player.getX(), ClientAccess.evernightMinecraft.level.getMinY() - 1, ClientAccess.evernightMinecraft.player.getZ()),
                state -> state.isSolid());
        if (rayLevel.isBlockInLine(rayContext).getType() != Type.MISS) {
            return;
        }
        int minimumY = ClientAccess.evernightMinecraft.level.getMinY();
        Integer configuredHeight = this.heightSetting.getValue();
        double triggerY = minimumY + configuredHeight;
        if (triggerY < ClientAccess.evernightMinecraft.player.getY()) {
            return;
        }
        if (this.modeSetting.getValue() == AvoidModule.ModeChoice.TELEPORT) {
            ClientAccess.evernightMinecraft.player.setDeltaMovement(0.0, 10.0D, 0.0);
        } else {
            LocalPlayer stopPlayer = ClientAccess.evernightMinecraft.player;
            Vec3 stoppedVelocity = stopPlayer.getDeltaMovement().with(Axis.Y, 0.0);
            stopPlayer.setDeltaMovement(stoppedVelocity);
            ClientAccess.evernightMinecraft.player.setOnGround(true);
        }
    }

    public enum ModeChoice implements NamedEntry {
        TELEPORT("Teleport"),
        CANCEL("Cancel");
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