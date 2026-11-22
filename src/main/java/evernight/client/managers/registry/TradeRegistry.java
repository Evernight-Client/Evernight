package evernight.client.managers.registry;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import evernight.client.ClientAccess;
import evernight.client.settings.core.JsonConfigurable;
import evernight.client.utils.inventory.TradeDefinition;
import evernight.client.utils.inventory.TradeOfferSnapshot;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class TradeRegistry extends Registry<TradeDefinition> implements ClientAccess, JsonConfigurable {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public TradeDefinition createDefault(String name) {
        return new TradeDefinition(name, Items.EMERALD, Items.DIAMOND_SWORD, 64, new ArrayList<>());
    }

    @Override
    public boolean register(TradeDefinition entry) {
        return this.registerTrade(entry);
    }

    @Override
    public void fromJson(JsonElement json) {
        Iterator<JsonElement> iterator = json.getAsJsonObject().getAsJsonArray("trades").iterator();
        while (iterator.hasNext()) {
            JsonElement element = iterator.next();
            try {
                Gson gson = ClientAccess.gson;
                Class<TradeDefinition> tradeType = TradeDefinition.class;
                TradeDefinition trade = gson.fromJson(element, tradeType);
                this.registerTrade(trade);
            } catch (Throwable failure) {
                LOGGER.warn("Could not read trade entry", failure);
            }
        }
    }

    @Override
    public JsonElement toJson() {
        JsonObject root = new JsonObject();
        JsonArray trades = new JsonArray();
        Iterator<TradeDefinition> iterator = this.getRegistry().iterator();
        while (iterator.hasNext()) {
            TradeDefinition trade = iterator.next();
            Gson gson = ClientAccess.gson;
            JsonElement tradeJson = gson.toJsonTree(trade);
            trades.add(tradeJson);
        }
        root.add("trades", trades);
        return root;
    }

    public void reregisterByName(String name) {
        Predicate<TradeDefinition> predicate = trade -> trade.getName().equalsIgnoreCase(name);
        Optional<TradeDefinition> match = this.findFirst(predicate);
        Consumer<TradeDefinition> consumer = this::registerTrade;
        match.ifPresent(consumer);
    }

    @Override
    public String getConfigName() {
        return "trades.json";
    }

    public boolean matches(TradeOfferSnapshot offer) {
        Predicate<TradeDefinition> predicate = trade -> trade.matches(offer);
        return this.findFirst(predicate).isPresent();
    }

    public boolean registerTrade(TradeDefinition trade) {
        if (trade.from() != Items.EMERALD && trade.to() != Items.EMERALD) {
            return false;
        }
        return super.registerEntry(trade);
    }
}