package evernight.client.modules.client.Fonts;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.events.core.ClientInitializationEvent;
import evernight.client.events.core.EventHandler;
import evernight.client.events.render.HudRenderEvent;
import evernight.client.events.ui.MinecraftTickEvent;
import evernight.client.gui.screens.core.ClientScreen;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.font.AtlasTextRenderer;
import evernight.client.render.font.FontRenderer;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.render.gui.GuiProjectionUtils;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.FontsStyleChoice;
import net.minecraft.client.Minecraft;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class FontsModule extends Module {
    private static final Runnable RELOAD_RENDERER = () -> {
        if (ClientAccess.evernightMinecraft.player != null
                || ClientAccess.evernightMinecraft.screen instanceof ClientScreen) {
            FontRenderer.INSTANCE.setRenderer(FontRenderer.INSTANCE.createRenderer());
        }
    };
    private static final AtomicBoolean reloadRequested = new AtomicBoolean(false);
    public static FontsModule INSTANCE;
    private static int reloadCooldownTicks;
    public final Setting<FontsStyleChoice> styleSetting;
    public final Setting<Integer> sizeSetting;
    public final Setting<Boolean> antiAliasSetting;
    public final Setting<Boolean> chatSetting;
    public final Setting<Integer> shiftSetting;
    public final Setting<Integer> translateSetting;
    public final Setting<Float> shadowSetting;
    private final Setting<Boolean> offsetsSetting;
    private final Vector2f transformedPosition;
    private final List<QueuedDraw> queuedDraws;

    public FontsModule() {
        super("Fonts", ModuleCategory.CLIENT);
        FontsStyleChoice defaultStyle = FontsStyleChoice.PLAIN;
        this.styleSetting = this.add(new EnumSetting<>("Style", defaultStyle));
        this.sizeSetting = this.add(new NumberSetting<>("Size", 16, 12, 20));
        this.antiAliasSetting = this.add(new BooleanSetting("AntiAlias", true));
        this.chatSetting = this.add(new BooleanSetting("Chat", false));
        BooleanSetting offsets = new BooleanSetting("Offsets", false);
        offsets.markAsGroup();
        offsets.markVisibilityDependency();
        this.offsetsSetting = this.add(offsets);
        this.shiftSetting = this.add(new NumberSetting<>("Shift", -2, -4, 4).dependsOn(this.offsetsSetting));
        this.translateSetting = this.add(new NumberSetting<>("Translate", -1, -4, 4).dependsOn(this.offsetsSetting));
        NumberSetting<Float> shadow = new NumberSetting<>("Shadow", 0.7F, 0.5F, 1.0F);
        this.shadowSetting = this.add(shadow.dependsOn(this.offsetsSetting));
        this.transformedPosition = new Vector2f();
        this.queuedDraws = new ArrayList<>();
        this.styleSetting.setChangeCallback(RELOAD_RENDERER);
        this.antiAliasSetting.setChangeCallback(RELOAD_RENDERER);
        this.sizeSetting.setChangeCallback(() -> reloadRequested.set(true));
        INSTANCE = this;
        ClientAccess.eventBus.subscribeStatic(FontsModule.class);
        this.setDrawn(false);
    }

    @EventHandler
    private static void onClientInitialized(ClientInitializationEvent event) {
        INSTANCE.setToggled(true);
        reloadRequested.set(true);
    }

    @EventHandler
    private static void onMinecraftTick(MinecraftTickEvent event) {
        Minecraft minecraft = ClientAccess.evernightMinecraft;
        Window window = minecraft.getWindow();
        long windowHandle = window.handle();
        if (GLFW.glfwGetMouseButton(windowHandle, 0) != 1) {
            AtomicBoolean requestedReloadFlag = reloadRequested;
            if (requestedReloadFlag.get()) {
                reloadRequested.set(false);
                if (INSTANCE.isToggled()) {
                    FontRenderer.INSTANCE.setRenderer(FontRenderer.INSTANCE.createRenderer());
                    reloadCooldownTicks = 3;
                }
            }
        }
        if (reloadCooldownTicks > 0) {
            reloadCooldownTicks--;
        }
    }

    public boolean hasReloadCooldown() {
        return reloadCooldownTicks > 0;
    }

    @Override
    public void onToggle() {
        reloadCooldownTicks = 3;
        if (this.isToggled()) {
            reloadRequested.set(true);
        } else {
            this.queuedDraws.clear();
        }
    }

    private void flushQueuedDraws(PoseStack poseStack) {
        if (!this.queuedDraws.isEmpty()) {
            GuiDrawUtils.flush(true);
            for (QueuedDraw draw : this.queuedDraws) {
                draw.render(poseStack);
            }
            this.queuedDraws.clear();
            GuiDrawUtils.flush(true);
        }
    }

    public void flushFocusedChat() {
        if (this.queuedDraws.isEmpty()) {
            return;
        }
        GuiProjectionUtils.beginGuiProjection();
        try {
            this.flushQueuedDraws(new PoseStack());
        } finally {
            GuiProjectionUtils.restoreProjection();
        }
    }

    public void enqueueText(
            Matrix3x2fc transform, FormattedCharSequence text, float x, float y, int color, boolean shadow) {
        this.transformedPosition.set(0.0F);
        transform.transformPosition(x, y, this.transformedPosition);
        this.queuedDraws.add(new FontsModule.QueuedText(text, this.transformedPosition.x(), this.transformedPosition.y(), color, shadow));
    }

    public void enqueueRectangle(
            Matrix3x2fc transform, float left, float top, float right, float bottom, int color) {
        this.transformedPosition.set(0.0F);
        transform.transformPosition(left, top, this.transformedPosition);
        left = this.transformedPosition.x();
        top = this.transformedPosition.y();
        transform.transformPosition(right, bottom, this.transformedPosition);
        this.queuedDraws.add(new FontsModule.QueuedRectangle(left, top, this.transformedPosition.x(), this.transformedPosition.y(), color));
    }

    @EventHandler
    private void onHudRender(HudRenderEvent.Unconditional event) {
        this.flushQueuedDraws(event.getPoseStack());
    }

    interface QueuedDraw {
        void render(PoseStack poseStack);
    }

    private record QueuedText(FormattedCharSequence text, float x, float y, int color, boolean shadow
    ) implements FontsModule.QueuedDraw {
        @Override
        public void render(PoseStack poseStack) {
            float chatScale = ClientAccess.evernightMinecraft.options.chatScale().get().floatValue();
            poseStack.pushPose();
            poseStack.scale(chatScale, chatScale, 1.0F);
            float scaledX = this.x / chatScale;
            float scaledY = this.y / chatScale;
            if (FontsModule.INSTANCE.isToggled()) {
                AtlasTextRenderer renderer = FontRenderer.INSTANCE.getRenderer();
                renderer.draw(poseStack, this.text, scaledX, scaledY, this.color, this.shadow);
            } else {
                ClientAccess.evernightMinecraft.font.drawInBatch(
                        this.text, scaledX, scaledY, this.color, this.shadow,
                        poseStack.last().pose(), ClientAccess.evernightMinecraft.renderBuffers().bufferSource(),
                        net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 15728880);
            }
            poseStack.popPose();
        }
    }

    private record QueuedRectangle(float left, float top, float right, float bottom, int color
    ) implements FontsModule.QueuedDraw {
        @Override
        public void render(PoseStack poseStack) {
            if (!FontsModule.INSTANCE.isToggled()) {
                return;
            }
            GuiDrawUtils.fillRect(poseStack, this.left, this.top, this.right, this.bottom, this.color);
        }
    }
}