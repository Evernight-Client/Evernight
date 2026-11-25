package evernight.client.settings.core;

import evernight.client.ClientContext;
import evernight.client.NamedFeature;
import evernight.client.config.json.EnumJsonConverter;
import evernight.client.render.text.Translations;
import net.fabricmc.loader.api.FabricLoader;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * A single configurable value: its current, staged and default value, optional numeric bounds, a
 * visibility predicate and JSON (de)serialisation.
 *
 * <p>Settings are registered on a {@link Module}; the ClickGUI renders them and
 * {@link evernight.client.managers.config.ConfigurationManager} persists them under
 * {@link #getConfigName()}. Numeric settings clamp through {@link #setValue(Object)} when bounds are
 * supplied; all other subclasses only store the value.
 */
public abstract class Setting<T> extends NamedFeature implements ValueProvider<T>, JsonConfigurable {
    public boolean expanded;
    public boolean visibilityDependency;
    public boolean opaqueColor;
    public boolean groupOnly;
    protected Runnable changeCallback;
    private T defaultValue;
    private Predicate<? super T> visibilityPredicate;
    private String configName;
    private String displayNameKey;
    private String boundaryLabel;
    private T value;
    private T stagedValue;
    private final T minimumValue;
    private final T maximumValue;
    private T previousValue;
    private boolean numericBoundsEnabled;
    private boolean excludedFromConfig;
    private SettingLabelCondition boundaryLabelCondition;

    public Setting(String name, T defaultValue) {
        this(name, defaultValue, null, null, null, false);
    }

    public Setting(String name, T defaultValue, Predicate<? super T> visibilityPredicate) {
        this(name, defaultValue, null, null, visibilityPredicate, false);
    }

    public Setting(String name, T defaultValue, T minimumValue, T maximumValue) {
        this(name, defaultValue, minimumValue, maximumValue, null, true);
    }

    public Setting(String name, T defaultValue, T minimumValue, T maximumValue, Predicate<? super T> visibilityPredicate) {
        this(name, defaultValue, minimumValue, maximumValue, visibilityPredicate, true);
    }

    /**
     * Single initialisation point for every constructor.
     *
     * @param numericBoundsEnabled whether {@link #setValue(Object)} should clamp against the supplied
     *                             minimum and maximum; only numeric settings enable it
     */
    private Setting(String name, T defaultValue, T minimumValue, T maximumValue, Predicate<? super T> visibilityPredicate,
                    boolean numericBoundsEnabled) {
        super(name);
        this.configName = this.getName();
        this.boundaryLabel = "";
        this.defaultValue = defaultValue;
        this.minimumValue = minimumValue;
        this.maximumValue = maximumValue;
        this.visibilityPredicate = visibilityPredicate;
        this.value = defaultValue;
        this.stagedValue = defaultValue;
        this.previousValue = defaultValue;
        this.numericBoundsEnabled = numericBoundsEnabled;
    }

    public boolean isAtMaximum() {
        Object maximum = this.getMaximumValue();
        return maximum.equals(this.getValue());
    }

    public Setting<T> markVisibilityDependency() {
        this.visibilityDependency = true;
        return this;
    }

    public String getBoundaryLabel() {
        return this.boundaryLabel;
    }

    public void reset() {
        this.setValue(this.defaultValue);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }
        if (!super.equals(other)) {
            return false;
        }
        Setting<?> otherSetting = (Setting<?>) other;
        return Objects.equals(this.configName, otherSetting.configName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.configName);
    }

    @Override
    public T getValue() {
        return this.value;
    }

    public void setValue(T newValue) {
        this.setStagedValue(newValue);
        if (this.numericBoundsEnabled) {
            Number minimum = (Number) this.minimumValue;
            float minimumAsFloat = minimum.floatValue();
            Number requestedMinimum = (Number) newValue;
            if (minimumAsFloat > requestedMinimum.floatValue()) {
                T lowerBound = this.minimumValue;
                this.setStagedValue(lowerBound);
            }
            Number maximum = (Number) this.maximumValue;
            float maximumAsFloat = maximum.floatValue();
            Number requestedMaximum = (Number) newValue;
            if (maximumAsFloat < requestedMaximum.floatValue()) {
                T upperBound = this.maximumValue;
                this.setStagedValue(upperBound);
            }
        }
        this.value = this.stagedValue;
        // Preserve reference-change notifications, including newly boxed numeric values.
        if (this.value != this.previousValue) {
            this.runChangeCallback();
        }
        this.previousValue = this.value;
    }

    public String getValueTypeName(Object candidate) {
        return candidate.getClass().getSimpleName();
    }

    public boolean hasStringValue() {
        return this.value instanceof String;
    }

    public T getPreviousValue() {
        return this.previousValue;
    }

    public T getStagedValue() {
        return this.stagedValue;
    }

    public void setStagedValue(T stagedValue) {
        this.stagedValue = stagedValue;
    }

    public boolean shouldDisplayBoundaryLabel() {
        if (this.hasNumericValue() && !this.boundaryLabel.isEmpty()) {
            return this.boundaryLabelCondition.matches(this);
        }
        return false;
    }

    @Override
    public String getConfigName() {
        return this.configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    /**
     * Overrides the label lookup with a full language key, used by addon modules so their settings
     * resolve {@code addons.<id>.names.settings.<Name>} instead of the client namespace. Like every
     * display lookup, a missing key falls back to English and finally to the full key.
     */
    public Setting<T> setDisplayNameKey(String displayNameKey) {
        this.displayNameKey = displayNameKey;
        return this;
    }

    /**
     * Translated label used by the ClickGUI. Config files keep using {@link #getConfigName()}.
     */
    public String getDisplayName() {
        if (this.displayNameKey != null) {
            return Translations.get(this.displayNameKey);
        }
        return Translations.setting(this.getName());
    }

    public T getMaximumValue() {
        return this.maximumValue;
    }

    public boolean isAtMinimum() {
        Object minimum = this.getMinimumValue();
        return minimum.equals(this.getValue());
    }

    public T getDefaultValue() {
        return this.defaultValue;
    }

    public void setDefaultValue(T defaultValue) {
        this.defaultValue = defaultValue;
    }

    @SafeVarargs
    public final Setting<T> dependsOn(Setting<?>... dependencies) {
        Predicate<T> dependencyVisibility = ignoredValue -> {
            for (Setting<?> dependency : dependencies) {
                if (dependency.groupOnly || dependency.visibilityDependency) {
                    if (!dependency.isExpanded()) {
                        return false;
                    }
                    if (!dependency.isVisible()) {
                        return false;
                    }
                } else {
                    Boolean dependencyValue = (Boolean) dependency.getValue();
                    if (!dependencyValue) {
                        return false;
                    }
                }
            }
            return true;
        };
        this.addVisibilityPredicate(dependencyVisibility);
        return this;
    }

    public final Setting<T> setBoundaryLabel(String label, SettingLabelCondition condition) {
        this.boundaryLabel = label;
        this.boundaryLabelCondition = condition;
        return this;
    }

    public void hideWithoutBaritone() {
        Predicate<T> baritoneAvailable = ignoredValue -> ClientContext.baritoneBridge.isAvailable();
        this.addVisibilityPredicate(baritoneAvailable);
    }

    /**
     * Hides this setting when none of the given optional mod ids is loaded, for settings that only
     * affect a feature of that mod.
     */
    public void hideUnlessModLoaded(String... modIds) {
        Predicate<T> modAvailable = ignoredValue -> {
            FabricLoader loader = FabricLoader.getInstance();
            for (String modId : modIds) {
                if (loader.isModLoaded(modId)) {
                    return true;
                }
            }
            return false;
        };
        this.addVisibilityPredicate(modAvailable);
    }

    public Setting<T> forceOpaqueColor() {
        this.opaqueColor = true;
        return this;
    }

    public void runChangeCallback() {
        if (this.changeCallback != null) {
            try {
                this.changeCallback.run();
            } catch (Throwable ignoredFailure) {
            }
        }
    }

    public boolean isGroupOnly() {
        return this.groupOnly;
    }

    public boolean isExpanded() {
        return this.expanded && this.visibilityDependency;
    }

    public boolean isVisible() {
        if (this.visibilityPredicate == null) {
            return true;
        }
        Predicate<? super T> predicate = this.visibilityPredicate;
        return predicate.test(this.getValue());
    }

    public boolean isNumericBoundsEnabled() {
        return this.numericBoundsEnabled;
    }

    public void disableNumericBounds() {
        this.numericBoundsEnabled = false;
    }

    public boolean isExcludedFromConfig() {
        return this.excludedFromConfig;
    }

    public void setExcludedFromConfig(boolean excluded) {
        this.excludedFromConfig = excluded;
    }

    public boolean hasNumericValue() {
        return this.value instanceof Number;
    }

    public void setChangeCallback(Runnable callback) {
        this.changeCallback = callback;
    }

    public String getStagedEnumDisplayName() {
        Enum stagedChoice = (Enum) this.stagedValue;
        return EnumJsonConverter.getName(stagedChoice);
    }

    public boolean hasEnumValue() {
        return this.value instanceof Enum;
    }

    public abstract void parseValue(String serializedValue);

    public void addVisibilityPredicate(Predicate<? super T> predicate) {
        if (this.visibilityPredicate == null) {
            this.visibilityPredicate = predicate;
        } else {
            Predicate<? super T> previous = this.visibilityPredicate;
            Objects.requireNonNull(predicate);
            this.visibilityPredicate = value -> previous.test(value) && predicate.test(value);
        }
    }

    public Setting<T> markAsGroup() {
        this.groupOnly = true;
        this.markVisibilityDependency();
        return this;
    }

    public T getMinimumValue() {
        return this.minimumValue;
    }
}