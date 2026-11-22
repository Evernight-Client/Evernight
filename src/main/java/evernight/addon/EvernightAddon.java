package evernight.addon;

/**
 * Base class for an Evernight addon.
 *
 * <p>An addon is a normal Fabric client mod whose {@code fabric.mod.json} declares the
 * {@code evernight-addon} entrypoint:
 *
 * <pre>{@code
 * "entrypoints": {
 *   "evernight-addon": ["com.example.mymod.MyAddon"]
 * }
 * }</pre>
 *
 * <p>Evernight instantiates the entrypoint once during client initialisation and calls
 * {@link #onInitialize(AddonContext)} on the render thread. Register everything the addon provides
 * (modules, commands, configuration, language files) from that method through the supplied
 * {@link AddonContext}; calling {@code ClientContext} directly works as well, but the context keeps
 * ownership and cleanup bookkeeping in one place.
 *
 * <p>Addon classes are loaded from the addon's own jar, so they may use any public Evernight API.
 * Mixins declared by the addon belong to its own mixin configuration and are not managed by Evernight.
 */
public abstract class EvernightAddon {
    /**
     * Called exactly once, after Evernight's managers exist but before configuration files are loaded.
     * Throwing from this method marks the addon as failed and does not prevent other addons from
     * loading; registrations performed before the exception stay active.
     */
    public abstract void onInitialize(AddonContext context);

    /**
     * Called when Minecraft is shutting down, in reverse load order. Optional; use it to stop threads
     * or flush state that is not part of the regular configuration files.
     */
    public void onShutdown() {
    }
}
