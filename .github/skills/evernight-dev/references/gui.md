# Evernight GUI 层 —— 界面与组件的调用接口

适用范围：要在 Evernight 里新增界面、往 ClickGUI 加行/按钮、用 `SettingComponent` 渲染设置、打开已有 Screen，
或接 HUD 编辑器交互时读这一份；HUD 的具体绘制（`HudWidgetRenderer`、锚点、边距）见 `render.md`，模块/设置框架见 `module-settings.md`。
全文路径相对仓库根，包根 `evernight`，GUI 代码在 `src/main/java/evernight/client/gui/`。

## 目录

- [1. 通用约定](#1-通用约定)
- [2. 组件基类与 ComponentFlag](#2-组件基类与-componentflag)
- [3. 现成组件](#3-现成组件)
- [4. SettingComponent 家族](#4-settingcomponent-家族)
- [5. 面板 Panel](#5-面板-panel)
- [6. 屏幕 Screen](#6-屏幕-screen)
- [7. 集合编辑器 Registry](#7-集合编辑器-registry)
- [8. 打开界面的标准写法](#8-打开界面的标准写法)
- [9. 通知与确认框](#9-通知与确认框)
- [常见任务速查](#常见任务速查)

## 1. 通用约定

源码：`src/main/java/evernight/client/gui/`（`GuiElement.java`、`ScissorStack.java`、
`CursorType.java`、`CloseState.java`）

### 1.1 GuiElement：所有界面元素的最小契约

`GuiElement`（GuiElement.java:7）是 `Panel` 与所有组件的根接口，全部方法都有 default 空实现。
关键约定：**组件不保存绝对坐标**，只保存相对所属 `Panel` 的 `yOffset`，由 Panel 布局时下发。

| 方法 | 约定 |
| --- | --- |
| `init()` | 每次 `Screen.init()` 调用一次，用于把数据同步到组件内部状态 |
| `render(PoseStack, double, double)` | 只负责画；不要在这里改布局 |
| `update(double mouseX, double mouseY)` | Panel 在 render 前对可见子元素调用，用于动画/缓存坐标 |
| `getBaseHeight()` / `getHeight()` | 基础行高 = `UIModule.styleSetting` 的 HeightPadding + `FontRenderer.getHeight()`；可展开组件重写 `getHeight()` |
| `getTextYOffset()` | 文本在行内垂直居中的偏移 |
| `getYOffset()/setYOffset(int)` | 容器分配纵向位置时调用；自定义容器必须把偏移继续传给子元素 |
| `isMouseOver(double, double)` | 默认 false，组件必须重写，否则收不到点击 |
| `mouseClicked/mouseReleased/mouseScrolled/keyPressed/charTyped` | 由 `Panel` 冒泡转发，参数为原始键码/字符 |

`ScissorStack` 是静态裁剪栈：`push(x, y, width, height)`（GUI 坐标，内部换算 framebuffer）、
`pop()`（关 scissor 并恢复上一层）、`pushIntersection(...)`（与栈顶求交，**要求栈非空**，
源码直接 `regions.getLast()`）。推/弹之前必须先 `GuiDrawUtils.flush()`，否则批量绘制会被裁错：

```java
GuiDrawUtils.flush();
ScissorStack.push(this.x - 1, this.y + this.getHeaderHeight(), this.getWidth() + 1,
        (int) (this.height * expansionValue) + 1 - this.getHeaderHeight());
// ... 画被裁剪的内容 ...
GuiDrawUtils.flush();
ScissorStack.pop();
```

（摘自 `gui/panels/Panel.java:291-294, 311-314`，`gui/components/ModuleComponent.java:269-276` 同样写法。）

`CursorType`（`CursorType.java:10`）枚举 `STANDARD` / `POINTER` / `INPUT`，`apply()` 才真正设置 GLFW 光标。
组件在 `render` 里**登记**下一帧光标；`ClientScreen.render` 每帧开头 `cursor.apply()` 后重置为 STANDARD
（`screens/ClientScreen.java:56, 297-298`）。真实用法：`ClientScreen.cursor = CursorType.INPUT;`
（`StringSettingComponent.java:107`）、`ClientScreen.cursor = CursorType.POINTER;`
（`AbstractComponent.java:95`）。

`CloseState`（`CloseState.java:3`）是"父容器是否已关闭"的二元契约，提供常量 `CloseState.ALWAYS_OPEN`。
`SettingComponent` 用它自动隐藏行；`ModuleComponent` 与 `ColorSettingComponent` 自己实现它
（模块收起时设置行不可见、取色器收起时控件不响应）。

## 2. 组件基类与 ComponentFlag

源码：`src/main/java/evernight/client/gui/components/`（AbstractComponent.java、ComponentFlag.java）

`AbstractComponent`（AbstractComponent.java:20）是所有组件的基类，构造器两个：
`AbstractComponent(Panel panel)`、`AbstractComponent(Panel panel, int yOffset)`。
它已经实现了四件"免费"的事：

- `render` 里处理 hover 音效、hover 背景高亮、`ClientScreen.cursor = CursorType.POINTER`、
  `ClientContext.getClickGui().markHovered()`。子类必须调用 `super.render(...)` 才有效果。
- `mouseClicked` 里按 `UIModule` 设置播放左右键音效，但**不执行任何动作**；子类自己判断命中后做业务。
- `isMouseOver` 用 `panel.getX()/getY() + yOffset` 计算，并用 `clampYToPanel` 夹在面板可视区内，
  所以面板滚动时命中判定自动跟随。
- `hasFlag/addFlags` 标志位；`getPanel()` 取所属面板；`isLeftClickEligible()` 默认 true，
  `ModuleComponent` 对 `PermanentModule` 返回 false。

`ComponentFlag`（ComponentFlag.java:3）只有三个值：

| 标志 | 含义 |
| --- | --- |
| `PADDING_SHIFT` | `getHorizontalPadding()` 从 1 变 2，用于设置行缩进（ModuleComponent 给每个设置子组件加此标志） |
| `SILENT` | 不播 hover/click 音效；**不**阻止点击回调，也不影响绘制 |
| `SILENT_HOVER` | 只关闭 hover 音效，点击音效照常 |

设置行组件的创建方式（摘自 `ModuleComponent.java:129-131`）：

```java
component.setYOffset(childY);
component.addFlags(new ComponentFlag[]{ComponentFlag.PADDING_SHIFT});
this.children.add(component);
```

## 3. 现成组件

源码：`src/main/java/evernight/client/gui/components/`（ButtonComponent.java、ActionButtonComponent.java、
ColorActionButton.java、ModuleComponent.java、HudModulePanel.java）

| 类 | 构造器 | 行为 / 可用方法 |
| --- | --- | --- |
| `ButtonComponent` | `(Panel, String)` / `(Panel, Supplier<String>)` | 文本按钮；`getLabel()`；`setHoverOffsetEnabled(true)` 让 hover 时文字上移 1px；`"\n"` 自动增高；protected `renderButtonBackground` |
| `ActionButtonComponent extends ButtonComponent` | `(Panel, String, Runnable)` / `(Panel, Supplier<String>, Runnable)` | 左键点击执行 `Runnable`；`isHighlighted()` 控制是否画高亮底（默认 true） |
| `ColorActionButton` | `(int yOffset, ColorSettingComponent editor, String label, Runnable action)` | **abstract**，子类实现 `float getLabelOffsetX()`；editor 不可用时自动不响应/不绘制 |
| `ModuleComponent` | `(Module module, Panel panel, int yOffset)` | 一行模块：左键 `module.toggle()`，右键 `toggleOpen()` 展开设置；`getModule()`、`setOpen(boolean)`、`isOpen()`、`isClosed()`、`startSearchHighlight()`；公共字段 `children`、`heightAnimation`、`animatedHeight`；自动为每个 Setting 建设置行，并在非 `HudWidgetModule`/`PermanentModule` 时追加 `KeybindComponent` |
| `HudModulePanel` | `HudModulePanel()` | 固定 `ModuleCategory.HUD` 的分类面板，只给 HUD 编辑器用；`getHeightLimit()` 按各行 baseHeight+1 累计 |

给面板加一个动作按钮（摘自 `gui/registry/RegistrySetScreen.java:92-94`）：

```java
this.extraPanel.registerChild(new ActionButtonComponent(this.extraPanel,
        "Clear",
        () -> this.registrySetting.getValue().clear()));
```

## 4. SettingComponent 家族

源码：`src/main/java/evernight/client/gui/components/setting/SettingComponent.java` 及同目录全部
`*SettingComponent.java`；映射逻辑在 `ModuleComponent.java:188-204`

`SettingComponent<T>`（SettingComponent.java:17）是**所有设置行**的基类：
构造器 `(Panel panel, Setting<T> setting)`、`(Panel panel, CloseState parentCloseState, Setting<T> setting)`。
公共成员：`public boolean editing`、`getSetting()`、`getParentCloseState()`、`isUnavailable()`。
`isUnavailable()` = 设置不可见 **或** 父容器已关闭；此时行不绘制、不响应点击，`getHeight()` 在 `setting` 不可见时返回 0
（父容器关闭时由 `ModuleComponent` 整体不渲染子行）。
`renderWithTextScroll` 是基类提供的"文字过长时横向滚动"工具（裁剪分支把 `parentCloseState` 强转为
`ModuleComponent`，见 SettingComponent.java:82-89；给自定义面板传 `CloseState.ALWAYS_OPEN` 时不要依赖它）。

**Setting → 组件的唯一映射**（`ModuleComponent#createSettingComponent` 的 switch，新设置类型必须在这里补 case）：

```java
return switch (setting) {
    case RegistrySetSetting<?> value -> new RegistrySetSettingComponent<>(panel, this, value);
    case evernight.client.settings.BooleanSetting value -> new BooleanSettingComponent(panel, this, value);
    case evernight.client.settings.NumberSetting<?> value -> new NumericSettingComponent<>(panel, this, value);
    case evernight.client.settings.ColorSetting value -> new ColorSettingComponent(panel, this, value);
    case evernight.client.settings.StringSetting value -> new StringSettingComponent(panel, this, value);
    case evernight.client.settings.EnumSetting<?> value -> new EnumSettingComponent<>(panel, this, value);
    case evernight.client.settings.IdentifierSetting value -> new SoundSettingComponent(panel, this, value);
    default -> null;
};
```

返回 `null` 的 Setting 不会渲染（例如 `StringSetSetting`）。因此**新增 Setting 子类 = 新增组件 + 补 case**，
否则模块里能看到设置值却看不到行。

各子类（构造器参数均以上表实际类型为准）：

| 组件 | 交互 |
| --- | --- |
| `BooleanSettingComponent(Panel, CloseState, Setting<Boolean>)` | 左键切换；`visibilityDependency` 时右键展开子设置；`groupOnly` 行不可点、显示 "..." |
| `NumericSettingComponent<N>(Panel, CloseState, NumberSetting<N>)` | 左键拖滑条（按 Integer/浮点取整，自动 clamp 到 min/max）；右键进入文本输入，Enter 提交、Esc 取消；字符只接受 `1234567890.,-` |
| `EnumSettingComponent<E>(Panel, CloseState, EnumSetting<E>)` | 左键切到下一个值；右键展开/收起选项列表（组件内部会调用 `panel.updateLayout()`） |
| `StringSettingComponent(Panel, CloseState, Setting<String>)` | 左键进入编辑，Enter 提交、Esc 取消、Ctrl+V 粘贴；`getText()`、`beginEditing()`（搜索框用）、`getCursorMarker()` |
| `ColorSettingComponent(Panel, CloseState, Setting<Color>)` | 右键点击标题行展开/收起 HSV+Alpha 取色器（左键拖色块/色相/透明度）；自带 Copy/Paste/Sync/Rainbow 控件（主题色不建 Sync）；实现 `CloseState`；`isHeaderHovered(...)`、`isClosed()` |
| `RegistrySetSettingComponent<E>(Panel, ModuleComponent, RegistrySetSetting<E>)` | 显示 `名称 [已选数量]`；左/右键打开 `RegistrySetScreen`；构造需要 `ModuleComponent` 当 CloseState |
| `SoundSettingComponent(Panel, CloseState, Setting<NamedIdentifier>)` | 显示 `setting.getValue().getName()`；左键打开 `SoundSelectionScreen(setting, getClickGui())` |
| `KeybindComponent(int index, ModuleComponent moduleRow[, Setting<Keybind>])` | 左键进入监听，再按任意键或鼠标键完成绑定；Esc/Delete 清除；右键循环 `TOGGLE / HOLD / HOLD_REVERSE` |

在**自定义 Panel** 里手动放设置行时传 `CloseState.ALWAYS_OPEN`，真实例子见
`TradeEditorPanel.java:48-49`、`PresetPanel.java:84-85`：

```java
this.addChild(new NumericSettingComponent<>(this, CloseState.ALWAYS_OPEN, this.maxPriceSetting));
this.addChild(new BooleanSettingComponent(this, CloseState.ALWAYS_OPEN, this.clearEnchantsSetting));
```

## 5. 面板 Panel

源码：`src/main/java/evernight/client/gui/panels/`（Panel.java、ModuleCategoryPanel.java）

`Panel`（Panel.java:28）实现 `GuiElement` + `NamedEntry` + `RegistryContainer<GuiElement>`。
构造器 `Panel(String name)`：`name` 是稳定英文名，标题由 `getTitle()` 走 `Translations.panel(name)` 实时解析。
默认宽 `92 + UIModule.styleSetting 的 widthPadding`，默认位置 (5,5)，右击标题栏折叠。

常用 public API：`getX()/getY()/setX()/setY()`、`getWidth()/getHeight()`、`getHeaderHeight()`、
`getChildren()`（raw `ArrayList`）、`children` 公共字段、`addChild(GuiElement)`、`addChildren(GuiElement...)`、
`registerChild(GuiElement)`、`updateLayout()`（按 children 高度重排 yOffset，Panel.render 每帧末尾自动调用）、
`setExpanded(boolean)/isFullyExpanded()`、`disableHeightLimit()`、`isMouseOverHeader(...)`。
`render` 内部会推 scissor、只渲染落在可视高度的子元素，并以 `mouseScrolled` 的 `amount * 20` 滚动内容。

- `ModuleCategoryPanel(ModuleCategory category)`：构造时把该分类模块按 `getName()` 排序后生成
  `ModuleComponent`；`isChildVisible` 用 ClickGUI 搜索框过滤（匹配 aliases 与显示名）。

给屏幕加面板（摘自 `screens/TradeManagerScreen.java:17-21`）：

```java
public TradeManagerScreen() {
    this.tradeListPanel.updateLayout();
    this.getPanels().add(this.tradeListPanel);
    this.getPanels().add(this.actionPanel);
}
```

## 6. 屏幕 Screen

源码：`src/main/java/evernight/client/gui/screens/`（ClientScreen.java 及全部子类）、
`src/main/java/evernight/client/gui/registry/RegistrySetScreen.java`

`ClientScreen`（ClientScreen.java:43）是所有自绘界面的基类（`extends Screen`，`isPauseScreen()` 返回 false，
`renderBackground` 为空）。子类要做的只有三件事：

1. 在构造器里往 `protected ArrayList<Panel> panels` 加面板（用 `this.panels.add(...)` 或 `getPanels().add(...)`）；
2. 需要额外绘制时覆写 `renderContents(GuiGraphics, PoseStack, int mouseX, int mouseY, float partialTick)`；
3. 用 `new XxxScreen(...).open()` 打开。

| public API | 作用 |
| --- | --- |
| `open()` | 设父屏并 `mc.setScreen(this)`；若当前屏也是 ClientScreen 则继承它的父屏并跳过开场动画（ClientScreen.java:387-396） |
| `closeWithTransition()` | 启动 150ms 关闭动画；`tick()` 结束后回到 parentScreen 或 `onClose()`。**不是**直接 setScreen |
| `getPanels()` | 面板列表；`getParentScreen()/setParentScreen(Screen)` |
| `setTooltipText(String)` | 屏幕在 render 末尾统一画 tooltip（带模糊底），每帧开头清空 |
| `suppressEscapeClose()` | 本帧按 Esc 不关闭（文本编辑/按键监听时用，见 StringSettingComponent） |
| `markHovered()` | 刷新 tooltip 存活时间；组件 hover 时由 AbstractComponent 自动调用 |
| `resetPanelPositions()` | 面板横向重排（UIModule 的 reset 设置用） |
| `getGuiScale()` / `hasMenuParent()` / `isClosing()` / `getTransitionOpacity()` | 缩放、父屏是否为标题/断连/多人菜单、关闭动画状态 |
| `onMouseClick(double,double,int)` / `onMouseRelease(...)` | 面板处理完后的钩子，默认空 |
| `renderBackgroundOverlay(PoseStack)` | 背景 tint/渐变，子类可覆写 |

`public static CursorType cursor`（ClientScreen.java:56）是屏幕级光标寄存器；`onClose()` 会恢复 STANDARD。

全部 Screen：

| 类 | 用途 | 构造器 | 关键 public 方法 / 打开方式 |
| --- | --- | --- | --- |
| `ClickGuiScreen` | ClickGUI 主界面：分类面板 + 搜索 + 模块预设粘贴 | `ClickGuiScreen()` | `getSearchBox()`、`getEditingSetting()/setEditingSetting(SettingComponent<?>)`、公共字段 `searchBox`/`openModuleComponents`；用 `ClientContext.getClickGui().open()` |
| `HudEditorScreen` | HUD 编辑器：拖动/吸附 HUD 组件、边距、HUD 模块面板 | `HudEditorScreen()` | `addWidget(HudAnchor, HudWidgetRenderer)`、`addWidget(HudWidgetRenderer)`、`removeWidget(...)`、`getAnchorGroup(HudAnchor)`、`getAnchorGroups()`、`requestDirectionRefresh()`、`renderWidgets(...)`；静态 `hoverLabel`；用 `ClientContext.getHudEditor()` + `setScreen` |
| `PresetScreen` | 预设保存/加载/删除 | `PresetScreen()` | `getListPanel()`、`getPresetPanel()`、`getSelectedCategory()/setSelectedCategory(PresetCategory)`；拖入 .json 转 `PresetImportScreen` |
| `RegistrySetScreen<E>` | 可搜索的集合设置编辑器 | `RegistrySetScreen(Screen parentScreen, RegistrySetSetting<E> registrySetting)` | `open()`；详见第 7 节 |
| `SoundSelectionScreen` | 音效选择 | `SoundSelectionScreen(Setting<NamedIdentifier> selectedSetting, Screen parentScreen)` | `getSelectedSetting()`；`closeWithTransition()` 回父屏；拖入 .ogg 复制到 custom 目录 |
| `ShulkerPreviewScreen` | 潜影盒预览（`extends ShulkerBoxScreen`） | `ShulkerPreviewScreen(ShulkerBoxMenu menu, Inventory inventory, Component title, Screen parentScreen)` | `rejectMouseClick(...)`、静态 `isRenderingPreview()`；Esc 回父屏；用 `setScreen(...)` |
| `TradeManagerScreen` | 交易列表/创建管理 | `TradeManagerScreen()` | `requestPanelChange(TradePanelRequest)`（`forCreate()` / `forEdit(trade)`） |
| `TradeEditorScreen` | 新建/编辑一条交易 | `TradeEditorScreen(String tradeName)` / `TradeEditorScreen(TradeDefinition trade)` | `setTooltipStack(ItemStack)`；先 `setParentScreen(managerScreen)` 再 `setScreen` |
| `ViewModelEditorScreen` | 第一人称手部模型编辑器 | `ViewModelEditorScreen(ViewModelModule module)` | `getEditMode()/setEditMode(ViewModelEditMode)`（NONE/TRANSLATE/ROTATE/SCALE） |
| `ConfirmGameCloseScreen` | 关游戏确认（`extends ConfirmScreen`） | `ConfirmGameCloseScreen()` | 由 MinecraftMixin 拦截关窗时 `setScreen` |
| `DisconnectConfirmationScreen` | 断连确认（`extends ConfirmScreen`） | `DisconnectConfirmationScreen(Runnable confirmationAction)` | 确认后执行 Runnable，取消则无操作；PauseScreenMixin 用法 |
| `PresetImportScreen` | 拖入预设文件的导入确认 | `PresetImportScreen(Screen previousScreen, Path sourcePath)` | 静态 `openForDroppedFiles(Screen, List)`（仅单个 .json 返回 true） |
| `ModuleImportConfirmationScreen` | 粘贴模块预设的导入确认 | `ModuleImportConfirmationScreen(Screen previousScreen, Module targetModule, JsonObject importedJson)` | Enter 直接导入；ClickGuiScreen 的 Ctrl+V 用它 |

几个必须知道的细节：

- `ClickGuiScreen` 搜索框 Ctrl+F 聚焦、Enter 定位并展开模块、Ctrl+V 解析剪贴板模块预设；
  `openModuleComponents` 最多保留 3 个展开的模块，超出会自动收起最早的。
- `HudEditorScreen` 的模块面板是 `HudModulePanel`，居中显示；左右键在 HUD 组件上分别触发拖动/展开设置行。
  它由 `HUDModule` 的 `HudEditor` 设置回调打开（HUDModule.java:306-318），不要自己 new。
- `PresetScreen` / `SoundSelectionScreen` / `ClickGuiScreen` 覆写 `onFilesDrop(List)` 处理拖放；
  `ClientScreen` 不拦截拖放。
- `TradeManagerScreen.requestPanelChange` 只是登记请求，真正换面板发生在下一次 `renderContents`。
- `ShulkerPreviewScreen.keyPressed` 用 Esc 直接 `setScreen(parentScreen)`，不走关闭动画。

## 7. 集合编辑器 Registry

源码：`src/main/java/evernight/client/gui/registry/`（`RegistrySetScreen.java`、
`RegistryEntryCatalog.java`、`RegistryEntryComponent.java`、`RegistryListPanel.java`）

`RegistrySetSetting<E>(String name, Registry<E> entryRegistry, E... defaultEntries)`
（`settings/RegistrySetSetting.java:20`，另有带 visibility 谓词的重载）是"从注册表里多选条目"的设置类型；
`getRegistry()` 取注册表、`modifyEntries(Consumer<Set<E>>)` 批量改并触发回调。

在设置行里打开它（原文，RegistrySetSettingComponent.java:49）：

```java
new RegistrySetScreen<>(ClientAccess.evernightMinecraft.screen, this.registrySetting).open();
```

`RegistrySetScreen<E>` 构造时建三个列表面板：

- **Available**：显示尚未选中的注册项（左键 `setting.addEntry(entry)`）；只有这一列受搜索框过滤。
- **Added**：显示已选中的注册项（左键 `setting.removeEntry(entry)`）。
- **Extra**：`StringSettingComponent` 搜索框（Ctrl+F 聚焦，`beginEditing()`）+ "Clear" 按钮清空集合。

`RegistryEntryCatalog` 是**包私有**类（RegistryEntryCatalog.java:21，外部不可调用），
只给 `BLOCK` / `ITEM` / `ENTITY_TYPE` 提供一份精选排序的候选清单（`entriesFor(Registry<?>)` →
`EntryList`，含 `orderedEntries()` / `contains(Object)`）；其它注册表返回空清单，此时所有注册项按注册表迭代顺序追加。
选中状态与配置的序列化由 `SetSetting` / `RegistrySetSetting` 负责，界面只是调用 `addEntry/removeEntry`。

## 8. 打开界面的标准写法

源码：全仓库 `grep "setScreen("` 的真实调用点；核心实现在
`src/main/java/evernight/client/gui/screens/core/ClientScreen.java:387-409`、
`src/main/java/evernight/client/ClientContext.java:101-113`、
`src/main/java/evernight/client/managers/task/TaskScheduler.java:46-50`

规则只有一条：**ClientScreen 子类优先用 `.open()`**，它负责父子屏关系与关闭动画返回；
需要指定返回目标时再手动 `setParentScreen` + `mc.setScreen(...)`。

```java
// 1) 常规：只开界面，关闭时回上一屏（SettingsModule.java:42）
(new PresetScreen()).open();

// 2) 单例界面：ClickGUI / HUD 编辑器由 ClientContext 缓存，不要 new
ClientContext.getClickGui().open();                       // TitleScreenMixin.java:51
Minecraft minecraft = ClientAccess.evernightMinecraft;    // HUDModule.java:313-315
HudEditorScreen editor = ClientContext.getHudEditor();
minecraft.setScreen(editor);

// 3) 需要显式父屏（交易编辑：关闭后回交易管理屏，TradeActionsPanel.java:42-44）
TradeEditorScreen editorScreen = new TradeEditorScreen(registeredTrade);
editorScreen.setParentScreen(this.managerScreen);
ClientAccess.evernightMinecraft.setScreen(editorScreen);

// 4) 显示模式的编辑器（ViewModelModule.java:136-138，原文）
Minecraft minecraft = ClientAccess.evernightMinecraft;
ViewModelEditorScreen screen = new ViewModelEditorScreen(this);
minecraft.setScreen(screen);
```

- 不在渲染线程/需要等下一个 tick 时，用 `ClientContext.taskScheduler.scheduleScreenChange(screen)`
  （内部 `scheduleRunTickTask` → `setScreen`）或 `scheduleRotationTask(runnable, 0)`；
  `TradeCommand` 打开 `TradeManagerScreen` 就是走 `scheduleScreenChange`。
- 开界面前检查世界状态：`ClientAccess` 的 `isWorldUnavailable()`（player/level 为空）；
  HUD 编辑器打开前 HUDModule 就做了这个判断。
- `ClientScreen.keyPressed` 处理 Esc：先广播给所有面板，未被 `suppressEscapeClose()` 取消才
  `closeWithTransition()`。所以自定义屏幕里要阻止 Esc 关闭，调 `suppressEscapeClose()` 即可。

## 9. 通知与确认框

源码：`src/main/java/evernight/client/modules/hud/HudNotification.java`、
`src/main/java/evernight/client/modules/client/Notifications/NotificationsModule.java`、
`src/main/java/evernight/client/utils/chat/ChatUtils.java`、
`src/main/java/evernight/client/gui/screens/core/ConfirmGameCloseScreen.java`、
`src/main/java/evernight/client/gui/screens/core/DisconnectConfirmationScreen.java`、
`src/main/java/evernight/client/gui/screens/preset/ModuleImportConfirmationScreen.java`

**聊天反馈（最常用）**：`ChatUtils.printChat(Component, int id)`、
`ChatUtils.printChat(Component, MessageSignature, MessagePriority)`、
`ChatUtils.moduleSignature(Module)`、`ChatUtils.createSignature(int)`、`ChatUtils.sendChatOrCommand(String)`：

```java
MessageSignature signature = ChatUtils.createSignature(signMessageId);
ChatUtils.printChat(notification, signature);            // VisualRangeModule.java:261-262
```

`NotificationsModule` 只是"模块开关/图腾/指令"的聊天播报模块，可用的公开入口只有
`shouldNotifyCommands(NotificationsModule)`、`getBracketColor()`、`getWatermarkColor()`（另有两个 public 颜色字段
`watermarkSetting`/`bracketsSetting`）；**没有**"发一条通知"的公共方法。

**HUD 通知**：`HudNotification(String text, long durationMillis, Color color)` 是数据对象
（`getText()`、`getColor()`、`getStartTimeMillis()`、`getEndTimeMillis()`、可覆写 `update()`），
`LagNotification`、`MusicNotification`、`GreetingNotification` 都继承它。运行时通知列表由
`WelcomerModule` 私有维护并渲染（`new HudNotification(...)` 后加入），**当前没有公共推送入口**；
外部只需读取/扩展类型，不要试图从别的模块往该列表塞对象。

**确认框**：直接复用原版 `ConfirmScreen(BooleanConsumer, Component, Component)` 并 `setScreen`（改写自 PanicCommand.java:84-94）：

```java
ConfirmScreen confirmationScreen = new ConfirmScreen((BooleanConsumer) (confirmed -> {
    if (confirmed) {
        this.unload();
    }
    ClientAccess.evernightMinecraft.setScreen(null);
}),
        Component.literal(Translations.ui("commands.Panic.ConfirmTitle", "Unload the client.")),
        Component.literal(Translations.ui("commands.Panic.ConfirmContinue", "Continue?"))
);
ClientAccess.evernightMinecraft.setScreen(confirmationScreen);
```

现成的两个封装：`new DisconnectConfirmationScreen(() -> { ... })`（断连）、`new ConfirmGameCloseScreen()`
（关游戏）；界面内的确认用 `ModuleImportConfirmationScreen(...)`（Enter 导入、Cancel 返回）或
`PresetImportScreen(...)`。组件里需要"再点一次才生效"的轻量确认（如删除交易）现有实现用计时器，
见 `TradeActionsPanel.deleteIfConfirmed`，不弹新屏。

## 常见任务速查

- 想在 ClickGUI 里给模块加一行设置 → 模块构造器里 `this.add(new BooleanSetting/NumberSetting/EnumSetting/StringSetting/ColorSetting(...))`，`ModuleComponent` 自动建行，不用写 UI 代码。
- 想新增一种设置类型并在 GUI 里显示 → 写新的 `SettingComponent` 子类，并在 `ModuleComponent#createSettingComponent` 的 switch 里加 case（否则返回 null 不渲染）。
- 想让设置行可折叠/依赖其它设置 → `markVisibilityDependency()` + `dependsOn(...)`；右键展开子设置。
- 想给设置行加 tooltip → 两份语言表加 `evernight.descriptions.settings.<ModuleConfigName>.<SettingConfigName>`，由 `Translations.settingDescription` 读取。
- 想新增一个完整界面 → `extends ClientScreen`，构造器里往 `this.panels` 加 Panel，需要自绘时覆写 `renderContents`，用 `new MyScreen().open()` 打开。
- 想新增一个面板 → `extends Panel`，构造器 `super("StableName")`，用 `registerChild/addChild` 塞组件，标题走语言表 `evernight.names.panels.<Name>`。
- 想加一个可点击按钮 → `new ActionButtonComponent(parentPanel, "Label", () -> {...})` 后 `panel.addChild(...)`；需要动态文案用 `Supplier<String>` 重载。
- 想让一行模块开关出现在分类面板里 → 交给 `ModuleCategoryPanel` 自动生成，不要手写 `new ModuleComponent`。
- 想响应 hover 改光标 → 组件 `render` 里 `ClientScreen.cursor = CursorType.POINTER;`（INPUT 用于输入框）。
- 想给组件加 hover 提示文字 → `ClientContext.getClickGui().setTooltipText(text)`（ModuleComponent 就这么做）。
- 想让自建组件跟着面板滚动 → 重写 `setYOffset(int)` 存偏移，命中判定用 `panel.getY() + yOffset`（或直接继承 `AbstractComponent`）。
- 想裁剪列表/滚动内容 → `GuiDrawUtils.flush(); ScissorStack.push(x, y, w, h); ...; GuiDrawUtils.flush(); ScissorStack.pop();`。
- 想在文本编辑时屏蔽 Esc 关闭 → `ClientContext.getClickGui().suppressEscapeClose()`。
- 想打开 ClickGUI → `ClientContext.getClickGui().open();`（单例，别 new）。
- 想打开 HUD 编辑器 → `ClientAccess.evernightMinecraft.setScreen(ClientContext.getHudEditor());`（先判 `isWorldUnavailable()`）。
- 想打开预设界面 → `(new PresetScreen()).open();`
- 想打开集合设置编辑器 → `new RegistrySetScreen<>(mc.screen, registrySetSetting).open();`
- 想打开音效选择 → `new SoundSelectionScreen(identifierSetting, ClientContext.getClickGui()).open();`
- 想打开交易管理/编辑 → `new TradeManagerScreen()` + `ClientContext.taskScheduler.scheduleScreenChange(screen)`；编辑用 `TradeEditorScreen` 并先 `setParentScreen`。
- 想打开手部模型编辑器 → `ClientAccess.evernightMinecraft.setScreen(new ViewModelEditorScreen(viewModelModule));`
- 想在非渲染线程开界面 → `ClientContext.taskScheduler.scheduleScreenChange(screen)`。
- 想弹一个确认框 → `new ConfirmScreen(confirmed -> {...}, title, message)` + `mc.setScreen(...)`；断连用 `DisconnectConfirmationScreen`。
- 想给玩家一条聊天反馈 → `ChatUtils.printChat(Component, id)` 或 `ChatUtils.printChat(component, signature)`，文本走语言表。
- 想在关闭界面前保存配置 → 覆写 `closeWithTransition()`：先 `super.closeWithTransition()`，再 `ClientAccess.executorService.submit(() -> ClientContext.configurationManager.save())`（ClickGuiScreen.java:318-322 的写法）。
