package evernight.gametest;

import evernight.client.ClientContext;
import evernight.client.gui.screens.clickgui.ClickGuiScreen;
import evernight.client.input.ClientKeybinds;
import evernight.client.modules.Keybind;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * G3: the ClickGUI key binding must open and close the screen in a real singleplayer world.
 */
public final class ClickGuiGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        GameTestSupport.runGuarded(context, () -> {
            try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
                singleplayer.getClientWorld().waitForChunksRender();
                Keybind keybind = ClientKeybinds.getClickGuiKeybind();
                require(!keybind.isUnbound(), "ClickGUI key binding is unbound");

                press(context, keybind);
                context.waitForScreen(ClickGuiScreen.class);
                context.runOnClient(client -> {
                    require(client.screen instanceof ClickGuiScreen, "ClickGUI did not open");
                    require(client.screen == ClientContext.getClickGui(),
                            "A different ClickGuiScreen instance was opened");
                });

                press(context, keybind);
                context.waitFor(client -> !(client.screen instanceof ClickGuiScreen));
                context.runOnClient(client -> require(client.screen == null,
                        "ClickGUI did not close, screen=" + client.screen));
            } finally {
                context.runOnClient(client -> {
                    if (client.screen instanceof ClickGuiScreen) {
                        client.setScreen(null);
                    }
                });
            }
        });
    }

    private static void press(ClientGameTestContext context, Keybind keybind) {
        if (keybind.mouse()) {
            context.getInput().pressMouse(keybind.code());
        } else {
            context.getInput().pressKey(keybind.code());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
