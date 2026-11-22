# 参与开发

感谢你愿意为 Evernight 提交代码。开始之前请先读 [AGENTS.md](AGENTS.md)：那几条是所有改动的硬性约束，本文件说明环境、流程与提交规范。

## 开发环境

| 组件 | 版本 |
| --- | --- |
| JDK | 25（构建；字节码目标 Java 21） |
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.5+ |
| Fabric API | 0.141.6+1.21.11 |

Loom 1.18.4 要求 Gradle 跑在 JDK 25 上；`options.release = 21` 保证产物仍是 Java 21 字节码。运行 `gradlew.bat runClient`（Linux/macOS 为 `./gradlew runClient`）启动开发客户端，运行目录是 `run/`。

## 构建与校验

```bash
# 只改 Java
gradlew.bat compileJava

# 改资源 / 语言表 / 发布前
gradlew.bat build

# 语言表键集合、空值、占位符检查
python .github/skills/evernight-dev/scripts/check-lang.py
```

构建产物：`build/libs/evernight-<version>.jar`（生产/插件依赖）与 `build/devlibs/evernight-<version>-dev.jar`（本体联调调试用；不要交给插件的 `mod*` 配置）。

## 代码约定

- **可见文本走语言表**：`Translations.*` 或 `NamedEntry#getDisplayName()`；`en_us.json` 与 `zh_cn.json` 键集合必须一致。
- **标识符不翻译**：模块名、`Setting#getConfigName()`、指令字面量、配置 JSON 键保持英文。
- **注册点唯一**：新模块进 `ModuleRegistry` 构造器；新 mixin 放进对应子包并登记 `evernight.mixins.<分类>.json`。
- **插件 API 稳定**：`evernight/addon` 的公开签名是对外契约，修改时同步更新 `docs/addons.md`、`.github/skills/evernight-addon-dev/`，并说明兼容性。
- **不要提交密钥**：Webhook、Token、Client ID 等一律走运行期配置，不要硬编码进源码或示例。

更细的 API 速查见 `.github/skills/evernight-dev/`。

## 分支与提交

- 从 `master` 切出功能分支，PR 目标为 `master`。
- 提交信息格式：`type(scope): 中文简述`，例如 `feat(hud): 新增坐标显示组件`、`fix(i18n): 补全漏翻的设置标签`。
- 代码与对应的语言表改动放在同一个提交。
- 一个 PR 只做一件事；重构与功能分开提交，便于审查。

## Pull Request

PR 描述请写清：

1. 改了什么、为什么改；
2. 如何验证（编译、构建、语言表检查；游戏内行为由维护者人工验收）；
3. 是否影响插件 API、配置文件键名或预设兼容性。

## 许可证

Evernight 以 `GPL-3.0-only` 发布。提交 PR 即表示你同意以同一许可证授权你的贡献（inbound = outbound），并确认你有权提交这些代码。

- 移植第三方代码时，PR 里必须写明来源与许可证，并同步更新 [licenses/THIRD-PARTY-NOTICES.md](licenses/THIRD-PARTY-NOTICES.md)；无许可证（保留所有权利）或与 GPL-3.0 不兼容的代码不得合并。
- 新增第三方素材（音效、字体、贴图）同样要登记，并确认允许再分发。
- 不要删除 `LICENSE` 与 `licenses/` 下的任何文本。
