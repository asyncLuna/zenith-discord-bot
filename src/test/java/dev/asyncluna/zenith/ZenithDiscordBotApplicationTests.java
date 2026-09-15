package dev.asyncluna.zenith;

import discord4j.core.GatewayDiscordClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"spring.quartz.auto-startup=false", "spring.data.mongodb.auto-index-creation=false"})
@ActiveProfiles("test")
@Import(ZenithDiscordBotApplicationTests.TestDependencies.class)
class ZenithDiscordBotApplicationTests {
    @Test
    void contextLoads() {}

    @TestConfiguration(proxyBeanMethods = false)
    static class TestDependencies {
        @Bean
        GatewayDiscordClient gatewayDiscordClient() {
            return Mockito.mock(GatewayDiscordClient.class);
        }
    }
}
