package dev.asyncluna.zenith.memberoftheweek;

import dev.asyncluna.zenith.memberoftheweek.config.MemberOfTheWeekProperties;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekRound;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekRoundStatus;
import dev.asyncluna.zenith.memberoftheweek.repository.MemberOfTheWeekRoundRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class MemberOfTheWeekStartupBootstrap {
    private final MemberOfTheWeekProperties properties;
    private final MemberOfTheWeekRoundRepository roundRepository;
    private final MemberOfTheWeekRoundService roundService;
    private final Clock memberOfTheWeekClock;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeVotingRound() {
        log.info("Checking for an active Member of the Week voting round");

        findLatestOpenRound()
                .flatMap(this::handleExistingRound)
                .switchIfEmpty(Mono.defer(this::openRoundIfRequired))
                .doOnNext(this::logStartupCompletion)
                .doOnError(this::logInitializationFailure)
                .subscribe();
    }

    private void logInitializationFailure(Throwable error) {
        log.error("Failed to initialize Member of the Week voting", error);
    }

    private Mono<MemberOfTheWeekRound> findLatestOpenRound() {
        return roundRepository.findFirstByGuildIdAndStatusOrderByStartsAtDesc(
                properties.guildId(), MemberOfTheWeekRoundStatus.OPEN);
    }

    private Mono<MemberOfTheWeekRound> openRoundIfRequired() {
        if (!isMonday()) {
            log.info("No open Member of the Week round found; waiting until Monday to open one");
            return Mono.empty();
        }

        log.info("No open Member of the Week round found on Monday; opening one now");
        return roundService.openInitialRound();
    }

    private Mono<MemberOfTheWeekRound> handleExistingRound(MemberOfTheWeekRound round) {
        Instant now = Instant.now(memberOfTheWeekClock);

        if (isActive(round, now)) {
            log.info(
                    "Active Member of the Week round already exists | roundId={} | endsAt={}",
                    round.getId(),
                    round.getEndsAt());
            return roundService.refreshVotingMessage(round);
        }

        return handleExpiredRound(round);
    }

    private boolean isActive(MemberOfTheWeekRound round, Instant now) {
        return round.getEndsAt() != null && round.getEndsAt().isAfter(now);
    }

    private Mono<MemberOfTheWeekRound> handleExpiredRound(MemberOfTheWeekRound round) {
        log.info(
                "Open Member of the Week round has expired | roundId={} | endsAt={}", round.getId(), round.getEndsAt());
        if (isMonday()) {
            log.info("Rotating expired Member of the Week round on Monday | roundId={}", round.getId());
            return roundService.rotateRound();
        }

        log.info(
                "Closing expired Member of the Week round without opening a replacement before Monday | roundId={}",
                round.getId());
        return roundService.closeCurrentRound();
    }

    private void logStartupCompletion(MemberOfTheWeekRound round) {
        log.info(
                "Member of the Week startup check completed | roundId={} | endsAt={}",
                round.getId(),
                round.getEndsAt());
    }

    private boolean isMonday() {
        return ZonedDateTime.now(memberOfTheWeekClock).getDayOfWeek() == DayOfWeek.MONDAY;
    }
}
