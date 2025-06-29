package dev.marten_mrfcyt.tournament.tournaments

import com.gufli.kingdomcraft.api.domain.Kingdom
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
import kotlin.text.get

class PlayerProgress private constructor() {
    private val logger = Logger.getLogger(PlayerProgress::class.java.name)
    private val file = File("plugins/Tournament/player_progress.yml")
    private val config = YamlConfiguration.loadConfiguration(file)

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
        Bukkit.getScheduler().runTaskAsynchronously(dev.marten_mrfcyt.tournament.Tournament.instance, Runnable {
            try {
                val newConfig = YamlConfiguration()

                val playerBatches = playerProgress.entries.chunked(100)
                for (batch in playerBatches) {
                    for ((playerId, tournamentProgress) in batch) {
                        if (tournamentProgress.isEmpty()) continue

                        tournamentProgress.forEach { (tournamentId, progress) ->
                            newConfig.set("$playerId.$tournamentId", progress)
                        }
                    }
                }

                synchronized(file) {
                    newConfig.save(file)
                }
            } catch (e: Exception) {
                logger.severe("Error saving player progress: ${e.message}")
                e.printStackTrace()
            }
        })
    }
    fun saveProgressToFileSync() {
        try {
            val newConfig = YamlConfiguration()

            playerProgress.forEach { (playerId, tournamentProgress) ->
                if (tournamentProgress.isEmpty()) return@forEach

                tournamentProgress.forEach { (tournamentId, progress) ->
                    newConfig.set("$playerId.$tournamentId", progress)
                }
            }

            synchronized(file) {
                newConfig.save(file)
            }
        } catch (e: Exception) {
            logger.severe("Error saving player progress: ${e.message}")
            e.printStackTrace()
        }
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

        logger.info("Giving rewards for tournament: $tournamentName (target: ${tournament.target})")

        val config = YamlConfiguration.loadConfiguration(File("plugins/Tournament/tournaments.yml"))
        val inventoryItems = config.getList("tournaments.${tournament.name.replace(" ", "_").lowercase()}.inventory")?.filterIsInstance<ItemStack>()
            ?: run {
                logger.warning("Tournament inventory is null or empty for $tournamentName")
                return
            }

        logger.info("Found ${inventoryItems.size} reward items for tournament $tournamentName")

        val rewardsManager = RewardsManager.getInstance()

        calculateStandingsAsync(tournament).thenAccept { topPlayers ->
            val topPlayerIds = topPlayers.map { it.first }.toSet()
            val allPlayerProgress = getAllProgressForTournament(tournament.name)

            if (tournament.target == TournamentTarget.PROVINCE) {
                logger.info("Processing PROVINCE rewards for tournament $tournamentName")
                val kingdoms = getKingdoms()
                logger.info("Found ${kingdoms.size} kingdoms")

                val kingdomMembers = kingdoms.flatMap { kingdom ->
                    val members = kingdom.members.keys
                    logger.info("Kingdom ${kingdom.name} has ${members.size} members")
                    members.map { it to kingdom }
                }.toMap()

                val kingdomScores = mutableMapOf<Kingdom, Int>()

                allPlayerProgress.forEach { (playerId, progress) ->
                    try {
                        val uuid = UUID.fromString(playerId)
                        val kingdom = kingdomMembers[uuid]
                        if (kingdom != null) {
                            kingdomScores[kingdom] = kingdomScores.getOrDefault(kingdom, 0) + progress
                            logger.info("Added $progress points to kingdom $kingdom (total: ${kingdomScores[kingdom]})")
                        }
                    } catch (e: Exception) {
                        logger.warning("Error parsing player ID $playerId: ${e.message}")
                    }
                }

                val sortedKingdoms = kingdomScores.entries
                    .sortedByDescending { it.value }
                    .map { it.key to it.value }

                logger.info("Sorted kingdoms by score: ${sortedKingdoms.joinToString { "${it.first}=${it.second}" }}")

                sortedKingdoms.forEachIndexed { index, (kingdom, score) ->
                    if (index <= 5) {
                        val reward = RewardsManager.Reward(
                            score = score,
                            position = index,
                            levels = tournament.levels ?: 0,
                            items = ArrayList(inventoryItems),
                            type = "provincie",
                            objectiveType = tournament.objective.type.name
                        )

                        logger.info("Adding province reward for kingdom $kingdom, tournament $tournamentName, score $score, position $index")
                        val success = rewardsManager.addProvinceReward(kingdom, tournament.name, reward)
                        logger.info("Province reward added successfully: $success")

                        // Debug province rewards map after adding
                        val rewardsMap = rewardsManager.getProvinceRewardsFor(kingdom)
                        logger.info("Kingdom $kingdom now has ${rewardsMap?.size ?: 0} rewards")

                        kingdom.members.keys.forEach { memberId ->
                            Bukkit.getPlayer(memberId)?.message(
                                "<green>Gefeliciteerd! Je provincie ${kingdom.name} heeft de ${tournament.name} gewonnen met $score punten! " +
                                        "Doe /tournament claimreward om je prijs te claimen."
                            )
                        }
                    }
                }
            } else {
                topPlayers.forEachIndexed { index, (playerId, score) ->
                    if (index <= 5) {
                        val reward = RewardsManager.Reward(
                            score = score,
                            position = index,
                            levels = tournament.levels ?: 0,
                            items = ArrayList(inventoryItems),
                            type = "player",
                            objectiveType = tournament.objective.type.name
                        )
                        rewardsManager.addPlayerReward(playerId, tournament.name, reward)
                        Bukkit.getPlayer(playerId)?.message(
                            "<green>Gefeliciteerd! Je hebt de ${tournament.name} gewonnen met $score punten! " +
                                    "Doe /tournament claimreward om je prijs te claimen."
                        )
                    }
                }

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

        try {
            logger.info("Saving rewards after tournament completion")
            val rewardsManager = RewardsManager.getInstance()

            rewardsManager.saveRewards()

            logger.info("Rewards saved, now reloading from disk")
            rewardsManager.loadRewards()

            // Now remove the players from tournament
            removePlayersFromTournament(tournament.name)
        } catch (e: Exception) {
            logger.severe("Error in reward processing: ${e.message}")
            e.printStackTrace()
        }
    }
    fun removePlayersFromTournament(tournamentId: String) {
        logger.info("Removing all player progress for tournament: $tournamentId")

        playerProgress.forEach { (_, tournaments) ->
            tournaments.remove(tournamentId)
        }

        val emptyPlayers = playerProgress.entries.filter { it.value.isEmpty() }
        emptyPlayers.forEach { playerProgress.remove(it.key) }

        logger.info("Removed tournament progress for $tournamentId")
    }
}