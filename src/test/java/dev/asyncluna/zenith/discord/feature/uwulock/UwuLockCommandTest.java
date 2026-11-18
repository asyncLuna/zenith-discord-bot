package dev.asyncluna.zenith.discord.feature.uwulock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.asyncluna.zenith.core.model.UwuLock;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UwuLockCommandTest {
    @Test
    void formatsRelativeDiscordTimestamp() {
        UwuLock lock = new UwuLock("123", Instant.ofEpochSecond(1700000000));

        assertThat(UwuLockCommand.formatLock(lock, 1, "unknown")).isEqualTo("`#1.` - <@123> - <t:1700000000:R>");
    }

    @Test
    void formatsLegacyLockWithoutTimestamp() {
        assertThat(UwuLockCommand.formatLock(new UwuLock("123", null), 2, "unknown"))
                .isEqualTo("`#2.` - <@123> - unknown");
    }
}
