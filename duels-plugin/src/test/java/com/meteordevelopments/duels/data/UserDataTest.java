package com.meteordevelopments.duels.data;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class UserDataTest {
    @Test
    void constructorCapturesPlayerIdentity() {
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("PlayerName");

        UserData data = new UserData(new File("build/test-users"), 1400, 10, player);

        assertEquals(uuid, data.getUuid());
        assertEquals("PlayerName", data.getName());
        verify(player).getUniqueId();
        verify(player).getName();
        verifyNoMoreInteractions(player);
    }

    @Test
    void identityConstructorDoesNotRequireBukkitPlayer() {
        UUID uuid = UUID.randomUUID();

        UserData data = new UserData(new File("build/test-users"), 1400, 10, uuid, "PlayerName");

        assertEquals(uuid, data.getUuid());
        assertEquals("PlayerName", data.getName());
    }

    @Test
    void saveAtomicallyWritesReadableJson(@TempDir Path folder) throws Exception {
        UUID uuid = UUID.randomUUID();
        UserData data = new UserData(folder.toFile(), 1400, 10, uuid, "PlayerName");

        data.trySave();

        Path file = folder.resolve(uuid + ".json");
        UserData restored = com.meteordevelopments.duels.util.json.JsonUtil.getObjectMapper()
                .readValue(Files.readString(file), UserData.class);
        assertEquals(uuid, restored.getUuid());
        assertEquals("PlayerName", restored.getName());
        assertFalse(Files.exists(folder.resolve(uuid + ".json.tmp")));
    }
}
