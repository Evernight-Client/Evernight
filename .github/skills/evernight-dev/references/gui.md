# Evernight GUI 层 —— 界面与组件的调用接口

范围：新增界面、往 ClickGUI 加行/按钮、用 `SettingComponent` 渲染设置、打开 Screen、接 HUD 编辑器交互。
代码在 `src/main/java/evernight/client/gui/`（包根 `evernight`）；HUD 绘制见 `render.md` 第 6 节，模块/设置框架见 `module-settings.md` 第 3、4 节。

## 1. 通用约定

### 1.1 GuiElement：所有界面元素的最小契约

`GuiElement`（GuiElement.java）是 `Panel` 与所有组件的根接口，方法全为 default 空实现。
**组件不保存绝对坐标**，只存相对所属 `Panel` 的 `yOffset`，由 Panel 布局下发。

- `init()`：`Screen.init()` 时同步数据。
- `render(PoseStack, double, double)`：只画；**不要改布局**。
- `update(double mouseX, double mouseY)`：Panel 在 render 前对可见子元素调用。
- `getBaseHeight()` / `getHeight()`：基础行高 = `UIModule.styleSetting` 的 HeightPadding + `FontRenderer.getHeight()`；可展开组件重写 `getHeight()`。
- `getTextYOffset()`：文本垂直居中偏移。
- `getYOffset()/setYOffset(int)`：容器下发位置；自定义容器必须继续传给子元素。
- `isMouseOver(double, double)`：默认 false，**必须重写，否则收不到点击**。
- `mouseClicked/mouseReleased/mouseScrolled/keyPressed/charTyped`：Panel 冒泡转发，参数为原始键码/字符。

`ScissorStack`：`push(x, y, width, height)`、`pop()`、
`pushIntersection(...)`（与栈顶求交，**要求栈非空**——源码直接 `regions.getLast()`）。
推/弹前必须 `GuiDrawUtils.flush()`，否则批量绘制被裁错（`gui/panels/Panel.java`）。

`CursorType`：`STANDARD` / `POINTER` / `INPUT`，`apply()` 才设 GLFW 光标；组件在 `render` 里**登记**下一帧光标
（`ClientScreen.cursor = CursorType.POINTER;`，输入框用 `INPUT`），每帧开头 `cursor.apply()` 后重置。

`CloseState`：父容器是否已关闭的二元契约，常量 `CloseState.ALWAYS_OPEN`；`SettingComponent` 用它自动隐藏行，
`ModuleComponent` 与 `ColorSettingComponent` 自己实现。

## 2. 组件基类与 ComponentFlag

`AbstractComponent` 是所有组件的基类，构造器 `AbstractComponent(Panel panel)` /
`AbstractComponent(Panel panel, int yOffset)`，已实现四件事：

- `render` 处理 hover 音效/高亮、置 `ClientScreen.cursor = CursorType.POINTER`、
  `ClientContext.getClickGui().markHovered()`；子类**必须调 `super.render(...)`** 才有效果。
- `mouseClicked` 按 `UIModule` 播放左右键音效，但**不执行任何动作**；子类判命中后自己做业务。
- `isMouseOver` 用 `panel.getX()/getY() + yOffset`，`clampYToPanel` 夹进可视区，滚动时命中自动跟随。
- `hasFlag/addFlags`；`getPanel()`；`isLeftClickEligible()` 默认 true，`ModuleComponent` 对 `PermanentModule` 返回 false。

`ComponentFlag`：`PADDING_SHIFT`（`getHorizontalPadding()` 从 1 变 2，ModuleComponent 给设置子组件加）、
`SILENT`（不播 hover/click 音效，但**不**阻止点击回调、不影响绘制）、`SILENT_HOVER`（只关 hover 音效）。

## 3. 现成组件

源码：`gui/components/`（ButtonComponent、ActionButtonComponent、ColorActionButton、ModuleComponent、HudModulePanel）。

