package com.meteordevelopments.duels.util.io;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

class LatestSnapshotWriterTest {
    @Test
    void coalescesPendingRequestsToLatestSnapshot() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        List<String> writes = new ArrayList<>();
        LatestSnapshotWriter<String> writer = new LatestSnapshotWriter<>(tasks::add, writes::add,
                failure -> fail(failure.getMessage()));

        assertTrue(writer.submit("old"));
        assertTrue(writer.submit("latest"));
        assertEquals(1, tasks.size());
        tasks.remove().run();

        assertEquals(List.of("latest"), writes);
    }

    @Test
    void finalSnapshotCannotBeOverwrittenByQueuedTask() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        List<String> writes = new ArrayList<>();
        LatestSnapshotWriter<String> writer = new LatestSnapshotWriter<>(tasks::add, writes::add,
                failure -> fail(failure.getMessage()));

        writer.submit("stale");
        writer.closeAndWrite("final");
        tasks.remove().run();

        assertEquals(List.of("final"), writes);
        assertFalse(writer.submit("too-late"));
    }

    @Test
    void schedulerRejectionDoesNotLeaveWriterPermanentlyScheduled() {
        List<Exception> failures = new ArrayList<>();
        LatestSnapshotWriter<String> writer = new LatestSnapshotWriter<>(task -> {
            throw new IllegalStateException("scheduler stopped");
        }, value -> fail("nothing should be written"), failures::add);

        assertTrue(writer.submit("first"));
        assertTrue(writer.submit("second"));

        assertEquals(2, failures.size());
    }
}
