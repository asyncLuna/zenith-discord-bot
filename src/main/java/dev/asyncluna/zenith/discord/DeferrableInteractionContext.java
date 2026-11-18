package dev.asyncluna.zenith.discord;

import dev.asyncluna.zenith.core.i18n.I18nManager;
import dev.asyncluna.zenith.core.model.GuildSettings;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.object.component.TopLevelModalComponent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.InteractionApplicationCommandCallbackReplyMono;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.InteractionCallbackSpec;
import discord4j.core.spec.InteractionCallbackSpecDeferReplyMono;
import discord4j.core.spec.InteractionFollowupCreateMono;
import discord4j.core.spec.InteractionFollowupCreateSpec;
import discord4j.core.spec.InteractionFollowupEditMono;
import discord4j.core.spec.InteractionPresentModalMono;
import discord4j.core.spec.InteractionPresentModalSpec;
import discord4j.core.spec.InteractionReplyEditMono;
import discord4j.core.spec.InteractionReplyEditSpec;
import discord4j.rest.interaction.InteractionResponse;
import java.util.Collection;
import reactor.core.publisher.Mono;

public abstract class DeferrableInteractionContext<E extends DeferrableInteractionEvent> extends InteractionContext<E> {
    protected DeferrableInteractionContext(E event, GuildSettings guildSettings, I18nManager i18nManager) {
        super(event, guildSettings, i18nManager);
    }

    public InteractionCallbackSpecDeferReplyMono deferReply() {
        return getEvent().deferReply();
    }

    public Mono<Void> deferReply(InteractionCallbackSpec spec) {
        return getEvent().deferReply(spec);
    }

    public InteractionApplicationCommandCallbackReplyMono reply() {
        return getEvent().reply();
    }

    public InteractionApplicationCommandCallbackReplyMono reply(String content) {
        return getEvent().reply(content);
    }

    public Mono<Void> reply(InteractionApplicationCommandCallbackSpec spec) {
        return getEvent().reply(spec);
    }

    public InteractionPresentModalMono presentModal() {
        return getEvent().presentModal();
    }

    public Mono<Void> presentModal(String title, String customId, Collection<TopLevelModalComponent> components) {
        return getEvent().presentModal(title, customId, components);
    }

    public Mono<Void> presentModal(InteractionPresentModalSpec spec) {
        return getEvent().presentModal(spec);
    }

    public InteractionReplyEditMono editReply() {
        return getEvent().editReply();
    }

    public InteractionReplyEditMono editReply(String content) {
        return getEvent().editReply(content);
    }

    public Mono<Message> editReply(InteractionReplyEditSpec spec) {
        return getEvent().editReply(spec);
    }

    public Mono<Message> getReply() {
        return getEvent().getReply();
    }

    public Mono<Void> deleteReply() {
        return getEvent().deleteReply();
    }

    public InteractionFollowupCreateMono createFollowup() {
        return getEvent().createFollowup();
    }

    public InteractionFollowupCreateMono createFollowup(String content) {
        return getEvent().createFollowup(content);
    }

    public Mono<Message> createFollowup(InteractionFollowupCreateSpec spec) {
        return getEvent().createFollowup(spec);
    }

    public InteractionFollowupEditMono editFollowup(Snowflake messageId) {
        return getEvent().editFollowup(messageId);
    }

    public Mono<Message> editFollowup(Snowflake messageId, InteractionReplyEditSpec spec) {
        return getEvent().editFollowup(messageId, spec);
    }

    public Mono<Void> deleteFollowup(Snowflake messageId) {
        return getEvent().deleteFollowup(messageId);
    }

    public InteractionResponse getInteractionResponse() {
        return getEvent().getInteractionResponse();
    }
}
