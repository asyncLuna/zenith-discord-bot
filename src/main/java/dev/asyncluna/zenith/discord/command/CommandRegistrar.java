package dev.asyncluna.zenith.discord.command;

import discord4j.core.GatewayDiscordClient;
import discord4j.discordjson.json.ApplicationCommandRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class CommandRegistrar implements SmartInitializingSingleton {
    private final GatewayDiscordClient gateway;
    private final CommandRegistry commandRegistry;

    @Override
    public void afterSingletonsInstantiated() {
        registerGlobalSlashCommands()
                .doOnSubscribe(__ -> logCommandSynchronizationStarted())
                .subscribe();
    }

    private void logCommandSynchronizationStarted() {
        log.info("Starting global Discord application command synchronization");
    }

    private Mono<Void> registerGlobalSlashCommands() {
        List<ApplicationCommandRequest> requests = commandRegistry.getCommandRequests();

        log.info("Synchronizing {} command(s) globally", requests.size());

        return gateway.getRestClient()
                .getApplicationId()
                .flatMap(appId -> gateway.getRestClient()
                        .getApplicationService()
                        .bulkOverwriteGlobalApplicationCommand(appId, requests)
                        .collectList()
                        .doOnNext(this::logCommandsSynchronized)
                        .onErrorResume(exception -> {
                            log.error("Failed to register global application slash commands", exception);
                            return Mono.empty();
                        }))
                .then();
    }

    private void logCommandsSynchronized(List<?> commands) {
        log.info("Successfully synchronized {} global application command(s)", commands.size());
    }
}
