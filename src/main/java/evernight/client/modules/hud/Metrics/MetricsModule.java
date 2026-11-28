package evernight.client.modules.hud.Metrics;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.managers.world.ServerStateManager;
import evernight.client.managers.world.WorldStateManager;
import evernight.client.modules.HudWidgetModule;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.font.FontRenderer;
import evernight.client.render.text.Translations;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.HudAnchor;
import evernight.client.utils.core.FrameTiming;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import evernight.client.render.hud.HudTextListRenderer;
import evernight.client.render.hud.HudTextRow;

public class MetricsModule extends HudWidgetModule {
    private final Setting<Boolean> speedSetting;
    private final Setting<Boolean> bpsSetting;
    private final Setting<Boolean> tpsSetting;
    private final Setting<Boolean> pingSetting;
    private final Setting<Boolean> fpsSetting;
    private final Setting<Boolean> serverBrandSetting;
    private final Setting<Boolean> durabilitySetting;
    private final Setting<Boolean> shulkersSetting;
    private final Setting<Boolean> chestSetting;
    private final Setting<Boolean> doubleChestSetting;
    private final List<HudTextRow> metricRows;
    private Component cachedDurabilityText;
    private int chestAndBarrelCount;
    private int shulkerCount;

    public MetricsModule() {
        super("Metrics", createAliases());
        this.speedSetting = this.add(new BooleanSetting("Speed", true).markVisibilityDependency());
        this.bpsSetting = this.add(new BooleanSetting("BPS", false).dependsOn(this.speedSetting));
        this.tpsSetting = this.add(new BooleanSetting("TPS", true));
        this.pingSetting = this.add(new BooleanSetting("Ping", true));
        this.fpsSetting = this.add(new BooleanSetting("FPS", true));
        this.serverBrandSetting = this.add(new BooleanSetting("ServerBrand", true));
        this.durabilitySetting = this.add(new BooleanSetting("Durability", false));
        this.shulkersSetting = this.add(new BooleanSetting("Shulkers", false));
        this.chestSetting = this.add(new BooleanSetting("Chest", false).markVisibilityDependency());
        this.doubleChestSetting = this.add(new BooleanSetting("Double", false).dependsOn(this.chestSetting));
        this.metricRows = new ArrayList<>();
        this.setRenderer(new HudTextListRenderer(this, this.metricRows));
        this.getRenderer().setAnchor(HudAnchor.BOTTOM_RIGHT);
    }

    private static Component formatStackDurability(ItemStack stack) {
        int maximumDamage = stack.getMaxDamage();
        int remainingDurability = maximumDamage - stack.getDamageValue();
        float remainingFraction = (float) remainingDurability / maximumDamage;
        Color durabilityColor = ColorUtils.redToGreen(remainingFraction);
        int textColor = durabilityColor.hashCode();
        double fractionForNaNCheck = remainingFraction;
        if (Double.isNaN(fractionForNaNCheck)) {
            textColor = -1;
        }
        String text = String.valueOf(remainingDurability);
        MutableComponent component = Component.literal(text);
        int colorArgument = textColor;
        return component.withColor(colorArgument);
    }

    private static Component formatSpeedMetric(MetricsModule owner, boolean useBpsSuffix) {
        String speedUnit = useBpsSuffix
                ? Translations.ui("modules.Metrics.BlocksPerSecond")
                : Translations.ui("modules.Metrics.KilometersPerHour");
        String speedTemplate = Translations.ui("hud.SpeedFormat") + speedUnit;
        Object[] formatArguments = new Object[2];
        formatArguments[0] = ChatFormatting.WHITE;
        double displaySpeed = ClientContext.movementSpeedTracker.getDisplaySpeed();
        Setting<Boolean> bpsOptionForValue = owner.bpsSetting;
        Boolean useBpsValue = bpsOptionForValue.getValue();
        double unitDivisor;
        if (useBpsValue) {
            unitDivisor = 3.6D;
        } else {
            unitDivisor = 1.0D;
        }
        double speedValue = displaySpeed / unitDivisor;
        formatArguments[1] = speedValue;
        String formattedText = speedTemplate.formatted(formatArguments);
        String text = formattedText.replace(",", ".");
        return Component.literal(text);
    }

    private static Component formatDurabilityMetric(MetricsModule owner) {
        String label = Translations.ui("hud.DurabilityLabel");
        MutableComponent labelComponent = Component.literal(label);
        Component durabilityText = owner.getHeldItemDurability();
        return labelComponent.append(durabilityText);
    }

    private static float measureMetricWidth(FontRenderer textRenderer, HudTextRow row) {
        Component rowText = row.getText();
        return -textRenderer.getWidth(rowText.getString());
    }

