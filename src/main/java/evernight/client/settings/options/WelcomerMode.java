package evernight.client.settings.options;

import evernight.client.ClientAccess;
import evernight.client.modules.hud.Welcomer.WelcomerModule;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.chat.Component;

public enum WelcomerMode implements ClientAccess, NamedEntry {
    NAME("Name") {
        @Override
        public Component createText(WelcomerModule ignoredWelcomer) {
            Minecraft minecraft = ClientAccess.evernightMinecraft;
            User user = minecraft.getUser();
            String username = user.getName();
            String greeting = "Hello " + username + " :')";
            return Component.literal(greeting);
        }
    },
    CUSTOM("Custom") {
        @Override
        public Component createText(WelcomerModule welcomer) {
            Setting<String> textSetting = welcomer.textSetting;
            String text = textSetting.getValue();
            Minecraft minecraft = ClientAccess.evernightMinecraft;
            User user = minecraft.getUser();
            text = text.replace("{name}", user.getName());
            return Component.literal(text);
        }
    };
    private final String displayName;

    WelcomerMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }

    public Component createText(WelcomerModule ignoredWelcomer) {
        return null;
    }
}