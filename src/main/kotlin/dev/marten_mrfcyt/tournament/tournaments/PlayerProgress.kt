package dev.marten_mrfcyt.tournament.tournaments

import dev.marten_mrfcyt.tournament.rewards.RewardsManager
import dev.marten_mrfcyt.tournament.tournaments.models.Tournament
import dev.marten_mrfcyt.tournament.tournaments.models.TournamentTarget
import dev.marten_mrfcyt.tournament.utils.*
import mlib.api.utilities.message
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

class PlayerProgress private constructor() {
    private val logger = Logger.getLogger(PlayerProgress::class.java.name)
    private val file = File("plugins/Tournament/player_progress.yml")
    private val config = YamlConfiguration.loadConfiguration(file)

    // Use ConcurrentHashMap for thread safety
    private val playerProgress = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    companion object {
        private lateinit var instance: PlayerProgress

        fun getInstance(): PlayerProgress {
            if (!::instance.isInitialized) {
                instance = PlayerProgress()
                instance.loadProgressFromFile()
            }
            return instance
        }
    }

    fun loadProgressFromFile() {
        logger.info("Loading player progress from disk...")
        playerProgress.clear()

        config.getKeys(false).forEach { playerId ->
            val playerSection = config.getConfigurationSection(playerId) ?: return@forEach

            val playerMap = playerProgress.computeIfAbsent(playerId) { ConcurrentHashMap() }

            playerSection.getKeys(false).forEach { tournamentId ->
                val progress = playerSection.getInt(tournamentId)
                playerMap[tournamentId] = progress
            }
        }

        logger.info("Loaded progress for ${playerProgress.size} players")
    }

    fun saveProgressToFile() {
        logger.info("Saving player progress to disk...")

        config.getKeys(false).forEach { key ->
            config.set(key, null)
        }

        playerProgress.forEach { (playerId, tournamentProgress) ->
            tournamentProgress.forEach { (tournamentId, progress) ->
                config.set("$playerId.$tournamentId", progress)
            }
        }
        config.save(file)
        logger.info("Player progress saved successfully")
    }

    fun getProgress(playerId: String, tournamentId: String): Int {
        return playerProgress[playerId]?.get(tournamentId) ?: 0
    }

    fun updateProgress(playerId: String, tournamentId: String, progress: Int) {
        val playerMap = playerProgress.computeIfAbsent(playerId) { ConcurrentHashMap() }
        playerMap[tournamentId] = progress
    }

    fun getAllProgressForTournament(tournamentId: String): Map<String, Int> {
        val result = HashMap<String, Int>()

        playerProgress.forEach { (playerId, tournaments) ->
            tournaments[tournamentId]?.let { progress ->
                result[playerId] = progress
            }
        }

        return result
    }

    fun calculateStandingsAsync(tournament: Tournament): CompletableFuture<List<Pair<UUID, Int>>> {
        return CompletableFuture.supplyAsync {
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
                logger.warning("Tournament not found: $tournamentName")
                return
            }

        val config = YamlConfiguration.loadConfiguration(File("plugins/Tournament/tournaments.yml"))
        val inventoryItems = config.getList("tournaments.${tournament.name.replace(" ", "_").lowercase()}.inventory")?.filterIsInstance<ItemStack>()
            ?: run {
                logger.warning("Tournament inventory is null or empty for $tournamentName")
                return
            }

        val rewardsManager = RewardsManager.getInstance()

        calculateStandingsAsync(tournament).thenAccept { topPlayers ->
            val topPlayerIds = topPlayers.map { it.first }.toSet()
            val allPlayerProgress = getAllProgressForTournament(tournament.name)

            if (tournament.target == TournamentTarget.PROVINCE) {
                val kingdoms = getKingdoms()
                val kingdomMembers = kingdoms.flatMap { kingdom -> getKingdomMembers(kingdom).map { it to kingdom } }.toMap()
                val kingdomScores = mutableMapOf<String, Int>()

                // Calculate kingdom scores
                allPlayerProgress.forEach { (playerId, progress) ->
                    try {
                        val uuid = UUID.fromString(playerId)
                        val kingdom = kingdomMembers[uuid]
                        if (kingdom != null) {
                            kingdomScores[kingdom] = kingdomScores.getOrDefault(kingdom, 0) + progress
                        }
                    } catch (e: Exception) {
                        logger.warning("Error parsing player ID $playerId: ${e.message}")
                    }
                }

                // Sort kingdoms by score
                val sortedKingdoms = kingdomScores.entries
                    .sortedByDescending { it.value }
                    .map { it.key to it.value }

                // Add rewards for top kingdoms
                sortedKingdoms.forEachIndexed { index, (kingdom, score) ->
                    if (index < 5) { // Only top 5 get rewards
                        val reward = RewardsManager.Reward(
                            score = score,
                            position = index,
                            levels = tournament.levels ?: 0,
                            items = ArrayList(inventoryItems),
                            type = "provincie"
                        )

                        rewardsManager.addProvinceReward(kingdom, tournament.name, reward)

                        // Notify players in this kingdom
                        getKingdomMembers(kingdom).forEach { memberId ->
                            Bukkit.getPlayer(memberId)?.message(
                                "<green>Gefeliciteerd! Je provincie $kingdom heeft de ${tournament.name} gewonnen met $score punten! " +
                                        "Doe /tournament claimreward om je prijs te claimen."
                            )
                        }
                    }
                }

                // Notify players in kingdoms that didn't win
                kingdoms.filter { it !in sortedKingdoms.take(5).map { it.first } }.forEach { kingdom ->
                    getKingdomMembers(kingdom).forEach { memberId ->
                        Bukkit.getPlayer(memberId)?.message(
                            "<red>Helaas, je provincie heeft niet hoog genoeg gescoord in de ${tournament.name}. " +
                                    "Probeer het volgende keer opnieuw!"
                        )
                    }
                }
            } else {
                // Add rewards for top players
                topPlayers.forEachIndexed { index, (playerId, score) ->
                    if (index < 5) { // Only top 5 get rewards
                        val reward = RewardsManager.Reward(
                            score = score,
                            position = index,
                            levels = tournament.levels ?: 0,
                            items = ArrayList(inventoryItems),
                            type = "player"
                        )

                        rewardsManager.addPlayerReward(playerId, tournament.name, reward)
                        Bukkit.getPlayer(playerId)?.message(
                            "<green>Gefeliciteerd! Je hebt de ${tournament.name} gewonnen met $score punten! " +
                                    "Doe /tournament claimreward om je prijs te claimen."
                        )
                    }
                }

                // Notify players that didn't win
                allPlayerProgress.keys
                    .filter { UUID.fromString(it) !in topPlayerIds }
                    .forEach { playerId ->
                        Bukkit.getPlayer(UUID.fromString(playerId))?.message(
                            "<red>Helaas, je hebt niet hoog genoeg gescoord in de ${tournament.name}. " +
                                    "Probeer het volgende keer opnieuw!"
                        )
                    }
            }
        }

        removePlayersFromTournament(tournament.name)
    }

    fun removePlayersFromTournament(tournamentId: String) {
        logger.info("Removing all player progress for tournament: $tournamentId")

        // Remove from memory
        playerProgress.forEach { (_, tournaments) ->
            tournaments.remove(tournamentId)
        }

        // Clean up empty player entries
        val emptyPlayers = playerProgress.entries.filter { it.value.isEmpty() }
        emptyPlayers.forEach { playerProgress.remove(it.key) }

        logger.info("Removed tournament progress for $tournamentId")
    }
}