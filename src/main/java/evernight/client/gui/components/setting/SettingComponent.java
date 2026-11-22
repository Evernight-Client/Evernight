package evernight.client.gui.components.setting;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientContext;
import evernight.client.gui.core.CloseState;
import evernight.client.gui.core.ScissorStack;
import evernight.client.gui.panels.Panel;
import evernight.client.gui.screens.clickgui.ClickGuiScreen;
import evernight.client.render.font.FontRenderer;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.render.text.Translations;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.FloatAnimation;
import evernight.client.gui.components.base.AbstractComponent;
import evernight.client.gui.components.ModuleComponent;

public abstract class SettingComponent<T> extends AbstractComponent {
    protected final FloatAnimation textScrollAnimation = new FloatAnimation();
    protected final CloseState parentCloseState;
    protected final Setting<T> setting;
    public boolean editing;
    private boolean wasHoveredForTextScroll;
    private long textScrollHoverStartedAtMillis;

    public SettingComponent(Panel panel, Setting<T> setting) {
        this(panel, () -> false, setting);
    }

    public SettingComponent(Panel panel, CloseState parentCloseState, Setting<T> setting) {
        super(panel, 0);
        this.parentCloseState = parentCloseState;
        this.setting = setting;
    }

    @Override
    public int getHeight() {
        return !this.setting.isVisible() ? 0 : this.getBaseHeight();
    }

    public boolean isUnavailable() {
        boolean unavailable = !this.setting.isVisible() || this.parentCloseState.isClosed();
        if (unavailable) {
            this.setting.expanded = false;
        }
        return unavailable;
    }

    public Setting<T> getSetting() {
        return this.setting;
    }

    protected ClickGuiScreen getClickGui() {
        return ClientContext.getClickGui();
    }

    public CloseState getParentCloseState() {
        return this.parentCloseState;
    }

    private void updateTextScroll(float textWidth) {
        if (textWidth <= this.panel.getWidth() - (4)) {
            this.textScrollAnimation.setValue(0.0F);
        } else {
            if (this.hovered) {
                if (!this.wasHoveredForTextScroll) {
                    this.textScrollHoverStartedAtMillis = System.currentTimeMillis();
                }
                if (System.currentTimeMillis() > this.textScrollHoverStartedAtMillis + 750L) {
                    this.textScrollAnimation.setTarget(textWidth - (this.panel.getWidth() - (4)), 500L);
                }
            } else {
                this.textScrollAnimation.setTarget(0.0F, 500L);
            }
            this.wasHoveredForTextScroll = this.hovered;
        }
    }

    protected void renderWithTextScroll(PoseStack poseStack, String text, Runnable renderer) {
        poseStack.pushPose();
        this.updateTextScroll(FontRenderer.INSTANCE.getWidth(text));
        float scrollOffset = -this.textScrollAnimation.getValue();
        // Custom panels pass CloseState.ALWAYS_OPEN instead of a ModuleComponent; they are already
        // clipped to the panel bounds, so only module rows need the extra scissor.
        boolean scissorRequired = scrollOffset != 0.0F && !(this instanceof EnumSettingComponent<?>)
                && this.parentCloseState instanceof ModuleComponent;
        if (scissorRequired) {
            ModuleComponent parentModule = (ModuleComponent) this.parentCloseState;
            GuiDrawUtils.flush();
            ScissorStack.pushIntersection(this.panel.getX() + this.getHorizontalPadding(), this.panel.getY()
                    + parentModule.getYOffset(), this.panel.getWidth()
                    - this.getHorizontalPadding(), parentModule.getHeight());
        }
        poseStack.translate(scrollOffset, 0.0F, 0.0F);
        renderer.run();
        if (scissorRequired) {
            GuiDrawUtils.flush();
            ScissorStack.pop();
        }
        poseStack.popPose();
    }

    @Override
    public void render(PoseStack poseStack, double mouseX, double mouseY) {
        if (this.getUiModule().settingsSetting.getValue()
                && this.isMouseOver(mouseX, mouseY)
                && this.parentCloseState instanceof ModuleComponent parentModule) {
            String moduleConfigName = parentModule.getModule().getConfigName();
            String settingConfigName = this.setting.getConfigName();
            String description = Translations.settingDescription(moduleConfigName, settingConfigName);
            if (description != null) {
                ClientContext.getClickGui().setTooltipText(description);
            }
        }
        super.render(poseStack, mouseX, mouseY);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isUnavailable()) {
            super.mouseClicked(mouseX, mouseY, button);
        }
    }
}