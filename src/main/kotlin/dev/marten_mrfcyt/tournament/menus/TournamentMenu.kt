package dev.marten_mrfcyt.tournament.menus

import com.destroystokyo.paper.profile.ProfileProperty
import com.gufli.kingdomcraft.api.domain.Kingdom
import dev.marten_mrfcyt.tournament.Tournament
import dev.marten_mrfcyt.tournament.tournaments.*
import dev.marten_mrfcyt.tournament.tournaments.models.ObjectiveType
import dev.marten_mrfcyt.tournament.tournaments.models.TournamentObjective
import dev.marten_mrfcyt.tournament.tournaments.models.TournamentTarget
import dev.marten_mrfcyt.tournament.utils.getKingdoms
import mlib.api.gui.GuiSize
import mlib.api.gui.types.PaginatedGui
import mlib.api.gui.types.builder.PaginatedGuiBuilder
import mlib.api.utilities.asMini
import mlib.api.utilities.setCustomValue
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.OfflinePlayer
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
                val kingdomProgress = mutableMapOf<Kingdom, Int>()
                playerData.forEach { (playerId, progress) ->
                    val kingdom = kingdoms.find { it.members.contains(UUID.fromString(playerId)) }
                    if (kingdom != null) {
                        kingdomProgress[kingdom] = kingdomProgress.getOrDefault(kingdom, 0) + progress
                    }
                }

                kingdoms.map { kingdom ->
                    val progress = kingdomProgress[kingdom] ?: 0
                    PaginatedGui.PaginatedItem(
                        Material.PLAYER_HEAD,
                        "<reset><bold><white>${kingdom.name}".asMini(),
                        listOf("<green>${getObjectiveDescription(tournament.objective, progress)}".asMini()),
                        1
                    ) to progress
                }.sortedByDescending { it.second }.map { it.first }
            } else {                // Create temporary items with default heads first
                playerData.mapNotNull { (playerId, progress) ->
                    val offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(playerId))
                    // Use a placeholder head initially
                    val placeholderHead = ItemStack(Material.PLAYER_HEAD)
                    val meta = placeholderHead.itemMeta as SkullMeta
                    meta.displayName("<reset><bold><white>${offlinePlayer.name ?: "Unknown Player"}".asMini())
                    meta.lore(listOf(getObjectiveDescription(tournament.objective, progress).asMini()))
                    placeholderHead.itemMeta = meta
                    
                    PaginatedGui.PaginatedItem(
                        placeholderHead.type,
                        meta.displayName() ?: "".asMini(),
                        meta.lore() ?: listOf(),
                        1
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
                            name("<green>Terug naar lijst".asMini())
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
                
                // Load player heads with 10 tick delays
                if (tournament.target == TournamentTarget.PLAYER) {
                    Bukkit.getScheduler().runTaskLater(Tournament.instance, Runnable {
                        // Only update if the player still has the GUI open
                        val currentInventory = player.openInventory.topInventory

                        playerData.forEach { (playerId, progress) ->
                            val offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(playerId))
                            val playerHead = createPlayerHead(offlinePlayer.uniqueId, getObjectiveDescription(tournament.objective, progress))

                            // Check all items in the current inventory
                            for (i in 0 until currentInventory.size) {
                                val item = currentInventory.getItem(i) ?: continue

                                // Skip non-player heads
                                if (item.type != Material.PLAYER_HEAD) continue

                                // Check if this is the right player's placeholder
                                val meta = item.itemMeta
                                if (meta != null && meta.hasDisplayName()) {
                                    val displayName = meta.displayName().toString()
                                    if (displayName.contains(offlinePlayer.name ?: "Unknown Player")) {
                                        // Update the player head
                                        currentInventory.setItem(i, playerHead)
                                    }
                                }
                            }
                        }
                    }, 1L) // 10 tick delay
                }
            })
        })
    }      
    private fun createPlayerHead(uuid: UUID, description: String?): ItemStack {
        val head = ItemStack(Material.PLAYER_HEAD)
        val meta = head.itemMeta as SkullMeta
        
        val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
        
        // Use Paper's API directly with the player's UUID
        meta.owningPlayer = offlinePlayer
        
        if (description != null) {
            meta.displayName("<reset><bold><white>${offlinePlayer.name ?: "Unknown Player"}".asMini())
            meta.lore(listOf(description.asMini()))
        }
        
        head.itemMeta = meta
        return head
    }

    private fun getObjectiveDescription(objective: TournamentObjective, progress: Int): String {
        return when (objective.type) {
            ObjectiveType.HAK_BLOK -> "Heeft $progress ${objective.getDisplayName()} gemined"
            ObjectiveType.DOOD_MOB -> "Heeft $progress ${objective.getDisplayName()}'s vermoord"
        }
    }
}