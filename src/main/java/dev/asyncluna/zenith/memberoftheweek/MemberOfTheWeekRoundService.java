package dev.asyncluna.zenith.memberoftheweek;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;

import dev.asyncluna.zenith.core.i18n.I18nManager;
import dev.asyncluna.zenith.core.i18n.SupportedLocale;
import dev.asyncluna.zenith.core.model.GuildSettings;
import dev.asyncluna.zenith.core.repository.GuildSettingsRepository;
import dev.asyncluna.zenith.memberoftheweek.config.MemberOfTheWeekProperties;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekRound;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekRoundStatus;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekVoteCount;
import dev.asyncluna.zenith.memberoftheweek.repository.MemberOfTheWeekRoundRepository;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberOfTheWeekRoundService {
    public static final String COMPONENT_ID_PREFIX = "member_of_the_week_vote:";

    private final GatewayDiscordClient gatewayDiscordClient;
    private final MemberOfTheWeekProperties properties;
    private final MemberOfTheWeekRoundRepository roundRepository;
    private final GuildSettingsRepository guildSettingsRepository;
    private final I18nManager i18nManager;
    private final MemberOfTheWeekMessageFactory messageFactory;
    private final ReactiveMongoTemplate mongoTemplate;
    private final Clock memberOfTheWeekClock;

    public Mono<MemberOfTheWeekRound> rotateRound() {
        log.info("Rotating Member of the Week voting round");

        return isPaused()
                .flatMap(paused -> paused ? Mono.empty() : closeCurrentRound().then(openNewRound()))
                .doOnSuccess(this::logRoundRotated)
                .doOnError(this::logRotationFailure);
    }

    public Mono<MemberOfTheWeekRound> openInitialRound() {
        log.info("Opening initial Member of the Week voting round");

        return isPaused()
                .flatMap(paused -> paused ? Mono.empty() : openNewRound())
                .doOnSuccess(this::logInitialRoundOpened);
    }

    public Mono<MemberOfTheWeekRound> getCurrentRound() {
        return roundRepository.findFirstByGuildIdAndStatusOrderByStartsAtDesc(
                properties.guildId(), MemberOfTheWeekRoundStatus.OPEN);
    }

    public Mono<MemberOfTheWeekRound> getCurrentRoundForGuild(String guildId) {
        return roundRepository.findFirstByGuildIdAndStatusOrderByStartsAtDesc(guildId, MemberOfTheWeekRoundStatus.OPEN);
    }

    public Mono<MemberOfTheWeekRound> getRound(String guildId, String roundId) {
        return roundRepository.findByIdAndGuildId(roundId, guildId);
    }

    public Flux<MemberOfTheWeekRound> getRounds(String guildId) {
        return roundRepository.findByGuildIdOrderByStartsAtDesc(guildId);
    }

    public Mono<MemberOfTheWeekRound> getLatestRound(String guildId) {
        return getRounds(guildId).next();
    }

    /**
     * Refreshes the persisted voting message without creating a second message.
     * This is useful after changing translations or embed formatting while a round
     * is still active.
     */
    public Mono<MemberOfTheWeekRound> refreshVotingMessage(MemberOfTheWeekRound round) {
        if (round.getMessageId() == null || round.getMessageId().isBlank()) {
            log.warn("Active Member of the Week round has no persisted message ID | roundId={}", round.getId());
            return Mono.just(round);
        }

        return getVotingChannel(round.getChannelId())
                .flatMap(channel -> channel.getMessageById(Snowflake.of(round.getMessageId())))
                .flatMap(message -> getGuildLocale().flatMap(locale -> {
                    String title = i18nManager.localize("member_of_the_week.embed.title", locale);
                    String description = messageFactory.createVotingDescription(round, locale);

                    boolean needsUpdate = message.getEmbeds().stream()
                            .findFirst()
                            .map(current -> !current.getTitle().orElse("").equals(title)
                                    || !current.getDescription().orElse("").equals(description))
                            .orElse(true);

                    if (!needsUpdate) {
                        return Mono.just(round);
                    }

                    var updatedEmbed = messageFactory.createVotingEmbed(round, locale);

                    return message.edit().withEmbeds(updatedEmbed).thenReturn(round);
                }))
                .doOnNext(this::logVotingMessageChecked)
                .onErrorResume(error -> keepRoundAfterRefreshFailure(round, error));
    }

    public Mono<List<MemberOfTheWeekVoteCount>> getVoteCounts(String guildId, String roundId) {
        Aggregation aggregation = newAggregation(
                match(Criteria.where("guildId").is(guildId).and("roundId").is(roundId)),
                group("candidateId").count().as("votes"),
                sort(Sort.Direction.DESC, "votes"));

        return aggregateVoteCounts(aggregation).collectList();
    }

    public Mono<List<MemberOfTheWeekVoteCount>> getAllVoteCounts(String guildId) {
        Aggregation aggregation = newAggregation(
                match(Criteria.where("guildId").is(guildId)),
                group("candidateId").count().as("votes"),
                sort(Sort.Direction.DESC, "votes"));

        return aggregateVoteCounts(aggregation).collectList();
    }

    public Mono<Long> getVoteCount(String guildId, String roundId, String candidateId) {
        return mongoTemplate.count(
                Query.query(Criteria.where("guildId")
                        .is(guildId)
                        .and("roundId")
                        .is(roundId)
                        .and("candidateId")
                        .is(candidateId)),
                "member_of_the_week_votes");
    }

    public Mono<Long> getAllVoteCount(String guildId, String candidateId) {
        return mongoTemplate.count(
                Query.query(
                        Criteria.where("guildId").is(guildId).and("candidateId").is(candidateId)),
                "member_of_the_week_votes");
    }

    private Flux<MemberOfTheWeekVoteCount> aggregateVoteCounts(Aggregation aggregation) {
        return mongoTemplate
                .aggregate(aggregation, "member_of_the_week_votes", Document.class)
                .map(document -> {
                    Number votes = document.get("votes", Number.class);
                    return new MemberOfTheWeekVoteCount(document.getString("_id"), votes.longValue());
                });
    }

    public Mono<MemberOfTheWeekRound> openRound() {
        return isPaused()
                .flatMap(paused -> paused
                        ? Mono.empty()
                        : getCurrentRound()
                                .flatMap(round -> Mono.<MemberOfTheWeekRound>empty())
                                .switchIfEmpty(openNewRound()));
    }

    public Mono<MemberOfTheWeekRound> closeCurrentRound() {
        return roundRepository
                .findFirstByGuildIdAndStatusOrderByStartsAtDesc(properties.guildId(), MemberOfTheWeekRoundStatus.OPEN)
                .flatMap(this::closeRound)
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("No open Member of the Week round found to close");
                    return Mono.empty();
                }));
    }

    public Mono<MemberOfTheWeekRound> closeExpiredRound() {
        Instant now = Instant.now(memberOfTheWeekClock);

        return roundRepository
                .findFirstByGuildIdAndStatusOrderByStartsAtDesc(properties.guildId(), MemberOfTheWeekRoundStatus.OPEN)
                .filter(round -> round.getEndsAt() != null && !round.getEndsAt().isAfter(now))
                .flatMap(this::closeRound)
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("No expired Member of the Week round found");
                    return Mono.empty();
                }));
    }

    private Mono<MemberOfTheWeekRound> closeRound(MemberOfTheWeekRound round) {
        log.info("Closing Member of the Week round | roundId={}", round.getId());

        return Mono.zip(findWinners(round.getId()), countVotes(round.getId()))
                .flatMap(tuple -> announceResults(tuple.getT1(), tuple.getT2()))
                .then(Mono.defer(() -> {
                    round.setStatus(MemberOfTheWeekRoundStatus.CLOSED);

                    return roundRepository.save(round);
                }))
                .doOnSuccess(
                        closedRound -> log.info("Member of the Week round closed | roundId={}", closedRound.getId()));
    }

    public Mono<Boolean> isPaused() {
        return guildSettingsRepository
                .findById(properties.guildId())
                .map(GuildSettings::isMemberOfTheWeekPaused)
                .defaultIfEmpty(false);
    }

    public Mono<Void> pauseVoting() {
        return closeCurrentRound().then(setPaused(true));
    }

    public Mono<Void> unpauseVoting() {
        return setPaused(false);
    }

    private Mono<Void> setPaused(boolean paused) {
        return guildSettingsRepository
                .findById(properties.guildId())
                .defaultIfEmpty(GuildSettings.builder().id(properties.guildId()).build())
                .flatMap(settings -> {
                    settings.setMemberOfTheWeekPaused(paused);
                    return guildSettingsRepository.save(settings);
                })
                .then();
    }

    private Mono<MemberOfTheWeekRound> openNewRound() {
        Instant startsAt = Instant.now(memberOfTheWeekClock);
        Instant endsAt = startsAt.plus(properties.votingDuration());

        MemberOfTheWeekRound round = MemberOfTheWeekRound.builder()
                .guildId(properties.guildId())
                .channelId(properties.channelId())
                .startsAt(startsAt)
                .endsAt(endsAt)
                .status(MemberOfTheWeekRoundStatus.OPEN)
                .build();

        return roundRepository.save(round).flatMap(savedRound -> sendVotingMessage(savedRound)
                .flatMap(message -> {
                    savedRound.setMessageId(message.getId().asString());

                    return roundRepository.save(savedRound);
                })
                .onErrorResume(error -> rollbackFailedRound(savedRound, error)));
    }

    private Mono<Message> sendVotingMessage(MemberOfTheWeekRound round) {
        return getGuildLocale().flatMap(locale -> getVotingChannel()
                .flatMap(channel -> channel.createMessage(messageFactory.createVotingMessage(round, locale)))
                .doOnSuccess(this::logVotingMessageSent));
    }

    private Mono<Void> announceResults(List<MemberOfTheWeekVoteCount> winners, long totalVotes) {
        MemberOfTheWeekVoteCount selectedWinner =
                winners.isEmpty() ? null : (winners.size() == 1 ? winners.getFirst() : pickRandomWinner(winners));
        return getGuildLocale().flatMap(locale -> {
            return getVotingChannel()
                    .flatMap(channel -> channel.createMessage(
                            messageFactory.createResultsMessage(winners, selectedWinner, totalVotes, locale)))
                    .doOnSuccess(this::logResultsAnnounced)
                    .then();
        });
    }

    private void logRoundRotated(MemberOfTheWeekRound round) {
        if (round == null) {
            return;
        }
        log.info("Member of the Week round rotated | roundId={} | endsAt={}", round.getId(), round.getEndsAt());
    }

    private void logRotationFailure(Throwable error) {
        log.error("Failed to rotate Member of the Week round", error);
    }

    private void logInitialRoundOpened(MemberOfTheWeekRound round) {
        if (round == null) {
            return;
        }
        log.info("Initial Member of the Week round opened | roundId={} | endsAt={}", round.getId(), round.getEndsAt());
    }

    private void logVotingMessageChecked(MemberOfTheWeekRound round) {
        log.info(
                "Checked Member of the Week voting message | roundId={} | messageId={}",
                round.getId(),
                round.getMessageId());
    }

    private Mono<MemberOfTheWeekRound> keepRoundAfterRefreshFailure(MemberOfTheWeekRound round, Throwable error) {
        log.warn(
                "Could not refresh Member of the Week voting message | roundId={} | messageId={}",
                round.getId(),
                round.getMessageId(),
                error);
        return Mono.just(round);
    }

    private Mono<MemberOfTheWeekRound> rollbackFailedRound(MemberOfTheWeekRound savedRound, Throwable error) {
        log.error(
                "Failed to send voting message; deleting newly created round | roundId={}", savedRound.getId(), error);
        return roundRepository.delete(savedRound).then(Mono.error(error));
    }

    private void logVotingMessageSent(Message message) {
        log.info(
                "Member of the Week voting message sent | channel={} | message={}",
                properties.channelId(),
                message.getId().asString());
    }

    private void logResultsAnnounced(Message message) {
        log.info(
                "Member of the Week results announced | message={}",
                message.getId().asString());
    }

    private Mono<Locale> getGuildLocale() {
        return guildSettingsRepository
                .findById(properties.guildId())
                .map(GuildSettings::getLocale)
                .map(SupportedLocale::forLanguageTag)
                .map(SupportedLocale::getLocale)
                .defaultIfEmpty(SupportedLocale.ENGLISH.getLocale());
    }

    private Mono<Long> countVotes(String roundId) {
        return mongoTemplate.count(Query.query(Criteria.where("roundId").is(roundId)), "member_of_the_week_votes");
    }

    private MemberOfTheWeekVoteCount pickRandomWinner(List<MemberOfTheWeekVoteCount> winners) {
        return winners.get(ThreadLocalRandom.current().nextInt(winners.size()));
    }

    private Mono<List<MemberOfTheWeekVoteCount>> findWinners(String roundId) {
        Aggregation aggregation = newAggregation(
                match(Criteria.where("roundId").is(roundId)),
                group("candidateId").count().as("votes"),
                sort(Sort.Direction.DESC, "votes"));

        return mongoTemplate
                .aggregate(aggregation, "member_of_the_week_votes", Document.class)
                .map(document -> {
                    String candidateId = document.getString("_id");

                    Number votes = document.get("votes", Number.class);

                    return new MemberOfTheWeekVoteCount(candidateId, votes.longValue());
                })
                .collectList()
                .map(this::onlyHighestVoteCounts);
    }

    private List<MemberOfTheWeekVoteCount> onlyHighestVoteCounts(List<MemberOfTheWeekVoteCount> results) {
        if (results.isEmpty()) {
            return List.of();
        }

        long highestVoteCount = results.getFirst().votes();

        return results.stream()
                .filter(result -> result.votes() == highestVoteCount)
                .toList();
    }

    private Mono<MessageChannel> getVotingChannel() {
        return guildSettingsRepository
                .findById(properties.guildId())
                .map(GuildSettings::getMemberOfTheWeekChannelId)
                .filter(channelId -> channelId != null && !channelId.isBlank())
                .defaultIfEmpty(properties.channelId())
                .flatMap(this::getVotingChannel);
    }

    private Mono<MessageChannel> getVotingChannel(String configuredChannelId) {
        String channelId = configuredChannelId == null || configuredChannelId.isBlank()
                ? properties.channelId()
                : configuredChannelId;

        return gatewayDiscordClient
                .getChannelById(Snowflake.of(channelId))
                .ofType(MessageChannel.class)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Member of the Week channel does not exist or is not a message channel: " + channelId)));
    }
}
