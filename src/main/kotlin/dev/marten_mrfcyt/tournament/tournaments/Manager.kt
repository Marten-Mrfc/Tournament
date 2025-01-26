package dev.marten_mrfcyt.tournament.tournaments

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.collections.set

data class Tournament(
    val name: String,
    val description: String,
    val endTime: String,
    val objective: String,
    val target: String,
    val inventory: Inventory? = null,
    val levels: Int? = null
)

class TournamentManager {

    private fun loadConfig(): YamlConfiguration {
        val file = File("plugins/Tournament/tournaments.yml")
        return YamlConfiguration.loadConfiguration(file)
    }

    fun saveTournament(tournament: Tournament) {
        val config = loadConfig()
        val tournamentId = tournament.name.replace(" ", "_").lowercase()
        config.set("tournaments.$tournamentId.name", tournament.name)
        config.set("tournaments.$tournamentId.description", tournament.description)
        config.set("tournaments.$tournamentId.objective", tournament.objective)
        config.set("tournaments.$tournamentId.target", tournament.target)
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
            val name = config.getString("tournaments.$key.name") ?: continue
            val description = config.getString("tournaments.$key.description") ?: continue
            val type = config.getString("tournaments.$key.endTime") ?: continue
            val objective = config.getString("tournaments.$key.objective") ?: continue
            val target = config.getString("tournaments.$key.target") ?: continue
            val inventory = config.get("tournaments.$key.inventory") as? Inventory
            val levels = config.getInt("tournaments.$key.levels")
            tournaments.add(Tournament(name, description, type, objective, target, inventory, levels))
        }
        return tournaments
    }
    fun getTopPlayers(tournamentName: String): HashMap<Player, Int> {
        val playerProgress = PlayerProgress().getAllProgressForTournament(tournamentName)
        val sortedPlayers = playerProgress.entries
            .sortedByDescending { it.value }
            .mapNotNull { (playerId, progress) ->
                val player = Bukkit.getPlayer(UUID.fromString(playerId))
                if (player != null) player to progress else null
            }
        return hashMapOf(*sortedPlayers.toTypedArray())
    }
    fun getActiveTournamentsByObjective(objective: String): List<Tournament> {
        val config = loadConfig()
        val tournaments = mutableListOf<Tournament>()
        val section = config.getConfigurationSection("tournaments") ?: return tournaments
        for (key in section.getKeys(false)) {
            val obj = config.getString("tournaments.$key.objective")
            if (obj == null) {
                println("Objective for tournament $key is null")
                continue
            }
            if (obj == objective) {
                val name = config.getString("tournaments.$key.name")
                if (name == null) {
                    println("Name for tournament $key is null")
                    continue
                }
                val description = config.getString("tournaments.$key.description")
                if (description == null) {
                    println("Description for tournament $key is null")
                    continue
                }
                val type = config.getString("tournaments.$key.endTime")
                if (type == null) {
                    println("End time for tournament $key is null")
                    continue
                }
                val target = config.getString("tournaments.$key.target")
                if (target == null) {
                    println("Target for tournament $key is null")
                    continue
                }
                val levels = config.getInt("tournaments.$key.levels")
                val inventory = config.get("tournaments.$key.inventory") as? Inventory
                if (inventory == null) {
                    println("Inventory for tournament $key is null")
                }
                tournaments.add(Tournament(name, description, type, obj, target, inventory, levels))
            }
        }
        return tournaments
    }
}