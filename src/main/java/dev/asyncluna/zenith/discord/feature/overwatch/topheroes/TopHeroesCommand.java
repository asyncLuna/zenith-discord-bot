package dev.asyncluna.zenith.discord.feature.overwatch.topheroes;

import dev.asyncluna.zenith.core.integration.overfastapi.OverfastApiService;
import dev.asyncluna.zenith.discord.command.BotCommand;
import dev.asyncluna.zenith.discord.command.Command;
import dev.asyncluna.zenith.discord.command.CommandContext;
import dev.asyncluna.zenith.discord.command.CommandOption;
import dev.asyncluna.zenith.discord.util.DiscordConstants;
import dev.asyncluna.zenith.discord.util.EmbedUtils;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
@Command(name = "top_heroes", description = "See a player's most played Overwatch heroes.")
@CommandOption(
        name = "player_id",
        description = "The BattleTag (e.g LUNAÃ‡Æ’#2788).",
        type = ApplicationCommandOption.Type.STRING,
        required = true)
@CommandOption(
        name = "gamemode",
        description = "Quick Play or Competitive Play.",
        type = ApplicationCommandOption.Type.STRING,
        autocomplete = true,
        required = true)
@CommandOption(
        name = "stat",
        description = "The career stat used to rank heroes.",
        type = ApplicationCommandOption.Type.STRING,
        autocomplete = true)
public class TopHeroesCommand implements BotCommand {
    private static final List<Stat> STATS = List.of(
            new Stat("game.time_played", "Time played", true),
            new Stat("game.games_won", "Games won", false),
            new Stat("game.games_played", "Games played", false),
            new Stat("game.win_percentage", "Win percentage", false),
            new Stat("best.weapon_accuracy_best_in_game", "Weapon accuracy - best in game", false),
            new Stat("best.eliminations_most_in_game", "Eliminations - most in game", false),
            new Stat("best.deaths_most_in_game", "Deaths - most in game", false),
            new Stat("best.final_blows_most_in_game", "Final blows - most in game", false),
            new Stat("best.solo_kills_most_in_game", "Solo kills - most in game", false),
            new Stat("best.objective_kills_most_in_game", "Objective kills - most in game", false),
            new Stat("best.objective_time_most_in_game", "Objective time - most in game", true),
            new Stat("best.hero_damage_done_most_in_game", "Hero damage done - most in game", false),
            new Stat("best.healing_done_most_in_game", "Healing done - most in game", false),
            new Stat("best.all_damage_done_most_in_game", "All damage done - most in game", false),
            new Stat("best.defensive_assists_most_in_game", "Defensive assists - most in game", false),
            new Stat("best.offensive_assists_most_in_game", "Offensive assists - most in game", false),
            new Stat("best.environmental_kills_most_in_game", "Environmental kills - most in game", false),
            new Stat("best.melee_final_blows_most_in_game", "Melee final blows - most in game", false),
            new Stat("best.time_spent_on_fire_most_in_game", "Time spent on fire - most in game", true),
            new Stat("best.kill_streak_best", "Kill streak - best", false),
            new Stat("best.multikill_best", "Multikill - best", false),
            new Stat("average.eliminations_avg_per_10_min", "Eliminations - avg per 10 min", false),
            new Stat("average.deaths_avg_per_10_min", "Deaths - avg per 10 min", false),
            new Stat("average.final_blows_avg_per_10_min", "Final blows - avg per 10 min", false),
            new Stat("average.solo_kills_avg_per_10_min", "Solo kills - avg per 10 min", false),
            new Stat("average.objective_kills_avg_per_10_min", "Objective kills - avg per 10 min", false),
            new Stat("average.objective_time_avg_per_10_min", "Objective time - avg per 10 min", true),
            new Stat("average.hero_damage_done_avg_per_10_min", "Hero damage done - avg per 10 min", false),
            new Stat("average.healing_done_avg_per_10_min", "Healing done - avg per 10 min", false),
            new Stat("average.assists_avg_per_10_min", "Assists - avg per 10 min", false),
            new Stat("average.time_spent_on_fire_avg_per_10_min", "Time spent on fire - avg per 10 min", true),
            new Stat("average.objective_contest_time_avg_per_10_min", "Objective contest time - avg per 10 min", true),
            new Stat("combat.eliminations", "Eliminations", false),
            new Stat("combat.deaths", "Deaths", false),
            new Stat("combat.final_blows", "Final blows", false),
            new Stat("combat.solo_kills", "Solo kills", false),
            new Stat("combat.objective_kills", "Objective kills", false),
            new Stat("combat.hero_damage_done", "Hero damage done", false),
            new Stat("combat.healing_done", "Healing done", false),
            new Stat("combat.environmental_kills", "Environmental kills", false),
            new Stat("combat.melee_final_blows", "Melee final blows", false),
            new Stat("combat.time_spent_on_fire", "Time spent on fire", true),
            new Stat("combat.objective_time", "Objective time", true));

