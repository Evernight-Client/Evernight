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
 * <p>Android runs the statically linked {@code android-aarch64} build: it needs neither bionic's
 * dynamic loader nor the device's libc. Whether it can run at all depends on the launcher, because
 * Android 10+ only permits {@code exec()} from app storage for apps that target API 28 or older, so
 * {@link #isSupported()} starts the engine once and hides the module when that is refused.
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
    /** Enough of a binary to read the ELF/PE header that identifies its architecture. */
    private static final int HEADER_BYTES = 512;
    private static final int PROBE_TIMEOUT_SECONDS = 5;

    /** Extracted engine of this session, so repeated calls do not re-read the bundled binary. */
    private static volatile Path resolved;

    /** Result of the Android exec probe; the platform cannot change within one session. */
    private static volatile Boolean androidEngineRuns;

    private VaultLootEngine() {
    }

    private static boolean isAndroid() {
        String vendor = System.getProperty("java.vendor", "").toLowerCase(Locale.ROOT);
        String vmName = System.getProperty("java.vm.name", "").toLowerCase(Locale.ROOT);
        if (vendor.contains("android") || vmName.contains("dalvik")
                || System.getProperty("os.name", "").equalsIgnoreCase("android")) {
            return true;
        }
        try {
            // Android exports these to every app process, and launchers that host an ordinary Linux
            // JVM (Pojav and similar) keep them while still reporting os.name = "Linux".
            String root = System.getenv("ANDROID_ROOT");
            String data = System.getenv("ANDROID_DATA");
            if ((root != null && !root.isEmpty()) || (data != null && !data.isEmpty())) {
                return true;
            }
            return Files.isRegularFile(Path.of("/system/build.prop"));
        } catch (RuntimeException unavailableEnvironment) {
            return false;
        }
    }

    private static boolean isAarch64() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return arch.contains("aarch64") || arch.contains("arm64");
    }

    /**
     * Name of the bundled engine for the running platform, or {@code null} when this project ships no
     * build for it (32-bit JVMs included).
     *
     * <p>Android gets its own static build instead of the {@code linux-*} one: a glibc binary cannot
     * start there even when the launcher allows {@code exec()}, and the static bionic build needs no
     * dynamic loader at all. Windows on ARM gets a native PE ARM64 build; an x86_64 JVM under
     * emulation still resolves to {@code windows-x86_64}, which works.
     */
    static String platformName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!arch.contains("64")) {
            return null;
        }
        if (isAndroid()) {
            return isAarch64() ? "android-aarch64" : null;
        }
        if (os.startsWith("windows")) {
            return isAarch64() ? "windows-aarch64" : "windows-x86_64";
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
        if (platform == null || !bundledEngineLooksRunnable(platform)) {
            return false;
        }
        return !platform.startsWith("android") || androidEngineExecutes();
    }

    /**
     * Starts the bundled engine once to prove that {@code exec()} is actually permitted.
     *
     * <p>Android 10+ labels app storage {@code execute}-denied for apps targeting API 29 or newer, so
     * a launcher decides whether any spawned binary can run; the engine answers by exiting with its
     * usage message. A refusal is a property of the environment, not of the engine, which is why the
     * module hides instead of offering settings that cannot work.
     */
    private static boolean androidEngineExecutes() {
        Boolean cached = androidEngineRuns;
        if (cached != null) {
            return cached;
        }
        boolean runs = false;
        try {
            Path executable = ensure();
            Process process = new ProcessBuilder(executable.toAbsolutePath().toString())
                    .directory(executable.getParent().toFile())
                    .redirectErrorStream(true)
                    .start();
            try {
                runs = process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                process.destroyForcibly();
            }
        } catch (IOException deniedOrUnreadable) {
            runs = false;
        }
        androidEngineRuns = runs;
        return runs;
    }

    /**
     * Rejects a bundled engine whose header does not match the platform's executable format and
     * architecture.
     *
     * <p>The engines are cross-compiled in CI, and a misconfigured toolchain once produced a Linux
     * ELF named {@code *.exe}. Checking the magic and the machine hides the module instead of failing
     * at runtime, and it also catches a host binary shipped under an {@code *-aarch64} name.
     */
    private static boolean bundledEngineLooksRunnable(String platform) {
        try (InputStream bundled = VaultLootEngine.class.getResourceAsStream(bundledResourcePath(platform))) {
            if (bundled == null) {
                return false;
            }
            return matchesPlatformHeader(platform, bundled.readNBytes(HEADER_BYTES));
        } catch (IOException unreadableResource) {
            return false;
        }
    }

    private static boolean matchesPlatformHeader(String platform, byte[] header) {
        if (header.length < 4) {
            return false;
        }
        if (platform.startsWith("windows")) {
            return isWindowsPeFor(platform, header);
        }
        if (platform.startsWith("linux") || platform.startsWith("android")) {
            return isElfFor(platform, header);
        }
        // Mach-O: thin little-endian (cffaedfe), thin big-endian (feedfacf), or fat (cafebabe).
        int first = header[0] & 0xFF;
        int second = header[1] & 0xFF;
        int third = header[2] & 0xFF;
        int fourth = header[3] & 0xFF;
        return (first == 0xCF && second == 0xFA && third == 0xED && fourth == 0xFE)
                || (first == 0xFE && second == 0xED && third == 0xFA && fourth == 0xCF)
                || (first == 0xCA && second == 0xFE && third == 0xBA && fourth == 0xBE);
    }

    /** ELF magic plus {@code e_machine}: 0xB7 is AArch64, 0x3E is x86-64. */
    private static boolean isElfFor(String platform, byte[] header) {
        if (header[0] != 0x7F || header[1] != 'E' || header[2] != 'L' || header[3] != 'F') {
            return false;
        }
        if (header.length < 20) {
            return false;
        }
        int machine = (header[18] & 0xFF) | ((header[19] & 0xFF) << 8);
        return machine == (platform.endsWith("aarch64") ? 0xB7 : 0x3E);
    }

    /**
     * PE magic plus the COFF machine (0xAA64 ARM64, 0x8664 x86-64).
     *
     * <p>Anything that cannot be read is accepted on the magic alone: the architecture check is a
     * guard against a mis-built release, not a reason to hide a working engine.
     */
    private static boolean isWindowsPeFor(String platform, byte[] header) {
        if (header[0] != 'M' || header[1] != 'Z') {
            return false;
        }
        int expected = platform.equals("windows-aarch64") ? 0xAA64 : 0x8664;
        if (header.length < 0x40) {
            return true;
        }
        int peOffset = (header[0x3C] & 0xFF) | ((header[0x3D] & 0xFF) << 8)
                | ((header[0x3E] & 0xFF) << 16) | ((header[0x3F] & 0xFF) << 24);
        if (peOffset <= 0 || peOffset + 6 > header.length
                || header[peOffset] != 'P' || header[peOffset + 1] != 'E'
                || header[peOffset + 2] != 0 || header[peOffset + 3] != 0) {
            return true;
        }
        int machine = (header[peOffset + 4] & 0xFF) | ((header[peOffset + 5] & 0xFF) << 8);
        return machine == expected;
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
            if (!matchesPlatformHeader(platform, data)) {
                throw new IOException("Bundled loot engine has the wrong format for " + platform);
            }
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
