package dev.asyncluna.zenith.discord.feature.overwatch.herostats;

import dev.asyncluna.zenith.core.i18n.I18nManager;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.HeroShort;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.HeroStatsSummary;
import dev.asyncluna.zenith.discord.feature.overwatch.herostats.HeroStatsCommand.HeroStatsSession;
import dev.asyncluna.zenith.discord.util.EmbedUtils;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.LayoutComponent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.WordUtils;
import org.springframework.stereotype.Component;

/** Builds the message view shown after a hero is selected. */
@Component
@RequiredArgsConstructor
public class HeroStatsSelectionRenderer {
    private final I18nManager i18nManager;

    public EmbedCreateSpec renderEmbed(
            HeroStatsSession session, HeroStatsSummary stats, HeroShort hero, Locale locale) {
        String heroName = hero != null ? hero.name() : stats.hero();
        EmbedCreateSpec.Builder embed =
                EmbedCreateSpec.builder().title(heroName).color(EmbedUtils.DEFAULT_COLOR);

        if (hero != null && hero.role() != null && !hero.role().isBlank()) {
            addField(embed, "hero.role", WordUtils.capitalizeFully(hero.role()), locale);
        }

        addField(embed, "hero.platform", session.platform().getFriendlyName(), locale);
        addField(embed, "hero.gamemode", WordUtils.capitalizeFully(session.gamemode()), locale);
        addField(embed, "hero.region", session.region().getFriendlyName(), locale);

        addOptionalField(embed, "hero.map", session.map(), locale, true);
        if (session.competitiveDivision() != null) {
            addField(
                    embed,
                    "hero.competitive_division",
                    session.competitiveDivision().getFriendlyName(),
                    locale);
        }
        if (session.orderBy() != null) {
            addField(embed, "hero.order_by", session.orderBy().getFriendlyName(), locale);
        }

        addField(embed, "hero.pickrate", percentage(stats.pickrate()), locale);
        addField(embed, "hero.winrate", percentage(stats.winrate()), locale);
        addField(embed, "hero.banrate", percentage(stats.banrate()), locale);

        if (hero != null && hero.portrait() != null) {
            embed.thumbnail(hero.portrait());
        }
        return embed.build();
    }

    public List<LayoutComponent> replaceDetailsButton(Message message, String sessionId, Locale locale) {
        Button detailsButton = Button.primary("hs-btn:" + sessionId, i18nManager.localize("hero.view_details", locale));
        List<LayoutComponent> components = new ArrayList<>();

        message.getComponents().forEach(component -> {
            if (component instanceof ActionRow actionRow) {
                boolean containsButton = actionRow.getChildren().stream().anyMatch(child -> child instanceof Button);
                components.add(containsButton ? ActionRow.of(detailsButton) : actionRow);
            }
        });
        return components;
    }

    private void addOptionalField(
            EmbedCreateSpec.Builder embed, String key, String value, Locale locale, boolean capitalize) {
        if (value == null) {
            return;
        }
        addField(embed, key, capitalize ? WordUtils.capitalizeFully(value) : value, locale);
    }

    private void addField(EmbedCreateSpec.Builder embed, String key, String value, Locale locale) {
        embed.addField(i18nManager.localize(key, locale), value, true);
    }

    private String percentage(Double value) {
        return value == null ? "N/A" : value + "%";
    }
}
