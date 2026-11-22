package evernight.client.modules.misc.Heaven;

import evernight.client.ClientAccess;
import evernight.client.events.render.CameraSetupEvent;
import evernight.client.events.core.EventHandler;
import evernight.client.events.ui.ScreenChangeEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class HeavenModule extends Module {
    private long deathTimeMillis;

    public HeavenModule() {
        super("Heaven", ModuleCategory.MISC);
    }

    @EventHandler
    private void onCameraSetupEvent(CameraSetupEvent event) {
        if (!(ClientAccess.evernightMinecraft.screen instanceof DeathScreen)) {
            return;
        }
        long elapsedSinceDeath = System.currentTimeMillis() - this.deathTimeMillis;
        float clampedElapsed = (float) Mth.clamp(elapsedSinceDeath, 0L, 10000L);
        float riseProgress = clampedElapsed / 10000.0F;
        Vec3 capturedPosition = event.getPosition();
        double riseOffset = riseProgress * 400.0F;
        Vec3 position = capturedPosition.add(0.0D, riseOffset, 0.0D);
        Axis yAxis = Axis.Y;
        double capturedY = position.y;
        double heightLimit = 400.0D;
        double clampedY = Math.min(capturedY, heightLimit);
        position = position.with(yAxis, clampedY);
        event.setPosition(position);
        event.cancel();
    }

    @EventHandler
    private void onScreenChange(ScreenChangeEvent.AfterInit event) {
        if (event.getNewScreen() instanceof DeathScreen) {
            this.deathTimeMillis = System.currentTimeMillis();
        }
    }
}