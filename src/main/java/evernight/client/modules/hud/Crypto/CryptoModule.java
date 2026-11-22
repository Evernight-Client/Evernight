package evernight.client.modules.hud.Crypto;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import evernight.client.ClientAccess;
import evernight.client.EvernightClient;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.modules.HudWidgetModule;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.text.Translations;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.StringSetting;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.core.FloatAnimation;
import evernight.client.utils.math.MathUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import evernight.client.modules.hud.HudTextListRenderer;
import evernight.client.modules.hud.HudTextRow;

public class CryptoModule extends HudWidgetModule {
    private final Setting<String> coinSetting;
    private final Setting<FiatCurrency> fiatSetting;
    private final HttpClient httpClient;
    private final ElapsedTimer updateTimer;
    private final FloatAnimation priceChangeAnimation;
    private boolean priceIncreased;
    private String formattedPrice;
    private String coinSlug;

    public CryptoModule() {
        super("Crypto");
        this.coinSetting = this.add(new StringSetting("Coin", "BTC"));
        this.fiatSetting = this.add(new EnumSetting<>("Fiat", CryptoModule.FiatCurrency.USD));
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
        this.updateTimer = new ElapsedTimer();
        this.priceChangeAnimation = new FloatAnimation();
        this.formattedPrice = "0.00";
        this.coinSlug = null;
        this.setRenderer(new HudTextListRenderer(this, new HudTextRow(this::getDisplayText, () -> true)));
        this.coinSetting.setChangeCallback(() -> {
            this.coinSlug = null;
            FloatAnimation callbackAnimation = this.priceChangeAnimation;
            callbackAnimation.setValue(0.0F);
            this.formattedPrice = "0.00";
            if (!this.isCoinSymbolValid()) {
                Setting<String> invalidCoinOption = this.coinSetting;
                invalidCoinOption.setValue("UNKNOWN");
            } else {
                ElapsedTimer timerForImmediateUpdate = this.updateTimer;
                timerForImmediateUpdate.setTime(5000L);
            }
        });
    }

    private void refreshPrice() {
        if (!this.isCoinSymbolValid()) {
            this.coinSlug = null;
            this.coinSetting.setValue("UNKNOWN");
            this.formattedPrice = "0.00";
            this.priceChangeAnimation.setValue(0.0F);
            return;
        }
        String cachedCoinSlug = this.coinSlug;
        String coinSymbol = (this.coinSetting.getValue()).toUpperCase(Locale.ROOT);
        FiatCurrency fiatCurrency = this.fiatSetting.getValue();
        String fiatSymbol = fiatCurrency.getName().toUpperCase(Locale.ROOT);
        if (cachedCoinSlug == null) {
            try {
                URI uri = new URI("https://api.alternative.me/v2/ticker/?convert=%s".formatted(fiatSymbol));
                JsonObject prices = this.requestPrices(uri);
                if (prices == null || prices.isEmpty()) {
                    return;
                }
                for (String key : prices.keySet()) {
                    if (!prices.get(key).isJsonObject()) {
                        continue;
                    }
                    JsonObject coin = prices.get(key).getAsJsonObject();
                    if (!coin.has("symbol") || !coin.has("website_slug")) {
                        continue;
                    }
                    String responseCoinSymbol = coin.get("symbol").getAsString();
                    if (coinSymbol.equals(responseCoinSymbol)) {
                        JsonObject quotes = coin.getAsJsonObject("quotes");
                        if (!quotes.has(fiatSymbol)) {
                            break;
                        }
                        JsonObject quote = quotes.getAsJsonObject(fiatSymbol);
                        this.updateQuotedPrice(quote.get("price").getAsFloat());
                        this.coinSlug = coin.get("website_slug").getAsString();
                        return;
                    }
                }
                if (this.coinSlug == null) {
                    this.formattedPrice = "UNKNOWN";
                    this.priceChangeAnimation.setValue(0.0F);
                    return;
                }
            } catch (Exception ignoredException) {
                return;
            }
        }
        try {
            String conversionUrl = "https://api.alternative.me/v2/ticker/%s/?convert=%s";
            URI uri = new URI(conversionUrl.formatted(cachedCoinSlug, fiatSymbol));
            JsonObject prices = this.requestPrices(uri);
            if (prices == null || prices.isEmpty()) {
                return;
            }
            for (String key : prices.keySet()) {
                if (!prices.get(key).isJsonObject()) {
                    continue;
                }
                JsonObject coin = prices.get(key).getAsJsonObject();
                if (!coin.has("quotes")) {
                    continue;
                }
                JsonObject quotes = coin.getAsJsonObject("quotes");
                JsonObject quote = quotes.getAsJsonObject(fiatSymbol);
                this.updateQuotedPrice(quote.get("price").getAsFloat());
                break;
            }
        } catch (Exception ignoredException) {
        }
    }

