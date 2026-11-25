---
name: evernight-addon-dev
description: 开发 Evernight 插件（Addon）的完整工作流：evernight-addon 入口、AddonContext 注册模块/指令/配置/语言、依赖发行 jar 构建与联调、排错。触发词：Evernight 插件、Addon、evernight-addon、EvernightAddon、AddonContext、插件系统、写插件。
---

# Evernight 插件开发

插件是**独立的 Fabric 客户端模组**，通过 `evernight-addon` 入口向本体注册模块、指令、配置与语言表；与本体分离编译、独立分发，启动时自动发现，单个插件失败不影响客户端或其他插件。

- 插件 API 源码：`src/main/java/evernight/addon/`
- 精确签名：[`references/api.md`](references/api.md)
- 完整可编译示例：[`references/example-addon.md`](references/example-addon.md)
- 工程模板：[evernight-addon-template](https://github.com/Evernight-Client/evernight-addon-template)
- 本体模块/设置/事件/渲染 API：`.github/skills/evernight-dev/references/`

## 1. 加载流程

1. 主初始化阶段构造 `ModuleRegistry`、`HudModuleRegistry`、`CommandRegistry` 等管理器。
2. `AddonManager.initialize()` 读取所有声明了 `evernight-addon` 入口的模组，按 mod id 排序，逐个 `new AddonContext` → `loadLanguageFiles()` → `onInitialize(context)`。
3. 之后 `ConfigurationManager.load()` 像读取本体模块一样恢复插件模块的状态与设置。
4. 存在插件时注册 `;addons`（alias `;addon`）；**没有任何插件时该指令完全不注册**。
5. 关游戏时先保存配置，再逆序调用 `onShutdown()` 并释放 `subscribe` 注册的监听器。

插件**不支持热卸载**（Fabric 不卸载类加载器）：把「开关行为」放进 Module，而不是依赖卸载。

## 2. 五条硬规则

1. **入口键只能是 `evernight-addon`**，入口类是 `evernight.addon.EvernightAddon` 的子类，`environment` 必须为 `client`。
2. **模块名全局唯一**：`new Module("Name", ...)` 的 `Name` 同时是配置键、预设键与指令别名来源，发布后不能改名；重名会让整个插件 `FAILED`。
3. **玩家可见文本只存在于语言表，键只用 `addons.<id>.*`**：`assets/<mod id>/lang/en_us.json` 与 `zh_cn.json` 键集一致；**不要用 `evernight.`**（本体保留命名空间）。Java 里只写键，不要自造英文显示名。
4. **只注册、不硬塞**：模块、指令、配置、语言都经 `AddonContext` 注册；不要直接改 `ModuleRegistry.entries`、`CommandDispatcher` 内部结构。
5. **初始化后不改结构**：结构注册只在 `onInitialize` 内一次性完成；运行期动态增删模块/指令不受支持（设置值与配置随时可改）。

## 3. 最小工程

`fabric.mod.json`（版本号按你实际测试过的填）：

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

`build.gradle` 依赖本体**发行 jar**（GitHub Release 的 `evernight-<version>.jar`）：

```gradle
modImplementation files("libs/evernight-<version>.jar")
```

Loom 会把这份 intermediary jar 重映射成开发用的 named。**不要**把 `build/devlibs/*-dev.jar` 交给 `mod*` 配置：它的 access widener 头是 `named`，会报 `Namespace mismatch, expected intermediary got named`。本体未发布到 Maven，用 Release jar 或本地 `build/libs/` 产物做文件依赖。与本体同一套 Loom，Gradle 需 JDK 25，`options.release = 21` 保证产物仍是 Java 21 字节码。

入口类：

```java
public final class ExampleAddon extends EvernightAddon {
    @Override
    public void onInitialize(AddonContext context) {
        ModuleCategory automation = context.registerCategory("Automation");   // 可选，需在用它之前
        context.registerModule(new ExampleModule(automation));
        context.registerCommand(new ExampleCommand());
    }

    @Override
    public void onShutdown() { /* 停线程、关连接 */ }
}
```

## 4. AddonContext 速查

| 签名 | 语义 |
| --- | --- |
| `AddonMetadata metadata()` / `String id()` | 元数据 / Fabric mod id |
| `Logger logger()` | 前缀 `Evernight/Addon/<id>` |
| `EventBus eventBus()` | 客户端事件总线 |
| `<T extends Module> T registerModule(T)` | 注册进 `ModuleRegistry`，并生成 `;<alias>` 指令 |
| `<T extends HudWidgetModule> T registerHudModule(T)` | 语义化别名，与上一行等价 |
| `ModuleCategory registerCategory(String name)` | 注册插件分类，返回对象供 `AddonModule` 构造器使用 |
| `<T extends Command> T registerCommand(T)` | 注册 Brigadier 指令（含 `getAliases()`） |
| `void registerConfig(JsonConfigurable)` | 额外配置文件 `evernight/<getConfigName()>` |
| `void subscribe(Object)` / `unsubscribe(Object)` | 注册/注销辅助对象的 `@EventHandler`，关闭时自动清理 |
| `void registerLanguage(String code, InputStream)` | 手动合并语言表（一般用不上） |
| `void loadLanguageFiles()` | 重读 `assets/<id>/lang/{en_us,zh_cn}.json`，初始化时已自动调用 |
| `Path dataDirectory()` | `evernight/addons/<id>/`，自动创建 |

注册失败抛 `IllegalStateException`（分类/模块/指令重名），`AddonManager` 记为 `FAILED` 并继续加载其他插件；**已成功的注册不回滚**，所以分类注册要在使用它的模块之前，易失败的注册尽量放在 `onInitialize` 末尾。`registerModule` 会校验模块声明的 addon id 与当前插件一致。

模块基类：`Module`（纯英文标识、不本地化）或 `AddonModule`（自动把模块名、描述、设置标签解析到 `addons.<id>.*`）。绝大多数插件用后者。

## 5. 模块、分类、指令

```java
public final class ExampleModule extends AddonModule {
    private final Setting<Boolean> announce;

    public ExampleModule(ModuleCategory category) {
        super("exampleaddon", "ExampleAddon", "Demonstrates the addon API.",
                category, "exampleaddon", "exaddon");
        this.announce = this.add(new BooleanSetting("Announce", false));
        this.add(new NumberSetting<>("Interval", 20, 1, 200).dependsOn(this.announce));
    }

    @EventHandler
    private void onTick(MinecraftTickEvent event) { /* 只在启用期间触发 */ }
}
```

- `AddonModule` 自动读取 `addons.<id>.names.modules.*`、`addons.<id>.descriptions.modules.*`、`addons.<id>.names.settings.*`；构造器的 `description` 参数仅为源码兼容保留，不再作为显示兜底。
- 分类是 final class：`COMBAT` `MISC` `RENDER` `MOVEMENT` `PLAYER` `AUTO` `EXPLOIT` `CLIENT` `HUD` 可直接复用；新分类只能 `onInitialize` 里注册（ClickGUI 首次打开才按 `ModuleCategory.values()` 建面板），名字全局唯一（含内置、大小写不敏感），空白或重名抛异常。显示名走 `addons.<id>.names.categories.<Name>`，两份 JSON 都要写。
- 设置链式方法返回 `Setting<T>`；先建变量再 `this.add(...)`，不要声明成 `NumberSetting`。
- HUD 组件继承 `HudWidgetModule`、构造器里 `setRenderer(...)`，用 `registerHudModule`。
- 指令继承 `Command`，在 `exec(LiteralArgumentBuilder root)` 里 `root.executes(action)`，别名用 `setAliases(...)`。
- 模块的 `enable()` 自动订阅、`disable()` 自动退订——**不要再** `context.subscribe(module)`，会重复触发。

## 6. 语言与配置

- 语言文件 `assets/<id>/lang/en_us.json` + `zh_cn.json`，UTF-8 扁平 JSON，键集必须一致，全部放 `addons.<id>.*`。查找链：当前语言 → 英文 → 完整键（中文缺键回退英文，两份都缺才显示完整键，方便发现漏翻）。只写 `en_us.json` 可以跑，但推荐两份都写。
- `AddonModule` 自动解析模块名/描述/设置标签；私有文案用 `Translations.get("addons.<id>.xxx")`。自定义枚举可覆写 `NamedEntry#getDisplayName()` 读自己的命名空间。
- 模块开关/按键/设置自动进 `evernight/modules.json`，HUD 进 `evernight/hud.json`，无需额外代码。
- 插件自有状态实现 `JsonConfigurable`（`toJson` / `fromJson` / `getConfigName`，必须覆写最后者）后 `context.registerConfig(...)`，文件落在 `evernight/<name>`，建议 `<id>.json` 避免与本体撞名。`ConfigurationManager` 尚未创建时该调用会被暂存，初始化后自动注册并读盘。
- 大文件或非配置数据放 `dataDirectory()`。`;reload` 会重读所有配置源，包括插件配置。

## 7. 生命周期与线程

- `onInitialize` 在主线程、配置加载之前执行：可安全访问 `ClientContext` 的注册表，但 `ClientContext.configurationManager` 还是 `null`；此时之后才注册的模块不会读到已有存档状态。
- 不要在主线做网络/磁盘重活：用 `ClientAccess.executorService` 或 `TaskScheduler` 切回主线程。
- 不要缓存构造期的 `ClientContext.getModule(...)`（可能为 null），在事件里现取并判空。
- 关闭时不要自己反注册模块；只停线程、关连接、释放监听器。

## 8. 构建、联调、排错

```bash
gradlew.bat build            # 产出 build/libs/evernight-<version>.jar（本体）
gradlew.bat compileJava      # 只改 Java
python .github/skills/evernight-dev/scripts/check-lang.py
```

联调：插件 jar 放进 `run/mods/` 后 `gradlew.bat runClient`；日志出现 `Loaded addon <name> <version> (<id>)` 即成功，失败打印 `Addon <id> failed to initialize` 与堆栈。游戏内 `;addons` 查看插件、版本与模块数；插件分类排在内置分类之后。

| 现象 | 检查 |
| --- | --- |
| 完全没有加载 | 入口键拼写、入口类是否 public 且有无参构造、`environment` 是否 client |
| `NoClassDefFoundError: evernight/...` | 环境没装对应版本本体，或 `depends.evernight` 不匹配 |
| 插件 `FAILED` + `Module name is already registered` | 模块名与本体/其他插件重名，改名 |
| ClickGUI 有模块但无法开关 | 模块构造器抛异常或未注册，看 `Addon <id> failed` 堆栈 |
| 语言不生效 | 资源路径命名空间是否等于 `fabric.mod.json` 的 id；两份 JSON 键集是否一致 |
| 设置不落盘 | 是否走 `this.add(...)`；是否 `setExcludedFromConfig(true)` |
| `Namespace mismatch` | 把 `-dev.jar` 交给了 `mod*` 配置，换发行 jar |

## 9. 版本与兼容

- 公开签名（`EvernightAddon`、`AddonContext`、`AddonMetadata`、`AddonInfo`）按语义化版本维护，破坏性改动提升次版本号；插件用 `depends.evernight` 声明兼容范围。
- 可以使用本体公开类（`ClientAccess`、`ClientContext`、事件、设置、渲染工具）；不要反射改私有状态，本体重构不保证二进制兼容。
- 需要 mixin 时在插件自己的 `fabric.mod.json` 声明自己的 mixin 配置，本体的清单不代管插件。
- 本体以 `GPL-3.0-only` 发布；链接其公开 API 的插件按 GPL-3.0 处理，不得重新分发本体代码或构建产物。

## 10. 提交前检查

1. 改了 API：`gradlew.bat compileJava` + `gradlew.bat build`。
2. 动了语言表：`check-lang.py` 必须 0 error。
3. 改了 `AddonContext` / `EvernightAddon` 公开签名：同步更新本 skill 与 `references/api.md`。
4. 不启动游戏、不声称验证过游戏内观感；构建与静态检查为止，行为由人验收。
