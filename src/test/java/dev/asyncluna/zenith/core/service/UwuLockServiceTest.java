package dev.asyncluna.zenith.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.asyncluna.zenith.core.model.UwuLock;
import dev.asyncluna.zenith.core.repository.UwuLockRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class UwuLockServiceTest {
    @Test
    void savesTimestampAndDoesNotReplaceExistingLock() {
        UwuLockRepository repository = mock(UwuLockRepository.class);
        when(repository.save(any(UwuLock.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        UwuLockService service = new UwuLockService(repository, mock(UwuLockMessageProcessor.class));
        Instant before = Instant.now();

        assertThat(service.lockUserAsync("123").block()).isTrue();
        assertThat(service.lockUserAsync("123").block()).isFalse();

        var saved = org.mockito.ArgumentCaptor.forClass(UwuLock.class);
        verify(repository, times(1)).save(saved.capture());
        assertThat(saved.getValue().discordId()).isEqualTo("123");
        assertThat(saved.getValue().createdAt()).isBetween(before, Instant.now());
    }

    @Test
    void listsStoredLocksIncludingLegacyRecords() {
        UwuLockRepository repository = mock(UwuLockRepository.class);
        UwuLock legacy = new UwuLock("123", null);
        UwuLock recent = new UwuLock("456", Instant.ofEpochSecond(1700000000));
        when(repository.findAll()).thenReturn(Flux.just(legacy, recent));
        UwuLockService service = new UwuLockService(repository, mock(UwuLockMessageProcessor.class));

        assertThat(service.getLockedUsers().collectList().block()).containsExactly(legacy, recent);
    }
}
