package dev.asyncluna.zenith.memberoftheweek;

import dev.asyncluna.zenith.memberoftheweek.exception.AlreadyVotedException;
import dev.asyncluna.zenith.memberoftheweek.exception.SelfVoteException;
import dev.asyncluna.zenith.memberoftheweek.exception.VotingRoundClosedException;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekRound;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekRoundStatus;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekVote;
import dev.asyncluna.zenith.memberoftheweek.repository.MemberOfTheWeekRoundRepository;
import dev.asyncluna.zenith.memberoftheweek.repository.MemberOfTheWeekVoteRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberOfTheWeekVoteService {
    private final MemberOfTheWeekRoundRepository roundRepository;
    private final MemberOfTheWeekVoteRepository voteRepository;
    private final Clock memberOfTheWeekClock;
    private final MemberOfTheWeekDiscordNotifier discordNotifier;

    public Mono<Void> recordVote(String roundId, String guildId, String voterId, String candidateId) {
        Instant now = Instant.now(memberOfTheWeekClock);

        return validateCandidate(voterId, candidateId)
                .then(findOpenRound(roundId, guildId, now))
                .flatMap(round -> saveVote(round.getId(), guildId, voterId, candidateId, now))
                .onErrorMap(DuplicateKeyException.class, error -> new AlreadyVotedException())
                .flatMap(this::writeVoteLog)
                .doOnSuccess(this::logVoteRecorded)
                .then();
    }

    private Mono<Void> validateCandidate(String voterId, String candidateId) {
        return voterId.equals(candidateId) ? Mono.error(new SelfVoteException()) : Mono.empty();
    }

    private Mono<MemberOfTheWeekRound> findOpenRound(String roundId, String guildId, Instant now) {
        return roundRepository
                .findById(roundId)
                .filter(round -> belongsToGuildAndIsOpen(round, guildId, now))
                .switchIfEmpty(Mono.error(new VotingRoundClosedException()));
    }

    private boolean belongsToGuildAndIsOpen(MemberOfTheWeekRound round, String guildId, Instant now) {
        return round.getGuildId().equals(guildId)
                && round.getStatus() == MemberOfTheWeekRoundStatus.OPEN
                && round.getStartsAt() != null
                && round.getEndsAt() != null
                && !now.isBefore(round.getStartsAt())
                && now.isBefore(round.getEndsAt());
    }

    private Mono<MemberOfTheWeekVote> saveVote(
            String roundId, String guildId, String voterId, String candidateId, Instant now) {
        return voteRepository.save(MemberOfTheWeekVote.builder()
                .roundId(roundId)
                .guildId(guildId)
                .voterId(voterId)
                .candidateId(candidateId)
                .createdAt(now)
                .build());
    }

    private Mono<MemberOfTheWeekVote> writeVoteLog(MemberOfTheWeekVote vote) {
        return discordNotifier
                .sendVoteLog(vote)
                .onErrorResume(error -> {
                    log.error(
                            "Vote was saved, but the Discord vote log failed | round={} | voter={} | candidate={}",
                            vote.getRoundId(),
                            vote.getVoterId(),
                            vote.getCandidateId(),
                            error);
                    return Mono.empty();
                })
                .thenReturn(vote);
    }

    private void logVoteRecorded(MemberOfTheWeekVote vote) {
        log.info(
                "Member of the Week vote recorded | round={} | voter={} | candidate={}",
                vote.getRoundId(),
                vote.getVoterId(),
                vote.getCandidateId());
    }
}
