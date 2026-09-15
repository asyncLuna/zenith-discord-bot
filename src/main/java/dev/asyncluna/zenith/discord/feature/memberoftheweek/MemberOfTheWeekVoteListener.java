package dev.asyncluna.zenith.discord.feature.memberoftheweek;

import dev.asyncluna.zenith.core.i18n.I18nManager;
import dev.asyncluna.zenith.core.i18n.SupportedLocale;
import dev.asyncluna.zenith.discord.listener.EventListener;
import dev.asyncluna.zenith.discord.settings.GuildSettingsProvider;
import dev.asyncluna.zenith.memberoftheweek.MemberOfTheWeekRoundService;
import dev.asyncluna.zenith.memberoftheweek.MemberOfTheWeekVoteService;
import dev.asyncluna.zenith.memberoftheweek.exception.AlreadyVotedException;
import dev.asyncluna.zenith.memberoftheweek.exception.SelfVoteException;
import dev.asyncluna.zenith.memberoftheweek.exception.VotingRoundClosedException;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemberOfTheWeekVoteListener implements EventListener<SelectMenuInteractionEvent> {
    private final MemberOfTheWeekVoteService voteService;
    private final GuildSettingsProvider guildSettingsProvider;
    private final I18nManager i18nManager;

    @Override
    public Mono<Void> execute(SelectMenuInteractionEvent event) {
        String customId = event.getCustomId();

        if (!customId.startsWith(MemberOfTheWeekRoundService.COMPONENT_ID_PREFIX)) {
            return Mono.empty();
        }

        return event.deferReply()
                .withEphemeral(true)
                .then(handleVote(event))
                .onErrorResume(error -> replyForError(event, error))
                .doOnError(this::logUnexpectedVoteError)
                .onErrorResume(error -> localizedReply(event, "member_of_the_week.vote.failed")
                        .onErrorResume(replyError -> {
                            log.error("Failed to edit deferred Member of the Week interaction response", replyError);

                            return Mono.empty();
                        })
                        .then());
    }

    private void logUnexpectedVoteError(Throwable error) {
        log.error("Unexpected error while handling a Member of the Week vote", error);
    }

    private Mono<Void> handleVote(SelectMenuInteractionEvent event) {
        if (event.getValues().isEmpty()) {
            return localizedReply(event, "member_of_the_week.vote.no_selection");
        }

        String roundId = event.getCustomId().substring(MemberOfTheWeekRoundService.COMPONENT_ID_PREFIX.length());

        Snowflake candidateId = parseCandidateId(event.getValues().getFirst());

        Snowflake voterId = event.getInteraction().getUser().getId();

        return event.getInteraction()
                .getGuild()
                .switchIfEmpty(Mono.error(new IllegalStateException("The interaction did not occur in a guild.")))
                .flatMap(guild -> recordVote(event, guild, roundId, candidateId, voterId))
                .then();
    }

    private Mono<Void> recordVote(
            SelectMenuInteractionEvent event,
            discord4j.core.object.entity.Guild guild,
            String roundId,
            Snowflake candidateId,
            Snowflake voterId) {
        return guild.getMemberById(candidateId)
                .switchIfEmpty(Mono.error(new MemberNotFoundException()))
                .flatMap(candidate -> {
                    if (candidate.isBot()) {
                        return localizedReply(event, "member_of_the_week.vote.bot");
                    }
                    return voteService
                            .recordVote(roundId, guild.getId().asString(), voterId.asString(), candidateId.asString())
                            .then(localizedReply(
                                    event, "member_of_the_week.vote.recorded", candidate.getDisplayName()));
                });
    }

    private Mono<Void> replyForError(SelectMenuInteractionEvent event, Throwable error) {
        String key =
                switch (error) {
                    case SelfVoteException ignored -> "member_of_the_week.vote.self_vote";
                    case AlreadyVotedException ignored -> "member_of_the_week.vote.already_voted";
                    case VotingRoundClosedException ignored -> "member_of_the_week.vote.round_closed";
                    case MemberNotFoundException ignored -> "member_of_the_week.vote.member_not_found";
                    case InvalidSelectionException ignored -> "member_of_the_week.vote.invalid_selection";
                    default -> null;
                };
        return key == null ? Mono.error(error) : localizedReply(event, key);
    }

    private Mono<Void> localizedReply(SelectMenuInteractionEvent event, String key, Object... args) {
        String guildId =
                event.getInteraction().getGuildId().map(Snowflake::asString).orElse("");
        return guildSettingsProvider
                .get(guildId)
                .map(settings ->
                        SupportedLocale.forLanguageTag(settings.getLocale()).getLocale())
                .flatMap(locale ->
                        event.editReply(i18nManager.localize(key, locale, args)).then());
    }

    private Snowflake parseCandidateId(String rawCandidateId) {
        try {
            return Snowflake.of(rawCandidateId);
        } catch (IllegalArgumentException exception) {
            throw new InvalidSelectionException(exception);
        }
    }

    private static final class MemberNotFoundException extends RuntimeException {}

    private static final class InvalidSelectionException extends RuntimeException {
        private InvalidSelectionException(Throwable cause) {
            super(cause);
        }
    }
}
