# Evernight 规则

1. 可见文本走语言表，键统一以 `evernight.` 开头（原版 `key.*` 除外），标识符保持英文，中英两份 JSON 键集一致。
2. 新模块注册 `ModuleRegistry`，新 mixin 登记对应清单。
3. 先 grep 先例再写，只改必要文件；提交前 `build` + `check-lang.py`。
4. `evernight/addon` 是公开插件 API，改签名要同步插件 skill `.github/skills/evernight-addon-dev/` 与语言表。
5. 任务功能需求完成之后提交 不要一个大提交 把不同的分为多个提交提交前需构建测试
6. 开发前需加载项目 skill
7. 遇到歧义决策未知主动提问
8. 统一 UTF-8（无 BOM）、LF 行尾、空格缩进；由根目录 `.gitattributes` 与 `.editorconfig` 约束，不提交 CRLF、tab 或 BOM。

细则见 `.github/skills/evernight-dev/`；写插件见 `.github/skills/evernight-addon-dev/`。
