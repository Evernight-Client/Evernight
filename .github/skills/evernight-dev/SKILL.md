---
name: evernight-dev
description: Evernight（Minecraft 1.21.11 Fabric 客户端）内部 API 速查与开发约定。每当要在本仓库新增或修改模块、设置、HUD 组件、ClickGUI 界面/组件、事件订阅、指令、渲染绘制、数据包发送、配置/预设，或需要查"该调哪个工具类/界面入口"，或要动 en_us.json / zh_cn.json 语言表时，先读本 skill——它列出 client/utils、client/gui、client/render、settings、managers、events、commands 的真实 public 接口，避免凭空猜类名与签名。
---

# Evernight 客户端开发

面向本仓库的编码代理手册：
**这里写"具体调哪个类哪个方法"，`AGENTS.md` 写"必须遵守什么"**。两者冲突时以 `AGENTS.md` 的规则为准。

## 目录

- [1. 先读哪份参考](#1-先读哪份参考)
- [2. 四条不可违反的规矩](#2-四条不可违反的规矩)
- [3. 代码骨架](#3-代码骨架)
- [4. 校验与提交](#4-校验与提交)

## 1. 先读哪份参考

| 要做的事 | 读 | 核心类 |
| --- | --- | --- |
| 新增模块、加设置、加枚举、注册 | `references/module-settings.md` | `Module`、`Setting` 家族、`ModuleRegistry`、`EventBus` |
| 改 HUD 组件、HUD 编辑器 | `references/render.md` + `module-settings.md` | `HudWidgetModule`、`HudWidgetRenderer`、`HudAnchorGroup` |
| 画 GUI 矩形/渐变/描边/文字、取色板 | `references/render.md` | `GuiDrawUtils`、`FontRenderer`、`Catppuccin`、`ColorUtils` |
| 画世界里的方框/线段 | `references/render.md` | `RenderUtils`、`WorldBoxRenderer`、`LineMeshRenderer` |
| 新界面 / 新组件 / 弹出框 | `references/gui.md` | `ClientScreen`、`AbstractComponent`、`Panel`、`SettingComponent` |
| 用现成工具（找方块、找槽位、算距离、发包、矩阵） | `references/utils.md` | `client/utils/**` |
| 订阅事件、请求转向、加指令、发通知 | `references/runtime.md` | `@EventHandler`、`RotationManager`、`Command`、`ChatUtils` |
| 动语言表 / 加玩家可见文本 | `references/i18n.md` | `Translations`、`LanguageTable` |

所有路径都相对仓库根，包根为 `evernight`，客户端代码在 `.client` 下。

## 2. 四条不可违反的规矩

1. **玩家可见文本必须走语言表**：`Translations.*` 或 `NamedEntry#getDisplayName()`。
   新增/改名模块、设置、枚举、插件分类时，Java 里只写语言键，不写任何显示文本；`en_us.json` 与 `zh_cn.json` 两份都要加键，键集合必须一致。查找顺序固定为「当前语言 → 英文 → 完整键」：中文缺键回退英文，两份都缺才显示完整键，不要自造英文名或空串兜底。
2. **标识符绝不翻译**：`getName()` / `getConfigName()` / 枚举 `getName()` / 指令字面量 / 配置 JSON 键
   保持英文原拼写。显示用 `getDisplayName()`。
3. **注册点唯一**：新模块进 `managers/ModuleRegistry` 构造器（字母序）；新 mixin 放进
   `evernight/mixin/minecraft/<功能域>/` 或 `evernight/mixin/mods/<模组>/` 子包，并登记到对应的
   `src/main/resources/evernight.mixins.<分类>.json`；否则静默不生效。
4. **改完必须编译**：只改 Java 可 `gradlew.bat compileJava`；动到资源/语言表必须
   `gradlew.bat build` 全量，并跑 `python .github/skills/evernight-dev/scripts/check-lang.py`。

## 3. 代码骨架

### 3.1 新增模块 + 设置

```java
package evernight.client.modules.misc;

import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.BooleanSetting;
import evernight.client.settings.NumberSetting;

public final class ExampleModule extends Module {
    private final BooleanSetting strictSetting;

    public ExampleModule() {
        super("Example", "Does something.", ModuleCategory.MISC, "example", "ex");
        this.strictSetting = this.add(new BooleanSetting("Strict", false));
        NumberSetting<Float> rangeSetting = new NumberSetting<>("Range", 3.0F, 1.0F, 6.0F);
        this.add(rangeSetting.dependsOn(this.strictSetting)); // 仅 Strict 开启时显示
    }
}
```

注意 `dependsOn` / `setConfigName` 等链式方法返回 `Setting<T>`，所以先建变量再 `this.add(...)`，
不要写成 `NumberSetting<Float> x = this.add(new NumberSetting<>(...).dependsOn(...))`（类型不匹配）。

注册（`managers/ModuleRegistry` 构造器内，按字母序插入）：

```java
if (!this.registerModule(new evernight.client.modules.misc.ExampleModule()))
    throw new IllegalStateException("Registration rejected ExampleModule");
```

设置 `"Strict"` / `"Range"` 的标签键：`evernight.names.settings.Strict`、`evernight.names.settings.Range`（两份 JSON 都加）。
依赖其它模块时在 `bootstrap/ModuleDependencies` 里声明顺序。

### 3.2 订阅事件

```java
@EventHandler                                  // 单参数、返回 void；priority 越大越先
private void onTick(LocalPlayerTickEvent event) {
    // 行为逻辑
}
```

- 实例订阅由 `Module#enable()` 自动完成（`eventBus.subscribe(this)`），`disable()` 反订阅——
  所以模块里**不要**手动 subscribe。
- 非模块的静态处理器用 `ClientAccess.eventBus.subscribeStatic(Xxx.class)`。
- 可取消事件实现 `Cancellable`，用 `event.cancel(...)`；不要自己维护取消标志。

### 3.3 给玩家一条反馈 / 一条通知

```java
ChatUtils.printChat(
        Component.literal(Translations.ui("modules.Example.Done")),
        ChatUtils.moduleSignature(this));   // 也用 ChatUtils.DEFAULT_SIGNATURE
```

- `ChatUtils.printChat(Component, MessageSignature)`：发到聊天栏，自动加 `[Evernight]` 前缀并切主线程。
- 需要前缀/水印：`ChatUtils.createPrefix()`、`ChatUtils.watermarkText(text)`。
- 消息文本走 `evernight.ui.modules.<Module>.<Key>` / `evernight.ui.commands.<Command>.<Key>`，不要硬编码。

### 3.4 打开一个界面

```java
new PresetScreen().open();                       // ClientScreen 子类：带父屏关系与关闭动画
ClientContext.getClickGui().open();              // ClickGUI / HUD 编辑器是单例，不要 new
```

`ClientScreen` 提供 `open()` / `closeWithTransition()` / `setParentScreen(Screen)` / `suppressEscapeClose()`。
界面构造参数、单例缓存与"下个 tick 再开"的写法见 `references/gui.md` 第 8 节。

### 3.5 发数据包 / 请求转向

```java
PacketSender.sendUseItemOn(hand, hitResult);       // 右键方块
PacketSender.sendAttack(entityId);                  // 攻击
PacketSender.sendRotation(yaw, pitch, onGround);    // 只发转向包
```

转向优先走 `RotationManager`（会被 AntiCheat 模块统一处理），`PacketSender` 只用于必须绕过它的场景。
完整列表见 `references/runtime.md`。

### 3.6 画点东西

```java
// 注意参数是 left / top / right / bottom，不是宽高
GuiDrawUtils.fillRect(poseStack, x, y, x + width, y + height, Catppuccin.MAUVE);
FontRenderer.INSTANCE.drawWithShadow(poseStack, text, x, y, Catppuccin.TEXT);
```

绘制走批处理，同一段绘制结束需要提交时调用 `GuiDrawUtils.flush()`（`TextUtils` 会处理文字批）。
完整签名与圆角/渐变/描边等变体见 `references/render.md`。

### 3.7 只改本机观感的渲染覆盖

「别人看得到、自己看不到」这类需求不要动服务器同步状态，在渲染期判断即可（示例：`SkinFlickerModule` 的 Visibility）。
方法声明在父类就 mixin 父类，再判断本机实例：

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

`ClientContext.getModule` 在模块构造期间不可靠，所以要在渲染时现取并判空，不要缓存成 `static final`。
Shader 模块会二次渲染同一批对象：用 `ClientContext.shaderRegistry.renderingIntoTarget` / `renderingHands`
区分主渲染与着色器 pass，完整说明见 `references/render.md` 第 10 节。

## 4. 校验与提交

```bash
python .github/skills/evernight-dev/scripts/check-lang.py   # 语言表：键集合/空值/占位符，0 error 才行
gradlew.bat compileJava                                 # 只改 Java 时的最小编译验证
gradlew.bat build                                       # 改资源/语言表时必须全量
```

提交格式 `type(scope): 中文简述`，例如 `feat(hud): 新增坐标显示组件`、`fix(i18n): 补全漏翻的设置标签`。
代码与对应语言表改动放进同一个提交。提交前对照 `AGENTS.md` 第 9 节的检查清单。

**AI 的验证边界**：只跑上面三条命令（构建 + 静态校验脚本），不要启动游戏、不要声称验证过界面效果。
游戏内行为、中英文界面观感、交互手感由人验收，AI 不要自行下结论。
另外注意：Gradle 需要写 `~/.gradle`，在受限沙箱里可能被拒绝，此时按提示申请一次工作区外写权限。

## 5. 参考文件索引

| 文件 | 内容 |
| --- | --- |
| `references/module-settings.md` | `Module` 生命周期、全部设置类型、`NamedEntry`、注册表、`EventBus` 机制、配置与预设 |
| `references/gui.md` | `GuiElement`/`ScissorStack`、组件生命周期、`SettingComponent` 家族、panels、全部 Screen、`gui/registry` |
| `references/render.md` | `GuiDrawUtils`/`RenderUtils`/`TextUtils`、字体、`Catppuccin`/`ColorUtils`、HUD 渲染器、世界空间渲染、着色器入口 |
| `references/utils.md` | `client/utils` 全量速查（world / inventory / entity / combat / movement / network / math / chat） |
| `references/runtime.md` | `ClientAccess`/`ClientContext`、事件目录、指令体系、管理器、发包策略、通知 |
| `references/i18n.md` | 语言表解析规则、键空间、显示/标识分离、常见错误 |
| `scripts/check-lang.py` | 语言表校验脚本（复刻 `LanguageTable` 解析规则） |
