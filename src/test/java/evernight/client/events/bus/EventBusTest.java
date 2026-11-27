package evernight.client.events.bus;

import evernight.client.events.core.Cancellable;
import evernight.client.events.core.EventHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBusTest {
    private final EventBus bus = new EventBus();

    static class PingEvent {
        final List<String> calls = new ArrayList<>();
    }

    static final class ChildPingEvent extends PingEvent {
    }

    static final class CancelEvent implements Cancellable {
        boolean cancelled;
        boolean observedCancelledAtEntry;

        @Override
        public boolean isCancelled() {
            return this.cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    static class PrioritySubscriber {
        @EventHandler(priority = 10)
        private void high(PingEvent event) {
            event.calls.add("high");
        }

        @EventHandler
        private void low(PingEvent event) {
            event.calls.add("low");
        }
    }

    static class SamePriorityFirst {
        @EventHandler
        private void on(PingEvent event) {
            event.calls.add("first");
        }
    }

    static class SamePrioritySecond {
        @EventHandler
        private void on(PingEvent event) {
            event.calls.add("second");
        }
    }

    static class StaticSubscriber {
        @EventHandler
        static void onStatic(PingEvent event) {
            event.calls.add("static");
        }

        @EventHandler
        void onInstance(PingEvent event) {
            event.calls.add("instance");
        }
    }

    static class InvalidHandlers {
        @EventHandler
        int notVoid(PingEvent event) {
            return 1;
        }

        @EventHandler
        void noArgs() {
        }

        @EventHandler
        void primitive(int event) {
        }

        @EventHandler
        void valid(PingEvent event) {
            event.calls.add("valid");
        }
    }

    static class ParentChildSubscriber {
        @EventHandler
        void onParent(PingEvent event) {
            event.calls.add("parent");
        }

        @EventHandler
        void onChild(ChildPingEvent event) {
            event.calls.add("child");
        }
    }

    static class CancellingSubscriber {
        @EventHandler(priority = 10)
        private void cancel(CancelEvent event) {
            event.observedCancelledAtEntry = event.isCancelled();
            event.cancel();
        }

        @EventHandler
        private void after(CancelEvent event) {
            event.observedCancelledAtEntry = true;
        }
    }

    static class SelfRemovingSubscriber {
        private final EventBus bus;

        SelfRemovingSubscriber(EventBus bus) {
            this.bus = bus;
        }

        @EventHandler
        private void on(PingEvent event) {
            this.bus.unsubscribe(this);
            event.calls.add("self");
        }
    }

    @Test
    @DisplayName("高优先级先执行，post 返回原事件")
    void priorityOrderAndReturnValue() {
        PrioritySubscriber subscriber = new PrioritySubscriber();
        this.bus.subscribe(subscriber);
        PingEvent event = new PingEvent();
        assertSame(event, this.bus.post(event));
        assertEquals(List.of("high", "low"), event.calls);
    }

    @Test
    @DisplayName("同优先级按 subscribeListener 的注册顺序执行")
    void samePriorityUsesRegistrationOrder() throws Exception {
        Method first = SamePriorityFirst.class.getDeclaredMethod("on", PingEvent.class);
        Method second = SamePrioritySecond.class.getDeclaredMethod("on", PingEvent.class);
        this.bus.subscribeListener(new MethodEventListener(SamePriorityFirst.class,
                new SamePriorityFirst(), first));
        this.bus.subscribeListener(new MethodEventListener(SamePrioritySecond.class,
                new SamePrioritySecond(), second));

        PingEvent event = new PingEvent();
        this.bus.post(event);
        assertEquals(List.of("first", "second"), event.calls);
    }

    @Test
    @DisplayName("实例订阅/反订阅与静态订阅隔离")
    void instanceAndStaticSubscription() {
        StaticSubscriber subscriber = new StaticSubscriber();
        this.bus.subscribeStatic(StaticSubscriber.class);
        PingEvent staticEvent = new PingEvent();
        this.bus.post(staticEvent);
        assertEquals(List.of("static"), staticEvent.calls);

        this.bus.unsubscribeStatic(StaticSubscriber.class);
        PingEvent afterStatic = new PingEvent();
        this.bus.post(afterStatic);
        assertTrue(afterStatic.calls.isEmpty());

        this.bus.subscribe(subscriber);
        PingEvent instanceEvent = new PingEvent();
        this.bus.post(instanceEvent);
        // 同优先级 handler 的先后顺序来自 getDeclaredMethods()，JVM 不保证；这里只校验两者都被触发。
        assertEquals(Set.of("static", "instance"), Set.copyOf(instanceEvent.calls));
        this.bus.unsubscribe(subscriber);
        PingEvent afterUnsubscribe = new PingEvent();
        this.bus.post(afterUnsubscribe);
        assertTrue(afterUnsubscribe.calls.isEmpty());
    }

    @Test
    @DisplayName("非法 handler 被忽略，合法 handler 仍执行")
    void invalidHandlersAreIgnored() {
        InvalidHandlers subscriber = new InvalidHandlers();
        this.bus.subscribe(subscriber);
        PingEvent event = new PingEvent();
        this.bus.post(event);
        assertEquals(List.of("valid"), event.calls);
    }

    @Test
    @DisplayName("子类事件只命中子类 handler，不命中父类 handler")
    void subclassEventUsesExactClass() {
        this.bus.subscribe(new ParentChildSubscriber());
        PingEvent parent = new PingEvent();
        this.bus.post(parent);
        assertEquals(List.of("parent"), parent.calls);

        ChildPingEvent child = new ChildPingEvent();
        this.bus.post(child);
        assertEquals(List.of("child"), child.calls);
    }

    @Test
    @DisplayName("postCancellable 重置取消状态，取消后停止后续 handler")
    void cancellableEventStopsAfterCancel() {
        this.bus.subscribe(new CancellingSubscriber());
        CancelEvent event = new CancelEvent();
        event.cancelled = true;
        assertSame(event, this.bus.postCancellable(event));
        assertFalse(event.observedCancelledAtEntry, "postCancellable must reset before dispatch");
        assertTrue(event.isCancelled());
    }

    @Test
    @DisplayName("分发中注销自己不会抛并发修改异常")
    void unsubscribeDuringDispatchIsSafe() {
        SelfRemovingSubscriber subscriber = new SelfRemovingSubscriber(this.bus);
        this.bus.subscribe(subscriber);
        PingEvent first = new PingEvent();
        this.bus.post(first);
        assertEquals(List.of("self"), first.calls);
        PingEvent second = new PingEvent();
        this.bus.post(second);
        assertTrue(second.calls.isEmpty());
    }
}
