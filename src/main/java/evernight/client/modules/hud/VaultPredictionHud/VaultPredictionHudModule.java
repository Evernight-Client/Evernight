package evernight.client.modules.hud.VaultPredictionHud;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientContext;
import evernight.client.modules.HudWidgetModule;
import evernight.client.modules.exploit.VaultPrediction.VaultPredictionModule;
import evernight.client.render.font.FontRenderer;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.render.hud.ResizableHudWidgetRenderer;
import evernight.client.render.text.Translations;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.HudAnchor;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Standard HUD widget for {@link VaultPredictionModule}: the next target's item icon plus either
 * the absolute worldwide opening (and distance) or only the distance. Its display options are
 * independent from the module's chat notification settings.
 */
public class VaultPredictionHudModule extends HudWidgetModule {
    private static final float ICON_SIZE = 16.0F;
    private static final float ICON_GAP = 2.0F;
    private static final float ROW_GAP = 1.0F;

    private final Setting<Boolean> showCountSetting;
    private final Setting<Boolean> showLastSetting;
    private final Setting<Boolean> showIconSetting;

    private String cachedItemId;
    private ItemStack cachedIcon;

    public VaultPredictionHudModule() {
        super("VaultPredictionHud");
        this.showCountSetting = this.add(new BooleanSetting("ShowCount", true));
        this.showLastSetting = this.add(new BooleanSetting("ShowLast", true).dependsOn(this.showCountSetting));
        this.showIconSetting = this.add(new BooleanSetting("ShowIcon", true));
        this.setRenderer(new ResizableHudWidgetRenderer(this));
        this.getRenderer().setAnchor(HudAnchor.TOP_LEFT);
    }

    @Override
    public void onEnable() {
        if (!this.isPredictionEnabled()) {
            ChatUtils.printChat(Component.literal(Translations.ui("modules.VaultPredictionHud.EnablePrediction")),
                    ChatUtils.moduleSignature(this));
        }
    }

    private boolean isPredictionEnabled() {
        VaultPredictionModule prediction = ClientContext.getModule(VaultPredictionModule.class);
        return prediction != null && prediction.isToggled();
    }

    @Override
    public float[] calculateDimensions() {
        Snapshot snapshot = this.snapshot();
        float textWidth = FontRenderer.INSTANCE.getWidth(snapshot.text());
        float mainWidth = textWidth + (snapshot.itemId() == null ? 0.0F : ICON_GAP + ICON_SIZE);
        float mainHeight = Math.max(snapshot.itemId() == null ? 0.0F : ICON_SIZE, FontRenderer.INSTANCE.getHeight());
        float lastWidth = snapshot.lastText() == null ? 0.0F : FontRenderer.INSTANCE.getWidth(snapshot.lastText());
        float width = Math.max(mainWidth, lastWidth);
        float height = mainHeight + (snapshot.lastText() == null ? 0.0F : ROW_GAP + FontRenderer.INSTANCE.getHeight());
        return new float[]{width, height};
    }

    @Override
    public void renderContent(PoseStack poses) {
        Snapshot snapshot = this.snapshot();
        float textWidth = FontRenderer.INSTANCE.getWidth(snapshot.text());
        ItemStack icon = this.icon(snapshot.itemId());
        float mainHeight = Math.max(icon == null ? 0.0F : ICON_SIZE, FontRenderer.INSTANCE.getHeight());
        float textY = icon == null ? 0.0F : (ICON_SIZE - FontRenderer.INSTANCE.getHeight()) / 2.0F;
        FontRenderer.INSTANCE.drawWithShadow(poses, snapshot.text(), 0.0F, textY, this.getHudColor(0.0F));
        if (icon != null) {
            // The icon follows the "N 次后" text, and z=0 keeps 3D items such as the heavy core visible.
            GuiDrawUtils.drawItem(poses, icon, textWidth + ICON_GAP, 0.0F, 0.0F, true);
        }
        if (snapshot.lastText() != null) {
            float lastY = mainHeight + ROW_GAP;
            FontRenderer.INSTANCE.drawWithShadow(poses, snapshot.lastText(), 0.0F, lastY, this.getHudColor(lastY));
        }
    }

    private ItemStack icon(String itemId) {
        if (itemId == null) {
            return null;
        }
        if (itemId.equals(this.cachedItemId)) {
            return this.cachedIcon;
        }
        Item item = switch (itemId) {
            case "minecraft:enchanted_golden_apple" -> Items.ENCHANTED_GOLDEN_APPLE;
            case "minecraft:heavy_core" -> Items.HEAVY_CORE;
            default -> null;
        };
        this.cachedItemId = itemId;
        this.cachedIcon = item == null ? null : new ItemStack(item);
        return this.cachedIcon;
    }

    private Snapshot snapshot() {
        VaultPredictionModule prediction = ClientContext.getModule(VaultPredictionModule.class);
        if (prediction == null || !prediction.isToggled()) {
            return new Snapshot(Translations.ui("modules.VaultPredictionHud.EnablePrediction"), null, null);
        }
        boolean showCount = this.showCountSetting.getValue();
        boolean showIcon = this.showIconSetting.getValue();
        long distance = prediction.getNextDistance();
        long nextOffset = prediction.getNextOffset();
        String itemId = prediction.getNextItemId();
        boolean hasTarget = distance >= 0 && nextOffset >= 0 && itemId != null;
        String text;
        String iconItem = null;
        if (showCount) {
            if (hasTarget) {
                text = Translations.format("modules.VaultPredictionHud.CountDistance", nextOffset, distance);
                iconItem = showIcon ? itemId : null;
            } else {
                text = Translations.ui("modules.VaultPredictionHud.Waiting");
            }
        } else if (hasTarget) {
            text = Translations.format("modules.VaultPredictionHud.Distance", distance);
            iconItem = showIcon ? itemId : null;
        } else {
            text = Translations.ui("modules.VaultPredictionHud.Waiting");
        }
        String lastText = null;
        if (showCount && this.showLastSetting.getValue()) {
            long last = prediction.getLastDetectedOpening();
            if (last > 0) {
                lastText = Translations.format("modules.VaultPredictionHud.LastOpening", last);
            }
        }
        return new Snapshot(text, iconItem, lastText);
    }

    private record Snapshot(String text, String itemId, String lastText) {
    }
}
