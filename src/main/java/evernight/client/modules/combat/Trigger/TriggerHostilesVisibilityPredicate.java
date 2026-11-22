package evernight.client.modules.combat.Trigger;

import java.util.function.Predicate;

public class TriggerHostilesVisibilityPredicate implements Predicate<Object> {
    private final TriggerModule trigger;

    public TriggerHostilesVisibilityPredicate(TriggerModule trigger) {
        this.trigger = trigger;
    }

    @Override
    public boolean test(Object ignoredValue) {
        return this.trigger.targetsSetting.isExpanded();
    }
}