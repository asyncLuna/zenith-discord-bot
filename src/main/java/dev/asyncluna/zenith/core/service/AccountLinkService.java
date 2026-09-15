package dev.asyncluna.zenith.core.service;

import dev.asyncluna.zenith.core.exception.AccountAlreadyLinkedException;
import dev.asyncluna.zenith.core.model.AccountLink;
import dev.asyncluna.zenith.core.repository.AccountLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AccountLinkService {
    private final AccountLinkRepository repository;

    public Mono<AccountLinkResult> linkAsync(String discordId, String battleTag) {
        return repository
                .findByBattleTag(battleTag)
                .flatMap(existingLink -> resolveExistingLink(existingLink, discordId))
                .switchIfEmpty(Mono.defer(() -> linkForDiscordUser(discordId, battleTag)));
    }

    private Mono<AccountLinkResult> resolveExistingLink(AccountLink link, String discordId) {
        if (!discordId.equals(link.getDiscordId())) {
            return Mono.error(new AccountAlreadyLinkedException());
        }
        return Mono.just(new AccountLinkResult(link, false));
    }

    private Mono<AccountLinkResult> linkForDiscordUser(String discordId, String battleTag) {
        return repository
                .findById(discordId)
                .flatMap(existingLink -> updateLink(existingLink, battleTag))
                .switchIfEmpty(Mono.defer(() -> createLink(discordId, battleTag)));
    }

    private Mono<AccountLinkResult> updateLink(AccountLink link, String battleTag) {
        link.setBattleTag(battleTag);
        return repository.save(link).map(savedLink -> new AccountLinkResult(savedLink, true));
    }

    private Mono<AccountLinkResult> createLink(String discordId, String battleTag) {
        AccountLink link =
                AccountLink.builder().discordId(discordId).battleTag(battleTag).build();
        return repository.save(link).map(savedLink -> new AccountLinkResult(savedLink, true));
    }
}
