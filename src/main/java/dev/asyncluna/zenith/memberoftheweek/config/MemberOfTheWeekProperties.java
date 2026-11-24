package dev.asyncluna.zenith.memberoftheweek.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zenith.member-of-the-week")
public record MemberOfTheWeekProperties(
        String guildId,
        String channelId,
        String logChannelId,
        String roleId,
        String votingNotificationRoleId,
        String timezone,
        String cron,
        Duration votingDuration) {}
