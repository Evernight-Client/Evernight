# Evernight 模块与设置框架 —— 新增模块 / 新增设置的完整约定

适用范围：给 Evernight 新增/修改模块、设置、HUD 组件、注册表项，或要弄清「模块何时订阅事件、设置怎样落盘、预设 payload 长什么样」时读本文。本文只写仓库中真实存在的 public/protected API；路径相对仓库根，包根为 `evernight`，客户端代码在 `client.` 下，行号以当前 checkout 为准。

## 目录

- [1. 文件地图](#1-文件地图)
- [2. Module 基类](#2-module-基类)
- [3. ModuleCategory 与派生基类](#3-modulecategory-与派生基类)
- [4. settings/ 设置体系](#4-settings-设置体系)
- [5. settings/options 枚举约定](#5-settingsoptions-枚举约定)
- [6. 注册表与初始化顺序](#6-注册表与初始化顺序)
- [7. 事件总线与 @EventHandler](#7-事件总线与-eventhandler)
- [8. 配置与预设](#8-配置与预设)
- [9. 从零新增一个模块（完整清单）](#9-从零新增一个模块完整清单)
- [常见任务速查](#常见任务速查)

## 1. 文件地图

| 关注点 | 源码路径 |
| --- | --- |
| 模块基类 / 分类 / 键位 / 派生基类 | `src/main/java/evernight/client/modules/`（`Module.java`、`ModuleCategory.java`、`Keybind.java`、`PermanentModule.java`、`HudWidgetModule.java`、`PlacementModule.java`） |
| 设置基类、具体类型与枚举 | `src/main/java/evernight/client/settings/`、`settings/options/` |
| 注册表与初始化 | `src/main/java/evernight/client/managers/`（`ModuleRegistry.java`、`HudModuleRegistry.java`、`Registry.java`、`AbstractRegistry.java`、`ConfigurableRegistry.java`）、`client/bootstrap/ClientInitializer.java`、`ModuleDependencies.java` |
| 事件定义 / 总线 | `src/main/java/evernight/client/events/`、`client/events/bus/` |
| 配置与预设 | `src/main/java/evernight/client/managers/config/ConfigurationManager.java`、`PresetRegistry.java`、`client/config/preset/PresetData.java`、`ModulePresetCodec.java`、`ClientDataDirectory.java` |
| 语言表 | `src/main/resources/assets/evernight/lang/en_us.json`、`zh_cn.json` |

## 2. Module 基类

源码：`src/main/java/evernight/client/modules/Module.java`（构造器 :47/:59，add :63/:68，enable :80，disable :229，fromJson :158，toJson :200，getConfigName :272，getSettings :280）。

### 2.1 构造器与身份

```java
public Module(String name, String description, ModuleCategory category, String... aliases) // :47
public Module(String name, ModuleCategory category, String... aliases)                    // :59
```

- `name` 就是 `getName()`，**稳定英文标识**，命令、别名、配置键、预设都依赖它，永不翻译。构造器会把 `name` 自己追加进 `aliases`，所以 `getAliases()` 至少含 1 个元素；不需要额外别名时传 `new String[0]`。
- `getConfigName()` 默认返回 `getName()`（:272），配置/预设用它做键。
- `getDisplayName()` = `Translations.module(getName())`（:153）；`getDescription()` 已被覆写为 `Translations.moduleDescription(getName(), super.getDescription())`（:145），即 Java 里的 description 是 fallback 文案。
- `getInfo()` 默认返回 `null`（:102）；子类可返回动态短文本，`getInfoString()`（:242）会拼成 `别名 [info]` 供 ModuleList HUD 显示。`getCategory()`（:224）返回构造时传入的分类。

### 2.2 设置与键位

```java
public <S extends Setting<?>> S add(S setting)                              // :63
public <S extends Setting<?>> S add(S setting, Setting<?> precedingSetting) // :68
public List<Setting<?>> getSettings()                                       // :280
```

`add` 返回传入的设置本身，因此可以 `this.rangeSetting = this.add(new NumberSetting<>(...))`；`register/unregister` 用于动态增删设置（`RegistryContainer` 契约），`getRegistry()`（:285）返回同一列表。GUI 按 `getSettings()` 顺序渲染。

`Keybind` 是 record（`client/modules/Keybind.java`）：

```java
public record Keybind(int code, Mode mode, boolean mouse) {                       // :6
    public static final Keybind UNBOUND = new Keybind(-1, Mode.TOGGLE, false);    // :7
    public boolean isUnbound() { return this.code < 0; }                          // :9
    public static Keybind toggle(int code, boolean mouse) { ... }                 // :13
}
```

`Mode` 枚举为 `TOGGLE("Toggle","toggle")`、`HOLD("Hold","hold")`、`HOLD_REVERSE("ReverseHold","hold_reverse")`（:33-36）；序列化用 `getSerializedName()`，显示用 `getDisplayName()`。`setKeybind/getKeybind`（:125/:133）读写字段，`modifyKeybind(Function)`（:258）供配置合并与外部键位托管覆写；`withMode/withCode/withMouse`（Keybind :17/:21/:29）返回新 record（record 不可变，配置合并时用来派生副本）。

### 2.3 启停生命周期（事件订阅发生在 enable）

```java
this.enabled = true;
ClientAccess.eventBus.subscribe(this);          // 启用时才订阅
EventBus eventBus = ClientAccess.eventBus;
ModuleToggleEvent toggleEvent = new ModuleToggleEvent(this);
eventBus.post(toggleEvent);
this.onToggle();
this.onEnable();
```

`disable()`（:229）对称：先 `unsubscribe(this)`，再置 `enabled = false`、post `ModuleToggleEvent`、`onToggle()`、`onDisable()`。`isToggled()` 返回 `enabled`；`toggle()/setToggled(boolean)` 来自 `client/utils/core/Toggleable.java` 的 default 方法。因此**写在模块里的 `@EventHandler` 只在模块启用期间才会被调用**，处理器里通常不必再判断 `isToggled()`。`onEnable/onDisable/onToggle` 是空实现的钩子（:111/:137/:114）。

### 2.4 序列化与两个覆盖开关

`toJson()` 写出：`enabled`、`key`（int）、`state`（`Keybind.Mode#getSerializedName`）、`mouse`、`drawn`、`settings`（只写非 `isGroupOnly()` 且非 `isExcludedFromConfig()` 的设置，键为 `setting.getConfigName()`）。`fromJson()` 对称读取，逐项容错，缺键忽略。

- `protected boolean usesExternalKeybind()`（:267）：返回 `true` 时 `fromJson` **不会**用配置覆盖 key/mouse（用于把键位托管给原版 `KeyMapping` 的模块）；`protected void ignoreState()`（:276）：此后 `enabled` 不写也不读（用于「状态由外部驱动」的常驻模块）。

真实用法（`client/modules/client/UI/UIModule.java`）：`getKeybind()` 从 `ClientKeybinds` 投影（:110），`modifyKeybind` 写回（:115），`usesExternalKeybind()` 返回 `true`（:121）；构造器末尾 `this.ignoreState(); this.setDrawn(false);`（:257-258）。`setDrawn/isDrawn`（:140/:121）控制该模块在列表/通知中的展示（`;drawn` 命令可切换），`setWip`（:129，protected）/`isWip`（:117）只标记开发中。

## 3. ModuleCategory 与派生基类

源码：`src/main/java/evernight/client/modules/ModuleCategory.java`、`PermanentModule.java`、`HudWidgetModule.java`、`src/main/java/evernight/client/render/hud/HudWidgetRenderer.java`、`ResizableHudWidgetRenderer.java`、`client/modules/hud/HudTextListRenderer.java`。

### 3.1 内置分类与插件分类

`ModuleCategory` 已从枚举改为 final class，实现 `NamedEntry`。对象只保存稳定英文名与语言键：`getName()` 返回稳定名，`getDisplayName()` 每次调用都走 `Translations.get(键)`，查找链为当前语言 → 英文 → 完整键，不在 Java 里自造英文名（内置键为 `evernight.names.categories.<Name>`）。

| 内置常量 | `getName()` | JSON 键（`evernight.names.categories.*`） |
| --- | --- | --- |
| `COMBAT` | `"Combat"` | `Combat` |
| `MISC` | `"Misc"` | `Misc` |
| `RENDER` | `"Render"` | `Render` |
| `MOVEMENT` | `"Movement"` | `Movement` |
| `PLAYER` | `"Player"` | `Player` |
| `AUTO` | `"Auto"` | `Auto` |
| `EXPLOIT` | `"Exploit"` | `Exploit` |
| `CLIENT` | `"Client"` | `Client` |
| `HUD` | `"HUD"` | `HUD` |

`values()` 返回不可变列表：内置分类按上表顺序在前，插件分类按注册顺序追加；`ordinal()` 就是该顺序（内置固定 0-8），`findByName(String)` 大小写不敏感。ClickGUI 遍历 `values()` 建面板（`ClickGuiScreen`），`ClientScreen.resetPanelPositions()` 用 `ordinal()` 排序；分类不写配置/预设，模块按对象身份（`==`）比较。

插件不能直接 `new` 分类，也不能改内置列表；只能在 `onInitialize` 里用 `AddonContext#registerCategory(String)` 注册，显示键为 `addons.<id>.names.categories.<Name>`，两份语言文件都要写；中文缺键回退英文，两份都缺显示完整键。普通模块开发继续直接使用 `ModuleCategory.MISC` 等常量；内置分类共 9 个（`COMBAT` 到 `HUD`，其中 `AUTO` 位于 `PLAYER` 与 `EXPLOIT` 之间）。详见 `.github/skills/evernight-addon-dev/SKILL.md`。

### 3.2 PermanentModule

`public abstract class PermanentModule extends Module`（`PermanentModule.java:6`）：`isToggled()` 恒为 `true`、`isDrawn()` 恒为 `false`、`getKeybind()` 恒为 `Keybind.UNBOUND`、`disable()` 空实现、`toJson()` 去掉 `enabled/key/state`。现有子类：`SettingsModule`、`ColorsModule`、`BaritoneModule`、`AntiCheatModule`。

**关键差异**：因为 `isToggled()` 恒真，`Module.enable()` 会在第一行提前 return，**PermanentModule 永远不会被 enable() 订阅**。所以这类模块要在构造器里手动订阅，真实写法见 `client/modules/client/AntiCheat/AntiCheatModule.java:370-371`：

```java
EventBus eventBus = ClientAccess.eventBus;
eventBus.subscribe(this);
```

`BaritoneModule.java:123-124` 相同。它们不再反订阅，生命周期等于进程。

### 3.3 HudWidgetModule（HUD 组件契约）

`public abstract class HudWidgetModule extends Module`（:23）：构造器只有 `public HudWidgetModule(String name, String... aliases)`（:28），分类固定为 `HUD`，`isDrawn()` 恒 `false`。它自己覆写了 `fromJson/toJson`（:52/:72），在 `settings` 之外额外持久化 `hud` 对象：`anchor`、`index`；当 anchor 为 `NONE` 时还存 `h`/`v`（水平/垂直对齐 ordinal）与相对坐标 `x`/`y`。

- `protected void setRenderer(HudWidgetRenderer renderer)`（:116）：**必须在构造器里调用**，否则 `renderer` 为 null；`getRenderer()`（:127）、`getAnchor()`（:131）、`setAnchor(HudAnchor)`（:108，会重挂到 `ClientContext.getHudEditor()` 的锚点分组）。
- 尺寸由渲染器缓存：`HudWidgetRenderer.getDimensions()`/`setDimensions(float[])`（`HudWidgetRenderer.java:62/:160`），子类渲染器在 `calculateDimensions()` 里算好交回。
- 最常用的两种现成渲染器：文本型 `HudTextListRenderer` + `HudTextRow`（行内容用 `Supplier<Component>` 每帧生成，自己算尺寸，参考 `PositionModule.java:136-218`）；自绘型 `ResizableHudWidgetRenderer`（模块覆写 `calculateDimensions()` 与 `renderContent(PoseStack)`，参考 `GraphModule.java:39-65`）。
- `getHudColor(float position)`（:120）用 `HUDModule#fadeSetting` 计算 HUD 主题渐变色；`isInHudEditor()`（:135）判断是否正在 HUD 编辑器里；HUD 模块的 `@EventHandler` 同样只在模块启用时触发。

文本型 HUD 模块的完整构造（摘自 `WatermarkModule.java:57-64`）：

```java
public WatermarkModule() {
    super("Watermark", new String[0]);
    this.versionColorSetting = this.add(new BooleanSetting("VersionColor", true));
    this.textSetting = this.add(new StringSetting("Text", "Evernight"));
    HudTextRow textRow = new HudTextRow(this::buildWatermarkText, () -> true);
    this.setRenderer(new HudTextListRenderer(this, textRow));
    this.getRenderer().setAnchor(HudAnchor.TOP_LEFT);
    this.setToggled(true);
}
```

自绘型最小实现（`calculateDimensions` 摘自 `GraphModule.java:39-47`）：

```java
@Override
public float[] calculateDimensions() {
    float[] dimensions = new float[2];
    Integer width = this.widthSetting.getValue();
    dimensions[0] = width.intValue();
    Integer height = this.heightSetting.getValue();
    dimensions[1] = height.intValue();
    return dimensions;
}

@Override
public void renderContent(PoseStack poses) { /* 在 widget 坐标系里画 */ }
// 构造器末尾：this.setRenderer(new ResizableHudWidgetRenderer(this));
```

HUD 模块的注册点在 `HudModuleRegistry` 构造器（不是 `ModuleRegistry`），配置落在 `hud.json`。另有 combat 专用的 `PlacementModule`（`PlacementModule.java:85`），普通模块不要继承。

## 4. settings/ 设置体系

源码：`src/main/java/evernight/client/settings/`。全部设置都是 `Setting<T>` 子类，由模块 `add(...)` 持有。

### 4.1 Setting<T> 基类

`public abstract class Setting<T> extends NamedFeature implements ValueProvider<T>, JsonConfigurable`（:22）。常用构造器（子类同名转发）：

```java
Setting(String name, T defaultValue)                                      // :55
Setting(String name, T defaultValue, Predicate<? super T> visibility)     // :59
Setting(String name, T defaultValue, T minimumValue, T maximumValue)      // :63（数值钳制）
Setting(String name, T defaultValue, T min, T max, Predicate<? super T> visibility) // :67
```

取值：`getValue()`（:117）、`getStagedValue()`（:133）、`getDefaultValue()`（:165）、`getMinimumValue/getMaximumValue()`（:319/:156）、`setValue(T)`（:275，数值类型按 min/max 钳制；引用变化时触发 changeCallback）、`setDefaultValue(T)`（:323）、`reset()`（:92）。

标识：`getName()` 来自 `NamedFeature`（设置名），`getConfigName()`（:145）默认等于 name，`setConfigName(String)`（:238）改持久化键；显示 `getDisplayName()` = `Translations.setting(getName())`（:152）——**标签看 name，配置与 tooltip 看 configName**。

### 4.2 链式修饰（全部真实存在）

| 方法 | 签名 / 位置 | 语义 |
| --- | --- | --- |
| `dependsOn` | `final Setting<T> dependsOn(Setting<?>... dependencies)` :170 | 依赖项为普通设置时按 Boolean 取值，为 false 则隐藏；依赖项是 `groupOnly`/`visibilityDependency` 时要求 `isExpanded() && isVisible()`。所以非 Boolean 设置只能作为 group 依赖。 |
| `markAsGroup` | `Setting<T> markAsGroup()` :313 | 设为折叠组标题：`groupOnly = true` 并同时 `markVisibilityDependency()`。 |
| `markVisibilityDependency` | :46；配套 `addVisibilityPredicate(Predicate<? super T>)` :303 | 标记「可见性依赖提供者」，GUI 才会展开/收起其子项；组内子设置用 `dependsOn(父)`，多个谓词取与。 |
| `setConfigName` | `void` :238 | 改配置键（发布后不可再改，否则老配置失效）。 |
| `setChangeCallback` | `void setChangeCallback(Runnable)` :258 | 值引用变化时执行；回调异常被吞。 |
| `hideWithoutBaritone` | `void` :199 | 追加可见性谓词：Baritone 不可用时隐藏。 |
| `setBoundaryLabel` | `final Setting<T> setBoundaryLabel(String, SettingLabelCondition)` :193 | 数值取到 MIN/MAX 时显示标签（如 `"None"`/`"Vanilla"`），仅对 `hasNumericValue()` 生效。 |
| `forceOpaqueColor` | `Setting<T> forceOpaqueColor()` :208 | 颜色设置强制 alpha=255。 |
| `setExcludedFromConfig` | `void setExcludedFromConfig(boolean)` :204 | 不参与 `Module#toJson/fromJson` 与预设序列化。 |
| `isVisible / isExpanded / isGroupOnly / isExcludedFromConfig` | :230/:226/:222/:250 | GUI 与序列化判定。 |

组与依赖的真实写法（`client/modules/client/UI/UIModule.java:186-193`）：

```java
BooleanSetting elementsOption = new BooleanSetting("Elements", true);
elementsOption.markAsGroup();
elementsOption.markVisibilityDependency();
this.elementsSetting = this.add(elementsOption);
this.aeroSetting = this.add(new BooleanSetting("Aero", true)
        .dependsOn(this.elementsSetting));
```

带单位与多依赖的例子（`client/modules/combat/AnchorAura/AnchorAuraModule.java:659`）：

```java
this.fadeTimeSetting = this.add(new NumberSetting<>("FadeTime", 1.0F, 0.1F, 1.0F)
        .withUnit("s").dependsOn(this.renderSetting, this.fadeSetting));
```

### 4.3 全部设置类型

| 类 | 构造器 | `getValue()` 类型 | 备注 |
| --- | --- | --- | --- |
| `BooleanSetting` | `(String, Boolean)` :13；`(String, Boolean, Predicate)` :9 | `Boolean` | 写布尔；`parseValue` 接受 toggle/true/false/1/0。 |
| `NumberSetting<N extends Number>` | `(String, N def, N min, N max)` :25；带 Predicate :21 | `N` | 写数字；`withUnit(String)` :89、`setNumber(Number)` :56、`Setting#disableNumericBounds()` :246；类型由 default 决定（Integer/Float/Double）。 |
| `EnumSetting<E extends Enum<E> & NamedEntry>` | `(String, E def)` :20；带 Predicate :12 | `E` | 写 `getName()`；未知值读回落第一个常量。 |
| `StringSetting` | `(String, String)` :13；带 Predicate :9 | `String` | 原样读写。 |
| `ColorSetting` | `(String, java.awt.Color)` :25；带 Predicate :21 | `Color` | `getValue()` 返回已解析颜色（主题同步/彩虹）；`getColor()` :110；JSON 存 `{rgba, rainbow, sync}`。 |
| `IdentifierSetting` | `(String, NamedIdentifier)` :11；带 Predicate :15 | `NamedIdentifier` | 存 `{name, category}`；`parseValue` 接受 `name` 或 `category:name`；常用于音效 id。 |
| `SetSetting<E>`（abstract） | `@SafeVarargs (String, E... defaults)` :27；带 Predicate :33 | `Set<E>` | 子类必须实现 `parseEntry(String)`、`formatEntry(E)`、`getAvailableEntries()`；`addEntry/removeEntry/containsEntry` :115/:55/:77。 |
| `StringSetSetting` | `(String, String... defaults)` :14；带 Predicate :18 | `Set<String>` | `parseEntry/formatEntry` 统一转小写；`getAvailableEntries` 为空，靠用户输入。 |
| `StringListSetting` | `(String, String... defaults)` :23 | `List<String>` | 有序、保留原文本；`addEntry/setEntry/removeEntry/moveEntry/replaceEntries/clearEntries` 改完会触发 changeCallback；ClickGUI 行打开 `StringListScreen`，`withPrepareAction(Runnable)` 可在打开前重新加载文件；需要「文件是唯一数据源」时配合 `setExcludedFromConfig(true)`，并用 `withResetAction(Runnable)` 避免重置设置时清空文件。 |
| `ActionSetting` | `(String, Runnable)` :20 | `Boolean` | 点击执行动作的界面行（如打开交易管理器），不保存值、自动 `setExcludedFromConfig(true)`；`withInfo(Supplier<String>)` 在右侧显示动态文本。 |
| `RegistrySetSetting<E>` | `(String, Registry<E>, E... defaults)` :20；带 Predicate :26 | `Set<E>` | 序列化为注册表 id 字符串；`getRegistry()` :74、`modifyEntries(Consumer<Set<E>>)` :59；真实用法 `new RegistrySetSetting<>("Blocks", BuiltInRegistries.BLOCK, Blocks.OBSIDIAN, Blocks.ENDER_CHEST)`。 |
| `CyclingTextSetting`（接口） | — | — | 只由 `NumberSetting`、`EnumSetting` 实现：提供 `matchesText/setValueFromText`，把多段引号参数按当前值循环（用于 `;module <name> <setting> <value>` 命令，`ModuleCommand.java:210` 调 `parseValue`）。 |

`SetSetting` 抽象三件套可参考 `StringSetSetting`（`parseEntry` 转小写、`getAvailableEntries` 返回空表、`formatEntry` 转小写）与 `RegistrySetSetting`（用 `Identifier.parse` 查注册表）。新增自定义集合设置时照抄这两个之一，不要直接 `new SetSetting`。

### 4.4 四个小接口

- `NamedEntry`（:7）：`String getName()`；default `getDisplayName()` = `Translations.value(getName())`（:18）；模块/设置/枚举都靠它同时实现「标识 + 显示」。
- `ValueProvider<T>`（:3）：只有 `T getValue()`；`JsonConfigurable`（:5）：`void fromJson(JsonElement)` + `JsonElement toJson()` + default `getConfigName()` 返回 `""`（注册表实现它并覆写成文件名，如 `modules.json`）。
- `SettingLabelCondition`（:5）：枚举 `MIN`/`MAX`，配合 `setBoundaryLabel` 判断当前值是否触边。

## 5. settings/options 枚举约定

源码：`src/main/java/evernight/client/settings/options/`。新增选项枚举的规则：

1. `implements NamedEntry`，字段存英文 displayName，`getName()` 返回它（**不要返回中文，也不要用 `name()` 当序列化名**）。
2. 显示标签由 default `getDisplayName()` 查 `evernight.names.values.<getName()>`；两份 JSON 的 `evernight.names.values.*` 都要加键。
3. 序列化走 `EnumJsonConverter`（`client/config/json/EnumJsonConverter.java`）：按 `getName()` 大小写不敏感匹配，找不到回退到枚举第一个常量；所以 `getName()` 发布后不可改。枚举作为设置时，标签键仍是 `evernight.names.settings.<SettingName>`（设置名，不是枚举名），tooltip 用设置 configName。

代表性枚举（4 个）：

| 枚举 | 形状 | 要点 |
| --- | --- | --- |
| `ESPModeChoice`（:5） | `PLAIN("Plain")/TEXT("Text")/BOTH("Both")` | 最简形式；JSON 只需 `evernight.names.values.Plain` 等。 |
| `HudAnchor`（:13） | 带附加数据：`name` + `HudVerticalAlignment` + `HudHorizontalAlignment` + `BiFunction<Integer,Integer,FloatBounds> boundsFactory` | `getName()` 返回小写 id（`top_left`…`none`），因此 JSON 键是 `evernight.names.values.top_left`；`NONE` 表示 HUD 组件自由定位（`HudWidgetModule.toJson` 才写 `h/v/x/y`）；`getBounds()/contains()` 供 HUD 编辑器命中测试。 |
| `PresetCategory`（:35） | 同时实现 `JsonConfigurable`，每个常量覆写 `fromJson/toJson` | 预设分类的「文件名 + payload 形态」都由它决定：`getDirectory()`（:327）指向 `presets/<小写名>`，`writeMetadata`（:348）写 `client/category`，`findByName`（:356）供命令解析；新增分类必须补 `evernight.names.values.<getName()>`，并确认 `ConfigurationManager.load()` 遍历 `PresetCategory.values()` 创建目录。 |
| `ListFilterMode`（:9） | 普通枚举 + 静态 `matches(...)` 与 `isAny()` 行为方法 | 枚举上可以挂纯函数逻辑；行为是**调用方**显式调用，不会自动生效。 |

## 6. 注册表与初始化顺序

源码：`src/main/java/evernight/client/managers/` 与 `client/bootstrap/`。

### 6.1 注册表契约

- `RegistryContainer<T>`（`client/utils/inventory/RegistryContainer.java`）：`register/unregister/getRegistry`；`AbstractRegistry<T>`（:16）：持有 `protected final List<T> entries`，`getRegistry()` 返回它（:24），`findFirst/findFirstOrNull(Predicate)`（:28/:37）。
- `Registry<T extends NamedEntry>`（:12）：`registerEntry`（:27）按 `getName()` 去重，重名返回 `false` 且不入表；`unregisterEntry`（:41）。
- `ModuleRegistry`（:30）：所有模块的唯一存储表（HUD 模块也由 `HudModuleRegistry` 追加进来）。构造器里逐个 `registerModule(new XxxModule())`，失败即 `throw new IllegalStateException("Registration rejected XxxModule")`（:56-57 模式）；最后 `entries.sort(Comparator.comparing(Module::getName))`（:415）。`registerModule`（:533）会先拒绝被隐藏的实验类并清理隐藏设置；`getModule(Class)`（:546，标了 `@Deprecated` 但仍在用）按类型索引实例；`getConfigName()` 返回 `"modules.json"`（:475）。
- `HudModuleRegistry`（:42）：构造器里把 HUD 模块 `ClientContext.moduleRegistry.registerModule(new XxxModule())` 注册进同一个 `ModuleRegistry`（:49-69）；`getConfigName()` 返回 `"hud.json"`（:131）；收到 `ClientInitializationEvent` 时对所有 `HudWidgetModule` 重新 `setAnchor(getAnchor())` 以重建锚点分组（:136-144）。
- `ConfigurableRegistry`（:10）：被 `ConfigurationManager` 保存/加载的 `JsonConfigurable` 列表；`ModuleRegistry`、`HudModuleRegistry` 已在内，新增模块不需要动它。

### 6.2 初始化顺序（决定你能安全依赖什么）

`ClientInitializer.initialize()`（主初始化，:60）按依赖顺序 new 管理器，关键是先 `ClientContext.moduleRegistry = new ModuleRegistry()`（:86）再 `ClientContext.hudModuleRegistry = new HudModuleRegistry()`（:87）。`initializeClient()`（:111）顺序是：

1. `ModuleDependencies.bind(ClientContext.moduleRegistry)`（:119）——所有跨模块引用在这里绑定；
2. `ClientKeybinds.register()`（:122）→ `ClientAccess.eventBus.post(new ClientInitializationEvent())`（:123，HUD 锚点重建等逻辑在这里）；
3. `new ConfigurationManager()` + `configuration.load()`（:125-128）——**模块全部构造完之后**才读配置。

结论：**模块构造器/静态初始化里不要依赖后注册的模块**。`ClientContext.moduleRegistry` 字段在 `new ModuleRegistry()` 返回后才赋值，构造期间调用 `ClientContext.getModule(Xxx.class)` 会拿到 `null`（HUD 模块注册更晚，构造时能取到 HUD 等模块，这是特例）。跨模块引用统一做静态 `bind` 方法，再在 `ModuleDependencies.bind` 里加一行，例如 `ColorSetting.bindModuleRegistry(registry)`（`ModuleDependencies.java:114`）就是把 `ColorsModule` 回填给颜色设置。

## 7. 事件总线与 @EventHandler

源码：`src/main/java/evernight/client/events/bus/EventBus.java`、`EventDispatcher.java`、`EventListener.java`、`MethodEventListener.java`；事件定义在 `client/events/`。总线单例是 `ClientAccess.eventBus`。

`EventDispatcher` 是总线接口（`subscribe/unsubscribe/subscribeStatic/post/postCancellable`，`EventDispatcher.java:5-21`）；`EventListener` 表示单条监听器（`isStatic()/getEventType()/getPriority()/call(Object)`）；`MethodEventListener` 把 `@EventHandler` 方法经 `LambdaMetafactory` 绑成 `Consumer` 并携带事件类型、static 标记与 priority（:13-61）。

### 7.1 方法签名规则

`@EventHandler`（`client/events/core/EventHandler.java:18`，`int priority() default 0` :19）只认满足以下条件的 declared method（含父类，`EventBus.collectListeners` :63-74）：

```java
@EventHandler
private void onSomeEvent(SomeEvent event) { ... }   // 返回 void、恰好 1 个非基本类型参数
```

返回类型必须是 `void`，参数个数必须为 1 且不是基本类型；可以是 `private`（`privateLookupIn` + `LambdaMetafactory` 绑定，:25-36）；`priority` 越大越先执行，相同 priority 的先后顺序不保证。

### 7.2 订阅时机

| 场景 | 写法 | 生命周期 |
| --- | --- | --- |
| 普通模块 | 不用写，`Module.enable()` 自动 `eventBus.subscribe(this)` | `disable()` 自动反订阅 |
| `PermanentModule` | 构造器里 `ClientAccess.eventBus.subscribe(this)`（`enable()` 因 `isToggled()` 恒真而提前 return） | 永不退订，见 `AntiCheatModule.java:370-371` |
| 仅需静态处理器常驻 | `ClientAccess.eventBus.subscribeStatic(Xxx.class)`：只注册 static 方法 | 手动 `unsubscribeStatic`；`AutoWalkModule.java:47-49`、`UIModule.java:254-256` |
| 管理器 / 非模块对象 | 构造器 `eventBus.subscribe(this)` | 需要时自行 `unsubscribe` |

注意 `subscribe(obj)` 会为该类继承链上所有 `@EventHandler` 方法建监听器，**包括 static 方法**；`subscribeStatic(clazz)` 只注册 static 方法。同一静态处理器不要两种方式都调，否则会注册两份、每事件触发两次。`EventBus` 用 `CopyOnWriteArrayList` 存监听器，派发过程中增删是安全的（:15-27）；实例监听器按对象身份缓存，**必须显式 unsubscribe**，否则对象泄漏。

### 7.3 取消语义与 EventStage

可取消事件继承 `CancellableEvent`（`client/events/core/CancellableEvent.java:3`）：`isCancelled()`、`cancel()`、`resetCancellation()`。`EventBus.post(event)`（:115）只负责逐个调用监听器、**不会**在取消后停止；发送方在派发结束后自行检查，例如 `mixin/ConnectionMixin.java:51-55`：

```java
PacketEvent.Receive event = new PacketEvent.Receive(packet);
ClientAccess.eventBus.post(event);
if (event.isCancelled()) {
    callback.cancel();
}
```

`EventBus.postCancellable(Cancellable)`（:126）会在派发前 `setCancelled(false)`，遇到取消立即 break；但 `Cancellable` 接口（`client/events/core/Cancellable.java`）在当前事件里没有实现者，仓库事件统一用 `CancellableEvent`，也没有 `postCancellable` 调用点——照 ConnectionMixin 的 `post` + `isCancelled()` 写法即可。

带阶段的事件（如 `PlayerAttackEvent`、`StepHeightEvent`）通过 `getStage()` 返回 `EventStage.Pre/Post`（`EventStage.java:3`，有 `isPre()/isPost()`），同一事件类按阶段各派发一次；`PacketEvent` 则用 `Receive/Send/PostSend` 三个子类区分方向与时机。

## 8. 配置与预设

源码：`src/main/java/evernight/client/managers/config/ConfigurationManager.java`、`PresetRegistry.java`；`client/config/preset/PresetData.java`、`ModulePresetCodec.java`、`ClientDataDirectory.java`。

### 8.1 配置文件在哪、键怎么来

- 根目录：`<gameDir>/evernight`（`ConfigurationManager.ROOT_DIRECTORY`，:31；旧目录 `mio-fabric` 由 `ClientDataDirectory.migrateLegacy`（:19）在启动时复制迁移）。
- 每个 `JsonConfigurable` 一个文件，文件名 = `getConfigName()`：模块配置是 `evernight/modules.json`（`ModuleRegistry.java:475`），HUD 是 `evernight/hud.json`（`HudModuleRegistry.java:131`）。
- 写盘时机：`ConfigurationManager.save()`（:136，遍历 `ConfigurableRegistry`），由 `mixin/MinecraftMixin.java:65-71` 在 `Minecraft.destroy` 时调用；读盘在 `initializeClient` 的 `configuration.load()`。
- 模块 JSON 的键：模块键 = `Module#getConfigName()`；内部 `settings` 键 = `Setting#getConfigName()`；另有 `enabled/key/state/mouse/drawn`（见 2.4）。**键一旦发布不可改名**，改名等于丢玩家配置。
- 新增模块不需要手动注册配置文件：`ModuleRegistry` 已在 `ConfigurableRegistry` 中，`toJson/fromJson` 自动遍历所有模块（跳过 `HudWidgetModule`，它们归 `hud.json`）。

### 8.2 预设

- 目录：`<gameDir>/evernight/presets/<category.getName().toLowerCase()>`（`PresetCategory.getDirectory()`，:327）。
- `PresetCategory` 决定 payload 字段：`toJson` 先 `writeMetadata` 写 `"client": "evernight"`、`"category": <小写分类名>`，再按分类写对应模块/设置（`MODULES/BINDS/HUD/COLORS/VISUALS/MACRO/ALL`，:35-323）。`PresetData` 是 `record PresetData(String name, JsonObject contents)`（:12），`saveToDirectory(Path)` 写 `<name>.json`（:18）。
- `PresetRegistry`（`extends Registry<PresetData>`，:23）：`applyPreset(String)`（:33）、`savePreset(String)`（:126）、`getPreset(String)`（:50）、`loadPresets()`（:153）、`forEachPreset(Consumer<PresetData>)`（:214）；从 `ClientContext.configurationManager.getPresetRegistry(category)`（:132）获取。
- 单模块剪贴板预设：`ModulePresetCodec.encode(Module)`（:48）把 `module.toJson()` 去掉 `key/state/mouse/enabled/drawn` 后 Base64，**并加入 `m` 字段 = `module.getConfigName()`**（:57）。`decode(String)`（:17）优先读 `m`、兼容旧字段 `module`（:38-46），按 `getConfigName()` 找模块，找不到抛 `IllegalArgumentException`；结果里的 `json` 直接喂 `module.fromJson(...)`（`PresetCommand.java:290-295`）。新增模块无需额外代码，只要不破坏 `getConfigName()` 与设置 `configName`。

## 9. 从零新增一个模块（完整清单）

以新增 `misc/ExampleModule` 为例，按顺序做完 6 步即可。

### 步骤 1：写类（含设置）

```java
package evernight.client.modules.misc;

import evernight.client.events.EventHandler;
import evernight.client.events.LocalPlayerTickEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.BooleanSetting;
import evernight.client.settings.NumberSetting;
import evernight.client.settings.Setting;

public final class ExampleModule extends Module {
    private final Setting<Boolean> strictSetting;
    private final Setting<Float> rangeSetting;

    public ExampleModule() {
        super("Example", "Does something.", ModuleCategory.MISC, "example", "ex");
        this.strictSetting = this.add(new BooleanSetting("Strict", false));
        this.rangeSetting = this.add(new NumberSetting<>("Range", 3.0F, 1.0F, 6.0F)
                .withUnit("m").dependsOn(this.strictSetting));
    }

    @EventHandler
    private void onTick(LocalPlayerTickEvent event) {
        if (!this.strictSetting.getValue()) {
            return;
        }
        // 每 tick 逻辑：上下文用 ClientAccess.evernightMinecraft / ClientContext
    }
}
```

### 步骤 2：注册到 ModuleRegistry

在 `client/managers/registry/ModuleRegistry.java` 构造器里按现有字母序插入；注册失败会抛异常，别忽略返回值：

```java
if (!this.registerModule(new evernight.client.modules.misc.ExampleModule()))
    throw new IllegalStateException("Registration rejected ExampleModule");
```

### 步骤 3：需要引用别的模块时加依赖绑定

在本类加静态字段与方法，并在 `client/bootstrap/ModuleDependencies.bind` 里加一行（按现有依赖顺序摆放）：

```java
private static SomeModule otherModule;   // 类字段

public static void bindModules(ModuleRegistry registry) {
    otherModule = registry.getModule(SomeModule.class);   // 再在 ModuleDependencies.bind 里调用本方法
}
```

### 步骤 4：补两份语言表

`en_us.json` 与 `zh_cn.json` **都要**加，且键集合一致：

```json
{
  "evernight.names.modules.Example": "Example",
  "evernight.descriptions.modules.Example": "Does something.",
  "evernight.names.settings.Strict": "Strict",
  "evernight.names.settings.Range": "Range"
}
```

设置 tooltip 可选：模块专属键写 `evernight.descriptions.settings.Example.Range`，或共享键 `evernight.descriptions.settings.Range`。运行时消息写 `evernight.ui.modules.Example.OutOfRange`，代码用 `Translations.ui("modules.Example.OutOfRange")`。

### 步骤 5：HUD 模块的额外一步

HUD 模块不注册进 `ModuleRegistry` 构造器，而是把 `ClientContext.moduleRegistry.registerModule(new ExampleHudModule());` 加进 `client/managers/registry/HudModuleRegistry.java` 构造器（:49-69 模式），配置自动进 `hud.json`；构造器里 `setRenderer(...)` + `getRenderer().setAnchor(HudAnchor.X)`，见 3.3。

### 步骤 6：验证

只改 Java：`gradlew.bat compileJava`；动了语言表/资源：`gradlew.bat build` + `python .github/skills/evernight-dev/scripts/check-lang.py`。检查旧模块配置键、`getConfigName()` 未变。

## 常见任务速查

1. 新增一个普通模块 → 继承 `Module`，构造器 `super("Name", "desc", ModuleCategory.MISC, "alias")`，再在 `ModuleRegistry` 构造器 `registerModule`。
2. 给模块加开关设置 → `this.add(new BooleanSetting("Strict", false))`，字段声明成 `Setting<Boolean>`。
3. 加数值设置（带单位/边界标签）→ `new NumberSetting<>("Delay", 50, 0, 250).withUnit("ms")`、`.setBoundaryLabel("None", SettingLabelCondition.MIN)`。
4. 加枚举设置 → 先写 `implements NamedEntry` 的枚举（`getName()` 英文），再 `new EnumSetting<>("Mode", ModeChoice.DEFAULT)`，两份 JSON 的 `evernight.names.values.*` 加键。
5. 设置 B 只在开关 A 打开时显示 → `b = this.add(new BooleanSetting("B", false).dependsOn(this.aSetting))`。
6. 做一组可折叠子设置 → 父 `new BooleanSetting("Group", false).markAsGroup()`，子全部 `.dependsOn(父)`。
7. 设置值改动后立刻生效 → `setting.setChangeCallback(() -> this.reloadSomething())`。
8. 某设置不写进配置/预设 → `setting.setExcludedFromConfig(true)`。
9. 给设置换持久化键（兼容旧配置时先确认）→ `setting.setConfigName("NewKey")`；标签仍查 `evernight.names.settings.<getName()>`。
10. 新增自定义集合设置 → 继承 `SetSetting<E>` 实现 `parseEntry/formatEntry/getAvailableEntries`；只存字符串就用 `StringSetSetting`。
11. 模块启用后才处理事件 → 写 `@EventHandler private void onX(XEvent event)`，订阅由 `Module.enable()` 负责。
12. 永远在跑的事件处理（不随模块开关）→ `ClientAccess.eventBus.subscribeStatic(Xxx.class)`（只注册 static 方法），或在构造器 `subscribe(this)`（PermanentModule 惯例）。
13. 读取另一个模块实例 → `ClientContext.getModule(OtherModule.class)`；构造期间不可靠，需用 `ModuleDependencies` 绑定。
14. 新增 HUD 文本组件 → 继承 `HudWidgetModule`，`HudTextRow` + `HudTextListRenderer` + `setAnchor`，注册进 `HudModuleRegistry`。
15. 导出/导入单模块预设 → `configurationManager.getModulePresetCodec().encode(module)` / `.decode(base64).json()`，payload 的 `m` 是 `module.getConfigName()`。
