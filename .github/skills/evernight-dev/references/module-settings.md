# Evernight 模块与设置框架 —— 新增模块 / 新增设置的完整约定

适用范围：新增/修改模块、设置、HUD 组件、注册表项，或弄清「模块何时订阅事件、设置怎样落盘、预设 payload 形态」。只写仓库真实存在的 public/protected API；包根 `evernight`，客户端代码在 `client.` 下。

## 1. 文件地图

源码在 `src/main/java/evernight/client/`：`modules/`、`settings/` 与 `settings/options/`、`managers/`（注册表/配置）、`events/` 与 `events/bus/`、`bootstrap/`（`ClientInitializer`、`ModuleDependencies`）。语言表在 `src/main/resources/assets/evernight/lang/`（`en_us.json`、`zh_cn.json`）。

## 2. Module 基类

源码 `evernight/client/modules/Module.java`。

### 2.1 构造器与身份

```java
public Module(String name, String description, ModuleCategory category, String... aliases)
public Module(String name, ModuleCategory category, String... aliases)
```

- `name` = `getName()`，**稳定英文标识**（命令/别名/配置键/预设依赖），永不翻译；构造器追加进 `aliases`，无别名传 `new String[0]`（`getAliases()` 至少 1 项）。
- `getConfigName()` 默认 `getName()`；`getDisplayName()` = `Translations.module(getName())`；`getDescription()` = `Translations.moduleDescription(getName(), super.getDescription())`，Java description 是 fallback。
- `getInfo()` 默认 `null`，`getInfoString()` 拼 `别名 [info]` 供 ModuleList HUD；`getCategory()` 返回构造分类。

### 2.2 设置与键位

```java
public <S extends Setting<?>> S add(S setting)
public <S extends Setting<?>> S add(S setting, Setting<?> precedingSetting)
public List<Setting<?>> getSettings()
```

`add` 返回传入的设置本身；`register/unregister` 动态增删设置，`getRegistry()` 返回同一列表；GUI 按 `getSettings()` 顺序渲染。

`Keybind` 是 record（`client/modules/Keybind.java`）：

```java
public record Keybind(int code, Mode mode, boolean mouse) {
    public static final Keybind UNBOUND = new Keybind(-1, Mode.TOGGLE, false);
    public boolean isUnbound() { return this.code < 0; }
    public static Keybind toggle(int code, boolean mouse)
}
```

`Mode`：`TOGGLE("Toggle","toggle")`、`HOLD("Hold","hold")`、`HOLD_REVERSE("ReverseHold","hold_reverse")`；序列化用 `getSerializedName()`，显示用 `getDisplayName()`。`setKeybind/getKeybind` 读写字段，`modifyKeybind(Function)` 供配置合并/外部键位托管覆写，`withMode/withCode/withMouse` 返回新 record。

### 2.3 启停生命周期（事件订阅发生在 enable）

`enable()`：`enabled = true` → `ClientAccess.eventBus.subscribe(this)` → post `ModuleToggleEvent` → `onToggle()` → `onEnable()`；`disable()` 对称且先 `unsubscribe(this)`。**模块里的 `@EventHandler` 只在启用期间被调用**，处理器通常不必再判断 `isToggled()`。`isToggled()` 返回 `enabled`，`toggle()/setToggled(boolean)` 来自 `client/utils/core/Toggleable.java` 的 default 方法；`onEnable/onDisable/onToggle` 是空实现钩子。

### 2.4 序列化与两个覆盖开关

`toJson()` 写 `enabled`、`key`(int)、`state`(`Keybind.Mode#getSerializedName`)、`mouse`、`drawn`、`settings`（只写非 `isGroupOnly()` 且非 `isExcludedFromConfig()` 的设置，键为 `Setting#getConfigName()`）；`fromJson()` 对称、逐项容错、缺键忽略。

- `protected boolean usesExternalKeybind()`：`true` 时 `fromJson` **不**用配置覆盖 key/mouse（键位托管给原版 `KeyMapping` 的模块用）。
- `protected void ignoreState()`：此后 `enabled` 不写也不读（状态由外部驱动的常驻模块用）。

`setDrawn/isDrawn` 控制列表/通知展示，`setWip`(protected)/`isWip` 只标记开发中。

### 2.5 可选模组依赖

