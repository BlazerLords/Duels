package com.meteordevelopments.duels.util.io;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Coalesces queued persistence requests and guarantees that a final synchronous snapshot wins.
 */
public final class LatestSnapshotWriter<T> {
    private final Consumer<Runnable> scheduler;
    private final CheckedConsumer<T> sink;
    private final Consumer<Exception> errorHandler;
    private final AtomicReference<T> pending = new AtomicReference<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object writeLock = new Object();

    public LatestSnapshotWriter(Consumer<Runnable> scheduler, CheckedConsumer<T> sink,
                                Consumer<Exception> errorHandler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
    }

    public boolean submit(T snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (closed.get()) {
            return false;
        }
        pending.set(snapshot);
        scheduleDrain();
        return true;
    }

    public void closeAndWrite(T finalSnapshot) {
        Objects.requireNonNull(finalSnapshot, "finalSnapshot");
        closed.set(true);
        pending.set(null);
        synchronized (writeLock) {
            write(finalSnapshot);
        }
    }

    private void scheduleDrain() {
        if (!closed.get() && scheduled.compareAndSet(false, true)) {
            try {
                scheduler.accept(this::drain);
            } catch (RuntimeException ex) {
                scheduled.set(false);
                errorHandler.accept(ex);
            }
        }
    }

    private void drain() {
        try {
            T snapshot;
            while (!closed.get() && (snapshot = pending.getAndSet(null)) != null) {
                synchronized (writeLock) {
                    if (!closed.get()) {
                        write(snapshot);
                    }
                }
            }
        } finally {
            scheduled.set(false);
            if (!closed.get() && pending.get() != null) {
                scheduleDrain();
            }
        }
    }

    private void write(T snapshot) {
        try {
            sink.accept(snapshot);
        } catch (Exception ex) {
            errorHandler.accept(ex);
        }
    }

    @FunctionalInterface
    public interface CheckedConsumer<T> {
        void accept(T value) throws Exception;
    }
}
