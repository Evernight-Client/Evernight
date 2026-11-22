package evernight.client.modules.hud.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.gui.core.ScissorStack;
import evernight.client.modules.HudWidgetModule;
import evernight.client.modules.exploit.NewChunks.ChunkKind;
import evernight.client.modules.exploit.NewChunks.DetectedChunk;
import evernight.client.modules.exploit.NewChunks.NewChunksModule;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.render.util.RenderUtils;
import evernight.client.render.hud.ResizableHudWidgetRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

import java.awt.*;
import java.util.Iterator;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class MapModule extends HudWidgetModule {
    private static final NewChunksModule newChunks;
    private static final Identifier NAVIGATION_TEXTURE;

    static {
        newChunks = ClientContext.getModule(NewChunksModule.class);
        String texturePath = "textures/nav.png";
        String textureNamespace = "evernight";
        NAVIGATION_TEXTURE = Identifier.fromNamespaceAndPath(textureNamespace, texturePath);
    }

    private final Setting<PointerMode> pointerModeSetting;
    private final Setting<Float> widthSetting;
    private final Setting<Float> heightSetting;
    private final Setting<Color> pointerColorSetting;
    private final Setting<Color> backgroundSetting;
    private final Setting<Color> outlineSetting;

    public MapModule() {
        super("Map", "minimap");
        this.pointerModeSetting = this.add(new EnumSetting<>("Pointer", MapModule.PointerMode.ARROW));
        this.widthSetting = this.add(new NumberSetting<>(
                "Width",
                76.0F, 50.0F, 500.0F));
        this.heightSetting = this.add(new NumberSetting<>(
                "Height",
                76.0F, 50.0F, 250.0F));
        this.pointerColorSetting = this.add(new ColorSetting("Pointer", Color.white));
        this.backgroundSetting = this.add(new ColorSetting(
                "Background",
                new Color(10, 10, 10, 50)));
        this.outlineSetting = this.add(new ColorSetting(
                "Outline",
                new Color(10, 10, 10, 100)));
        this.pointerColorSetting.setConfigName("PointerColor");
        this.pointerColorSetting.forceOpaqueColor();
        ResizableHudWidgetRenderer widget = new ResizableHudWidgetRenderer(this);
        widget.enableResizing(this);
        this.setRenderer(widget);
    }

    @Override
    public float[] calculateDimensions() {
        float[] dimensions = new float[2];
        Setting<Float> widthOption = this.widthSetting;
        Float width = widthOption.getValue();
        dimensions[0] = width;
        Setting<Float> heightOption = this.heightSetting;
        Float height = heightOption.getValue();
        dimensions[1] = height;
        return dimensions;
    }

    private boolean isChunkWithinRange(ChunkPos chunk) {
        Setting<Float> widthOption = this.widthSetting;
        Float width = widthOption.getValue();
        double radiusOperand = width.floatValue();
        Setting<Float> heightOption = this.heightSetting;
        Float height = heightOption.getValue();
        double mapHeight = height.floatValue();
        double mapWidth = radiusOperand;
        radiusOperand = Math.hypot(mapWidth, mapHeight);
        double radius = radiusOperand / 16.0D;
        int chunkRadius = (int) Math.ceil(radius);
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        ChunkPos playerChunk = player.chunkPosition();
        return chunk.distanceSquared(playerChunk) <= chunkRadius * chunkRadius;
    }

    @Override
    public void renderContent(PoseStack poses) {
        float[] widgetSize = this.renderer.getDimensions();
        if (widgetSize != null) {
            int[] renderColorValues = RenderUtils.getRenderColor();
            int previousRenderState = renderColorValues[3];
            RenderUtils.setColorAlpha(255);
            GuiDrawUtils.flush();
            RenderUtils.setColorAlpha(previousRenderState);
            RenderUtils backgroundRenderer = RenderUtils.INSTANCE;
            Setting<Float> backgroundWidthOption = this.widthSetting;
            Float backgroundWidth = backgroundWidthOption.getValue();
            float rectangleWidth = backgroundWidth;
            Setting<Float> backgroundHeightOption = this.heightSetting;
            Float backgroundHeight = backgroundHeightOption.getValue();
            float rectangleHeight = backgroundHeight;
            Setting<Color> backgroundColorOption = this.backgroundSetting;
            backgroundRenderer.fillRect(
                    poses, 0.0F, 0.0F, rectangleWidth, rectangleHeight, backgroundColorOption.getValue());
            RenderUtils outlineRenderer = RenderUtils.INSTANCE;
            float outlineX = -1.0F;
            float outlineY = -1.0F;
            Setting<Float> outlineWidthOption = this.widthSetting;
            Float outlineWidth = outlineWidthOption.getValue();
            rectangleWidth = outlineWidth;
            Setting<Float> outlineHeightOption = this.heightSetting;
            Float outlineHeight = outlineHeightOption.getValue();
            rectangleHeight = outlineHeight;
            Setting<Color> outlineColorOption = this.outlineSetting;
            outlineRenderer.drawRectOutline(
                    poses, outlineX, outlineY, rectangleWidth, rectangleHeight, outlineColorOption.getValue());
            Setting<Float> centerWidthOption = this.widthSetting;
            Float centerWidth = centerWidthOption.getValue();
            float mapWidth = centerWidth;
            float centerX = mapWidth / 2.0F;
            Setting<Float> centerHeightOption = this.heightSetting;
            Float centerHeight = centerHeightOption.getValue();
            float mapHeight = centerHeight;
            float centerY = mapHeight / 2.0F;
            poses.pushPose();
            float mapDepth = 0.0F;
            poses.translate(centerX, centerY, mapDepth);
            NewChunksModule dungeonSource = newChunks;
            Iterator chunks = dungeonSource.getDetectedDungeons().iterator();
            while (chunks.hasNext()) {
                BlockPos dungeonPosition = (BlockPos) chunks.next();
                ChunkPos dungeonChunk = new ChunkPos(dungeonPosition);
                if (this.isChunkWithinRange(dungeonChunk)) {
                    Setting<Color> dungeonColorOption = newChunks.dungeonFillSetting;
                    Color dungeonFill = dungeonColorOption.getValue();
                    Color dungeonColor = ColorUtils.withAlpha(dungeonFill, 0.4F);
                    this.renderChunk(poses, dungeonChunk, dungeonColor);
                }
            }
            NewChunksModule visibleChunkSource = newChunks;
            chunks = visibleChunkSource.getVisibleChunks().iterator();
            while (chunks.hasNext()) {
                DetectedChunk visibleChunk = (DetectedChunk) chunks.next();
                ChunkPos chunkPosition = visibleChunk.chunkPosition();
                if (this.isChunkWithinRange(chunkPosition)) {
                    ChunkKind visibleKind = visibleChunk.kind();
                    NewChunksModule moduleForKindColors = newChunks;
                    if (visibleKind.getColors(moduleForKindColors) != null) {
                        ChunkKind visibleColoredKind = visibleChunk.kind();
                        NewChunksModule moduleForFillColor = newChunks;
                        Color chunkColor = visibleColoredKind.getColors(moduleForFillColor)[0];
                        chunkColor = ColorUtils.withAlpha(chunkColor, 0.25F);
                        ChunkPos drawnChunkPosition = visibleChunk.chunkPosition();
                        Color drawnChunkColor = chunkColor;
                        this.renderChunk(poses, drawnChunkPosition, drawnChunkColor);
                    }
                }
            }
            poses.popPose();
            ScissorStack.push((int) this.renderer.getX(), (int) this.renderer.getY(), (int) widgetSize[0],
                    (int) widgetSize[1]);
            GuiDrawUtils.flush();
            ScissorStack.pop();
            this.renderPointer(poses, centerX, centerY);
        }
    }

    private void renderChunk(PoseStack poses, ChunkPos chunk, Color color) {
        GameRenderer gameRenderer = ClientAccess.evernightMinecraft.gameRenderer;
        Camera camera = gameRenderer.getMainCamera();
        Vec3 cameraPosition = camera.position();
        double cameraCoordinate = cameraPosition.x;
        float cameraOffsetX = (float) (cameraCoordinate - chunk.getMinBlockX());
        float scaledOffsetX = cameraOffsetX / 2.0F;
        float offsetX = scaledOffsetX - 8.0F;
        cameraCoordinate = cameraPosition.z;
        float cameraOffsetZ = (float) (cameraCoordinate - chunk.getMinBlockZ());
        float scaledOffsetZ = cameraOffsetZ / 2.0F;
        float offsetZ = scaledOffsetZ - 8.0F;
        poses.pushPose();
        float markerDepth = 0.0F;
        poses.translate(offsetX, offsetZ, markerDepth);
        float markerWidth = 8.0F;
        GuiDrawUtils.fillRect(poses, 0.0F, 0.0F, markerWidth, 8.0F, color);
        poses.popPose();
    }

    private void renderPointer(PoseStack poses, float centerX, float centerY) {
        poses.pushPose();
        float pointerDepth = 0.0F;
        poses.translate(centerX, centerY, pointerDepth);
        Setting<PointerMode> dotModeOption = this.pointerModeSetting;
        if (dotModeOption.getValue() == MapModule.PointerMode.DOT) {
            RenderUtils dotRenderer = RenderUtils.INSTANCE;
            float dotX = -1.0F;
            float dotY = -1.0F;
            float dotWidth = 1.0F;
            float dotHeight = 1.0F;
            Setting<Color> dotColorOption = this.pointerColorSetting;
            dotRenderer.fillRect(poses, dotX, dotY, dotWidth, dotHeight, dotColorOption.getValue());
        } else {
            Setting<PointerMode> arrowModeOption = this.pointerModeSetting;
            if (arrowModeOption.getValue() == MapModule.PointerMode.ARROW) {
                float arrowScaleX = 0.05F;
                float arrowScaleY = 0.05F;
                float arrowScaleZ = 1.0F;
                poses.scale(arrowScaleX, arrowScaleY, arrowScaleZ);
                Axis rotationAxis = Axis.ZP;
                float rotationOffset = 45.0F;
                GameRenderer gameRenderer = ClientAccess.evernightMinecraft.gameRenderer;
                Camera camera = gameRenderer.getMainCamera();
                float rotationDegrees = rotationOffset + camera.yRot();
                Quaternionf arrowRotation = rotationAxis.rotationDegrees(rotationDegrees);
                poses.mulPose(arrowRotation);
                int[] renderStateValues = RenderUtils.getRenderColor();
                float renderAlpha = renderStateValues[3];
                Setting<Color> arrowColorOption = this.pointerColorSetting;
                int pointerArgb = ColorUtils.toArgb(arrowColorOption.getValue(), renderAlpha);
                Identifier texture = NAVIGATION_TEXTURE;
                GuiDrawUtils.drawTexture(poses, texture, -128, -128, 0.0F, 0.0F, 256, 256, 256, 256, pointerArgb);
                RenderBuffers renderBuffers = ClientAccess.evernightMinecraft.renderBuffers();
                BufferSource bufferSource = renderBuffers.bufferSource();
                bufferSource.endBatch();
            }
        }
        poses.popPose();
    }

    private enum PointerMode implements NamedEntry {
        NONE("None"),
        ARROW("Arrow"),
        DOT("Dot");
        private final String displayName;

        PointerMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }
}