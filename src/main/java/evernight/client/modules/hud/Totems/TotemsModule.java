package evernight.client.modules.hud.Totems;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.modules.HudWidgetModule;
import evernight.client.render.font.FontRenderer;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.render.hud.ResizableHudWidgetRenderer;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.inventory.InventoryUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.awt.*;
import java.util.function.Predicate;

public class TotemsModule extends HudWidgetModule {
    private final Setting<Boolean> whiteSetting;
    private ItemStack totemStack;

    public TotemsModule() {
        super("Totems");
        this.whiteSetting = this.add(new BooleanSetting("White", false));
        this.setRenderer(new ResizableHudWidgetRenderer(this));
    }

    @Override
    public float[] calculateDimensions() {
        float[] dimensions = new float[2];
        dimensions[0] = 16.0F;
        dimensions[1] = 16.0F;
        return dimensions;
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent ignoredEvent) {
        LocalPlayer localPlayer = ClientAccess.evernightMinecraft.player;
        if (!localPlayer.isSpectator()) {
            this.totemStack = new ItemStack(Items.TOTEM_OF_UNDYING,
                    InventoryUtils.countItems(candidateStack -> {
                        Item totemItemForMatch = Items.TOTEM_OF_UNDYING;
                        return candidateStack.is(totemItemForMatch);
                    }));
        }
    }

    @Override
    public void renderContent(PoseStack poses) {
        if (this.totemStack != null) {
            ItemStack stackForEmptyCheck = this.totemStack;
            if (stackForEmptyCheck.getCount() != 0) {
                ItemStack stackToRender = this.totemStack;
                GuiDrawUtils.drawItem(poses, stackToRender, 0.0F, 0.0F, -150.0F, true);
                ItemStack stackForOverlayCheck = this.totemStack;
                if (stackForOverlayCheck.getCount() > 1) {
                    int totemCount = this.totemStack.getCount();
                    String countText = String.valueOf(totemCount);
                    poses.pushPose();
                    double textDepth = 0.1D;
                    double textTranslateY = 0.0;
                    double textTranslateX = 0.0;
                    poses.translate(textTranslateX, textTranslateY, textDepth);
                    FontRenderer font = FontRenderer.INSTANCE;
                    float textX = 17.0F - FontRenderer.INSTANCE.getWidth(countText);
                    float textY = 9.0F;
                    Setting<Boolean> whiteOption = this.whiteSetting;
                    Boolean useWhite = whiteOption.getValue();
                    Color textColor;
                    if (useWhite) {
                        textColor = Color.white;
                    } else {
                        textColor = this.getHudColor(9.0F);
                    }
                    font.drawWithShadow(poses, countText, textX, textY, textColor);
                    poses.popPose();
                }
            }
        }
    }
}