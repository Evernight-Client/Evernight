package evernight.client.gui.screens.core;

import evernight.client.ClientAccess;
import evernight.client.render.text.Translations;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

public class DisconnectConfirmationScreen extends ConfirmScreen {
    public DisconnectConfirmationScreen(Runnable confirmationAction) {
        super(new ConfirmationCallback(confirmationAction),
                Component.literal(Translations.ui("gui.Confirm Disconnect")),
                Component.literal(Translations.ui("gui.Are you sure that you want to disconnect?"))
        );
    }

    private record ConfirmationCallback(Runnable confirmationAction)
            implements BooleanConsumer, ClientAccess {
        @Override
        public void accept(boolean confirmed) {
            ClientAccess.evernightMinecraft.setScreen(null);
            if (confirmed) {
                this.confirmationAction.run();
            }
        }
    }
}