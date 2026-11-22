package evernight.client.utils.world;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.player.CrystalInteractionManager;
import evernight.client.managers.player.SwingInteractionManager;
import evernight.client.modules.client.AntiCheat.AntiCheatModule;
import evernight.client.modules.misc.KillEffects.KillEffectsModule;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.AntiCheatStrictDirectionChoice;
import evernight.client.settings.options.BlockSampleMode;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.combat.BoundingBoxUtils;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.entity.SwingAction;
import evernight.client.utils.math.MathConstants;
import evernight.client.utils.network.PacketSender;
import evernight.mixin.minecraft.world.BlockBehaviourAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResult.Success;
import net.minecraft.world.InteractionResult.SwingSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public final class BlockInteractionUtils implements ClientAccess {
    private static final Direction[] placementDirections;
    private static final ElapsedTimer interactionTimer;
    public static boolean isPlacingBlock;
    private static final AntiCheatModule antiCheat;
    private static final KillEffectsModule killEffects;

    static {
        placementDirections = new Direction[]{Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP};
        interactionTimer = new ElapsedTimer();
        isPlacingBlock = false;
        antiCheat = ClientContext.getModule(AntiCheatModule.class);
        killEffects = ClientContext.getModule(KillEffectsModule.class);
    }

    private BlockInteractionUtils() {
    }

    public static boolean isInteractionCooldownActive() {
        ElapsedTimer timerSnapshot = interactionTimer;
        return !timerSnapshot.hasElapsed(50L);
    }

    public static void resetInteractionCooldown() {
        interactionTimer.reset();
    }

    public static boolean requiresSneakForInteraction(BlockPos position) {
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        BlockState state = level.getBlockState(position);
        Block block = state.getBlock();
        if (block == Blocks.RESPAWN_ANCHOR) {
            return true;
        }
        return state.hasBlockEntity() || block instanceof BedBlock || block instanceof AbstractChestBlock
                || block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof ButtonBlock
                || block instanceof ComposterBlock || block instanceof NoteBlock
                || block instanceof StandingSignBlock || block instanceof AnvilBlock
                || block instanceof AbstractFurnaceBlock || block instanceof CraftingTableBlock
                || block instanceof EnchantingTableBlock || block instanceof CakeBlock;
    }

    public static boolean canPlaceAt(BlockPos position, boolean playersOnly) {
        return canPlaceAt(position, playersOnly, false);
    }

    public static boolean isThrowableItem(Item item) {
        return item instanceof EggItem || item instanceof SnowballItem || item instanceof EnderpearlItem
                || item instanceof ExperienceBottleItem || item instanceof ThrowablePotionItem
                || item instanceof TridentItem;
    }

    public static boolean isGlass(BlockState state) {
        Block glassPane = Blocks.GLASS_PANE;
        if (state.is(glassPane)) {
            return true;
        }
        Block glass = Blocks.GLASS;
        return state.is(glass) || state.getBlock() instanceof StainedGlassBlock
                || state.getBlock() instanceof StainedGlassPaneBlock;
    }

    public static Direction findSupportDirection(
            BlockPos position, boolean strictDirection, boolean checkRaytrace) {
        Direction[] directions = placementDirections;
        for (Direction direction : directions) {
            BlockPos neighbor = position.relative(direction);
            if (neighbor.getY() >= WorldUtils.getTopY()) {
                continue;
            }
            ClientLevel level = ClientAccess.evernightMinecraft.level;
            WorldBorder borderSnapshot = level.getWorldBorder();
            double x = neighbor.getX();
            double z = neighbor.getZ();
            if (!borderSnapshot.isWithinBounds(x, z)) {
                continue;
            }
            if (checkRaytrace) {
                BlockSampleMode raytraceMode = BlockSampleMode.X8;
                Direction clickedFace = direction.getOpposite();
                if (!WorldUtils.canSeeAny(raytraceMode.sampleFace(neighbor, clickedFace))) {
                    continue;
                }
            }
            if (ClientContext.worldStateManager.isPlacementOccupied(neighbor)) {
                if (!strictDirection) {
                    return direction;
                }
                if (getStrictDirections(neighbor).contains(direction.getOpposite())) {
                    return direction;
                }
            }
        }
        return null;
    }

    public static boolean isInteractiveBlock(Block block) {
        return block instanceof CraftingTableBlock || block instanceof AnvilBlock
                || block instanceof ButtonBlock || block instanceof BasePressurePlateBlock
                || block instanceof BaseEntityBlock || block instanceof BedBlock || block instanceof FenceGateBlock
                || block instanceof DoorBlock || block instanceof NoteBlock || block instanceof TrapDoorBlock;
    }

    public static boolean canPlaceCrystal(
            BlockPos basePosition, boolean requireTwoAirBlocks, boolean checkEntities, boolean checkCrystals, boolean allowFire, boolean shortHeight, boolean expandPlayers
    ) {
        if (!WorldUtils.isBedrockOrObsidian(basePosition)) {
            return false;
        }
        BlockPos crystalPosition = basePosition.offset(0, 1, 0);
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        Block crystalBlock = level.getBlockState(crystalPosition).getBlock();
        if (allowFire) {
            if (crystalBlock != Blocks.AIR && crystalBlock != Blocks.FIRE) {
                return false;
            }
        } else if (crystalBlock != Blocks.AIR) {
            return false;
        }
        if (requireTwoAirBlocks) {
            ClientLevel upperLevel = ClientAccess.evernightMinecraft.level;
            BlockPos upperPosition = basePosition.offset(0, 2, 0);
            if (upperLevel.getBlockState(upperPosition).getBlock() != Blocks.AIR) {
                return false;
            }
        }
        AABB boundsSnapshot = new AABB(crystalPosition.getX(), crystalPosition.getY(), crystalPosition.getZ(),
                crystalPosition.getX() + 1, (double) crystalPosition.getY() + (shortHeight ? 0.9D : 2.0D), crystalPosition.getZ() + 1
        );
        boolean blocked = false;
        ClientLevel entityLevel = ClientAccess.evernightMinecraft.level;
        for (Entity entity : entityLevel.entitiesForRendering()) {
            if (entity.isRemoved()) {
                continue;
            }
            if (!entity.isAlive()) {
                continue;
            }
            AABB entityBounds = entity.getBoundingBox();
            if (entity instanceof Player && entity != ClientAccess.evernightMinecraft.player && expandPlayers) {
                entityBounds = entityBounds.inflate(0.01D);
            } else if (entity instanceof EndCrystal && !checkCrystals) {
                continue;
            }
            if (entityBounds.intersects(boundsSnapshot)) {
                blocked = true;
                break;
            }
        }
        return !checkEntities || !blocked;
    }

    public static boolean placeBlockIgnoringCrystals(
            BlockPos position, Direction direction, boolean airPlace, InteractionHand hand) {
        return placeBlockIgnoringCrystals(position, null, direction, airPlace, hand);
    }

    public static boolean placeBlock(
            BlockPos position, Vec3 hitPosition, Direction direction, boolean airPlace, InteractionHand hand) {
        if (ClientAccess.evernightMinecraft.level == null || ClientAccess.evernightMinecraft.player == null
                || ClientAccess.evernightMinecraft.gameMode == null) {
            return false;
        }
        Block heldBlockSnapshot = getHeldBlock(hand);
        if (!canPlaceBlock(position, heldBlockSnapshot, false)) {
            return false;
        }
        boolean placingInAir = airPlace && direction == null;
        if (direction == null) {
            if (!airPlace) {
                return false;
            }
            Direction fallbackDirection;
            if (ClientAccess.evernightMinecraft.hitResult instanceof BlockHitResult targetHitResult) {
                fallbackDirection = targetHitResult.getDirection();
            } else {
                fallbackDirection = Direction.DOWN;
            }
            direction = fallbackDirection;
        }
        BlockPos neighborSnapshot;
        if (placingInAir) {
            neighborSnapshot = position;
        } else {
            neighborSnapshot = position.relative(direction);
        }
        boolean requiresSneak = requiresSneakForInteraction(neighborSnapshot);
        if (hitPosition == null) {
            hitPosition = position.getCenter().add(
                    Vec3.atLowerCornerOf(direction.getUnitVec3i()).scale(MathConstants.HALF));
        }
        if (requiresSneak) {
            ClientContext.playerStateTracker.setSneaking(true);
        }
        isPlacingBlock = true;
        MultiPlayerGameMode gameModeSnapshot = ClientAccess.evernightMinecraft.gameMode;
        LocalPlayer playerSnapshot = ClientAccess.evernightMinecraft.player;
        BlockHitResult hitResultSnapshot = new BlockHitResult(hitPosition, placingInAir ? direction : direction.getOpposite(), neighborSnapshot, false);
        InteractionResult result = gameModeSnapshot.useItemOn(playerSnapshot, hand, hitResultSnapshot);
        isPlacingBlock = false;
        if (result instanceof Success success) {
            if (success.swingSource() == SwingSource.CLIENT) {
                ClientAccess.evernightMinecraft.player.swing(hand);
            }
        }
        if (requiresSneak) {
            ClientContext.playerStateTracker.setSneaking(false);
        }
        return true;
    }

    private static Block getHeldBlock(InteractionHand hand) {
        if (ClientAccess.evernightMinecraft.player.getItemInHand(hand).getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock();
        }
        return null;
    }

    public static Entity attackCrystalAt(BlockPos position, int minimumAge) {
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof EndCrystal) {
                AABB entityBounds = entity.getBoundingBox();
                AABB blockBounds = new AABB(position);
                if (entityBounds.intersects(blockBounds) && entity.tickCount >= minimumAge) {
                    attackEntity(entity);
                    return entity;
                }
            }
        }
        return null;
    }

    public static boolean canPlaceBlock(BlockPos position, Block block, boolean playersOnly) {
        return canPlaceBlock(position, block, playersOnly, false);
    }

    public static boolean canPlaceBlock(
            BlockPos position, Block block, boolean playersOnly, boolean ignoreCrystals) {
        if (isOutsidePlacementBounds(position)) {
            return false;
        }
        if (WorldUtils.isPlacementOccupied(position)) {
            return false;
        }
        VoxelShape shapeSnapshot;
        if (block == null) {
            shapeSnapshot = Shapes.empty();
        } else {
            BlockState collisionState = block.defaultBlockState();
            ClientLevel collisionLevel = ClientAccess.evernightMinecraft.level;
            BlockPos collisionPosition = BlockPos.ZERO;
            shapeSnapshot = collisionState.getCollisionShape(collisionLevel, collisionPosition);
        }
        AABB boundsSnapshot;
        if (shapeSnapshot.isEmpty()) {
            boundsSnapshot = new AABB(position);
        } else {
            AABB shapeBounds = shapeSnapshot.bounds();
            boundsSnapshot = BoundingBoxUtils.placeAtBlock(shapeBounds, position);
        }
        if (block != null && !((BlockBehaviourAccessor) block).isCollidable()) {
            return true;
        }
        return !hasBlockingEntity(boundsSnapshot, playersOnly, ignoreCrystals);
    }

    public static boolean hasBlockingEntity(AABB bounds, boolean playersOnly, boolean ignoreCrystals) {
        ClientLevel entityLevel = ClientAccess.evernightMinecraft.level;
        Class<Entity> entityType = Entity.class;
        Predicate<Entity> entityFilter = candidateEntity -> !(candidateEntity instanceof ThrownExperienceBottle)
                && !(candidateEntity instanceof ItemEntity) && !(candidateEntity instanceof ItemFrame)
                && !(candidateEntity instanceof FireworkRocketEntity) && !(candidateEntity instanceof ExperienceOrb);
        for (Entity entity : entityLevel.getEntitiesOfClass(entityType, bounds, entityFilter)) {
            AABB entityBounds = entity.getBoundingBox();
            if (!BoundingBoxUtils.hasVolumeOverlap(bounds, entityBounds)
                    && entity != ClientAccess.evernightMinecraft.player) {
                continue;
            }
            if (entity instanceof EndCrystal && ignoreCrystals) {
                continue;
            }
            if (entity instanceof Arrow) {
                continue;
            }
            if (entity instanceof Player) {
                if (entity.getBoundingBox().intersects(bounds)) {
                    return true;
                }
            } else if (!playersOnly) {
                return true;
            }
        }
        return false;
    }

    public static void attackEntity(Entity entity) {
        CrystalInteractionManager attackManagerSnapshot = ClientContext.crystalInteractionManager;
        int entityId = entity.getId();
        if (attackManagerSnapshot.attackEntity(entityId)) {
            if (killEffects.isToggled() && entity instanceof Player targetPlayer) {
                KillEffectsModule effectsSnapshot = killEffects;
                effectsSnapshot.trackTarget(targetPlayer);
            }
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            player.resetOnlyAttackStrengthTicker();
        }
    }

    public static void interactWithEntity(Entity entity, InteractionHand hand) {
        MultiPlayerGameMode gameModeSnapshot = ClientAccess.evernightMinecraft.gameMode;
        LocalPlayer playerSnapshot = ClientAccess.evernightMinecraft.player;
        EntityHitResult hitResultSnapshot = new EntityHitResult(entity);
        gameModeSnapshot.interactAt(playerSnapshot, entity, hitResultSnapshot, hand);
        ClientAccess.evernightMinecraft.gameMode.interact(ClientAccess.evernightMinecraft.player, entity, hand);
        ClientAccess.evernightMinecraft.player.swing(hand);
    }

    public static BlockPos findSupportPosition(
            BlockPos position, int maxDistance, boolean checkPlayers, boolean strictDirection, Predicate<? super BlockPos> predicate
    ) {
        for (int distance = 1; distance <= maxDistance; distance++) {
            for (Direction direction : placementDirections) {
                BlockPos candidate = position.relative(direction, distance);
                if (!predicate.test(candidate)) {
                    continue;
                }
                if (findSupportDirection(candidate, strictDirection) == null) {
                    continue;
                }
                ClientLevel playerLevel = ClientAccess.evernightMinecraft.level;
                Class<Player> playerType = Player.class;
                AABB playerBounds = new AABB(candidate);
                Predicate<Player> playerFilter = candidatePlayer -> checkPlayers;
                if (playerLevel.getEntitiesOfClass(playerType, playerBounds, playerFilter).isEmpty()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    public static boolean placeBlock(BlockPos position, boolean airPlace, InteractionHand hand) {
        Direction directionSnapshot = findSupportDirection(position);
        return placeBlock(position, null, directionSnapshot, airPlace, hand);
    }

    public static BlockPos findSupportPosition(
            BlockPos position, int maxDistance, boolean checkPlayers, boolean strictDirection) {
        Predicate<BlockPos> acceptPosition = candidate -> true;
        return findSupportPosition(position, maxDistance, checkPlayers, strictDirection, acceptPosition);
    }

    public static boolean isCollidable(BlockPos position) {
        return ((BlockBehaviourAccessor) getBlock(position)).isCollidable();
    }

    public static boolean isOutsidePlacementBounds(BlockPos position) {
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        WorldBorder borderSnapshot = level.getWorldBorder();
        if (!borderSnapshot.isWithinBounds(position)) {
            return true;
        }
        if (!Level.isInSpawnableBounds(position)) {
            return true;
        }
        if (position.getY() >= WorldUtils.getTopY()) {
            return true;
        }
        return !(position.getY() >= ClientAccess.evernightMinecraft.level.getMinY());
    }

    public static Block getBlock(BlockPos position) {
        if (position == null) {
            return null;
        }
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        return level.getBlockState(position).getBlock();
    }

    public static boolean placeBlockIgnoringCrystals(
            BlockPos position, Vec3 hitPosition, Direction direction, boolean airPlace, InteractionHand hand) {
        return placeBlockIgnoringCrystals(position, hitPosition, direction, airPlace, hand, ClientAccess.evernightMinecraft.player.getItemInHand(hand));
    }

    public static boolean placeBlockIgnoringCrystals(
            BlockPos position, Vec3 hitPosition, Direction direction, boolean airPlace, InteractionHand hand, ItemStack heldStack
    ) {
        if (ClientAccess.evernightMinecraft.level == null || ClientAccess.evernightMinecraft.player == null
                || ClientAccess.evernightMinecraft.gameMode == null) {
            return false;
        }
        Block heldBlock;
        if (heldStack.getItem() instanceof BlockItem blockItem) {
            heldBlock = blockItem.getBlock();
        } else {
            heldBlock = null;
        }
        if (!canPlaceBlock(position, heldBlock, false, true)) {
            return false;
        }
        return sendPlacement(position, hitPosition, direction, airPlace, hand);
    }

    public static List<BlockPos> findSupportPath(
            BlockPos position, int maxDistance, boolean checkPlayers, boolean strictDirection, Predicate<? super BlockPos> predicate
    ) {
        ArrayList<BlockPos> path = new ArrayList<>();
        for (Direction direction : placementDirections) {
            path.clear();
            for (int distance = 1; distance <= maxDistance; distance++) {
                BlockPos candidate = position.relative(direction, distance);
                path.addFirst(candidate);
                if (!predicate.test(candidate)) {
                    break;
                }
                ClientLevel playerLevel = ClientAccess.evernightMinecraft.level;
                Class<Player> playerType = Player.class;
                AABB playerBounds = new AABB(candidate);
                Predicate<Player> playerFilter = candidatePlayer -> checkPlayers;
                if (!playerLevel.getEntitiesOfClass(playerType, playerBounds, playerFilter).isEmpty()) {
                    break;
                }
                if (findSupportDirection(candidate, strictDirection) != null) {
                    return List.copyOf(path);
                }
            }
        }
        return Collections.emptyList();
    }

    public static List<BlockPos> findSupportPath(
            BlockPos position, int maxDistance, boolean checkPlayers, boolean strictDirection) {
        Predicate<BlockPos> acceptPosition = candidate -> true;
        return findSupportPath(position, maxDistance, checkPlayers, strictDirection, acceptPosition);
    }

    public static Direction findSupportDirection(BlockPos position, boolean strictDirection) {
        return findSupportDirection(position, strictDirection, false);
    }

    public static List<Direction> getStrictDirectionsOrFallback(BlockPos position, Direction fallback) {
        List<Direction> directions = getStrictDirections(position);
        if (directions.isEmpty()) {
            return Collections.singletonList(fallback);
        } else {
            return directions;
        }
    }

    public static boolean canPlaceAt(BlockPos position, boolean playersOnly, boolean ignoreCrystals) {
        if (isOutsidePlacementBounds(position)) {
            return false;
        }
        if (WorldUtils.isPlacementOccupied(position)) {
            return false;
        }
        AABB boundsSnapshot = new AABB(position);
        return !hasBlockingEntity(boundsSnapshot, playersOnly, ignoreCrystals);
    }

    public static boolean canPlayerInteract() {
        if (ClientAccess.evernightMinecraft.player.isSpectator()) {
            return false;
        }
        LocalPlayer alivePlayer = ClientAccess.evernightMinecraft.player;
        if (!alivePlayer.isAlive()) {
            return false;
        }
        if (ClientAccess.evernightMinecraft.player.isHandsBusy()) {
            return false;
        }
        LocalPlayer sleepingPlayer = ClientAccess.evernightMinecraft.player;
        return !sleepingPlayer.isSleeping();
    }

    public static boolean isStrictDirectionEnabled() {
        Setting<AntiCheatStrictDirectionChoice> strictDirectionSetting = antiCheat.strictDirectionSetting;
        return strictDirectionSetting.getValue() != AntiCheatStrictDirectionChoice.OFF;
    }

    public static Direction findSupportDirection(BlockPos position) {
        return findSupportDirection(position, false);
    }

    public static boolean sendPlacement(
            BlockPos position, Vec3 hitPosition, Direction direction, boolean airPlace, InteractionHand hand) {
        boolean placingInAir = airPlace && direction == null;
        if (direction == null) {
            List<Direction> directions = getStrictDirections(position);
            if (!airPlace) {
                return false;
            }
            direction = directions.isEmpty() ? Direction.DOWN : directions.getFirst();
        }
        BlockPos neighborSnapshot;
        if (placingInAir) {
            neighborSnapshot = position;
        } else {
            neighborSnapshot = position.relative(direction);
        }
        boolean requiresSneak = requiresSneakForInteraction(neighborSnapshot) && !placingInAir;
        if (requiresSneak) {
            ClientContext.playerStateTracker.setSneaking(true);
        }
        if (hitPosition == null) {
            hitPosition = position.getCenter().add(
                    Vec3.atLowerCornerOf(direction.getUnitVec3i()).scale(MathConstants.HALF));
        }
        BlockHitResult hitResultSnapshot = new BlockHitResult(hitPosition, placingInAir ? direction : direction.getOpposite(), neighborSnapshot, false);
        PacketSender.sendUseItemOn(hand, hitResultSnapshot);
        SwingInteractionManager swingManager = ClientContext.swingManager;
        SwingAction interactionKind = SwingAction.INTERACT;
        swingManager.swing(hand, interactionKind);
        if (requiresSneak) {
            ClientContext.playerStateTracker.setSneaking(false);
        }
        return true;
    }

    public static List getStrictDirections(BlockPos position) {
        Direction[] directionOrder = placementDirections;
        ArrayList<Direction> directions = new ArrayList<>(List.of(directionOrder));
        if (antiCheat.strictDirectionSetting.getValue() == AntiCheatStrictDirectionChoice.NCP) {
            for (Direction direction : placementDirections) {
                BlockPos neighbor = position.relative(direction);
                ClientLevel level = ClientAccess.evernightMinecraft.level;
                BlockState neighborState = level.getBlockState(neighbor);
                if (WorldUtils.isPlacementOccupied(neighbor)) {
                    if (neighborState.isSolidRender()) {
                        directions.remove(direction);
                    }
                }
            }
        }
        if ((position.getY() + 1) > WorldUtils.getTopY()) {
            directions.remove(Direction.UP);
        }
        double deltaX = (ClientAccess.evernightMinecraft.player.getEyePosition().x) - position.getCenter().x;
        double deltaZ = (ClientAccess.evernightMinecraft.player.getEyePosition().z) - position.getCenter().z;
        if (deltaX > MathConstants.HALF) {
            directions.remove(Direction.WEST);
        } else if (deltaX < -MathConstants.HALF) {
            directions.remove(Direction.EAST);
        } else {
            directions.remove(Direction.WEST);
            directions.remove(Direction.EAST);
        }
        if (deltaZ > MathConstants.HALF) {
            directions.remove(Direction.NORTH);
        } else if (deltaZ < -MathConstants.HALF) {
            directions.remove(Direction.SOUTH);
        } else {
            directions.remove(Direction.NORTH);
            directions.remove(Direction.SOUTH);
        }
        if (antiCheat.strictDirectionSetting.getValue() == AntiCheatStrictDirectionChoice.GRIM) {
            boolean sampleAbove = PlayerStateUtils.streamBodySamplePositions().noneMatch(bodySample -> {
                double sampleY = bodySample.y;
                return sampleY > position.getY() + 1;
            });
            boolean sampleBelow = PlayerStateUtils.streamBodySamplePositions().noneMatch(bodySample -> {
                double sampleY = bodySample.y;
                return sampleY < position.getY();
            });
            if (sampleAbove) {
                directions.remove(Direction.UP);
            }
            if (sampleBelow) {
                directions.remove(Direction.DOWN);
            }
        } else {
            if ((ClientAccess.evernightMinecraft.player.getEyePosition().y) < position.getY()) {
                directions.remove(Direction.UP);
            }
            if ((ClientAccess.evernightMinecraft.player.getEyePosition().y) > position.getY()) {
                directions.remove(Direction.DOWN);
            }
        }
        return directions;
    }
}