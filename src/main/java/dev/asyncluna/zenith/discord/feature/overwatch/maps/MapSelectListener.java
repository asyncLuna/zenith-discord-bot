package dev.asyncluna.zenith.discord.feature.overwatch.maps;

import dev.asyncluna.zenith.core.i18n.I18nManager;
import dev.asyncluna.zenith.core.i18n.SupportedLocale;
import dev.asyncluna.zenith.core.integration.overfastapi.OverfastApiService;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.Map;
import dev.asyncluna.zenith.discord.feature.overwatch.maps.MapCommand.MapSession;
import dev.asyncluna.zenith.discord.listener.EventListener;
import dev.asyncluna.zenith.discord.settings.GuildSettingsProvider;
import dev.asyncluna.zenith.discord.util.EmbedUtils;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.spec.EmbedCreateSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class MapSelectListener implements EventListener<SelectMenuInteractionEvent> {
    private final OverfastApiService overfastApiService;
    private final GuildSettingsProvider guildSettingsProvider;
    private final I18nManager i18nManager;
    private final MapCommand mapCommand;

    @Override
    public Mono<Void> execute(SelectMenuInteractionEvent event) {
        String fullCustomId = event.getCustomId();

        String[] parts = fullCustomId.split(":", 2);
        String menuPrefix = parts[0];

        if (!"map-1".equals(menuPrefix) && !"map-2".equals(menuPrefix)) return Mono.empty();
        if (parts.length < 2) return Mono.empty();

        String sessionId = parts[1];

        MapSession session = mapCommand.getSessionCache().getIfPresent(sessionId);
        String guildIdStr =
                event.getInteraction().getGuildId().map(Snowflake::asString).orElse("");

        return guildSettingsProvider
                .get(guildIdStr)
                .flatMap(settings -> {
                    Locale currentLocale =
                            SupportedLocale.forLanguageTag(settings.getLocale()).getLocale();

                    if (session == null) {
                        return event.reply()
                                .withEphemeral(true)
                                .withContent(
                                        i18nManager.localize("error.menu_interaction_expired", currentLocale, "map"));
                    }

                    String interactionUserId =
                            event.getInteraction().getUser().getId().asString();
                    if (!session.userId().equals(interactionUserId)) {
                        String localizedError = i18nManager.localize("error.menu_not_owned", currentLocale);
                        return event.reply().withEphemeral(true).withContent(localizedError);
                    }

                    String selectedMapKey = event.getValues().getFirst();
                    session.setSelectedMapKey(selectedMapKey.toLowerCase());

                    return overfastApiService
                            .getMaps(session.gamemode())
                            .filter(map -> map.key().equalsIgnoreCase(selectedMapKey))
                            .next()
                            .flatMap(map -> {
                                EmbedCreateSpec embed = createMapEmbed(map, currentLocale);

                                List<ActionRow> layoutComponents = new ArrayList<>();
                                event.getMessage().ifPresent(message -> message.getComponents()
                                        .forEach(row -> {
                                            if (row instanceof ActionRow actionRow) {
                                                layoutComponents.add(actionRow);
                                            }
                                        }));

                                return event.edit().withEmbeds(embed).withComponents(layoutComponents);
                            });
                })
                .then();
    }

    private EmbedCreateSpec createMapEmbed(Map map, Locale currentLocale) {
        EmbedCreateSpec.Builder embedBuilder =
                EmbedCreateSpec.builder().title(map.name()).color(EmbedUtils.DEFAULT_COLOR);

        if (!StringUtils.isBlank(map.location()))
            embedBuilder.addField(i18nManager.localize("map.location", currentLocale), map.location(), true);

        if (!StringUtils.isBlank(map.countryCode()))
            embedBuilder.addField(
                    i18nManager.localize("map.country_code", currentLocale),
                    map.countryCode().toUpperCase(),
                    true);

        if (map.gamemodes() != null && !map.gamemodes().isEmpty()) {
            String modesList = map.gamemodes().stream()
                    .map(mode -> WordUtils.capitalizeFully(mode.replace("-", " ")))
                    .collect(Collectors.joining(", "));
            embedBuilder.addField(i18nManager.localize("map.gamemodes", currentLocale), modesList, false);
        }

        if (!StringUtils.isBlank(map.screenshot())) embedBuilder.image(map.screenshot());

        return embedBuilder.build();
    }
}
