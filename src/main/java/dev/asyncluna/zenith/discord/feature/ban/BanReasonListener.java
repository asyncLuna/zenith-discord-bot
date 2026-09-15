package dev.asyncluna.zenith.discord.feature.ban;

import dev.asyncluna.zenith.core.i18n.I18nManager;
import dev.asyncluna.zenith.core.i18n.SupportedLocale;
import dev.asyncluna.zenith.discord.listener.EventListener;
import dev.asyncluna.zenith.discord.settings.GuildSettingsProvider;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.guild.BanEvent;
import discord4j.core.object.audit.ActionType;
import discord4j.core.object.audit.AuditLogPart;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.AuditLogQuerySpec;
import discord4j.core.spec.MessageCreateSpec;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@RequiredArgsConstructor
public class BanReasonListener implements EventListener<BanEvent> {
    private static final Snowflake MOD_CHANNEL_ID = Snowflake.of("1520157330946261202");
    private static final String DEFAULT_BAN_REASON = "No reason provided";
    private final GuildSettingsProvider guildSettingsProvider;
    private final I18nManager i18nManager;

    @Override
    public Mono<Void> execute(BanEvent event) {
        log.info(
                "BanEvent received | guild={} | user={} ({})",
                event.getGuildId().asString(),
                event.getUser().getTag(),
                event.getUser().getId().asString());

        return Mono.just(event)
                .delayElement(Duration.ofSeconds(1))
                .flatMap(e -> e.getGuild()
                        .doOnNext(guild -> logAuditLogFetch(guild, e))
                        .flatMap(guild -> guild.getAuditLog(AuditLogQuerySpec.builder()
                                        .actionType(ActionType.MEMBER_BAN_ADD)
                                        .build())
                                .doOnNext(this::logAuditLogPartReceived)
                                .next()
                                .switchIfEmpty(Mono.fromRunnable(() -> logNoAuditLogPart(e)))
                                .flatMap(part -> processAuditPart(part, e))))
                .onErrorResume(this::handleException)
                .then();
    }

    private Mono<Void> processAuditPart(AuditLogPart part, BanEvent event) {
        try {
            String bannedId = event.getUser().getId().asString();

            log.info(
                    "Inspecting audit log part | guild={} | bannedUser={} | entryCount={}",
                    part.getGuildId().asString(),
                    bannedId,
                    part.getEntries().size());

            return Mono.justOrEmpty(part.getEntries().stream()
                            .filter(entry -> entry.getTargetId()
                                    .map(Snowflake::asString)
                                    .orElse("")
                                    .equals(bannedId))
                            .findFirst())
                    .flatMap(entry -> {
                        log.info(
                                "Matched audit entry | target={} | responsibleUser={} | reason={}",
                                entry.getTargetId().map(Snowflake::asString).orElse("N/A"),
                                entry.getResponsibleUserId()
                                        .map(Snowflake::asString)
                                        .orElse("N/A"),
                                entry.getReason().orElse("<empty>"));

                        if (isMissingReason(entry.getReason().orElse(null))) {
                            log.info(
                                    "Ban reason missing/default, preparing reminder | channelId={} | bannedUser={}",
                                    MOD_CHANNEL_ID.asString(),
                                    event.getUser().getId().asString());

                            return entry.getResponsibleUserId()
                                    .map(responsible -> event.getGuild()
                                            .doOnNext(guild -> logReminderSend(guild, responsible))
                                            .flatMap(guild -> guildSettingsProvider
                                                    .get(part.getGuildId().asString())
                                                    .map(settings -> settings.getModerationLogChannelId() == null
                                                                    || settings.getModerationLogChannelId()
                                                                            .isBlank()
                                                            ? MOD_CHANNEL_ID
                                                            : Snowflake.of(settings.getModerationLogChannelId()))
                                                    .flatMap(guild::getChannelById))
                                            .cast(TextChannel.class)
                                            .doOnNext(this::logReminderChannelResolved)
                                            .flatMap(channel -> guildSettingsProvider
                                                    .get(part.getGuildId().asString())
                                                    .map(settings -> SupportedLocale.forLanguageTag(
                                                                    settings.getLocale())
                                                            .getLocale())
                                                    .flatMap(locale -> channel.createMessage(MessageCreateSpec.builder()
                                                            .content(i18nManager.localize(
                                                                    "ban.missing_reason",
                                                                    locale,
                                                                    "<@" + responsible.asString() + ">",
                                                                    event.getUser()
                                                                            .getTag()))
                                                            .build()))))
                                    .orElseGet(() -> {
                                        log.info(
                                                "No responsible user id found in audit entry | guild={} | bannedUser={}",
                                                part.getGuildId().asString(),
                                                bannedId);
                                        return Mono.empty();
                                    });
                        }

                        log.info(
                                "Ban reason present, no reminder needed | bannedUser={} | reason={}",
                                bannedId,
                                entry.getReason().orElse("<empty>"));
                        return Mono.empty();
                    })
                    .switchIfEmpty(Mono.fromRunnable(() -> log.info(
                            "No matching audit entry found for banned user | guild={} | bannedUser={}",
                            part.getGuildId().asString(),
                            bannedId)))
                    .then();
        } catch (Exception exception) {
            log.error("Failed while processing audit part for ban event", exception);
            return Mono.empty();
        }
    }

    private boolean isMissingReason(String reason) {
        return reason == null || reason.isBlank() || reason.equalsIgnoreCase(DEFAULT_BAN_REASON);
    }

    private void logAuditLogFetch(discord4j.core.object.entity.Guild guild, BanEvent event) {
        log.info(
                "Fetching ban audit log | guild={} | bannedUser={}",
                guild.getId().asString(),
                event.getUser().getId().asString());
    }

    private void logAuditLogPartReceived(AuditLogPart part) {
        log.info(
                "Received audit log part | guild={} | entries={}",
                part.getGuildId().asString(),
                part.getEntries().size());
    }

    private void logNoAuditLogPart(BanEvent event) {
        log.info(
                "No audit log part returned for ban event | guild={} | bannedUser={}",
                event.getGuildId().asString(),
                event.getUser().getId().asString());
    }

    private void logReminderSend(discord4j.core.object.entity.Guild guild, Snowflake moderatorId) {
        log.info(
                "Sending reminder to channel | guild={} | channelId={} | moderator={}",
                guild.getId().asString(),
                MOD_CHANNEL_ID.asString(),
                moderatorId.asString());
    }

    private void logReminderChannelResolved(TextChannel channel) {
        log.info(
                "Resolved reminder channel | name={} | id={}",
                channel.getName(),
                channel.getId().asString());
    }
}
