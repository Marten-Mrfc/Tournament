package dev.marten_mrfcyt.tournament.menus

import dev.marten_mrfcyt.tournament.Tournament
import dev.marten_mrfcyt.tournament.tournaments.PlayerProgress
import dev.marten_mrfcyt.tournament.tournaments.TournamentManager
import dev.marten_mrfcyt.tournament.utils.asMini
import dev.marten_mrfcyt.tournament.utils.getKingdomMembers
import dev.marten_mrfcyt.tournament.utils.getKingdoms
import dev.marten_mrfcyt.tournament.utils.setCustomValue
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import org.bukkit.inventory.meta.SkullMeta
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

class TournamentMenu(private val tournamentManager: TournamentManager) {

    private val logger: Logger = Logger.getLogger(TournamentMenu::class.java.name)
    private val itemsPerPage = 36

    fun open(player: Player, tournamentName: String, page: Int = 0) {
        val tournament = tournamentManager.getTournaments().find { it.name == tournamentName }
        if (tournament == null) {
            logger.warning("Tournament $tournamentName not found")
            return
        }
        val inventory: Inventory = Bukkit.createInventory(null, 9 * 5, "Tournament: ${tournament.name}".asMini())
        Bukkit.getScheduler().runTask(Tournament.instance, Runnable {
            player.openInventory(inventory)
        })
        val kingdoms = getKingdoms()
        CompletableFuture.runAsync {
            val playerData = PlayerProgress.getInstance().getAllProgressForTournament(tournamentName)
            val items = if (tournament.target == "provincie") {
                val kingdomProgress = mutableMapOf<String, Int>()
                playerData.forEach { (playerId, progress) ->
                    val kingdom = kingdoms.find { getKingdomMembers(it).contains(UUID.fromString(playerId)) }
                    if (kingdom != null) {
                        kingdomProgress[kingdom] = kingdomProgress.getOrDefault(kingdom, 0) + progress
                    }
                }

                kingdoms.map { kingdom ->
                    val progress = kingdomProgress[kingdom] ?: 0
                    createKingdomHead(kingdom, getObjectiveDescription(tournament.objective, progress)) to progress
                }.sortedWith(compareByDescending<Pair<ItemStack, Int>> { it.second }.thenBy { it.first.itemMeta?.displayName })
            } else {
                playerData.map { (playerId, progress) ->
                    val offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(playerId))
                    createPlayerHead(offlinePlayer, getObjectiveDescription(tournament.objective, progress)) to progress
                }.sortedWith(compareByDescending<Pair<ItemStack, Int>> { it.second }.thenBy { it.first.itemMeta?.displayName })
            }

            val paginatedItems = items.drop(page * itemsPerPage).take(itemsPerPage)

            Bukkit.getScheduler().runTask(Tournament.instance, Runnable {
                paginatedItems.forEach { (item) ->
                    inventory.addItem(item)
                }
                addNavigationItems(inventory, page, (items.size + itemsPerPage - 1) / itemsPerPage - 1, tournamentName)
            })
        }
    }

    private fun addNavigationItems(inventory: Inventory, page: Int, totalPages: Int, tournament: String) {
        if (page > 0) {
            val previousPageItem = ItemStack(Material.ARROW)
            val meta = previousPageItem.itemMeta
            meta.displayName("<reset><bold><white>Previous Page".asMini())
            setCustomValue(meta, Tournament.instance, "page", page - 1)
            setCustomValue(meta, Tournament.instance, "gui", "tournament")
            setCustomValue(meta, Tournament.instance, "tournament", tournament)
            previousPageItem.itemMeta = meta
            inventory.setItem(36, previousPageItem)
        }

        if (page < totalPages) {
            val nextPageItem = ItemStack(Material.ARROW)
            val meta = nextPageItem.itemMeta
            meta.displayName("<reset><bold><white>Next Page".asMini())
            setCustomValue(meta, Tournament.instance, "page", page + 1)
            setCustomValue(meta, Tournament.instance, "gui", "tournament")
            setCustomValue(meta, Tournament.instance, "tournament", tournament)
            nextPageItem.itemMeta = meta
            inventory.setItem(44, nextPageItem)
        }
    }

    private fun createPlayerHead(offlinePlayer: OfflinePlayer, value: String): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta: SkullMeta = item.itemMeta as SkullMeta
        meta.displayName("<reset><bold><white>${offlinePlayer.name}".asMini())
        meta.lore(listOf("<green>$value".asMini()))
        meta.owningPlayer = offlinePlayer
        setCustomValue(meta, Tournament.instance, "gui", "tournament")
        item.itemMeta = meta
        return item
    }

    private fun createKingdomHead(kingdom: String, value: String): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta: SkullMeta = item.itemMeta as SkullMeta
        meta.displayName("<reset><bold><white>$kingdom".asMini())
        meta.lore(listOf("<green>$value".asMini()))
        setCustomValue(meta, Tournament.instance, "gui", "tournament")
        item.itemMeta = meta
        return item
    }

    private fun getObjectiveDescription(objective: String, progress: Int): String {
        return when {
            objective.startsWith("mineblock:") -> "Heeft $progress ${objective.removePrefix("mineblock:").lowercase()} gemined"
            objective.startsWith("killentity:") -> "Heeft $progress ${objective.removePrefix("killentity:").lowercase()}'s vermoord"
            else -> "Progress: $progress"
        }
    }
}