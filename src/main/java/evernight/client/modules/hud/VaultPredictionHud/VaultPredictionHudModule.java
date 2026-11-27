package evernight.client.modules.hud.VaultPredictionHud;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientContext;
import evernight.client.modules.HudWidgetModule;
import evernight.client.modules.exploit.VaultPrediction.VaultPredictionModule;
import evernight.client.render.font.FontRenderer;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.render.hud.ResizableHudWidgetRenderer;
import evernight.client.render.text.Translations;
import evernight.client.settings.options.HudAnchor;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Standard HUD widget for {@link VaultPredictionModule}: the next target's item icon plus either
 * the absolute worldwide opening (and distance) or only the distance, depending on the module's
 * count-display setting. While VaultPrediction is disabled it shows a reminder instead of data.
 */
public class VaultPredictionHudModule extends HudWidgetModule {
    private static final float ICON_SIZE = 16.0F;
    private static final float ICON_GAP = 2.0F;

    private String cachedItemId;
    private ItemStack cachedIcon;

    public VaultPredictionHudModule() {
        super("VaultPredictionHud");
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
        float iconWidth = snapshot.itemId() == null ? 0.0F : ICON_SIZE + ICON_GAP;
        float height = Math.max(snapshot.itemId() == null ? 0.0F : ICON_SIZE, FontRenderer.INSTANCE.getHeight());
        return new float[]{iconWidth + textWidth, height};
    }

    @Override
    public void renderContent(PoseStack poses) {
        Snapshot snapshot = this.snapshot();
        ItemStack icon = this.icon(snapshot.itemId());
        float textX = 0.0F;
        float textY = 0.0F;
        if (icon != null) {
            GuiDrawUtils.drawItem(poses, icon, 0.0F, 0.0F, -150.0F, true);
            textX = ICON_SIZE + ICON_GAP;
            textY = (ICON_SIZE - FontRenderer.INSTANCE.getHeight()) / 2.0F;
        }
        FontRenderer.INSTANCE.drawWithShadow(poses, snapshot.text(), textX, textY, this.getHudColor(0.0F));
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
            return new Snapshot(Translations.ui("modules.VaultPredictionHud.EnablePrediction"), null);
        }
        long distance = prediction.getNextDistance();
        String itemId = prediction.getNextItemId();
        boolean hasTarget = distance >= 0 && itemId != null;
        if (prediction.isCountDisplayOn()) {
            if (hasTarget) {
                return new Snapshot(Translations.format("modules.VaultPredictionHud.CountDistance",
                        prediction.getCurrentOpening(), distance), itemId);
            }
            return new Snapshot(Translations.format("modules.VaultPrediction.Info",
                    prediction.getCurrentOpening()), null);
        }
        if (hasTarget) {
            return new Snapshot(Translations.format("modules.VaultPredictionHud.Distance", distance), itemId);
        }
        return new Snapshot(Translations.ui("modules.VaultPredictionHud.Waiting"), null);
    }

    private record Snapshot(String text, String itemId) {
    }
}