| 类 | 构造器 | 行为 / 可用方法 |
| --- | --- | --- |
| `ButtonComponent` | `(Panel, String)` / `(Panel, Supplier<String>)` | 文本按钮；`getLabel()`；`setHoverOffsetEnabled(true)` hover 上移 1px；`"\n"` 自动增高；protected `renderButtonBackground` |
| `ActionButtonComponent extends ButtonComponent` | `(Panel, String, Runnable)` / `(Panel, Supplier<String>, Runnable)` | 左键执行 `Runnable`；`isHighlighted()` 控高亮底（默认 true） |
| `ColorActionButton` | `(int yOffset, ColorSettingComponent editor, String label, Runnable action)` | **abstract**，子类实现 `float getLabelOffsetX()`；editor 不可用时自动不响应/不绘制 |
| `ModuleComponent` | `(Module module, Panel panel, int yOffset)` | 左键 `module.toggle()`，右键 `toggleOpen()`；`getModule()`、`setOpen(boolean)`、`isOpen()`、`isClosed()`、`startSearchHighlight()`；字段 `children`、`heightAnimation`、`animatedHeight`；自动为每个 Setting 建行，非 `HudWidgetModule`/`PermanentModule` 追加 `KeybindComponent` |
| `HudModulePanel` | `HudModulePanel()` | 固定 `ModuleCategory.HUD`，只给 HUD 编辑器用；`getHeightLimit()` 按各行 baseHeight+1 累计 |

加动作按钮：`panel.registerChild(new ActionButtonComponent(panel, "Clear", () -> setting.getValue().clear()))`。

## 4. SettingComponent 家族

`SettingComponent<T>`（`gui/components/setting/`）是**所有设置行**的基类，构造器
`(Panel panel, Setting<T> setting)` / `(Panel panel, CloseState parentCloseState, Setting<T> setting)`；
公共成员 `public boolean editing`、`getSetting()`、`getParentCloseState()`、`isUnavailable()`。
`isUnavailable()` = 设置不可见 **或** 父容器已关闭（不绘制、不响应点击，`getHeight()` 在设置不可见时返回 0）。
`renderWithTextScroll` 的裁剪分支把 `parentCloseState` 强转为 `ModuleComponent`，
**给自定义面板传 `CloseState.ALWAYS_OPEN` 时不要依赖它**。

**Setting → 组件的唯一映射**在 `ModuleComponent#createSettingComponent` 的 switch；`<?>` 类型用
`new XxxSettingComponent<>(panel, this, value)`，第二个实参是本 `ModuleComponent`（兼作 CloseState）：

| Setting → 组件 | 构造器签名 / 交互 |
| --- | --- |
| `BooleanSetting` → `BooleanSettingComponent` | `(Panel, CloseState, Setting<Boolean>)`：左键切换；`visibilityDependency` 右键展开子设置；`groupOnly` 显示 "..." |
| `NumberSetting<?>` → `NumericSettingComponent<N>` | `(Panel, CloseState, NumberSetting<N>)`：左键拖滑条（Integer/浮点取整，clamp 到 min/max）；右键文本输入，Enter 提交、Esc 取消；字符只收 `1234567890.,-` |
| `EnumSetting<?>` → `EnumSettingComponent<E>` | `(Panel, CloseState, EnumSetting<E>)`：左键切下一个值；右键展开/收起选项 |
| `StringSetting` → `StringSettingComponent` | `(Panel, CloseState, Setting<String>)`：左键编辑，Enter 提交、Esc 取消、Ctrl+V 粘贴；`getText()`、`beginEditing()`（搜索框用）、`setText(String)`、`getCursorMarker()` |
| `StringListSetting` → `StringListSettingComponent` | `(Panel, ModuleComponent, StringListSetting)`：显示 `名称 [条目数]`；左/右键打开 `StringListScreen` |
| `ActionSetting` → `ActionSettingComponent` | `(Panel, ModuleComponent, ActionSetting)`：显示名称与 `withInfo(...)` 动态文本；左/右键执行动作 |
| `ColorSetting` → `ColorSettingComponent` | `(Panel, CloseState, Setting<Color>)`：右键标题行展开/收起 HSV+Alpha 取色器；Copy/Paste/Sync/Rainbow（主题色不建 Sync）；实现 `CloseState`；`isHeaderHovered(...)`、`isClosed()` |
| `IdentifierSetting` → `SoundSettingComponent` | `(Panel, CloseState, Setting<NamedIdentifier>)`：显示 `setting.getValue().getName()`；左键打开 `SoundSelectionScreen(setting, getClickGui())` |
| `RegistrySetSetting<?>` → `RegistrySetSettingComponent<E>` | `(Panel, ModuleComponent, RegistrySetSetting<E>)`：显示 `名称 [已选数量]`；左/右键打开 `RegistrySetScreen` |
| 其它（如 `StringSetSetting`） | `default -> null`，**不渲染**：新增 Setting 子类 = 新增组件 + 补 case，否则能看到值却看不到行 |

