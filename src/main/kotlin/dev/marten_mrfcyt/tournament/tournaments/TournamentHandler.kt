package dev.marten_mrfcyt.tournament.tournaments

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDeathEvent

class TournamentHandler(private val tournamentManager: TournamentManager) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onBlockBreak(event: BlockBreakEvent) {
        if(event.isCancelled) return
        val player = event.player
        val block = event.block
        val tournaments = tournamentManager.getActiveTournamentsByObjective("mineblock:${block.type}")
        tournaments.forEach { tournament ->
            updatePlayerProgress(player, tournament)
        }
    }

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        val entity = event.entity
        val killer = entity.killer
        if (killer is Player) {
            println("Killed entity: ${entity.type}, killer: ${killer.name}")
            val tournaments = tournamentManager.getActiveTournamentsByObjective("killentity:${entity.type}")
            tournaments.forEach { tournament ->
                updatePlayerProgress(killer, tournament)
            }
        }
    }

    fun updatePlayerProgress(player: Player, tournament: Tournament) {
        val playerId = player.uniqueId.toString()
        val tournamentId = tournament.name
        val progress = PlayerProgress().getProgress(playerId, tournamentId) + 1
        PlayerProgress().updateProgress(playerId, tournamentId, progress)
    }
}