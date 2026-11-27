package evernight.client.render.text;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resource level checks for {@code en_us.json} and {@code zh_cn.json}.
 *
 * <p>Runs as part of {@code gradlew test} / {@code gradlew build}, so a broken language table fails
 * the build. It replaced the former {@code check-lang.py} script and covers the whole rule set: JSON
 * shape, duplicate keys, the twelve key sections, key set and section order parity, empty values,
 * angle tags, stray backslashes, verbatim English copies, placeholder arity (including the reviewed
 * Chinese plural suffix omissions) and keys referenced from Java that are missing from the table.
 */
class LanguageTableTest {
    private static final String REPOSITORY_ROOT_MARKER =
            "src/main/resources/assets/evernight/lang";

    /** Section prefixes, in the order the table lists them. */
    private static final List<String> SECTIONS = List.of(
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
            "evernight.ui.commands");
    /** Vanilla keys may coexist because Minecraft reads the same files for keybinds. */
    private static final List<String> VANILLA_PREFIXES = List.of("key.", "category.");
    /** Conversion specifiers only; width and precision do not matter. */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "%(?:(\\d+)\\$)?[-#+0,(<]*\\d*(?:\\.\\d+)?([sSdfxXoberaAcCeEgGtTn%])");
    private static final Pattern ANGLE_TAG_PATTERN =
            Pattern.compile("<([A-Za-z_][A-Za-z0-9_]*)>");
    private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z]{3,}\\s+[A-Za-z]{2,}");
    /**
     * {@code Translations.<method>("literal", ...)} calls whose first argument is a literal key
     * fragment; dynamic concatenations are skipped by {@link #resolveKey}.
     */
    private static final Pattern TRANSLATION_CALL_PATTERN = Pattern.compile(
            "Translations\\.(ui|format|module|setting|value|category|panel|sound|moduleDescription"
                    + "|get|find)\\(\\s*\"([^\"]*)\"");
    /** Full client-owned keys passed to {@code LocalizedNames.clientChinese}. */
    private static final Pattern CLIENT_CHINESE_PATTERN =
            Pattern.compile("clientChinese\\(\\s*\"(evernight\\.[^\"]+)\"");
    private static final Map<String, String> KEY_PREFIXES = Map.ofEntries(
            Map.entry("ui", "evernight.ui."),
            Map.entry("format", "evernight.ui."),
            Map.entry("module", "evernight.names.modules."),
            Map.entry("setting", "evernight.names.settings."),
            Map.entry("value", "evernight.names.values."),
            Map.entry("category", "evernight.names.categories."),
            Map.entry("panel", "evernight.names.panels."),
            Map.entry("sound", "evernight.names.sounds."),
            Map.entry("moduleDescription", "evernight.descriptions.modules."));
    /** {@code LanguageTable} only turns these two tags into colors. */
    private static final Set<String> SUPPORTED_TAGS = Set.of("red", "yellow");
    /** Common color names that are easy to assume support but stay literal text. */
    private static final Set<String> COMMON_COLOR_NAMES = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "grey", "dark_gray", "dark_grey", "blue", "green", "aqua",
            "red", "light_purple", "pink", "yellow", "white", "reset", "bold",
            "italic", "underline", "strikethrough", "obfuscated");
    /** Proper nouns that legitimately stay identical across both languages. */
    private static final Set<String> PROPER_NOUNS = Set.of(
            "esp", "hud", "gui", "api", "url", "tps", "fps", "cps", "b/s", "km/h",
            "baritone", "discord", "telegram", "webhook", "imgur", "clientid",
            "sodium", "lithium", "viafabricplus", "meteor", "smtc", "mpris", "dbus",
            "ncp", "grim", "grimv3", "vivaldi", "2b2t", "constantiam", "folia",
            "deepseek", "evernight", "xaero", "linux", "macos", "windows",
            "alt", "shift", "ctrl", "mlg", "cev", "xqz", "bps", "paper", "xyz");
    /**
     * Chinese has no English plural suffix, so these keys intentionally use one {@code %s} less than
     * their English counterpart. Every other argument still has to line up.
     */
    private static final Set<String> PLURAL_SUFFIX_KEYS = Set.of(
            "evernight.ui.commands.Color.ListHeader",
            "evernight.ui.commands.TwoB2tApi.Day",
            "evernight.ui.commands.TwoB2tApi.Hour",
            "evernight.ui.commands.TwoB2tApi.Minute",
            "evernight.ui.commands.TwoB2tApi.Second");

    private static LangFile english;
    private static LangFile chinese;

    @BeforeAll
    static void loadLanguageTables() {
        Path languageDirectory = repositoryRoot().resolve(REPOSITORY_ROOT_MARKER);
        Path englishPath = languageDirectory.resolve("en_us.json");
        Path chinesePath = languageDirectory.resolve("zh_cn.json");
        assertTrue(Files.isRegularFile(englishPath), "缺少语言表: " + englishPath);
        assertTrue(Files.isRegularFile(chinesePath), "缺少语言表: " + chinesePath);
        english = load(englishPath);
        chinese = load(chinesePath);
    }

    @Test
    @DisplayName("语言表是合法 JSON：UTF-8、无 BOM、无重复键、值均为字符串")
    void tablesAreValidJson() {
        assertNoProblems(english, "en_us.json");
        assertNoProblems(chinese, "zh_cn.json");
        assertFalse(english.entries().isEmpty(), "en_us.json 没有任何键");
        assertFalse(chinese.entries().isEmpty(), "zh_cn.json 没有任何键");
    }

    @Test
    @DisplayName("所有键都属于 12 个 section 或原版 key./category. 前缀")
    void keysBelongToKnownSections() {
        List<String> issues = new ArrayList<>();
        collectUnknownSections(english, "en_us.json", issues);
        collectUnknownSections(chinese, "zh_cn.json", issues);
        assertTrue(issues.isEmpty(), String.join("\n", issues));
    }

    @Test
    @DisplayName("两份语言表的 section 集合与出现顺序一致")
    void sectionsAreConsistent() {
        List<String> englishSections = sectionsInOrder(english.entries());
        List<String> chineseSections = sectionsInOrder(chinese.entries());
        Set<String> missingInChinese = new TreeSet<>(englishSections);
        missingInChinese.removeAll(chineseSections);
        Set<String> missingInEnglish = new TreeSet<>(chineseSections);
        missingInEnglish.removeAll(englishSections);
        List<String> issues = new ArrayList<>();
        if (!missingInChinese.isEmpty()) {
            issues.add("zh_cn.json 缺少 section: " + String.join(", ", missingInChinese));
        }
        if (!missingInEnglish.isEmpty()) {
            issues.add("en_us.json 缺少 section: " + String.join(", ", missingInEnglish));
        }
        if (issues.isEmpty() && !englishSections.equals(chineseSections)) {
            issues.add("两份语言表的 section 顺序不一致（或同一 section 的键不连续）");
        }
        assertTrue(issues.isEmpty(), String.join("\n", issues));
    }

    @Test
    @DisplayName("两份语言表的键集完全相同")
    void keySetsMatch() {
        Set<String> missingInChinese = new TreeSet<>(english.entries().keySet());
        missingInChinese.removeAll(chinese.entries().keySet());
        Set<String> missingInEnglish = new TreeSet<>(chinese.entries().keySet());
        missingInEnglish.removeAll(english.entries().keySet());
        List<String> issues = new ArrayList<>();
        missingInChinese.forEach(key -> issues.add("zh_cn.json 缺少键: " + key));
        missingInEnglish.forEach(key -> issues.add("en_us.json 缺少键: " + key));
        assertTrue(issues.isEmpty(), String.join("\n", issues));
    }

    @Test
    @DisplayName("语言表没有空值")
    void valuesAreNotEmpty() {
        List<String> issues = new ArrayList<>();
        collectEmptyValues(english, "en_us.json", issues);
        collectEmptyValues(chinese, "zh_cn.json", issues);
        assertTrue(issues.isEmpty(), String.join("\n", issues));
    }

    @Test
    @DisplayName("尖括号只使用 <red>/<yellow>，其余会原样显示")
    void angleTagsAreSupported() {
        List<String> issues = new ArrayList<>();
        collectUnsupportedTags(english, "en_us.json", issues);
        collectUnsupportedTags(chinese, "zh_cn.json", issues);
        assertTrue(issues.isEmpty(), String.join("\n", issues));
    }

    @Test
    @DisplayName("值里没有反斜杠（换行请用 JSON 的 \\n 转义）")
    void valuesContainNoRawBackslash() {
        List<String> issues = new ArrayList<>();
        collectRawBackslashes(english, "en_us.json", issues);
        collectRawBackslashes(chinese, "zh_cn.json", issues);
        assertTrue(issues.isEmpty(), String.join("\n", issues));
    }

    @Test
    @DisplayName("中文没有直接照抄英文")
    void translationsAreNotVerbatimEnglish() {
        List<String> issues = new ArrayList<>();
        for (String key : sortedIntersection()) {
            String englishValue = english.entries().get(key);
            if (!englishValue.equals(chinese.entries().get(key))) {
                continue;
            }
            String sectionKey = key.startsWith("evernight.")
                    ? key.substring("evernight.".length())
                    : key;
            if (!sectionKey.startsWith("descriptions.") && !sectionKey.startsWith("ui.")) {
                continue;
            }
            if (PROPER_NOUNS.contains(englishValue.strip().toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (!WORD_PATTERN.matcher(englishValue).find()) {
                continue;
            }
            issues.add("zh_cn.json 直接照抄了英文: " + key + " = \"" + englishValue + "\"");
        }
        assertTrue(issues.isEmpty(), String.join("\n", issues));
    }

    @Test
    @DisplayName("占位符参数对齐：zh 不越界，个数差异仅限已登记的复数后缀")
    void placeholdersAreConsistent() {
        List<String> issues = new ArrayList<>();
        for (String key : sortedIntersection()) {
            int[] englishProfile = placeholderProfile(english.entries().get(key));
            int[] chineseProfile = placeholderProfile(chinese.entries().get(key));
            if (chineseProfile[0] > englishProfile[0]) {
                issues.add("zh_cn 引用了第 " + chineseProfile[0] + " 个参数，但 en_us 只有 "
                        + englishProfile[0] + " 个，String.format 会抛异常: " + key);
                continue;
            }
            if (chineseProfile[1] == englishProfile[1]) {
                continue;
            }
            boolean reviewedPluralSuffix = PLURAL_SUFFIX_KEYS.contains(key)
                    && chineseProfile[1] == englishProfile[1] - 1;
            if (!reviewedPluralSuffix) {
                issues.add("占位符个数不一致（en " + englishProfile[1] + " / zh "
                        + chineseProfile[1] + "）: " + key);
            }
        }
        assertTrue(issues.isEmpty(), String.join("\n", issues));
    }

    @Test
    @DisplayName("代码里静态引用的语言键都存在于两份语言表")
    void referencedKeysExistInBothTables() {
        Path sourceRoot = repositoryRoot().resolve("src/main/java");
        assertTrue(Files.isDirectory(sourceRoot), "缺少源码目录: " + sourceRoot);
        List<String> missing = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(sourceRoot)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text;
                try {
                    text = Files.readString(source, StandardCharsets.UTF_8);
                } catch (IOException failure) {
                    missing.add(source + " 读取失败（需要 UTF-8）：" + failure.getMessage());
                    continue;
                }
                collectMissingKeys(sourceRoot, source, text, missing);
            }
        } catch (IOException failure) {
            throw new AssertionError("遍历源码目录失败: " + sourceRoot, failure);
        }
        assertTrue(missing.isEmpty(),
                "以下键在代码中被引用但语言表缺失：\n" + String.join("\n", missing));
    }

    private static void collectMissingKeys(Path sourceRoot, Path source, String text,
                                           List<String> missing) {
        String relative = sourceRoot.relativize(source).toString().replace('\\', '/');
        Matcher translationCalls = TRANSLATION_CALL_PATTERN.matcher(text);
        while (translationCalls.find()) {
            String key = resolveKey(translationCalls.group(1), translationCalls.group(2), text,
                    translationCalls.end());
            if (key != null) {
                reportIfMissing(relative, lineOf(text, translationCalls.start()), key, missing);
            }
        }
        Matcher clientChineseCalls = CLIENT_CHINESE_PATTERN.matcher(text);
        while (clientChineseCalls.find()) {
            String following = text.substring(clientChineseCalls.end()).stripLeading();
            if (following.startsWith("+")) {
                continue;
            }
            reportIfMissing(relative, lineOf(text, clientChineseCalls.start()),
                    clientChineseCalls.group(1), missing);
        }
    }

    /**
     * @return the full key referenced by a literal translation call, or {@code null} when the call
     *         builds its key at runtime or targets a foreign (addon) namespace
     */
    private static String resolveKey(String method, String literal, String text, int callEnd) {
        String following = text.substring(callEnd).stripLeading();
        if (following.startsWith("+")) {
            return null;
        }
        if (literal.isEmpty() || literal.endsWith(".")) {
            return null;
        }
        if ("get".equals(method) || "find".equals(method)) {
            return literal.startsWith("evernight.") ? literal : null;
        }
        String prefix = KEY_PREFIXES.get(method);
        return prefix == null ? null : prefix + literal;
    }

    private static void reportIfMissing(String relative, int line, String key,
                                        List<String> missing) {
        if (!english.entries().containsKey(key) || !chinese.entries().containsKey(key)) {
            missing.add(relative + ":" + line + " -> " + key);
        }
    }

    private static int lineOf(String text, int index) {
        int line = 1;
        for (int position = 0; position < index; position++) {
            if (text.charAt(position) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static void assertNoProblems(LangFile file, String name) {
        assertTrue(file.problems().isEmpty(),
                name + ":\n" + String.join("\n", file.problems()));
    }

    private static void collectUnknownSections(LangFile file, String name, List<String> issues) {
        for (String key : file.entries().keySet()) {
            if (isVanillaKey(key)) {
                continue;
            }
            if (sectionOf(key) == null) {
                issues.add(name + " 的键不属于 12 个 section（不会被读取）: " + key);
            }
        }
    }

    private static void collectEmptyValues(LangFile file, String name, List<String> issues) {
        for (Map.Entry<String, String> entry : file.entries().entrySet()) {
            if (entry.getValue().isBlank()) {
                issues.add(name + " 空值: " + entry.getKey());
            }
        }
    }

    private static void collectUnsupportedTags(LangFile file, String name, List<String> issues) {
        for (Map.Entry<String, String> entry : file.entries().entrySet()) {
            Matcher matcher = ANGLE_TAG_PATTERN.matcher(entry.getValue());
            while (matcher.find()) {
                String tag = matcher.group(1).toLowerCase(Locale.ROOT);
                if (COMMON_COLOR_NAMES.contains(tag) && !SUPPORTED_TAGS.contains(tag)) {
                    issues.add(name + " 写了 <" + matcher.group(1)
                            + ">，但只会认 <red>/<yellow>，它会原样显示: " + entry.getKey());
                }
            }
        }
    }

    private static void collectRawBackslashes(LangFile file, String name, List<String> issues) {
        for (Map.Entry<String, String> entry : file.entries().entrySet()) {
            if (entry.getValue().indexOf('\\') >= 0) {
                issues.add(name + " 值里有反斜杠，JSON 解析后会原样显示（换行请写 \\n 转义）: "
                        + entry.getKey());
            }
        }
    }

    private static List<String> sortedIntersection() {
        Set<String> keys = new TreeSet<>(english.entries().keySet());
        keys.retainAll(chinese.entries().keySet());
        return new ArrayList<>(keys);
    }

    private static String sectionOf(String key) {
        if (isVanillaKey(key)) {
            return null;
        }
        String bestMatch = null;
        for (String section : SECTIONS) {
            if (key.startsWith(section + ".")
                    && (bestMatch == null || section.length() > bestMatch.length())) {
                bestMatch = section;
            }
        }
        return bestMatch;
    }

    private static boolean isVanillaKey(String key) {
        for (String prefix : VANILLA_PREFIXES) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> sectionsInOrder(Map<String, String> entries) {
        List<String> order = new ArrayList<>();
        for (String key : entries.keySet()) {
            String section = sectionOf(key);
            if (section == null) {
                continue;
            }
            if (order.isEmpty() || !order.getLast().equals(section)) {
                order.add(section);
            }
        }
        return order;
    }

    /**
     * @return {@code {highest referenced argument, total placeholder count}}; explicit indexes like
     *         {@code %1$d ... %3$s} yield {@code {3, 2}}
     */
    private static int[] placeholderProfile(String value) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        int total = 0;
        int highestExplicitIndex = 0;
        while (matcher.find()) {
            if ("%".equals(matcher.group(2))) {
                continue;
            }
            total++;
            if (matcher.group(1) != null) {
                highestExplicitIndex = Math.max(highestExplicitIndex,
                        Integer.parseInt(matcher.group(1)));
            }
        }
        if (highestExplicitIndex > 0) {
            return new int[] {highestExplicitIndex, total};
        }
        return new int[] {total, total};
    }

    private static LangFile load(Path path) {
        List<String> problems = new ArrayList<>();
        Map<String, String> entries = new LinkedHashMap<>();
        String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            problems.add("读取失败（文件必须是 UTF-8）: " + failure);
            return new LangFile(entries, problems);
        }
        if (text.startsWith("\uFEFF")) {
            problems.add("带 UTF-8 BOM，Gson 会把 BOM 当成 JSON 内容而解析失败");
        }
        JsonReader reader = new JsonReader(new StringReader(text));
        reader.setStrictness(Strictness.STRICT);
        try {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                problems.add("顶层必须是 JSON 对象");
                return new LangFile(entries, problems);
            }
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (entries.containsKey(key)) {
                    problems.add("重复键（Gson 保留后者）: " + key);
                }
                if (reader.peek() == JsonToken.STRING) {
                    entries.put(key, reader.nextString());
                } else {
                    problems.add("值必须是字符串: " + key);
                    reader.skipValue();
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                problems.add("JSON 结束后还有多余内容");
            }
        } catch (IOException | RuntimeException failure) {
            problems.add("不是合法 JSON: " + failure.getMessage());
        }
        return new LangFile(entries, problems);
    }

    /**
     * Walks up from the working directory so the test also works from an IDE that starts in a
     * subdirectory. Gradle runs tests with the project directory as working directory.
     */
    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve(REPOSITORY_ROOT_MARKER))) {
                return candidate;
            }
        }
        throw new IllegalStateException("找不到仓库根目录（需要包含 " + REPOSITORY_ROOT_MARKER
                + "），当前目录: " + current);
    }

    private record LangFile(Map<String, String> entries, List<String> problems) {
    }
}