- 整个模块依赖某个可选模组时，在构造器调 `protected final void requiresMod(String... modIds)`：任一 id 加载即算可用（如 `"baritone", "baritone-meteor"`）。`isAvailable()` 返回结果，GUI/补全会据此过滤；不可用模块仍注册（配置不丢），但不会出现在 ClickGUI、搜索和模块名补全里，`;<alias>` 不注册，`enable()` 直接返回。
- 只有个别配置项依赖可选模组时，用 `Setting#hideWithoutBaritone()` 或 `hideUnlessModLoaded(String...)`，别把无意义的开关留在界面上。
- 可选模组类不能出现在模块的字段类型、`implements` 列表或 `@EventHandler` 方法签名里（反射解析字段类型会在未安装时崩溃），桥接与字段类型禁区见 `SKILL.md` §3.8。

## 3. ModuleCategory 与派生基类

### 3.1 内置分类与插件分类

`ModuleCategory` 是 final class，实现 `NamedEntry`：`getName()` 返回稳定名；`getDisplayName()` 走 `Translations.get(键)`，查找链 当前语言 → 英文 → 完整键，键为 `evernight.names.categories.<Name>`。

内置常量 `COMBAT`、`MISC`、`RENDER`、`MOVEMENT`、`PLAYER`、`AUTO`、`EXPLOIT`、`CLIENT`、`HUD`，`getName()` 为对应首字母大写名，JSON 键同名。

`values()` 不可变：内置按上序在前，插件按注册顺序追加；`ordinal()` 即该顺序（内置固定 0-8），`findByName(String)` 大小写不敏感。分类不写配置/预设，模块按对象身份（`==`）比较。

插件不能 `new` 分类或改内置列表，只能在 `onInitialize` 用 `AddonContext#registerCategory(String)` 注册，显示键 `addons.<id>.names.categories.<Name>`，两份语言表都要写；普通模块直接用 `ModuleCategory.MISC` 等常量。内置共 9 个，`AUTO` 位于 `PLAYER` 与 `EXPLOIT` 之间。

### 3.2 PermanentModule

`public abstract class PermanentModule extends Module`：`isToggled()` 恒 `true`、`isDrawn()` 恒 `false`、`getKeybind()` 恒 `Keybind.UNBOUND`、`disable()` 空实现、`toJson()` 去掉 `enabled/key/state`。子类：`SettingsModule`、`ColorsModule`、`BaritoneModule`、`AntiCheatModule`。

**坑**：`isToggled()` 恒真使 `Module.enable()` 第一行提前 return，**PermanentModule 永远不会被 `enable()` 订阅**，必须在构造器手动订阅（`AntiCheatModule`、`BaritoneModule`）：`ClientAccess.eventBus.subscribe(this);`。它们不反订阅，生命周期等于进程。

### 3.3 HudWidgetModule（HUD 组件契约）

`public abstract class HudWidgetModule extends Module`：构造器 `public HudWidgetModule(String name, String... aliases)`，分类固定 `HUD`，`isDrawn()` 恒 `false`；覆写 `fromJson/toJson`，在 `settings` 外额外持久化 `hud`：`anchor`、`index`，anchor 为 `NONE` 时还存 `h`/`v`（水平/垂直对齐 ordinal）与相对坐标 `x`/`y`。

- `protected void setRenderer(HudWidgetRenderer renderer)`：**必须在构造器里调用**，否则 `renderer` 为 null；`getRenderer()`、`getAnchor()`、`setAnchor(HudAnchor)`（重挂到 `ClientContext.getHudEditor()` 锚点分组）。
- 尺寸由渲染器缓存：`HudWidgetRenderer.getDimensions()`/`setDimensions(float[])`，子类在 `calculateDimensions()` 算好交回。
- 文本型 `HudTextListRenderer` + `HudTextRow`（行 `Supplier<Component>` 每帧生成）；自绘型 `ResizableHudWidgetRenderer`（覆写 `calculateDimensions()`/`renderContent(PoseStack)`）。
- `getHudColor(float position)` 用 `HUDModule#fadeSetting`；`isInHudEditor()` 判断是否在编辑器。

HUD 模块注册点在 `HudModuleRegistry` 构造器，配置落 `hud.json`；combat 专用 `PlacementModule` 普通模块不要继承。

## 4. settings/ 设置体系

源码 `client/settings/`；所有设置均为 `Setting<T>` 子类。

