package evernight.client.gui.components.base;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.audio.SoundClip;
import evernight.client.gui.core.CursorType;
import evernight.client.gui.core.GuiElement;
import evernight.client.gui.panels.Panel;
import evernight.client.gui.screens.core.ClientScreen;
import evernight.client.modules.PermanentModule;
import evernight.client.render.color.Catppuccin;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.utils.core.FloatAnimation;
import net.minecraft.util.Mth;
import evernight.client.gui.components.ModuleComponent;

public abstract class AbstractComponent implements ClientAccess, GuiElement {
    protected final FloatAnimation hoverAnimation = new FloatAnimation();
    protected final Panel panel;
    protected int yOffset;
    protected int flags;
    protected boolean hovered = false;

    public AbstractComponent(Panel panel) {
        this(panel, 0);
    }

    public AbstractComponent(Panel panel, int yOffset) {
        this.panel = panel;
        this.yOffset = yOffset;
    }

    @Override
    public int getYOffset() {
        return this.yOffset;
    }

    @Override
    public void setYOffset(int yOffset) {
        this.yOffset = yOffset;
    }

    protected boolean isLeftClickEligible() {
        return !(this instanceof ModuleComponent component)
                || !(component.getModule() instanceof PermanentModule);
    }

    protected int getHorizontalPadding() {
        return this.hasFlag(ComponentFlag.PADDING_SHIFT) ? 2 : 1;
    }

    public Panel getPanel() {
        return this.panel;
    }

    protected float getHoverAnimationValue() {
        return this.hoverAnimation.getValue();
    }

    protected boolean supportsHoverSurface() {
        return true;
    }

    protected float clampYToPanel(float yCoordinate) {
        return Mth.clamp(yCoordinate, this.panel.getY() + this.panel.getHeaderHeight() - 2,
                this.panel.getY() + this.panel.getHeight());
    }

    public boolean hasFlag(ComponentFlag flag) {
        return (this.flags & flag.getMask()) == flag.getMask();
    }

    public void addFlags(ComponentFlag... flagsToAdd) {
        for (ComponentFlag flag : flagsToAdd) {
            this.flags |= flag.getMask();
        }
    }

    @Override
    public void render(PoseStack stack, double mouseX, double mouseY) {
        if (this.isMouseOver(mouseX, mouseY)) {
            if (!this.hovered && this.getUiModule().hoverSetting.getValue()
                    && !this.hasFlag(ComponentFlag.SILENT) && !this.hasFlag(ComponentFlag.SILENT_HOVER)) {
                SoundClip hoverSound = ClientContext.soundManager.getSound(this.getUiModule().hoverSoundSetting
                        .getValue());
                if (hoverSound != null) {
                    hoverSound.play(this.getUiModule().hoverVolumeSetting.getValue());
                }
            }
            this.hovered = true;
            ClientContext.getClickGui().markHovered();
            ClientScreen.cursor = CursorType.POINTER;
        } else {
            this.hovered = false;
        }
        this.hoverAnimation.setTarget(this.isMouseOver(mouseX, mouseY), 65L);
        float hoverValue = this.hoverAnimation.getValue();
        if (this.supportsHoverSurface() && hoverValue > 0.01F) {
            GuiDrawUtils.fillRect(stack, this.panel.getX() + 1.0F, this.panel.getY() + this.yOffset + 0.5F,
                    this.panel.getX() + this.panel.getWidth() - 1.0F, this.panel.getY() + this.yOffset
                            + this.getBaseHeight() - 0.5F, ColorUtils.toArgb(Catppuccin.SURFACE2, hoverValue * 0.12F));
        }
        GuiElement.super.render(stack, mouseX, mouseY);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX > this.panel.getX() && mouseX < this.panel.getX() + this.panel.getWidth() && mouseY
                > this.clampYToPanel(this.panel.getY() + this.yOffset) && mouseY
                < this.clampYToPanel(this.panel.getY() + this.getHeight() + this.yOffset);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (this.isMouseOver(mouseX, mouseY) && !this.hasFlag(ComponentFlag.SILENT)) {
            if (button == 0 && this.isLeftClickEligible()) {
                if (this.getUiModule().leftClickSetting.getValue()) {
                    SoundClip leftClickSound = ClientContext.soundManager.getSound(this.getUiModule()
                            .leftClickSoundSetting.getValue());
                    if (leftClickSound != null) {
                        leftClickSound.play(this.getUiModule().leftClickVolumeSetting.getValue());
                    }
                }
            } else if (button == 1 && this.getUiModule().rightClickSetting.getValue()) {
                SoundClip rightClickSound = ClientContext.soundManager.getSound(this.getUiModule()
                        .rightClickSoundSetting.getValue());
                if (rightClickSound != null) {
                    rightClickSound.play(this.getUiModule().rightClickVolumeSetting.getValue());
                }
            }
        }
    }
}