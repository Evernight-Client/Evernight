package evernight.client.utils.vault;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.function.LongConsumer;

/**
 * Pure-Java vault loot prediction core.
 *
 * <p>Every opening is generated from the vanilla global {@code RandomSequence} for the vault loot
 * table, so "the N-th ominous vault opened worldwide" depends only on the world seed and N. The core
 * keeps that sequence exactly, including data-dependent enchantment draws, and scans it in parallel
 * from 65,536-opening Xoroshiro checkpoints.
 *
 * <p>Offsets are {@code long} end to end. The index-space methods mirror the old native CLI:
 * {@code search} skips {@code offset} openings and examines the next {@code limit};
 * {@code matches} examines indices {@code [0, maxScan)}; {@code narrow} examines
 * {@code [rangeStart, rangeEnd)}. Reported offsets are 1-based.
 *
 * <p>No native binary, subprocess or game client is involved. The only state kept between calls is
 * the checkpoint cache in {@link VaultSequenceCache}.
 */
public final class VaultLootCore {
    private static final int MAX_MATCHES = 2_000_000;
    private static final int MAX_THREADS = 32;
    private static final long PROGRESS_STEP = 1L << 20;

    private VaultLootCore() {
    }

    /** One target item occurrence: the {@code offset}-th vault opening yielded {@code count} items. */
    public record Hit(long offset, String itemId, int count) {
    }

    /**
     * Scans {@code limit} openings starting after {@code offset} and returns at most {@code amount}
     * occurrences of any item in {@code targetSpec} ({@code "all"} matches everything).
     */
    public static List<Hit> search(long seed, boolean ominous, long offset, long limit, int amount,
                                   String targetSpec, LongConsumer progress) {
        if (limit <= 0 || amount <= 0) {
            return List.of();
        }
        int[] targets = VaultLootSpec.targetItems(targetSpec);
        long indexStart = Math.max(0L, offset);
        long indexEnd = saturatedAdd(indexStart, limit);
        List<Task> tasks = tasks(seed, ominous, indexStart, indexEnd, null, targets, amount, progress);
        List<Hit> hits = new ArrayList<>(Math.min(amount, 64));
        int threads = threadCount(tasks.size());
        for (int base = 0; base < tasks.size(); base += threads) {
            List<Task> batch = tasks.subList(base, Math.min(tasks.size(), base + threads));
            List<TaskResult> results = run(batch, threads);
            List<Hit> batchHits = new ArrayList<>();
            for (TaskResult result : results) {
                batchHits.addAll(result.hits);
            }
            batchHits.sort(Comparator.comparingLong(Hit::offset));
            for (Hit hit : batchHits) {
                hits.add(hit);
                if (hits.size() >= amount) {
                    return hits;
                }
            }
        }
        return hits;
    }

    /** Every opening offset in {@code [1, maxScan]} whose full drop sequence matches {@code spec}. */
    public static List<Long> matches(long seed, boolean ominous, long maxScan, String spec,
                                     LongConsumer progress) {
        return scanSequence(seed, ominous, 0L, Math.max(0L, maxScan), spec, null, 0L, progress);
    }

    /**
     * Keeps the matches in {@code [rangeStart, rangeEnd)} that stay reachable from at least one
     * previous candidate within {@code delta} openings. Indices are 0-based; results are 1-based.
     */
    public static List<Long> narrow(long seed, boolean ominous, long rangeStart, long rangeEnd,
                                    long delta, String spec, List<Long> candidates,
                                    LongConsumer progress) {
        long[] sortedCandidates = new long[candidates.size()];
        for (int i = 0; i < sortedCandidates.length; i++) {
            sortedCandidates[i] = candidates.get(i);
        }
        Arrays.sort(sortedCandidates);
        long start = Math.max(0L, rangeStart);
        long end = Math.max(start, rangeEnd);
        return scanSequence(seed, ominous, start, end, spec, sortedCandidates, Math.max(1L, delta), progress);
    }

    /** First match in {@code [start, start + maxAttempts)}, or {@code -1}. */
    public static long detect(long seed, boolean ominous, long start, long maxAttempts, String spec,
                              LongConsumer progress) {
        List<Long> found = scanSequence(seed, ominous, Math.max(0L, start),
                saturatedAdd(Math.max(0L, start), Math.max(0L, maxAttempts)), spec, null, 0L, progress);
        return found.isEmpty() ? -1L : found.get(0);
    }

    private static List<Long> scanSequence(long seed, boolean ominous, long indexStart, long indexEnd,
                                           String spec, long[] candidates, long delta,
                                           LongConsumer progress) {
        if (indexEnd <= indexStart) {
            return List.of();
        }
        VaultLootSpec parsed = VaultLootSpec.parse(spec);
        List<Task> tasks = tasks(seed, ominous, indexStart, indexEnd, parsed, null, 0, progress);
        int threads = threadCount(tasks.size());
        List<Long> matches = new ArrayList<>();
        List<TaskResult> results = run(tasks, threads);
        for (TaskResult result : results) {
            for (long offset : result.offsets) {
                if (candidates != null && !nearCandidate(candidates, offset, delta)) {
                    continue;
                }
                matches.add(offset);
                if (matches.size() >= MAX_MATCHES) {
                    matches.sort(Long::compare);
                    return matches;
                }
            }
        }
        matches.sort(Long::compare);
        return matches;
    }

