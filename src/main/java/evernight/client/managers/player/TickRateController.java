package evernight.client.managers.player;

import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.ui.ModuleToggleEvent;
import evernight.client.modules.Module;
import net.minecraft.util.Mth;

import java.util.concurrent.ConcurrentHashMap;

public class TickRateController implements ClientAccess {
    private final ConcurrentHashMap<Module, Boolean> moduleReleaseStates = new ConcurrentHashMap<>();
    private float multiplier;

    public TickRateController() {
        this.multiplier = 1.0F;
        ClientAccess.eventBus.subscribe(this);
    }

    @EventHandler
    private void onModuleToggle(ModuleToggleEvent event) {
        Module module = event.module();
        if (!module.isToggled()) {
            Module moduleToRelease = event.module();
            this.releaseMultiplier(moduleToRelease);
        }
    }

    public void requestMultiplier(Module module, float requestedMultiplier) {
        this.moduleReleaseStates.compute(module, (keyModule, previousReleased) -> false);
        this.multiplier = requestedMultiplier;
    }

    public float getMultiplier() {
        return this.multiplier;
    }

    public void releaseMultiplier(Module module) {
        this.moduleReleaseStates.compute(module, (keyModule, previousReleased) -> {
            if (Boolean.FALSE.equals(previousReleased)) {
                this.multiplier = 1.0F;
            }
            return true;
        });
    }

    public static class PlayerTicks extends TickRateController {
        private float fractionalTicks;

        @EventHandler(priority = -200)
        private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
            float rate = this.getMultiplier();
            if (rate <= 1.0F) {
                return;
            }
            this.fractionalTicks += rate - (int) rate;
            for (int extraTicks = 0;
                 extraTicks < Math.floor(rate) + Math.floor(this.fractionalTicks) - 1.0D;
                 extraTicks++) {
                PlayerTickExecutor.runExtraTick();
            }
            float fractionSnapshot = this.fractionalTicks;
            if (fractionSnapshot >= 1.0F) {
                float fractionToReduce = this.fractionalTicks;
                float reducedFraction = fractionToReduce - 1.0F;
                float maximumFraction = 1.0F;
                this.fractionalTicks = Mth.clamp(reducedFraction, 0.0F, maximumFraction);
            }
        }
    }
}