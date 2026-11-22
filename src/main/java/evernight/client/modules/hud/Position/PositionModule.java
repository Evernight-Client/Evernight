package evernight.client.modules.hud.Position;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.network.PacketEvent;
import evernight.client.managers.task.TaskScheduler;
import evernight.client.modules.HudWidgetModule;
import evernight.client.modules.misc.CoordinateHider.CoordinateHiderModule;
import evernight.client.modules.player.Freecam.FreecamModule;
import evernight.client.render.text.Translations;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.settings.core.SettingLabelCondition;
import evernight.client.settings.options.DimensionType;
import evernight.client.settings.options.HudAnchor;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.world.CoordinateCensor;
import evernight.client.utils.collections.IntPair;
import evernight.client.utils.math.MathUtils;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.TimeUnit;
import evernight.client.modules.hud.HudTextListRenderer;
import evernight.client.modules.hud.HudTextRow;

public class PositionModule extends HudWidgetModule {
    private static final FreecamModule freecam;

    static {
        freecam = ClientContext.getModule(FreecamModule.class);
    }

    private final Setting<Float> safeRangeSetting;
    private final Setting<Boolean> netherSetting;
    private final Setting<Boolean> prefixSetting;
    private final Setting<Boolean> fakeSetting;
    private final ElapsedTimer redactionTimer;
    private IntPair fakeOffset;

