package evernight.client.modules.render.VoidESP;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.render.WorldRenderEvent;
import evernight.client.managers.player.RotationManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.mesh.WorldBoxRenderer;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class VoidESPModule extends Module {
    final Setting<Boolean> fadeSetting;
    final Setting<Boolean> colorsSetting;
    private final Setting<Float> lineWidthSetting;
    private final Setting<Integer> heightSetting;
    private final Setting<Integer> radiusSetting;
    private final Setting<Float> fadeRadiusSetting;
    private final Setting<Color> fillSetting;
    private final Setting<Color> lineSetting;
    private final Set voidBlocks;
    private boolean resetPending;

    public VoidESPModule() {
        super("VoidESP", ModuleCategory.RENDER);
        this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 3.0F));
        String heightKey = "Height";
        this.heightSetting = this.add(new NumberSetting<>(heightKey, 0, -1, 4).withUnit("m"));
        this.radiusSetting = this.add(new NumberSetting<>("Radius", 10, 1, 16).withUnit("m"));
        this.fadeSetting = this.add(new BooleanSetting("Fade", true));
        Predicate<Object> fadeRadiusVisibility = ignored -> {
            return this.fadeSetting.getValue();
        };
        this.fadeRadiusSetting = this.add(new NumberSetting<>("FadeRadius", 7.0F, 1.0F, 16.0F, fadeRadiusVisibility).withUnit("m"));
        this.colorsSetting = this.add(new BooleanSetting("Colors", true).markAsGroup().markVisibilityDependency());
        Predicate<Object> fillVisibility = ignored -> {
            Setting<Boolean> colors = this.colorsSetting;
            return colors.isExpanded();
        };
        this.fillSetting = this.add(new ColorSetting("Fill", new Color(822018048, true), fillVisibility));
        Predicate<Object> lineVisibility = ignored -> {
            Setting<Boolean> colors = this.colorsSetting;
            return colors.isExpanded();
        };
        this.lineSetting = this.add(new ColorSetting("Line", new Color(-1140916224, true), lineVisibility));
        this.voidBlocks = new HashSet();
    }

    private static boolean isBedrock(BlockPos position) {
        return WorldUtils.getBlock(position) == Blocks.BEDROCK;
    }

    private static int getColumnHeight(VoidESPModule owner, BlockPos position) {
        int columnHeight = (owner.heightSetting.getValue());
        if (columnHeight <= 1) {
            return columnHeight;
        }
        for (int offset = 0; offset <= columnHeight; offset++) {
            ClientLevel level = ClientAccess.evernightMinecraft.level;
            BlockPos columnPosition = position.above(offset);
            BlockState blockState = level.getBlockState(columnPosition);
            if (!blockState.canBeReplaced()) {
                columnHeight -= columnHeight - offset;
            }
        }
        return Math.max(columnHeight, 1);
    }

    private boolean shouldRemoveVoidBlock(BlockPos position) {
        GameRenderer gameRenderer = ClientAccess.evernightMinecraft.gameRenderer;
        Vec3 cameraPosition = (gameRenderer.getMainCamera()).position();
        Integer radiusValue = this.radiusSetting.getValue();
        if (!position.closerToCenterThan(cameraPosition, radiusValue.intValue())) {
            return true;
        }
        return isBedrock(position);
    }

    private boolean isVoidBlock(BlockPos position) {
        return position.getY() == ClientAccess.evernightMinecraft.level.getMinY() && !isBedrock(position);
    }

    @Override
    public String getInfo() {
        int blockCount = this.voidBlocks.size();
        return String.valueOf(blockCount);
    }

    @Override
    public void onDisable() {
        this.voidBlocks.clear();
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        if (this.resetPending) {
            this.voidBlocks.clear();
            this.resetPending = false;
        }
        this.voidBlocks.removeIf((java.util.function.Predicate<net.minecraft.core.BlockPos>) this::shouldRemoveVoidBlock);
        GameRenderer gameRenderer = ClientAccess.evernightMinecraft.gameRenderer;
        Vec3 cameraPosition = (gameRenderer.getMainCamera()).position();
        float scanRadius = (this.radiusSetting.getValue()).intValue();
        Stream voidBlockStream = WorldUtils.streamSphereBlocks(cameraPosition, scanRadius).filter(this::isVoidBlock);
        Set destinationBlocks = this.voidBlocks;
        voidBlockStream.forEach((java.util.function.Consumer<net.minecraft.core.BlockPos>) destinationBlocks::add);
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent.Main event) {
        Iterator positions = this.voidBlocks.iterator();
        while (positions.hasNext()) {
            BlockPos position = (BlockPos) positions.next();
            AABB blockBox = new AABB(position);
            int baseY = position.getY();
            double maxY = baseY + getColumnHeight(this, position);
            AABB box = blockBox.setMaxY(maxY);
            if (RotationManager.isVisible(box)) {
                GameRenderer gameRenderer = ClientAccess.evernightMinecraft.gameRenderer;
                Vec3 cameraPosition = (gameRenderer.getMainCamera()).position();
                Color fillColor = this.fillSetting.getValue();
                Color lineColor = this.lineSetting.getValue();
                Vec3 blockCenter = position.getCenter();
                double distance = cameraPosition.distanceTo(blockCenter);
                Boolean fadeEnabled = this.fadeSetting.getValue();
                if (fadeEnabled) {
                    Float fadeRadiusValue = this.fadeRadiusSetting.getValue();
                    if (distance >= fadeRadiusValue.floatValue()) {
                        Float fadeStartValue = this.fadeRadiusSetting.getValue();
                        double distancePastFadeStart = distance - fadeStartValue.floatValue();
                        float renderRadius = (this.radiusSetting.getValue()).intValue();
                        Float fadeRangeStartValue = this.fadeRadiusSetting.getValue();
                        double fadeProgress = distancePastFadeStart / (renderRadius - fadeRangeStartValue);
                        float alphaFactor = 1.0F - (float) Mth.clamp(fadeProgress, 0.0D, 1.0D);
                        Color fillAlphaSource = fillColor;
                        fillColor = ColorUtils.withAlpha(fillColor, (int) (alphaFactor * fillAlphaSource.getAlpha())
                        );
                        Color lineAlphaSource = lineColor;
                        lineColor = ColorUtils.withAlpha(lineColor, (int) (alphaFactor * lineAlphaSource.getAlpha())
                        );
                    }
                }
                WorldBoxRenderer.fillBox(event.getPoseStack(), box, fillColor);
                PoseStack poseStack = event.getPoseStack();
                Float lineWidthValue = this.lineWidthSetting.getValue();
                WorldBoxRenderer.outlineBox(poseStack, box, lineColor, lineWidthValue);
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof ClientboundRespawnPacket)) {
            if (!(event.getPacket() instanceof ClientboundLoginPacket)) {
                return;
            }
        }
        this.resetPending = true;
    }
}