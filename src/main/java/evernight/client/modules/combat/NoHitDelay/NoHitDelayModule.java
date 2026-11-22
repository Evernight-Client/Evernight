package evernight.client.modules.combat.NoHitDelay;

import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.ui.MinecraftRunTickEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.utils.core.ElapsedTimer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;

public class NoHitDelayModule extends Module {
    private final ElapsedTimer useKeyTimer = new ElapsedTimer();
    private boolean wasUseKeyDown;

    public NoHitDelayModule() {
        super("NoHitDelay", ModuleCategory.COMBAT);
    }

    @EventHandler
    private void onClientTick(MinecraftRunTickEvent event) {
        KeyMapping useKey = ClientAccess.evernightMinecraft.options.keyUse;
        boolean useKeyDown = useKey.isDown();
        if (useKeyDown && !this.wasUseKeyDown) {
            this.useKeyTimer.reset();
        }
        this.wasUseKeyDown = useKeyDown;
    }

    public boolean hasUseKeyDelayElapsed() {
        ItemStack offhandStack = ClientAccess.evernightMinecraft.player.getOffhandItem();
        KeyMapping useKey = ClientAccess.evernightMinecraft.options.keyUse;
        if (!useKey.isDown()) {
            return false;
        }
        Item crossbowItem = Items.CROSSBOW;
        if (offhandStack.is(crossbowItem)) {
            return false;
        }
        int delayMillis = 25;
        DataComponentType<?> foodComponent = DataComponents.FOOD;
        if (offhandStack.has(foodComponent) || offhandStack.getItem() instanceof PotionItem) {
            delayMillis = 50;
        }
        long requiredDelayMillis = delayMillis;
        return this.useKeyTimer.hasElapsed(requiredDelayMillis);
    }
}