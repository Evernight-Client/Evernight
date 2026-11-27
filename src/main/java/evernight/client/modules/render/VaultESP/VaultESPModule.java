package evernight.client.modules.render.VaultESP;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.network.MinecraftDisconnectEvent;
import evernight.client.events.network.MultiPlayerGameModeUseItemOnEvent;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.render.WorldRenderEvent;
import evernight.client.managers.player.RotationManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.mesh.LineMeshRenderer;
import evernight.client.render.mesh.WorldBoxRenderer;
import evernight.client.render.text.Translations;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.ActionSetting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.utils.world.ChunkScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Highlights ominous vaults, and nothing else: plain vaults are ignored.
 *
 * <p>An ominous vault is not a separate block, it is {@code minecraft:vault} with the block state
 * property {@code ominous=true}, and {@code vault_state=inactive} means it was never opened. Both
 * properties arrive with the chunk data, so this is a purely local render with no outgoing packets.
 *
 * <p>Scanning follows the client chunk lifecycle through {@link ChunkScanner} instead of polling every
 * tick: a loaded chunk is scanned once on a worker thread, block updates move single positions in or
 * out, and unloading a chunk drops its positions. Section palettes are checked first so only sections
 * that contain a vault are walked block by block.
 *
 * <p>Some servers keep sending {@code inactive} after the vault was opened, so right-clicking an
 * ominous vault also records it in {@link VaultOpenedStore} and later render passes treat it as opened.
 */
public class VaultESPModule extends Module {
    private static final int MAX_RENDER_LIMIT = 64;

    private final Setting<Integer> rangeSetting;
    private final Setting<Boolean> onlyUnopenedSetting;
    private final Setting<Boolean> notifySetting;
    private final Setting<Integer> maxRenderSetting;
    private final Setting<Color> unopenedColorSetting;
    private final Setting<Color> openedColorSetting;
    private final Setting<Integer> fillSetting;
    private final Setting<Integer> outlineSetting;
    private final Setting<MarkerMode> markerSetting;
    private final ActionSetting clearOpenedSetting;

    /** Confirmed ominous vault positions; mutated from the scan worker and read on the render thread. */
    private final Set<BlockPos> vaultPositions = Collections.synchronizedSet(new HashSet<>());
    /** Positions already reported through the chat notification. */
    private final Set<BlockPos> notifiedPositions = Collections.synchronizedSet(new HashSet<>());
    /** Cached surface height per {@code x/z}, used for the beam marker. */
    private final Map<Long, Integer> surfaceCache = new HashMap<>();
    private final ChunkScanner chunkScanner;

    /** Standalone opened record shared by every module session; loaded on first access. */
    private final VaultOpenedStore openedStore = VaultOpenedStore.shared();
    private Identifier lastDimension;

    public VaultESPModule() {
        super("VaultESP", ModuleCategory.RENDER);
        this.rangeSetting = this.add(new NumberSetting<>("Range", 500, 32, 500).withUnit("m"));
        this.onlyUnopenedSetting = this.add(new BooleanSetting("OnlyUnopened", true));
        this.notifySetting = this.add(new BooleanSetting("Notify", false));
        this.maxRenderSetting = this.add(new NumberSetting<>("MaxRender", 32, 4, MAX_RENDER_LIMIT));
        this.unopenedColorSetting = this.add(new ColorSetting("UnopenedColor", new Color(255, 200, 40, 220)));
        this.openedColorSetting = this.add(new ColorSetting("OpenedColor", new Color(160, 160, 160, 180)));
        this.fillSetting = this.add(new NumberSetting<>("Fill", 35, 0, 100).withUnit("%"));
        this.outlineSetting = this.add(new NumberSetting<>("Outline", 100, 0, 100).withUnit("%"));
        this.markerSetting = this.add(new EnumSetting<>("MarkerMode", MarkerMode.TRACER));
        this.clearOpenedSetting = this.add(new ActionSetting("ClearOpened", this::clearOpenedRecords)
                .withInfo(this::openedStatus));
        this.chunkScanner = new ChunkScanner(1,
                (java.util.function.BiConsumer<ChunkPos, LevelChunk>) ((chunkPosition, chunk) ->
                        this.scanChunk(chunkPosition, chunk)),
                (java.util.function.BiConsumer<ChunkPos, LevelChunk>) ((chunkPosition, chunk) ->
                        this.forgetChunk(chunkPosition)),
                (java.util.function.BiConsumer<BlockPos, BlockState>) (this::onBlockUpdated));
    }

    @Override
    public void onEnable() {
        this.clearState();
        this.chunkScanner.start();
        this.chunkScanner.scanLoadedChunks();
    }

    @Override
    public void onDisable() {
        this.chunkScanner.stop();
        this.clearState();
        this.openedStore.saveIfDirty();
    }

