package dev.asyncluna.zenith.memberoftheweek;

import dev.asyncluna.zenith.core.model.GuildSettings;
import dev.asyncluna.zenith.core.repository.GuildSettingsRepository;
import dev.asyncluna.zenith.discord.util.EmbedUtils;
import dev.asyncluna.zenith.memberoftheweek.config.MemberOfTheWeekProperties;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekVote;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.AllowedMentions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberOfTheWeekDiscordNotifier {
    private final GatewayDiscordClient gatewayDiscordClient;
    private final MemberOfTheWeekProperties properties;
    private final GuildSettingsRepository guildSettingsRepository;

    public Mono<Void> sendVoteLog(MemberOfTheWeekVote vote) {
        return guildSettingsRepository
                .findById(properties.guildId())
                .map(GuildSettings::getMemberOfTheWeekLogChannelId)
                .filter(channelId -> channelId != null && !channelId.isBlank())
                .defaultIfEmpty(properties.logChannelId())
                .flatMap(channelId -> sendVoteLog(vote, channelId));
    }

    private Mono<Void> sendVoteLog(MemberOfTheWeekVote vote, String configuredChannelId) {
        if (configuredChannelId == null || configuredChannelId.isBlank()) {
            log.warn("Member of the Week vote log channel is not configured");
            return Mono.empty();
        }

        Snowflake logChannelId;
        try {
            logChannelId = Snowflake.of(configuredChannelId);
        } catch (IllegalArgumentException exception) {
            return Mono.error(new IllegalStateException(
                    "Invalid Member of the Week log channel ID: " + configuredChannelId, exception));
        }

        Snowflake voterId = Snowflake.of(vote.getVoterId());
        Snowflake candidateId = Snowflake.of(vote.getCandidateId());
        EmbedCreateSpec embed = EmbedCreateSpec.builder()
                .color(EmbedUtils.DEFAULT_COLOR)
                .title("Member of the Week vote recorded")
                .addField("Voter", "<@" + vote.getVoterId() + ">", true)
                .addField("Candidate", "<@" + vote.getCandidateId() + ">", true)
                .addField("Round", "`" + vote.getRoundId() + "`", false)
                .timestamp(vote.getCreatedAt())
                .build();
        MessageCreateSpec message = MessageCreateSpec.builder()
                .addEmbed(embed)
                .allowedMentions(AllowedMentions.builder()
                        .allowUser(voterId, candidateId)
                        .build())
                .build();

        return gatewayDiscordClient
                .getChannelById(logChannelId)
                .ofType(MessageChannel.class)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Member of the Week log channel does not exist or is not a message channel: "
                                + configuredChannelId)))
                .flatMap(channel -> channel.createMessage(message))
                .doOnSuccess(createdMessage -> logVoteLogSent(configuredChannelId, createdMessage))
                .then();
    }

    private void logVoteLogSent(String channelId, Message createdMessage) {
        log.info(
                "Member of the Week vote log sent | channel={} | message={}",
                channelId,
                createdMessage.getId().asString());
    }
}
