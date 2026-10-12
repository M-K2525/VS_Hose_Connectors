package com.mk2525.vsfluidlink.client;

import com.mk2525.vsfluidlink.VsFluidLinkConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerConfigScreenTest {
    @Test void screenIncludesEveryGeneralAndBufferSetting() {
        var options = ServerConfigScreen.configOptions();
        for (String section : List.of("general", "buffers")) {
            com.electronwill.nightconfig.core.UnmodifiableConfig settings =
                    VsFluidLinkConfig.SERVER_SPEC.getValues().get(section);
            for (var entry : settings.entrySet()) {
                var matches = options.stream().filter(option -> option.key().equals(entry.getKey())).toList();
                assertEquals(1, matches.size(), section + "." + entry.getKey());
                assertSame(entry.getValue(), matches.get(0).value());
                assertEquals(section.equals("buffers"), matches.get(0).buffer());
            }
        }
    }

    @Test void paginationLeavesRoomForFooterAtTypicalGuiHeights() {
        for (int height : new int[]{180, 240, 300, 480, 720}) {
            int rows = ServerConfigScreen.rowsPerPage(height);
            assertTrue(rows >= 1);
            assertTrue(58 + (rows - 1) * 28 + 20 <= height - 80);
        }
        assertTrue(ServerConfigScreen.rowsPerPage(240) < 8);
        assertTrue(ServerConfigScreen.rowsPerPage(480) >= 8);
    }
}