    private void clearState() {
        this.vaultPositions.clear();
        this.notifiedPositions.clear();
        this.surfaceCache.clear();
        this.lastDimension = null;
    }

    // ==================== Opened records ====================

    private String openedStatus() {
        return Translations.format("modules." + this.getName() + ".OpenedStatus", this.openedStore.count());
    }

    private void clearOpenedRecords() {
        int removed = this.openedStore.count();
        this.openedStore.clearAll();
        this.openedStore.saveIfDirty();
        this.notify("Cleared", removed);
    }

    private boolean isOpened(BlockPos position, BlockState state) {
        if (state.getValueOrElse(VaultBlock.STATE, VaultState.INACTIVE) != VaultState.INACTIVE) {
            return true;
        }
        return this.openedStore.isOpened(position.getX(), position.getY(), position.getZ());
    }

    /** Any right-click on an ominous vault counts as "opened", matching what the player expects to see. */
    @EventHandler
    private void onUseItemOn(MultiPlayerGameModeUseItemOnEvent event) {
        if (event.isCancelled() || this.isWorldUnavailable()) {
            return;
        }
        BlockPos position = event.getHitResult().getBlockPos();
        if (!isOminous(evernightMinecraft.level.getBlockState(position))) {
            return;
        }
        if (!this.openedStore.mark(position.getX(), position.getY(), position.getZ())) {
            return;
        }
        this.openedStore.saveIfDirty();
        if (this.notifySetting.getValue()) {
            this.notify("Recorded", position.getX(), position.getY(), position.getZ(), this.openedStore.count());
        }
    }

    // ==================== Scanning ====================

    @EventHandler
    private void onLocalPlayerTick(LocalPlayerTickEvent event) {
        if (this.isWorldUnavailable()) {
            return;
        }
        Identifier dimension = evernightMinecraft.level.dimension().identifier();
        if (!Objects.equals(dimension, this.lastDimension)) {
            this.lastDimension = dimension;
            this.vaultPositions.clear();
            this.notifiedPositions.clear();
            this.surfaceCache.clear();
            this.chunkScanner.scanLoadedChunks();
        }
        this.openedStore.saveIfDirty();
    }

    @EventHandler
    private void onMinecraftDisconnect(MinecraftDisconnectEvent event) {
        this.clearState();
    }

