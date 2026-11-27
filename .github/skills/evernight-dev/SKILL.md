---
name: evernight-dev
description: Evernight（Minecraft 1.21.11 Fabric 客户端）内部开发速查。新增/修改模块、设置、HUD 组件、ClickGUI 界面或组件、事件订阅、指令、渲染绘制、数据包、配置/预设，或要动 en_us.json / zh_cn.json 语言表时先读本 skill：给出 client/utils、client/gui、client/render、settings、managers、events、commands 的真实 public 接口，避免猜类名与签名。
---

# Evernight 客户端开发

`AGENTS.md` 定规矩，本 skill 说具体调哪个类。冲突以 `AGENTS.md` 为准。包根 `evernight`，客户端代码在 `evernight.client`。

## 1. 参考索引

| 要做的事 | 读 | 核心类 |
| --- | --- | --- |
| 新增模块、设置、注册 | `references/module-settings.md` | `Module`、`Setting` 家族、`ModuleRegistry`、`EventBus` |
| HUD 组件与 HUD 编辑器 | `references/render.md` + `module-settings.md` | `HudWidgetModule`、`HudWidgetRenderer`、`HudAnchorGroup` |
| GUI 矩形/渐变/描边/文字/取色 | `references/render.md` | `GuiDrawUtils`、`FontRenderer`、`Catppuccin`、`ColorUtils` |
| 世界空间方框/线段 | `references/render.md` | `RenderUtils`、`WorldBoxRenderer`、`LineMeshRenderer` |
| 新界面/组件/弹出框 | `references/gui.md` | `ClientScreen`、`AbstractComponent`、`Panel`、`SettingComponent` |
| 现成工具（找方块/槽位/距离/矩阵） | `references/utils.md` | `client/utils/**` |
| 事件、转向、指令、通知 | `references/runtime.md` | `@EventHandler`、`RotationManager`、`Command`、`ChatUtils` |
| 语言表 | `references/i18n.md` | `Translations`、`LanguageTable` |

## 2. 四条规矩

1. **可见文本只存在于语言表**：Java 里只写键，`en_us.json` 与 `zh_cn.json` 键集必须一致；查找链固定为「当前语言 → 英文 → 完整键」，不要自造英文名或空串兜底。
2. **标识符绝不翻译**：`getName()` / `getConfigName()` / 枚举名 / 指令字面量 / 配置键保持英文原拼写，显示用 `getDisplayName()`。
3. **注册点唯一**：新模块进 `managers/ModuleRegistry` 构造器（字母序）；新 mixin 放 `mixin/minecraft/<域>/` 或 `mixin/mods/<模组>/`，并登记到对应 `evernight.mixins.<分类>.json`，否则静默不生效。
4. **改完必须完整构建**：任何改动完成后都跑 `gradlew.bat build`。

## 3. 代码骨架

### 3.1 模块 + 设置

```java
public final class ExampleModule extends Module {
    private final BooleanSetting strictSetting;

    public ExampleModule() {
        super("Example", "Does something.", ModuleCategory.MISC, "example", "ex");
        this.strictSetting = this.add(new BooleanSetting("Strict", false));
        this.add(new NumberSetting<>("Range", 3.0F, 1.0F, 6.0F).dependsOn(this.strictSetting));
    }
}
```

`dependsOn` / `setConfigName` 返回 `Setting<T>`：先建变量再 `this.add(...)`，别写成 `NumberSetting<Float> x = this.add(...dependsOn(...))`（类型不匹配）。
设置标签键：`evernight.names.settings.Strict`，两份 JSON 都加。依赖其它模块时在 `bootstrap/ModuleDependencies` 声明顺序。

注册（`ModuleRegistry` 构造器内按字母序）：

```java
if (!this.registerModule(new ExampleModule()))
    throw new IllegalStateException("Registration rejected ExampleModule");
```

### 3.2 事件

```java
@EventHandler                                  // 单参数、返回 void；priority 越大越先
private void onTick(LocalPlayerTickEvent event) { }
```

- 模块实例由 `Module#enable()` 自动 `subscribe`、`disable()` 反订阅——模块里不要再手动订阅。
- 非模块的静态处理器用 `ClientAccess.eventBus.subscribeStatic(Xxx.class)`。
- 可取消事件实现 `Cancellable`，用 `event.cancel(...)`，别自己维护取消标志。

### 3.3 反馈与通知

```java
ChatUtils.printChat(Component.literal(Translations.ui("modules.Example.Done")),
        ChatUtils.moduleSignature(this));   // 或用 ChatUtils.DEFAULT_SIGNATURE
```

自动加 `[Evernight]` 前缀并切主线程；消息文本走 `evernight.ui.modules.<Module>.<Key>` / `evernight.ui.commands.<Command>.<Key>`。

### 3.4 打开界面

```java
new PresetScreen().open();                  // ClientScreen 子类：带父屏关系与关闭动画
ClientContext.getClickGui().open();         // ClickGUI / HUD 编辑器是单例，不要 new
```

`ClientScreen` 提供 `open()` / `closeWithTransition()` / `setParentScreen(Screen)` / `suppressEscapeClose()`；构造参数与「下个 tick 再开」见 `references/gui.md`。

### 3.5 数据包与转向

```java
PacketSender.sendUseItemOn(hand, hitResult);
PacketSender.sendAttack(entityId);
PacketSender.sendRotation(yaw, pitch, onGround);
```

