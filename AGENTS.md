# Evernight 规则

1. 可见文本走语言表，键统一以 `evernight.` 开头（原版 `key.*` 除外），标识符保持英文，中英两份 JSON 键集一致。
2. 新模块注册 `ModuleRegistry`，新 mixin 登记对应清单。
3. 先 grep 先例再写，只改必要文件；任何改动完成后都完整构建 `gradlew.bat build`（构建自动运行单元测试）。
4. `evernight/addon` 是公开插件 API，改签名要同步插件 skill `.github/skills/evernight-addon-dev/` 与语言表。
5. 任务过程中阶段性完成应该进行提交；提交前需完整构建。发版前必须在本机运行 `gradlew.bat releaseTest`（构建 + 单元测试）。GitHub Actions 的 `build` 也会自动跑单元测试；`release.yml` 从 tag 构建并发布 Release，也支持手动 dispatch 指定 tag。
6. 开发前需加载项目 skill
7. 遇到歧义决策未知主动提问
8. 统一 UTF-8（无 BOM）、LF 行尾、空格缩进（`.bat` 检出用 CRLF）；由根目录 `.gitattributes` 与 `.editorconfig` 约束，不提交 CRLF、tab 或 BOM。
9. 注释统一用英文：javadoc、行注释、构建脚本与配置文件里的注释都写英文；标识符与语言表内容不动，文档与提交信息仍可用中文。

细则见 `.github/skills/evernight-dev/`；写插件见 `.github/skills/evernight-addon-dev/`。
