package dev.asyncluna.zenith.discord.feature.overwatch.player;

import dev.asyncluna.zenith.core.integration.overfastapi.OverfastApiService;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.PlayerCompetitiveRank;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.PlayerSummary;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.RoleRank;
import dev.asyncluna.zenith.discord.command.BotCommand;
import dev.asyncluna.zenith.discord.command.Command;
import dev.asyncluna.zenith.discord.command.CommandContext;
import dev.asyncluna.zenith.discord.command.CommandOption;
import dev.asyncluna.zenith.discord.util.EmbedUtils;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.spec.EmbedCreateSpec;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.WordUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Command(name = "player_summary", description = "Get summary and competitive ranks for an Overwatch player.")
@CommandOption(
        name = "player_id",
        description = "The BattleTag (e.g LUNAÃ‡Æ’#2788).",
        type = ApplicationCommandOption.Type.STRING,
        required = true)
public class PlayerSummaryCommand implements BotCommand {
    private final OverfastApiService overfastApiService;

    @Override
    public Mono<?> handle(CommandContext ctx) {
        String playerId = ctx.getOptionAsString("player_id").orElseThrow();

        return overfastApiService
                .getPlayerSummary(playerId)
                .flatMap(playerSummary -> {
                    EmbedCreateSpec embed = createPlayerSummaryEmbed(playerSummary, ctx);
                    return ctx.editReply().withEmbeds(embed);
                })
                .onErrorResume(throwable -> ctx.editReply()
                        .withEmbeds(EmbedCreateSpec.builder()
                                .title(ctx.localize("error.command_execution_failed_title"))
                                .description(ctx.localize("player.no_results"))
                                .color(EmbedUtils.ERROR_COLOR)
                                .build()));
    }

    private EmbedCreateSpec createPlayerSummaryEmbed(PlayerSummary playerSummary, CommandContext ctx) {
        String title = playerSummary.username();
        if (playerSummary.title() != null && !playerSummary.title().isBlank())
            title += " Ã¢â‚¬â€ " + playerSummary.title();

        EmbedCreateSpec.Builder embedBuilder =
                EmbedCreateSpec.builder().title(title).color(EmbedUtils.DEFAULT_COLOR);

        if (playerSummary.avatar() != null && !playerSummary.avatar().isBlank())
            embedBuilder.thumbnail(playerSummary.avatar());

        if (playerSummary.namecard() != null && !playerSummary.namecard().isBlank())
            embedBuilder.image(playerSummary.namecard());

        StringBuilder descriptionBuilder = new StringBuilder();

        if (playerSummary.lastUpdatedAt() != null) {
            descriptionBuilder
                    .append("Ã°Å¸â€¢â€™ ")
                    .append(ctx.localize("player.last_updated"))
                    .append(" <t:")
                    .append(playerSummary.lastUpdatedAt())
                    .append(":R>");
        }

        if (playerSummary.endorsement() != null && playerSummary.endorsement().level() != null) {
            if (!descriptionBuilder.isEmpty()) descriptionBuilder.append("\n\n");
            descriptionBuilder
                    .append("Ã°Å¸Ââ€  ")
                    .append(ctx.localize("player.endorsement_level"))
                    .append(" **")
                    .append(playerSummary.endorsement().level())
                    .append("**");
        }

        if (!descriptionBuilder.isEmpty()) {
            embedBuilder.description(descriptionBuilder.toString());
        }

        if (playerSummary.competitive() != null) {
            if (playerSummary.competitive().pc() != null) {
                String pcRanks = formatRankContainer(playerSummary.competitive().pc(), ctx);
                embedBuilder.addField(
                        "<:bullet:1516494932142198894> " + ctx.localize("player.platform.pc"), pcRanks, false);
            }
            if (playerSummary.competitive().console() != null) {
                String consoleRanks =
                        formatRankContainer(playerSummary.competitive().console(), ctx);
                embedBuilder.addField(
                        "<:bullet:1516494932142198894> " + ctx.localize("player.platform.console"),
                        consoleRanks,
                        false);
            }
        }

        embedBuilder.footer(ctx.localize("player.disclaimer.regional_assets"), null);

        return embedBuilder.build();
    }

    private String formatRankContainer(PlayerCompetitiveRank rank, CommandContext ctx) {
        StringBuilder rankContainerBuilder = new StringBuilder();

        appendRoleRank(rankContainerBuilder, "tank", ctx.localize("competitive.role.tank"), rank.tank());
        appendRoleRank(rankContainerBuilder, "damage", ctx.localize("competitive.role.damage"), rank.damage());
        appendRoleRank(rankContainerBuilder, "support", ctx.localize("competitive.role.support"), rank.support());
        appendRoleRank(rankContainerBuilder, "open", ctx.localize("competitive.role.open"), rank.open());

        if (rankContainerBuilder.isEmpty()) {
            return "\u200E\u2002Ã¢â€â€> " + ctx.localize("player.rank.unranked");
        }

        return rankContainerBuilder.toString();
    }

    private void appendRoleRank(StringBuilder builder, String roleKey, String roleLabel, RoleRank roleRank) {
        if (roleRank != null
                && roleRank.division() != null
                && !roleRank.division().isBlank()) {
            if (!builder.isEmpty()) builder.append("\n");

            builder.append("\u200E\u2002Ã¢â€â€> ")
                    .append(getRoleEmoji(roleKey))
                    .append(" ")
                    .append(getRankEmoji(roleRank.division()))
                    .append(" ")
                    .append(roleLabel)
                    .append(": **")
                    .append(WordUtils.capitalizeFully(roleRank.division()))
                    .append(" ")
                    .append(roleRank.tier())
                    .append("**");
        }
    }

    private String getRoleEmoji(String role) {
        return switch (role.toLowerCase()) {
            case "tank" -> "<:tank:1516490952926298212>";
            case "damage" -> "<:damage:1516490951789645955>";
            case "support" -> "<:support:1516490950719963326>";
            case "open" -> "<:open_queue:1516489924667248732>";
            default -> "Ã¢Ââ€œ";
        };
    }

    private String getRankEmoji(String division) {
        return switch (division.toLowerCase()) {
            case "bronze" -> "<:bronze:1516488822093906023>";
            case "silver" -> "<:silver:1516488823595339977>";
            case "gold" -> "<:gold:1516488824908288202>";
            case "platinum" -> "<:platinum:1516488826011521137>";
            case "diamond" -> "<:diamond:1516488827265618154>";
            case "master" -> "<:master:1516488828708323368>";
            case "grandmaster" -> "<:grandmaster:1516488829782196244>";
            case "champion" -> "<:champion:1516488830956339380>";
            case "top500" -> "<:top500:1516488832210567390>";
            default -> "Ã¢â€“Â«Ã¯Â¸Â";
        };
    }
}
