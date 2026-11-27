package evernight.client.utils.core;

import evernight.client.render.text.ReverseStringFormatter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class FileUtils {
    private static final String suffixPathPattern;

    static {
        suffixPathPattern = "\u0001\u0001";
    }

    public static byte[] readBytes(Path path) throws IOException {
        FileInputStream input = null;
        byte[] bytes;
        try {
            input = new FileInputStream(path.toFile());
            bytes = input.readAllBytes();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignoredCloseFailure) {
                }
            }
        }
        return bytes;
    }

    public static Path findExistingWithSuffixes(Path path, String... suffixes) {
        File baseFile = path.toFile();
        if (baseFile.exists()) {
            return path;
        }
        for (String suffix : suffixes) {
            String pathText = String.valueOf(path);
            String candidatePathText = new ReverseStringFormatter().prepend(suffix).prepend(pathText)
                    .format(suffixPathPattern);
            Path candidatePath = Path.of(candidatePathText);
            File candidateFile = candidatePath.toFile();
            if (candidateFile.exists()) {
                return candidatePath;
            }
        }
        return path;
    }

    public static void writeBytes(Path path, byte[] bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(path.toFile())) {
            output.write(bytes);
        }
    }

    public static void writeUtf8(Path path, String text) throws IOException {
        Charset charset = StandardCharsets.UTF_8;
        writeBytes(path, text.getBytes(charset));
    }

    public static String readUtf8(Path path) throws IOException {
        return new String(readBytes(path), StandardCharsets.UTF_8);
    }

    /**
     * Writes {@code text} to a sibling temporary file and then replaces {@code path} with it, so a
     * crash or kill during the write cannot leave a truncated file behind.
     *
     * <p>When the file system cannot rename atomically the replacement still happens through a normal
     * move, which keeps the torn-write protection for the common case.
     */
    public static void writeUtf8Atomically(Path path, String text) throws IOException {
        Path target = path.toAbsolutePath();
        Path directory = target.getParent();
        if (directory == null) {
            throw new IOException("Cannot resolve the parent directory of " + path);
        }
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupportedAtomicMove) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}