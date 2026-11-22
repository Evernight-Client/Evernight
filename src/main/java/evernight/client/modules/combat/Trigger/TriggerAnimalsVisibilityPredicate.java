package evernight.client.modules.combat.Trigger;

import java.util.function.Predicate;

public class TriggerAnimalsVisibilityPredicate implements Predicate<Object> {
    private final TriggerModule trigger;

    public TriggerAnimalsVisibilityPredicate(TriggerModule trigger) {
        this.trigger = trigger;
    }

    @Override
    public boolean test(Object ignoredValue) {
        return this.trigger.targetsSetting.isExpanded();
    }
}