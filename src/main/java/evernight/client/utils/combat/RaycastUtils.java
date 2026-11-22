package evernight.client.utils.combat;

import evernight.client.ClientAccess;
import evernight.client.modules.exploit.Reach.ReachModule;
import evernight.client.render.util.RenderUtils;
import evernight.client.utils.math.MathConstants;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class RaycastUtils implements ClientAccess {
    private RaycastUtils() {
    }

    public static BlockHitResult raycast(BlockRaycastContext context) {
        Vec3 start = context.start();
        Vec3 end = context.end();
        BiFunction<BlockRaycastContext, BlockPos, BlockHitResult> clipBlock = (blockContext, blockPos) -> {
            BlockState state = ClientAccess.evernightMinecraft.level.getBlockState(blockPos);
            BlockRaycastClassifier classifier = blockContext.classifier();
            BlockRaycastOverride override = classifier.test(blockPos, state);
            if (override == BlockRaycastOverride.IGNORE) {
                Block air = Blocks.AIR;
                state = air.defaultBlockState();
            } else if (override == BlockRaycastOverride.SOLID) {
                Block bedrock = Blocks.BEDROCK;
                state = bedrock.defaultBlockState();
            }
            ClientLevel shapeLevel = ClientAccess.evernightMinecraft.level;
            VoxelShape shape = blockContext.getBlockShape(state, shapeLevel, blockPos);
            ClientLevel interactionLevel = ClientAccess.evernightMinecraft.level;
            Vec3 blockStart = blockContext.start();
            Vec3 blockEnd = blockContext.end();
            return interactionLevel.clipWithInteractionOverride(blockStart, blockEnd, blockPos, shape, state);
        };
        Function<BlockRaycastContext, BlockHitResult> onMiss = missContext -> {
            Vec3 missStart = missContext.start();
            Vec3 missEnd = missContext.end();
            Vec3 difference = missStart.subtract(missEnd);
            Vec3 missLocation = missContext.start();
            double directionZ = difference.z;
            double directionY = difference.y;
            double directionX = difference.x;
            Direction direction = Direction.getApproximateNearest(directionX, directionY, directionZ);
            Vec3 containingPosition = missContext.end();
            BlockPos missPos = BlockPos.containing(containingPosition);
            return BlockHitResult.miss(missLocation, direction, missPos);
        };
        return BlockGetter.traverseBlocks(start, end, context, clipBlock, onMiss);
    }

    public static HitResult raycastFromRotation(Vec3 origin, float yaw, float pitch, float range) {
        float scaledPitch = pitch * MathConstants.RADIANS_PER_DEGREE;
        float scaledNegatedYaw = -yaw * MathConstants.RADIANS_PER_DEGREE;
        float yawCosine = Mth.cos(scaledNegatedYaw);
        float yawSine = Mth.sin(scaledNegatedYaw);
        float pitchCosine = Mth.cos(scaledPitch);
        float pitchSine = Mth.sin(scaledPitch);
        Vec3 direction = new Vec3(yawSine * pitchCosine, -pitchSine, yawCosine * pitchCosine);
        double scaledDirectionX = direction.x * range;
        double scaledDirectionY = direction.y * range;
        double offsetZ = direction.z * range;
        Vec3 end = origin.add(scaledDirectionX, scaledDirectionY, offsetZ);
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        ClipContext clipContext = new ClipContext(origin, end,
                net.minecraft.world.level.ClipContext.Block.OUTLINE, Fluid.NONE,
                ClientAccess.evernightMinecraft.player);
        return level.clip(clipContext);
    }

    public static HitResult raycastWithEntityTrace(LocalPlayer player) {
        ReachModule.setNoEntityTraceSuppressed(true);
        float partialTick = RenderUtils.getPartialTick();
        Entity cameraEntity = ClientAccess.evernightMinecraft.getCameraEntity();
        HitResult hitResult = player.raycastHitResult(partialTick, cameraEntity);
        ReachModule.setNoEntityTraceSuppressed(false);
        return hitResult;
    }

    public static int getGroundDistance() {
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        ClipContext clipContext = new ClipContext(groundTraceOrigin(),
                new Vec3(ClientAccess.evernightMinecraft.player.getX(), groundTraceMinimumY(), ClientAccess.evernightMinecraft.player.getZ()),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, Fluid.NONE,
                ClientAccess.evernightMinecraft.player);
        BlockHitResult groundHit = level.clip(clipContext);
        double playerY = ClientAccess.evernightMinecraft.player.getY();
        double verticalDifference = playerY - groundHit.getLocation().y;
        return (int) Math.round(verticalDifference);
    }

    private static Vec3 groundTraceOrigin() {
        return ClientAccess.evernightMinecraft.player.position();
    }

    private static double groundTraceMinimumY() {
        return ClientAccess.evernightMinecraft.level.getMinY();
    }

    public static int getCollisionBlockYOffset() {
        double playerY = ClientAccess.evernightMinecraft.player.getY();
        int roundedBaseY = (int) Math.round(playerY) + 1;
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        ClipContext clipContext = new ClipContext(ClientAccess.evernightMinecraft.player.position(),
                new Vec3(ClientAccess.evernightMinecraft.player.getX(), WorldUtils.getTopY(), ClientAccess.evernightMinecraft.player.getZ()),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, Fluid.NONE,
                ClientAccess.evernightMinecraft.player);
        BlockHitResult collisionHit = level.clip(clipContext);
        BlockPos hitBlockPos = collisionHit.getBlockPos();
        return hitBlockPos.getY() - roundedBaseY - 1;
    }

    public static boolean hasVisibleEntitySample(Entity entity) {
        AABB bounds = entity.getBoundingBox();
        float heightFraction = 0.0F;
        while (true) {
            if (!(heightFraction <= 1.0F)) {
                return false;
            }
            double sampleY = bounds.minY + (bounds.maxY - bounds.minY) * heightFraction;
            List samplePoints = BoundingBoxUtils.getCornersByDistance(bounds, sampleY);
            for (int pointIndex = 0; pointIndex < samplePoints.size() - 1; pointIndex++) {
                Vec3 eyePosition = ClientAccess.evernightMinecraft.player.getEyePosition();
                Vec3 endPoint = (Vec3) samplePoints.get(pointIndex);
                if (isLineVisible(eyePosition, endPoint)) {
                    return true;
                }
            }
            heightFraction += 0.5F;
        }
    }

    public static boolean isLineVisible(Vec3 startPoint, Vec3 endPoint) {
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        ClipContext clipContext = new ClipContext(startPoint, endPoint,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, Fluid.NONE,
                ClientAccess.evernightMinecraft.player);
        BlockHitResult clipResult = level.clip(clipContext);
        return clipResult.getType() == Type.MISS;
    }
}