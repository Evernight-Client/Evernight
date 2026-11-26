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
 * Bridge to the loot engine, a {@code vaulttool} CLI built from the upstream cubiomes loot library.
 *
 * <p>The engine itself lives in a separate repository ({@code evernight-vault-engine}) and is built
 * there for every supported platform; this project only downloads those release binaries during the
 * build and packs them under {@code assets/evernight/vault/}. Nothing is committed here, and a local
 * build without the downloaded binaries simply produces a jar without an engine.
 *
 * <p>On first use the engine for the running platform is extracted into
 * {@code <game directory>/evernight/tools} and reused afterwards (the engine keeps its per-seed state
 * next to itself). A player-supplied {@code vaulttool} / {@code vaulttool.exe} in that directory takes
 * precedence, which also covers platforms this project does not build for. {@link #isSupported()}
 * reports whether any engine is usable; the prediction module hides itself otherwise.
 *
 * <p>Scanning and filtering happen inside the engine, so the Java side only parses a handful of
 * lines per call. Never widen this into "parse every predicted drop": a large scan would allocate
 * tens of thousands of objects on the game heap.
 */
public final class VaultLootEngine {
    private static final String RESOURCE_PREFIX = "/assets/evernight/vault/vaulttool-";
    private static final String EXTERNAL_TOOL_NAME = "vaulttool";
    private static final String EXTERNAL_WINDOWS_TOOL_NAME = "vaulttool.exe";
    private static final String CANDIDATE_FILE_NAME = "vault-candidates.txt";
    private static final int LIST_TIMEOUT_SECONDS = 120;
    private static final int SCAN_TIMEOUT_SECONDS = 300;

    /** Extracted engine of this session, so repeated calls do not re-read the bundled binary. */
    private static volatile Path resolved;

    private VaultLootEngine() {
    }

    private static boolean isAndroid() {
        String vendor = System.getProperty("java.vendor", "").toLowerCase(Locale.ROOT);
        String vmName = System.getProperty("java.vm.name", "").toLowerCase(Locale.ROOT);
        return vendor.contains("android") || vmName.contains("dalvik")
                || System.getProperty("os.name", "").equalsIgnoreCase("android");
    }

    private static boolean isAarch64() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return arch.contains("aarch64") || arch.contains("arm64");
    }

    /**
     * Name of the bundled engine for the running platform, or {@code null} when this project ships no
     * build for it (32-bit JVMs included).
     *
     * <p>Android is deliberately unsupported: modern Android versions refuse to execute binaries
     * extracted into app-writable storage, so the prediction module hides itself there instead of
     * offering switches that cannot work. Environments that expose a normal Linux JVM (Pojav and
     * similar) still resolve to the {@code linux-*} build.
     */
    static String platformName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!arch.contains("64") || isAndroid()) {
            return null;
        }
        if (os.startsWith("windows")) {
            return "windows-x86_64";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "macos-universal";
        }
        if (os.contains("linux")) {
            return isAarch64() ? "linux-aarch64" : "linux-x86_64";
        }
        return null;
    }

    /** Whether a loot engine can run here, either bundled or supplied by the player. */
    public static boolean isSupported() {
        if (externalTool() != null) {
            return true;
        }
        String platform = platformName();
        return platform != null && bundledResource(platform) != null;
    }

    private static Path toolDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve("evernight").resolve("tools");
    }

    /** Player-supplied engine in {@code evernight/tools}, or {@code null}. */
    private static Path externalTool() {
        Path directory = toolDirectory();
        Path nativeTool = directory.resolve(EXTERNAL_TOOL_NAME);
        if (Files.isRegularFile(nativeTool)) {
            return nativeTool;
        }
        Path windowsTool = directory.resolve(EXTERNAL_WINDOWS_TOOL_NAME);
        return Files.isRegularFile(windowsTool) ? windowsTool : null;
    }

    private static String bundledResourcePath(String platform) {
        return RESOURCE_PREFIX + platform + (platform.startsWith("windows") ? ".exe" : "");
    }

    private static java.net.URL bundledResource(String platform) {
        return VaultLootEngine.class.getResource(bundledResourcePath(platform));
    }

    /** Returns a runnable engine path, extracting the bundled build on first use. */
    public static Path ensure() throws IOException {
        Path cached = resolved;
        if (cached != null && Files.isRegularFile(cached)) {
            return cached;
        }
        Path external = externalTool();
        if (external != null) {
            resolved = external;
            return external;
        }
        String platform = platformName();
        if (platform == null) {
            throw new IOException("No loot engine is available for " + System.getProperty("os.name") + "/"
                    + System.getProperty("os.arch"));
        }
        String resourcePath = bundledResourcePath(platform);
        Path directory = toolDirectory();
        Files.createDirectories(directory);
        Path executable = directory.resolve(EXTERNAL_TOOL_NAME + "-" + platform
                + (platform.startsWith("windows") ? ".exe" : ""));
        try (InputStream bundled = VaultLootEngine.class.getResourceAsStream(resourcePath)) {
            if (bundled == null) {
                throw new IOException("Bundled loot engine resource is missing: " + resourcePath);
            }
            byte[] data = bundled.readAllBytes();
            boolean unchanged = Files.isRegularFile(executable) && Files.size(executable) == data.length;
            if (!unchanged) {
                Files.write(executable, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
            }
            if (!platform.startsWith("windows")) {
                markExecutable(executable);
            }
        }
        resolved = executable;
        return executable;
    }

    private static void markExecutable(Path executable) {
        try {
            Files.setPosixFilePermissions(executable,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-x---"));
        } catch (UnsupportedOperationException | IOException ignoredFileSystem) {
            // Windows and other non-POSIX file systems do not need (or support) the permission bits.
        }
    }

    /**
     * Scans {@code limit} vault openings starting at {@code offset} and returns at most {@code amount}
     * hits for the semicolon-separated item ids in {@code targetSpec}.
     *
     * @param lootVersion loot-table generation to simulate, passed to the engine as
     *                    {@code VAULT_MC_VERSION}; older engines ignore it and keep their built-in set
     */
    public static List<VaultLootLogic.Hit> search(long seed, boolean ominous, int offset, int limit, int amount,
                                                  String targetSpec, String lootVersion) throws IOException {
        List<String> lines = collect(ensure(), lootVersion, "search", Long.toString(seed), ominous ? "1" : "0",
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
                                        IntConsumer progress, String lootVersion) throws IOException {
        List<Integer> offsets = new ArrayList<>();
        stream(ensure(), lootVersion, progress, line -> {
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
                                       String spec, List<Integer> candidates, IntConsumer progress,
                                       String lootVersion) throws IOException {
        Path directory = toolDirectory();
        Files.createDirectories(directory);
        Path candidateFile = directory.resolve(CANDIDATE_FILE_NAME);
        StringBuilder text = new StringBuilder();
        for (Integer candidate : candidates) {
            text.append(candidate).append('\n');
        }
        Files.writeString(candidateFile, text.toString(), StandardCharsets.UTF_8);
        List<Integer> kept = new ArrayList<>();
        stream(ensure(), lootVersion, progress, line -> {
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

    private static void stream(Path executable, String lootVersion, IntConsumer progress, LineSink sink,
                               String... arguments) throws IOException {
        Process process = start(executable, lootVersion, arguments);
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

    private static List<String> collect(Path executable, String lootVersion, String... arguments) throws IOException {
        Process process = start(executable, lootVersion, arguments);
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

    private static Process start(Path executable, String lootVersion, String... arguments) throws IOException {
        List<String> command = new ArrayList<>(arguments.length + 1);
        command.add(executable.toAbsolutePath().toString());
        for (String argument : arguments) {
            command.add(argument);
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(executable.getParent().toFile());
        builder.redirectErrorStream(true);
        if (lootVersion != null && !lootVersion.isBlank()) {
            builder.environment().put("VAULT_MC_VERSION", lootVersion);
        }
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
