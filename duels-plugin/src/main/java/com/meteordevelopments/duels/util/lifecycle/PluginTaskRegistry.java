package com.meteordevelopments.duels.util.lifecycle;

import com.meteordevelopments.duels.api.folialib.task.WrappedTask;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Owns scheduler tasks created by the plugin across load and unload cycles. */
public final class PluginTaskRegistry {
    private final Set<WrappedTask> tasks = ConcurrentHashMap.newKeySet();

    /** Registers a task as owned by the current plugin load cycle. */
    public <T extends WrappedTask> T track(T task) {
        tasks.add(task);
        return task;
    }

    /** Removes a naturally completed one-shot task without cancelling it. */
    public void complete(WrappedTask task) {
        if (task != null) {
            tasks.remove(task);
        }
    }

    /** Cancels a task and removes it from this registry. */
    public void cancel(WrappedTask task) {
        tasks.remove(task);
        if (!task.isCancelled()) {
            task.cancel();
        }
    }

    /** Cancels all tasks still owned by the current plugin load cycle. */
    public void cancelAll() {
        WrappedTask[] scheduled = tasks.toArray(WrappedTask[]::new);
        tasks.clear();
        for (WrappedTask task : scheduled) {
            if (!task.isCancelled()) {
                task.cancel();
            }
        }
    }

    int size() {
        return tasks.size();
    }
}
