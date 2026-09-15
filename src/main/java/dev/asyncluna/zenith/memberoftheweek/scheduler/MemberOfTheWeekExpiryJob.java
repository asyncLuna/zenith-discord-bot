package dev.asyncluna.zenith.memberoftheweek.scheduler;

import dev.asyncluna.zenith.memberoftheweek.MemberOfTheWeekRoundService;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekRound;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class MemberOfTheWeekExpiryJob implements Job {
    private final MemberOfTheWeekRoundService roundService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.debug("Member of the Week expiry check started");

        try {
            roundService
                    .closeExpiredRound()
                    .doOnNext(this::logExpiredRoundClosed)
                    .block(Duration.ofMinutes(2));
        } catch (Exception exception) {
            log.error("Member of the Week expiry check failed", exception);
            throw new JobExecutionException(exception, true);
        }
    }

    private void logExpiredRoundClosed(MemberOfTheWeekRound round) {
        log.info("Expired Member of the Week round closed | roundId={}", round.getId());
    }
}
