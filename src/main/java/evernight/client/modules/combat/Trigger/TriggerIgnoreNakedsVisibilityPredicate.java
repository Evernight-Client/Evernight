package evernight.client.modules.combat.Trigger;

import java.util.function.Predicate;

public class TriggerIgnoreNakedsVisibilityPredicate implements Predicate<Object> {
    private final TriggerModule trigger;

    public TriggerIgnoreNakedsVisibilityPredicate(TriggerModule trigger) {
        this.trigger = trigger;
    }

    @Override
    public boolean test(Object ignoredValue) {
        if (!this.trigger.targetsSetting.isExpanded()) {
            return false;
        }
        return this.trigger.playersSetting.isExpanded();
    }
}