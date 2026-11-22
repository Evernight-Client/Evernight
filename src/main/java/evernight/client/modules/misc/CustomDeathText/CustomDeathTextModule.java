package evernight.client.modules.misc.CustomDeathText;

import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.StringSetting;
import net.minecraft.network.chat.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public class CustomDeathTextModule extends Module {
    private static final String[] randomMessages;

    static {
        String[] messages = new String[9];
        messages[0] = "YOU USELESS SHITSTAIN";
        messages[1] = "GAME OVER MOTHERFUCKER! YOU SUCK!";
        messages[2]
                = "HOW THE HELL ARE YOU SO BAD AT THIS GAME?";
        messages[3]
                = "HOW DO YOU FUCK UP SO BADLY JESUS FUCKING CHRIST?";
        messages[4]
                = "YOU DIED! CONGRATS FUCKFACE";
        messages[5]
                = "OH MY GOD YOU SUCK! AUTO-UNINSTALLING";
        messages[6]
                = "HOLY FUCKING SHIT SERIOUSLY";
        messages[7]
                = "YOU FUCKING RETARD!";
        messages[8] = "YOU DENSE FUCK";
        randomMessages = messages;
    }

    final Setting<ModeChoice> modeSetting;
    private final Setting<String> messageSetting;

    public CustomDeathTextModule() {
        super("CustomDeathText", ModuleCategory.MISC);
        this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.CUSTOM));
        Predicate<Object> customMode = ignored -> this.modeSetting.getValue() == ModeChoice.CUSTOM;
        this.messageSetting = this.add(new StringSetting("Message", "Uh oh.", customMode));
        this.setDrawn(false);
    }

    public Component getDeathText() {
        Setting<ModeChoice> modeSettingSnapshot = this.modeSetting;
        if (modeSettingSnapshot.getValue() == CustomDeathTextModule.ModeChoice.CUSTOM) {
            Setting<String> messageSettingSnapshot = this.messageSetting;
            String message = messageSettingSnapshot.getValue();
            String trimmedMessage = message.trim();
            return Component.nullToEmpty(trimmedMessage);
        } else {
            String[] messagesSnapshot = randomMessages;
            ThreadLocalRandom randomSnapshot = ThreadLocalRandom.current();
            int messageCount = randomMessages.length;
            String randomMessage = messagesSnapshot[randomSnapshot.nextInt(messageCount)];
            return Component.literal(randomMessage);
        }
    }

    public enum ModeChoice implements NamedEntry {
        CUSTOM("Custom"),
        RANDOM("Random");
        private final String displayName;

        ModeChoice(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }
}