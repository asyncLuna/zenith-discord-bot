package dev.asyncluna.zenith.memberoftheweek;

import dev.asyncluna.zenith.core.i18n.I18nManager;
import dev.asyncluna.zenith.discord.util.EmbedUtils;
import dev.asyncluna.zenith.memberoftheweek.config.MemberOfTheWeekProperties;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekRound;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekVoteCount;
import discord4j.common.util.Snowflake;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.AllowedMentions;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Creates the Discord messages used by the Member of the Week lifecycle. */
@Component
@RequiredArgsConstructor
public class MemberOfTheWeekMessageFactory {
    private final I18nManager i18nManager;
    private final MemberOfTheWeekProperties properties;
    private final Clock memberOfTheWeekClock;

    public MessageCreateSpec createVotingMessage(MemberOfTheWeekRound round, Locale locale) {
        SelectMenu selectMenu = SelectMenu.ofUser(
                        MemberOfTheWeekRoundService.COMPONENT_ID_PREFIX + round.getId(), Collections.emptyList())
                .withPlaceholder(i18nManager.localize("member_of_the_week.select_member", locale))
                .withMinValues(1)
                .withMaxValues(1);

        MessageCreateSpec.Builder message = MessageCreateSpec.builder()
                .addEmbed(createVotingEmbed(round, locale))
                .addComponent(ActionRow.of(selectMenu));
        addRoleMention(message);
        return message.build();
    }

    public EmbedCreateSpec createVotingEmbed(MemberOfTheWeekRound round, Locale locale) {
        return EmbedCreateSpec.builder()
                .color(EmbedUtils.DEFAULT_COLOR)
                .title(i18nManager.localize("member_of_the_week.embed.title", locale))
                .description(createVotingDescription(round, locale))
                .timestamp(Instant.now(memberOfTheWeekClock))
                .build();
    }

    public MessageCreateSpec createResultsMessage(
            List<MemberOfTheWeekVoteCount> winners,
            MemberOfTheWeekVoteCount selectedWinner,
            long totalVotes,
            Locale locale) {
        EmbedCreateSpec embed = EmbedCreateSpec.builder()
                .color(EmbedUtils.DEFAULT_COLOR)
                .title(i18nManager.localize("member_of_the_week.results.title", locale))
                .description(createResultsDescription(winners, selectedWinner, totalVotes, locale))
                .timestamp(Instant.now(memberOfTheWeekClock))
                .build();

        MessageCreateSpec.Builder message = MessageCreateSpec.builder().addEmbed(embed);
        if (selectedWinner != null) {
            Snowflake winnerId = Snowflake.of(selectedWinner.candidateId());
            message.content("<@" + winnerId.asString() + ">")
                    .allowedMentions(
                            AllowedMentions.builder().allowUser(winnerId).build());
        }
        return message.build();
    }

    private void addRoleMention(MessageCreateSpec.Builder message) {
        if (properties.roleId() == null || properties.roleId().isBlank()) {
            return;
        }
        Snowflake roleId = Snowflake.of(properties.roleId());
        message.content("<@&" + roleId.asString() + ">")
                .allowedMentions(AllowedMentions.builder().allowRole(roleId).build());
    }

    public String createVotingDescription(MemberOfTheWeekRound round, Locale locale) {
        String endsAt = MemberOfTheWeekTimeFormatter.format(round.getEndsAt(), memberOfTheWeekClock.getZone());
        return i18nManager.localize("member_of_the_week.embed.description", locale, endsAt);
    }

    private String createResultsDescription(
            List<MemberOfTheWeekVoteCount> winners,
            MemberOfTheWeekVoteCount selectedWinner,
            long totalVotes,
            Locale locale) {
        String result;
        if (winners.isEmpty()) {
            result = i18nManager.localize("member_of_the_week.results.no_votes", locale);
        } else if (winners.size() == 1) {
            MemberOfTheWeekVoteCount winner = winners.getFirst();
            result = i18nManager.localize(
                    "member_of_the_week.results.winner",
                    locale,
                    mention(winner),
                    winner.votes(),
                    voteLabel(winner.votes(), locale));
        } else {
            long voteCount = winners.getFirst().votes();
            String mentions = winners.stream()
                    .map(this::mention)
                    .reduce((first, second) -> first + ", " + second)
                    .orElse("");
            result = i18nManager.localize(
                    "member_of_the_week.results.tie",
                    locale,
                    mentions,
                    voteCount,
                    voteLabel(voteCount, locale),
                    mention(selectedWinner));
        }
        return result + "\n\n" + i18nManager.localize("member_of_the_week.results.total", locale, totalVotes);
    }

    private String voteLabel(long votes, Locale locale) {
        return i18nManager.localize(
                votes == 1 ? "member_of_the_week.results.vote" : "member_of_the_week.results.votes", locale);
    }

    private String mention(MemberOfTheWeekVoteCount voteCount) {
        return "<@" + voteCount.candidateId() + ">";
    }
}