    private JsonObject requestPrices(URI uri) {
        HttpRequest request = HttpRequest.newBuilder().GET().uri(uri)
                .header("Accept", "application/json")
                .header("User-Agent", EvernightClient.USER_AGENT)
                .build();
        HttpResponse<String> response = this.httpClient.sendAsync(request, BodyHandlers.ofString())
                .orTimeout(15L, TimeUnit.SECONDS).join();
        int statusCode = response.statusCode();
        if (statusCode != 200) {
            this.formattedPrice = "UNKNOWN";
            FloatAnimation statusFailureAnimation = this.priceChangeAnimation;
            statusFailureAnimation.setValue(0.0F);
            return null;
        }
        Gson gson = ClientAccess.gson;
        String responseBody = response.body();
        Class<JsonObject> responseType = JsonObject.class;
        JsonObject responseJson = gson.fromJson(responseBody, responseType);
        if (!responseJson.has("data") || !responseJson.has("metadata")) {
            this.formattedPrice = "UNKNOWN";
            this.priceChangeAnimation.setValue(0.0F);
            return null;
        }
        JsonObject metadata = responseJson.getAsJsonObject("metadata");
        if (metadata.has("error") && !metadata.get("error").isJsonNull()) {
            this.formattedPrice = "UNKNOWN";
            this.priceChangeAnimation.setValue(0.0F);
            return null;
        }
        return responseJson.getAsJsonObject("data");
    }

    private void updateQuotedPrice(float quotedPrice) {
        String priceText = "%.2f".formatted(quotedPrice);
        float roundedPrice = Float.parseFloat(priceText);
        if (!this.formattedPrice.equalsIgnoreCase("0.00")
                && !this.formattedPrice.equalsIgnoreCase("UNKNOWN")) {
            this.updatePriceChangeAnimation(roundedPrice);
        }
        this.formattedPrice = priceText;
    }

    private boolean isCoinSymbolValid() {
        String coinSymbol = this.coinSetting.getValue();
        return !coinSymbol.isEmpty() && coinSymbol.matches("[a-zA-Z]+");
    }

    private Component getDisplayText() {
        MutableComponent text = Component.empty();
        String coinSymbol = this.coinSetting.getValue();
        String displayCoinSymbol = coinSymbol.toUpperCase();
        text.append(displayCoinSymbol);
        text.append(" ");
        int priceColor;
        if (this.priceIncreased) {
            Color gainColor = Color.GREEN;
            Color neutralColor = Color.WHITE;
            FloatAnimation gainAnimation = this.priceChangeAnimation;
            Color gainBlendedColor = ColorUtils.blend(gainColor, neutralColor, gainAnimation.getValue());
            priceColor = gainBlendedColor.hashCode();
        } else {
            Color lossColor = Color.RED;
            Color lossNeutralColor = Color.WHITE;
            FloatAnimation lossAnimation = this.priceChangeAnimation;
            Color lossBlendedColor = ColorUtils.blend(lossColor, lossNeutralColor, lossAnimation.getValue());
            priceColor = lossBlendedColor.hashCode();
        }
        String priceText = "UNKNOWN".equals(this.formattedPrice)
                ? Translations.ui("gui.Unknown")
                : this.formattedPrice;
        MutableComponent priceComponent = Component.literal(priceText);
        MutableComponent coloredPrice = priceComponent.withColor(priceColor);
        text.append(coloredPrice);
        return text;
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent ignoredEvent) {
        ElapsedTimer timer = this.updateTimer;
        TimeUnit intervalUnit = TimeUnit.SECONDS;
        if (timer.hasElapsed(5.0D, intervalUnit)) {
            ExecutorService executor = ClientAccess.executorService;
            Runnable refreshTask = this::refreshPrice;
            executor.submit(refreshTask);
            timer = this.updateTimer;
            timer.reset();
        }
    }

    private void updatePriceChangeAnimation(float price) {
        String previousPriceText = this.formattedPrice;
        float parsedPreviousPrice = Float.parseFloat(previousPriceText);
        float comparisonPrice = MathUtils.floorToDecimals(parsedPreviousPrice, 2);
        float priceDifference = price - comparisonPrice;
        double absoluteDifference = Math.abs(priceDifference);
        if (absoluteDifference < 0.01D) {
            FloatAnimation unchangedAnimation = this.priceChangeAnimation;
            unchangedAnimation.setValue(0.0F);
            return;
        }
        this.priceIncreased = price > comparisonPrice;
        if (comparisonPrice != price) {
            FloatAnimation changeAnimation = this.priceChangeAnimation;
            float changeProgress = 1.0F;
            changeAnimation.setValue(changeProgress);
            FloatAnimation fadeAnimation = this.priceChangeAnimation;
            fadeAnimation.setTarget(0.0F, 1000L);
        }
    }

    private enum FiatCurrency implements NamedEntry {
        USD("USD"),
        EUR("EUR"),
        RUB("RUB"),
        CNY("CNY"),
        TRY("TRY"),
        JPY("JPY"),
        PLN("PLN"),
        CHF("CHF"),
        GBP("GBP"),
        CAD("CAD"),
        KRW("KRW"),
        BRL("BRL");
        private final String displayName;

        FiatCurrency(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }
}