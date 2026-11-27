package evernight.client.utils.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUtilsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("UTF-8 读写保留中文与换行")
    void utf8RoundTrip() throws IOException {
        Path path = this.temporaryDirectory.resolve("config.json");
        String text = "{\"name\":\"中文\",\"line\":1}\n第二行";
        FileUtils.writeUtf8(path, text);
        assertEquals(text, FileUtils.readUtf8(path));
        assertArrayEquals(text.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                FileUtils.readBytes(path));
    }

    @Test
    @DisplayName("原子写替换已有内容且不留下临时文件")
    void atomicWriteReplacesContentWithoutTempFiles() throws IOException {
        Path path = this.temporaryDirectory.resolve("config.json");
        FileUtils.writeUtf8(path, "old");
        FileUtils.writeUtf8Atomically(path, "new");
        assertEquals("new", FileUtils.readUtf8(path));
        try (Stream<Path> files = Files.list(this.temporaryDirectory)) {
            assertFalse(files.anyMatch(file -> file.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    @DisplayName("原子写自动创建父目录")
    void atomicWriteCreatesParentDirectories() throws IOException {
        Path path = this.temporaryDirectory.resolve("a/b/c/config.json");
        FileUtils.writeUtf8Atomically(path, "nested");
        assertTrue(Files.isRegularFile(path));
        assertEquals("nested", FileUtils.readUtf8(path));
    }

    @Test
    @DisplayName("原子写失败时删除临时文件并保留目标目录")
    void atomicWriteFailureCleansTemporaryFile() throws IOException {
        Path target = this.temporaryDirectory.resolve("target");
        Files.createDirectory(target);
        assertThrows(IOException.class, () -> FileUtils.writeUtf8Atomically(target, "data"));
        assertTrue(Files.isDirectory(target));
        try (Stream<Path> files = Files.list(this.temporaryDirectory)) {
            assertFalse(files.anyMatch(file -> {
                String name = file.getFileName().toString();
                return name.startsWith("target") && name.endsWith(".tmp");
            }));
        }
    }

    @Test
    @DisplayName("后缀查找：原文件优先，其次按后缀顺序，最后返回原路径")
    void findExistingWithSuffixes() throws IOException {
        Path base = this.temporaryDirectory.resolve("messages");
        assertFalse(Files.exists(base));

        Path suffix = this.temporaryDirectory.resolve("messages.txt");
        Files.writeString(suffix, "text");
        assertEquals(suffix, FileUtils.findExistingWithSuffixes(base, ".txt"));
        assertEquals(suffix, FileUtils.findExistingWithSuffixes(base, ".json", ".txt"));

        Files.writeString(base, "base");
        assertEquals(base, FileUtils.findExistingWithSuffixes(base, ".txt"));

        Path missing = this.temporaryDirectory.resolve("missing");
        assertEquals(missing, FileUtils.findExistingWithSuffixes(missing, ".txt", ".json"));
    }
}
