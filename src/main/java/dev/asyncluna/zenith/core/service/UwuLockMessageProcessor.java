package dev.asyncluna.zenith.core.service;

import dev.asyncluna.zenith.core.util.Uwuifier;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.Webhook;
import discord4j.core.object.entity.channel.ThreadChannel;
import discord4j.core.object.entity.channel.TopLevelGuildMessageChannel;
import discord4j.core.spec.WebhookCreateSpec;
import discord4j.core.spec.WebhookExecuteSpec;
import discord4j.rest.util.AllowedMentions;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Handles the Discord-specific message transformation for the Uwu Lock feature. */
@Component
@RequiredArgsConstructor
public class UwuLockMessageProcessor {
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?://)?([a-zA-Z0-9\\-_]+\\.)+[a-zA-Z]{2,}(/[\\w\\-_~:/?#\\[\\]@!$&'()*+,;=.\\u00A0-\\uFFFF]*)?$");

    private final Uwuifier uwuifier;

    public Mono<Void> processAsync(MessageCreateEvent event, Member member) {
        Message message = event.getMessage();

        return message.getChannel()
                .flatMap(channel -> {
                    Mono<TopLevelGuildMessageChannel> parentChannel;
                    Optional<Snowflake> threadId;

                    if (channel instanceof ThreadChannel thread) {
                        parentChannel = thread.getParent().cast(TopLevelGuildMessageChannel.class);
                        threadId = Optional.of(thread.getId());
                    } else if (channel instanceof TopLevelGuildMessageChannel guildChannel) {
                        parentChannel = Mono.just(guildChannel);
                        threadId = Optional.empty();
                    } else {
                        return Mono.empty();
                    }

                    return parentChannel.flatMap(parent -> message.delete()
                            .then(findOrCreateWebhook(parent))
                            .flatMap(webhook -> executeReplacement(webhook, member, message, threadId)));
                })
                .then();
    }

    private Mono<Void> executeReplacement(
            Webhook webhook, Member member, Message message, Optional<Snowflake> threadId) {
        WebhookExecuteSpec.Builder builder = WebhookExecuteSpec.builder()
                .username(member.getDisplayName())
                .avatarUrl(member.getAvatarUrl())
                .content(determineReplacementContent(message))
                .allowedMentions(AllowedMentions.builder().build());
        threadId.ifPresent(builder::threadId);
        return webhook.execute(builder.build()).then();
    }

    private String determineReplacementContent(Message message) {
        String content = message.getContent().trim();
        boolean hasNativeMedia = !message.getAttachments().isEmpty()
                || !message.getStickersItems().isEmpty();
        if (content.isEmpty() || hasNativeMedia || containsOnlyLinks(content)) {
            return uwuifier.getRandomMessage();
        }
        return uwuifier.uwuify(content);
    }

    private boolean containsOnlyLinks(String content) {
        String[] tokens = content.split("\\s+");
        for (String token : tokens) {
            if (!URL_PATTERN.matcher(token).matches()) {
                return false;
            }
        }
        return tokens.length > 0;
    }

    private Mono<Webhook> findOrCreateWebhook(TopLevelGuildMessageChannel channel) {
        return channel.getWebhooks()
                .filter(webhook -> webhook.getCreator().isPresent()
                        && webhook.getCreator()
                                .get()
                                .getId()
                                .equals(channel.getClient().getSelfId()))
                .next()
                .switchIfEmpty(channel.createWebhook(
                        WebhookCreateSpec.builder().name("Zenith-UwuLock").build()));
    }
}