    private static boolean nearCandidate(long[] sortedCandidates, long offset, long delta) {
        long lowest = offset - delta;
        int low = 0;
        int high = sortedCandidates.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (sortedCandidates[mid] < lowest) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low < sortedCandidates.length && sortedCandidates[low] < offset;
    }

    private static List<Task> tasks(long seed, boolean ominous, long indexStart, long indexEnd,
                                    VaultLootSpec spec, int[] targets, int amount,
                                    LongConsumer progress) {
        long firstCheckpoint = indexStart / VaultSequenceCache.INTERVAL;
        long lastCheckpoint = (indexEnd - 1) / VaultSequenceCache.INTERVAL;
        int count = (int) Math.min(Integer.MAX_VALUE, lastCheckpoint - firstCheckpoint + 1);
        List<Task> tasks = new ArrayList<>(count);
        for (long checkpoint = firstCheckpoint; checkpoint <= lastCheckpoint; checkpoint++) {
            long taskStart = Math.max(indexStart, checkpoint * VaultSequenceCache.INTERVAL);
            long taskEnd = Math.min(indexEnd, saturatedMultiply(checkpoint + 1, VaultSequenceCache.INTERVAL));
            tasks.add(new Task(seed, ominous, checkpoint, taskStart, taskEnd, spec, targets, amount, progress));
        }
        return tasks;
    }

    private static List<TaskResult> run(List<Task> tasks, int threads) {
        if (tasks.size() == 1) {
            try {
                return List.of(tasks.get(0).call());
            } catch (Exception failure) {
                throw new IllegalStateException("Vault scan failed", failure);
            }
        }
        ExecutorService executor = Executors.newFixedThreadPool(threads, new ScanThreadFactory());
        try {
            List<Future<TaskResult>> futures = executor.invokeAll(tasks);
            List<TaskResult> results = new ArrayList<>(futures.size());
            for (Future<TaskResult> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException failure) {
                    throw new IllegalStateException("Vault scan failed", failure.getCause());
                }
            }
            return results;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Vault scan interrupted", interrupted);
        } finally {
            executor.shutdownNow();
        }
    }

    private static int threadCount(int tasks) {
        int available = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(Math.min(MAX_THREADS, available), tasks));
    }

    private static long saturatedAdd(long left, long right) {
        long result = left + right;
        return result < left ? Long.MAX_VALUE : result;
    }

    private static long saturatedMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static final class ScanThreadFactory implements ThreadFactory {
        private int next;

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "evernight-vault-scan-" + this.next++);
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }

    private record TaskResult(long[] offsets, List<Hit> hits) {
    }

    private static final class Task implements Callable<TaskResult> {
        private final long seed;
        private final boolean ominous;
        private final long checkpoint;
        private final long start;
        private final long end;
        private final VaultLootSpec spec;
        private final int[] targets;
        private final int amount;
        private final LongConsumer progress;

        Task(long seed, boolean ominous, long checkpoint, long start, long end, VaultLootSpec spec,
             int[] targets, int amount, LongConsumer progress) {
            this.seed = seed;
            this.ominous = ominous;
            this.checkpoint = checkpoint;
            this.start = start;
            this.end = end;
            this.spec = spec;
            this.targets = targets;
            this.amount = amount;
            this.progress = progress;
        }

        @Override
        public TaskResult call() {
            long checkpointIndex = this.checkpoint * VaultSequenceCache.INTERVAL;
            VaultSequenceCache.State state = VaultSequenceCache.stateAt(this.seed, this.ominous, checkpointIndex);
            VaultLootGenerator generator = new VaultLootGenerator();
            generator.reset(state.lo(), state.hi());
            long position = checkpointIndex;
            while (position < this.start) {
                generator.generate(this.ominous ? VaultLootData.OMINOUS : VaultLootData.NORMAL);
                position++;
            }

            LongList offsets = new LongList();
            List<Hit> hits = new ArrayList<>();
            long nextProgress = this.start + PROGRESS_STEP;
            for (long index = this.start; index < this.end; index++) {
                generator.generate(this.ominous ? VaultLootData.OMINOUS : VaultLootData.NORMAL);
                long offset = index + 1;
                if (this.spec != null) {
                    if (this.spec.matches(generator)) {
                        offsets.add(offset);
                    }
                } else {
                    for (int drop = 0; drop < generator.size(); drop++) {
                        if (this.targets == null || contains(this.targets, generator.item(drop))) {
                            hits.add(new Hit(offset, VaultLootData.ITEM_NAMES[generator.item(drop)],
                                    generator.count(drop)));
                            if (hits.size() >= this.amount) {
                                break;
                            }
                        }
                    }
                    if (hits.size() >= this.amount) {
                        break;
                    }
                }
                if (this.progress != null && index >= nextProgress) {
                    this.progress.accept(index);
                    nextProgress = index + PROGRESS_STEP;
                }
            }
            return new TaskResult(offsets.toArray(), hits);
        }

        private static boolean contains(int[] values, int value) {
            for (int candidate : values) {
                if (candidate == value) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class LongList {
        private long[] values = new long[16];
        private int size;

        void add(long value) {
            if (this.size == this.values.length) {
                this.values = Arrays.copyOf(this.values, this.values.length * 2);
            }
            this.values[this.size++] = value;
        }

        long[] toArray() {
            return Arrays.copyOf(this.values, this.size);
        }
    }
}
