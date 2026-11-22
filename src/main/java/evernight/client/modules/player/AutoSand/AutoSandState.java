package evernight.client.modules.player.AutoSand;

/**
 * State machine of the auto sand miner.
 *
 * <p>Internal only: the player never sees these names, so they are not part of the language table.
 */
enum AutoSandState {
    /**
     * Short buffer after a mode switch; returns to {@link #MINING}.
     */
    IDLE,
    /**
     * Mining sand with Baritone; the module does not constrain the mine process.
     */
    MINING,
    /**
     * Main inventory is full, storing the sand.
     */
    INVENTORY_FULL,
    /**
     * The shared storage chain owns the surface spot, container handling and reclaim.
     */
    STORAGE_CHAIN,
    /**
     * Repairing the shovel with experience bottles.
     */
    REPAIRING_SHOVEL
}
