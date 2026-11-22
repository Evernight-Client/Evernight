package evernight.client.config.legacy;

import com.google.gson.*;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.config.ConfigurationManager;
import evernight.client.managers.registry.SocialRegistry;
import evernight.client.utils.core.FileUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

public final class LegacySocialMigration implements ClientAccess {
    private LegacySocialMigration() {
    }

    public static void migrate() throws IOException {
        Path friendsPath = ConfigurationManager.ROOT_DIRECTORY.resolve("friends.json");
        Path socialsPath = ConfigurationManager.ROOT_DIRECTORY.resolve("socials.json");
        if (!friendsPath.toFile().exists()) {
            return;
        }
        if (socialsPath.toFile().exists()) {
            return;
        }
        String friendsText = FileUtils.readUtf8(friendsPath);
        JsonObject friendsRoot = JsonParser.parseString(friendsText).getAsJsonObject();
        JsonArray friends = friendsRoot.getAsJsonArray("friends");
        Iterator<JsonElement> friendIterator = friends.iterator();
        while (friendIterator.hasNext()) {
            JsonElement friend = friendIterator.next();
            SocialRegistry registry = ClientContext.socialRegistry;
            String name = friend.getAsString();
            registry.addFriend(name);
        }
        Gson gson = ClientAccess.gson;
        SocialRegistry registry = ClientContext.socialRegistry;
        JsonElement socials = registry.toJson();
        FileUtils.writeUtf8(socialsPath, gson.toJson(socials));
        friendsPath.toFile().delete();
    }
}