### 4.1 Setting<T> 基类

```java
public abstract class Setting<T> extends NamedFeature implements ValueProvider<T>, JsonConfigurable
```

常用构造器：

```java
Setting(String name, T defaultValue)
Setting(String name, T defaultValue, Predicate<? super T> visibility)
Setting(String name, T defaultValue, T minimumValue, T maximumValue)
Setting(String name, T defaultValue, T min, T max, Predicate<? super T> visibility)
```

取值：`getValue()`、`getStagedValue()`、`getDefaultValue()`、`getMinimumValue/getMaximumValue()`、`setValue(T)`（数值按 min/max 钳制；引用变化触发 changeCallback）、`setDefaultValue(T)`、`reset()`。`getConfigName()` 默认等于 name，`setConfigName(String)` 改持久化键；`getDisplayName()` = `Translations.setting(getName())`——**标签看 name，配置与 tooltip 看 configName**。

小接口：`NamedEntry`（`String getName()`，default `getDisplayName()` = `Translations.value(getName())`）；`ValueProvider<T>`（`T getValue()`）；`JsonConfigurable`（`fromJson(JsonElement)` / `toJson()` / default `getConfigName()` 返回 `""`，注册表覆写成文件名如 `modules.json`）；`SettingLabelCondition`（枚举 `MIN`/`MAX`，配合 `setBoundaryLabel`）。

### 4.2 链式修饰（全部真实存在）

| 方法 | 签名 | 语义 |
| --- | --- | --- |
| `dependsOn` | `final Setting<T> dependsOn(Setting<?>... dependencies)` | 普通设置按 Boolean，false 隐藏；group 依赖要求 `isExpanded() && isVisible()`；非 Boolean 只能作 group 依赖；返回 `Setting<T>`。 |
| `markAsGroup` | `Setting<T> markAsGroup()` | 折叠组标题：`groupOnly = true` 且 `markVisibilityDependency()`。 |
| `markVisibilityDependency` | `void`；配套 `addVisibilityPredicate(Predicate<? super T>)` | 标记可见性依赖提供者；子设置用 `dependsOn(父)`，多谓词取与。 |
| `setConfigName` | `void setConfigName(String)` | 改配置键；**发布后不可改，否则老配置失效**。 |
| `setChangeCallback` | `void setChangeCallback(Runnable)` | 值引用变化时执行；异常被吞。 |
| `hideWithoutBaritone` | `void hideWithoutBaritone()` | Baritone 不可用时隐藏。 |
| `hideUnlessModLoaded` | `void hideUnlessModLoaded(String... modIds)` | 任一给定模组 id 未加载时隐藏，用于 Xaero 等其它可选模组的单项配置。 |
| `setBoundaryLabel` | `final Setting<T> setBoundaryLabel(String, SettingLabelCondition)` | 触 MIN/MAX 显示标签（`"None"`/`"Vanilla"`），仅 `hasNumericValue()`。 |
| `forceOpaqueColor` | `Setting<T> forceOpaqueColor()` | alpha=255。 |
| `setExcludedFromConfig` | `void setExcludedFromConfig(boolean)` | 不进 `Module#toJson/fromJson` 与预设。 |

### 4.3 全部设置类型

| 类 | 构造器 | `getValue()` |
| --- | --- | --- |
| `BooleanSetting` | `(String, Boolean)`；`(String, Boolean, Predicate)` | `Boolean` |
| `NumberSetting<N extends Number>` | `(String, N def, N min, N max)`；带 Predicate | `N` |
| `EnumSetting<E extends Enum<E> & NamedEntry>` | `(String, E def)`；带 Predicate | `E` |
| `StringSetting` | `(String, String)`；带 Predicate | `String` |
| `ColorSetting` | `(String, java.awt.Color)`；带 Predicate | `Color` |
| `IdentifierSetting` | `(String, NamedIdentifier)`；带 Predicate | `NamedIdentifier` |
| `SetSetting<E>`（abstract） | `@SafeVarargs (String, E... defaults)`；带 Predicate | `Set<E>` |
| `StringSetSetting` | `(String, String... defaults)`；带 Predicate | `Set<String>` |
| `StringListSetting` | `(String, String... defaults)` | `List<String>` |
| `ActionSetting` | `(String, Runnable)` | `Boolean` |
| `RegistrySetSetting<E>` | `(String, Registry<E>, E... defaults)`；带 Predicate | `Set<E>` |
| `CyclingTextSetting`（接口） | — | — |

