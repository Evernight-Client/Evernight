<p align="center">
  <img src="src/main/resources/assets/evernight/icon.png" width="96" alt="Evernight">
</p>

<h1 align="center">Evernight</h1>

<p align="center">面向 Minecraft 1.21.11 的 Fabric 客户端</p>

Evernight 将战斗、移动、渲染、玩家、自动、杂项、漏洞利用、HUD 与客户端功能收进同一套模块框架：模块共用设置、按键、预设、配置与通知体系，界面、指令与语言表遵循同一套命名约定。插件（Addon）可以独立编译，通过 `evernight-addon` 入口挂接同一套框架。

> 运行环境：Minecraft 1.21.11 · Fabric Loader 0.19.5+ · Fabric API · Java 21+

## 目录

- [功能特性](#功能特性)
- [环境要求](#环境要求)
- [安装](#安装)
- [快速上手](#快速上手)
- [指令一览](#指令一览)
- [插件（Addon）](#插件addon)
- [从源码构建](#从源码构建)
- [项目结构](#项目结构)
- [参与开发](#参与开发)
- [许可](#许可)
- [反馈](#反馈)

## 功能特性

### 模块

约 200 个模块，分为 9 个分类：

| 分类 | 模块数（约） | 代表模块 |
| --- | --- | --- |
| Combat（战斗） | 34 | Aura、AutoCrystal、AnchorAura、AutoMace、AutoPot、AutoArmor、AntiPhase |
| Render（渲染） | 34 | ESP、Chams、Tracers、HoleESP、NameTags、Search、Xray、Shader、Skybox |
| Player（玩家） | 15 | ChestStealer、Scaffold、Nuker、InventoryTweaks、Freecam |
| Auto（自动） | 14 | AutoFarm、AutoCraft、AutoFish、AutoTrade、AutoMining、AutoClicker |
| Misc（杂项） | 29 | AutoReconnect、ChatFilter、ExtraScreenshot、KillEffects、Spammer、StashFinder、VisualRange、WebhookNotifs |
| Movement（移动） | 25 | Speed、Flight、ElytraFly、Velocity、Step、NoFall、Jesus、LongJump、FakeLag |
| Exploit（漏洞利用） | 19 | NewChunks、Phase、Reach、Timer、PingSpoof、Trident、XCarry、GrimDisabler |
| Client（客户端） | 10 | UI、HUD、Fonts、Colors、Notifications、Settings、Discord、Capes、AntiCheat、Baritone |
| HUD | 20 | Watermark、ModuleList、Armor、Effects、Totems、TextRadar、Music、PlayerModel、Inventory |

每个模块都可以单独开关、绑定按键并调整设置；设置类型覆盖布尔、数值、枚举、颜色、字符串、字符串集合与注册表集合，并支持按条件显隐。模块名（小写）可以直接作为指令使用（例如 `;aura`），也可以为指令设置别名。

**聊天过滤（ChatFilter）** 同时支持两种过滤来源：`;chatfilter` 指令维护的「编号 + 过滤文本」列表，以及 ClickGUI 内维护的关键词列表（默认屏蔽 `lotus`）。命中的聊天消息会在进入聊天栏之前被直接丢弃，用于压制特定词造成的刷屏卡顿；指令过滤器可开启区分大小写，关键词列表始终按不区分大小写的包含匹配，`IgnorePM` 开启后私聊消息直接放行。关键词列表在 ClickGUI 里点开设置项即可增删（点条目删除、输入后回车或点添加新增），字符串集合设置都支持这种编辑方式。

### 界面

- **ClickGUI**（默认 `Y`）：按分类展示模块与设置，支持搜索与 Tab 补全。
- **HUD 编辑器**：在 `Client → HUD → HudEditor` 中开启，可拖动 20 个 HUD 组件的锚点、方向与边距。
- **其他界面**：通知与确认框、预设导入确认、声音选择、交易编辑器、潜影盒预览、地图预览、视角模型编辑器。

### 配置与预设

- 配置保存在游戏目录下的 `evernight/` 文件夹中，包含 `modules.json`、`hud.json`、`colors.json`、`commands.json`、`macros.json`、`aliases.json`、`chatfilter.json`、`waypoints.json`、`kits.json`、`trades.json`、`socials.json`、`webhook.json` 等文件。
- 预设分为 Modules、Binds、Hud、Colors、Visuals、Macro、All 七类，支持保存、加载、导出、导入、恢复与撤销。
- 预设可以从文件、剪贴板（`Ctrl+V`）或拖放导入，导入前会显示确认界面。
- 好友与敌人、路径点、kit、交易模板、聊天过滤、宏与指令别名同样统一保存在配置目录中，`;reload` 可重新加载。

### 指令系统

指令基于 Brigadier，带参数补全，默认前缀为 `;`（可通过 `;prefix` 或 `Client → Settings` 修改）。内置 40 余条指令，每个模块另有自动生成的开关指令，并支持自定义别名与宏。

### 集成

- **Baritone**（Meteor 分支 `baritone-meteor` 或 rfresh2 分支 `baritone`）：提供 `Baritone`、`ObstaclePasser` 模块与 `;baritone` 指令；未安装 Baritone 时自动禁用桥接。
- **ViaFabricPlus**：协议版本兼容层，可连接更低版本服务器。
- **Xaero's Minimap**：在「Client → 路径点设置」里开启「路径点同步」后，客户端路径点与 Xaero 的 `Evernight Waypoints` 集双向同步；关闭时清空该集。同一模块里可选择路径点按服务器名或按 IP/域名区分。
- **EntityCulling**：避免 ESP、描边等渲染被遮挡剔除。
- **Sodium / Lithium / ImmediatelyFast**：声明为兼容模组，可同时安装。

### 渲染、字体与声音

- 界面采用 Catppuccin Mocha 配色，ClickGUI、HUD 强调色与 tooltip 使用同一套色板，颜色常量集中在 `Catppuccin` 类中，便于统一调整。
- 自定义渲染管线覆盖 ESP、Chams、描边、渐变、模糊、泛光与景深，并附带 custom、multi、sky、util 四组着色器资源。
- 内置 MiSans 字体：开启 `Fonts` 后全客户端文本改用 MiSans 渲染，关闭则回退原版字体；支持样式、字号（12–20）、抗锯齿、聊天字体与偏移调整。
- 声音系统包含击杀特效、连杀音效与自定义音效目录，并提供声音选择界面。

### 通知与媒体

- 通知系统覆盖模块开关、击杀、延迟等事件，可将通知推送到 Discord Webhook 或 Telegram（`;webhook`）。
- **Discord Rich Presence**：在 Discord 中显示当前客户端状态。
- **音乐 HUD**：读取系统媒体会话（Windows SMTC、macOS Now Playing、Linux MPRIS），显示曲目、艺术家与播放进度。
- **截图**：`ExtraScreenshot` 支持复制到剪贴板或上传 Imgur，Imgur ClientId 可在模块设置中替换为自己的。

## 环境要求

| 组件 | 要求 |
| --- | --- |
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.5 或更高 |
| Fabric API | 对应 1.21.11 的版本 |
| Java | 21 或更高（运行） |
| JDK | 25（构建；Loom 1.18.4 的 Gradle JVM 要求） |

## 安装

1. 为 Minecraft 1.21.11 安装 Fabric Loader。
2. 将 Fabric API 与 `evernight-<version>.jar` 放入 `.minecraft/mods`。
3. 启动游戏。首次启动会在游戏目录创建 `evernight/` 配置文件夹。
4. 按需安装 Baritone（Meteor 或 rfresh2 分支）、ViaFabricPlus、Xaero's Minimap 或 EntityCulling，以启用对应集成。

> 构建好的发行版可加 QQ 群 **949374129** 获取；也可以按[从源码构建](#从源码构建)自行编译。

## 快速上手

- **打开界面**：默认按 `Y` 打开 ClickGUI。按键在「选项 → 控制 → 按键」的「杂项」分类中，名称为 `key.evernight.click_gui`，可随时修改。
- **执行指令**：在聊天框输入 `;help` 查看指令列表；`;toggle <模块>` 开关模块。
- **切换语言**：`Client → UI → Language`，支持 English 与简体中文。
- **管理预设**：`Client → Settings → Presets` 打开预设界面，或使用 `;preset` 指令。
- **编辑 HUD**：`Client → HUD → HudEditor`，开启后即可拖动 HUD 组件。
- **操作假人**：`Misc → Fakeplayer` 展开后点击 `Panel` 打开图形面板，可生成/移除假人、击杀、录制与回放、管理登出点（需先进入世界）。
- **配置目录**：游戏目录下的 `evernight/`，常用子目录如下。

```
evernight/
├── presets/    预设（Modules / Binds / Hud / Colors / Visuals / Macro / All）
├── spammer/    Spammer 文案
├── killsay/    KillSay 文案
├── textures/   自定义贴图与披风
├── maps/       地图缓存
├── chunks/     NewChunks 数据
├── sounds/     自定义音效
└── shaders/    着色器
```

## 指令一览

所有指令均以 `;` 开头（示例中的前缀可按需修改）。

| 指令 | 说明 |
| --- | --- |
| `help` | 列出全部指令 |
| `toggle <模块>` | 开关模块 |
| `bind` | 绑定模块按键（`set` / `clear` / `list` / `all`） |
| `prefix <字符>` | 修改指令前缀 |
| `alloff` | 关闭所有模块 |
| `panic` | 紧急关闭全部模块 |
| `preset` | 预设管理（`save` / `load` / `list` / `export` / `import` / `restore` / `undo`） |
| `alias` | 指令别名（`set` / `remove`） |
| `macro` | 宏的录制、编辑与导入导出 |
| `friend` / `enemy` | 好友与敌人名单（`add` / `remove` / `list` / `sync`） |
| `waypoints` | 路径点（`add` / `remove` / `rename` / `list` / `toggle`） |
| `kit` | 套装（`load` / `rename`） |
| `trade` | 交易模板（`new` / `edit` / `delete` / `enchant` / `list`） |
| `webhook` | 通知推送（`get` / `set` / `telegram` / `chatid` / `clear`） |
| `folder` | 打开配置子目录（`spammer` / `killsay` / `textures` / `sounds` / `shaders`） |
| `reload` | 重新加载配置 |
| `fakeplayer` | 假玩家（`record` / `play` / `logout` / `kill`） |

## 插件（Addon）

Evernight 提供与 Meteor Client 类似的插件机制：插件是独立的 Fabric 客户端模组，通过 `evernight-addon` 入口注册模块、指令、配置与语言表，由客户端启动时自动发现。

- 插件 API：`src/main/java/evernight/addon/`（`EvernightAddon`、`AddonContext`、`AddonManager`）。
- 开发指南：[docs/addons.md](docs/addons.md)，包含工程骨架、完整示例、构建与排错。
- 工程模板：[evernight-addon-template](https://github.com/Evernight-Client/evernight-addon-template)，含示例插件、语言表校验与 agent skill。
- 插件可复用本体模块分类，也可通过 `context.registerCategory("...")` 注册新分类，并使用全部设置类型、事件总线、HUD 组件与渲染工具；单个插件初始化失败不会影响客户端或其他插件。
- 没有插件时不会注册 `;addons` 指令；有插件时可在游戏内查看版本与模块数。

## 从源码构建

需要 JDK 25 运行 Gradle（Loom 1.18.4 要求；产物字节码目标仍为 Java 21）。在仓库根目录执行 Gradle wrapper：

```bash
# Linux / macOS
./gradlew build

# Windows
gradlew.bat build
```

构建产物位于 `build/libs/evernight-<version>.jar`（同时生成 sources jar）。开发时可运行 `gradlew.bat runClient` 启动客户端，运行目录为 `run/`。

## 项目结构

```
src/main/java/evernight/
├── addon/          插件 API（入口、上下文、管理器、;addons 指令）
├── client/
│   ├── modules/     大类目录 + 每模块一个同名子目录（combat/AutoMine/、render/ESP/……）
│   ├── commands/    分类目录 + 每命令一个同名子目录（control/Module/、movement/Elytra/……）
│   ├── gui/         core / components / panels / registry / screens
│   ├── managers/    registry / config / player / render / world / social / audio / task / command
│   ├── render/      font / gui / hud / shader / mesh / esp / color / resource / util / text
│   ├── settings/    core / types / options
│   ├── events/      core / input / render / network / player / world / ui + bus
│   └── utils/       core / chat / collections / combat / entity / inventory / math / movement / network / world
└── mixin/           Mixin 注入（minecraft/ 按功能域，mods/ 按目标模组）

src/main/resources/
├── assets/evernight/   图标、语言表、字体、着色器、音效与披风
├── evernight.mixins.<分类>.json   与 mixin/ 子包一一对应的 Mixin 清单
└── fabric.mod.json

docs/addons.md          插件（Addon）开发指南
CONTRIBUTING.md         贡献流程与代码约定
```

## 参与开发

- 玩家可见文本统一通过 `Translations` 读取，`en_us.json` 与 `zh_cn.json` 的键集合必须一致；本体键以 `evernight.` 开头，插件键使用 `addons.<id>.*`。
- 模块名、设置 `configName`、指令名与配置键属于稳定标识，保持英文，不要用翻译名替换。
- `evernight/addon` 是对外插件 API，修改公开签名时同步更新 `docs/addons.md` 与插件 skill。
- 提交前至少编译一次；改动资源或语言表时执行完整构建。更多约定见 [AGENTS.md](AGENTS.md) 与 [CONTRIBUTING.md](CONTRIBUTING.md)。

Minecraft 是 Mojang Studios 的商标，Evernight 与 Mojang Studios、Microsoft 无隶属关系。

## 许可

Evernight 以 [GNU General Public License v3.0](LICENSE)（`GPL-3.0-only`）发布，不提供任何担保。

- 本项目的基础代码来自 Open Zio（MIT），其版权与许可声明按 MIT 要求保留在 [licenses/MIT-OpenZio.txt](licenses/MIT-OpenZio.txt)；合并后的整体按 GPL-3.0-only 分发。
- 内置的第三方组件（DiscordIPC、LPCTools 移植模块、MiSans 字体等）各自适用原许可证，逐项说明见 [licenses/THIRD-PARTY-NOTICES.md](licenses/THIRD-PARTY-NOTICES.md)。
- 再分发或发布修改版本时，必须同时保留 `LICENSE` 与 `licenses/` 下的全部文本；构建产物会把它们放进 jar 的 `META-INF/`。
- Minecraft 及其资产归 Mojang Studios 所有，本项目不包含游戏代码或资源，运行需自行拥有正版 Minecraft；官方条款链接见 [licenses/THIRD-PARTY-NOTICES.md](licenses/THIRD-PARTY-NOTICES.md) 第 4 节。
- 插件（Addon）作者请先读 [docs/addons.md](docs/addons.md) 的「版本与兼容」一节。

## 反馈

问题与建议请提交到 [GitHub Issues](https://github.com/Evernight-Client/Evernight/issues)。

<p align="center">
  <a href="https://github.com/deepseek-ai/deepseek-harness"><img src="https://img.shields.io/badge/powered_by-dsh-4D6BFE?style=flat-square&logo=deepseek&logoColor=white" alt="powered by dsh" height="20"></a>
</p>

