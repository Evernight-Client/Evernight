package evernight.client.modules.render.Tunnels;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.render.WorldRenderEvent;
import evernight.client.managers.player.RotationManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.mesh.WorldBoxRenderer;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.world.ChunkScanner;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.util.Mth;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public class TunnelsModule extends Module {
    private static final MutableBlockPos scanPosition;

    static {
        scanPosition = new MutableBlockPos();
    }

    private final Setting<Boolean> verticalsSetting;
    private final Setting<Float> lineWidthSetting;
    private final Setting<Float> heightSetting;
    private final Setting<Integer> minLengthSetting;
    private final Setting<Color> fillSetting;
    private final Setting<Color> outlineSetting;
    private final ElapsedTimer mergeTimer;
    private final Queue<Tunnel> pendingTunnels;
    private final AtomicBoolean mergeInProgress;
    private final ChunkScanner chunkScanner;
    private volatile List<Tunnel> tunnels;

    public TunnelsModule() {
        super("Tunnels", ModuleCategory.RENDER);
        this.verticalsSetting = this.add(new BooleanSetting("Verticals", true));
        this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 3.0F));
        this.heightSetting = this.add(new NumberSetting<>("Height", 0.1F, -1.0F, 2.0F).withUnit("m"));
        String minimumLengthKey = "MinLength";
        this.minLengthSetting = this.add(new NumberSetting<>(minimumLengthKey, 4, 1, 16)
                .withUnit("m"));
        this.fillSetting = this.add(new ColorSetting("Fill", new Color(1358888960, true)));
        this.outlineSetting = this.add(new ColorSetting("Outline", new Color(-65536, true)));
        this.mergeTimer = new ElapsedTimer();
        this.pendingTunnels = new ConcurrentLinkedQueue<>();
        this.tunnels = Collections.emptyList();
        this.mergeInProgress = new AtomicBoolean(false);
        this.chunkScanner = new ChunkScanner(1,
                (java.util.function.BiConsumer<net.minecraft.world.level.ChunkPos, net.minecraft.world.level.chunk.LevelChunk>) this::scanChunk,
                (java.util.function.BiConsumer<net.minecraft.world.level.ChunkPos, net.minecraft.world.level.chunk.LevelChunk>) ((unloadedChunkPosition, unusedChunk) -> {
                    BlockableEventLoop<?> client = ClientAccess.evernightMinecraft;
                    Runnable removeUnloadedTunnels = () -> this.tunnels.removeIf(
                            tunnel -> {
                                return this.isTunnelCenterInChunk(tunnel, unloadedChunkPosition);
                            }
                    );
                    client.executeIfPossible(removeUnloadedTunnels);
                }), null);
    }

    @NotNull
    private static Set<Tunnel> collectMergedTunnels(List<Tunnel> tunnels) {
        HashSet<Tunnel> merged = new HashSet<>();
        for (int index = 0; index < tunnels.size(); index++) {
            TunnelsModule.Tunnel tunnel = tunnels.get(index);
            if (!merged.contains(tunnel)) {
                for (int candidateIndex = index + 1; candidateIndex < tunnels.size(); candidateIndex++) {
                    TunnelsModule.Tunnel candidate = tunnels.get(candidateIndex);
                    if (!merged.contains(candidate)) {
                        if (tunnel.canMergeWith(candidate)) {
                            tunnel.merge(candidate);
                            merged.add(candidate);
                        }
                    }
                }
            }
        }
        return merged;
    }

    private boolean isNonSolid(BlockPos position) {
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        BlockState state = level.getBlockState(position);
        return !state.isSolidRender();
    }

    private boolean isHorizontalTunnelCell(BlockPos position) {
        if (!this.isNonSolid(position.above()) || this.isNonSolid(position.below())
                || this.isNonSolid(position.above().above())) {
            return false;
        }
        if (this.isNonSolid(position.north()) && this.isNonSolid(position.south())
                && this.isNonSolid(position.above().north()) && this.isNonSolid(position.above().south())) {
            return !this.isNonSolid(position.east()) && !this.isNonSolid(position.west())
                    && !this.isNonSolid(position.above().east()) && !this.isNonSolid(position.above().west());
        }
        return this.isNonSolid(position.east()) && this.isNonSolid(position.west())
                && this.isNonSolid(position.above().east()) && this.isNonSolid(position.above().west())
                && !this.isNonSolid(position.north()) && !this.isNonSolid(position.south())
                && !this.isNonSolid(position.above().north()) && !this.isNonSolid(position.above().south());
    }

    @Override
    public void onEnable() {
        ChunkScanner capturedScanner = this.chunkScanner;
        capturedScanner.start();
        if (!this.isWorldUnavailable()) {
            capturedScanner = this.chunkScanner;
            capturedScanner.scanLoadedChunks();
        }
    }

    @Override
    public void onDisable() {
        this.tunnels.clear();
        ChunkScanner scanner = this.chunkScanner;
        scanner.stop();
    }

    private boolean isVerticalTunnelCell(BlockPos position) {
        Boolean scanVerticals = this.verticalsSetting.getValue();
        if (!scanVerticals) {
            return false;
        }
        Plane horizontalPlane = Plane.HORIZONTAL;
        Iterator<Direction> directions = horizontalPlane.iterator();
        while (directions.hasNext()) {
            Direction horizontalDirection = directions.next();
            BlockPos sidePosition = position.relative(horizontalDirection);
            ClientLevel level = ClientAccess.evernightMinecraft.level;
            BlockState sideState = level.getBlockState(sidePosition);
            if (!sideState.isSolidRender()) {
                return false;
            }
            Block bambooBlock = Blocks.BAMBOO_BLOCK;
            if (sideState.is(bambooBlock)) {
                return false;
            }
            Block basaltBlock = Blocks.BASALT;
            if (sideState.is(basaltBlock)) {
                return false;
            }
        }
        Plane verticalPlane = Plane.VERTICAL;
        directions = verticalPlane.iterator();
        while (directions.hasNext()) {
            Direction verticalDirection = directions.next();
            BlockPos firstVerticalPosition = position.relative(verticalDirection);
            if (!this.isNonSolid(firstVerticalPosition)) {
                return false;
            }
            BlockPos secondVerticalPosition = firstVerticalPosition.relative(verticalDirection);
            if (!this.isNonSolid(secondVerticalPosition)) {
                return false;
            }
        }
        return true;
    }

    private boolean isTunnelCenterInChunk(TunnelsModule.Tunnel tunnel, ChunkPos chunkPosition) {
        int maxX = tunnel.maxX;
        int minX = tunnel.minX;
        float centerX = Mth.lerpInt(0.5F, minX, maxX);
        int maxZ = tunnel.maxZ;
        int minZ = tunnel.minZ;
        float centerZ = Mth.lerpInt(0.5F, minZ, maxZ);
        return centerX >= chunkPosition.getMinBlockX() && centerX <= chunkPosition.getMaxBlockX()
                && centerZ >= chunkPosition.getMinBlockZ() && centerZ <= chunkPosition.getMaxBlockZ();
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        if (!this.isWorldUnavailable()) {
            AtomicBoolean mergeFlag = this.mergeInProgress;
            if (!mergeFlag.get()) {
                ElapsedTimer capturedTimer = this.mergeTimer;
                ElapsedTimer timerForElapsedCheck = capturedTimer;
                if (timerForElapsedCheck.hasElapsed(75L)) {
                    capturedTimer = this.mergeTimer;
                    capturedTimer.reset();
                    boolean busy = true;
                    AtomicBoolean busyFlag = this.mergeInProgress;
                    busyFlag.set(busy);
                    ArrayList<Tunnel> snapshot = new ArrayList<>(this.tunnels);
                    Vec3 eyePosition = ClientAccess.evernightMinecraft.player.getEyePosition();
                    ClientAccess.executorService.submit(() -> this.mergePendingTunnels(snapshot, eyePosition));
                }
            }
        }
    }

    @EventHandler
    private void onRenderWorld(WorldRenderEvent.Main event) {
        if (!this.isWorldUnavailable()) {
            List<Tunnel> visibleTunnels = this.tunnels;
            Iterator<Tunnel> iterator = visibleTunnels.iterator();
            while (iterator.hasNext()) {
                TunnelsModule.Tunnel tunnel = iterator.next();
                AABB bounds = tunnel.getBounds();
                double horizontalLength = Math.max(bounds.getXsize(), bounds.getZsize());
                double length = Math.max(horizontalLength, bounds.getYsize());
                Integer minimumLength = this.minLengthSetting.getValue();
                if (!(length < minimumLength.intValue())) {
                    float renderHeight = (this.heightSetting.getValue());
                    if (tunnel.vertical) {
                        renderHeight = (float) bounds.getYsize();
                    }
                    double renderTop = bounds.minY + renderHeight;
                    AABB renderBounds = bounds.setMaxY(renderTop);
                    if (RotationManager.isVisible(renderBounds)) {
                        PoseStack fillPoses = event.getPoseStack();
                        Setting<Color> fillOption = this.fillSetting;
                        WorldBoxRenderer.fillBox(fillPoses, renderBounds, fillOption.getValue());
                        PoseStack outlinePoses = event.getPoseStack();
                        Color outlineColor = this.outlineSetting.getValue();
                        Float lineWidth = this.lineWidthSetting.getValue();
                        WorldBoxRenderer.outlineBox(outlinePoses, renderBounds, outlineColor, lineWidth);
                    }
                }
            }
        }
    }

    private void scanChunk(ChunkPos chunkPosition, LevelChunk unusedChunk) {
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        if (level == null) {
            return;
        }
        MutableBlockPos scanCursor = new MutableBlockPos();
        int firstY = level.getMinY() + 1;
        int topY = (int) WorldUtils.getTopY();
        int endY = topY - 2;
        int localX = 0;
        while (true) {
            if (localX >= 16) {
                return;
            }
            int localZ = 0;
            while (true) {
                if (localZ >= 16) {
                    localX++;
                    break;
                }
                int x = chunkPosition.getMinBlockX() + localX;
                int z = chunkPosition.getMinBlockZ() + localZ;
                for (int y = firstY; y < endY; y++) {
                    int blockY = y;
                    scanCursor.set(x, blockY, z);
                    MutableBlockPos nonSolidPosition = scanCursor;
                    if (this.isNonSolid(nonSolidPosition)) {
                        MutableBlockPos verticalPosition = scanCursor;
                        if (!this.isVerticalTunnelCell(verticalPosition)) {
                            MutableBlockPos horizontalPosition = scanCursor;
                            if (this.isHorizontalTunnelCell(horizontalPosition)) {
                                this.pendingTunnels.add(new TunnelsModule.Tunnel(x, y, z, x + 1, y + 1, z + 1, false)
                                );
                            }
                        } else {
                            int verticalStartY = y;
                            while (y < endY) {
                                int nextY = y + 1;
                                MutableBlockPos verticalPositionToSet = scanCursor;
                                MutableBlockPos nextVerticalPosition = verticalPositionToSet.set(x, nextY, z);
                                if (!this.isVerticalTunnelCell(nextVerticalPosition)) {
                                    break;
                                }
                                y++;
                            }
                            this.pendingTunnels.add(
                                    new TunnelsModule.Tunnel(x, verticalStartY, z, x + 1, y + 1, z + 1, true));
                        }
                    }
                }
                localZ++;
            }
        }
    }

    private void mergePendingTunnels(List<Tunnel> snapshot, Vec3 eyePosition) {
        try {
            boolean changed = false;
            TunnelsModule.Tunnel pending;
            while ((pending = this.pendingTunnels.poll()) != null) {
                boolean mergedPending = false;
                Iterator<Tunnel> iterator = snapshot.iterator();
                while (iterator.hasNext()) {
                    TunnelsModule.Tunnel existing = iterator.next();
                    if (existing.canMergeWith(pending)) {
                        existing.merge(pending);
                        mergedPending = true;
                        changed = true;
                        break;
                    }
                }
                if (!mergedPending) {
                    snapshot.add(pending);
                    changed = true;
                }
            }
            if (changed) {
                Set<Tunnel> merged = collectMergedTunnels(snapshot);
                snapshot.removeAll(merged);
            }
            snapshot.removeIf(tunnel -> {
                double distanceSquared = tunnel.distanceToCenterSquared(eyePosition);
                return distanceSquared > 65536.0D;
            });
            ClientAccess.evernightMinecraft.execute(() -> {
                this.tunnels = snapshot;
                this.mergeInProgress.set(false);
            });
        } catch (Exception ignoredFailure) {
            this.mergeInProgress.set(false);
        }
    }

    private static class Tunnel {
        public final boolean vertical;
        public int minX;
        public int minY;
        public int minZ;
        public int maxX;
        public int maxY;
        public int maxZ;
        private AABB cachedBounds = null;

        public Tunnel(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean vertical) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.vertical = vertical;
        }

        public AABB getBounds() {
            if (this.cachedBounds == null) {
                this.cachedBounds = new AABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
            }
            return this.cachedBounds;
        }

        public double distanceToCenterSquared(Vec3 position) {
            double coordinateMinimum = this.minX;
            double coordinateSpan = this.maxX - this.minX;
            double centerX = coordinateMinimum + coordinateSpan * 0.5D;
            coordinateMinimum = this.minY;
            coordinateSpan = this.maxY - this.minY;
            double centerY = coordinateMinimum + coordinateSpan * 0.5D;
            coordinateMinimum = this.minZ;
            coordinateSpan = this.maxZ - this.minZ;
            double centerZ = coordinateMinimum + coordinateSpan * 0.5D;
            return position.distanceToSqr(centerX, centerY, centerZ);
        }

        public boolean canMergeWith(TunnelsModule.Tunnel other) {
            if (this.vertical != other.vertical) {
                return false;
            }
            if (this.vertical) {
                if (this.minX != other.minX || this.maxX != other.maxX) {
                    return false;
                }
                return this.minZ == other.minZ && this.maxZ == other.maxZ
                        && (this.minY == other.maxY || this.maxY == other.minY);
            }
            if (this.minY != other.minY || this.maxY != other.maxY) {
                return false;
            }
            boolean touchesAlongX = (this.minX == other.maxX || this.maxX == other.minX)
                    && this.minZ == other.minZ && this.maxZ == other.maxZ;
            return touchesAlongX || (this.minZ == other.maxZ || this.maxZ == other.minZ)
                    && this.minX == other.minX && this.maxX == other.maxX;
        }

        public void merge(TunnelsModule.Tunnel other) {
            int otherMinX = other.minX;
            int currentMinX = this.minX;
            this.minX = Math.min(currentMinX, otherMinX);
            int otherMinY = other.minY;
            int currentMinY = this.minY;
            this.minY = Math.min(currentMinY, otherMinY);
            int otherMinZ = other.minZ;
            int currentMinZ = this.minZ;
            this.minZ = Math.min(currentMinZ, otherMinZ);
            int otherMaxX = other.maxX;
            int currentMaxX = this.maxX;
            this.maxX = Math.max(currentMaxX, otherMaxX);
            int otherMaxY = other.maxY;
            int currentMaxY = this.maxY;
            this.maxY = Math.max(currentMaxY, otherMaxY);
            int otherMaxZ = other.maxZ;
            int currentMaxZ = this.maxZ;
            this.maxZ = Math.max(currentMaxZ, otherMaxZ);
            this.cachedBounds = null;
        }
    }
}