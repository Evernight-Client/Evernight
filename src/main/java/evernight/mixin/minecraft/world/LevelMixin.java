package evernight.mixin.minecraft.world;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import evernight.client.bootstrap.MixinModuleRefs;
import evernight.client.modules.render.Ambience.AmbienceModule;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(Level.class)
public class LevelMixin {
    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void overrideRainLevel(CallbackInfoReturnable<Float> callback) {
        if (MixinModuleRefs.ambience.worldWeatherSetting.getValue() && MixinModuleRefs.ambience.isToggled()
                && !MixinModuleRefs.ambience.isWeatherOverrideSuppressed()) {
            callback.setReturnValue(MixinModuleRefs.ambience.weatherSetting.getValue() == AmbienceModule.WeatherChoice.CLEAR ? 0.0F : 1.0F);
            callback.cancel();
        }
    }

    @WrapOperation(method = "isRaining", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getRainLevel(F)F"))
    private float readRainLevelForRainCheck(Level level, float partialTick, Operation original) {
        if (MixinModuleRefs.ambience.worldWeatherSetting.getValue() && MixinModuleRefs.ambience.isToggled() && MixinModuleRefs.ambience.weatherSetting.getValue() == AmbienceModule.WeatherChoice.CLEAR) {
            return (Float) MixinModuleRefs.ambience.withWeatherOverrideSuppressed(() -> (Float) original.call(level, partialTick));
        }
        return (Float) original.call(level, partialTick);
    }

    @ModifyExpressionValue(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;create()Lnet/minecraft/util/RandomSource;"),
            require = 0)
    private RandomSource useThreadSafeRandomSource(RandomSource originalRandom) {
        return RandomSource.createThreadSafe();
    }

    @Inject(method = "guardEntityTick", at = @At("HEAD"), cancellable = true)
    private void cancelNullEntityTick(Consumer ticker, Entity entity, CallbackInfo callback) {
        if (entity == null) {
            callback.cancel();
        }
    }
}
