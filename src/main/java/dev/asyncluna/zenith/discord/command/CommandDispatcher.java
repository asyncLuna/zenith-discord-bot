package dev.asyncluna.zenith.discord.command;

import dev.asyncluna.zenith.core.i18n.I18nManager;
import dev.asyncluna.zenith.core.i18n.SupportedLocale;
import dev.asyncluna.zenith.discord.settings.GuildSettingsProvider;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CommandDispatcher {
    private final CommandRegistry commandRegistry;
    private final GuildSettingsProvider guildSettingsProvider;
    private final I18nManager i18nManager;
    private final CommandErrorHandler commandErrorHandler;

    public CommandDispatcher(
            CommandRegistry commandRegistry,
            GuildSettingsProvider guildSettingsProvider,
            I18nManager i18nManager,
            CommandErrorHandler commandErrorHandler) {
        this.commandRegistry = commandRegistry;
        this.guildSettingsProvider = guildSettingsProvider;
        this.i18nManager = i18nManager;
        this.commandErrorHandler = commandErrorHandler;
    }

    public Mono<Void> dispatch(ChatInputInteractionEvent event) {
        return Mono.defer(() -> {
            String commandName = event.getCommandName();
            BotCommand command = commandRegistry.get(commandName);

            boolean isEphemeral = false;
            if (command != null) {
                Command meta = command.getClass().getAnnotation(Command.class);
                if (meta != null) isEphemeral = meta.ephemeral();
            }

            return event.deferReply().withEphemeral(isEphemeral).then(Mono.defer(() -> {
                String guildIdStr = event.getInteraction()
                        .getGuildId()
                        .map(Snowflake::asString)
                        .orElse("");

                return guildSettingsProvider.get(guildIdStr).flatMap(settings -> {
                    if (command == null) {
                        Locale locale = SupportedLocale.forLanguageTag(settings.getLocale())
                                .getLocale();
                        String localizedError = i18nManager.localize("error.unknown_command", locale);

                        return event.editReply(localizedError).then();
                    }

                    logCommandExecution(event, commandName);

                    CommandContext ctx = new CommandContext(event, settings, i18nManager);

                    return command.handleAsync(ctx)
                            .then()
                            .onErrorResume(
                                    commandException -> commandErrorHandler.handle(ctx, commandName, commandException));
                });
            }));
        });
    }

    public Mono<Void> dispatchAutocomplete(ChatInputAutoCompleteEvent event) {
        return Mono.defer(() -> {
            String commandName = event.getCommandName();
            BotCommand command = commandRegistry.get(commandName);

            if (command == null) return event.respondWithSuggestions(Collections.emptyList());

            return command.autocompleteAsync(event).onErrorResume(exception -> {
                log.error(
                        "Unhandled exception during autocomplete execution for command '/{}'", commandName, exception);
                return event.respondWithSuggestions(Collections.emptyList());
            });
        });
    }

    private void logCommandExecution(ChatInputInteractionEvent event, String commandName) {
        StringBuilder commandBuilder = new StringBuilder("/").append(commandName);
        List<String> optionsList = new ArrayList<>();

        buildOptionsLog(event.getOptions(), commandBuilder, optionsList);

        String formattedOptions = optionsList.isEmpty() ? "" : " [" + String.join(", ", optionsList) + "]";

        log.info(
                "Dispatching command: '{}{}' | User: {} ({}) | Guild: {}",
                commandBuilder,
                formattedOptions,
                event.getInteraction().getUser().getUsername(),
                event.getInteraction().getUser().getId().asString(),
                event.getInteraction().getGuildId().map(Snowflake::asString).orElse("DM"));
    }

    private void buildOptionsLog(
            List<ApplicationCommandInteractionOption> options, StringBuilder commandBuilder, List<String> optionsList) {

        for (ApplicationCommandInteractionOption option : options) {
            if (option.getValue().isPresent()) {
                optionsList.add(option.getName() + "=" + option.getValue().get().getRaw());
            } else {
                commandBuilder.append(" ").append(option.getName());

                if (!option.getOptions().isEmpty()) {
                    buildOptionsLog(option.getOptions(), commandBuilder, optionsList);
                }
            }
        }
    }
}
