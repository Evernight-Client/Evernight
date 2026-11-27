package evernight.client.modules.auto.AutoTransport;

/**
 * One step of the pearl transport loop.
 *
 * <p>The names mirror the movement plan: the source side (the end the player is leaving) loads the
 * backpack and stores boxes in the shared ender chest, the destination side unloads into the cargo
 * chest. Both sides share pearl detection, refilling and throwing, which is why those phases are not
 * side-specific.
 */
public enum AutoTransportPhase {
    /**
     * Idle at an end: waits until the cargo chest can be seen before starting the loop.
     */
    WAIT_RECALL,
    /**
     * Reads the pearl count in the backpack and picks refill or throw.
     */
    DETECT_PEARL,
    /**
     * Tops the pearl stack up to a full stack from the marked pearl chest.
     */
    REFILL_PEARL,
    /**
     * Sneaks to the rim of the nearest bubble column without stepping on it, aims down and throws.
     */
    THROW_PEARL,
    /**
     * Waits {@code ThrowDelay} milliseconds after the throw before touching containers.
     */
    THROW_SETTLE,
    /**
     * Source side: pulls shulker boxes out of the cargo chest into the backpack.
     */
    SOURCE_LOAD,
    /**
     * Source side: stores backpack shulker boxes in the pre-placed ender chest.
     */
    SOURCE_STORE,
    /**
     * Source side: refills the backpack from the cargo chest once more.
     */
    SOURCE_REFILL,
    /**
     * Destination side: unloads backpack shulker boxes into the cargo chest.
     */
    DEST_UNLOAD,
    /**
     * Destination side: takes shulker boxes out of the pre-placed ender chest.
     */
    DEST_TAKE,
    /**
     * Destination side: unloads the boxes taken from the ender chest.
     */
    DEST_UNLOAD2,
    /**
     * Sends the configured request command, expanding the random tag.
     */
    REQUEST,
    /**
     * Waits for the partner to trigger the pearl and pull the player over.
     */
    TRANSIT,
    /**
     * A required target or precondition is missing; retries the previous phase every second.
     */
    PAUSED
}
