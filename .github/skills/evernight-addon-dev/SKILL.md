---
name: evernight-addon-dev
description: 开发 Evernight 插件（Addon）的完整工作流：evernight-addon 入口、AddonContext 注册模块/指令/配置/语言、依赖 dev jar 构建与联调、排错。触发词：Evernight 插件、Addon、evernight-addon、EvernightAddon、AddonContext、插件系统、写插件。
---

# Evernight 插件开发

面向在本仓库内新增/维护插件系统的代理。插件本体代码在 `src/main/java/evernight/addon/`，玩家向文档在 `docs/addons.md`，本 skill 记录**写一个插件时要遵守什么、调哪个 API、怎么验收**。

## 目录

- [1. 先读什么](#1-先读什么)
- [2. 五条硬规则](#2-五条硬规则)
- [3. 最小骨架](#3-最小骨架)
- [4. AddonContext 速查](#4-addoncontext-速查)
- [5. 模块 / 设置 / 事件](#5-模块--设置--事件)
- [6. 指令 / 语言 / 配置](#6-指令--语言--配置)
- [7. 构建与联调](#7-构建与联调)
- [8. 排错](#8-排错)
- [9. 提交前检查](#9-提交前检查)

## 1. 先读什么

| 要做的事 | 读 |
| --- | --- |
| 开箱即用的工程模板 | [evernight-addon-template](https://github.com/Evernight-Client/evernight-addon-template) |
| 插件工程骨架、完整示例 | `docs/addons.md`、`references/example-addon.md` |
| AddonContext / AddonManager 精确签名 | `references/api.md` |
| 模块基类、设置类型、HUD、注册表 | `.github/skills/evernight-dev/references/module-settings.md` |
| 事件目录、ClientContext、发包、通知 | `.github/skills/evernight-dev/references/runtime.md` |
| 语言表规则 | `.github/skills/evernight-dev/references/i18n.md` |
| 本体源码 | `src/main/java/evernight/client/` |

## 2. 五条硬规则

1. **入口键只能是 `evernight-addon`**，入口类是 `evernight.addon.EvernightAddon` 的子类，`environment` 必须是 `client`。
2. **模块名全局唯一**：`new Module("Name", ...)` 的 `Name` 同时是配置键、预设键、指令别名来源，发布后不能改名。重名会让整个插件初始化失败。
3. **玩家可见文本走语言表，键只用 `addons.<mod id>.*`**：`assets/<mod id>/lang/en_us.json` 与 `zh_cn.json` 键集一致；**不要用 `evernight.` 开头**，那是本体保留命名空间。新增文案/新增分类时 Java 里只写键，不要自造英文显示名；缺键按完整键显示（方便发现漏翻），两份 JSON 都要补。
4. **只注册、不硬塞**：模块、指令、配置、语言都通过 `AddonContext` 注册；不要直接操作 `ModuleRegistry.entries`、`CommandDispatcher` 内部结构。
5. **初始化后不改结构**：`onInitialize` 负责一次性注册；运行期动态增删模块/指令不受支持（配置与设置值可以随时改）。

## 3. 最小骨架

`fabric.mod.json`：

```json
{
  "schemaVersion": 1,
  "id": "exampleaddon",
  "version": "1.0.0",
  "name": "Example Addon",
  "environment": "client",
  "entrypoints": { "evernight-addon": ["com.example.exampleaddon.ExampleAddon"] },
  "depends": {
    "fabricloader": ">=0.19.5",
    "minecraft": "1.21.11",
    "java": ">=21",
    "evernight": ">=4.0.0"
  }
}
```

入口类：

```java
public final class ExampleAddon extends EvernightAddon {
    @Override
    public void onInitialize(AddonContext context) {
        context.registerModule(new ExampleModule());
        context.registerCommand(new ExampleCommand());
    }
}
```

依赖本体的发行 jar：`modImplementation files("libs/evernight-<version>.jar")`（GitHub Release 的 `evernight-<version>.jar`；Loom 会把 intermediary 重映射成开发用的 named）。**不要**把 `build/devlibs/*-dev.jar` 交给 `mod*` 配置：它的 access widener 头是 `named`，会报 `Namespace mismatch`。Gradle 需 JDK 25（Loom 1.18.4），编译目标 21。

## 4. AddonContext 速查

```java
AddonMetadata metadata()                 // id / name / version / authors / website
String id()
Logger logger()                          // 前缀 Evernight/Addon/<id>
EventBus eventBus()
<T extends Module> T registerModule(T)   // 同时生成 ;<alias> 指令
<T extends HudWidgetModule> T registerHudModule(T)
ModuleCategory registerCategory(String name) // 注册插件分类，传给 AddonModule 构造器
<T extends Command> T registerCommand(T)
void registerConfig(JsonConfigurable)    // 额外 evernight/<name>.json
void subscribe(Object) / unsubscribe(Object)
void registerLanguage(String code, InputStream)
void loadLanguageFiles()                 // 初始化时已自动调用
Path dataDirectory()                     // evernight/addons/<id>/
```

模块继承 `AddonModule` 时：`super(addonId, name, description, category, aliases...)`，语言键自动落到 `addons.<addonId>.*`。

注册失败抛 `IllegalStateException`；`AddonManager` 把该插件记为 `FAILED`，不会影响其他插件。

## 5. 模块 / 设置 / 事件

```java
public final class ExampleModule extends AddonModule {
    private final Setting<Boolean> announceSetting;
    private final int interval;

    public ExampleModule() {
        super("exampleaddon", "ExampleAddon", "Demonstrates the addon API.",
                ModuleCategory.MISC, "exampleaddon", "exaddon");
        this.announceSetting = this.add(new BooleanSetting("Announce", false));
        this.interval = 20;
    }

    @EventHandler
    private void onTick(MinecraftTickEvent event) { /* 只在启用时触发 */ }
}
```

- `AddonModule` 自动解析 `addons.<id>.names.modules.*`、`addons.<id>.descriptions.modules.*` 与 `addons.<id>.names.settings.*`；查找顺序为「当前语言 → 英文 → 完整键」，所以只写 `en_us.json` 时中文界面会显示英文，两份都缺才显示完整键。构造器的 `description` 参数仅为源码兼容保留，不再作为显示兜底；不需要本地化时请直接继承 `Module`。
- 分类：内置分类（`ModuleCategory.COMBAT` … `HUD`）可直接复用；需要新分类时在 `onInitialize` 里调用 `context.registerCategory("Automation")`，把返回的 `ModuleCategory` 传给 `AddonModule` 构造器：
  ```java
  ModuleCategory automation = context.registerCategory("Automation");
  context.registerModule(new ExampleModule(automation));
  ```
- 自定义分类的限制：只能 `onInitialize` 注册（ClickGUI 首次打开时才按 `ModuleCategory.values()` 建面板）；名字是稳定英文标识，全局唯一（含内置分类、大小写不敏感），建议只用字母数字，发布后不要改名；空白或重名抛 `IllegalStateException`，插件记为 `FAILED`。分类注册要放在使用它的模块之前。
- 分类显示名走 `addons.<id>.names.categories.<Name>`（如 `addons.exampleaddon.names.categories.Automation`），`en_us.json` 与 `zh_cn.json` 两份都要写；中文缺键回退英文，两份都缺才显示完整键，不要在 Java 里自造显示名。
- 设置链式方法返回 `Setting<T>`；先建变量再 `this.add(...)`，不要声明成 `NumberSetting`。
- HUD 组件继承 `HudWidgetModule`、构造器里 `setRenderer(...)`，再用 `registerHudModule`。
- 辅助对象订阅事件用 `context.subscribe(obj)`，关闭时自动退订；模块不要重复订阅。

## 6. 指令 / 语言 / 配置

- 指令继承 `Command`，`exec(LiteralArgumentBuilder root)` 里 `root.executes(action)`；别名用 `setAliases(...)`。
- 语言：`assets/<id>/lang/en_us.json` + `zh_cn.json`，所有键放 `addons.<id>.*`（**禁止** `evernight.` 前缀）；`AddonModule` 自动读取模块名/描述/设置标签，私有文案用 `Translations.get("addons.<id>.key")`。查找链是当前语言 → 英文 → 完整键：可以只提供 `en_us.json` 让中文界面回退英文，但推荐两份都写；两份都缺会显示完整键。
- 模块状态自动进 `modules.json`，HUD 进 `hud.json`；额外状态实现 `JsonConfigurable` 后 `context.registerConfig(...)`，文件名带插件 id。
- 关闭时不要自己反注册模块；只清理线程/连接/监听器。

## 7. 构建与联调

```bash
gradlew.bat build                       # 本体，产出 build/libs/evernight-<version>.jar
gradlew.bat compileJava                 # 只改 Java
gradlew.bat build                       # 改资源/语言表
python .github/skills/evernight-dev/scripts/check-lang.py
```

联调：插件 jar 放进 `run/mods/`，启动 `gradlew.bat runClient`；日志出现 `Loaded addon <name> <version> (<id>)`，游戏内 `;addons` 可查。没有插件时 `;addons` 不注册。

## 8. 排错

| 现象 | 检查 |
| --- | --- |
| 完全没有加载 | 入口键拼写、入口类是否 public 且无参构造、`environment` 是否 client |
| `NoClassDefFoundError: evernight/...` | 运行环境没有安装对应版本的 Evernight，或 `depends.evernight` 版本不匹配 |
| 插件 FAILED，日志有 `Module name is already registered` | 模块名重名；改名后重试 |
| ClickGUI 有模块但无法开关 | 模块构造器抛异常/未注册；看 `Addon <id> failed` 堆栈 |
| 语言不生效 | 资源路径的命名空间是否等于 `fabric.mod.json` 的 id；两份 JSON 键集是否一致 |
| 设置不落盘 | 设置是否走 `this.add(...)`；是否 `setExcludedFromConfig(true)` |

## 9. 提交前检查

1. 新增/修改插件 API 时：`gradlew.bat compileJava` + `gradlew.bat build`。
2. 动语言表：`check-lang.py` 必须 0 error。
3. 若改了 `AddonContext` / `EvernightAddon` 的公开签名，同步更新 `docs/addons.md`、本 skill 与 `references/api.md`。
4. 不启动游戏、不声称验证过游戏内观感；构建与静态检查到此为止，行为由人验收。
