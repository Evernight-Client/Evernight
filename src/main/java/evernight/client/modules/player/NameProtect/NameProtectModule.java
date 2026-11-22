package evernight.client.modules.player.NameProtect;

import evernight.client.ClientAccess;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.StringSetting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.Iterator;

public class NameProtectModule extends Module {
    public static boolean replacementBypassed;

    static {
        replacementBypassed = false;
    }

    public final Setting<String> nameSetting;
    public final Setting<Boolean> othersSetting;
    public final Setting<Boolean> skinSetting;
    public final Setting<Boolean> slimSetting;

    public NameProtectModule() {
        super("NameProtect", ModuleCategory.PLAYER);
        this.nameSetting = this.add(new StringSetting("Name", "Me"));
        this.othersSetting = this.add(new BooleanSetting("Others", false));
        this.skinSetting = this.add(new BooleanSetting("Skin", true));
        this.slimSetting = this.add(new BooleanSetting("Slim", false).dependsOn(this.skinSetting));
    }

    public String protectNames(String text) {
        if (replacementBypassed || text == null || text.isEmpty()) {
            return text;
        }
        String nameToReplace = ClientAccess.evernightMinecraft.getUser().getName();
        String protectedName = this.getProtectedName();
        text = this.replaceWholeName(text, nameToReplace, protectedName);
        if ((this.othersSetting.getValue()) && ClientAccess.evernightMinecraft.level != null) {
            ClientLevel level = ClientAccess.evernightMinecraft.level;
            Iterator<? extends AbstractClientPlayer> players = level.players().iterator();
            while (players.hasNext()) {
                nameToReplace = players.next().getGameProfile().name();
                text = this.replaceWholeName(text, nameToReplace, "redacted");
            }
        }
        return text;
    }

    private String replaceWholeName(String text, String name, String replacement) {
        if (name == null || name.isEmpty()) {
            return text;
        }
        StringBuilder result = new StringBuilder(text.length());
        int cursor = 0;
        int nameLength = name.length();
        while (cursor < text.length()) {
            int matchStart = text.indexOf(name, cursor);
            if (matchStart == -1) {
                result.append(text, cursor, text.length());
                break;
            }
            int matchEnd = matchStart + nameLength;
            boolean leftBoundary = matchStart == 0 || !Character.isLetterOrDigit(text.charAt(matchStart - 1));
            boolean rightBoundary = matchEnd == text.length() || !Character.isLetterOrDigit(text.charAt(matchEnd));
            if (leftBoundary && rightBoundary) {
                result.append(text, cursor, matchStart);
                result.append(replacement);
                cursor = matchEnd;
            } else {
                result.append(text, cursor, matchStart + 1);
                cursor = matchStart + 1;
            }
        }
        return result.toString();
    }

    public String getProtectedName() {
        String name = this.nameSetting.getValue();
        return name.length() > 16 ? name.substring(0, 16) : name;
    }
}