package evernight.gametest;

import evernight.client.ClientContext;
import evernight.client.managers.registry.CommandRegistry;
import evernight.client.modules.hud.Watermark.WatermarkModule;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * G2: exercises the real Brigadier command path and the automatic per-module alias.
 */
public final class CommandGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        GameTestSupport.runGuarded(context, () -> context.runOnClient(client -> {
            WatermarkModule watermark = ClientContext.getModule(WatermarkModule.class);
            require(watermark != null, "WatermarkModule is not registered");
            boolean initial = watermark.isToggled();
            try {
                CommandRegistry.executeCommand("toggle Watermark true");
                require(watermark.isToggled(), "toggle true did not enable Watermark");

                CommandRegistry.executeCommand("toggle Watermark false");
                require(!watermark.isToggled(), "toggle false did not disable Watermark");

                CommandRegistry.executeCommand("toggle Watermark true");
                require(watermark.isToggled(), "second toggle true did not enable Watermark");

                CommandRegistry.executeCommand("watermark false");
                require(!watermark.isToggled(), "module alias did not disable Watermark");

                CommandRegistry.executeCommand("watermark true");
                require(watermark.isToggled(), "module alias did not enable Watermark");
            } finally {
                watermark.setToggled(initial);
            }
        }));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
