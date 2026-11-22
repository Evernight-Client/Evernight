package evernight.client.modules.hud;

import evernight.client.media.core.MediaSnapshot;
import evernight.client.media.core.PortableMediaSessionProvider;

public final class MediaSessionBridge {
    private static final PortableMediaSessionProvider MEDIA = new PortableMediaSessionProvider(MediaSessionBridge::update);
    public static volatile int playbackStatus;
    public static volatile String artist = "";
    public static volatile String title = "";
    public static volatile long positionMillis;
    public static volatile long durationMillis;

    static {
        MEDIA.start();
        Runtime.getRuntime().addShutdownHook(new Thread(MEDIA::close, "evernight-media-stop"));
    }

    private MediaSessionBridge() {
    }

    private static void update(MediaSnapshot state) {
        artist = state.artist();
        title = state.title();
        positionMillis = state.positionMillis();
        durationMillis = state.durationMillis();
        playbackStatus = state.playbackStatus();
    }

    public static String getDisplayText() {
        return artist + " - " + title;
    }

    public static void stopMediaUpdates() {
        MEDIA.close();
    }
}