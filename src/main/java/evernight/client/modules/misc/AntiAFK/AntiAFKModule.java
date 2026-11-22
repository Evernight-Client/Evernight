package evernight.client.modules.misc.AntiAFK;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.player.InputOverrideManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.text.Translations;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.utils.math.MathConstants;
import evernight.mixin.minecraft.client.MinecraftAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.input.InputPressEvent;
import evernight.client.events.input.MouseHandlerTurnPlayerEvent;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.ui.ChatMessageEvent;

public class AntiAFKModule extends Module {
    final Setting<Boolean> attackSetting;
    private final Setting<Float> delaySetting;
    private final Setting<Boolean> autoReplySetting;
    private final Setting<Boolean> jumpSetting;
    private final Setting<Boolean> sneakSetting;
    private final Setting<Boolean> rotateSetting;
    private final Setting<Boolean> onlySwingSetting;
    private final ElapsedTimer idleTimer;
    private final ElapsedTimer actionTimer;
    private boolean jumpActionPending;
    private boolean sneakActionPending;

    public AntiAFKModule() {
        super("AntiAFK", ModuleCategory.MISC);
        this.delaySetting = this.add(new NumberSetting<>("Delay", 15.0F, 1.0F, 60.0F).withUnit("s"));
        Boolean autoReplyDefault = false;
        this.autoReplySetting = this.add(new BooleanSetting("AutoReply", autoReplyDefault));
        Boolean jumpDefault = false;
        this.jumpSetting = this.add(new BooleanSetting("Jump", jumpDefault));
        Boolean sneakDefault = false;
        this.sneakSetting = this.add(new BooleanSetting("Sneak", sneakDefault));
        Boolean rotateDefault = false;
        this.rotateSetting = this.add(new BooleanSetting("Rotate", rotateDefault));
        Boolean attackDefault = false;
        this.attackSetting = this.add(new BooleanSetting("Attack", attackDefault));
        Predicate<Object> onlySwingVisible = ignored -> this.attackSetting.getValue();
        Boolean onlySwingDefault = false;
        this.onlySwingSetting = this.add(new BooleanSetting("OnlySwing", onlySwingDefault, onlySwingVisible));
        this.idleTimer = new ElapsedTimer();
        this.actionTimer = new ElapsedTimer();
    }

    private List<Runnable> createActions() {
        List<Runnable> actions = new ArrayList<>();
        Setting<Boolean> jumpSnapshot = this.jumpSetting;
        Boolean jumpEnabled = jumpSnapshot.getValue();
        if (jumpEnabled) {
            actions.add(() -> this.jumpActionPending = true);
        }
        Setting<Boolean> sneakSnapshot = this.sneakSetting;
        Boolean sneakEnabled = sneakSnapshot.getValue();
        if (sneakEnabled) {
            actions.add(() -> this.sneakActionPending = true);
        }
        Setting<Boolean> rotateSnapshot = this.rotateSetting;
        Boolean rotateEnabled = rotateSnapshot.getValue();
        if (rotateEnabled) {
            Random rotationRandom = new Random();
            actions.add(() -> {
                        LocalPlayer rotationPlayerSnapshot = ClientAccess.evernightMinecraft.player;
                        float yaw = rotationRandom.nextFloat(MathConstants.FULL_TURN_DEGREES);
                        rotationPlayerSnapshot.setYRot(yaw);
                        rotationPlayerSnapshot = ClientAccess.evernightMinecraft.player;
                        float pitch = rotationRandom.nextFloat(1.8e+02F) - MathConstants.NINETY_DEGREES;
                        rotationPlayerSnapshot.setXRot(pitch);
                    }
            );
        }
        Setting<Boolean> attackSnapshot = this.attackSetting;
        Boolean attackEnabled = attackSnapshot.getValue();
        if (attackEnabled) {
            actions.add(() -> {
                Setting<Boolean> onlySwingSnapshot = this.onlySwingSetting;
                Boolean onlySwing = onlySwingSnapshot.getValue();
                if (onlySwing) {
                    LocalPlayer swingPlayer = ClientAccess.evernightMinecraft.player;
                    InteractionHand swingHand = InteractionHand.MAIN_HAND;
                    swingPlayer.swing(swingHand);
                } else {
                    ((MinecraftAccessor) ClientAccess.evernightMinecraft).attack();
                }
            });
        }
        return actions;
    }

