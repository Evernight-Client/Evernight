package evernight.mixin.minecraft.render;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.render.NameTagRenderEvent;
import evernight.client.modules.render.NameTags.NameTagsModule;
import evernight.client.render.util.EntityRenderStateAccess;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    private static final NameTagsModule nameTagsModule = ClientContext.getModule(NameTagsModule.class);

    @Inject(method = "submitNameTag", at = @At("HEAD"), cancellable = true)
    private void postNameTagRenderCheck(
            EntityRenderState renderState, PoseStack poses, SubmitNodeCollector collector, CameraRenderState cameraState, CallbackInfo callback
    ) {
        if (ClientContext.shaderRegistry.renderingIntoTarget) {
            callback.cancel();
            return;
        }
        Entity entity = EntityRenderStateAccess.getEntity(renderState);
        NameTagRenderEvent event = new NameTagRenderEvent(poses, entity);
        ClientAccess.eventBus.post(event);
        if (event.isCancelled()) {
            callback.cancel();
        }
    }

    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void suppressVanillaPlayerNameTag(
            Entity entity, double distanceSquared, CallbackInfoReturnable<Boolean> callback) {
        if (entity instanceof Player && nameTagsModule.isToggled()) {
            callback.cancel();
            callback.setReturnValue(entity.hasCustomName());
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void bindEntityToRenderState(
            Entity entity, EntityRenderState renderState, float partialTick, CallbackInfo callback) {
        EntityRenderStateAccess.setEntity(renderState, entity);
    }
}