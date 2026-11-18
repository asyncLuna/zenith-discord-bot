package dev.asyncluna.zenith.discord.feature.uwulock;

import dev.asyncluna.zenith.core.model.UwuLock;
import dev.asyncluna.zenith.core.service.UwuLockService;
import dev.asyncluna.zenith.discord.command.BotCommand;
import dev.asyncluna.zenith.discord.command.Command;
import dev.asyncluna.zenith.discord.command.CommandContext;
import dev.asyncluna.zenith.discord.command.CommandException;
import dev.asyncluna.zenith.discord.command.CommandOption;
import dev.asyncluna.zenith.discord.command.SubCommand;
import discord4j.common.util.Snowflake;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import discord4j.rest.util.Permission;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Command(name = "uwulock", description = "Manage user uwu blocks.")
@CommandOption(
        name = "apply",
        description = "Lock a user into talking only in uwu speak.",
        type = ApplicationCommandOption.Type.SUB_COMMAND,
        subCommands = {
            @SubCommand(
                    name = "user",
                    description = "The target member to apply the uwu lock to.",
                    type = ApplicationCommandOption.Type.USER,
                    required = true)
        })
@CommandOption(
        name = "remove",
        description = "Release a user from the shackles of uwu speak.",
        type = ApplicationCommandOption.Type.SUB_COMMAND,
        subCommands = {
            @SubCommand(
                    name = "user",
                    description = "The target member to release from uwu lock.",
                    type = ApplicationCommandOption.Type.USER,
                    required = true)
        })
@CommandOption(
        name = "list",
        description = "List all users currently caught in an uwu lock.",
        type = ApplicationCommandOption.Type.SUB_COMMAND)
public class UwuLockCommand implements BotCommand {
    private static final Color EMBED_COLOR = Color.of(0x36393f);
    private static final Snowflake MEMBER_OF_THE_WEEK_ROLE = Snowflake.of("1572567577346056202");

    private final UwuLockService uwuLockService;

    @Override
    public Mono<?> handle(CommandContext ctx) {
        return hasAdminOrMemberOfTheWeekRole(ctx).flatMap(authorized -> {
            if (!authorized) {
                return Mono.error(new CommandException(ctx.localize("uwulock.error.unauthorized")));
            }

            boolean hasApply = ctx.getEvent().getOption("apply").isPresent();
            boolean hasRemove = ctx.getEvent().getOption("remove").isPresent();
            boolean hasList = ctx.getEvent().getOption("list").isPresent();

            if (hasApply) return handleApply(ctx);
            if (hasRemove) return handleRemove(ctx);
            if (hasList) return handleList(ctx);

            return Mono.error(new CommandException(ctx.localize("uwulock.error.generic")));
        });
    }

    private Mono<Boolean> hasAdminOrMemberOfTheWeekRole(CommandContext ctx) {
        return ctx.getEvent()
                .getInteraction()
                .getMember()
                .map(member -> member.getBasePermissions()
                        .map(permissions -> permissions.contains(Permission.ADMINISTRATOR)
                                || member.getRoleIds().contains(MEMBER_OF_THE_WEEK_ROLE))
                        .defaultIfEmpty(member.getRoleIds().contains(MEMBER_OF_THE_WEEK_ROLE)))
                .orElse(Mono.just(false));
    }

    private Mono<?> handleApply(CommandContext ctx) {
        return ctx.getOptionAsUser("user")
                .orElseGet(() -> Mono.error(new CommandException(ctx.localize("uwulock.error.invalid_user"))))
                .flatMap(user -> uwuLockService
                        .lockUserAsync(user.getId().asString())
                        .flatMap(success -> {
                            if (!success) {
                                return Mono.error(new CommandException(
                                        ctx.localize("uwulock.apply.already_locked", user.getMention())));
                            }
                            String description = ctx.localize("uwulock.apply.success", user.getMention());
                            return ctx.editReply()
                                    .withEmbeds(EmbedCreateSpec.builder()
                                            .color(EMBED_COLOR)
                                            .description(description)
                                            .build());
                        }))
                .onErrorResume(exception -> {
                    if (exception instanceof CommandException) return Mono.error(exception);
                    return Mono.error(new CommandException(ctx.localize("uwulock.error.generic")));
                });
    }

    private Mono<?> handleRemove(CommandContext ctx) {
        return ctx.getOptionAsUser("user")
                .orElseGet(() -> Mono.error(new CommandException(ctx.localize("uwulock.error.invalid_user"))))
                .flatMap(user -> uwuLockService
                        .unlockUserAsync(user.getId().asString())
                        .flatMap(success -> {
                            if (!success) {
                                return Mono.error(new CommandException(
                                        ctx.localize("uwulock.remove.not_locked", user.getMention())));
                            }
                            String description = ctx.localize("uwulock.remove.success", user.getMention());
                            return ctx.editReply()
                                    .withEmbeds(EmbedCreateSpec.builder()
                                            .color(EMBED_COLOR)
                                            .description(description)
                                            .build());
                        }))
                .onErrorResume(exception -> {
                    if (exception instanceof CommandException) return Mono.error(exception);
                    return Mono.error(new CommandException(ctx.localize("uwulock.error.generic")));
                });
    }

    private Mono<?> handleList(CommandContext ctx) {
        AtomicInteger counter = new AtomicInteger(1);
        return uwuLockService
                .getLockedUsers()
                .map(lock -> formatLock(lock, counter.getAndIncrement(), ctx.localize("uwulock.list.unknown_time")))
                .collect(Collectors.joining("\n"))
                .flatMap(mentionsList -> {
                    if (mentionsList.isEmpty()) {
                        return ctx.editReply()
                                .withEmbeds(EmbedCreateSpec.builder()
                                        .color(EMBED_COLOR)
                                        .description(ctx.localize("uwulock.list.empty"))
                                        .build());
                    }
                    return ctx.editReply()
                            .withEmbeds(EmbedCreateSpec.builder()
                                    .color(EMBED_COLOR)
                                    .title(ctx.localize("uwulock.list.title"))
                                    .description(mentionsList)
                                    .build());
                })
                .onErrorResume(exception -> Mono.error(new CommandException(ctx.localize("uwulock.error.generic"))));
    }

    static String formatLock(UwuLock lock, int number, String unknownTime) {
        String timestamp = lock.createdAt() == null
                ? unknownTime
                : "<t:" + lock.createdAt().getEpochSecond() + ":R>";
        return "`#" + number + ".` - <@" + lock.discordId() + "> - " + timestamp;
    }
}
