package evernight.client.settings;

import com.google.gson.JsonPrimitive;
import evernight.client.settings.core.Setting;
import evernight.client.settings.core.SettingLabelCondition;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.types.StringSetting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingTest {
    @Test
    @DisplayName("NumberSetting clamp 到 min/max，staged/value 同步")
    void numberClampsToBounds() {
        NumberSetting<Float> setting = new NumberSetting<>("Range", 1.0F, 0.0F, 2.0F);
        assertEquals(1.0F, setting.getValue());
        setting.setValue(99.0F);
        assertEquals(2.0F, setting.getValue());
        assertEquals(2.0F, setting.getStagedValue());
        setting.setValue(-99.0F);
        assertEquals(0.0F, setting.getValue());
        assertEquals(0.0F, setting.getStagedValue());
        assertEquals(0.0F, setting.getMinimumValue());
        assertEquals(2.0F, setting.getMaximumValue());
        assertTrue(setting.isAtMinimum());
        assertFalse(setting.isAtMaximum());
        setting.reset();
        assertEquals(1.0F, setting.getValue());
    }

    @Test
    @DisplayName("NumberSetting 的 Integer/Double 文本与 JSON 解析")
    void numberParsing() {
        NumberSetting<Integer> integer = new NumberSetting<>("Count", 5, 0, 10);
        integer.setValueFromText("7");
        assertEquals(7, integer.getValue());
        integer.fromJson(new JsonPrimitive(99));
        assertEquals(10, integer.getValue());
        assertEquals(10, integer.toJson().getAsInt());
        assertTrue(integer.matchesText("10"));
        assertFalse(integer.matchesText("9"));

        NumberSetting<Double> decimal = new NumberSetting<>("Scale", 0.5D, 0.0D, 1.0D);
        decimal.parseValue("0.75");
        assertEquals(0.75D, decimal.getValue());
        decimal.fromJson(new JsonPrimitive(2.0D));
        assertEquals(1.0D, decimal.getValue());
    }

    @Test
    @DisplayName("NumberSetting 的 unit/configName/边界标签")
    void numberMetadata() {
        NumberSetting<Float> setting = new NumberSetting<>("Damage", 1.0F, 0.0F, 2.0F)
                .withUnit("%");
        setting.setConfigName("CustomDamage");
        setting.setBoundaryLabel("Min", SettingLabelCondition.MIN);
        assertEquals("%", setting.getUnit());
        assertEquals("CustomDamage", setting.getConfigName());
        setting.setValue(0.0F);
        assertTrue(setting.shouldDisplayBoundaryLabel());
        setting.setValue(2.0F);
        assertFalse(setting.shouldDisplayBoundaryLabel());
    }

    @Test
    @DisplayName("BooleanSetting 文本解析与 JSON")
    void booleanParsing() {
        BooleanSetting setting = new BooleanSetting("Flag", false);
        setting.parseValue("toggle");
        assertTrue(setting.getValue());
        setting.parseValue("0");
        assertFalse(setting.getValue());
        setting.parseValue("true");
        assertTrue(setting.getValue());
        setting.parseValue("1");
        assertTrue(setting.getValue());
        setting.parseValue("not-a-boolean");
        assertTrue(setting.getValue());
        setting.fromJson(new JsonPrimitive(false));
        assertFalse(setting.getValue());
        assertFalse(setting.toJson().getAsBoolean());
    }

    @Test
    @DisplayName("StringSetting 文本与 JSON")
    void stringSetting() {
        StringSetting setting = new StringSetting("Name", "default");
        setting.parseValue("custom");
        assertEquals("custom", setting.getValue());
        setting.fromJson(new JsonPrimitive("json"));
        assertEquals("json", setting.getValue());
        assertEquals("json", setting.toJson().getAsString());
    }

    @Test
    @DisplayName("dependsOn 布尔依赖控制可见性")
    void dependsOnBoolean() {
        BooleanSetting dependency = new BooleanSetting("Dependency", false);
        BooleanSetting child = new BooleanSetting("Child", true);
        child.dependsOn(dependency);
        assertFalse(child.isVisible());
        dependency.setValue(true);
        assertTrue(child.isVisible());
        dependency.setValue(false);
        assertFalse(child.isVisible());
    }

    @Test
    @DisplayName("groupOnly 依赖需要 expanded + visible")
    void dependsOnGroup() {
        BooleanSetting group = new BooleanSetting("Group", false);
        group.markAsGroup();
        BooleanSetting child = new BooleanSetting("Child", true);
        child.dependsOn(group);
        assertFalse(group.isExpanded());
        assertFalse(child.isVisible());
        group.expanded = true;
        assertTrue(group.isExpanded());
        assertTrue(child.isVisible());
        group.addVisibilityPredicate(ignored -> false);
        assertFalse(group.isVisible());
        assertFalse(child.isVisible());
    }

    @Test
    @DisplayName("多个 visibility predicate 必须同时满足")
    void visibilityPredicatesCombine() {
        BooleanSetting setting = new BooleanSetting("Value", true);
        setting.addVisibilityPredicate(value -> value);
        setting.addVisibilityPredicate(value -> true);
        assertTrue(setting.isVisible());
        setting.addVisibilityPredicate(value -> false);
        assertFalse(setting.isVisible());
    }

    @Test
    @DisplayName("change callback 在有效变化时触发，reset 恢复默认")
    void changeCallbackAndDefaults() {
        BooleanSetting setting = new BooleanSetting("Flag", true);
        AtomicInteger changes = new AtomicInteger();
        setting.setChangeCallback(changes::incrementAndGet);
        setting.setValue(false);
        assertEquals(1, changes.get());
        setting.setValue(false);
        assertEquals(1, changes.get());
        setting.reset();
        assertTrue(setting.getValue());
        assertEquals(2, changes.get());
        setting.setDefaultValue(false);
        setting.reset();
        assertFalse(setting.getValue());
    }

    @Test
    @DisplayName("Setting equals 按类/名称/configName 判定")
    void equalityUsesClassAndConfigName() {
        BooleanSetting first = new BooleanSetting("Flag", true);
        BooleanSetting second = new BooleanSetting("Flag", false);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        second.setConfigName("Other");
        assertNotEquals(first, second);
        assertNotEquals(first, new StringSetting("Flag", "x"));
        Setting<Boolean> same = first;
        assertEquals(first, same);
    }
}