设置行折叠/依赖：`markVisibilityDependency()` + `dependsOn(...)`，右键展开子设置（见 `module-settings.md` 第 4.2 节）。
`KeybindComponent(int index, ModuleComponent moduleRow[, Setting<Keybind>])` 不在 switch 里，由 `ModuleComponent`
追加：左键监听，再按任意键/鼠标键完成绑定；Esc/Delete 清除；右键循环 `TOGGLE / HOLD / HOLD_REVERSE`。
自定义 Panel 手放设置行传 `CloseState.ALWAYS_OPEN`：
`this.addChild(new NumericSettingComponent<>(this, CloseState.ALWAYS_OPEN, this.maxPriceSetting));`

## 5. 面板 Panel

`Panel` 实现 `GuiElement` + `NamedEntry` + `RegistryContainer<GuiElement>`。构造器 `Panel(String name)`：
`name` 是稳定英文名，标题由 `getTitle()` 走 `Translations.panel(name)` 实时解析（键见 `i18n.md` 第 3 节）。
默认宽 `92 + UIModule.styleSetting 的 widthPadding`，默认位置 (5,5)，右击标题栏折叠。

API：`getX()/getY()/setX()/setY()`、`getWidth()/getHeight()`、`getHeaderHeight()`、`getChildren()`、
字段 `children`、`addChild(GuiElement)`、`addChildren(GuiElement...)`、`registerChild(GuiElement)`、
`updateLayout()`（按 children 高度重排 yOffset，每帧末尾自动调）、`setExpanded(boolean)/isFullyExpanded()`、
`disableHeightLimit()`、`isMouseOverHeader(...)`。`render` 推 scissor、只渲染可视子元素。

`ModuleCategoryPanel(ModuleCategory category)`：构造时把该分类模块按 `getName()` 排序生成 `ModuleComponent`；
`isChildVisible` 用 ClickGUI 搜索框过滤（匹配 aliases）。

## 6. 屏幕 Screen

`ClientScreen`（`gui/screens/core/`）是所有自绘界面的基类（`extends Screen`，`isPauseScreen()` 返回 false，
`renderBackground` 为空）。子类只做三件事：

1. 构造器里往 `protected ArrayList<Panel> panels` 加面板（`this.panels.add(...)` 或 `getPanels().add(...)`）；
2. 需要额外绘制时覆写 `renderContents(GuiGraphics, PoseStack, int mouseX, int mouseY, float partialTick)`；
3. 用 `new XxxScreen(...).open()` 打开。

- `open()`：设父屏并 `mc.setScreen(this)`；当前屏也是 ClientScreen 则继承其父屏并跳过开场动画。
- `closeWithTransition()`：150ms 关闭动画，`tick()` 后回 parentScreen 或 `onClose()`；**不是**直接 setScreen。
- `getPanels()` / `getParentScreen()/setParentScreen(Screen)` / `resetPanelPositions()`：面板列表 / 父屏 / 横向重排。
- `setTooltipText(String)` / `markHovered()` / `suppressEscapeClose()`：tooltip / 刷新存活 / Esc 不关闭。
- `getGuiScale()` / `hasMenuParent()` / `isClosing()` / `getTransitionOpacity()`：缩放 / 父屏是否菜单 / 关闭动画状态。
- `onMouseClick(double,double,int)` / `onMouseRelease(...)` / `renderBackgroundOverlay(PoseStack)`：钩子 / 背景 tint。
- `public static CursorType cursor`：屏幕级光标寄存器，`onClose()` 恢复 STANDARD。

全部 Screen（`gui/screens/` 下，`RegistrySetScreen` 在 `gui/registry/`）：

- `ClickGuiScreen()`：`getSearchBox()`、`getEditingSetting()/setEditingSetting(SettingComponent<?>)`、字段
  `searchBox`/`openModuleComponents`；用 `ClientContext.getClickGui().open()`。
