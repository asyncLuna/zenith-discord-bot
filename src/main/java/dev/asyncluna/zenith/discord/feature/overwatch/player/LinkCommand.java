package dev.asyncluna.zenith.discord.feature.overwatch.player;

import dev.asyncluna.zenith.core.exception.AccountAlreadyLinkedException;
import dev.asyncluna.zenith.core.service.AccountLinkService;
import dev.asyncluna.zenith.discord.command.BotCommand;
import dev.asyncluna.zenith.discord.command.Command;
import dev.asyncluna.zenith.discord.command.CommandContext;
import dev.asyncluna.zenith.discord.command.CommandOption;
import discord4j.core.object.command.ApplicationCommandOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Command(name = "link", description = "Link your Overwatch account with your Discord account.", ephemeral = true)
@CommandOption(
        name = "battle_tag",
        description = "Case-sensitive BattleTag (e.g. LUNAÃ‡Æ’#2788)",
        type = ApplicationCommandOption.Type.STRING,
        required = true)
@RequiredArgsConstructor
@Slf4j
public class LinkCommand implements BotCommand {
    private final AccountLinkService accountLinkService;

    @Override
    public Mono<?> handle(CommandContext ctx) {
        String discordId = ctx.getAuthor().getId().asString();
        String battleTag = ctx.getOptionAsString("battle_tag").orElse("");
        String battleTagDashed = battleTag.replace("#", "-");

        if (battleTagDashed.isBlank())
            return ctx.editReply(ctx.localize("error.battle_tag_not_specified")).then();

        return accountLinkService
                .linkAsync(discordId, battleTagDashed)
                .flatMap(result -> {
                    if (result.isNew()) {
                        return ctx.editReply(ctx.localize("link.success", battleTag));
                    } else {
                        return ctx.editReply(ctx.localize("link.already_linked_self"));
                    }
                })
                .onErrorResume(
                        AccountAlreadyLinkedException.class,
                        __ -> ctx.editReply(ctx.localize("link.already_linked_other")))
                .onErrorResume(exception -> {
                    log.error(
                            "Error while linking account for Discord ID '{}' and BattleTag '{}'",
                            discordId,
                            battleTag,
                            exception);
                    return ctx.editReply(ctx.localize("link.failed", battleTag));
                });
    }
}
