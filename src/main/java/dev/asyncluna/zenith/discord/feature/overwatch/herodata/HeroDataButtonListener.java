package dev.asyncluna.zenith.discord.feature.overwatch.herodata;

import dev.asyncluna.zenith.core.i18n.I18nManager;
import dev.asyncluna.zenith.core.i18n.SupportedLocale;
import dev.asyncluna.zenith.core.integration.overfastapi.OverfastApiService;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.Ability;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.Hero;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.Perk;
import dev.asyncluna.zenith.discord.feature.overwatch.herodata.HeroDataCommand.HeroDataSession;
import dev.asyncluna.zenith.discord.listener.EventListener;
import dev.asyncluna.zenith.discord.settings.GuildSettingsProvider;
import dev.asyncluna.zenith.discord.util.EmbedUtils;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.spec.EmbedCreateSpec;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class HeroDataButtonListener implements EventListener<ButtonInteractionEvent> {
    private final OverfastApiService overfastApiService;
    private final GuildSettingsProvider guildSettingsProvider;
    private final I18nManager i18nManager;
    private final HeroDataCommand heroDataCommand;

    @Override
    public Mono<Void> execute(ButtonInteractionEvent event) {
        String fullCustomId = event.getCustomId();

        String[] parts = fullCustomId.split(":", 2);
        String btnPrefix = parts[0];

        if (!"hd-abilities".equals(btnPrefix) && !"hd-perks".equals(btnPrefix) && !"hd-back".equals(btnPrefix))
            return Mono.empty();
        if (parts.length < 2) return Mono.empty();

        String sessionId = parts[1];
        HeroDataSession session = heroDataCommand.getSessionCache().getIfPresent(sessionId);
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
                                .withContent(i18nManager.localize(
                                        "error.menu_interaction_expired", currentLocale, "hero_data"));
                    }

                    String interactionUserId =
                            event.getInteraction().getUser().getId().asString();
                    if (!session.userId().equals(interactionUserId)) {
                        String localizedError = i18nManager.localize("error.menu_not_owned", currentLocale);
                        return event.reply().withEphemeral(true).withContent(localizedError);
                    }

                    return overfastApiService
                            .getHeroData(session.heroKey(), session.locale())
                            .flatMap(heroData -> {
                                if ("hd-back".equals(btnPrefix)) {
                                    EmbedCreateSpec dataEmbed = heroDataCommand.createHeroDataEmbed(
                                            heroData, session.locale(), settings, i18nManager);

                                    String viewAbilitiesLabel =
                                            i18nManager.localize("hero.view_abilities", currentLocale);
                                    Button abilitiesButton =
                                            Button.primary("hd-abilities:" + sessionId, viewAbilitiesLabel);

                                    String viewPerksLabel = i18nManager.localize("hero.view_perks", currentLocale);
                                    Button perksButton = Button.secondary("hd-perks:" + sessionId, viewPerksLabel);

                                    return event.edit()
                                            .withEmbeds(dataEmbed)
                                            .withComponents(ActionRow.of(abilitiesButton, perksButton));
                                }

                                if ("hd-abilities".equals(btnPrefix)) {
                                    EmbedCreateSpec abilitiesEmbed = createHeroAbilitiesEmbed(heroData, currentLocale);

                                    String goBackLabel = i18nManager.localize("hero.go_back", currentLocale);
                                    Button backButton = Button.danger("hd-back:" + sessionId, goBackLabel);

                                    return event.edit()
                                            .withEmbeds(abilitiesEmbed)
                                            .withComponents(ActionRow.of(backButton));
                                }

                                EmbedCreateSpec perksEmbed = createHeroPerksEmbed(heroData, currentLocale);

                                String goBackLabel = i18nManager.localize("hero.go_back", currentLocale);
                                Button backButton = Button.danger("hd-back:" + sessionId, goBackLabel);

                                return event.edit().withEmbeds(perksEmbed).withComponents(ActionRow.of(backButton));
                            });
                })
                .then();
    }

    private EmbedCreateSpec createHeroAbilitiesEmbed(Hero heroData, Locale currentLocale) {
        String abilitiesTitleTemplate = i18nManager.localize("hero.abilities_title", currentLocale);
        String embedTitle = String.format(abilitiesTitleTemplate, heroData.name());

        EmbedCreateSpec.Builder embedBuilder =
                EmbedCreateSpec.builder().title(embedTitle).color(EmbedUtils.DEFAULT_COLOR);

        if (heroData.portrait() != null && !heroData.portrait().isBlank()) embedBuilder.thumbnail(heroData.portrait());

        if (heroData.abilities() != null && !heroData.abilities().isEmpty()) {
            for (Ability ability : heroData.abilities()) {
                String name = ability.name() != null
                        ? ability.name()
                        : i18nManager.localize("hero.unknown_ability", currentLocale);
                String description = ability.description() != null ? ability.description() : "";
                embedBuilder.addField(name, description, false);
            }
        } else {
            String noAbilitiesMessage = i18nManager.localize("hero.no_abilities_found", currentLocale);
            embedBuilder.description(noAbilitiesMessage);
        }

        return embedBuilder.build();
    }

    private EmbedCreateSpec createHeroPerksEmbed(Hero heroData, Locale currentLocale) {
        String perksTitleTemplate = i18nManager.localize("hero.perks_title", currentLocale);
        String embedTitle = String.format(perksTitleTemplate, heroData.name());

        EmbedCreateSpec.Builder embedBuilder =
                EmbedCreateSpec.builder().title(embedTitle).color(EmbedUtils.DEFAULT_COLOR);

        if (heroData.portrait() != null && !heroData.portrait().isBlank()) embedBuilder.thumbnail(heroData.portrait());

        boolean hasPerks = false;

        if (heroData.perks() != null) {
            if (heroData.perks().major() != null && !heroData.perks().major().isEmpty()) {
                hasPerks = true;
                String majorHeader = i18nManager.localize("hero.perks.major", currentLocale);
                embedBuilder.addField("Ã°Å¸â€Â¹ " + majorHeader, "-------------------------", false);
                for (Perk perk : heroData.perks().major()) {
                    embedBuilder.addField(perk.name(), perk.description(), false);
                }
            }

            if (heroData.perks().minor() != null && !heroData.perks().minor().isEmpty()) {
                hasPerks = true;
                String minorHeader = i18nManager.localize("hero.perks.minor", currentLocale);
                embedBuilder.addField("Ã°Å¸â€Â¸ " + minorHeader, "-------------------------", false);
                for (Perk perk : heroData.perks().minor()) {
                    embedBuilder.addField(perk.name(), perk.description(), false);
                }
            }
        }

        if (!hasPerks) {
            String noPerksMessage = i18nManager.localize("hero.no_perks_found", currentLocale);
            embedBuilder.description(noPerksMessage);
        }

        return embedBuilder.build();
    }
}
