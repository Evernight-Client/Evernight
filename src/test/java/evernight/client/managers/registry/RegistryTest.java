package evernight.client.managers.registry;

import evernight.client.NamedFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryTest {
    @Test
    @DisplayName("按插入顺序保存并对重名/null 拒绝注册")
    void registerKeepsOrderAndRejectsDuplicates() {
        Registry<NamedFeature> registry = new Registry<>();
        NamedFeature first = new NamedFeature("First");
        NamedFeature second = new NamedFeature("Second");
        assertTrue(registry.register(first));
        assertTrue(registry.register(second));
        assertFalse(registry.register(new NamedFeature("First")));
        assertFalse(registry.register(null));
        assertEquals(List.of(first, second), registry.getRegistry());
    }

    @Test
    @DisplayName("findFirst/findFirstOrNull 返回首个匹配")
    void findFirstReturnsFirstMatch() {
        Registry<NamedFeature> registry = new Registry<>();
        NamedFeature first = new NamedFeature("First");
        NamedFeature second = new NamedFeature("Second");
        registry.register(first);
        registry.register(second);

        assertSame(first, registry.findFirstOrNull(entry -> entry.getName().startsWith("F")));
        assertEquals(first, registry.findFirst(entry -> entry.getName().startsWith("F")).orElseThrow());
        assertTrue(registry.findFirst(entry -> entry.getName().equals("Missing")).isEmpty());
        assertNull(registry.findFirstOrNull(entry -> entry.getName().equals("Missing")));
    }

    @Test
    @DisplayName("注销后移出列表，可再次注册同名项")
    void unregisterAllowsReRegistration() {
        Registry<NamedFeature> registry = new Registry<>();
        NamedFeature first = new NamedFeature("First");
        registry.register(first);
        assertTrue(registry.unregister(first));
        assertFalse(registry.register(null));
        assertTrue(registry.getRegistry().isEmpty());

        NamedFeature replacement = new NamedFeature("First");
        assertTrue(registry.register(replacement));
        assertSame(replacement, registry.getRegistry().get(0));
    }
}
