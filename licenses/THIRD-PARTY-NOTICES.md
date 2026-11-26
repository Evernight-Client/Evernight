# 第三方组件与许可声明

Evernight 本体以 [GPL-3.0-only](../LICENSE) 发布。本文件记录本体中包含或依赖的第三方代码与资源及其许可证。

## 许可证文本索引

| 文件 | 适用对象 |
| --- | --- |
| [GPL-3.0.txt](GPL-3.0.txt) | 本项目整体（GPL-3.0-only）；与根目录的 [LICENSE](../LICENSE) 内容相同 |
| [LGPL-3.0.txt](LGPL-3.0.txt) | LPCTools 移植模块（AutoGrindstone）；LGPL-3.0 以 GPL-3.0 为基础 |
| [Apache-2.0.txt](Apache-2.0.txt) | 内置的 DiscordIPC |
| [MIT-OpenZio.txt](MIT-OpenZio.txt) | 上游 Open Zio 基础代码 |
| [PinIn-MIT.txt](PinIn-MIT.txt) | 内置的裁剪版 PinIn 拼音匹配库及其拼音字典 |
| [MiSans.txt](MiSans.txt) | 内置 MiSans 字体（字体许可，非开源许可，按原协议要求随附） |
| [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) | 本文件：逐项说明来源、许可证与已知风险 |

分发本项目的任何构建产物时，必须把 `LICENSE`、本文件与同目录下的许可证文本一并提供；`build` 会把它们打进 jar 的 `META-INF/`。

## 1. 代码来源

### Open Zio — MIT

本体的模块框架、事件总线、设置系统、渲染与界面等绝大部分基础代码来自 Open Zio（MIT）。

- Copyright (c) 2025 Cypphi and Open Zio contributors
- Copyright (c) 2026 wxh1024 (Evernight modifications)

MIT 与 GPL-3.0 兼容，合并后的整体按 GPL-3.0-only 分发；上游的版权与许可声明按 MIT 要求原样保留在 [MIT-OpenZio.txt](MIT-OpenZio.txt)。

### PinIn — MIT

