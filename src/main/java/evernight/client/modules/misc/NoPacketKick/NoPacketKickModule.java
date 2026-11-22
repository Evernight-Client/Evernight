package evernight.client.modules.misc.NoPacketKick;

import evernight.client.ClientAccess;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.text.Translations;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.chat.ChatUtils;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.SkipPacketException;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * Keeps the session alive when a broken packet would normally disconnect the client.
 *
 * <p>Two interception strategies are available:
 * <ul>
 *     <li><b>Legacy</b> (default) - the historical Evernight behaviour: decode failures consume the
 *     rest of the frame and rethrow, and {@code Connection#exceptionCaught} swallows every throwable
 *     while the module is enabled. Aggressive, but it also hides timeouts and leaves the frame stream
 *     poisoned when the failure happens before the packet payload is reached.</li>
 *     <li><b>Enhanced</b> - the PacketFix-style behaviour, enabled by the {@code Enhanced} category
 *     switch (right-click expands the three switches below it, each configuring one failure window):
 *     <ul>
 *         <li>{@link InterceptionType#DECODE} - the packet codec failed for one frame. The frame is
 *         consumed and reported as {@link net.minecraft.network.SkipPacketDecoderException}, which
 *         vanilla treats as a skippable packet instead of a kick.</li>
 *         <li>{@link InterceptionType#HANDLER} - a packet handler threw, which would otherwise show the
 *         {@code Network Protocol Error} screen via
 *         {@link net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl#onPacketError}.</li>
 *         <li>{@link InterceptionType#CONNECTION} - the netty pipeline reported a decode fault. The
 *         connection is kept open, but at most {@link #MAX_CONNECTION_FAULTS} times per
 *         {@link #FAULT_WINDOW_MILLIS}, so a permanently desynchronised stream still falls through to
 *         the vanilla disconnect instead of spinning forever.</li>
 *     </ul>
 *     Only the {@code PLAY} protocol is touched in enhanced mode; handshake and configuration failures
 *     keep vanilla behaviour.</li>
 * </ul>
 *
 * <p>Every interception is logged, and the {@code Message} switch mirrors it into chat with a one
 * notice per second throttle.
 */
public class NoPacketKickModule extends Module {
    private static final String LEGACY_DECODER_EXCEPTION_MARKER = "io.netty.handler.codec.DecoderException";
    private static final long NOTICE_INTERVAL_MILLIS = 1000L;
    private static final long FAULT_WINDOW_MILLIS = 10_000L;
    private static final int MAX_CONNECTION_FAULTS = 8;
    private static final int MAX_DETAIL_LENGTH = 120;
    public final Setting<Boolean> messageSetting;
    private final Setting<Boolean> enhancedSetting;
    private final Setting<Boolean> decodeErrorsSetting;
    private final Setting<Boolean> handlerErrorsSetting;
    private final Setting<Boolean> connectionErrorsSetting;
    private int connectionFaults;
    private long connectionFaultWindowStart;
    private String lastPublishedDetail = "";
    private long lastPublishMillis;
    public NoPacketKickModule() {
        super("NoPacketKick", ModuleCategory.MISC);
        this.messageSetting = this.add(new BooleanSetting("Message", false));
        this.enhancedSetting = this.add(new BooleanSetting("Enhanced", false).markVisibilityDependency());
        this.decodeErrorsSetting = this.add(
                new BooleanSetting("DecodeErrors", true).dependsOn(this.enhancedSetting));
        this.handlerErrorsSetting = this.add(
                new BooleanSetting("HandlerErrors", true).dependsOn(this.enhancedSetting));
        this.connectionErrorsSetting = this.add(
                new BooleanSetting("ConnectionErrors", true).dependsOn(this.enhancedSetting));
        this.setDrawn(false);
    }

    private static String describe(Throwable failure) {
        String detail = String.valueOf(failure);
        return detail.length() > MAX_DETAIL_LENGTH
                ? detail.substring(0, MAX_DETAIL_LENGTH) + "..."
                : detail;
    }

    @Override
    public void onEnable() {
        this.connectionFaults = 0;
        this.connectionFaultWindowStart = 0L;
        this.lastPublishedDetail = "";
        this.lastPublishMillis = 0L;
    }

    /**
     * Whether the historical interception is active (module enabled and enhanced mode off).
     */
    public boolean shouldUseLegacyInterception() {
        return this.isToggled() && !this.enhancedSetting.getValue();
    }

    /**
     * Legacy behaviour of the decoder hook: consume the failed frame and rethrow the original
     * exception. The connection survives because {@link #shouldUseLegacyInterception()} also makes
     * {@code Connection#exceptionCaught} swallow it.
     */
    public boolean shouldSkipLegacyDecodeError() {
        return this.shouldUseLegacyInterception();
    }

    /**
     * Legacy behaviour of the disconnect screen hook: hide kicks whose reason embeds a decoder
     * exception.
     */
    public boolean shouldSuppressLegacyDisconnect(String reason) {
        return this.shouldUseLegacyInterception() && reason.contains(LEGACY_DECODER_EXCEPTION_MARKER);
    }

    /**
     * Enhanced mode: whether a frame that fails to decode should be dropped instead of disconnecting
     * the player.
     */
    public boolean shouldSkipDecodeError(ConnectionProtocol protocol) {
        return this.isEnhanced() && this.decodeErrorsSetting.getValue() && protocol == ConnectionProtocol.PLAY;
    }

    /**
     * Enhanced mode: whether an exception thrown by a packet handler should be logged and ignored
     * instead of triggering the {@code disconnect.packetError} disconnect.
     */
    public boolean shouldIgnoreHandlerError(ConnectionProtocol protocol) {
        return this.isEnhanced() && this.handlerErrorsSetting.getValue() && protocol == ConnectionProtocol.PLAY;
    }

    /**
     * Enhanced mode: whether a netty pipeline decode fault should be swallowed. Skippable packets are
     * left to vanilla, and the suppression is capped so a hopeless stream eventually disconnects
     * normally.
     *
     * @return {@code true} when the caller must cancel vanilla exception handling
     */
    public boolean shouldIgnoreConnectionError(Throwable failure, ConnectionProtocol protocol) {
        if (!this.isEnhanced() || !this.connectionErrorsSetting.getValue()
                || protocol != ConnectionProtocol.PLAY) {
            return false;
        }
        if (!(failure instanceof DecoderException) || failure instanceof SkipPacketException) {
            return false;
        }
        long now = Util.getMillis();
        if (now - this.connectionFaultWindowStart > FAULT_WINDOW_MILLIS) {
            this.connectionFaultWindowStart = now;
            this.connectionFaults = 0;
        }
        return ++this.connectionFaults <= MAX_CONNECTION_FAULTS;
    }

    /**
     * Records one intercepted failure: always logs it (throttled by content), and mirrors it into chat
     * when the {@code Message} switch is enabled.
     */
    public void logInterceptedException(Throwable failure, InterceptionType type) {
        String detail = describe(failure);
        long now = Util.getMillis();
        boolean firstNotice = !detail.equals(this.lastPublishedDetail)
                || now - this.lastPublishMillis >= NOTICE_INTERVAL_MILLIS;
        if (firstNotice) {
            this.lastPublishedDetail = detail;
            this.lastPublishMillis = now;
            Logger logger = ClientAccess.logger;
            logger.warn("NoPacketKick: suppressed {} error: {}",
                    type.name().toLowerCase(Locale.ROOT), detail);
        }
        if (!firstNotice || !this.messageSetting.getValue()) {
            return;
        }
        ChatUtils.printChat(
                Component.literal(Translations.format(type.translationKey, detail)),
                ChatUtils.moduleSignature(this));
    }

    private boolean isEnhanced() {
        return this.isToggled() && this.enhancedSetting.getValue();
    }

    public enum InterceptionType {
        DECODE("modules.NoPacketKick.DecodeError"),
        HANDLER("modules.NoPacketKick.HandlerError"),
        CONNECTION("modules.NoPacketKick.ConnectionError");

        private final String translationKey;

        InterceptionType(String translationKey) {
            this.translationKey = translationKey;
        }
    }
}
