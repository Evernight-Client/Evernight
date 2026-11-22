package evernight.client.events.player;

import net.minecraft.world.entity.player.Player;
import evernight.client.events.core.CancellableEvent;

public final class PlayerTotemEvent extends CancellableEvent {
    private final Player player;
    private final int popCount;
    private final PlayerTotemEvent.Kind kind;

    public PlayerTotemEvent(Player player, int popCount, PlayerTotemEvent.Kind kind) {
        this.player = player;
        this.popCount = popCount;
        this.kind = kind;
    }

    public Player getPlayer() {
        return this.player;
    }

    public int getPopCount() {
        return this.popCount;
    }

    public PlayerTotemEvent.Kind getKind() {
        return this.kind;
    }

    public String getName() {
        return this.player.getGameProfile().name();
    }

    public enum Kind {
        TOTEM_POP, DEATH
    }
}