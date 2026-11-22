# Evernight 规则

1. 可见文本走语言表，键统一以 `evernight.` 开头（原版 `key.*` 除外），标识符保持英文，中英两份 JSON 键集一致。
2. 新模块注册 `ModuleRegistry`，新 mixin 登记对应清单。
3. 先 grep 先例再写，只改必要文件；提交前 `build` + `check-lang.py`。
4. `evernight/addon` 是公开插件 API，改签名要同步 `docs/addons.md`、插件 skill 与语言表。

细则见 `.github/skills/evernight-dev/`；写插件见 `.github/skills/evernight-addon-dev/` 与 `docs/addons.md`。
