package dev.asyncluna.zenith.discord.feature.confessions;

import dev.asyncluna.zenith.core.i18n.I18nManager;
import dev.asyncluna.zenith.core.i18n.SupportedLocale;
import dev.asyncluna.zenith.core.model.GuildSettings;
import dev.asyncluna.zenith.discord.listener.EventListener;
import dev.asyncluna.zenith.discord.settings.GuildSettingsProvider;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.FileUpload;
import discord4j.core.object.component.TextInput;
import discord4j.core.object.emoji.Emoji;
import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.Color;
import java.util.Collections;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConfessionModalListener implements EventListener<ModalSubmitInteractionEvent> {
    private static final Color CONFESSION_BLUE = Color.of(0x3498DB);
    static final String REPLY_MODAL_PREFIX = "confession_reply_modal:";
    static final String REPLY_INPUT_ID = "confession_reply_input";
    private final GuildSettingsProvider guildSettingsProvider;
    private final I18nManager i18nManager;
    private final ConfessionCooldown confessionCooldown;

    @Override
    public Mono<Void> execute(ModalSubmitInteractionEvent event) {
        String customId = event.getCustomId();
        String guildId =
                event.getInteraction().getGuildId().map(Snowflake::asString).orElse("");
        return guildSettingsProvider.get(guildId).flatMap(settings -> {
            Locale locale = SupportedLocale.forLanguageTag(settings.getLocale()).getLocale();
            if (customId.startsWith(REPLY_MODAL_PREFIX)) return handleReply(event, customId, locale, settings);
            return handleConfession(event, customId, locale, settings);
        });
    }

    private Mono<Void> handleConfession(
            ModalSubmitInteractionEvent event, String customId, Locale locale, GuildSettings settings) {
        if (!customId.equals("confession_modal")) return Mono.empty();

        log.info(
                "Received confession modal submission | Guild: {} | User: {} ({})",
                event.getInteraction().getGuildId().map(Snowflake::asString).orElse("N/A"),
                event.getInteraction().getUser().getUsername(),
                event.getInteraction().getUser().getId().asString());

        String confessionMessage = event.getComponents(TextInput.class).stream()
                .filter(component -> component.getCustomId().equals("confession_input"))
                .findFirst()
                .flatMap(TextInput::getValue)
                .orElse("");

        if (confessionMessage.isBlank())
            return event.reply(InteractionApplicationCommandCallbackSpec.builder()
                            .content(i18nManager.localize("confessions.empty", locale))
                            .ephemeral(true)
                            .build())
                    .then();

        String guildId =
                event.getInteraction().getGuildId().map(Snowflake::asString).orElse("unknown");
        String userId = event.getInteraction().getUser().getId().asString();

        if (!confessionCooldown.tryStart(guildId, userId))
            return event.reply(InteractionApplicationCommandCallbackSpec.builder()
                            .content(i18nManager.localize("confessions.cooldown", locale))
                            .ephemeral(true)
                            .build())
                    .then();

        Attachment attachment = event.getComponents(FileUpload.class).stream()
                .filter(component -> component.getCustomId().equals("confession_upload"))
                .findFirst()
                .flatMap(component -> component.getValues().orElse(Collections.emptyList()).stream()
                        .findFirst())
                .flatMap(attachmentId -> event.getResolved()
                        .map(resolved -> resolved.getAttachments().get(attachmentId)))
                .orElse(null);

        User author = event.getInteraction().getUser();

        return event.deferReply()
                .withEphemeral(true)
                .then(event.getInteraction().getGuild().flatMap(guild -> guild.getChannels()
                        .filter(channel -> channel instanceof TextChannel)
                        .cast(TextChannel.class)
                        .collectList()
                        .flatMap(channels -> {
                            TextChannel confessionsChannel = channels.stream()
                                    .filter(channel -> matchesConfiguredChannel(
                                            channel, settings.getConfessionsChannelId(), "confessions"))
                                    .findFirst()
                                    .orElse(null);

                            TextChannel logChannel = channels.stream()
                                    .filter(channel -> channel.getName().equalsIgnoreCase("confessionsÃ¯Â¹â€™log"))
                                    .findFirst()
                                    .orElse(null);

                            if (confessionsChannel == null) {
                                return Mono.error(new IllegalStateException("Confessions channel was not found."));
                            }

                            if (settings.getConfessionsLogChannelId() != null
                                    && !settings.getConfessionsLogChannelId().isBlank()) {
                                logChannel = channels.stream()
                                        .filter(channel -> channel.getId()
                                                .asString()
                                                .equals(settings.getConfessionsLogChannelId()))
                                        .findFirst()
                                        .orElse(null);
                            }

                            EmbedCreateSpec.Builder publicEmbedBuilder = EmbedCreateSpec.builder()
                                    .title(i18nManager.localize("confessions.embed.title", locale))
                                    .description(confessionMessage)
                                    .color(CONFESSION_BLUE);

                            if (attachment != null) publicEmbedBuilder.image(attachment.getUrl());

                            Button confessionButton = Button.primary(
                                    ConfessionButtonListener.SUBMIT_BUTTON_ID,
                                    Emoji.unicode("\uD83E\uDD2B"),
                                    i18nManager.localize("confessions.button.submit", locale));
                            MessageCreateSpec publicMessageSpec = MessageCreateSpec.builder()
                                    .addEmbed(publicEmbedBuilder.build())
                                    .addComponent(ActionRow.of(confessionButton))
                                    .build();

                            Mono<Void> sendPublic = confessionsChannel
                                    .createMessage(publicMessageSpec)
                                    .flatMap(publicMessage -> publicMessage
                                            .edit()
                                            .withComponents(ActionRow.of(
                                                    confessionButton,
                                                    Button.secondary(
                                                            ConfessionButtonListener.REPLY_BUTTON_PREFIX
                                                                    + publicMessage
                                                                            .getId()
                                                                            .asString(),
                                                            i18nManager.localize("confessions.button.reply", locale))))
                                            .then());

                            Mono<Void> sendToLog = Mono.empty();
                            if (logChannel != null) {
                                EmbedCreateSpec.Builder logEmbedBuilder = EmbedCreateSpec.builder()
                                        .title(i18nManager.localize("confessions.log.title", locale))
                                        .description(confessionMessage)
                                        .addField(
                                                i18nManager.localize("confessions.log.author", locale),
                                                author.getMention()
                                                        + " ("
                                                        + author.getId().asString()
                                                        + ")",
                                                false)
                                        .color(CONFESSION_BLUE);

                                if (attachment != null) logEmbedBuilder.image(attachment.getUrl());

                                sendToLog = logChannel
                                        .createMessage(logEmbedBuilder.build())
                                        .then();
                            }

                            return Mono.when(sendPublic, sendToLog);
                        })))
                .then(event.editReply(i18nManager.localize("confessions.sent", locale)))
                .onErrorResume(exception -> {
                    confessionCooldown.clear(guildId, userId);
                    log.error("Failed to process confession modal", exception);
                    return event.editReply(i18nManager.localize("confessions.send_failed", locale));
                })
                .then();
    }

    private Mono<Void> handleReply(
            ModalSubmitInteractionEvent event, String customId, Locale locale, GuildSettings settings) {
        String confessionMessageId = customId.substring(REPLY_MODAL_PREFIX.length());
        String reply = event.getComponents(TextInput.class).stream()
                .filter(component -> component.getCustomId().equals(REPLY_INPUT_ID))
                .findFirst()
                .flatMap(TextInput::getValue)
                .orElse("");

        if (confessionMessageId.isBlank() || reply.isBlank()) {
            return event.reply(InteractionApplicationCommandCallbackSpec.builder()
                            .content(i18nManager.localize("confessions.reply.empty", locale))
                            .ephemeral(true)
                            .build())
                    .then();
        }

        String guildId =
                event.getInteraction().getGuildId().map(Snowflake::asString).orElse("unknown");
        String responderId = event.getInteraction().getUser().getId().asString();

        return event.deferReply()
                .withEphemeral(true)
                .then(event.getInteraction()
                        .getChannel()
                        .cast(TextChannel.class)
                        .flatMap(channel -> {
                            String confessionLink = "https://discord.com/channels/"
                                    + guildId
                                    + "/"
                                    + channel.getId().asString()
                                    + "/"
                                    + confessionMessageId;

                            EmbedCreateSpec replyEmbed = EmbedCreateSpec.builder()
                                    .title(i18nManager.localize("confessions.reply.embed.title", locale))
                                    .description(reply)
                                    .addField(
                                            i18nManager.localize("confessions.reply.embed.response_to", locale),
                                            "[View confession](" + confessionLink + ")",
                                            false)
                                    .color(CONFESSION_BLUE)
                                    .build();

                            Mono<Void> sendPublic =
                                    channel.createMessage(replyEmbed).then();
                            return findLogChannel(event)
                                    .flatMap(logChannel -> logChannel
                                            .createMessage(EmbedCreateSpec.builder()
                                                    .title(i18nManager.localize("confessions.reply.log.title", locale))
                                                    .description(reply)
                                                    .addField(
                                                            i18nManager.localize(
                                                                    "confessions.reply.log.responder", locale),
                                                            "<@" + responderId + "> (" + responderId + ")",
                                                            false)
                                                    .addField(
                                                            i18nManager.localize(
                                                                    "confessions.reply.log.confession", locale),
                                                            confessionLink,
                                                            false)
                                                    .color(CONFESSION_BLUE)
                                                    .build())
                                            .then())
                                    .switchIfEmpty(Mono.empty())
                                    .then(sendPublic);
                        }))
                .then(event.editReply(i18nManager.localize("confessions.reply.sent", locale)))
                .onErrorResume(exception -> {
                    log.error("Failed to process confession reply", exception);
                    return event.editReply(i18nManager.localize("confessions.reply.send_failed", locale));
                })
                .then();
    }

    private Mono<TextChannel> findLogChannel(ModalSubmitInteractionEvent event, GuildSettings settings) {
        String configuredChannelId = settings.getConfessionsLogChannelId();
        if (configuredChannelId == null || configuredChannelId.isBlank()) {
            return findLogChannel(event);
        }

        return event.getInteraction()
                .getGuild()
                .flatMapMany(guild -> guild.getChannels())
                .filter(channel -> channel instanceof TextChannel)
                .cast(TextChannel.class)
                .filter(channel -> channel.getId().asString().equals(configuredChannelId))
                .next();
    }

    private Mono<TextChannel> findLogChannel(ModalSubmitInteractionEvent event) {
        return event.getInteraction()
                .getGuild()
                .flatMapMany(guild -> guild.getChannels())
                .filter(channel -> channel instanceof TextChannel)
                .cast(TextChannel.class)
                .filter(channel -> channel.getName().equalsIgnoreCase("confessionsÃ¯Â¹â€™log"))
                .next();
    }

    private boolean matchesConfiguredChannel(TextChannel channel, String configuredChannelId, String fallbackName) {
        return configuredChannelId != null && !configuredChannelId.isBlank()
                ? channel.getId().asString().equals(configuredChannelId)
                : channel.getName().equalsIgnoreCase(fallbackName);
    }
}