- `HudEditorScreen()`：`addWidget(HudAnchor, HudWidgetRenderer)`、`addWidget(HudWidgetRenderer)`、`removeWidget(...)`、
  `getAnchorGroup(HudAnchor)`、`getAnchorGroups()`、`requestDirectionRefresh()`、`renderWidgets(...)`；静态 `hoverLabel`。
- `PresetScreen()`：`getListPanel()`、`getPresetPanel()`、`getSelectedCategory()/setSelectedCategory(PresetCategory)`。
- `RegistrySetScreen(Screen parentScreen, RegistrySetSetting<E> registrySetting)`：集合设置编辑器，见第 7 节。
- `SoundSelectionScreen(Setting<NamedIdentifier> selectedSetting, Screen parentScreen)`：`getSelectedSetting()`；
  `closeWithTransition()` 回父屏。
- `ShulkerPreviewScreen(ShulkerBoxMenu menu, Inventory inventory, Component title, Screen parentScreen)`
  （`extends ShulkerBoxScreen`）：`rejectMouseClick(...)`、静态 `isRenderingPreview()`；Esc 回父屏。
- `TradeManagerScreen()`：`requestPanelChange(TradePanelRequest)`（`forCreate()` / `forEdit(trade)`）。
- `TradeEditorScreen(String tradeName)` / `TradeEditorScreen(TradeDefinition trade)`：`setTooltipStack(ItemStack)`；
  先 `setParentScreen(managerScreen)` 再 `setScreen`。
- `ViewModelEditorScreen(ViewModelModule module)`：`getEditMode()/setEditMode(ViewModelEditMode)`（NONE/TRANSLATE/ROTATE/SCALE）。
- `ConfirmGameCloseScreen()`（`extends ConfirmScreen`）：MinecraftMixin 拦截关窗时打开。
- `DisconnectConfirmationScreen(Runnable confirmationAction)`（`extends ConfirmScreen`）：确认执行 Runnable，取消无操作。
- `PresetImportScreen(Screen previousScreen, Path sourcePath)`：静态 `openForDroppedFiles(Screen, List)`（仅单个 .json 返回 true）。
- `ModuleImportConfirmationScreen(Screen previousScreen, Module targetModule, JsonObject importedJson)`：
  Enter 直接导入；Ctrl+V 用它。

坑：

- `ClickGuiScreen`：Ctrl+F 聚焦搜索框、Enter 定位并展开模块、Ctrl+V 解析剪贴板模块预设；
  `openModuleComponents` 最多留 3 个展开模块，超出自动收起最早的。
- `HudEditorScreen` 的模块面板是 `HudModulePanel`；左右键在 HUD 组件上分别触发拖动/展开设置行。
  它由 `HUDModule` 的 `HudEditor` 设置回调打开，**不要自己 new**。
- `PresetScreen` / `SoundSelectionScreen` / `ClickGuiScreen` 覆写 `onFilesDrop(List)`；`ClientScreen` 不拦截。
- `TradeManagerScreen.requestPanelChange` 只登记请求，真正换面板在下一次 `renderContents`。
- `ShulkerPreviewScreen.keyPressed` 用 Esc 直接 `setScreen(parentScreen)`，不走关闭动画。

## 7. 集合编辑器 Registry

`RegistrySetSetting<E>(String name, Registry<E> entryRegistry, E... defaultEntries)`（`settings/RegistrySetSetting.java`，
另有带 visibility 谓词的重载）：`getRegistry()`、`modifyEntries(Consumer<Set<E>>)`。设置行里打开：

```java
new RegistrySetScreen<>(ClientAccess.evernightMinecraft.screen, this.registrySetting).open();
```

`RegistrySetScreen<E>` 构造时建三个列表面板：**Available**（未选中项，左键 `setting.addEntry(entry)`，
只有这列受搜索框过滤）、**Added**（已选中项，左键 `setting.removeEntry(entry)`）、**Extra**
（`StringSettingComponent` 搜索框，Ctrl+F 聚焦 `beginEditing()` + "Clear" 按钮清空集合）。

