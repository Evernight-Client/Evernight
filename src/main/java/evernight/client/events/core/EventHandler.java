package evernight.client.events.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
/**
 * Marks a method as an {@link EventBus} handler.
 *
 * <p>The method must take exactly one non-primitive event parameter and return {@code void}. It may be
 * static, in which case the listener has to be registered with
 * {@link evernight.client.events.bus.EventBus#subscribeStatic(Class)}. Handlers with a higher
 * {@link #priority()} are invoked first.
 */
public @interface EventHandler {
    int priority() default 0;
}