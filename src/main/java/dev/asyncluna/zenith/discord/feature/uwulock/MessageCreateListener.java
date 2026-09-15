package dev.asyncluna.zenith.discord.feature.uwulock;

import dev.asyncluna.zenith.core.service.UwuLockService;
import dev.asyncluna.zenith.discord.listener.EventListener;
import discord4j.core.event.domain.message.MessageCreateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageCreateListener implements EventListener<MessageCreateEvent> {
    private final UwuLockService uwuLockService;

    @Override
    public Mono<Void> execute(MessageCreateEvent event) {
        return uwuLockService.handleMessageCreateAsync(event);
    }
}
