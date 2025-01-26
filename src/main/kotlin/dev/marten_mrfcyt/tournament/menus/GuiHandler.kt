package dev.marten_mrfcyt.tournament.menus

import dev.marten_mrfcyt.tournament.Tournament
import dev.marten_mrfcyt.tournament.tournaments.TournamentManager
import dev.marten_mrfcyt.tournament.utils.checkCustomValue
import dev.marten_mrfcyt.tournament.utils.getCustomValue
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.Material

class GuiHandler(private val tournamentManager: TournamentManager) : Listener {
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val item = event.currentItem ?: return
        val itemMeta = item.itemMeta ?: return
        if (checkCustomValue(itemMeta, Tournament.instance, "gui", "tournamentlist")) {
            event.isCancelled = true
            val tournamentName = getCustomValue(itemMeta, Tournament.instance, "tournament") as? String ?: return
            TournamentMenu(tournamentManager).open(player, tournamentName)
        } else if (checkCustomValue(itemMeta, Tournament.instance, "gui", "tournament")) {
            event.isCancelled = true
            val tournamentName = getCustomValue(itemMeta, Tournament.instance, "tournament") as? String ?: return
            when (item.type) {
                Material.ARROW -> {
                    TournamentMenu(tournamentManager).open(player, tournamentName, getCustomValue(itemMeta, Tournament.instance, "page") as? Int ?: return)
                }
                else -> return
            }
        } else if (checkCustomValue(itemMeta, Tournament.instance, "gui", "rewards")) {
            event.isCancelled = true
            val tournamentName = getCustomValue(itemMeta, Tournament.instance, "tournament") as? String ?: return
            RewardsMenu().claimReward(player, tournamentName)
            RewardsMenu().open(player)
        } else if (checkCustomValue(itemMeta, Tournament.instance, "gui", "glasspane")) {
            event.isCancelled = true
        }
    }
}