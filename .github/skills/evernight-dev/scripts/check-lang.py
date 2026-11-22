#!/usr/bin/env python3
"""Evernight 语言表校验。

语言表是标准 JSON（UTF-8）：顶层为扁平对象，键是完整键（如 evernight.names.modules.Aura），
值是字符串。LanguageTable 用 Gson 解析；换行在 JSON 里写 \n，引号写 \"。

比对 src/main/resources/assets/evernight/lang/en_us.json 与 zh_cn.json。

用法（仓库根目录）：
    python .github/skills/evernight-dev/scripts/check-lang.py

退出码：
    0 = 通过（可能有 WARN）
    1 = 存在 ERROR
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

# 12 个自定义键前缀；列表顺序同时用于报告 section 顺序。
# 所有客户端自有键都在 evernight. 命名空间下，避免与其他资源包/插件冲突。
SECTIONS = [
    "evernight.descriptions.modules",
    "evernight.descriptions.settings",
    "evernight.names.modules",
    "evernight.names.settings",
    "evernight.names.sounds",
    "evernight.names.values",
    "evernight.names.categories",
    "evernight.names.panels",
    "evernight.ui",
    "evernight.ui.chat",
    "evernight.ui.modules",
    "evernight.ui.commands",
]
# 原版资源包同样会读取这两份 JSON，keybind 等原版键允许共存。
VANILLA_PREFIXES = ("key.", "category.")
# %s %d %1$s %-10.2f %% …… 只关心转换说明，不关心宽度/精度
PLACEHOLDER_RE = re.compile(r"%(?:(\d+)\$)?[-#+0,(<]*\d*(?:\.\d+)?([sSdfxXoberaAcCeEgGtTn%])")
ANGLE_TAG_RE = re.compile(r"<([A-Za-z_][A-Za-z0-9_]*)>")
# LanguageTable 只把这两个标记换成颜色，其余尖括号按字面保留
SUPPORTED_TAGS = {"red", "yellow"}
# 常见误用：以为这些也会被换成颜色
COMMON_COLOR_NAMES = {
    "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
    "gold", "gray", "grey", "dark_gray", "dark_grey", "blue", "green", "aqua",
    "red", "light_purple", "pink", "yellow", "white", "reset", "bold",
    "italic", "underline", "strikethrough", "obfuscated", "green",
}
# zh 与 en 完全相同时允许的专有名词（小写比较）
PROPER_NOUNS = {
    "esp", "hud", "gui", "api", "url", "tps", "fps", "cps", "b/s", "km/h",
    "baritone", "discord", "telegram", "webhook", "imgur", "clientid",
    "sodium", "lithium", "viafabricplus", "meteor", "smtc", "mpris", "dbus",
    "ncp", "grim", "grimv3", "vivaldi", "2b2t", "constantiam", "folia",
    "deepseek", "evernight", "xaero", "linux", "macos", "windows",
    "alt", "shift", "ctrl", "mlg", "cev", "xqz", "bps", "paper", "xyz",
}

ERRORS: list[str] = []
WARNINGS: list[str] = []


def repo_root() -> Path:
    for parent in Path(__file__).resolve().parents:
        if (parent / "src/main/resources/assets/evernight/lang").is_dir():
            return parent
    sys.exit("找不到仓库根目录（需要包含 src/main/resources/assets/evernight/lang）")


def is_vanilla_key(key: str) -> bool:
    return key.startswith(VANILLA_PREFIXES)


def section_of(key: str) -> str | None:
    """按最长前缀判断键所属的 section；原版键不属于任何 section。"""
    if is_vanilla_key(key):
        return None
    matches = [section for section in SECTIONS if key.startswith(section + ".")]
    return max(matches, key=len) if matches else None


def parse(path: Path) -> tuple[dict[str, str], list[str]]:
    """读取并校验 JSON 结构，返回 (键->值, 解析期问题)。"""
    entries: dict[str, str] = {}
    problems: list[str] = []
    duplicates: list[str] = []

    def reject_duplicates(pairs):
        obj = {}
        for key, value in pairs:
            if key in obj:
                duplicates.append(key)
            obj[key] = value
        return obj

    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError as error:
        problems.append("%s 不是合法的 UTF-8：%s" % (path.name, error))
        return entries, problems
    if text.startswith("\ufeff"):
        problems.append("%s 带 UTF-8 BOM，Gson 会把 BOM 当成 JSON 内容而解析失败" % path.name)
    try:
        data = json.loads(text, object_pairs_hook=reject_duplicates)
    except json.JSONDecodeError as error:
        problems.append(
            "%s 不是合法 JSON：第 %d 行第 %d 列：%s"
            % (path.name, error.lineno, error.colno, error.msg)
        )
        return entries, problems
    if not isinstance(data, dict):
        problems.append("%s 顶层必须是 JSON 对象" % path.name)
        return entries, problems
    for key, value in data.items():
        if not isinstance(value, str):
            problems.append("%s 的值必须是字符串：%s" % (path.name, key))
            continue
        entries[key] = value
    for key in duplicates:
        problems.append("%s 重复键（Gson 保留后者）：%s" % (path.name, key))
    return entries, problems


def sections_in_order(entries: dict[str, str]) -> list[str]:
    """按首次出现顺序返回 section，并检查同一 section 的键是否连续。"""
    order: list[str] = []
    for key in entries:
        section = section_of(key)
        if section is None:
            continue
        if not order or order[-1] != section:
            order.append(section)
    return order


def placeholder_profile(value: str) -> tuple[int, int]:
    """返回 (引用到的最大参数序号, 占位符个数)。

    显式序号 %1$s 按序号算；隐式占位符按出现顺序依次占位。
    这样 %1$d ... %3$s 会得到 max_index=3，而 %d ... %s%s 得到 3。
    """
    explicit = [int(m.group(1)) for m in PLACEHOLDER_RE.finditer(value) if m.group(1)]
    total = len([m for m in PLACEHOLDER_RE.finditer(value) if m.group(2) != "%"])
    if explicit:
        return max(explicit), total
    return total, total


def check_value(name: str, key: str, value: str) -> None:
    if not value.strip():
        ERRORS.append("%s 空值: %s" % (name, key))
        return
    for tag in ANGLE_TAG_RE.findall(value):
        lowered = tag.lower()
        if lowered in COMMON_COLOR_NAMES and lowered not in SUPPORTED_TAGS:
            WARNINGS.append(
                "%s 写了 <%s>，但 LanguageTable 只认 <red>/<yellow>，它会原样显示: %s"
                % (name, tag, key)
            )
    if "\\" in value:
        WARNINGS.append(
            "%s 值里有反斜杠，JSON 解析后会原样显示（换行请写 \\n 转义）: %s" % (name, key)
        )


def check_copy(name: str, key: str, en_value: str, zh_value: str) -> None:
    if zh_value != en_value:
        return
    if not (key.startswith("descriptions.") or key.startswith("ui.")):
        return  # names.* 里保留英文很常见（专有名词、单位）
    if en_value.strip().lower() in PROPER_NOUNS:
        return
    if not re.search(r"[A-Za-z]{3,}\s+[A-Za-z]{2,}", en_value):
        return  # 单词/缩写/格式化片段不报警
    WARNINGS.append("%s 直接照抄了英文: %s = %r" % (name, key, en_value))


def main() -> int:
    root = repo_root()
    lang_dir = root / "src/main/resources/assets/evernight/lang"
    en_path, zh_path = lang_dir / "en_us.json", lang_dir / "zh_cn.json"
    for path in (en_path, zh_path):
        if not path.is_file():
            ERRORS.append("缺少语言表: %s" % path)
    if ERRORS:
        return report()

    en, en_problems = parse(en_path)
    zh, zh_problems = parse(zh_path)
    ERRORS.extend(en_problems)
    ERRORS.extend(zh_problems)

    en_sections = sections_in_order(en)
    zh_sections = sections_in_order(zh)
    en_section_set = set(en_sections)
    zh_section_set = set(zh_sections)
    if en_section_set != zh_section_set:
        only_en = sorted(en_section_set - zh_section_set)
        only_zh = sorted(zh_section_set - en_section_set)
        if only_en:
            ERRORS.append("zh_cn.json 缺少 section: %s" % ", ".join(only_en))
        if only_zh:
            ERRORS.append("en_us.json 缺少 section: %s" % ", ".join(only_zh))
    elif en_sections != zh_sections:
        ERRORS.append("两个文件的 section 顺序不一致（或同一 section 的键不连续）")

    for name, entries in (("en_us.json", en), ("zh_cn.json", zh)):
        for key in sorted(entries):
            if section_of(key) is None and not is_vanilla_key(key):
                ERRORS.append("%s 的键不属于 12 个 section（不会被读取）: %s" % (name, key))

    for key in sorted(set(en) - set(zh)):
        ERRORS.append("zh_cn.json 缺少键: %s" % key)
    for key in sorted(set(zh) - set(en)):
        ERRORS.append("en_us.json 缺少键: %s" % key)

    for key in sorted(set(en) & set(zh)):
        en_value, zh_value = en[key], zh[key]
        check_value("en_us.json", key, en_value)
        check_value("zh_cn.json", key, zh_value)
        check_copy("zh_cn.json", key, en_value, zh_value)

        en_index, en_count = placeholder_profile(en_value)
        zh_index, zh_count = placeholder_profile(zh_value)
        if zh_index > en_index:
            ERRORS.append(
                "zh_cn 引用了第 %d 个参数，但 en_us 只有 %d 个，String.format 会抛异常: %s"
                % (zh_index, en_index, key)
            )
        elif zh_count != en_count:
            WARNINGS.append(
                "占位符个数不一致（en %d / zh %d，常见于中文省略复数后缀，请人工确认）: %s"
                % (en_count, zh_count, key)
            )

    print("en_us.json: %d 键 / %d section" % (len(en), len(en_section_set)))
    print("zh_cn.json: %d 键 / %d section" % (len(zh), len(zh_section_set)))
    return report()


def report() -> int:
    for warning in WARNINGS:
        print("WARN  " + warning)
    for error in ERRORS:
        print("ERROR " + error)
    if ERRORS:
        print("\n失败：%d 个错误 / %d 个警告" % (len(ERRORS), len(WARNINGS)))
        return 1
    print("\n通过：0 个错误 / %d 个警告" % len(WARNINGS))
    return 0


if __name__ == "__main__":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    sys.exit(main())
