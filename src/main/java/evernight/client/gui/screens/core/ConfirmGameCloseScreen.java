package evernight.client.gui.screens.core;

import evernight.client.ClientAccess;
import evernight.client.render.text.Translations;
import evernight.mixin.minecraft.client.MinecraftAccessor;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class ConfirmGameCloseScreen extends ConfirmScreen {
    public ConfirmGameCloseScreen() {
        super(new CloseConfirmationHandler(),
                Component.literal(Translations.ui("gui.Confirm Game Close")),
                Component.literal(Translations.ui("gui.Are you sure that you want to close minecraft?"))
        );
    }

    private static final class CloseConfirmationHandler implements BooleanConsumer, ClientAccess {
        @Override
        public void accept(boolean confirmed) {
            if (!((MinecraftAccessor) ClientAccess.evernightMinecraft).evernight$isDisconnecting()) {
                Minecraft client = ClientAccess.evernightMinecraft;
                client.setScreen(null);
            }
            if (confirmed) {
                Minecraft client = ClientAccess.evernightMinecraft;
                client.destroy();
            } else {
                Minecraft client = ClientAccess.evernightMinecraft;
                long windowHandle = (client.getWindow()).handle();
                GLFW.glfwSetWindowShouldClose(windowHandle, false);
            }
        }
    }
}