备注：`BooleanSetting.parseValue` 接受 toggle/true/false/1/0；`NumberSetting` 有 `withUnit(String)`、`setNumber(Number)`、`Setting#disableNumericBounds()`，类型由 default 决定；`EnumSetting` 写 `getName()`，未知值回落首个常量；`ColorSetting` 有 `getColor()`、JSON 存 `{rgba, rainbow, sync}`；`IdentifierSetting` 存 `{name, category}`，`parseValue` 接受 `name` 或 `category:name`；`SetSetting` 子类实现 `parseEntry(String)`、`formatEntry(E)`、`getAvailableEntries()`，另有 `addEntry/removeEntry/containsEntry`；`StringSetSetting` 读写转小写、可用表为空；`StringListSetting` 有序、增删改触发 changeCallback、`withPrepareAction(Runnable)` 打开前重载文件、文件为唯一数据源时配 `setExcludedFromConfig(true)` + `withResetAction(Runnable)`；`ActionSetting` 不保存值、自动 `setExcludedFromConfig(true)`、`withInfo(Supplier<String>)`；`RegistrySetSetting` 存注册表 id，有 `getRegistry()`、`modifyEntries(Consumer<Set<E>>)`；`CyclingTextSetting` 只由 `NumberSetting`/`EnumSetting` 实现，供 `;module` 命令。

## 5. settings/options 枚举约定

源码 `client/settings/options/`。新增选项枚举：

1. `implements NamedEntry`，字段存英文 displayName，`getName()` 返回它（**不要中文，不要用 `name()` 当序列化名**）。
2. 标签由 default `getDisplayName()` 查 `evernight.names.values.<getName()>`；两份 JSON 的 `evernight.names.values.*` 都要加键。
3. 序列化走 `EnumJsonConverter`：按 `getName()` 大小写不敏感匹配，找不到回退第一个常量，所以 `getName()` 发布后不可改。枚举作为设置时标签键仍是 `evernight.names.settings.<SettingName>`，tooltip 用设置 configName。

| 枚举 | 形状 | 要点 |
| --- | --- | --- |
| `ESPModeChoice` | `PLAIN("Plain")/TEXT("Text")/BOTH("Both")` | JSON 加 `evernight.names.values.Plain` 等。 |
| `HudAnchor` | `name` + `HudVerticalAlignment` + `HudHorizontalAlignment` + `BiFunction<Integer,Integer,FloatBounds> boundsFactory` | `getName()` 是小写 id（`top_left`…`none`），JSON 键 `evernight.names.values.top_left`；`NONE` 为自由定位（才写 `h/v/x/y`）；`getBounds()/contains()` 供编辑器命中。 |
| `PresetCategory` | 实现 `JsonConfigurable`，每常量覆写 `fromJson/toJson` | 决定文件名与 payload：`getDirectory()` → `presets/<小写名>`，`writeMetadata` 写 `client/category`，`findByName` 供命令解析；新增分类补 `evernight.names.values.<getName()>`，`ConfigurationManager.load()` 遍历 `values()` 建目录。 |
| `ListFilterMode` | 普通枚举 + 静态 `matches(...)`/`isAny()` | 行为由**调用方**显式调用，不会自动生效。 |

## 6. 注册表与初始化顺序

### 6.1 注册表契约

