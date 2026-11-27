package evernight.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Keeps a failed or stuck client GameTest from leaving the Minecraft process alive.
 *
 * <p>Fabric's runner normally closes the client after a test, but an assertion failure while the
 * game is shutting down can leave non-daemon threads (Xaero, Via, Litematica) running indefinitely.
 * A failure gets a short grace period to print its stack trace, then the JVM is halted; a test that
 * never returns is halted after the watchdog timeout (override with
 * {@code -Devernight.gametest.timeoutSeconds=...}).
 */
public final class GameTestSupport {
    private static final long FAILURE_EXIT_GRACE_MILLIS = 3_000L;
    private static final long DEFAULT_TIMEOUT_SECONDS = 180L;

    private GameTestSupport() {
    }

    public static void runGuarded(ClientGameTestContext context, Runnable body) {
        long timeoutSeconds = Long.getLong("evernight.gametest.timeoutSeconds", DEFAULT_TIMEOUT_SECONDS);
        startWatchdog(Math.max(1L, timeoutSeconds) * 1000L);
        try {
            body.run();
        } catch (Throwable failure) {
            failure.printStackTrace();
            startFailureExit();
            throw failure;
        }
    }

    private static void startWatchdog(long timeoutMillis) {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(timeoutMillis);
            } catch (InterruptedException interrupted) {
                return;
            }
            System.err.println("[evernight-gametest] test did not finish within " + timeoutMillis
                    + " ms; halting the client process");
            Runtime.getRuntime().halt(2);
        }, "evernight-gametest-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private static void startFailureExit() {
        Thread failureExit = new Thread(() -> {
            try {
                Thread.sleep(FAILURE_EXIT_GRACE_MILLIS);
            } catch (InterruptedException interrupted) {
                return;
            }
            System.err.println("[evernight-gametest] failed test did not exit; halting the client process");
            Runtime.getRuntime().halt(1);
        }, "evernight-gametest-failure-exit");
        failureExit.setDaemon(true);
        failureExit.start();
    }
}