    public PositionModule() {
        super("Position", "coordinates");
        NumberSetting<Float> safeRangeOption = new NumberSetting<>(
                "SafeRange", 0.0F, 0.0F, 100.0F
        );
        String safeRangeUnit = "k";
        NumberSetting<Float> safeRangeWithUnit = safeRangeOption.withUnit(safeRangeUnit);
        this.safeRangeSetting = this.add(safeRangeWithUnit);
        this.netherSetting = this.add(new BooleanSetting("Nether", true));
        this.prefixSetting = this.add(new BooleanSetting("Prefix", true));
        this.fakeSetting = this.add(new BooleanSetting("Fake", false));
        this.redactionTimer = new ElapsedTimer();
        Setting<Float> safeRangeForDisplay = this.safeRangeSetting;
        SettingLabelCondition minimumDisplayBound = SettingLabelCondition.MIN;
        String minimumDisplayText = Translations.ui("gui.None");
        safeRangeForDisplay.setBoundaryLabel(minimumDisplayText, minimumDisplayBound);
        Setting<Boolean> fakeCallbackOption = this.fakeSetting;
        Runnable fakeCallback = () -> {
            TaskScheduler scheduler = ClientContext.taskScheduler;
            Runnable offsetTask = () -> {
                boolean negateX = MathUtils.passesRandomThreshold(50);
                boolean negateZ = MathUtils.passesRandomThreshold(50);
                LocalPlayer playerForBaseX = ClientAccess.evernightMinecraft.player;
                double negatedPlayerX = -playerForBaseX.getX();
                double xBase = negatedPlayerX * 2.0D;
                double randomX = Math.random();
                LocalPlayer playerForRandomX = ClientAccess.evernightMinecraft.player;
                double scaledRandomX = randomX * playerForRandomX.getX();
                int fakeX = (int) (xBase + scaledRandomX * 4.0D);
                LocalPlayer playerForBaseZ = ClientAccess.evernightMinecraft.player;
                double negatedPlayerZ = -playerForBaseZ.getZ();
                double zBase = negatedPlayerZ * 2.0D;
                double randomZ = Math.random();
                LocalPlayer playerForRandomZ = ClientAccess.evernightMinecraft.player;
                double scaledRandomZ = randomZ * playerForRandomZ.getZ();
                int fakeZ = (int) (zBase + scaledRandomZ * 4.0D);
                fakeX += 13;
                fakeZ -= 37;
                if (negateX) {
                    fakeX = -fakeX;
                }
                if (negateZ) {
                    fakeZ = -fakeZ;
                }
                this.fakeOffset = new IntPair(fakeX, fakeZ);
            };
            scheduler.scheduleRotationTask(offsetTask, 0);
        };
        fakeCallbackOption.setChangeCallback(fakeCallback);
        HudTextRow coordinateRow = new HudTextRow(() -> {
            coordinateVisibility:
            if (!this.isWorldUnavailable()) {
                Setting<Float> safeRangeOptionForEnabled = this.safeRangeSetting;
                Float safeRangeEnabledValue = safeRangeOptionForEnabled.getValue();
                if (safeRangeEnabledValue > 0.0F) {
                    Vec3 positionForSafeRange = ClientAccess.evernightMinecraft.player.position();
                    Vec3 origin = Vec3.ZERO;
                    Setting<Float> safeRangeOptionForDistance = this.safeRangeSetting;
                    Float safeRangeValue = safeRangeOptionForDistance.getValue();
                    float safeRangeThousands = safeRangeValue;
                    double safeRangeDistance = safeRangeThousands * 1000.0F;
                    if (!positionForSafeRange.closerThan(origin, safeRangeDistance)) {
                        if (this.isRedactionGraceActive()) {
                            break coordinateVisibility;
                        }
                    }
                }
                ClientLevel level = ClientAccess.evernightMinecraft.level;
                ResourceKey<?> dimension = level.dimension();
                Identifier dimensionIdentifier = dimension.identifier();
                String dimensionPath = dimensionIdentifier.getPath();
                boolean inNether = dimensionPath.contains("nether");
                Vec3 displayPosition = ClientAccess.evernightMinecraft.player.position();
                FreecamModule freecamForEnabledCheck = freecam;
                if (freecamForEnabledCheck.isToggled()) {
                    Camera camera = ClientAccess.evernightMinecraft.gameRenderer.getMainCamera();
                    Vec3 cameraPosition = camera.position();
                    double playerHeight = ClientAccess.evernightMinecraft.player.getBbHeight();
                    displayPosition = cameraPosition.subtract(0.0D, playerHeight, 0.0D);
                }
                Setting<Boolean> fakeDisplayOption = this.fakeSetting;
                Boolean useFakeOffset = fakeDisplayOption.getValue();
                if (useFakeOffset && this.fakeOffset != null) {
                    IntPair offsetForXCheck = this.fakeOffset;
                    if (offsetForXCheck.first() == 0) {
                        IntPair offsetForZCheck = this.fakeOffset;
                        if (offsetForZCheck.second() == 0) {
                            this.fakeSetting.runChangeCallback();
                        }
                    }
                    double offsetX = this.fakeOffset.first();
                    double offsetZ = this.fakeOffset.second();
                    displayPosition = displayPosition.add(offsetX, 0.0D, offsetZ);
                }
                BlockPos currentBlockPosition = BlockPos.containing(displayPosition);
                BlockPos convertedBlockPosition;
                if (inNether) {
                    Vec3 convertedPosition = displayPosition.multiply(8.0D, 1.0D, 8.0D);
                    convertedBlockPosition = BlockPos.containing(convertedPosition);
                } else {
                    Vec3 convertedPosition = displayPosition.multiply(0.125D, 1.0D, 0.125D);
                    convertedBlockPosition = BlockPos.containing(convertedPosition);
                }
                String coordinatePrefix = this.getCoordinatePrefix();
                ChatFormatting prefixColor = ChatFormatting.WHITE;
                String prefixColorText = String.valueOf(prefixColor);
                String formattedPrefix = coordinatePrefix + prefixColorText;
                int currentX = currentBlockPosition.getX();
                int currentY = currentBlockPosition.getY();
                int currentZ = currentBlockPosition.getZ();
                String coordinateText = currentX + ", " + currentY + ", " + currentZ;
                int convertedX = convertedBlockPosition.getX();
                int convertedZ = convertedBlockPosition.getZ();
                String convertedText = " (" + convertedX + ", " + convertedZ + ")";
                Setting<Boolean> netherDisplayOption = this.netherSetting;
                Boolean showConvertedPosition = netherDisplayOption.getValue();
                if (showConvertedPosition) {
                    if (WorldUtils.getDimension() != DimensionType.THE_END) {
                        coordinateText = coordinateText + convertedText;
                    }
                }
                String positionText = formattedPrefix + coordinateText;
                if (CoordinateHiderModule.shouldHideHudCoordinates()) {
                    positionText = CoordinateCensor.censorAllNumbers(positionText);
                }
                return Component.literal(positionText);
            }
            Object[] redactedFormatArguments = new Object[2];
            redactedFormatArguments[0] = this.getCoordinatePrefix();
            redactedFormatArguments[1] = ChatFormatting.WHITE;
            String redactedText = Translations.format("modules.Position.Redacted", redactedFormatArguments);
            return Component.literal(redactedText);
        }, () -> true);
        this.setRenderer(new HudTextListRenderer(this, coordinateRow));
        this.getRenderer().setAnchor(HudAnchor.BOTTOM_LEFT);
    }

    private boolean isRedactionGraceActive() {
        int playerTicks = ClientAccess.evernightMinecraft.player.tickCount;
        if (playerTicks >= 200) {
            ElapsedTimer timer = this.redactionTimer;
            double delaySeconds = 5.0D;
            TimeUnit delayUnit = TimeUnit.SECONDS;
            return !timer.hasElapsed(delaySeconds, delayUnit);
        }
        return true;
    }

    private String getCoordinatePrefix() {
        Setting<Boolean> prefixOption = this.prefixSetting;
        Boolean showPrefix = prefixOption.getValue();
        if (!showPrefix) {
            return "";
        }
        return Translations.ui("hud.XYZ") + " ";
    }

    @EventHandler
    private void onPlayerPositionPacket(PacketEvent.Receive event) {
        if (!this.isWorldUnavailable()) {
            if (event.getPacket() instanceof ClientboundPlayerPositionPacket packet) {
                PositionMoveRotation positionChange = packet.change();
                Vec3 packetPosition = positionChange.position();
                LocalPlayer player = ClientAccess.evernightMinecraft.player;
                Vec3 playerPosition = player.position();
                double distanceSquared = packetPosition.distanceToSqr(playerPosition);
                if (distanceSquared >= 10000.0D) {
                    this.redactionTimer.reset();
                }
            }
        }
    }
}