    private boolean isIdle() {
        ElapsedTimer timerSnapshot = this.idleTimer;
        Setting<Float> delaySnapshot = this.delaySetting;
        float delaySeconds = delaySnapshot.getValue();
        long delayMillis = (long) (delaySeconds * 1e+03F);
        return timerSnapshot.hasElapsed(delayMillis);
    }

    @Override
    public void onEnable() {
        this.idleTimer.reset();
        this.actionTimer.reset();
    }

    @EventHandler
    private void onInputActivity(InputPressEvent event) {
        this.idleTimer.reset();
    }

    @EventHandler
    private void onMouseHandlerTurnPlayerEvent(MouseHandlerTurnPlayerEvent event) {
        this.idleTimer.reset();
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        if (!this.isIdle()) {
            return;
        }
        KeyMapping jumpKeySnapshot = ClientAccess.evernightMinecraft.options.keyJump;
        if (jumpKeySnapshot.isDown()) {
            Setting<Boolean> jumpSettingSnapshot = this.jumpSetting;
            Boolean jumpEnabled = jumpSettingSnapshot.getValue();
            if (jumpEnabled) {
                boolean jumpPressed = false;
                KeyMapping jumpKey = ClientAccess.evernightMinecraft.options.keyJump;
                jumpKey.setDown(jumpPressed);
            }
        }
        ElapsedTimer timerCapture = this.actionTimer;
        Setting<Float> delaySnapshot = this.delaySetting;
        Float delayValue = delaySnapshot.getValue();
        double delaySecondsCapture = delayValue.floatValue();
        TimeUnit delayUnit = TimeUnit.SECONDS;
        if (timerCapture.hasElapsed(delaySecondsCapture, delayUnit)) {
            List<Runnable> actions = this.createActions();
            Collections.shuffle(actions);
            if (!actions.isEmpty()) {
                actions.get(0).run();
            }
            timerCapture = this.actionTimer;
            timerCapture.reset();
        }
        if (this.jumpActionPending) {
            InputOverrideManager jumpOverridesSnapshot = ClientContext.inputOverrides;
            jumpOverridesSnapshot.requestJump((true));
        }
        if (this.sneakActionPending) {
            InputOverrideManager sneakOverridesSnapshot = ClientContext.inputOverrides;
            sneakOverridesSnapshot.requestSneak((false));
        }
        this.jumpActionPending = this.sneakActionPending = false;
    }

    @EventHandler(priority = 100)
    private void onIncomingMessage(ChatMessageEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Setting<Boolean> autoReplySnapshot = this.autoReplySetting;
        Boolean autoReplyEnabled = autoReplySnapshot.getValue();
        if (!autoReplyEnabled) {
            return;
        }
        if (!this.isIdle()) {
            return;
        }
        if (event.getStage() != EventStage.Pre || event.getMessage() == null) {
            return;
        }
        String messageCapture = event.getMessage().getString();
        if (!ChatUtils.isIncomingWhisper(messageCapture)) {
            return;
        }
        BlockableEventLoop<?> replyClient = ClientAccess.evernightMinecraft;
        Runnable replyAction = () -> {
            ClientPacketListener replyConnectionCapture = ClientAccess.evernightMinecraft.player.connection;
            LocalPlayer replyPlayer = ClientAccess.evernightMinecraft.player;
            String playerName = replyPlayer.getName().getString();
            double randomSample = Math.random();
            double replySuffixValue = randomSample * 10000.0D;
            int replySuffix = (int) Math.floor(replySuffixValue);
            String replyCommand = Translations.format("modules.AntiAFK.AutoReply", playerName, replySuffix);
            replyConnectionCapture.sendCommand(replyCommand);
        };
        replyClient.executeIfPossible(replyAction);
    }
}