- `RegistryContainer<T>`（`client/utils/inventory/RegistryContainer.java`）：`register/unregister/getRegistry`；`AbstractRegistry<T>`：`protected final List<T> entries`，`getRegistry()` 返回它，`findFirst/findFirstOrNull(Predicate)`。
- `Registry<T extends NamedEntry>`：`registerEntry` 按 `getName()` 去重，重名返回 `false` 且不入表；`unregisterEntry`。
- `ModuleRegistry`：所有模块的唯一存储表。构造器逐个 `registerModule(new XxxModule())`，失败即 `throw new IllegalStateException("Registration rejected XxxModule")`；最后按 `Module::getName` 排序。`registerModule` 拒绝隐藏实验类并清理隐藏设置；`getModule(Class)`（`@Deprecated` 但仍用）按类型索引；`getConfigName()` = `"modules.json"`。
- **坑：模块的字段类型不能是可选模组的类。** `registerModule` 会先调 `ExperimentalFeatures.unregisterHiddenSettings(module)`，它反射 `module.getClass().getDeclaredFields()`，而解析字段类型就会加载该类型。字段若声明成依赖可选模组的实现类（例：`EntityControlModule.pathfinder` 声明为 `EntityControlPathfinder`，后者 `implements AbstractGameEventListener`），没装 Baritone 时启动在 `Class.getDeclaredFields0` 处抛 `NoClassDefFoundError: baritone/api/event/listener/AbstractGameEventListener`，客户端卡在主初始化阶段。字段只声明桥接口或与可选模组无关的抽象，实现由桥在 `isAvailable()` 为真时构造（`BaritoneBridge` + `DisabledBaritoneBridge`、`WaypointAdapter` + `DisabledWaypointAdapter`，见 `SKILL.md` §3.8）；可选模组类也不要出现在模块的字段类型、`implements` 列表或 `@EventHandler` 方法签名里。
- `HudModuleRegistry`：构造器把 HUD 模块 `ClientContext.moduleRegistry.registerModule(new XxxModule())` 注册进同一 `ModuleRegistry`；`getConfigName()` = `"hud.json"`；收到 `ClientInitializationEvent` 时对所有 `HudWidgetModule` 重新 `setAnchor(getAnchor())` 重建锚点分组。
- `ConfigurableRegistry`：`ConfigurationManager` 保存/加载的 `JsonConfigurable` 列表，两个 registry 已在内。

### 6.2 初始化顺序（决定你能安全依赖什么）

`ClientInitializer.initialize()` 先 `ClientContext.moduleRegistry = new ModuleRegistry()` 再 `ClientContext.hudModuleRegistry = new HudModuleRegistry()`。`initializeClient()` 顺序：`ModuleDependencies.bind(ClientContext.moduleRegistry)`→ `ClientKeybinds.register()` → post `ClientInitializationEvent`→ `new ConfigurationManager()` + `configuration.load()`（**模块全部构造完之后**才读配置）。

**坑**：模块构造器/静态初始化里不要依赖后注册的模块。`moduleRegistry` 在 `new ModuleRegistry()` 返回后才赋值，构造期间 `ClientContext.getModule(Xxx.class)` 返回 `null`（HUD 模块注册更晚，构造时能取到 HUD 等模块，属特例）。跨模块引用统一写静态 `bind` 方法并在 `ModuleDependencies.bind` 加一行，如 `ColorSetting.bindModuleRegistry(registry)` 把 `ColorsModule` 回填给颜色设置。

## 7. 事件总线与 @EventHandler

源码 `client/events/bus/`（`EventBus`、`EventDispatcher`、`EventListener`、`MethodEventListener`），事件定义在 `client/events/`；总线单例是 `ClientAccess.eventBus`。`EventDispatcher`：`subscribe/unsubscribe/subscribeStatic/post/postCancellable`；`EventListener`：`isStatic()/getEventType()/getPriority()/call(Object)`；`MethodEventListener` 把 `@EventHandler` 方法经 `LambdaMetafactory` 绑成 `Consumer`。

### 7.1 方法签名规则

`@EventHandler`（`client/events/core/EventHandler.java`，`int priority() default 0`）只认满足以下条件的 declared method（含父类，`EventBus.collectListeners`）：

```java
@EventHandler
private void onSomeEvent(SomeEvent event) { ... }   // void、恰好 1 个非基本类型参数
```

返回必须 `void`，参数恰好 1 个且非基本类型；可以 `private`（`privateLookupIn` + `LambdaMetafactory` 绑定）；`priority` 越大越先执行，相同 priority 先后顺序不保证。

### 7.2 订阅时机

| 场景 | 写法 | 生命周期 |
| --- | --- | --- |
| 普通模块 | 不用写，`Module.enable()` 自动 `eventBus.subscribe(this)` | `disable()` 自动反订阅 |
| `PermanentModule` | 构造器 `ClientAccess.eventBus.subscribe(this)` | 永不退订，见 `AntiCheatModule` |
| 仅需静态处理器常驻 | `ClientAccess.eventBus.subscribeStatic(Xxx.class)`：只注册 static 方法 | 手动 `unsubscribeStatic`；`AutoWalkModule`、`UIModule` |
| 管理器 / 非模块对象 | 构造器 `eventBus.subscribe(this)` | 需要时自行 `unsubscribe` |

