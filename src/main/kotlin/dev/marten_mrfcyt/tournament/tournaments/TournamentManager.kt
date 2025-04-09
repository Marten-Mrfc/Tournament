// src/main/kotlin/dev/marten_mrfcyt/tournament/tournaments/TournamentManager.kt
package dev.marten_mrfcyt.tournament.tournaments

import dev.marten_mrfcyt.tournament.tournaments.models.Tournament
import dev.marten_mrfcyt.tournament.tournaments.models.TournamentObjective
import dev.marten_mrfcyt.tournament.tournaments.models.TournamentTarget
import dev.marten_mrfcyt.tournament.utils.getKingdomMembers
import dev.marten_mrfcyt.tournament.utils.getKingdoms
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.Inventory
import java.io.File
import java.util.UUID

class TournamentManager {
    private val logger = dev.marten_mrfcyt.tournament.Tournament.instance.logger

    private fun loadConfig(): YamlConfiguration {
        val file = File("plugins/Tournament/tournaments.yml")
        return YamlConfiguration.loadConfiguration(file)
    }

    fun saveTournament(tournament: Tournament) {
        val config = loadConfig()
        val tournamentId = tournament.name.replace(" ", "_").lowercase()
        config.set("tournaments.$tournamentId.name", tournament.name)
        config.set("tournaments.$tournamentId.description", tournament.description)
        config.set("tournaments.$tournamentId.objective", TournamentObjective.toString(tournament.objective))
        config.set("tournaments.$tournamentId.target", TournamentTarget.toString(tournament.target))
        config.set("tournaments.$tournamentId.levels", tournament.levels)
        config.set("tournaments.$tournamentId.endTime", tournament.endTime)
        if (tournament.inventory != null) {
            val filledSlots = tournament.inventory.contents.filterNotNull().filter { it.type != Material.AIR }
            config.set("tournaments.$tournamentId.inventory", filledSlots)
        }
        config.save(File("plugins/Tournament/tournaments.yml"))
    }

    fun removeTournament(tournamentId: String) {
        val config = loadConfig()
        config.set("tournaments.$tournamentId", null)
        config.save(File("plugins/Tournament/tournaments.yml"))
    }

    fun getTournamentsNames(): List<String> {
        val config = loadConfig()
        val list = config.getConfigurationSection("tournaments")?.getKeys(false)?.toList()
        return list ?: listOf()
    }

    fun getTournaments(): List<Tournament> {
        val config = loadConfig()
        val tournaments = mutableListOf<Tournament>()
        val section = config.getConfigurationSection("tournaments") ?: return tournaments

        for (key in section.getKeys(false)) {
            try {
                val name = config.getString("tournaments.$key.name") ?: continue
                val description = config.getString("tournaments.$key.description") ?: continue
                val endTime = config.getString("tournaments.$key.endTime") ?: continue
                val objectiveString = config.getString("tournaments.$key.objective") ?: continue
                val targetString = config.getString("tournaments.$key.target") ?: continue
                val inventory = config.get("tournaments.$key.inventory") as? Inventory
                val levels = config.getInt("tournaments.$key.levels")

                val objective = TournamentObjective.fromString(objectiveString)
                val target = TournamentTarget.fromString(targetString)

                tournaments.add(
                    Tournament(
                        name,
                        description,
                        endTime,
                        objective,
                        target,
                        inventory,
                        levels
                    )
                )
            } catch (e: Exception) {
                logger.warning("Failed to load tournament $key: ${e.message}")
            }
        }

        return tournaments
    }

    fun getTopPlayers(tournamentName: String): HashMap<OfflinePlayer, Int> {
        val playerProgress = PlayerProgress.getInstance().getAllProgressForTournament(tournamentName)
        val sortedPlayers = playerProgress.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { (playerId, progress) ->
                val player = Bukkit.getOfflinePlayer(UUID.fromString(playerId))
                player to progress
            }
        return hashMapOf(*sortedPlayers.toTypedArray())
    }

    fun getTopProvincies(tournamentName: String): HashMap<String, Int> {
        val playerProgress = PlayerProgress.getInstance().getAllProgressForTournament(tournamentName)
        val sortedPlayers = playerProgress.entries
            .sortedByDescending { it.value }
            .take(5)
            .mapNotNull { (playerId, progress) ->
                val province = getKingdoms().find { getKingdomMembers(it).contains(UUID.fromString(playerId)) }
                if (province != null) province to progress else null
            }
        return hashMapOf(*sortedPlayers.toTypedArray())
    }

    fun getActiveTournamentsByObjective(objectiveString: String): List<Tournament> {
        val tournaments = getTournaments()
        return tournaments.filter { tournament ->
            val tournamentObjectiveString = TournamentObjective.toString(tournament.objective)
            tournamentObjectiveString == objectiveString
        }
    }
}