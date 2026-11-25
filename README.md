<p align="center">
  <img src="src/main/resources/assets/evernight/icon.png" width="96" alt="Evernight">
</p>

<h1 align="center">Evernight</h1>

<p align="center">面向 Minecraft 1.21.11 的 Fabric 客户端</p>

Evernight 把战斗、移动、渲染、玩家、自动、杂项、漏洞利用、HUD 与客户端功能收进同一套模块框架：模块共用设置、按键、预设、配置与通知体系，界面、指令与语言表遵循同一套命名约定。插件（Addon）可独立编译，通过 `evernight-addon` 入口挂接同一套框架。

> 运行环境：Minecraft 1.21.11 · Fabric Loader · Fabric API · Java 21+

> 官方 QQ 群：949374129

## 安装

1. 为 Minecraft 1.21.11 安装 Fabric Loader。
2. 把 Fabric API 与 `evernight-<version>.jar` 放进 `.minecraft/mods`。
3. 启动游戏，首次运行会在游戏目录生成 `evernight/` 配置文件夹。
4. 可选集成：Baritone、ViaFabricPlus、Xaero's Minimap、EntityCulling、Sodium / Lithium / ImmediatelyFast。

## 快速上手

| 想做什么 | 怎么做 |
| --- | --- |
| 打开界面 | 按 `Y` 打开 ClickGUI（按键名 `key.evernight.click_gui`，可改） |
| 查指令 | 聊天框输入 `;help`；`;toggle <模块>` 开关模块 |
| 切语言 | `Client → UI → Language` |
| 管预设 | `Client → Settings → Presets`，或 `;preset` |
| 编辑 HUD | `Client → HUD → HudEditor`，开启后拖动组件 |
| 配置目录 | 游戏目录下的 `evernight/`：`presets/` `spammer/` `killsay/` `textures/` `maps/` `chunks/` `sounds/` `shaders/` |

## 文档

| 要看什么 | 去哪 |
| --- | --- |
| 安装、界面、指令、模块、配置与预设 | [Wiki](https://github.com/Evernight-Client/Evernight/wiki) |
| 在本仓库写代码（模块 / 设置 / GUI / 渲染 / 工具类） | [evernight-dev skill](.github/skills/evernight-dev/SKILL.md) |
| 写插件（Addon） | [evernight-addon-dev skill](.github/skills/evernight-addon-dev/SKILL.md) · [工程模板](https://github.com/Evernight-Client/evernight-addon-template) |
| 贡献约定 | [AGENTS.md](AGENTS.md) · [CONTRIBUTING.md](CONTRIBUTING.md) |

## 从源码构建

需要 JDK 25 运行 Gradle（Loom 1.19.3 要求；产物字节码目标 Java 21）：

```bash
./gradlew build        # Linux / macOS
gradlew.bat build      # Windows
```

产物在 `build/libs/evernight-<version>.jar`（同时生成 sources jar）；`gradlew.bat runClient` 以 `run/` 为运行目录启动客户端。

## 许可

以 [GPL-3.0-only](LICENSE) 发布，不提供任何担保。基础代码来自 Open Zio（MIT），第三方组件声明见 [licenses/THIRD-PARTY-NOTICES.md](licenses/THIRD-PARTY-NOTICES.md)；再分发须保留 `LICENSE` 与 `licenses/` 下全部文本。Minecraft 及其资产归 Mojang Studios 所有，本项目不包含游戏代码或资源。

## 反馈

问题与建议：[GitHub Issues](https://github.com/Evernight-Client/Evernight/issues)。使用疑问先查 [Wiki](https://github.com/Evernight-Client/Evernight/wiki) 与 QQ 群 949374129。

<p align="center">
  <a href="https://github.com/deepseek-ai/deepseek-harness"><img src="https://img.shields.io/badge/powered_by-dsh-4D6BFE?style=flat-square&logo=deepseek&logoColor=white" alt="powered by dsh" height="20"></a>
</p>
