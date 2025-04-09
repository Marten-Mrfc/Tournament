package dev.marten_mrfcyt.tournament.menus

import dev.marten_mrfcyt.tournament.tournaments.PlayerProgress
import dev.marten_mrfcyt.tournament.tournaments.TournamentManager
import dev.marten_mrfcyt.tournament.tournaments.models.TournamentTarget
import mlib.api.gui.GuiSize
import mlib.api.gui.types.builder.StandardGuiBuilder
import mlib.api.utilities.asMini
import org.bukkit.Material
import org.bukkit.entity.Player
import java.time.Duration
import java.time.LocalDateTime

class TournamentListMenu(private val tournamentManager: TournamentManager) {

    fun open(player: Player) {
        val tournaments = tournamentManager.getTournaments().take(7)

        val gui = StandardGuiBuilder()
            .title("Tournaments".asMini())
            .size(GuiSize.ROW_FIVE)
            .setup { standardGui ->
                standardGui.fill(Material.GRAY_STAINED_GLASS_PANE) {
                    name(" ".asMini())
                    onClick { event -> event.isCancelled = true }
                }

                standardGui.item(Material.GOLD_INGOT) {
                    name("<b><yellow>Rewards Menu</yellow></b>".asMini())
                    description(listOf(
                        "<dark_gray>Bekijk de rewards die je kan claimen".asMini(),
                        "<dark_gray>en claim ze als je in de top 5 staat.".asMini()
                    ))
                    slots(40)
                    onClick { event ->
                        event.isCancelled = true
                        RewardsMenu().open(player)
                    }
                }

                tournaments.forEachIndexed { index, tournament ->
                    val slotIndex = 19 + index
                    if (slotIndex <= 25) {
                        val objectiveType = tournament.objective.type.name
                        val objectiveTarget = tournament.objective.getDisplayName()

                        standardGui.item(getMaterialForObjective(tournament.objective.type.name)) {
                            name("<b><gold>${tournament.name.uppercase()}</gold></b>".asMini())

                            val topEntriesLore = if (tournament.target == TournamentTarget.PROVINCE) {
                                val topProvinces = tournamentManager.getTopProvincies(tournament.name)
                                topProvinces.entries
                                    .sortedByDescending { it.value }
                                    .take(5)
                                    .mapIndexed { i, entry ->
                                        val color = getRankColor(i)
                                        "$color${i + 1}. <white>${entry.key} - ${entry.value}P".asMini()
                                    }
                            } else {
                                val topPlayers = tournamentManager.getTopPlayers(tournament.name)
                                topPlayers.entries
                                    .sortedByDescending { it.value }
                                    .take(5)
                                    .mapIndexed { i, entry ->
                                        val color = getRankColor(i)
                                        "$color${i + 1}. <white>${entry.key.name} - ${entry.value}P".asMini()
                                    }
                            }

                            val playerScore = PlayerProgress.getInstance().getProgress(
                                player.uniqueId.toString(),
                                tournament.name
                            )

                            description(listOf(
                                "<dark_gray>${tournament.description}".asMini(),
                                "<dark_gray>Target: <white>${tournament.target}".asMini(),
                                "<dark_gray>Objective: <white>$objectiveType ($objectiveTarget)".asMini(),
                                if (tournament.levels != null) "<dark_gray>Levels: <white>${tournament.levels}".asMini() else "".asMini(),
                                "".asMini(),
                                "<b><gold>JOUW SCORE</gold></b>".asMini(),
                                "<white>$playerScore punten".asMini(),
                                "".asMini(),
                                "<b><gold>TIJD RESTEREND</gold></b>".asMini(),
                                "<white>${formatTimeRemaining(LocalDateTime.parse(tournament.endTime))}".asMini(),
                                "".asMini(),
                                "<b><gold>LEADERBOARD</gold></b>".asMini()
                            ) + topEntriesLore)

                            slots(slotIndex)

                            onClick { event ->
                                event.isCancelled = true
                                TournamentMenu(tournamentManager).open(player, tournament.name)
                            }
                        }
                    }
                }

                if (tournaments.isEmpty()) {
                    standardGui.item(Material.BARRIER) {
                        name("<b><red>Geen actieve toernooien</red></b>".asMini())
                        description(listOf(
                            "".asMini(),
                            "<gray>Er zijn momenteel geen actieve toernooien.".asMini(),
                            "<gray>Kom later terug!".asMini()
                        ))
                        slots(22)
                        onClick { event ->
                            event.isCancelled = true
                        }
                    }
                }
            }
            .build()

        gui.open(player)
    }

    private fun getMaterialForObjective(objectiveType: String): Material {
        return when (objectiveType) {
            "MINE_BLOCK" -> Material.DIAMOND_PICKAXE
            "KILL_ENTITY" -> Material.IRON_SWORD
            else -> Material.PAPER
        }
    }

    private fun getRankColor(rank: Int): String {
        return when (rank) {
            0 -> "<#C9B037>" // Gold
            1 -> "<#D7D7D7>" // Silver
            2 -> "<#6A3805>" // Bronze
            else -> "<gray>"
        }
    }

    private fun formatTimeRemaining(endTime: LocalDateTime): String {
        val duration = Duration.between(LocalDateTime.now(), endTime)
        if (duration.isNegative) return "Afgelopen"

        val weeks = duration.toDays() / 7
        val days = duration.toDays() % 7
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60

        return listOf(
            if (weeks > 0) "${weeks}w" else "",
            if (days > 0) "${days}d" else "",
            if (hours > 0) "${hours}h" else "",
            if (minutes > 0) "${minutes}m" else "",
            if (seconds > 0) "${seconds}s" else ""
        ).filter { it.isNotEmpty() }.joinToString(" ")
    }
}