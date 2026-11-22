package evernight.addon;

/**
 * Load result for one addon, exposed to the {@code ;addons} command and any addon-management UI.
 */
public record AddonInfo(
        AddonMetadata metadata,
        Status status,
        String error,
        int moduleCount,
        int commandCount) {
    public enum Status {
        LOADED,
        FAILED
    }

    public boolean isLoaded() {
        return this.status == Status.LOADED;
    }
}
