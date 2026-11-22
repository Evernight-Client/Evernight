# Evernight 插件（Addon）开发指南

Evernight 的插件系统参考 Meteor Client：插件是**独立的 Fabric 客户端模组**，通过 `evernight-addon` 入口向客户端注册模块、指令、配置与语言表。插件与本体分离编译、独立分发，客户端启动时自动发现；任何一个插件初始化失败都不会阻止客户端或其他插件加载。

- 插件 API 源码：`src/main/java/evernight/addon/`
- 模块/设置/事件/渲染等本体 API：见 `.github/skills/evernight-dev/references/`
- 本文示例的完整文件：`.github/skills/evernight-addon-dev/references/example-addon.md`
- 开箱即用的工程模板：[Evernight-Client/evernight-addon-template](https://github.com/Evernight-Client/evernight-addon-template)（示例插件、语言表校验与 agent skill 一并提供）

## 1. 加载流程

1. 客户端主初始化阶段创建 `ModuleRegistry`、`HudModuleRegistry`、`CommandRegistry` 等管理器。
2. `AddonManager.initialize()` 通过 Fabric Loader 读取所有声明了 `evernight-addon` 入口的模组，按模组 id 排序后逐个调用 `EvernightAddon#onInitialize(AddonContext)`。
3. 插件注册的模块进入全局模块表，之后 `ConfigurationManager.load()` 会像读取本体模块一样读取它们的状态与设置。
4. 存在插件时注册 `;addons` 指令；**没有任何插件时该指令完全不注册**，不会出现在 `;help` 里。
5. 游戏关闭时按相反顺序调用 `onShutdown()`，并释放通过 `AddonContext#subscribe` 注册的监听器。

插件**不支持热卸载**：Fabric 的类加载器不会卸载模组，插件应把「开关行为」放进 Module，而不是依赖卸载。

## 2. 最小插件工程

### 2.1 `fabric.mod.json`

```json
{
  "schemaVersion": 1,
  "id": "exampleaddon",
  "version": "1.0.0",
  "name": "Example Addon",
  "description": "An Evernight addon example.",
  "authors": ["Your Name"],
  "environment": "client",
  "entrypoints": {
    "evernight-addon": ["com.example.exampleaddon.ExampleAddon"]
  },
  "depends": {
    "fabricloader": ">=0.19.5",
    "minecraft": "1.21.11",
    "java": ">=21",
    "fabric-api": "*",
    "evernight": ">=1.0"
  }
}
```

要点：

- `environment` 必须是 `client`。
- 入口键固定为 `evernight-addon`，值是实现 `evernight.addon.EvernightAddon` 的类。
- `id` 同时是语言资源命名空间：`assets/<id>/lang/en_us.json`。
- `depends.evernight` 按你实际测试过的版本填写，避免 API 不兼容。

### 2.2 `build.gradle`

以 Evernight 本体的 `build.gradle` / `settings.gradle` 为模板（同样的 Loom、Minecraft 与映射版本），把对本体的依赖换成发行 jar：

```gradle
dependencies {
    minecraft "com.mojang:minecraft:1.21.11"
    mappings loom.officialMojangMappings()
    modImplementation "net.fabricmc:fabric-loader:0.20.1"
    modImplementation "net.fabricmc.fabric-api:fabric-api:0.141.6+1.21.11"

    // 本体发行 jar：GitHub Release 的 evernight-<version>.jar
    modImplementation files("libs/evernight-1.0.1.jar")
}
```

`modImplementation` 声明的是 mod 依赖：Loom 会在构建时把这份 intermediary jar 重映射成开发用的 named 命名空间，插件代码因此可以直接继承 `EvernightAddon` 并使用本体 API。不要把 `build/devlibs/evernight-<version>-dev.jar` 交给 `mod*` 配置——它的 access widener 头是 `named`，Loom 会按 intermediary 处理 mod 依赖，构建时报 `Namespace mismatch, expected intermediary got named`。本体尚未发布到 Maven，用 GitHub Release 的 jar 或本地构建的 `build/libs/evernight-<version>.jar` 做文件依赖即可。

插件工程与本体使用同一套 Loom 1.18.4，因此 **Gradle 需要 JDK 25**（`options.release = 21` 保证插件仍编译为 Java 21 字节码）。

### 2.3 入口类

```java
package com.example.exampleaddon;

import evernight.addon.AddonContext;
import evernight.addon.EvernightAddon;

public final class ExampleAddon extends EvernightAddon {
    @Override
    public void onInitialize(AddonContext context) {
        // AddonManager 已经自动读取 assets/exampleaddon/lang/*.json，这里无需再调用 loadLanguageFiles()。
        context.registerModule(new ExampleModule());
        context.registerCommand(new ExampleCommand());
    }
}
```

### 2.4 模块

```java
package com.example.exampleaddon;

import evernight.addon.AddonModule;
import evernight.client.events.core.EventHandler;
import evernight.client.events.ui.MinecraftTickEvent;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.text.Translations;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;

public final class ExampleModule extends AddonModule {
    private final Setting<Boolean> announceSetting;
    private final Setting<Integer> intervalSetting;
    private int ticks;

    public ExampleModule() {
        super("exampleaddon", "ExampleAddon", "Demonstrates the addon API.",
                ModuleCategory.MISC, "exampleaddon", "exaddon");
        this.announceSetting = this.add(new BooleanSetting("Announce", false));
        this.intervalSetting = this.add(
                new NumberSetting<>("Interval", 20, 1, 200).dependsOn(this.announceSetting));
    }

    @EventHandler
    private void onTick(MinecraftTickEvent event) {
        if (!this.announceSetting.getValue()) {
            return;
        }
        if (++this.ticks < this.intervalSetting.getValue()) {
            return;
        }
        this.ticks = 0;
        ChatUtils.printChat(
                Component.literal(Translations.get("addons.exampleaddon.tick")),
                ChatUtils.moduleSignature(this));
    }
}
```

说明：

- 模块名（`AddonModule` 的第二个构造参数）是全局唯一标识，命令别名、配置键、预设都依赖它；**发布后不要改名**。
- `AddonModule` 自动从 `addons.<id>.names.modules.*`、`addons.<id>.descriptions.modules.*`、`addons.<id>.names.settings.*` 读取显示文本；缺键时回退英文标识。不需要本地化时可直接继承 `Module`。
- `ModuleCategory` 目前是枚举，插件复用本体 8 个分类，不支持新增分类。
- `@EventHandler` 方法只在模块启用期间被调用；`enable()` 自动订阅，`disable()` 自动退订。
- 设置类型与 HUD 组件写法见 `.github/skills/evernight-dev/references/module-settings.md`。

### 2.5 指令

```java
package com.example.exampleaddon;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import evernight.client.commands.Command;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;

public final class ExampleCommand extends Command {
    public ExampleCommand() {
        super("exampleaddon");
        this.setAliases("exaddon");
    }

    @Override
    public void exec(LiteralArgumentBuilder root) {
        com.mojang.brigadier.Command<Object> action = context -> {
            ChatUtils.printChat(Component.literal("Hello from Example Addon."), ChatUtils.DEFAULT_SIGNATURE);
            return 1;
        };
        root.executes(action);
    }
}
```

### 2.6 语言表（可选但推荐）

`src/main/resources/assets/exampleaddon/lang/en_us.json`：

```json
{
  "addons.exampleaddon.names.modules.ExampleAddon": "Example Addon",
  "addons.exampleaddon.names.settings.Announce": "Announce",
  "addons.exampleaddon.names.settings.Interval": "Interval",
  "addons.exampleaddon.descriptions.modules.ExampleAddon": "Demonstrates the addon API.",
  "addons.exampleaddon.tick": "Example addon ticked."
}
```

`zh_cn.json` 的键必须与 `en_us.json` 完全一致。**插件的所有语言键都放在 `addons.<id>.*` 下，不要用 `evernight.` 开头**：那是本体保留命名空间，插件共用它会重新引入冲突。`AddonModule` 会按约定读取模块名、描述与设置标签；插件私有文字用 `Translations.get("addons.<id>.xxx")` 读取。语言表在切换语言时会自动重新叠加插件条目。

## 3. AddonContext API

| 方法 | 作用 |
| --- | --- |
| `metadata()` / `id()` | 插件元数据 / Fabric 模组 id |
| `logger()` | 带 `Evernight/Addon/<id>` 前缀的 SLF4J logger |
| `eventBus()` | 客户端事件总线 |
| `registerModule(Module)` | 注册模块（含 HUD 组件），并生成 `;模块别名` 指令；继承 `AddonModule` 可获得命名空间化的显示文本 |
| `registerHudModule(HudWidgetModule)` | 语义化别名，等价于上一行 |
| `registerCommand(Command)` | 注册 Brigadier 指令 |
| `registerConfig(JsonConfigurable)` | 注册独立 JSON 配置文件（文件名来自 `getConfigName()`） |
| `registerLanguage(String code, InputStream)` | 手动合并语言表（一般用不上） |
| `loadLanguageFiles()` | 重新读取 `assets/<id>/lang/{en_us,zh_cn}.json`，初始化时已自动执行 |
| `subscribe(Object)` / `unsubscribe(Object)` | 注册/注销辅助对象的 `@EventHandler`，关闭时自动清理 |
| `dataDirectory()` | `evernight/addons/<id>/`，按需创建 |

注册失败会抛 `IllegalStateException`（例如模块/指令重名），`AddonManager` 记录为 `FAILED` 并继续加载其他插件。**已成功的注册不会回滚**，所以建议把容易失败的注册放在 `onInitialize` 末尾。

模块基类有两种选择：`Module`（纯英文标识，不做本地化）或 `AddonModule`（自动把模块名、描述、设置标签解析到 `addons.<id>.*`）。绝大多数插件应使用后者。

## 4. 配置

- 模块开关、按键、设置自动写入 `evernight/modules.json`；HUD 组件写入 `evernight/hud.json`。插件不需要额外代码。
- 插件自己的状态（缓存、用户列表等）可以实现 `JsonConfigurable` 并在 `onInitialize` 里 `context.registerConfig(myConfig)`，文件落在 `evernight/<getConfigName()>`，建议命名为 `<id>.json` 避免和本体冲突。
- 大文件或非配置数据放 `context.dataDirectory()`。
- `;reload` 会重新读取所有配置源，包括插件注册的配置。

## 5. 生命周期与线程

- `onInitialize` 在主线程、配置加载之前执行；此时可以安全访问 `ClientContext` 的注册表，但 `ClientContext.configurationManager` 还不存在（这正是 `registerConfig` 会暂存的原因）。
- 不要在主线程做网络/磁盘重活：使用 `ClientAccess.executorService` 或 `TaskScheduler` 切回主线程。
- `onShutdown` 在配置保存之后调用，用于停止线程、关闭连接。
- 需要跨模块引用时，不要缓存构造期的 `ClientContext.getModule(...)` 结果（可能是 null）；在事件里现取并判空。

## 6. 调试与验收

1. 先准备本体 jar：用 GitHub Release 的 `evernight-<version>.jar`，或本地 `gradlew.bat build` 产出的 `build/libs/evernight-<version>.jar`，放进插件工程的 `libs/`。
2. 构建插件，把插件 jar 放进开发运行目录 `run/mods/`（或正式游戏的 `.minecraft/mods/`）。
3. 启动游戏，日志中应出现 `Loaded addon <名称> <版本> (<id>)`；失败会打印 `Addon <id> failed to initialize` 与堆栈。
4. 游戏内执行 `;addons` 查看已加载插件、版本与模块数；没有插件时该指令不存在。
5. 检查：模块出现在 ClickGUI 对应分类、设置能保存到 `modules.json`、重进游戏后状态还在、`;help` 里能看到插件指令。

常见问题：

| 现象 | 原因 |
| --- | --- |
| 日志没有插件记录 | `fabric.mod.json` 的入口键不是 `evernight-addon`，或入口类不是 `EvernightAddon` 子类 |
| `NoClassDefFoundError: evernight/...` | 运行环境没有安装对应版本的 Evernight，或 `depends.evernight` 版本不匹配 |
| 模块名冲突导致 `FAILED` | 模块名与本体/其他插件重名；模块名是全局键，换一个 |
| 语言不生效 | 资源路径必须是 `assets/<fabric.mod.json 的 id>/lang/en_us.json`，且 JSON 键与另一语言一致 |
| 设置不保存 | 设置没有通过 `this.add(...)` 注册，或 `setExcludedFromConfig(true)` |

## 7. 版本与兼容

- 插件以 `depends.evernight` 声明兼容范围；API 的公开签名（`EvernightAddon`、`AddonContext`、`AddonMetadata`、`AddonInfo`）遵循语义化版本，破坏性改动会提升次版本号。
- 插件可以使用本体的公开类（`ClientAccess`、`ClientContext`、事件、设置、渲染工具），但不要反射修改私有字段；本体重构不保证二进制兼容。
- 插件需要 mixin 时，在自己的 `fabric.mod.json` 里声明自己的 mixin 配置；本体的 mixin 清单不会代管插件。
- 本体以 `GPL-3.0-only` 发布，链接本体公开 API 的插件按 GPL-3.0 处理。插件不得重新分发本体的代码或构建产物，但可以在 `depends` 里声明对 `evernight` 的依赖。
