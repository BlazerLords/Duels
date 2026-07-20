package com.meteordevelopments.duels.core.countdown;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.config.Config;
import com.meteordevelopments.duels.config.Lang;
import com.meteordevelopments.duels.core.arena.ArenaImpl;
import com.meteordevelopments.duels.core.match.DuelMatch;
import com.meteordevelopments.duels.data.UserManagerImpl;
import com.meteordevelopments.duels.api.folialib.task.WrappedTask;
import com.meteordevelopments.duels.util.StringUtil;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DuelCountdownTest {
    @Test
    void sendsOneEntryPerRunAndStopsAfterLastMessage() {
        DuelsPlugin plugin = mock(DuelsPlugin.class);
        when(plugin.getConfiguration()).thenReturn(mock(Config.class));
        when(plugin.getLang()).thenReturn(mock(Lang.class));
        when(plugin.getUserManager()).thenReturn(mock(UserManagerImpl.class));
        ArenaImpl arena = mock(ArenaImpl.class);
        when(arena.isUsed()).thenReturn(true);

        RecordingCountdown countdown = new RecordingCountdown(plugin, arena, mock(DuelMatch.class),
                List.of("first", "second"));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
            bukkit.when(Bukkit::getBukkitVersion).thenReturn("1.21.11-R0.1-SNAPSHOT");
            try (MockedStatic<StringUtil> strings = mockStatic(StringUtil.class)) {
                strings.when(() -> StringUtil.color(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
                countdown.run();
                assertEquals(1, countdown.sent);
                countdown.run();
                assertEquals(2, countdown.sent);
                countdown.run();
            }
        }

        assertEquals(2, countdown.sent);
        verify(arena).setCountdown(null);
    }

    @Test
    void usesTrackedSynchronousSchedulerAndCancelsThroughPlugin() {
        DuelsPlugin plugin = mock(DuelsPlugin.class);
        when(plugin.getConfiguration()).thenReturn(mock(Config.class));
        when(plugin.getLang()).thenReturn(mock(Lang.class));
        when(plugin.getUserManager()).thenReturn(mock(UserManagerImpl.class));
        WrappedTask task = mock(WrappedTask.class);
        when(plugin.doSyncRepeat(any(Runnable.class), eq(0L), eq(20L))).thenReturn(task);
        ArenaImpl arena = mock(ArenaImpl.class);
        when(arena.isUsed()).thenReturn(false);
        RecordingCountdown countdown = new RecordingCountdown(plugin, arena, mock(DuelMatch.class), List.of());

        countdown.startCountdown(0L, 20L);
        countdown.run();

        verify(plugin).doSyncRepeat(countdown, 0L, 20L);
        verify(plugin).cancelTask(task);
        verify(task, never()).cancel();
    }

    private static final class RecordingCountdown extends DuelCountdown {
        private int sent;

        private RecordingCountdown(DuelsPlugin plugin, ArenaImpl arena, DuelMatch match, List<String> messages) {
            super(plugin, arena, match, messages, List.of());
        }

        @Override
        protected void sendMessage(String rawMessage, String message, String title) {
            sent++;
        }
    }
}
