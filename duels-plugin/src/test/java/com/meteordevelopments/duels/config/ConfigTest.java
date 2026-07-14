package com.meteordevelopments.duels.config;

import com.meteordevelopments.duels.DuelsPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigTest {
    @Test
    void cachesSpectatorJoinMessageSetting() throws Exception {
        DuelsPlugin plugin = mock(DuelsPlugin.class);
        when(plugin.getDataFolder()).thenReturn(new File("build/test-data"));
        TestConfig config = new TestConfig(plugin);
        YamlConfiguration values = new YamlConfiguration();
        values.set("config-version", Integer.MAX_VALUE);
        values.set("spectate.hide-join-message-from-fighters", true);

        config.load(values);

        assertTrue(config.isSpecHideJoinMessageFromFighters());
    }

    private static final class TestConfig extends Config {
        private TestConfig(DuelsPlugin plugin) {
            super(plugin);
        }

        private void load(YamlConfiguration values) throws Exception {
            loadValues(values);
        }
    }
}
