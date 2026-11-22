package evernight.client.events.network;

import net.minecraft.world.level.chunk.LevelChunk;
import evernight.client.events.core.CancellableEvent;

public final class ClientChunkCacheReplaceWithPacketDataEvent extends CancellableEvent {
    private final LevelChunk chunk;

    public ClientChunkCacheReplaceWithPacketDataEvent(LevelChunk chunk) {
        this.chunk = chunk;
    }

    public LevelChunk getChunk() {
        return this.chunk;
    }
}