package evernight.client.gui.components.setting;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.config.json.EnumJsonConverter;
import evernight.client.gui.core.CloseState;
import evernight.client.gui.panels.Panel;
import evernight.client.modules.client.UI.UIModule;
import evernight.client.render.font.FontRenderer;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.core.NamedEntry;
import evernight.client.utils.core.ClampedInterpolatedFloat;
import evernight.client.utils.core.InterpolatedFloat;

import java.util.ArrayList;
import java.util.regex.Matcher;

public class EnumSettingComponent<E extends Enum<E> & NamedEntry> extends SettingComponent<E> {
    private final ArrayList<EnumOptionRow<E>> optionRows = new ArrayList<>();
    private final InterpolatedFloat selectionOffsetAnimation = new InterpolatedFloat(() -> 2.0F
            * this.getUiModule().animSpeedSetting.getValue(), true);
    private final ClampedInterpolatedFloat fillAnimation = new ClampedInterpolatedFloat(() -> 2.0F
            * UIModule.INSTANCE.animSpeedSetting.getValue(), true);
    private final int selectableValueCount;
    private boolean expanded = false;
    private int componentYOffset;
    private int cachedX;
    private int cachedY;
    private int selectedIndex;

    public EnumSettingComponent(Panel panel, CloseState parentCloseState, EnumSetting<E> setting) {
        super(panel, parentCloseState, setting);
        int rowYOffset = this.getBaseHeight();
        int selectableIndex = 0;
        for (E candidate : this.setting.getDefaultValue().getDeclaringClass().getEnumConstants()) {
            this.optionRows.add(new EnumOptionRow<>(this, candidate, rowYOffset, selectableIndex));
            rowYOffset += this.getBaseHeight();
            selectableIndex++;
        }
        this.selectableValueCount = selectableIndex;
    }

    private E nextValue() {
        E value = this.setting.getValue();
        return value.getDeclaringClass().cast(EnumJsonConverter.nextValue(value));
    }

    public void setSelectedIndex(int index) {
        this.selectedIndex = index;
    }

    @Override
    public void setYOffset(int yOffset) {
        this.componentYOffset = yOffset;
    }

    @Override
    public int getHeight() {
        if (this.isUnavailable()) {
            InterpolatedFloat selectionAnimation = this.selectionOffsetAnimation;
            float selectionOffset = -this.getBaseHeight();
            selectionAnimation.setValue(selectionOffset);
            return 0;
        } else {
            return this.expanded ? this.getBaseHeight() + this.selectableValueCount * this.getBaseHeight() + 1
                    : super.getHeight();
        }
    }

    @Override
    public void init() {
        this.setSelectedIndex((this.setting.getValue()).ordinal());
    }

    public int getY() {
        return this.cachedY;
    }

    public int getX() {
        return this.cachedX;
    }

    @Override
    public void render(PoseStack poseStack, double mouseX, double mouseY) {
        if (this.isUnavailable()) {
            this.fillAnimation.reset();
            return;
        }
        super.render(poseStack, mouseX, mouseY);
        this.fillAnimation.setTarget(true);
        if (this.expanded) {
            this.selectionOffsetAnimation.setTarget(this.selectedIndex * this.getBaseHeight());
            float selectionOffset = this.selectionOffsetAnimation.updateAndGet();
            float firstRowY = this.panel.getY() + this.componentYOffset + this.getBaseHeight() - 0.5F;
            GuiDrawUtils.fillRect(poseStack, this.panel.getX() + this.getHorizontalPadding()
                    + 1, Math.max(selectionOffset + firstRowY, firstRowY), this.panel.getX()
                    + this.panel.getWidth() - (2), this.panel.getY() + this.componentYOffset
                    + this.getBaseHeight() * (2) + selectionOffset
                    - 0.5F, this.getUiModule().colorSetting.getValue());
        } else {
            this.selectionOffsetAnimation.setTarget(-this.getBaseHeight());
        }
        int edgeInset = this.expanded ? 1 : 0;
        float fillWidth = Math.max(this.panel.getWidth() * this.fillAnimation.updateAndGet() - 1.0F, 2.0F);
        GuiDrawUtils.fillRect(poseStack, this.panel.getX() + this.getHorizontalPadding()
                + edgeInset, this.panel.getY() + this.componentYOffset + 0.5F + edgeInset, this.panel.getX()
                + fillWidth - edgeInset, this.panel.getY() + this.componentYOffset + this.getBaseHeight()
                - 0.5F, this.getUiModule().colorSetting.getValue());
        if (this.expanded) {
            this.optionRows.forEach(row -> row.render(poseStack, mouseX, mouseY));
            if (UIModule.INSTANCE.lineSetting.getValue()) {
                GuiDrawUtils.drawRectOutline(poseStack, this.panel.getX()
                        + this.getHorizontalPadding(), this.panel.getY() + this.componentYOffset
                        + 0.5F, this.panel.getX() + this.panel.getWidth() - (2), this.panel.getY()
                        + this.componentYOffset + this.getHeight()
                        - 1.5F, this.getUiModule().colorSetting.getValue());
            }
        }
        String settingName = this.setting.getDisplayName();
        String valueName = this.setting.getValue().getDisplayName();
        String label = (settingName + ": \u0001")
                .replaceFirst("\u0001", Matcher.quoteReplacement(String.valueOf(valueName)));
        this.renderWithTextScroll(poseStack, label, () -> FontRenderer.INSTANCE.drawWithShadow(poseStack, label,
                this.panel.getX() + (4), this.panel.getY() + this.getTextYOffset()
                        - this.getHoverAnimationValue()
                        + this.componentYOffset, this.getUiModule().textColorSetting.getValue()));
        int rowYOffset = this.getBaseHeight();
        for (EnumOptionRow<E> row : this.optionRows) {
            row.setYOffset(rowYOffset);
            rowYOffset += this.getBaseHeight();
        }
    }

    public boolean isExpanded() {
        return this.expanded;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX > this.cachedX && mouseX < this.cachedX + this.getPanel().getWidth() && mouseY
                > this.clampYToPanel(this.cachedY) && mouseY < this.clampYToPanel(this.cachedY + 11);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (this.isUnavailable()) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
        this.optionRows.forEach(row -> row.mouseClicked(mouseX, mouseY, button));
        if (this.isMouseOver(mouseX, mouseY)) {
            if (button == 0) {
                this.setting.setValue(this.nextValue());
                this.selectedIndex = (this.selectedIndex + 1) % this.selectableValueCount;
            }
            if (button == 1) {
                int selectableIndex = 0;
                for (int enumIndex = 0;
                     enumIndex
                             < (this.setting.getValue()).getDeclaringClass().getEnumConstants().length;
                     enumIndex++) {
                    Enum candidate = ((Enum[]) (this.setting.getValue()).getDeclaringClass()
                            .getEnumConstants())[enumIndex];
                    if (candidate == this.setting.getValue()) {
                        this.selectedIndex = selectableIndex;
                        break;
                    }
                    selectableIndex++;
                }
                this.expanded = !this.expanded;
                this.getPanel().updateLayout();
            }
        }
    }

    @Override
    public void update(double mouseX, double mouseY) {
        if (this.isUnavailable()) {
            return;
        }
        this.cachedY = this.getPanel().getY() + this.componentYOffset;
        this.cachedX = this.getPanel().getX();
    }
}