    private static void countContainers(MetricsModule owner, WorldStateManager worldTracker) {
        for (BlockEntity blockEntity : worldTracker.getBlockEntities()) {
            if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity) {
                owner.chestAndBarrelCount++;
            }
            if (blockEntity instanceof ShulkerBoxBlockEntity) {
                owner.shulkerCount++;
            }
        }
        Setting<Boolean> doubleChestOption = owner.doubleChestSetting;
        Boolean combineChestPairs = doubleChestOption.getValue();
        if (combineChestPairs) {
            int combinedContainerCount = owner.chestAndBarrelCount;
            owner.chestAndBarrelCount = combinedContainerCount / 2;
        }
    }

    private static String[] createAliases() {
        String[] aliases = new String[2];
        aliases[0] = "infohud";
        aliases[1] = "info";
        return aliases;
    }

    private Component getHeldItemDurability() {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        ItemStack heldStack = player.getMainHandItem();
        Component durabilityText = formatStackDurability(heldStack);
        if (heldStack.getMaxDamage() == 0 && this.cachedDurabilityText != null) {
            return this.cachedDurabilityText;
        }
        this.cachedDurabilityText = durabilityText;
        return durabilityText;
    }

    @Override
    public void onEnable() {
        this.metricRows.clear();
        List<HudTextRow> rowsForRegistration = this.metricRows;
        HudTextRow speedRow = new HudTextRow(() -> {
            Setting<Boolean> bpsOptionForSuffix = this.bpsSetting;
            Boolean useBpsSuffix = bpsOptionForSuffix.getValue();
            return formatSpeedMetric(this, useBpsSuffix);
        }, this.speedSetting::getValue);
        HudTextRow tpsRow = new HudTextRow(() -> {
            Object[] formatArguments = new Object[2];
            formatArguments[0] = ChatFormatting.WHITE;
            ServerStateManager networkTracker = ClientContext.serverStateManager;
            float ticksPerSecond = networkTracker.getTickRate();
            formatArguments[1] = ticksPerSecond;
            String format = Translations.ui("hud.TPSFormat");
            String formattedText = format.formatted(formatArguments);
            String text = formattedText.replace(",", ".");
            return Component.literal(text);
        }, this.tpsSetting::getValue);
        HudTextRow pingRow = new HudTextRow(() -> {
            Object[] formatArguments = new Object[2];
            formatArguments[0] = ChatFormatting.WHITE;
            ServerStateManager networkTracker = ClientContext.serverStateManager;
            int pingMillis = networkTracker.getLatencyMillis();
            formatArguments[1] = pingMillis;
            String format = Translations.ui("hud.PingFormat");
            String text = format.formatted(formatArguments);
            return Component.literal(text);
        }, this.pingSetting::getValue);
        HudTextRow fpsRow = new HudTextRow(() -> {
            Object[] formatArguments = new Object[2];
            formatArguments[0] = ChatFormatting.WHITE;
            FrameTiming frameTiming = ClientContext.frameTiming;
            int framesPerSecond = frameTiming.getFramesPerSecond();
            formatArguments[1] = framesPerSecond;
            String format = Translations.ui("hud.FPSFormat");
            String text = format.formatted(formatArguments);
            return Component.literal(text);
        }, this.fpsSetting::getValue);
        HudTextRow serverBrandRow = new HudTextRow(() -> {
            Object[] formatArguments = new Object[2];
            formatArguments[0] = ChatFormatting.WHITE;
            ClientPacketListener connection = ClientAccess.evernightMinecraft.player.connection;
            formatArguments[1] = connection.serverBrand();
            String format = Translations.ui("hud.ServerBrandFormat");
            String text = format.formatted(formatArguments);
            return Component.literal(text);
        }, this.serverBrandSetting::getValue);
        HudTextRow durabilityRow = new HudTextRow(() -> formatDurabilityMetric(this), () -> {
            Setting<Boolean> durabilityOption = this.durabilitySetting;
            Boolean showDurability = durabilityOption.getValue();
            if (!showDurability) {
                return false;
            }
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            ItemStack heldStack = player.getMainHandItem();
            return heldStack.isDamageableItem();
        }
        );
        HudTextRow chestRow = new HudTextRow(() -> {
            Object[] formatArguments = new Object[2];
            formatArguments[0] = ChatFormatting.WHITE;
            int count = this.chestAndBarrelCount;
            formatArguments[1] = count;
            String format = Translations.ui("hud.ChestsFormat");
            String text = format.formatted(formatArguments);
            return Component.literal(text);
        }, this.chestSetting::getValue);
        HudTextRow shulkerRow = new HudTextRow(() -> {
            Object[] formatArguments = new Object[2];
            formatArguments[0] = ChatFormatting.WHITE;
            int count = this.shulkerCount;
            formatArguments[1] = count;
            String format = Translations.ui("hud.ShulkersFormat");
            String text = format.formatted(formatArguments);
            return Component.literal(text);
        }, this.shulkersSetting::getValue);
        rowsForRegistration.addAll(List.of(speedRow, tpsRow, pingRow, fpsRow, serverBrandRow, durabilityRow, chestRow, shulkerRow));
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent ignoredEvent) {
        List<HudTextRow> rowsToSort = this.metricRows;
        Function<HudTextRow, Float> metricWidth = row -> {
            FontRenderer textRenderer = FontRenderer.INSTANCE;
            return measureMetricWidth(textRenderer, row);
        };
        Comparator<HudTextRow> metricComparator = Comparator.comparing(metricWidth);
        rowsToSort.sort(metricComparator);
        this.shulkerCount = this.chestAndBarrelCount = 0;
        WorldStateManager worldTracker = ClientContext.worldStateManager;
        countContainers(this, worldTracker);
    }
}