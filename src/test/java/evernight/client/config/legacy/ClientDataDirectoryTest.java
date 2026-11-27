package evernight.client.config.legacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientDataDirectoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("目标目录已存在时直接返回，不覆盖旧数据")
    void existingDestinationIsReturnedUnchanged() throws IOException {
        Path destination = this.temporaryDirectory.resolve("evernight");
        Files.createDirectory(destination);
        Files.writeString(destination.resolve("keep.txt"), "current");

        Path legacy = this.temporaryDirectory.resolve("mio-fabric");
        Files.createDirectory(legacy);
        Files.writeString(legacy.resolve("old.txt"), "legacy");

        assertEquals(destination, ClientDataDirectory.migrateLegacy(destination));
        assertEquals("current", Files.readString(destination.resolve("keep.txt")));
        assertFalse(Files.exists(destination.resolve("old.txt")));
    }

    @Test
    @DisplayName("没有旧目录时不创建目标，直接返回")
    void missingLegacyReturnsDestination() {
        Path destination = this.temporaryDirectory.resolve("evernight");
        assertEquals(destination, ClientDataDirectory.migrateLegacy(destination));
        assertFalse(Files.exists(destination));
    }

    @Test
    @DisplayName("旧目录整树复制到目标，旧目录保留")
    void copiesLegacyTree() throws IOException {
        Path legacy = this.temporaryDirectory.resolve("mio-fabric");
        Path nested = legacy.resolve("presets");
        Files.createDirectories(nested);
        Files.writeString(legacy.resolve("modules.json"), "root");
        Files.writeString(nested.resolve("p1.json"), "preset");

        Path destination = this.temporaryDirectory.resolve("evernight");
        assertEquals(destination, ClientDataDirectory.migrateLegacy(destination));
        assertTrue(Files.isDirectory(destination));
        assertEquals("root", Files.readString(destination.resolve("modules.json")));
        assertEquals("preset", Files.readString(destination.resolve("presets/p1.json")));
        assertTrue(Files.exists(legacy.resolve("modules.json")));
    }

    @Test
    @DisplayName("目标路径是文件时抛 UncheckedIOException，不碰文件")
    void destinationAsFileFails() throws IOException {
        Path destination = this.temporaryDirectory.resolve("evernight");
        Files.writeString(destination, "not-a-directory");
        assertThrows(UncheckedIOException.class, () -> ClientDataDirectory.migrateLegacy(destination));
        assertEquals("not-a-directory", Files.readString(destination));
    }

    @Test
    @DisplayName("旧路径是文件时抛 UncheckedIOException")
    void legacyAsFileFails() throws IOException {
        Path legacy = this.temporaryDirectory.resolve("mio-fabric");
        Files.writeString(legacy, "not-a-directory");
        Path destination = this.temporaryDirectory.resolve("evernight");
        assertThrows(UncheckedIOException.class, () -> ClientDataDirectory.migrateLegacy(destination));
        assertFalse(Files.exists(destination));
    }
}