**坑**：`subscribe(obj)` 为该类继承链上所有 `@EventHandler` 建监听器，**包括 static 方法**；`subscribeStatic(clazz)` 只注册 static；同一静态处理器不要两种方式都调，否则注册两份、每事件触发两次。`EventBus` 用 `CopyOnWriteArrayList` 存监听器，派发中增删安全；实例监听器按对象身份缓存，**必须显式 unsubscribe**，否则对象泄漏。

### 7.3 取消语义与 EventStage

可取消事件继承 `CancellableEvent`（`client/events/core/CancellableEvent.java`）：`isCancelled()`、`cancel()`、`resetCancellation()`。`EventBus.post(event)` 只逐个调用监听器、**不会**在取消后停止；发送方须在派发后自行检查，如 `mixin/ConnectionMixin.java`：`post(event); if (event.isCancelled()) callback.cancel();`。

`EventBus.postCancellable(Cancellable)` 派发前 `setCancelled(false)`，遇取消立即 break；但 `Cancellable` 接口在当前事件里没有实现者，仓库事件统一用 `CancellableEvent`，也没有 `postCancellable` 调用点——照 ConnectionMixin 的 `post` + `isCancelled()` 写法即可。

带阶段的事件（如 `PlayerAttackEvent`、`StepHeightEvent`）用 `getStage()` 返回 `EventStage.Pre/Post`（`isPre()/isPost()`），同一事件类按阶段各派发一次；`PacketEvent` 用 `Receive/Send/PostSend` 三个子类区分方向与时机。

## 8. 配置与预设

### 8.1 配置文件在哪、键怎么来

- 根目录 `<gameDir>/evernight`（`ConfigurationManager.ROOT_DIRECTORY`）；旧目录 `mio-fabric` 由 `ClientDataDirectory.migrateLegacy` 启动时迁移。
- 每个 `JsonConfigurable` 一个文件，文件名 = `getConfigName()`：`evernight/modules.json`、`evernight/hud.json`。
- 凭据：`ExtraScreenshot` 的 `ClientId` 走 `evernight/screenshot.json`（`ScreenshotCredentialStore`），模块设置 `setExcludedFromConfig(true)`，故配置、预设与剪贴板预设都不带该凭据。
- 写盘 `ConfigurationManager.save()` 遍历 `ConfigurableRegistry`，`mixin/MinecraftMixin.java` 在 `Minecraft.destroy` 调用；读盘在 `configuration.load()`。
- 模块键 = `Module#getConfigName()`，`settings` 键 = `Setting#getConfigName()`，另有 `enabled/key/state/mouse/drawn`。**键一旦发布不可改名，否则丢玩家配置**。
- 新增模块不用手动注册：`toJson/fromJson` 自动遍历所有模块（跳过 `HudWidgetModule`，它们归 `hud.json`）。

### 8.2 预设

- 目录 `<gameDir>/evernight/presets/<category.getName().toLowerCase()>`（`PresetCategory.getDirectory()`）。
- `PresetCategory.toJson` 先 `writeMetadata` 写 `"client": "evernight"`、`"category": <小写分类名>`，再按分类写模块/设置（`MODULES/BINDS/HUD/COLORS/VISUALS/MACRO/ALL`）。`PresetData` 是 `record PresetData(String name, JsonObject contents)`，`saveToDirectory(Path)` 写 `<name>.json`。
- `PresetRegistry`（`extends Registry<PresetData>`）：`applyPreset(String)`、`savePreset(String)`、`getPreset(String)`、`loadPresets()`、`forEachPreset(Consumer<PresetData>)`；从 `ClientContext.configurationManager.getPresetRegistry(category)` 获取。
- 单模块剪贴板预设：`ModulePresetCodec.encode(Module)` 把 `module.toJson()` 去掉 `key/state/mouse/enabled/drawn` 后 Base64，**并加入 `m` 字段 = `module.getConfigName()`**；`decode(String)` 优先读 `m`、兼容旧字段 `module`，按 `getConfigName()` 找模块，找不到抛 `IllegalArgumentException`；结果 `json` 直接喂 `module.fromJson(...)`（`PresetCommand`）。

验证与提交见 `SKILL.md` 第 4 节。
