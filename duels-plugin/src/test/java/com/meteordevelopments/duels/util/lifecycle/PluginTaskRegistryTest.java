package com.meteordevelopments.duels.util.lifecycle;

import com.meteordevelopments.duels.api.folialib.task.WrappedTask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PluginTaskRegistryTest {
    @Test
    void completedOneShotTaskIsNoLongerTracked() {
        PluginTaskRegistry registry = new PluginTaskRegistry();
        WrappedTask task = mock(WrappedTask.class);

        registry.track(task);
        registry.complete(task);

        assertEquals(0, registry.size());
        verify(task, never()).cancel();
    }

    @Test
    void explicitCancellationRemovesAndCancelsTask() {
        PluginTaskRegistry registry = new PluginTaskRegistry();
        WrappedTask task = mock(WrappedTask.class);
        registry.track(task);

        registry.cancel(task);

        assertEquals(0, registry.size());
        verify(task).cancel();
    }

    @Test
    void cancelAllCancelsEachLiveTaskOnlyOnce() {
        PluginTaskRegistry registry = new PluginTaskRegistry();
        WrappedTask first = mock(WrappedTask.class);
        WrappedTask second = mock(WrappedTask.class);
        registry.track(first);
        registry.track(second);

        registry.cancelAll();
        registry.cancelAll();

        assertEquals(0, registry.size());
        verify(first).cancel();
        verify(second).cancel();
    }
}
