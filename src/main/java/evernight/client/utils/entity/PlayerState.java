package evernight.client.utils.entity;

import evernight.client.settings.options.SocialRole;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public interface PlayerState {
    void evernight$setRole(SocialRole role);

    boolean evernight$isNextToWall();

    SocialRole evernight$getRole();

    boolean evernight$wasCrawling();

    default float getItemUseProgress(ItemStack stack, LivingEntity entity) {
        float elapsedMillis = (float) (System.currentTimeMillis() - evernight$getLastEatingTime());
        return elapsedMillis / (stack.getUseDuration(entity) * 50.0F + 50.0F);
    }

    float evernight$getHealthFactor();

    long evernight$getLastEatingTime();

    int evernight$getLastAttackedTicksServer();

    void evernight$resetLastAttackedServer();
}