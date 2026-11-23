package evernight.client.settings.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import evernight.client.settings.core.Setting;

import java.util.function.Supplier;

/**
 * A ClickGUI row that runs an action instead of storing a value.
 *
 * <p>Used for entry points to editors that live in their own screen (the trade manager, for
 * example), so the player no longer needs a chat command to reach them. The row is excluded from
 * config and presets: it has no state to persist.
 */
public final class ActionSetting extends Setting<Boolean> {
    private final Runnable action;
    private Supplier<String> infoSupplier;

    public ActionSetting(String name, Runnable action) {
        super(name, Boolean.FALSE);
        this.action = action;
        this.setExcludedFromConfig(true);
    }

    /** Optional right-aligned text rendered on the row, e.g. the number of entries. */
    public ActionSetting withInfo(Supplier<String> infoSupplier) {
        this.infoSupplier = infoSupplier;
        return this;
    }

    public String getInfoText() {
        return this.infoSupplier == null ? "" : this.infoSupplier.get();
    }

    public void runAction() {
        if (this.action != null) {
            this.action.run();
        }
    }

    @Override
    public void parseValue(String text) {
    }

    @Override
    public void fromJson(JsonElement json) {
    }

    @Override
    public JsonElement toJson() {
        return JsonNull.INSTANCE;
    }
}