    private void scanChunk(ChunkPos chunkPosition, LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        int minimumY = chunk.getMinY();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            // Palette-level filter: skip sections without a single vault block.
            if (!section.maybeHas(VaultESPModule::isOminous)) {
                continue;
            }
            int baseY = minimumY + sectionIndex * 16;
            for (int localY = 0; localY < 16; localY++) {
                for (int localX = 0; localX < 16; localX++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        if (!isOminous(section.getBlockState(localX, localY, localZ))) {
                            continue;
                        }
                        this.track(new BlockPos(chunkPosition.getMinBlockX() + localX, baseY + localY,
                                chunkPosition.getMinBlockZ() + localZ));
                    }
                }
            }
        }
    }

    private void forgetChunk(ChunkPos chunkPosition) {
        int minimumX = chunkPosition.getMinBlockX();
        int maximumX = chunkPosition.getMaxBlockX();
        int minimumZ = chunkPosition.getMinBlockZ();
        int maximumZ = chunkPosition.getMaxBlockZ();
        this.vaultPositions.removeIf(position -> position.getX() >= minimumX && position.getX() <= maximumX
                && position.getZ() >= minimumZ && position.getZ() <= maximumZ);
        this.notifiedPositions.removeIf(position -> position.getX() >= minimumX && position.getX() <= maximumX
                && position.getZ() >= minimumZ && position.getZ() <= maximumZ);
    }

    private void onBlockUpdated(BlockPos position, BlockState state) {
        if (isOminous(state)) {
            this.track(position);
        } else {
            this.vaultPositions.remove(position);
            this.notifiedPositions.remove(position);
            this.surfaceCache.remove(surfaceKey(position.getX(), position.getZ()));
        }
    }

    private void track(BlockPos position) {
        if (!this.vaultPositions.add(position)) {
            return;
        }
        this.surfaceCache.remove(surfaceKey(position.getX(), position.getZ()));
        if (!this.notifySetting.getValue() || !this.notifiedPositions.add(position)) {
            return;
        }
        evernightMinecraft.execute(() -> {
            if (this.isWorldUnavailable() || evernightMinecraft.player == null) {
                return;
            }
            double distance = Math.sqrt(evernightMinecraft.player.getEyePosition()
                    .distanceToSqr(Vec3.atCenterOf(position)));
            this.notify("Found", position.getX(), position.getY(), position.getZ(), distance);
        });
    }

    private static long surfaceKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static boolean isOminous(BlockState state) {
        return state != null && state.is(Blocks.VAULT) && state.getValueOrElse(VaultBlock.OMINOUS, false);
    }

    // ==================== Rendering ====================

    @EventHandler
    private void onWorldRender(WorldRenderEvent.Main event) {
        if (this.isWorldUnavailable() || this.vaultPositions.isEmpty()) {
            return;
        }
        double range = this.rangeSetting.getValue();
        double squaredRange = range * range;
        Vec3 eye = evernightMinecraft.player.getEyePosition();
        List<BlockPos> visible = this.nearest(eye, squaredRange);
        if (visible.isEmpty()) {
            return;
        }
        MarkerMode marker = this.markerSetting.getValue();
        boolean drawTracer = marker == MarkerMode.TRACER || marker == MarkerMode.BOTH;
        boolean drawBeam = marker == MarkerMode.BEAM || marker == MarkerMode.BOTH;
        boolean drawFill = this.fillSetting.getValue() > 0;
        boolean drawOutline = this.outlineSetting.getValue() > 0;
        PoseStack poseStack = event.getPoseStack();
        LineMeshRenderer.begin(poseStack, 1.0F);
        for (BlockPos position : visible) {
            BlockState state = evernightMinecraft.level.getBlockState(position);
            if (!isOminous(state)) {
                continue;
            }
            boolean opened = this.isOpened(position, state);
            if (this.onlyUnopenedSetting.getValue() && opened) {
                continue;
            }
            Color color = opened ? this.openedColorSetting.getValue() : this.unopenedColorSetting.getValue();
            AABB bounds = new AABB(position);
            if (!RotationManager.isVisible(bounds)) {
                continue;
            }
            if (drawFill) {
                WorldBoxRenderer.fillBox(poseStack, bounds, ColorUtils.multiplyAlpha(color,
                        this.fillSetting.getValue() / 100.0F));
            }
            if (drawOutline) {
                WorldBoxRenderer.outlineBox(poseStack, bounds, ColorUtils.multiplyAlpha(color,
                        this.outlineSetting.getValue() / 100.0F), 1.0F);
            }
            if (drawTracer) {
                LineMeshRenderer.addTracer(Vec3.atCenterOf(position), color);
            }
            if (drawBeam) {
                this.drawBeam(poseStack, position, color);
            }
        }
        LineMeshRenderer.draw();
    }

    /** Positions inside the range sorted by distance, capped so a large vault cluster cannot stall a frame. */
    private List<BlockPos> nearest(Vec3 eye, double squaredRange) {
        List<BlockPos> inside = new ArrayList<>();
        synchronized (this.vaultPositions) {
            for (BlockPos position : this.vaultPositions) {
                if (eye.distanceToSqr(Vec3.atCenterOf(position)) <= squaredRange) {
                    inside.add(position);
                }
            }
        }
        inside.sort(Comparator.comparingDouble(position -> eye.distanceToSqr(Vec3.atCenterOf(position))));
        int limit = this.maxRenderSetting.getValue();
        if (inside.size() > limit) {
            return new ArrayList<>(inside.subList(0, limit));
        }
        return inside;
    }

    /** Thin beam from the vault roof up to the world surface, so a buried vault is visible from above. */
    private void drawBeam(PoseStack poseStack, BlockPos position, Color color) {
        Integer surface = this.surfaceCache.get(surfaceKey(position.getX(), position.getZ()));
        if (surface == null) {
            surface = evernightMinecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, position.getX(),
                    position.getZ());
            this.surfaceCache.put(surfaceKey(position.getX(), position.getZ()), surface);
        }
        if (surface <= position.getY() + 1) {
            return;
        }
        double halfWidth = 0.0625;
        AABB beam = new AABB(position.getX() + 0.5 - halfWidth, position.getY() + 1.0,
                position.getZ() + 0.5 - halfWidth, position.getX() + 0.5 + halfWidth, surface,
                position.getZ() + 0.5 + halfWidth);
        WorldBoxRenderer.fillBox(poseStack, beam, ColorUtils.multiplyAlpha(color, 0.5F));
        WorldBoxRenderer.outlineBox(poseStack, beam, color, 1.0F);
    }

    // ==================== Feedback ====================

    private void notify(String key, Object... arguments) {
        ChatUtils.printChat(Component.literal(Translations.format("modules." + this.getName() + "." + key, arguments)),
                ChatUtils.moduleSignature(this));
    }

    @Override
    public String getInfo() {
        return String.valueOf(this.vaultPositions.size());
    }

    /** How a found vault is marked: a tracer from the camera, a beam to the surface, or both. */
    public enum MarkerMode implements NamedEntry {
        NONE("None"),
        TRACER("Tracer"),
        BEAM("Beam"),
        BOTH("Both");

        private final String name;

        MarkerMode(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return this.name;
        }
    }
}
