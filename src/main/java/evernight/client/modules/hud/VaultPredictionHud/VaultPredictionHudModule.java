package evernight.client.modules.hud.VaultPredictionHud;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientContext;
import evernight.client.modules.HudWidgetModule;
import evernight.client.modules.exploit.VaultPrediction.VaultPredictionModule;
import evernight.client.modules.exploit.VaultPrediction.VaultPredictionModule.PredictionEntry;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Standard HUD widget for {@link VaultPredictionModule}. It can show the next target, the full
 * prediction list, the absolute opening number and the most recent detected opening; every display
 * option is local to the HUD and independent from the module's chat notification settings.
 */
public class VaultPredictionHudModule extends HudWidgetModule {
    private static final float ICON_SIZE = 16.0F;
    private static final float ICON_GAP = 2.0F;
    private static final float ROW_GAP = 1.0F;

    private final Setting<Boolean> showCountSetting;
    private final Setting<Boolean> showListSetting;
    private final Setting<Boolean> showLastSetting;
    private final Setting<Boolean> showIconSetting;
    private final Map<String, ItemStack> iconCache = new HashMap<>();

    public VaultPredictionHudModule() {
        super("VaultPredictionHud");
        this.showCountSetting = this.add(new BooleanSetting("ShowCount", true));
        this.showListSetting = this.add(new BooleanSetting("ShowList", true));
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
        List<Row> rows = snapshot.rows();
        float width = 0.0F;
        float height = 0.0F;
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            float textWidth = FontRenderer.INSTANCE.getWidth(row.text());
            width = Math.max(width, textWidth + (row.itemId() == null ? 0.0F : ICON_GAP + ICON_SIZE));
            if (index > 0) {
                height += ROW_GAP;
            }
            height += Math.max(row.itemId() == null ? 0.0F : ICON_SIZE, FontRenderer.INSTANCE.getHeight());
        }
        if (snapshot.lastText() != null) {
            width = Math.max(width, FontRenderer.INSTANCE.getWidth(snapshot.lastText()));
            if (!rows.isEmpty()) {
                height += ROW_GAP;
            }
            height += FontRenderer.INSTANCE.getHeight();
        }
        return new float[]{width, height};
    }

    @Override
    public void renderContent(PoseStack poses) {
        Snapshot snapshot = this.snapshot();
        float y = 0.0F;
        for (Row row : snapshot.rows()) {
            float textWidth = FontRenderer.INSTANCE.getWidth(row.text());
            ItemStack icon = this.icon(row.itemId());
            float rowHeight = Math.max(icon == null ? 0.0F : ICON_SIZE, FontRenderer.INSTANCE.getHeight());
            float textY = icon == null ? 0.0F : (ICON_SIZE - FontRenderer.INSTANCE.getHeight()) / 2.0F;
            FontRenderer.INSTANCE.drawWithShadow(poses, row.text(), 0.0F, y + textY, this.getHudColor(y));
            if (icon != null) {
                // The icon follows the "N 次后" text, and z=0 keeps 3D items such as the heavy core visible.
                GuiDrawUtils.drawItem(poses, icon, textWidth + ICON_GAP, y, 0.0F, true);
            }
            y += rowHeight + ROW_GAP;
        }
        if (snapshot.lastText() != null) {
            FontRenderer.INSTANCE.drawWithShadow(poses, snapshot.lastText(), 0.0F, y, this.getHudColor(y));
        }
    }

    private ItemStack icon(String itemId) {
        if (itemId == null) {
            return null;
        }
        ItemStack cached = this.iconCache.get(itemId);
        if (cached != null) {
            return cached;
        }
        Item item = switch (itemId) {
            case "minecraft:enchanted_golden_apple" -> Items.ENCHANTED_GOLDEN_APPLE;
            case "minecraft:heavy_core" -> Items.HEAVY_CORE;
            default -> null;
        };
        if (item == null) {
            return null;
        }
        ItemStack stack = new ItemStack(item);
        this.iconCache.put(itemId, stack);
        return stack;
    }

    private Snapshot snapshot() {
        VaultPredictionModule prediction = ClientContext.getModule(VaultPredictionModule.class);
        if (prediction == null || !prediction.isToggled()) {
            return new Snapshot(List.of(new Row(Translations.ui("modules.VaultPredictionHud.EnablePrediction"), null)),
                    null);
        }
        boolean showCount = this.showCountSetting.getValue();
        boolean showIcon = this.showIconSetting.getValue();
        List<Row> rows = new ArrayList<>();
        if (this.showListSetting.getValue()) {
            List<PredictionEntry> entries = prediction.getPredictionEntries();
            if (entries.isEmpty()) {
                rows.add(new Row(Translations.ui("modules.VaultPredictionHud.Waiting"), null));
            } else {
                for (PredictionEntry entry : entries) {
                    String text = showCount
                            ? Translations.format("modules.VaultPredictionHud.CountDistance", entry.offset(),
                                    entry.distance())
                            : Translations.format("modules.VaultPredictionHud.Distance", entry.distance());
                    rows.add(new Row(text, showIcon ? entry.itemId() : null));
                }
            }
        } else {
            long distance = prediction.getNextDistance();
            long nextOffset = prediction.getNextOffset();
            String itemId = prediction.getNextItemId();
            boolean hasTarget = distance >= 0 && nextOffset >= 0 && itemId != null;
            if (showCount) {
                if (hasTarget) {
                    rows.add(new Row(Translations.format("modules.VaultPredictionHud.CountDistance", nextOffset,
                            distance), showIcon ? itemId : null));
                } else {
                    long current = prediction.getCurrentOpening();
                    rows.add(new Row(current > 0
                            ? Translations.format("modules.VaultPrediction.Info", current)
                            : Translations.ui("modules.VaultPredictionHud.Waiting"), null));
                }
            } else if (hasTarget) {
                rows.add(new Row(Translations.format("modules.VaultPredictionHud.Distance", distance),
                        showIcon ? itemId : null));
            } else {
                rows.add(new Row(Translations.ui("modules.VaultPredictionHud.Waiting"), null));
            }
        }
        String lastText = null;
        if (showCount && this.showLastSetting.getValue()) {
            long last = prediction.getLastDetectedOpening();
            if (last > 0 && (this.showListSetting.getValue() || last != prediction.getCurrentOpening())) {
                lastText = Translations.format("modules.VaultPredictionHud.LastOpening", last);
            }
        }
        return new Snapshot(List.copyOf(rows), lastText);
    }

    private record Row(String text, String itemId) {
    }

    private record Snapshot(List<Row> rows, String lastText) {
    }
}
