package evernight.client.modules.hud;

import evernight.client.ClientAccess;
import evernight.client.render.text.Translations;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.WelcomerMode;
import net.minecraft.network.chat.Component;

import java.awt.*;
import evernight.client.modules.hud.Welcomer.WelcomerModule;

public final class GreetingNotification extends HudNotification {
    private final WelcomerModule welcomer;

    public GreetingNotification(WelcomerModule welcomer) {
        super("", 604800000L, null);
        this.welcomer = welcomer;
    }

    @Override
    public Color getColor() {
        WelcomerModule colorOwner = this.welcomer;
        WelcomerModule positionOwner = this.welcomer;
        return colorOwner.getHudColor(positionOwner.getRenderer().getY());
    }

    @Override
    public void update() {
        if (this.welcomer != null) {
            Setting<WelcomerMode> modeSetting = this.welcomer.modeSetting;
            WelcomerMode modeValue = modeSetting.getValue();
            WelcomerModule textOwner = this.welcomer;
            Component greeting = modeValue.createText(textOwner);
            String greetingText = greeting.getString();
            if (modeValue == WelcomerMode.NAME) {
                greetingText = Translations.format("modules.GreetingNotification.Hello",
                        ClientAccess.evernightMinecraft.getUser().getName());
            }
            super.text = greetingText;
        }
    }
}
