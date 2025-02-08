package dev.marten_mrfcyt.tournament.menus

import dev.marten_mrfcyt.tournament.Tournament
import dev.marten_mrfcyt.tournament.tournaments.PlayerProgress
import dev.marten_mrfcyt.tournament.tournaments.TournamentManager
import dev.marten_mrfcyt.tournament.utils.asMini
import dev.marten_mrfcyt.tournament.utils.setCustomValue
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import org.bukkit.inventory.meta.ItemMeta
import java.time.Duration
import java.time.LocalDateTime

class TournamentListMenu(private val tournamentManager: TournamentManager) {

    fun open(player: Player) {
        val tournaments = tournamentManager.getTournaments().take(7)

        val inventory: Inventory = Bukkit.createInventory(null, 9 * 5, "Tournaments".asMini())

        val fillerItem = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val fillerMeta: ItemMeta = fillerItem.itemMeta
        fillerMeta.displayName(" ".asMini())
        setCustomValue(fillerMeta, Tournament.instance, "gui", "glasspane")
        fillerItem.itemMeta = fillerMeta
        for (i in 0 until inventory.size) {
            inventory.setItem(i, fillerItem)
        }
        for (i in 19 until 26) {
            inventory.setItem(i, null)
        }

        tournaments.forEachIndexed { index, tournament ->
            val item = ItemStack(Material.PAPER)
            val meta: ItemMeta = item.itemMeta
            meta.displayName("<b><gold>${tournament.name.uppercase()}</gold></b>".asMini())
            val topPlayers = tournamentManager.getTopPlayers(tournament.name)
            val topPlayersLore = topPlayers.keys.mapIndexed { index, player ->
                val color = when (index) {
                    0 -> "<#C9B037>"
                    1 -> "<#D7D7D7>"
                    2 -> "<#6A3805>"
                    else -> "<gray>"
                }
                "$color${index + 1}. <white>${player.name} - ${topPlayers[player]}P".asMini()
            }
            meta.lore(listOf(
                "<dark_gray>${tournament.description}".asMini(),
                "<dark_gray>Per ${tournament.target}.".asMini(),
                "".asMini(),
                "<b><gold>JOUW SCORE</gold></b>".asMini(),
                "<white>${PlayerProgress().getProgress(player.uniqueId.toString(), tournament.name)}".asMini(),
                "".asMini(),
                "<b><gold>TIJD RESTEREND</gold></b>".asMini(),
                "<white>${formatTimeRemaining(LocalDateTime.parse(tournament.endTime))}".asMini(),
                "".asMini()
            ) + topPlayersLore)
            setCustomValue(meta, Tournament.instance, "tournament", tournament.name)
            setCustomValue(meta, Tournament.instance, "gui", "tournamentlist")
            item.itemMeta = meta
            inventory.setItem(19 + index, item)
        }
        player.openInventory(inventory)
    }

    private fun formatTimeRemaining(endTime: LocalDateTime): String {
        val duration = Duration.between(LocalDateTime.now(), endTime)
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