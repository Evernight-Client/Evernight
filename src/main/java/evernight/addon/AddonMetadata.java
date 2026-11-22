package evernight.addon;

import java.util.List;

/**
 * Immutable metadata for one loaded addon, projected from the Fabric mod container that declared the
 * {@code evernight-addon} entrypoint.
 *
 * <p>{@link #id()} is the Fabric mod id (it must match the addon's {@code fabric.mod.json} and its
 * {@code assets/<id>/lang} namespace); {@link #displayName()} is only used for chat and UI text.
 */
public record AddonMetadata(
        String id,
        String name,
        String version,
        String description,
        List<String> authors,
        String website) {
    public AddonMetadata {
        authors = authors == null ? List.of() : List.copyOf(authors);
        website = website == null ? "" : website;
    }

    public String displayName() {
        return this.name == null || this.name.isBlank() ? this.id : this.name;
    }

    public String authorsText() {
        return String.join(", ", this.authors);
    }
}
