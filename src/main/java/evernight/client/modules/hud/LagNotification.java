package evernight.client.modules.hud;

import evernight.client.ClientContext;
import evernight.client.managers.world.ServerStateManager;
import evernight.client.render.text.Translations;

import java.awt.*;

public final class LagNotification extends HudNotification {
    private boolean finished;
    private float lagSeconds;

    public LagNotification() {
        super("", 3600000L, createColor());
    }

    private static Color createColor() {
        return new Color(128, 128, 128, 255);
    }

    @Override
    public void update() {
        long nowMillis = System.currentTimeMillis();
        ServerStateManager networkActivity = ClientContext.serverStateManager;
        long idleMillis = nowMillis - networkActivity.getLastPacketTime();
        if (idleMillis < 1000L && !this.finished) {
            long completionTimeMillis = System.currentTimeMillis();
            this.endTimeMillis = completionTimeMillis + 500L;
            this.finished = true;
        }
        if (!this.finished) {
            float idleMillisAsFloat = (float) idleMillis;
            this.lagSeconds = idleMillisAsFloat / 1000.0F;
        }
        super.text = Translations.format("modules.Welcomer.LagNotification", this.lagSeconds);
    }

    public void finish() {
        if (!this.finished) {
            long nowMillis = System.currentTimeMillis();
            this.endTimeMillis = nowMillis + 500L;
            this.finished = true;
        }
    }
}