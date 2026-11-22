package evernight.client.events.bus;

import evernight.client.events.core.Cancellable;

public interface EventDispatcher {
    void subscribe(Object subscriber);

    void unsubscribeStatic(Class<?> listenerClass);

    void unsubscribe(Object subscriber);

    void subscribeListener(EventListener listener);

    Cancellable postCancellable(Cancellable event);

    void unsubscribeListener(EventListener listener);

    Object post(Object event);

    void subscribeStatic(Class<?> listenerClass);
}