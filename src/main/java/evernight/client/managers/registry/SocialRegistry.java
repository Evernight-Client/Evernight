package evernight.client.managers.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.config.json.EnumJsonConverter;
import evernight.client.modules.client.Colors.ColorsModule;
import evernight.client.modules.misc.SocialEntry;
import evernight.client.settings.core.JsonConfigurable;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.SocialRole;
import evernight.client.utils.entity.PlayerState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.awt.*;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class SocialRegistry extends Registry<SocialEntry> implements ClientAccess, JsonConfigurable {
    /**
     * Accounts that are always friends: they are not stored in the social list, cannot be removed or
     * turned into enemies, and are not written to the config file.
     */
    private static final List<String> BUILT_IN_FRIENDS = List.of("wxh1024");
    private static final ColorsModule colorsModule;
    private static final EnumJsonConverter<SocialRole> roleConverter;

    static {
        colorsModule = ClientContext.getModule(ColorsModule.class);
        roleConverter = new EnumJsonConverter<>(SocialRole.class);
    }

    private static boolean isBuiltInFriend(String name) {
        for (String builtInFriend : BUILT_IN_FRIENDS) {
            if (builtInFriend.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public boolean isFriendName(String name) {
        return this.getRole(name) == SocialRole.FRIEND;
    }

    public void addFriend(String name) {
        this.removeSocial(name);
        this.registerSocial(new SocialEntry(name, SocialRole.FRIEND));
    }

    public Color getEnemyColor() {
        Setting<Color> colorSetting = colorsModule.enemyColorSetting;
        return colorSetting.getValue();
    }

    public Color getFriendColor() {
        Setting<Color> colorSetting = colorsModule.friendColorSetting;
        return colorSetting.getValue();
    }

    public boolean isFriendEntity(Entity entity) {
        if (entity instanceof Player player) {
            return this.isFriendPlayer(player);
        } else {
            return false;
        }
    }

    @Override
    public boolean register(SocialEntry entry) {
        return this.registerSocial(entry);
    }

    public boolean registerSocial(SocialEntry entry) {
        String capturedName = entry.getName();
        if (isBuiltInFriend(capturedName)) {
            this.updatePlayerRole(capturedName, SocialRole.FRIEND);
            return false;
        }
        SocialRole role = entry.role();
        this.updatePlayerRole(capturedName, role);
        return super.registerEntry(entry);
    }

    @Override
    public void fromJson(JsonElement json) {
        JsonObject root = json.getAsJsonObject();
        if (root.has("socials")) {
            root = json.getAsJsonObject();
            JsonArray socials = root.getAsJsonArray("socials");
            Iterator socialsIterator = socials.iterator();
            while (socialsIterator.hasNext()) {
                JsonElement entryElement = (JsonElement) socialsIterator.next();
                JsonObject entryObject = entryElement.getAsJsonObject();
                if (entryObject.has("name")) {
                    if (entryObject.has("role")) {
                        JsonElement nameElement = entryObject.get("name");
                        String name = nameElement.getAsString();
                        EnumJsonConverter<SocialRole> capturedConverter = roleConverter;
                        JsonElement roleElement = entryObject.get("role");
                        SocialRole role = capturedConverter.fromJson(roleElement);
                        SocialEntry allocatedSocial = new SocialEntry(name, role);
                        this.registerSocial(allocatedSocial);
                    }
                }
            }
        }
    }

    @Override
    public JsonElement toJson() {
        JsonObject root = new JsonObject();
        JsonArray socials = new JsonArray();
        Iterator<SocialEntry> socialsIterator = this.entries.iterator();
        while (socialsIterator.hasNext()) {
            SocialEntry social = socialsIterator.next();
            JsonObject entryObject = new JsonObject();
            String name = social.getName();
            entryObject.addProperty("name", name);
            EnumJsonConverter<SocialRole> capturedConverter = roleConverter;
            SocialRole capturedRole = social.role();
            JsonElement roleElement = capturedConverter.toJson(capturedRole);
            entryObject.add("role", roleElement);
            socials.add(entryObject);
        }
        root.add("socials", socials);
        return root;
    }

    public void addEnemy(String name) {
        this.removeSocial(name);
        this.registerSocial(new SocialEntry(name, SocialRole.ENEMY));
    }

    public boolean isEnemyName(String name) {
        return this.getRole(name) == SocialRole.ENEMY;
    }

    @Override
    public String getConfigName() {
        return "socials.json";
    }

    public SocialRole getRole(String name) {
        if (isBuiltInFriend(name)) {
            return SocialRole.FRIEND;
        }
        Optional<SocialRole> mappedRole = this.findFirst(entry -> {
            String capturedName = entry.getName();
            return capturedName.equalsIgnoreCase(name);
        }).map(SocialEntry::role);
        return mappedRole.orElse(null);
    }

    public boolean isEnemyEntity(Entity entity) {
        if (entity instanceof Player player) {
            return this.isEnemyPlayer(player);
        } else {
            return false;
        }
    }

    public Color getColor(String name, Color defaultColor) {
        if (this.isFriendName(name)) {
            return this.getFriendColor();
        } else if (this.isEnemyName(name)) {
            return this.getEnemyColor();
        } else {
            return defaultColor;
        }
    }

    private void updatePlayerRole(String name, SocialRole role) {
        if (!this.isWorldUnavailable()) {
            ClientLevel level = ClientAccess.evernightMinecraft.level;
            Iterator playersIterator = level.players().iterator();
            while (playersIterator.hasNext()) {
                AbstractClientPlayer player = (AbstractClientPlayer) playersIterator.next();
                GameProfile profile = player.getGameProfile();
                String capturedPlayerName = profile.name();
                if (capturedPlayerName.equalsIgnoreCase(name) && player instanceof PlayerState roleHolder) {
                    roleHolder.evernight$setRole(role);
                    return;
                }
            }
        }
    }

    public boolean removeSocial(String name) {
        if (isBuiltInFriend(name)) {
            this.updatePlayerRole(name, SocialRole.FRIEND);
            return false;
        }
        this.updatePlayerRole(name, null);
        return this.entries.removeIf(entry -> {
                    String capturedName = entry.getName();
                    return capturedName.equalsIgnoreCase(name);
                }
        );
    }

    public boolean isEnemyPlayer(Player player) {
        PlayerState roleHolder = (PlayerState) player;
        return roleHolder.evernight$getRole() == SocialRole.ENEMY;
    }

    public boolean isFriendPlayer(Player player) {
        PlayerState roleHolder = (PlayerState) player;
        return roleHolder.evernight$getRole() == SocialRole.FRIEND;
    }
}