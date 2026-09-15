package dev.asyncluna.zenith.discord.feature.overwatch.herostats;

import dev.asyncluna.zenith.core.i18n.I18nManager;
import dev.asyncluna.zenith.core.i18n.SupportedLocale;
import dev.asyncluna.zenith.core.integration.overfastapi.OverfastApiService;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.HeroShort;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.HeroStatsSummary;
import dev.asyncluna.zenith.discord.feature.overwatch.herostats.HeroStatsCommand.HeroStatsSession;
import dev.asyncluna.zenith.discord.listener.EventListener;
import dev.asyncluna.zenith.discord.settings.GuildSettingsProvider;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class HeroStatsSelectListener implements EventListener<SelectMenuInteractionEvent> {
    private static final String HERO_STATS_MENU_PREFIX = "hs-";

    private final OverfastApiService overfastApiService;
    private final GuildSettingsProvider guildSettingsProvider;
    private final I18nManager i18nManager;
    private final HeroStatsCommand heroStatsCommand;
    private final HeroStatsSelectionRenderer renderer;

    @Override
    public Mono<Void> execute(SelectMenuInteractionEvent event) {
        return parseSelection(event)
                .map(selection -> loadLocale(event).flatMap(locale -> handleSelection(event, selection, locale)))
                .orElseGet(Mono::empty);
    }

    private Mono<Locale> loadLocale(SelectMenuInteractionEvent event) {
        String guildId =
                event.getInteraction().getGuildId().map(Snowflake::asString).orElse("");
        return guildSettingsProvider.get(guildId).map(settings -> SupportedLocale.forLanguageTag(settings.getLocale())
                .getLocale());
    }

    private Mono<Void> handleSelection(SelectMenuInteractionEvent event, HeroSelection selection, Locale locale) {
        HeroStatsSession session = heroStatsCommand.getSessionCache().getIfPresent(selection.sessionId());
        if (session == null) {
            return replyWithError(event, "error.menu_interaction_expired", locale, "hero_stats");
        }
        if (!ownsSession(event, session)) {
            return replyWithError(event, "error.menu_not_owned", locale);
        }
        if (event.getValues().isEmpty()) {
            return Mono.empty();
        }

        String selectedHero = event.getValues().getFirst();
        session.setSelectedHeroKey(selectedHero.toLowerCase(Locale.ROOT));

        return loadSelectedHero(session, selectedHero)
                .flatMap(result -> updateMessage(event, selection.sessionId(), session, result, locale))
                .then();
    }

    private Mono<SelectedHero> loadSelectedHero(HeroStatsSession session, String selectedHero) {
        Mono<Map<String, HeroShort>> heroes = overfastApiService
                .getHeroes(null, null, null)
                .collect(Collectors.toMap(hero -> hero.key().toLowerCase(Locale.ROOT), hero -> hero));
        Mono<HeroStatsSummary> stats = overfastApiService
                .getHeroStats(
                        session.platform(),
                        session.gamemode(),
                        session.region(),
                        session.role(),
                        session.map(),
                        session.competitiveDivision(),
                        session.orderBy())
                .filter(hero -> hero.hero().equalsIgnoreCase(selectedHero))
                .next();

        return Mono.zip(stats, heroes)
                .map(tuple -> new SelectedHero(
                        tuple.getT1(), tuple.getT2().get(tuple.getT1().hero().toLowerCase(Locale.ROOT))));
    }

    private Mono<?> updateMessage(
            SelectMenuInteractionEvent event,
            String sessionId,
            HeroStatsSession session,
            SelectedHero selectedHero,
            Locale locale) {
        return event.edit()
                .withEmbeds(renderer.renderEmbed(session, selectedHero.stats(), selectedHero.details(), locale))
                .withComponents(event.getMessage()
                        .map(message -> renderer.replaceDetailsButton(message, sessionId, locale))
                        .orElseGet(java.util.List::of));
    }

    private boolean ownsSession(SelectMenuInteractionEvent event, HeroStatsSession session) {
        return session.userId().equals(event.getInteraction().getUser().getId().asString());
    }

    private Mono<Void> replyWithError(
            SelectMenuInteractionEvent event, String key, Locale locale, Object... arguments) {
        return event.reply().withEphemeral(true).withContent(i18nManager.localize(key, locale, arguments));
    }

    private Optional<HeroSelection> parseSelection(SelectMenuInteractionEvent event) {
        String[] parts = event.getCustomId().split(":", 2);
        if (parts.length != 2 || !isHeroStatsMenu(parts[0]) || parts[1].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new HeroSelection(parts[1]));
    }

    private boolean isHeroStatsMenu(String prefix) {
        return prefix.equals(HERO_STATS_MENU_PREFIX + "1") || prefix.equals(HERO_STATS_MENU_PREFIX + "2");
    }

    private record HeroSelection(String sessionId) {}

    private record SelectedHero(HeroStatsSummary stats, HeroShort details) {}
}
