package dev.asyncluna.zenith.memberoftheweek.scheduler;

import dev.asyncluna.zenith.memberoftheweek.MemberOfTheWeekRoundService;
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
public class MemberOfTheWeekRotationJob implements Job {
    private final MemberOfTheWeekRoundService roundService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info(
                "Member of the Week Quartz job started | scheduledFireTime={} | actualFireTime={}",
                context.getScheduledFireTime(),
                context.getFireTime());

        try {
            roundService.rotateRound().block(Duration.ofMinutes(2));

            log.info("Member of the Week Quartz job completed");
        } catch (Exception exception) {
            log.error("Member of the Week Quartz job failed", exception);

            throw new JobExecutionException(exception, false);
        }
    }
}
