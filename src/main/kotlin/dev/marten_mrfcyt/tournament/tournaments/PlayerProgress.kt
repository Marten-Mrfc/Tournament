package dev.marten_mrfcyt.tournament.tournaments

import dev.marten_mrfcyt.tournament.utils.*
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.set
import kotlin.concurrent.withLock

class PlayerProgress {
    private val file = File("plugins/Tournament/player_progress.yml")
    private val config = YamlConfiguration.loadConfiguration(file)
    private val playerProgress = ConcurrentHashMap<String, Int>()
    private val lock = ReentrantLock()

    init {
        loadProgressFromFile()
    }

    private fun loadProgressFromFile() {
        config.getKeys(false).forEach { key ->
            playerProgress[key] = config.getInt(key)
        }
    }

    fun getProgress(playerId: String, tournamentId: String): Int {
        return playerProgress.getOrDefault("$playerId-$tournamentId", 0)
    }

    fun updateProgress(playerId: String, tournamentId: String, progress: Int) {
        lock.withLock {
            val key = "$playerId-$tournamentId"
            playerProgress[key] = progress
            config.set(key, progress)
            config.save(file)
        }
    }

    fun saveProgressToFile() {
        lock.withLock {
            playerProgress.forEach { (key, progress) ->
                config.set(key, progress)
            }
            config.save(file)
        }
    }

    fun getAllProgressForTournament(tournamentId: String): Map<String, Int> {
        return playerProgress.filterKeys { it.endsWith("-$tournamentId") }
            .mapKeys { it.key.substringBeforeLast("-$tournamentId") }
    }

    fun calculateStandingsAsync(tournament: Tournament): CompletableFuture<List<Pair<UUID, Int>>> {
        return CompletableFuture.supplyAsync<List<Pair<UUID, Int>>> {
            val playerProgress = getAllProgressForTournament(tournament.name)
            val sortedPlayers = playerProgress.entries
                .sortedByDescending { it.value }
                .map { (playerId, progress) ->
                    UUID.fromString(playerId) to progress
                }
            sortedPlayers.take(5)
        }
    }

    fun giveRewards(tournamentName: String) {
        val tournamentManager = TournamentManager()
        val tournaments = tournamentManager.getTournaments()
        val tournament = tournaments.find { it.name.replace(" ", "_").lowercase() == tournamentName.replace(" ", "_").lowercase() }
            ?: run {
                println("Tournament not found | Contact Marten_mrfcyt")
                return
            }

        val config = YamlConfiguration.loadConfiguration(File("plugins/Tournament/tournaments.yml"))
        val inventoryItems = config.getList("tournaments.${tournament.name.replace(" ", "_").lowercase()}.inventory")?.filterIsInstance<ItemStack>()
            ?: run {
                println("Tournament inventory is null or empty | Contact Marten_mrfcyt")
                return
            }

        val rewardsConfig = YamlConfiguration.loadConfiguration(File("plugins/Tournament/playerrewards.yml"))

        calculateStandingsAsync(tournament).thenAccept { topPlayers ->
            val topPlayerIds = topPlayers.map { it.first }.toSet()
            val allPlayerProgress = getAllProgressForTournament(tournament.name)

            if (tournament.target == "provincie") {
                val kingdoms = getKingdoms()
                val kingdomMembers = kingdoms.flatMap { kingdom -> getKingdomMembers(kingdom).map { it to kingdom } }.toMap()

                topPlayers.forEach { (playerId, progress) ->
                    val kingdom = kingdomMembers[UUID.fromString(playerId.toString())]
                    if (kingdom != null) {
                        val rewardKey = "provincie.${kingdom}.${tournament.name.replace(" ", "_").lowercase()}"
                        rewardsConfig.set("$rewardKey.score", progress)
                        rewardsConfig.set("$rewardKey.position", topPlayers.indexOf(Pair(playerId, progress)))
                        rewardsConfig.set("$rewardKey.items", inventoryItems)
                        rewardsConfig.set("$rewardKey.levels", tournament.levels)
                        rewardsConfig.set("$rewardKey.type", "provincie")
                        rewardsConfig.save(File("plugins/Tournament/playerrewards.yml"))
                        Bukkit.getPlayer(playerId)?.message("Gefeliciteerd! Je provincie $kingdom heeft de ${tournament.name} gewonnen met $progress punten! Doe /tournament claimreward om je prijs te claimen.")
                    }
                }

                kingdomMembers.keys.filterNot { UUID.fromString(it.toString()) in topPlayerIds }.forEach { playerId ->
                    Bukkit.getPlayer(UUID.fromString(playerId.toString()))?.message("Helaas, je provincie heeft niet hoog genoeg gescoord in de ${tournament.name}. Probeer het volgende keer opnieuw!")
                }
            } else {
                topPlayers.forEach { (playerId, progress) ->
                    val rewardKey = "players.$playerId.${tournament.name.replace(" ", "_").lowercase()}"
                    rewardsConfig.set("$rewardKey.score", progress)
                    rewardsConfig.set("$rewardKey.position", topPlayers.indexOf(Pair(playerId, progress)))
                    rewardsConfig.set("$rewardKey.items", inventoryItems)
                    rewardsConfig.set("$rewardKey.levels", tournament.levels)
                    rewardsConfig.save(File("plugins/Tournament/playerrewards.yml"))
                    Bukkit.getPlayer(playerId)?.message("Gefeliciteerd! Je hebt de ${tournament.name} gewonnen met $progress punten! Doe /tournament claimreward om je prijs te claimen.")
                }

                allPlayerProgress.keys.filterNot { UUID.fromString(it) in topPlayerIds }.forEach { playerId ->
                    Bukkit.getPlayer(UUID.fromString(playerId))?.message("Helaas, je hebt niet hoog genoeg gescoord in de ${tournament.name}. Probeer het volgende keer opnieuw!")
                }
            }
        }
        removePlayersFromTournament(tournament.name)
    }

    fun removePlayersFromTournament(tournamentId: String) {
        val keysToRemove = playerProgress.keys.filter { it.endsWith("-$tournamentId") }
        println("Removing keys: $keysToRemove")
        keysToRemove.forEach { key ->
            playerProgress.remove(key)
            config.set(key, null)
        }
        config.save(file)
    }
}