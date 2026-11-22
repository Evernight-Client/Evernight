package evernight.client.modules.render.Borders;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.render.WorldRenderEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.util.RenderUtils;
import evernight.client.render.mesh.WorldBoxRenderer;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.settings.core.SettingLabelCondition;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.awt.*;

public class BordersModule extends Module {
    private final Setting<Color> chunkSetting;
    private final Setting<Color> mapSetting;
    private final Setting<Float> lineWidthSetting;
    private final Setting<Integer> mapSizeSetting;
    private final Setting<Integer> levelSetting;

    public BordersModule() {
        super("Borders", ModuleCategory.RENDER);
        this.chunkSetting = this.add(new ColorSetting("Chunk", new Color(-65536, true)));
        this.mapSetting = this.add(new ColorSetting("Map", new Color(-65281, true)));
        this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 3.0F));
        this.mapSizeSetting = this.add(new NumberSetting<>("MapSize", 0, 0, 4));
        this.levelSetting = this.add(new NumberSetting<>("Level", -1, -1, 320));
        SettingLabelCondition minimumBoundary = SettingLabelCondition.MIN;
        this.levelSetting.setBoundaryLabel("~", minimumBoundary);
    }

    private static int getChunkRegionSize() {
        return 16;
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent.Main event) {
        Color chunkColor = this.chunkSetting.getValue();
        if (chunkColor.getAlpha() != 0) {
            BordersModule.Region chunkRegion = this.createChunkRegion();
            PoseStack chunkPoseStack = event.getPoseStack();
            Color chunkDrawColor = this.chunkSetting.getValue();
            int chunkLevel = (this.levelSetting.getValue());
            float chunkDrawLineWidth = (this.lineWidthSetting.getValue());
            chunkRegion.render(chunkPoseStack, chunkDrawColor, chunkLevel, chunkDrawLineWidth);
        }
        Color mapColor = this.mapSetting.getValue();
        if (mapColor.getAlpha() != 0) {
            BordersModule.Region mapRegion = this.createMapRegion();
            PoseStack mapPoseStack = event.getPoseStack();
            Color mapDrawColor = this.mapSetting.getValue();
            int mapDrawLevel = (this.levelSetting.getValue());
            float mapDrawLineWidth = (this.lineWidthSetting.getValue());
            mapRegion.render(mapPoseStack, mapDrawColor, mapDrawLevel, mapDrawLineWidth);
        }
    }

    private Region createChunkRegion() {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        ChunkPos chunk = player.chunkPosition();
        return new BordersModule.Region(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(), getChunkRegionSize());
    }

    private Region createMapRegion() {
        int mapSize = 128 * (1 << this.mapSizeSetting.getValue());
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        int mapColumn = Mth.floor((player.getBlockX() + 64.0D) / mapSize);
        int mapRow = Mth.floor((player.getBlockZ() + 64.0D) / mapSize);
        int centerX = mapColumn * mapSize + mapSize / 2 - 64;
        int centerZ = mapRow * mapSize + mapSize / 2 - 64;
        return new BordersModule.Region(centerX, centerZ, mapSize);
    }

    private record Region(int centerX, int centerZ, int size) {
        public void render(PoseStack poseStack, Color color, int level, float lineWidth) {
            double renderY = level;
            if (level == -1) {
                LocalPlayer player = ClientAccess.evernightMinecraft.player;
                float partialTick = RenderUtils.getPartialTick();
                renderY = player.getPosition(partialTick).y;
            }
            AABB bounds = this.getBounds(renderY);
            WorldBoxRenderer.outlineBox(poseStack, bounds, color, lineWidth);
        }

        public AABB getBounds(double level) {
            int regionSize = this.size;
            int halfSize = regionSize / 2;
            return new AABB(this.centerX - halfSize, level, this.centerZ - halfSize, this.centerX + halfSize,
                    level, this.centerZ + halfSize);
        }
    }
}