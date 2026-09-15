package dev.asyncluna.zenith.core.service;

import dev.asyncluna.zenith.core.model.UwuLock;
import dev.asyncluna.zenith.core.repository.UwuLockRepository;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Member;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class UwuLockService {
    private final UwuLockRepository uwuLockRepository;
    private final UwuLockMessageProcessor messageProcessor;
    private final Set<String> lockedUserIds = ConcurrentHashMap.newKeySet();

    @EventListener(ApplicationReadyEvent.class)
    public void loadLocksIntoMemory() {
        uwuLockRepository
                .findAll()
                .map(UwuLock::discordId)
                .doOnNext(lockedUserIds::add)
                .doOnComplete(this::logLocksLoaded)
                .doOnError(this::logLockLoadFailure)
                .onErrorComplete()
                .subscribe();
    }

    private void logLocksLoaded() {
        log.info("Loaded {} Uwu Lock user(s)", lockedUserIds.size());
    }

    private void logLockLoadFailure(Throwable error) {
        log.error("Failed to load Uwu Lock users", error);
    }

    public Mono<Void> handleMessageCreateAsync(MessageCreateEvent event) {
        if (event.getGuildId().isEmpty() || event.getMember().isEmpty()) return Mono.empty();

        Member member = event.getMember().get();
        String userId = member.getId().asString();

        if (!lockedUserIds.contains(userId)) return Mono.empty();

        return messageProcessor.processAsync(event, member);
    }

    public Mono<Boolean> lockUserAsync(String userId) {
        if (lockedUserIds.contains(userId)) return Mono.just(false);
        return uwuLockRepository
                .save(new UwuLock(userId))
                .doOnSuccess(saved -> lockedUserIds.add(userId))
                .thenReturn(true);
    }

    public Mono<Boolean> unlockUserAsync(String userId) {
        if (!lockedUserIds.contains(userId)) return Mono.just(false);
        return uwuLockRepository
                .deleteById(userId)
                .then(Mono.fromRunnable(() -> lockedUserIds.remove(userId)))
                .thenReturn(true);
    }

    public Flux<String> getLockedUsers() {
        return Flux.fromIterable(lockedUserIds);
    }
}
