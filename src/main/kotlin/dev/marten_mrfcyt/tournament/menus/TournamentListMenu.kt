package dev.marten_mrfcyt.tournament.menus

import dev.marten_mrfcyt.tournament.Tournament
import dev.marten_mrfcyt.tournament.tournaments.PlayerProgress
import dev.marten_mrfcyt.tournament.tournaments.TournamentManager
import dev.marten_mrfcyt.tournament.tournaments.models.ObjectiveType
import dev.marten_mrfcyt.tournament.tournaments.models.TournamentTarget
import mlib.api.gui.GuiSize
import mlib.api.gui.types.builder.StandardGuiBuilder
import mlib.api.utilities.asMini
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import java.time.Duration
import java.time.LocalDateTime

class TournamentListMenu(private val tournamentManager: TournamentManager) {
    fun open(player: Player) {
        // Build the GUI with placeholders first
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

                // Add placeholder items for tournaments
                for (index in 0 until 7) {
                    val slotIndex = 19 + index
                    standardGui.item(Material.BOOK) {
                        name("<b><gold>Loading...</gold></b>".asMini())
                        description(listOf(
                            "<gray>Tournament data is loading...".asMini()
                        ))
                        slots(slotIndex)
                        onClick { event -> event.isCancelled = true }
                    }
                }
            }
            .build()

        gui.open(player)
        
        // Load tournament data with a 10 tick delay
        Bukkit.getScheduler().runTaskLater(Tournament.instance, Runnable {
            // Check if player still has GUI open
            val currentInventory = player.openInventory.topInventory
            val tournaments = tournamentManager.getTournaments().take(7)

            if (tournaments.isEmpty()) {
                // Replace with "No tournaments" message
                currentInventory.setItem(22, createNoTournamentsItem())

                // Clear placeholder items if any
                for (index in 0 until 7) {
                    val slotIndex = 19 + index
                    if (slotIndex != 22) {
                        currentInventory.setItem(slotIndex, null)
                    }
                }
            } else {                    // Update with actual tournament data
                tournaments.forEachIndexed { index, tournament ->
                    val slotIndex = 19 + index
                    if (slotIndex <= 25) {
                        val tournamentItem = createTournamentItem(player, tournament, tournamentManager)
                        currentInventory.setItem(slotIndex, tournamentItem)
                    }
                }

                // Clear any remaining placeholder items
                for (index in tournaments.size until 7) {
                    val slotIndex = 19 + index
                    currentInventory.setItem(slotIndex, null)
                }

                // Set up a click listener for the tournament items
                Bukkit.getPluginManager().registerEvents(object : org.bukkit.event.Listener {
                    @org.bukkit.event.EventHandler
                    fun onInventoryClick(event: org.bukkit.event.inventory.InventoryClickEvent) {
                        if (event.whoClicked != player) return
                        if (event.inventory != currentInventory) return

                        val slot = event.rawSlot
                        if (slot >= 19 && slot <= 25) {
                            event.isCancelled = true

                            val tournamentIndex = slot - 19
                            if (tournamentIndex < tournaments.size) {
                                val tournament = tournaments[tournamentIndex]

                                // Close this menu and open the tournament details
                                player.closeInventory()
                                Bukkit.getScheduler().runTaskLater(Tournament.instance, Runnable {
                                    TournamentMenu(tournamentManager).open(player, tournament.name)
                                }, 1L)
                            }
                        }
                    }
                }, Tournament.instance)}
        }, 10L) // 10 tick delay
    }
    
    private fun createNoTournamentsItem(): org.bukkit.inventory.ItemStack {
        val item = org.bukkit.inventory.ItemStack(Material.BARRIER)
        val meta = item.itemMeta
        meta.displayName("<b><red>Geen actieve toernooien</red></b>".asMini())
        meta.lore(listOf(
            "".asMini(),
            "<gray>Er zijn momenteel geen actieve toernooien.".asMini(),
            "<gray>Kom later terug!".asMini()
        ))
        item.itemMeta = meta
        return item
    }
    
    private fun createTournamentItem(player: Player, tournament: dev.marten_mrfcyt.tournament.tournaments.models.Tournament, tournamentManager: TournamentManager): org.bukkit.inventory.ItemStack {
        val objectiveTarget = tournament.objective.getDisplayName()
        val material = getMaterialForObjective(tournament.objective.type)
        
        val item = org.bukkit.inventory.ItemStack(material)
        val meta = item.itemMeta
        
        meta.displayName("<b><gold>${tournament.name.uppercase()}</gold></b>".asMini())
        
        val topEntriesLore = if (tournament.target == TournamentTarget.PROVINCE) {
            val topProvinces = tournamentManager.getTopProvincies(tournament.name)
            topProvinces.entries
                .sortedByDescending { it.value }
                .take(5)
                .mapIndexed { i, entry ->
                    val color = getRankColor(i)
                    "$color${i + 1}. <white>${entry.key.name} - ${entry.value}P".asMini()
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

        meta.lore(listOf(
            "<dark_gray>${tournament.description}".asMini(),
            "<dark_gray>Soort: <white>${TournamentTarget.toString(tournament.target).lowercase().replaceFirstChar { it.uppercase() }}".asMini(),
            "<dark_gray>${ObjectiveType.toString(tournament.objective.type)}: <white>$objectiveTarget".asMini(),
            "".asMini(),
            "<b><gold>JOUW SCORE</gold></b>".asMini(),
            "<white>$playerScore punten".asMini(),
            "".asMini(),
            "<b><gold>TIJD RESTEREND</gold></b>".asMini(),
            "<white>${formatTimeRemaining(LocalDateTime.parse(tournament.endTime))}".asMini(),
            "".asMini(),
            "<b><gold>LEADERBOARD</gold></b>".asMini()
        ) + topEntriesLore)
        
        item.itemMeta = meta
        
        // Set click handler with NMS or via event later
        return item
    }    
    private fun getMaterialForObjective(objectiveType: ObjectiveType): Material {
        return when (objectiveType) {
            ObjectiveType.HAK_BLOK -> Material.DIAMOND_PICKAXE
            ObjectiveType.DOOD_MOB -> Material.IRON_SWORD
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