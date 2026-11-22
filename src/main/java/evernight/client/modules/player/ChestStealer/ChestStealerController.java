package evernight.client.modules.player.ChestStealer;

import evernight.client.ClientAccess;

public abstract class ChestStealerController implements ClientAccess {
    protected final ChestStealerModule module;

    public ChestStealerController(ChestStealerModule module) {
        this.module = module;
    }
}