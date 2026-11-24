package dev.asyncluna.zenith.memberoftheweek.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.asyncluna.zenith.memberoftheweek.MemberOfTheWeekRoundService;
import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import reactor.core.publisher.Mono;

class MemberOfTheWeekJobTest {
    @Test
    void expiryFailureDoesNotImmediatelyRefire() {
        var service = mock(MemberOfTheWeekRoundService.class);
        when(service.closeExpiredRound()).thenReturn(Mono.error(new IllegalStateException("failure")));

        JobExecutionException error = catchThrowableOfType(
                () -> new MemberOfTheWeekExpiryJob(service).execute(mock(JobExecutionContext.class)),
                JobExecutionException.class);
        assertThat(error.refireImmediately()).isFalse();
    }

    @Test
    void rotationFailureDoesNotImmediatelyRefire() {
        var service = mock(MemberOfTheWeekRoundService.class);
        when(service.rotateRound()).thenReturn(Mono.error(new IllegalStateException("failure")));

        JobExecutionException error = catchThrowableOfType(
                () -> new MemberOfTheWeekRotationJob(service).execute(mock(JobExecutionContext.class)),
                JobExecutionException.class);
        assertThat(error.refireImmediately()).isFalse();
    }
}
