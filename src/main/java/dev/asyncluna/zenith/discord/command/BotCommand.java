package dev.asyncluna.zenith.discord.command;

import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import java.util.Collections;
import reactor.core.publisher.Mono;

public interface BotCommand {
    Mono<?> handle(CommandContext context);

    default Mono<?> handleAsync(CommandContext context) {
        return handle(context);
    }

    default Mono<Void> autocomplete(ChatInputAutoCompleteEvent event) {
        return event.respondWithSuggestions(Collections.emptyList());
    }

    default Mono<Void> autocompleteAsync(ChatInputAutoCompleteEvent event) {
        return autocomplete(event);
    }
}
