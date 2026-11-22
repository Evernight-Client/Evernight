package evernight.client.utils.core;

public final class UncheckedExceptions {
    private UncheckedExceptions() {
    }

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> RuntimeException rethrow(Throwable failure) throws T {
        throw (T) failure;
    }
}