`RegistryEntryCatalog` 是**包私有**类，外部不可调用；只为 `BLOCK` / `ITEM` / `ENTITY_TYPE` 提供精选排序清单
（`entriesFor(Registry<?>)` → `EntryList`，含 `orderedEntries()` / `contains(Object)`），其它注册表返回空清单。

## 7.1 搜索框（统一搜索模式）

所有搜索框（Registry 集合编辑器、ClickGUI 模块搜索、`ChestSearchBar` 箱子搜索、交易物品/附魔选择）共用
`client/utils/search/`，行为由 `UI > Search` 的 `SearchMode` 决定：`AUTO` 跟随 Evernight 界面语言，
`CHINESE` 匹配中文名 + 智能拼音（全拼、首字母、中英混输），`ENGLISH` 匹配英文名 + 注册名（`modid:path`、`path`）。

- `SearchQuery`：归一化后的查询 + 解析后的模式 + 每个候选字符串的结果缓存；`matchesEnglish(...)` 只做大小写不敏感
  `contains`，`matchesChinese(...)` = 字面 `contains` 或拼音匹配（`PinyinMatcher` → 内置裁剪版 PinIn，无索引；
  默认开启模糊音 zh/z、sh/s、ch/c、ang/an、eng/en、ing/in、v/u）。
- `SearchQueryHolder`：每个搜索框一个，`update(文本)` 只在文本或生效模式变化时重建 `SearchQuery`，**布局循环里可以每帧调**。
- `SearchKey`：某条目的 `english[]` / `chinese[]` 名字集合；`matches(query)` 按当前模式选一侧。
- `SearchNames`：为 item / block / entity / effect / potion / sound / enchantment / `ItemStack`（含附魔与药水效果）/
  `Module` 构建 `SearchKey`，英文名与中文名分别通过 `LocalizedNames`（资源管理器的 `en_us` / `zh_cn` 语言表，
  含模组键）查询，并补注册名。中文模式**不会**命中英文名，英文模式**不会**命中中文名。

## 8. 打开界面的标准写法

核心：`gui/screens/core/ClientScreen.java`、`client/ClientContext.java`。
规则：**ClientScreen 子类优先 `.open()`**（`(new PresetScreen()).open()`），它负责父子屏关系与关闭动画返回；
要指定返回目标时再 `setParentScreen` + `mc.setScreen(...)`（`TradeEditorScreen` 先 `setParentScreen(managerScreen)`）。
ClickGUI / HUD 编辑器是单例：`ClientContext.getClickGui().open()`、
`ClientAccess.evernightMinecraft.setScreen(ClientContext.getHudEditor())`，**不要 new**。

- 不在渲染线程/要等下一个 tick：`ClientContext.taskScheduler.scheduleScreenChange(screen)` 或
  `scheduleRotationTask(runnable, 0)`；`TradeCommand` 就这么开交易屏。
- 开界面前检查 `ClientAccess` 的 `isWorldUnavailable()`（player/level 为空）。
- `ClientScreen.keyPressed` 的 Esc：先广播给所有面板，未被 `suppressEscapeClose()` 取消才 `closeWithTransition()`。

## 9. 通知与确认框

**聊天 / HUD 通知**：`ChatUtils.printChat(Component, int id)`、`printChat(Component, MessageSignature,
MessagePriority)`、`createSignature(int)`、`moduleSignature(Module)`、`sendChatOrCommand(String)`；
`HudNotification(String text, long durationMillis, Color color)`（`getText()`、`getColor()`、`getStartTimeMillis()`、
`getEndTimeMillis()`，子类 `LagNotification` / `MusicNotification` / `GreetingNotification`）。
`NotificationsModule` 公开入口只有 `shouldNotifyCommands(NotificationsModule)`、`getBracketColor()`、
`getWatermarkColor()`（另有 public 字段 `watermarkSetting` / `bracketsSetting`），**没有**「发一条通知」的公共方法；
`WelcomerModule` 的通知列表也无公共推送入口，不要从别的模块往里塞对象。详见 `runtime.md` 第 8 节。

**确认框**：原版 `ConfirmScreen(BooleanConsumer, Component, Component)` + `setScreen`；
断连/关游戏/导入确认的封装见第 6 节 Screen 表；「再点一次才生效」的确认用计时器（`TradeActionsPanel.deleteIfConfirmed`）。
