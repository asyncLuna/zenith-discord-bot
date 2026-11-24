package dev.asyncluna.zenith.memberoftheweek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.asyncluna.zenith.core.i18n.I18nManager;
import dev.asyncluna.zenith.memberoftheweek.config.MemberOfTheWeekProperties;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekRound;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

class MemberOfTheWeekMessageFactoryTest {
    private static final String VOTING_ROLE_ID = "1526317499862356018";
    private static final Locale LOCALE = Locale.ENGLISH;

    @Test
    void mentionsVotingNotificationRoleAndAllowsThatRoleMention() {
        var message = createFactory(VOTING_ROLE_ID).createVotingMessage(round(), LOCALE);
        String request = message.asRequest().getJsonPayload().toString();

        assertThat(request).contains("<@&" + VOTING_ROLE_ID + ">");
        assertThat(request.split(VOTING_ROLE_ID, -1)).hasSize(3);
    }

    @Test
    void omitsVotingRoleMentionWhenNotificationRoleIsUnset() {
        var message = createFactory(null).createVotingMessage(round(), LOCALE);
        String request = message.asRequest().getJsonPayload().toString();

        assertThat(request).doesNotContain("<@&");
        assertThat(request).doesNotContain(VOTING_ROLE_ID);
    }

    private MemberOfTheWeekMessageFactory createFactory(String votingNotificationRoleId) {
        var messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(anyString(), isNull(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var properties = new MemberOfTheWeekProperties(
                "1", "2", null, "1572567577346056202", votingNotificationRoleId, "UTC", "", Duration.ofDays(7));
        return new MemberOfTheWeekMessageFactory(new I18nManager(messageSource), properties, Clock.systemUTC());
    }

    private MemberOfTheWeekRound round() {
        return MemberOfTheWeekRound.builder().id("round").endsAt(Instant.EPOCH).build();
    }
}