    private final OverfastApiService overfastApiService;
    private final TopHeroesSessionManager sessionManager;
    private final TopHeroesRenderer renderer;
    private final TopHeroesComponentFactory componentFactory;

    @Override
    public Mono<?> handle(CommandContext ctx) {
        String playerId = ctx.getOptionAsString("player_id").orElseThrow();
        String mode = ctx.getOptionAsString("gamemode").orElse("quickplay").toLowerCase(Locale.ROOT);
        Stat stat = findStat(ctx.getOptionAsString("stat").orElse("game.time_played"));

        return overfastApiService
                .getPlayerCareerStats(playerId, mode)
                .map(stats -> {
                    String sessionId = UUID.randomUUID().toString();
                    TopHeroesSession session = new TopHeroesSession(
                            ctx.getAuthor().getId().asString(), playerId, mode, stat.path, stats, new int[1]);
                    sessionManager.put(sessionId, session);
                    return new SessionView(sessionId, session);
                })
                .flatMap(view -> renderer.totalPages(view.session()) > 1
                        ? ctx.editReply()
                                .withEmbeds(renderer.render(view.session(), ctx.getLocale()))
                                .withComponents(componentFactory.createPageButtons(
                                        view.sessionId(), view.session(), ctx.getLocale()))
                        : ctx.editReply().withEmbeds(renderer.render(view.session(), ctx.getLocale())))
                .onErrorResume(exception -> {
                    log.error("Failed to build top heroes response for playerId={}", playerId, exception);
                    return ctx.editReply()
                            .withEmbeds(EmbedCreateSpec.builder()
                                    .title(ctx.localize("top_heroes.error.title"))
                                    .description(ctx.localize("top_heroes.error.description"))
                                    .color(EmbedUtils.ERROR_COLOR)
                                    .build());
                });
    }

    private record SessionView(String sessionId, TopHeroesSession session) {}

    static Stat findStat(String path) {
        return STATS.stream()
                .filter(stat -> stat.path.equalsIgnoreCase(path))
                .findFirst()
                .orElse(STATS.getFirst());
    }

    @Override
    public Mono<Void> autocomplete(ChatInputAutoCompleteEvent event) {
        var focusedOption = event.getFocusedOption();
        String name = focusedOption.getName();
        String input = focusedOption
                .getValue()
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElse("")
                .toLowerCase(Locale.ROOT);
        List<ApplicationCommandOptionChoiceData> choices =
                switch (name) {
                    case "gamemode" -> List.of(
                            choice("Quick Play", "quickplay"), choice("Competitive Play", "competitive"));
                    case "stat" -> STATS.stream()
                            .filter(stat -> stat.label.toLowerCase(Locale.ROOT).contains(input))
                            .limit(DiscordConstants.MAX_AUTO_COMPLETE_RESULTS)
                            .map(stat -> choice(stat.label, stat.path))
                            .toList();
                    default -> List.of();
                };
        return event.respondWithSuggestions(choices).then();
    }

    private static ApplicationCommandOptionChoiceData choice(String name, String value) {
        return ApplicationCommandOptionChoiceData.builder()
                .name(name)
                .value(value)
                .build();
    }

    record Stat(String path, String label, boolean duration) {}
}
