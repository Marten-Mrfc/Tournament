package dev.marten_mrfcyt.tournament.menus

import dev.marten_mrfcyt.tournament.Tournament
import dev.marten_mrfcyt.tournament.tournaments.*
import dev.marten_mrfcyt.tournament.tournaments.models.ObjectiveType
import dev.marten_mrfcyt.tournament.tournaments.models.TournamentObjective
import dev.marten_mrfcyt.tournament.tournaments.models.TournamentTarget
import dev.marten_mrfcyt.tournament.utils.getKingdomMembers
import dev.marten_mrfcyt.tournament.utils.getKingdoms
import mlib.api.gui.GuiSize
import mlib.api.gui.types.PaginatedGui
import mlib.api.gui.types.builder.PaginatedGuiBuilder
import mlib.api.utilities.asMini
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.util.UUID
import java.util.logging.Logger

class TournamentMenu(private val tournamentManager: TournamentManager) {

    private val logger: Logger = Logger.getLogger(TournamentMenu::class.java.name)

    fun open(player: Player, tournamentName: String, page: Int = 0) {
        val tournament = tournamentManager.getTournaments().find { it.name == tournamentName }
        if (tournament == null) {
            logger.warning("Tournament $tournamentName not found")
            return
        }

        Bukkit.getScheduler().runTaskAsynchronously(Tournament.instance, Runnable {
            val kingdoms = getKingdoms()
            val playerData = PlayerProgress.getInstance().getAllProgressForTournament(tournamentName)

            val items = if (tournament.target == TournamentTarget.PROVINCE) {
                val kingdomProgress = mutableMapOf<String, Int>()
                playerData.forEach { (playerId, progress) ->
                    val kingdom = kingdoms.find { getKingdomMembers(it).contains(UUID.fromString(playerId)) }
                    if (kingdom != null) {
                        kingdomProgress[kingdom] = kingdomProgress.getOrDefault(kingdom, 0) + progress
                    }
                }

                kingdoms.map { kingdom ->
                    val progress = kingdomProgress[kingdom] ?: 0
                    PaginatedGui.PaginatedItem(
                        Material.PLAYER_HEAD,
                        "<reset><bold><white>$kingdom".asMini(),
                        listOf("<green>${getObjectiveDescription(tournament.objective, progress)}".asMini()),
                        1
                    ) to progress
                }.sortedByDescending { it.second }.map { it.first }
            } else {
                val meta = ItemStack(Material.PLAYER_HEAD).itemMeta as SkullMeta

                playerData.mapNotNull { (playerId, progress) ->
                    val offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(playerId))
                    meta.owningPlayer = offlinePlayer
                    PaginatedGui.PaginatedItem(
                        Material.PLAYER_HEAD,
                        "<reset><bold><white>${offlinePlayer.name ?: "Unknown"}".asMini(),
                        listOf("<green>${getObjectiveDescription(tournament.objective, progress)}".asMini()),
                        1,
                        meta
                    ) to progress
                }.sortedByDescending { it.second }.map { it.first }
            }

            Bukkit.getScheduler().runTask(Tournament.instance, Runnable {
                val gui = PaginatedGuiBuilder()
                    .title("Tournament: ${tournament.name}".asMini())
                    .size(GuiSize.ROW_FIVE)
                    .setBackground(Material.GRAY_STAINED_GLASS_PANE)
                    .onClick { event ->
                        event.isCancelled = true
                    }
                    .customizeGui { g ->
                        g.item(Material.ARROW) {
                            name("<green>Back to List".asMini())
                            slots(40)
                            onClick { e ->
                                e.isCancelled = true
                                TournamentListMenu(tournamentManager).open(player)
                            }
                        }
                    }
                    .build()

                gui.setItems(items)
                if (page > 0) {
                    for (i in 0 until page) {
                        gui.nextPage()
                    }
                }

                gui.open(player)
            })
        })
    }

    private fun getObjectiveDescription(objective: TournamentObjective, progress: Int): String {
        return when (objective.type) {
            ObjectiveType.MINE_BLOCK -> "Heeft $progress ${objective.getDisplayName()} gemined"
            ObjectiveType.KILL_ENTITY -> "Heeft $progress ${objective.getDisplayName()}'s vermoord"
        }
    }
}