转向优先走 `RotationManager`（由 AntiCheat 模块统一处理），`PacketSender` 只用于必须绕过它的场景。

### 3.6 绘制

```java
GuiDrawUtils.fillRect(poseStack, x, y, x + width, y + height, Catppuccin.MAUVE);  // 参数是 left/top/right/bottom
FontRenderer.INSTANCE.drawWithShadow(poseStack, text, x, y, Catppuccin.TEXT);
```

绘制走批处理，需要提交时调 `GuiDrawUtils.flush()`（`TextUtils` 会处理文字批）；圆角/渐变/描边见 `references/render.md`。

### 3.7 只改本机观感的渲染覆盖

「别人看得到、自己看不到」不要动服务器同步状态，在渲染期判断；方法声明在父类就 mixin 父类：

```java
@Mixin(Avatar.class)
public abstract class AvatarMixin {
    @Inject(method = "isModelPartShown", at = @At("HEAD"), cancellable = true)
    private void suppressLocalOnly(PlayerModelPart part, CallbackInfoReturnable<Boolean> callback) {
        SomeModule module = ClientContext.getModule(SomeModule.class);
        if (module == null || !module.shouldSuppressLocal()) return;
        if ((Object) this != Minecraft.getInstance().player) return;
        callback.setReturnValue(module.getStableValue(part));
    }
}
```

`ClientContext.getModule` 在模块构造期不可靠，渲染时现取并判空，不要缓存成 `static final`。Shader pass 会二次渲染同一批对象，用 `ClientContext.shaderRegistry.renderingIntoTarget` / `renderingHands` 区分。

### 3.8 可选模组（Baritone / Xaero 等）：缺失时隐藏，桥接隔离

**缺失对应模组时，把它专属的模块和配置项从界面上隐藏，不要留一堆点不动的开关。**

- 整个模块依赖某个可选模组：在构造器调 `requiresMod("baritone", "baritone-meteor")`（任一 id 加载即算可用）。模块仍注册、配置保留，但 ClickGUI、搜索、模块名补全与 `;<alias>` 都会隐藏，`enable()` 拒绝启用。
- 只有个别配置项依赖：用 `Setting#hideWithoutBaritone()` 或 `hideUnlessModLoaded("xaerominimap")`。
- 当前映射：缺 Baritone 隐藏模块 `Baritone`、`AutoMining`、`AutoSand`、`ObstaclePasser`，以及 `PearlBot.Return` 等单项；`AutoTransport`、`PearlBot` 保留（近距离仍可用）。缺 Xaero 隐藏 `StashFinder.Xaero`、`WaypointSettings.WaypointSync` 与 `CoordinateHider` 的 Minimap 组。
- 新增可选模组支持时，先按上面的隐藏规则接 `requiresMod` / `hideUnlessModLoaded`，再补桥接与 mixin。

**实现上，模块的字段类型、方法签名不要直接引用可选模组的类。** `ModuleRegistry` 注册模块时会经 `ExperimentalFeatures` 反射 `getDeclaredFields()`，解析字段类型会加载该类型；字段类型若依赖可选模组，未安装时启动即 `NoClassDefFoundError`（典型：`EntityControlModule.pathfinder` 声明成 `EntityControlPathfinder`，而它 `implements AbstractGameEventListener`）。

统一走现成桥接：`ClientContext.baritoneBridge`（`BaritoneBridge` + `BaritoneAdapter` / `DisabledBaritoneBridge`）、`ClientContext.waypointAdapter`（`WaypointAdapter` + `XaeroWaypointAdapter` / `DisabledWaypointAdapter`）。

- 模块只持有桥接口或与可选模组无关的抽象；真实实现只在 `isBaritoneLoaded()` / `isAvailable()` 为真时构造，空实现负责无害的默认值。
- 可选模组类只允许出现在桥的实现类里（`BaritoneAdapter`、`XaeroWaypointAdapter`），不要出现在模块的字段类型、`implements` 列表或 `@EventHandler` 签名里。
- 运行时判断一律用 `ClientContext.baritoneBridge.isAvailable()`；`@Pseudo` mixin 的 `evernight.mixins.<模组>.json` 只负责可选注入，不能替代上面的字段隔离。

细节与崩溃堆栈见 `references/module-settings.md` §6.1。

## 4. 校验与提交

```bash
gradlew.bat build        # 任何改动完成后都完整构建；自动跑单元测试
gradlew.bat test         # 只跑单元测试，需要单独验证时使用
gradlew.bat releaseTest  # 发版门槛；build 已包含测试
```

`.github/workflows/release.yml` 只从 tag 构建并发布 GitHub Release；`build` 会自动跑单元测试。

编码与排版：UTF-8（无 BOM）、LF 行尾、空格缩进（Java 4 空格，JSON/YAML 2 空格；`.bat` 检出为 CRLF），由根目录 `.gitattributes` 与 `.editorconfig` 约束；不要提交 CRLF、tab 或 BOM。

提交格式 `type(scope): 中文简述`，如 `feat(hud): 新增坐标显示组件`；代码与对应语言表改动放同一提交。

**AI 的验证边界**：只跑完整构建，不启动游戏、不声称验证过界面效果，游戏内行为由人验收。Gradle 需写 `~/.gradle`，受限沙箱里被拒时按提示申请一次工作区外写权限。