`src/main/java/me/towdium/pinin/` 与 `assets/evernight/pinyin/data.txt` 是 [PinIn](https://github.com/Towdium/PinIn) 的裁剪内置副本，用于物品、方块、实体、附魔、模块等搜索框的智能拼音匹配（全拼、首字母、中英混输）。

- Copyright (c) 2019 Juntong Liu
- 条款：[PinIn-MIT.txt](PinIn-MIT.txt)。
- 裁剪范围：只保留无索引的即时匹配器（`PinIn#contains`），删除了索引搜索器（TreeSearcher / CachedSearcher / SimpleSearcher）、加速器、双拼等键盘方案、注音格式转换与运行期模糊音配置；匹配算法未改动。
- 拼音数据来自 PinIn 自带的 `data.txt`（其来源为 rime-terra-pinyin 与 mozillazg/pinyin-data），随 MIT 许可一并分发。

### LPCTools — LGPL-3.0-only

`evernight/client/modules/player/AutoGrindstone/` 移植自 LPCTools 的自动砂轮工具，版权归 LPCTools 作者。

- 条款：[LGPL-3.0.txt](LGPL-3.0.txt)，其依赖的 GPL-3.0 全文见 [LICENSE](../LICENSE)。
- 按 LGPL-3.0 第 2 条，该部分并入本项目后按 GPL-3.0-only 分发。

### DiscordIPC（jagrosh）— Apache-2.0

`src/main/java/com/jagrosh/discordipc/` 是 DiscordIPC 的内置副本，用于 Discord Rich Presence。

- 版权归 jagrosh 及 DiscordIPC 贡献者。
- 许可证全文：[Apache-2.0.txt](Apache-2.0.txt)。
- 本项目对该副本做过修改（重连退避、传输层与枚举整理等）；按 Apache-2.0 第 4 条在此注明。

### 参考实现（未包含其代码）

以下项目仅作为设计或行为参考，不构成本项目的一部分：

- PacketFixer（MIT）——NoPacketKick 增强拦截的思路。
- OpenHachimi ——部分模块的行为参考。
- Meteor Client（GPL-3.0）——仅读取 `meteor-client/friends.nbt` 文件格式。

## 2. 编译期 / 运行期依赖（不随本体分发）

以下模组以 `compileOnly` / `modCompileOnly` 接入，发布 jar 中不含其代码。

| 依赖 | 许可证 | 用途 | 打包 |
| --- | --- | --- | --- |
| Fabric Loader / Fabric API | Apache-2.0 | 运行环境 | 否 |
| Baritone（Meteor / rfresh2 分支） | LGPL-3.0 | 自动挖矿/挖沙、`;baritone` | 否（1 个 mixin） |
| ViaFabricPlus | GPL-3.0 | 协议版本兼容 | 否（3 个 mixin） |
| Lithium | LGPL-3.0-only | 兼容 | 否（1 个 mixin） |
| ImmediatelyFast | LGPL-3.0 | 兼容 | 否（1 个 mixin） |
| Sodium | PolyForm Shield 1.0.0 | 兼容 | 否（仅编译，未用其代码） |
| EntityCulling | tr7zw Protective License | 渲染剔除兼容 | 否（仅开发期运行） |
| Xaero's Minimap / XaeroPlus | All Rights Reserved | 路径点同步、坐标隐藏 | 否（6 个 mixin） |
| DevAuth | MIT | 开发期登录 | 否（仅开发期运行） |
| JetBrains annotations | Apache-2.0 | 编译注解 | 否 |
| Minecraft / Mojang mappings | Mojang EULA（专有，非开源） | 目标平台 | 否（见第 4 节） |

ViaFabricPlus 内嵌的 ViaVersion / ViaBackwards / ViaRewind 同为 GPL-3.0，仅用于编译与开发运行，不进产物。

### Xaero 系 mixin 说明

`evernight/mixin/mods/xaero/` 与 `.../xaeroplus/` 改写了 Xaero's Minimap 与 XaeroPlus 的内部绘制方法。这两个模组为 All Rights Reserved，未授予创作衍生作品的权利。

Evernight 与 Xaero 无任何隶属或授权关系；如原作者提出要求，相关 mixin 会立即移除。

## 3. 内置资源

| 资源 | 路径 | 许可 / 出处 |
| --- | --- | --- |
| MiSans Regular | `assets/evernight/font/misans-regular.ttf` | 小米 MiSans 字体许可，见 [MiSans.txt](MiSans.txt) |
| 拼音字典 | `assets/evernight/pinyin/data.txt` | 取自内置的 PinIn（数据来自 rime-terra-pinyin 与 mozillazg/pinyin-data），见 [PinIn-MIT.txt](PinIn-MIT.txt) |
| 界面贴图、图标、披风 | `assets/evernight/textures/`、`capes/`、`icon.png` | 本项目自制 |
| 着色器 | `assets/evernight/shaders/` | 本项目编写 |
| 原版着色器改写 | `assets/minecraft/shaders/core/evernight_*` | 由 Minecraft 原版着色器改编，原资产归 Mojang |
| 界面音效 | `assets/evernight/sounds/` | 本项目合成，随 GPL-3.0 发布 |
| 连杀播报音效 | `assets/evernight/offline/killstreaks/` | 本项目生成，随 GPL-3.0 发布 |

### 音效

全部内置音效都由本仓库的脚本生成，不含任何第三方录音：

- 界面音效由 [scripts/audio/generate.py](../scripts/audio/generate.py) 用 numpy 合成后编码为 Ogg Vorbis。
- 连杀播报由 [scripts/audio/render_tts.ps1](../scripts/audio/render_tts.ps1) 调用系统语音合成渲染，再由同一个脚本编码。

重新生成：

```
scripts/audio/render_tts.ps1
python scripts/audio/generate.py --tts-dir build/audio-tts
```

## 4. Minecraft 与 Mojang

Minecraft 及其游戏资产、官方映射与商标归 Mojang Studios / Microsoft 所有。它是专有软件，**不是开源许可**，因此不放进本目录当作组件许可，只在这里说明关系：

- 本项目是 Fabric 客户端模组，不包含 Minecraft 的游戏代码与资源；运行需要用户自行购买正版 Minecraft，并安装对应版本的 Fabric Loader 与 Fabric API。
- 构建期使用 Mojang 官方映射，产物经 intermediary 重映射，jar 内不含映射文件或反编译源码。
- `assets/minecraft/shaders/core/evernight_*` 由原版核心着色器改编（文件内注释已注明），属于对 Minecraft 资产的修改；若发行平台或 Mojang 提出要求，这部分会移除或改为运行期注入。
- Minecraft 是 Mojang Studios 的商标，本项目与其无隶属或背书关系。

官方条款：

- 最终用户许可协议（EULA）：https://www.minecraft.net/en-us/eula
- 使用准则（Usage Guidelines）：https://www.minecraft.net/en-us/usage-guidelines
- 品牌与资产准则：https://www.minecraft.net/en-us/brand

## 5. 维护约定

- 新增或替换第三方代码、素材时，在同一个 PR 内更新本文件与同目录下的许可证文本。
- 音效不要直接提交外部音频文件；改 `scripts/audio/` 里的脚本并重新生成。
- 不得移除 `LICENSE`、本文件或 本目录下的许可文本。
- 移植前先确认上游许可证；无许可证（保留所有权利）或与 GPL-3.0 不兼容的代码不得合并。
