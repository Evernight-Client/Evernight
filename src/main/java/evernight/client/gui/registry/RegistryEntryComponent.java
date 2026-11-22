package evernight.client.gui.registry;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.gui.core.ScissorStack;
import evernight.client.gui.components.base.ButtonComponent;
import evernight.client.gui.components.base.ComponentFlag;
import evernight.client.gui.panels.Panel;
import evernight.client.render.font.FontRenderer;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.utils.core.ClampedInterpolatedFloat;
import evernight.client.utils.core.FloatAnimation;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Block;

import java.util.Locale;

class RegistryEntryComponent<E> extends ButtonComponent {
    public final ClampedInterpolatedFloat visibilityAnimation = new ClampedInterpolatedFloat(
            () -> this.getUiModule().animSpeedSetting.getValue() * 2.0F, true);
    protected final FloatAnimation hoverScrollAnimation = new FloatAnimation();
    private final RegistrySetScreen<E> screen;
    private final E entry;
    private final Item iconItem;
    private final ItemStack iconStack;
    private final boolean availableEntry;
    private boolean resetVisibilityAnimation = true;
    private boolean visible;

    public RegistryEntryComponent(RegistrySetScreen<E> screen, Panel panel, Identifier identifier, E entry, boolean availableEntry) {
        // Registry labels are localised names/user data, not evernight.ui keys: translating them would
        // render "evernight.ui.Gold Block" instead of "Gold Block".
        super(panel, createLabel(identifier, entry), false);
        this.screen = screen;
        this.entry = entry;
        this.availableEntry = availableEntry;
        this.iconStack = createIconStack(identifier, entry);
        this.iconItem = this.iconStack.getItem();
        this.addFlags(ComponentFlag.SILENT_HOVER);
    }

    private static ItemStack createIconStack(Identifier identifier, Object entry) {
        if (entry instanceof EntityType entityType) {
            if (entityType == EntityType.ITEM) {
                return Items.STICK.getDefaultInstance();
            }
            SpawnEggItem spawnEgg = SpawnEggItem.byId(entityType);
            if (spawnEgg != null) {
                return spawnEgg.getDefaultInstance();
            }
        }
        if (entry instanceof Potion potion) {
            Holder potionHolder = BuiltInRegistries.POTION.wrapAsHolder(potion);
            return PotionContents.createItemStack(Items.SPLASH_POTION, potionHolder);
        } else {
            return BuiltInRegistries.ITEM.getValue(identifier).getDefaultInstance();
        }
    }

    /**
     * Resolves the display label for one registry entry. Items, blocks, entity types, effects, potions
     * and sounds use their vanilla localised names; other registry types fall back to a humanised
     * identifier. Never return a translation key here: the label is drawn verbatim.
     */
    private static String createLabel(Identifier identifier, Object entry) {
        if (entry instanceof Item item) {
            return item.getName().getString();
        }
        if (entry instanceof Block block) {
            return block.getName().getString();
        }
        if (entry instanceof EntityType<?> entityType) {
            return entityType.getDescription().getString();
        }
        if (entry instanceof MobEffect effect) {
            return effect.getDisplayName().getString();
        }
        if (entry instanceof Potion potion) {
            Holder<Potion> potionHolder = BuiltInRegistries.POTION.wrapAsHolder(potion);
            return PotionContents.createItemStack(Items.POTION, potionHolder).getHoverName().getString();
        }
        if (entry instanceof SoundEvent sound) {
            String subtitleKey = "subtitles." + sound.location().getNamespace()
                    + "." + sound.location().getPath();
            if (Language.getInstance().has(subtitleKey)) {
                return Language.getInstance().getOrDefault(subtitleKey);
            }
        }
        return FontRenderer.capitalizeWords(identifier.toShortLanguageKey().replace("_", " "));
    }

    @Override
    public void update(double mouseX, double mouseY) {
        this.visible = this.screen.registrySetting.getValue().contains(this.entry);
        if (this.availableEntry) {
            this.visible = !this.visible;
            String searchText = this.screen.searchComponent.getText();
            if (!searchText.isEmpty() && !this.getLabel().toLowerCase()
                    .contains(searchText.toLowerCase(Locale.ROOT))) {
                this.visible = false;
                this.resetVisibilityAnimation = true;
                this.visibilityAnimation.setValue(false);
                return;
            }
        }
        if (this.resetVisibilityAnimation) {
            this.visibilityAnimation.setValue(this.visible);
            this.resetVisibilityAnimation = false;
        } else {
            this.visibilityAnimation.setTarget(this.visible);
            this.visibilityAnimation.updateAndGet();
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (this.isMouseOver(mouseX, mouseY) && button == 0) {
            if (this.availableEntry) {
                this.screen.registrySetting.addEntry(this.entry);
            } else {
                this.screen.registrySetting.removeEntry(this.entry);
            }
        }
    }

    @Override
    public void render(PoseStack poseStack, double mouseX, double mouseY) {
        if (this.visible) {
            float iconOffset = 0.0F;
            if (this.iconItem != null && this.iconItem != Items.AIR) {
                GuiDrawUtils.drawItem(poseStack, this.iconStack, this.panel.getX()
                        + (3), (int) (this.panel.getY() + this.yOffset + this.getTextYOffset() - 2.0F), 0.0F);
                iconOffset = 2e+01F;
            }
            poseStack.pushPose();
            poseStack.translate(iconOffset, 1.5F, 0.0F);
            boolean hovered = this.isMouseOver(mouseX, mouseY);
            this.hoverScrollAnimation.setTarget(hovered, 500L);
            this.renderClippedLabel(poseStack, iconOffset + 3.0F, mouseX, mouseY);
            poseStack.popPose();
        }
    }

    @Override
    public int getHeight() {
        return Math.max((int) (16.0F * this.visibilityAnimation.getValue()), this.visible ? 1 : 0);
    }

    private void renderClippedLabel(PoseStack poseStack, float textOffset, double mouseX, double mouseY) {
        float textWidth = FontRenderer.INSTANCE.getWidth(this.getLabel()) + textOffset;
        float scrollProgress = this.hoverScrollAnimation.getValue();
        int rightInset = scrollProgress > 0.0F ? 0 : 2;
        if (textWidth >= this.panel.getWidth() && scrollProgress > 0.0F) {
            GuiDrawUtils.flush();
            ScissorStack.pushIntersection((int) (this.panel.getX() + textOffset), this.panel.getY()
                    + this.yOffset, (int) (this.panel.getWidth() - textOffset
                    - rightInset), this.getHeight());
            poseStack.translate(-(textWidth - this.panel.getWidth()) * scrollProgress, 0.0F, 0.0F);
            super.render(poseStack, mouseX, mouseY);
            GuiDrawUtils.flush();
            ScissorStack.pop();
        } else {
            super.render(poseStack, mouseX, mouseY);
        }
    }
}