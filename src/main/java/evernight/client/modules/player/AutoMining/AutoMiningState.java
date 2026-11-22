package evernight.client.modules.player.AutoMining;

/**
 * State machine of the auto miner.
 *
 * <p>Internal only: the player never sees these names, so they are not part of the language table.
 */
enum AutoMiningState {
    /**
     * Short buffer after a mode switch; returns to {@link #MINING}.
     */
    IDLE,
    /**
     * Baritone is mining the configured target blocks.
     */
    MINING,
    /**
     * Nether only: mining quartz to repair the pickaxe with the gained experience.
     */
    REPAIRING_PICKAXE,
    /**
     * Main inventory is full, storing the loot through the shared storage chain.
     */
    INVENTORY_FULL,
    /**
     * The shared storage chain owns the safe spot search, container handling and reclaim.
     */
    STORAGE_CHAIN,
    /**
     * Carving finished, placing the crafting table used to compress diamonds.
     */
    PLACING_CRAFTING_TABLE,
    /**
     * Crafting diamond blocks on the placed crafting table.
     */
    CRAFTING,
    /**
     * Timed pause, then back to the remembered resume state.
     */
    WAITING,
    /**
     * Overworld only: walking out of the deep dark.
     */
    ESCAPING_DEEP_DARK
}
