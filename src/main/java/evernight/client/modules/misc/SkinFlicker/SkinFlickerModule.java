package evernight.client.modules.misc.SkinFlicker;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.managers.registry.ShaderRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.shader.IrisShaderBridge;
import evernight.client.utils.core.ElapsedTimer;
import net.minecraft.client.Options;
import net.minecraft.world.entity.player.PlayerModelPart;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class SkinFlickerModule extends Module {
    private static final PlayerModelPart[] HORIZONTAL_PART_ORDER;
    private static final PlayerModelPart[] VERTICAL_PART_ORDER;

    static {
        PlayerModelPart[] horizontalParts = new PlayerModelPart[(6)];
        horizontalParts[0] = PlayerModelPart.LEFT_SLEEVE;
        horizontalParts[1] = PlayerModelPart.JACKET;
        horizontalParts[2] = PlayerModelPart.HAT;
        horizontalParts[3] = PlayerModelPart.LEFT_PANTS_LEG;
        horizontalParts[4] = PlayerModelPart.RIGHT_PANTS_LEG;
        horizontalParts[(5)] = PlayerModelPart.RIGHT_SLEEVE;
        HORIZONTAL_PART_ORDER = horizontalParts;
        PlayerModelPart[] verticalParts = new PlayerModelPart[6];
        verticalParts[0] = PlayerModelPart.HAT;
        verticalParts[1] = PlayerModelPart.JACKET;
        verticalParts[(2)] = PlayerModelPart.LEFT_SLEEVE;
        verticalParts[3] = PlayerModelPart.RIGHT_SLEEVE;
        verticalParts[4] = PlayerModelPart.LEFT_PANTS_LEG;
        verticalParts[(5)] = PlayerModelPart.RIGHT_PANTS_LEG;
        VERTICAL_PART_ORDER = verticalParts;
    }

    private final Setting<ModeChoice> modeSetting;
    private final Setting<VisibilityChoice> visibilitySetting;
    private final Setting<Boolean> capeSetting;
    private final Setting<Float> delaySetting;
    private final Map<PlayerModelPart, Boolean> savedModelParts;
    private final int modelPartCount;
    private final ElapsedTimer flickerTimer;
    private boolean optionsDirty;
    private int sequenceIndex;

    public SkinFlickerModule() {
        super("SkinFlicker", ModuleCategory.MISC, "skinblinker");
        this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.FULL));
        this.visibilitySetting = this.add(new EnumSetting<>("Visibility", VisibilityChoice.ALL));
        this.capeSetting = this.add(new BooleanSetting("Cape", false));
        this.delaySetting = this.add(new NumberSetting<>("Delay", 1.0F, 0.0F, 10.0F).withUnit("s"));
        this.savedModelParts = new HashMap<>();
        this.modelPartCount = PlayerModelPart.values().length;
        this.flickerTimer = new ElapsedTimer();
    }

    @Override
    public void onEnable() {
        for (PlayerModelPart part : PlayerModelPart.values()) {
            Map<PlayerModelPart, Boolean> savedPartsSnapshot = this.savedModelParts;
            Options options = ClientAccess.evernightMinecraft.options;
            boolean enabled = options.isModelPartEnabled(part);
            savedPartsSnapshot.put(part, enabled);
        }
    }

    @Override
    public void onDisable() {
        Map<PlayerModelPart, Boolean> savedPartsSnapshot = this.savedModelParts;
        Options options = ClientAccess.evernightMinecraft.options;
        savedPartsSnapshot.forEach(options::setModelPart);
    }

    @EventHandler
    private void onPlayerTick(LocalPlayerTickEvent event) {
        ElapsedTimer timerSnapshot = this.flickerTimer;
        Setting<Float> delaySettingSnapshot = this.delaySetting;
        Float delayValue = delaySettingSnapshot.getValue();
        double delaySeconds = delayValue.floatValue();
        TimeUnit delayUnit = TimeUnit.SECONDS;
        if (timerSnapshot.hasElapsed(delaySeconds, delayUnit)) {
            Setting<ModeChoice> modeSettingSnapshot = this.modeSetting;
            SkinFlickerModule.ModeChoice mode = modeSettingSnapshot.getValue();
            switch (mode.ordinal()) {
                case 0:
                    for (PlayerModelPart part : PlayerModelPart.values()) {
                        if (this.shouldTogglePart(part)) {
                            this.togglePart(part);
                        }
                    }
                    break;
                case 1:
                case 2:
                    Setting<ModeChoice> sequenceModeSnapshot = this.modeSetting;
                    PlayerModelPart[] partOrder = sequenceModeSnapshot.getValue() == SkinFlickerModule.ModeChoice.HORIZONTAL
                            ? HORIZONTAL_PART_ORDER : VERTICAL_PART_ORDER;
                    int indexSnapshot = this.sequenceIndex;
                    PlayerModelPart sequencePart = partOrder[indexSnapshot % (6)];
                    if (this.shouldTogglePart(sequencePart)) {
                        this.togglePart(sequencePart);
                        int nextIndex = this.sequenceIndex + 1;
                        this.sequenceIndex = nextIndex % (6);
                    }
                    break;
                case 3:
                    PlayerModelPart[] allParts = PlayerModelPart.values();
                    ThreadLocalRandom randomSnapshot = ThreadLocalRandom.current();
                    int randomBound = this.modelPartCount;
                    PlayerModelPart randomPart = allParts[randomSnapshot.nextInt(randomBound)];
                    if (this.shouldTogglePart(randomPart)) {
                        this.togglePart(randomPart);
                    }
            }
            if (this.optionsDirty) {
                Options options = ClientAccess.evernightMinecraft.options;
                options.broadcastOptions();
                this.optionsDirty = false;
            }
            timerSnapshot = this.flickerTimer;
            timerSnapshot.reset();
        }
    }

    private void togglePart(PlayerModelPart part) {
        this.optionsDirty = true;
        Options writeOptionsSnapshot = ClientAccess.evernightMinecraft.options;
        Options readOptions = ClientAccess.evernightMinecraft.options;
        boolean enabled = !readOptions.isModelPartEnabled(part);
        writeOptionsSnapshot.setModelPart(part, enabled);
    }

    private boolean shouldTogglePart(PlayerModelPart part) {
        Setting<Boolean> capeSettingSnapshot = this.capeSetting;
        Boolean includeCape = capeSettingSnapshot.getValue();
        return includeCape || part != PlayerModelPart.CAPE;
    }

    /**
     * Whether the local client has to render the player with the model parts captured on enable instead of the
     * parts currently being flickered. The options keep flickering and are still broadcast, so other players keep
     * seeing the effect; only the local render is affected.
     */
    public boolean shouldSuppressLocalFlicker() {
        if (!this.isToggled()) {
            return false;
        }
        return switch (this.visibilitySetting.getValue()) {
            case ALL -> false;
            case OTHERS -> true;
            case NO_SHADER -> this.isShaderPassActive();
        };
    }

    /**
     * True while a shader pass is drawing. Every model part of the local player (not just the first person sleeve)
     * then uses the captured skin, so the shader output stays stable while the normal view keeps flickering.
     */
    private boolean isShaderPassActive() {
        ShaderRegistry shaderRegistry = ClientContext.shaderRegistry;
        if (shaderRegistry != null && (shaderRegistry.renderingIntoTarget || shaderRegistry.renderingHands)) {
            return true;
        }
        return IrisShaderBridge.isHandRendererActive();
    }

    /**
     * Model part visibility captured when the module was enabled, used as the stable value for local renders.
     */
    public boolean getSavedModelPart(PlayerModelPart part) {
        Boolean savedPart = this.savedModelParts.get(part);
        return savedPart == null || savedPart;
    }

    public enum VisibilityChoice implements NamedEntry {
        ALL("All"),
        OTHERS("Others"),
        NO_SHADER("NoShader");
        private final String displayName;

        VisibilityChoice(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }

    public enum ModeChoice implements NamedEntry {
        FULL("Full"),
        VERTICAL("Vertical"),
        HORIZONTAL("Horizontal"),
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