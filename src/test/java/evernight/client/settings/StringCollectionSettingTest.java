package evernight.client.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import evernight.client.settings.types.StringListSetting;
import evernight.client.settings.types.StringSetSetting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringCollectionSettingTest {
    @Test
    @DisplayName("StringListSetting 增删改移按索引生效，非法索引不修改")
    void listEditing() {
        StringListSetting setting = new StringListSetting("Messages", "a", "b", "c");
        assertEquals(List.of("a", "b", "c"), setting.getValue());

        setting.addEntry("d");
        assertEquals(List.of("a", "b", "c", "d"), setting.getValue());
        setting.setEntry(1, "B");
        assertEquals(List.of("a", "B", "c", "d"), setting.getValue());
        setting.removeEntry(0);
        assertEquals(List.of("B", "c", "d"), setting.getValue());
        setting.moveEntry(0, 2);
        assertEquals(List.of("c", "d", "B"), setting.getValue());

        setting.setEntry(-1, "x");
        setting.setEntry(99, "x");
        setting.removeEntry(-1);
        setting.removeEntry(99);
        setting.moveEntry(-1, 0);
        setting.moveEntry(0, 99);
        assertEquals(List.of("c", "d", "B"), setting.getValue());
    }

    @Test
    @DisplayName("StringListSetting null/空列表/替换/JSON 边界")
    void listNullAndJson() {
        StringListSetting setting = new StringListSetting("Messages", "a");
        setting.addEntry(null);
        setting.setEntry(0, null);
        assertEquals(List.of("a"), setting.getValue());

        setting.replaceEntries(List.of("x", "y"));
        assertEquals(List.of("x", "y"), setting.getValue());
        setting.replaceEntries(null);
        assertTrue(setting.getValue().isEmpty());

        JsonArray array = new JsonArray();
        array.add(new JsonPrimitive("one"));
        array.add(JsonNull.INSTANCE);
        array.add(new JsonPrimitive("two"));
        setting.fromJson(array);
        assertEquals(List.of("one", "two"), setting.getValue());

        setting.fromJson(new JsonPrimitive("not-an-array"));
        assertEquals(List.of("one", "two"), setting.getValue());

        assertEquals(List.of("one", "two"), setting.toJson().getAsJsonArray().asList().stream()
                .map(element -> element.getAsString()).toList());
    }

    @Test
    @DisplayName("StringListSetting parseValue 添加，reset 恢复默认")
    void listParseAndReset() {
        StringListSetting setting = new StringListSetting("Messages", "a");
        setting.parseValue("b");
        assertEquals(List.of("a", "b"), setting.getValue());
        setting.parseValue(null);
        assertEquals(List.of("a", "b"), setting.getValue());
        setting.reset();
        assertEquals(List.of("a"), setting.getValue());

        AtomicInteger resets = new AtomicInteger();
        setting.withResetAction(resets::incrementAndGet);
        setting.reset();
        assertEquals(1, resets.get());
        assertEquals(List.of("a"), setting.getValue());
    }

    @Test
    @DisplayName("StringListSetting change callback 只在真实编辑时触发")
    void listChangeCallback() {
        StringListSetting setting = new StringListSetting("Messages", "a");
        AtomicInteger changes = new AtomicInteger();
        setting.setChangeCallback(changes::incrementAndGet);
        setting.addEntry("b");
        setting.setEntry(0, "A");
        setting.removeEntry(1);
        setting.moveEntry(0, 0);
        setting.moveEntry(0, 5);
        setting.clearEntries();
        assertEquals(4, changes.get());
    }

    @Test
    @DisplayName("StringSetSetting 小写解析/格式化与切换")
    void setParsingAndToggle() {
        StringSetSetting setting = new StringSetSetting("Set", "a", "b");
        assertEquals(Set.of("a", "b"), setting.getValue());
        assertEquals("abc", setting.parseEntry("ABC"));
        assertEquals("abc", setting.formatEntry("ABC"));

        setting.parseValue("C");
        assertTrue(setting.containsParsedEntry("C"));
        setting.parseValue("c");
        assertFalse(setting.containsParsedEntry("c"));
        assertEquals(Set.of("a", "b"), setting.getValue());
    }

    @Test
    @DisplayName("StringSetSetting JSON/reset/parsed 操作")
    void setJsonAndParsedOperations() {
        StringSetSetting setting = new StringSetSetting("Set", "a");
        JsonArray array = new JsonArray();
        array.add(new JsonPrimitive("X"));
        array.add(new JsonPrimitive("y"));
        setting.fromJson(array);
        assertEquals(Set.of("X", "y"), setting.getValue());
        assertEquals(Set.of("x", "y"), setting.getFormattedValues());
        assertEquals(Set.of("x", "y"), setting.toJson().getAsJsonArray().asList().stream()
                .map(element -> element.getAsString()).collect(java.util.stream.Collectors.toSet()));

        setting.fromJson(new JsonPrimitive("not-an-array"));
        assertEquals(Set.of("X", "y"), setting.getValue());

        setting.addParsedEntry("Z");
        assertEquals("z", setting.getLastChangedEntry());
        assertTrue(setting.containsParsedEntry("Z"));
        setting.removeParsedEntry("Z");
        assertFalse(setting.containsParsedEntry("z"));

        setting.reset();
        assertEquals(Set.of("a"), setting.getValue());
        assertTrue(setting.getFormattedAvailableEntries().isEmpty());
    }
}
