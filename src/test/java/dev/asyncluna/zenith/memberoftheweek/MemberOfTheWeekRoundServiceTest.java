package dev.asyncluna.zenith.memberoftheweek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import dev.asyncluna.zenith.core.i18n.I18nManager;
import dev.asyncluna.zenith.core.repository.GuildSettingsRepository;
import dev.asyncluna.zenith.memberoftheweek.config.MemberOfTheWeekProperties;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekRound;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekRoundStatus;
import dev.asyncluna.zenith.memberoftheweek.repository.MemberOfTheWeekRoundRepository;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.MessageCreateSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class MemberOfTheWeekRoundServiceTest {
    @Test
    void doesNotAnnounceAgainWhenRoleUpdateFailsAfterAnnouncement() {
        var gateway = mock(GatewayDiscordClient.class);
        var repository = mock(MemberOfTheWeekRoundRepository.class);
        var settings = mock(GuildSettingsRepository.class);
        var factory = mock(MemberOfTheWeekMessageFactory.class);
        var mongo = mock(ReactiveMongoTemplate.class);
        var channel = mock(MessageChannel.class);
        var message = mock(Message.class);
        var round = MemberOfTheWeekRound.builder()
                .id("round")
                .guildId("1")
                .endsAt(Instant.EPOCH)
                .status(MemberOfTheWeekRoundStatus.OPEN)
                .build();
        var closed = MemberOfTheWeekRound.builder()
                .id("round")
                .status(MemberOfTheWeekRoundStatus.CLOSED)
                .build();
        var properties = new MemberOfTheWeekProperties("1", "2", null, "3", null, "UTC", "", Duration.ofDays(7));
        var service = new MemberOfTheWeekRoundService(
                gateway, properties, repository, settings, mock(I18nManager.class), factory, mongo, Clock.systemUTC());

        when(repository.findFirstByGuildIdAndStatusOrderByStartsAtDesc("1", MemberOfTheWeekRoundStatus.OPEN))
                .thenReturn(Mono.just(round));
        when(mongo.aggregate(any(Aggregation.class), eq("member_of_the_week_votes"), eq(Document.class)))
                .thenReturn(Flux.empty());
        when(mongo.count(any(Query.class), eq("member_of_the_week_votes"))).thenReturn(Mono.just(0L));
        AtomicBoolean claimed = new AtomicBoolean();
        when(mongo.findAndModify(
                        any(Query.class),
                        any(Update.class),
                        any(FindAndModifyOptions.class),
                        eq(MemberOfTheWeekRound.class)))
                .thenAnswer(invocation ->
                        Mono.defer(() -> claimed.compareAndSet(false, true) ? Mono.just(closed) : Mono.empty()));
        when(settings.findById("1")).thenReturn(Mono.empty());
        when(gateway.getChannelById(Snowflake.of("2"))).thenReturn(Mono.just(channel));
        when(factory.createResultsMessage(any(), any(), anyLong(), any()))
                .thenReturn(MessageCreateSpec.builder().build());
        when(channel.createMessage(any(MessageCreateSpec.class))).thenReturn(Mono.just(message));
        when(message.getId()).thenReturn(Snowflake.of("4"));
        when(gateway.getGuildById(Snowflake.of("1"))).thenReturn(Mono.error(new IllegalStateException("role failure")));

        assertThatThrownBy(() -> service.closeExpiredRound().block()).hasMessageContaining("role failure");
        assertThat(service.closeExpiredRound().block()).isNull();
        verify(channel, times(1)).createMessage(any(MessageCreateSpec.class));
        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(Update.class);
        verify(mongo, times(2))
                .findAndModify(
                        query.capture(),
                        update.capture(),
                        any(FindAndModifyOptions.class),
                        eq(MemberOfTheWeekRound.class));
        assertThat(query.getValue().getQueryObject())
                .containsEntry("id", "round")
                .containsEntry("status", MemberOfTheWeekRoundStatus.OPEN);
        assertThat(update.getValue().getUpdateObject().get("$set", Document.class))
                .containsEntry("status", MemberOfTheWeekRoundStatus.CLOSED);
    }
}
