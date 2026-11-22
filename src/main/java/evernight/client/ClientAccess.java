package evernight.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import evernight.client.events.bus.EventBus;
import evernight.client.utils.inventory.ItemRegistryJsonAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public interface ClientAccess {
    Logger logger = LoggerFactory.getLogger(EvernightClient.NAME);
    Minecraft evernightMinecraft = Minecraft.getInstance();
    Gson gson = new GsonBuilder().registerTypeAdapter(Item.class, new ItemRegistryJsonAdapter.ItemAdapter())
            .registerTypeAdapter(BlockItem.class, new ItemRegistryJsonAdapter.BlockItemAdapter())
            .setPrettyPrinting().create();
    EventBus eventBus = new EventBus();
    ExecutorService executorService = Executors.newCachedThreadPool();

    static String getVersion() {
        return EvernightClient.VERSION;
    }

    static boolean isLocalPlayer(Object candidate) {
        return ClientAccess.evernightMinecraft.player == candidate;
    }

    default boolean isWorldUnavailable() {
        return ClientAccess.evernightMinecraft.player == null || ClientAccess.evernightMinecraft.level == null;
    }
}