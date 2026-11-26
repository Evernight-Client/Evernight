package evernight.client.utils.vault;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * Bridge to the bundled loot engine, a {@code vaulttool} build of the cubiomes loot library that
 * mirrors the trial-chamber loot tables.
 *
 * <p>The engine ships as a Windows executable ({@code assets/evernight/vault/vaulttool.exe}); the
 * first use extracts it into {@code <game directory>/evernight/tools} and every later call reuses that
 * copy (the engine keeps its per-seed state next to itself). On other systems the same directory may
 * hold a native {@code vaulttool} build for that platform, which is used when present.
 * {@link #isSupported()} reports whether an engine is usable at all; the prediction module hides itself
 * otherwise.
 *
 * <p>Scanning and filtering happen inside the engine, so the Java side only parses a handful of
 * lines per call. Never widen this into "parse every predicted drop": a large scan would allocate
 * tens of thousands of objects on the game heap.
 */
public final class VaultLootEngine {
    private static final String RESOURCE_PATH = "/assets/evernight/vault/vaulttool.exe";
    private static final String WINDOWS_TOOL_NAME = "vaulttool.exe";
    private static final String NATIVE_TOOL_NAME = "vaulttool";
    private static final String CANDIDATE_FILE_NAME = "vault-candidates.txt";
    private static final int LIST_TIMEOUT_SECONDS = 120;
    private static final int SCAN_TIMEOUT_SECONDS = 300;

    private VaultLootEngine() {
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /**
     * Whether an engine is available on this platform.
     *
     * <p>Windows always has the bundled executable. Every other system can only use an engine the
     * player placed in {@code evernight/tools}; without one the module hides itself instead of
     * offering switches that cannot work.
     */
    public static boolean isSupported() {
        if (isWindows()) {
            return VaultLootEngine.class.getResource(RESOURCE_PATH) != null;
        }
        return Files.isRegularFile(nativeToolPath());
    }

    private static Path toolDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve("evernight").resolve("tools");
    }

    private static Path nativeToolPath() {
        return toolDirectory().resolve(NATIVE_TOOL_NAME);
    }

    /** Extracts the bundled engine (Windows) or reuses a native build, then returns its path. */
    public static Path ensure() throws IOException {
        Path directory = toolDirectory();
        Files.createDirectories(directory);
        if (!isWindows()) {
            Path nativeTool = nativeToolPath();
            if (Files.isRegularFile(nativeTool)) {
                return nativeTool;
            }
            throw new IOException("No vault loot engine found at " + nativeTool);
        }
        Path executable = directory.resolve(WINDOWS_TOOL_NAME);
        try (InputStream bundled = VaultLootEngine.class.getResourceAsStream(RESOURCE_PATH)) {
            if (bundled == null) {
                throw new IOException("Bundled loot engine resource is missing: " + RESOURCE_PATH);
            }
            byte[] data = bundled.readAllBytes();
            if (!Files.exists(executable) || Files.size(executable) != data.length) {
                Files.write(executable, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
            }
        }
        return executable;
    }

    /**
     * Scans {@code limit} vault openings starting at {@code offset} and returns at most {@code amount}
     * hits for the semicolon-separated item ids in {@code targetSpec}.
     */
    public static List<VaultLootLogic.Hit> search(long seed, boolean ominous, int offset, int limit, int amount,
                                                  String targetSpec) throws IOException {
        List<String> lines = collect(ensure(), "search", Long.toString(seed), ominous ? "1" : "0",
                Integer.toString(offset), Integer.toString(limit), Integer.toString(amount), targetSpec);
        List<VaultLootLogic.Hit> hits = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith("HIT ")) {
                continue;
            }
            String[] parts = line.split(" ");
            if (parts.length < 4) {
                continue;
            }
            try {
                hits.add(new VaultLootLogic.Hit(Integer.parseInt(parts[1]), parts[2], Integer.parseInt(parts[3])));
            } catch (NumberFormatException ignoredMalformedLine) {
            }
        }
        return hits;
    }

    /** Every offset below {@code maxScan} whose drops match {@code spec} in arrival order. */
    public static List<Integer> matches(long seed, boolean ominous, int maxScan, String spec,
                                        IntConsumer progress) throws IOException {
        List<Integer> offsets = new ArrayList<>();
        stream(ensure(), progress, line -> {
            if (!line.startsWith("M ")) {
                return;
            }
            try {
                offsets.add(Integer.parseInt(line.substring(2).trim()));
            } catch (NumberFormatException ignoredMalformedLine) {
            }
        }, "matches", Long.toString(seed), ominous ? "1" : "0", Integer.toString(maxScan), spec);
        return offsets;
    }

    /**
     * Keeps the candidates that stay reachable from at least one previous candidate within
     * {@code delta} openings, scanning only {@code [rangeStart, rangeEnd)}.
     */
    public static List<Integer> narrow(long seed, boolean ominous, int rangeStart, int rangeEnd, int delta,
                                       String spec, List<Integer> candidates, IntConsumer progress) throws IOException {
        Path directory = toolDirectory();
        Files.createDirectories(directory);
        Path candidateFile = directory.resolve(CANDIDATE_FILE_NAME);
        StringBuilder text = new StringBuilder();
        for (Integer candidate : candidates) {
            text.append(candidate).append('\n');
        }
        Files.writeString(candidateFile, text.toString(), StandardCharsets.UTF_8);
        List<Integer> kept = new ArrayList<>();
        stream(ensure(), progress, line -> {
            if (!line.startsWith("KEEP ")) {
                return;
            }
            try {
                kept.add(Integer.parseInt(line.substring(5).trim()));
            } catch (NumberFormatException ignoredMalformedLine) {
            }
        }, "narrow", Long.toString(seed), ominous ? "1" : "0", Integer.toString(rangeStart),
                Integer.toString(rangeEnd), Integer.toString(delta), spec, candidateFile.toAbsolutePath().toString());
        return kept;
    }

    private interface LineSink {
        void accept(String line);
    }

    private static void stream(Path executable, IntConsumer progress, LineSink sink, String... arguments)
            throws IOException {
        Process process = start(executable, arguments);
        int producedLines = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                producedLines++;
                if (line.startsWith("PROGRESS ")) {
                    try {
                        progress.accept(Integer.parseInt(line.substring(9).trim()));
                    } catch (NumberFormatException ignoredMalformedLine) {
                    }
                } else {
                    sink.accept(line);
                }
            }
        }
        awaitExit(process, SCAN_TIMEOUT_SECONDS, producedLines);
    }

    private static List<String> collect(Path executable, String... arguments) throws IOException {
        Process process = start(executable, arguments);
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        awaitExit(process, LIST_TIMEOUT_SECONDS, lines.size());
        return lines;
    }

    private static Process start(Path executable, String... arguments) throws IOException {
        List<String> command = new ArrayList<>(arguments.length + 1);
        command.add(executable.toAbsolutePath().toString());
        for (String argument : arguments) {
            command.add(argument);
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(executable.getParent().toFile());
        builder.redirectErrorStream(true);
        return builder.start();
    }

    /**
     * Waits for the engine and rejects a silent non-zero exit.
     *
     * <p>The engine may legitimately report "nothing found" through its exit code while still printing
     * the lines the caller parses, so only a run that printed nothing is treated as a failure.
     */
    private static void awaitExit(Process process, int timeoutSeconds, int producedLines) throws IOException {
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Loot engine timed out after " + timeoutSeconds + " seconds");
            }
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return;
        }
        int exitCode = process.exitValue();
        if (exitCode != 0 && producedLines == 0) {
            throw new IOException("Loot engine exited with code " + exitCode);
        }
    }
}
