package evernight.client.modules.movement.Jesus;

import evernight.client.ClientAccess;
import evernight.client.events.world.BlockCollisionShapeEvent;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.world.WorldUtils;
import evernight.mixin.minecraft.network.ServerboundMovePlayerPacketAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Predicate;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class JesusModule extends Module {
    final Setting<ModeChoice> modeSetting;
    private final Setting<Boolean> strictSetting;
    private final Setting<Float> ascendingSetting;
    private final double[] strictYOffsetSequence;
    private int strictOffsetIndex;

    public JesusModule() {
        super("Jesus", ModuleCategory.MOVEMENT);
        this.modeSetting = this.add(new EnumSetting<>("Mode", JesusModule.ModeChoice.DOLPHIN));
        Predicate<Object> showStrict = ignored -> this.modeSetting.getValue() == ModeChoice.SOLID;
        this.strictSetting = this.add(new BooleanSetting("Strict", false, showStrict));
        Predicate<Object> showAscending = ignored -> this.modeSetting.getValue() == ModeChoice.SOLID;
        this.ascendingSetting = this.add(new NumberSetting<>("Ascending", 1.0F, 0.1F, 1.0F, showAscending));
        this.strictYOffsetSequence = new double[]{
                0.08232659236482401D, 0.13927999979019162D, 0.1654239999427795D,
                0.11427136035293574D, 0.04194693730418576D, 0.01236341326161235D
        };
    }

    @Override
    public String getInfo() {
        return (this.modeSetting.getValue()).getName();
    }

    @Override
    public void onToggle() {
        this.strictOffsetIndex = 0;
    }

    private boolean isStableAboveWater() {
        if (Math.abs(ClientAccess.evernightMinecraft.player.yo - ClientAccess.evernightMinecraft.player.getY()) > 0.01D) {
            return false;
        }
        for (BlockPos position : PlayerStateUtils.getOccupiedBlockPositions(ClientAccess.evernightMinecraft.player)) {
            if (WorldUtils.getBlock(position.below()) != Blocks.WATER) {
                return false;
            }
        }
        return true;
    }

    public boolean canWalkOnWater() {
        if (this.isWorldUnavailable() || ClientAccess.evernightMinecraft.player.input == null) {
            return false;
        }
        if (ClientAccess.evernightMinecraft.player.isPassenger()) {
            return false;
        }
        return !ClientAccess.evernightMinecraft.player.input.keyPresses.shift();
    }

    @EventHandler
    private void onPlayerTick(LocalPlayerTickEvent event) {
        if (!this.canWalkOnWater() || !ClientAccess.evernightMinecraft.player.isInWater()) {
            return;
        }
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        double upwardImpulse = this.modeSetting.getValue() == ModeChoice.DOLPHIN
                ? 0.0399D : 0.08D * this.ascendingSetting.getValue();
        player.push(0.0, upwardImpulse, 0.0);
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (this.modeSetting.getValue() != JesusModule.ModeChoice.SOLID || !this.canWalkOnWater()) {
            return;
        }
        if (!(event.getPacket() instanceof ServerboundMovePlayerPacket movementPacket)) {
            return;
        }
        if (!this.strictSetting.getValue() || !this.isStableAboveWater()) {
            return;
        }
        ServerboundMovePlayerPacketAccessor packetAccessor = (ServerboundMovePlayerPacketAccessor) movementPacket;
        packetAccessor.setOnGround(false);
        packetAccessor.setY(movementPacket.getY(0.0) + this.strictYOffsetSequence[this.strictOffsetIndex++]);
        this.strictOffsetIndex %= this.strictYOffsetSequence.length;
    }

    @EventHandler
    private void onCollisionShape(BlockCollisionShapeEvent event) {
        if (this.isWorldUnavailable()) {
            return;
        }
        if (ClientAccess.evernightMinecraft.player.isInWater()) {
            return;
        }
        if (this.modeSetting.getValue() != JesusModule.ModeChoice.SOLID || !this.canWalkOnWater()) {
            return;
        }
        float blockYAsFloat = event.getPos().getY();
        double liquidSurfaceY = blockYAsFloat + 0.2F;
        if (liquidSurfaceY > ClientAccess.evernightMinecraft.player.getY()) {
            return;
        }
        if (event.getState().is(Blocks.WATER)) {
            AABB waterBounds = new AABB(0.0, 0.0, 0.0, 1.0D, 0.98D, 1.0D);
            VoxelShape waterShape = Shapes.create(waterBounds);
            event.setShape(waterShape);
        }
    }

    public enum ModeChoice implements NamedEntry {
        SOLID("Solid"),
        DOLPHIN("Dolphin");
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