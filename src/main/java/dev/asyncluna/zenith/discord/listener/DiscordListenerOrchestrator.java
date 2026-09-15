package dev.asyncluna.zenith.discord.listener;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@Slf4j
public class DiscordListenerOrchestrator {
    @Bean
    public <T extends Event> ApplicationRunner discordBotRunner(
            GatewayDiscordClient gateway, List<EventListener<T>> eventListeners) {
        return args -> {
            log.info("Registering {} event listener(s)", eventListeners.size());

            for (EventListener<T> listener : eventListeners) {
                gateway.on(listener.getEventType())
                        .flatMap(event -> listener.executeAsync(event).onErrorResume(listener::handleException))
                        .subscribe();
            }
        };
    }
}
