package evernight.client.modules.player.Freecam;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.movement.NoSlow.NoSlowModule;
import evernight.client.utils.core.KeyUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;

public class FreecamInput extends ClientInput implements ClientAccess {
    private static NoSlowModule noSlow;

    static {
        noSlow = ClientContext.getModule(NoSlowModule.class);
    }

    public static void bindModules(ModuleRegistry registry) {
        noSlow = registry.getModule(NoSlowModule.class);
    }

    private boolean isMovementKeyDown(KeyMapping mapping) {
        if (noSlow.isToggled() && noSlow.isGuiMoveAllowed() && noSlow.guiMoveSetting.getValue()) {
            return KeyUtils.isPhysicallyDown(mapping);
        }
        return mapping.isDown();
    }

    @Override
    public void tick() {
        KeyMapping forwardKey = ClientAccess.evernightMinecraft.options.keyUp;
        boolean positiveDown = this.isMovementKeyDown(forwardKey);
        KeyMapping backKey = ClientAccess.evernightMinecraft.options.keyDown;
        boolean negativeDown = this.isMovementKeyDown(backKey);
        float forward = this.getAxisInput(positiveDown, negativeDown);
        KeyMapping leftKey = ClientAccess.evernightMinecraft.options.keyLeft;
        positiveDown = this.isMovementKeyDown(leftKey);
        KeyMapping rightKey = ClientAccess.evernightMinecraft.options.keyRight;
        negativeDown = this.isMovementKeyDown(rightKey);
        float sideways = this.getAxisInput(positiveDown, negativeDown);
        this.moveVector = new Vec2(sideways, forward);
    }

    private float getAxisInput(boolean positiveDown, boolean negativeDown) {
        return positiveDown == negativeDown ? 0.0F : positiveDown ? 1.0F : -1.0F;
    }
}