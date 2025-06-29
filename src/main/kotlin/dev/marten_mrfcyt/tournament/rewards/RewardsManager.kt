package dev.marten_mrfcyt.tournament.rewards

import com.gufli.kingdomcraft.api.domain.Kingdom
import dev.marten_mrfcyt.tournament.Tournament
import dev.marten_mrfcyt.tournament.utils.getKingdoms
import mlib.api.utilities.message
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger
import kotlin.collections.forEach
import kotlin.text.set

class RewardsManager private constructor() {
    private val logger = Logger.getLogger(RewardsManager::class.java.name)
    private val file = File("plugins/Tournament/playerrewards.yml")

    // In-memory storage
    private val playerRewards = ConcurrentHashMap<UUID, MutableMap<String, Reward>>()
    private val provinceRewards = ConcurrentHashMap<Any, MutableMap<String, Reward>>()
    private val pendingProvinceNames = mutableSetOf<String>()

    companion object {
        private lateinit var instance: RewardsManager

        fun getInstance(): RewardsManager {
            if (!::instance.isInitialized) {
                instance = RewardsManager()
            }
            return instance
        }
    }

    fun saveRewards() {
        try {
            val newConfig = YamlConfiguration()

            // Save player rewards
            playerRewards.forEach { (uuid, rewards) ->
                if (rewards.isEmpty()) return@forEach

                rewards.forEach { (tournamentName, reward) ->
                    val path = "players.${uuid}.${tournamentName}"
                    val section = newConfig.createSection(path)
                    saveRewardToSection(section, reward)
                }
            }

            // Save province rewards
            provinceRewards.forEach { (provinceKey, rewards) ->
                if (rewards.isEmpty()) return@forEach

                val provinceName = when (provinceKey) {
                    is Kingdom -> provinceKey.name
                    is String -> provinceKey
                    else -> return@forEach
                }

                rewards.forEach { (tournamentName, reward) ->
                    val path = "provincie.${provinceName}.${tournamentName}"
                    val section = newConfig.createSection(path)
                    saveRewardToSection(section, reward)
                    logger.info("Saved province reward: ${provinceName}, tournament: $tournamentName")
                }
            }
            synchronized(file) {
                newConfig.save(file)
                file.setLastModified(System.currentTimeMillis())
            }
        } catch (e: Exception) {
            logger.severe("Error saving rewards: ${e.message}")
            e.printStackTrace()
        }
    }

    fun loadRewards() {
        playerRewards.clear()
        provinceRewards.clear()
        pendingProvinceNames.clear()

        try {
            val config = YamlConfiguration.loadConfiguration(file)

            // Load player rewards
            val playersSection = config.getConfigurationSection("players")

            playersSection?.getKeys(false)?.forEach { playerId ->
                try {
                    val uuid = UUID.fromString(playerId)
                    val playerMap = playerRewards.computeIfAbsent(uuid) { mutableMapOf() }

                    val tournamentSection = playersSection.getConfigurationSection(playerId)
                    tournamentSection?.getKeys(false)?.forEach { tournamentName ->
                        val section = tournamentSection.getConfigurationSection(tournamentName)
                        if (section != null) {
                            playerMap[tournamentName] = loadRewardFromSection(section)
                        }
                    }
                } catch (e: Exception) {
                    logger.severe("Error loading player rewards for $playerId: ${e.message}")
                }
            }
            logger.info("Loaded ${playerRewards.size} player rewards")
            // Load province rewards (using the same section name as when saving)
            val provincesSection = config.getConfigurationSection("provincie")

            provincesSection?.getKeys(false)?.forEach { provinceName ->
                logger.info("Loading rewards for province: $provinceName")
                val kingdoms = getKingdoms()

                if (kingdoms.isEmpty()) {
                    logger.warning("Kingdoms are not yet loaded - will defer province rewards loading")

                    // Store the rewards with the province name as key
                    val provinceMap = provinceRewards.computeIfAbsent(provinceName) { mutableMapOf() }

                    val tournamentSection = provincesSection.getConfigurationSection(provinceName)
                    tournamentSection?.getKeys(false)?.forEach { tournamentName ->
                        val section = tournamentSection.getConfigurationSection(tournamentName)
                        if (section != null) {
                            provinceMap[tournamentName] = loadRewardFromSection(section)
                            logger.info("Temporarily stored reward for province $provinceName, tournament $tournamentName")
                        }
                    }

                    pendingProvinceNames.add(provinceName)

                    // Schedule delayed kingdom resolution
                    Bukkit.getScheduler().runTaskLater(Tournament.instance, Runnable {
                        resolveProvinceKingdoms()
                    }, 100L)

                    return@forEach
                }

                val kingdom = kingdoms.find { it.name.equals(provinceName, ignoreCase = true) }
                if (kingdom == null) {
                    logger.warning("Could not find province with name: $provinceName (available: ${kingdoms.joinToString { it.name }})")
                    return@forEach
                }

                val provinceMap = provinceRewards.computeIfAbsent(kingdom) { mutableMapOf() }
                logger.info("Loading rewards for province: $provinceName")
                val tournamentSection = provincesSection.getConfigurationSection(provinceName)
                tournamentSection?.getKeys(false)?.forEach { tournamentName ->
                    val section = tournamentSection.getConfigurationSection(tournamentName)
                    if (section != null) {
                        provinceMap[tournamentName] = loadRewardFromSection(section)
                    }
                }
            }

            logger.info("Rewards loaded successfully!")
            logger.info("Loaded ${playerRewards.size} player rewards and ${provinceRewards.size} province rewards.")
        } catch (e: Exception) {
            logger.severe("Error loading rewards: ${e.message}")
            e.printStackTrace()
        }
    }
    private fun resolveProvinceKingdoms() {
        logger.info("Resolving province kingdoms...")
        val kingdoms = getKingdoms()

        if (kingdoms.isEmpty()) {
            logger.warning("Kingdoms still not available. Will try again later.")
            Bukkit.getScheduler().runTaskLater(Tournament.instance, Runnable {
                resolveProvinceKingdoms()
            }, 100L)
            return
        }

        // Try to resolve pending province names
        val resolvedNames = mutableListOf<String>()

        pendingProvinceNames.forEach { provinceName ->
            val kingdom = kingdoms.find { it.name.equals(provinceName, ignoreCase = true) }

            if (kingdom != null) {
                // Found the kingdom, transfer rewards
                val rewards = provinceRewards[provinceName] as? MutableMap<String, Reward>
                if (rewards != null) {
                    provinceRewards.remove(provinceName)
                    provinceRewards[kingdom] = rewards
                    logger.info("Resolved kingdom ${kingdom.name} for province $provinceName with ${rewards.size} rewards")
                    resolvedNames.add(provinceName)
                }
            } else {
                logger.warning("Still could not find kingdom for province: $provinceName")
            }
        }

        pendingProvinceNames.removeAll(resolvedNames)

        if (pendingProvinceNames.isNotEmpty()) {
            logger.warning("${pendingProvinceNames.size} provinces still pending resolution")
        } else {
            logger.info("All provinces successfully resolved!")
        }
    }

    fun getProvinceRewardsForPlayer(playerId: UUID): Map<Kingdom, Pair<String, Reward>> {
        val kingdoms = getKingdoms()
        logger.info("Looking for kingdom with player $playerId among ${kingdoms.size} kingdoms")

        val playerKingdom = kingdoms.find { kingdom ->
            val members = kingdom.members.keys
            val contains = members.contains(playerId)
            logger.info("Kingdom ${kingdom.name} has player? $contains (member count: ${members.size})")
            contains
        }

        if (playerKingdom == null) {
            logger.warning("No kingdom found for player $playerId")
            return emptyMap()
        }

        logger.info("Found kingdom ${playerKingdom.name} for player $playerId")

        // Check for direct kingdom match
        val kingdomRewards = provinceRewards[playerKingdom] as? Map<String, Reward>

        // Check for string-based kingdom name match if direct match fails
        val nameBasedRewards = if (kingdomRewards == null) {
            provinceRewards[playerKingdom.name] as? Map<String, Reward>
        } else null

        val foundRewards = kingdomRewards ?: nameBasedRewards

        if (foundRewards == null) {
            logger.warning("No rewards found for kingdom ${playerKingdom.name}")
            return emptyMap()
        }

        logger.info("Found ${foundRewards.size} rewards for kingdom ${playerKingdom.name}")

        // Return all kingdom rewards
        return foundRewards.map { (tournamentName, reward) ->
            playerKingdom to Pair(tournamentName, reward)
        }.toMap()
    }

    private fun loadRewardFromSection(section: ConfigurationSection): Reward {
        val score = section.getInt("score")
        val position = section.getInt("position")
        val levels = section.getInt("levels", 0)
        val items = section.getList("items")?.filterIsInstance<ItemStack>() ?: listOf()
        val type = section.getString("type") ?: "player"
        val objectiveType = section.getString("objectiveType") ?: "default"

        return Reward(score, position, levels, ArrayList(items), type, objectiveType)
    }

    private fun saveRewardToSection(section: ConfigurationSection, reward: Reward) {
        section.set("score", reward.score)
        section.set("position", reward.position)
        section.set("levels", reward.levels)
        section.set("items", reward.items)
        section.set("type", reward.type)
        section.set("objectiveType", reward.objectiveType)
    }

    fun addPlayerReward(playerId: UUID, tournamentName: String, reward: Reward): Boolean {
        if (tournamentName.isBlank()) {
            logger.warning("Attempted to add player reward with blank tournament name")
            return false
        }

        val playerMap = playerRewards.computeIfAbsent(playerId) { mutableMapOf() }
        val previousReward = playerMap.put(tournamentName, reward)

        // Notify if this is an update
        if (previousReward != null && previousReward.score != reward.score) {
            Bukkit.getPlayer(playerId)?.message("<yellow>Your reward for ${tournamentName.replace("_", " ")} has been updated!")
        }

        // Always save immediately
        saveRewards()
        return true
    }

    fun addProvinceReward(province: Kingdom, tournamentName: String, reward: Reward): Boolean {
        if (tournamentName.isBlank()) {
            logger.warning("Attempted to add province reward with blank province or tournament name")
            return false
        }

        val provinceMap = provinceRewards.computeIfAbsent(province) { mutableMapOf() }
        val previousReward = provinceMap.put(tournamentName, reward)

        // Notify province members about new or updated rewards
        if (previousReward == null || previousReward.score != reward.score) {
            Bukkit.getScheduler().runTask(Tournament.instance, Runnable {
                province.members.mapNotNull { Bukkit.getPlayer(it.key) }.forEach { player ->
                    player.message("<yellow>Jou provincie heeft een reward voor: ${tournamentName.replace("_", " ")}!")
                }
            })
        }

        // Always save immediately
        saveRewards()
        return true
    }

    fun getPlayerRewards(playerId: UUID): Map<String, Reward> {
        return playerRewards[playerId]?.toMap() ?: emptyMap()
    }

    fun getProvinceRewardsFor(province: Kingdom): Map<String, Reward>? {
        return provinceRewards[province]?.toMap()
    }

    fun claimReward(player: Player, tournamentName: String, isProvinceReward: Boolean, provinceName: Kingdom? = null) {
        val playerId = player.uniqueId

        val reward = if (isProvinceReward) {
            val province = provinceName ?: getKingdoms().find { kingdom ->
                kingdom.members.keys.contains(playerId)
            } ?: run {
                logger.warning("No province found for player ${player.name}")
                return
            }

            provinceRewards[province]?.get(tournamentName) ?: run {
                logger.warning("No province reward found for $province:$tournamentName")
                return
            }
        } else {
            playerRewards[playerId]?.get(tournamentName) ?: run {
                logger.warning("No player reward found for ${player.name}:$tournamentName")
                return
            }
        }

        val rewardItems = ArrayList(reward.items)
        val position = reward.position + 1
        val objectiveType = reward.objectiveType
        // Process reward claiming logic
        val config = Tournament.instance.config

        val ecoAmount = config.getInt("rewards.$objectiveType.$position.eco",
            config.getInt("rewards.default.$position.eco", 0))
        val diamonds = config.getInt("rewards.$objectiveType.$position.diamonds",
            config.getInt("rewards.default.$position.diamonds", 0))

        if (ecoAmount > 0) {
            Tournament.essentials.getUser(player).money += ecoAmount.toBigDecimal()
            player.message("<green>+$ecoAmount gulden!")
        }

        if (diamonds > 0) {
            rewardItems.add(ItemStack(Material.DIAMOND, diamonds))
        }

        val remainingItems = mutableListOf<ItemStack>()
        rewardItems.forEach { item ->
            val result = player.inventory.addItem(item)
            if (result.isNotEmpty()) {
                remainingItems.addAll(result.values)
            }
        }

        if (remainingItems.isNotEmpty()) {
            remainingItems.forEach { item ->
                player.world.dropItemNaturally(player.location, item)
            }
            player.message("<red>Jammer genoeg had je niet genoeg ruimte in je inventory voor alle items en zijn ze op de grond gevallen.")
        }

        if (reward.levels > 0) {
            player.giveExpLevels(reward.levels)
            player.message("<green>+${reward.levels} experience levels!")
        }

        // Now remove the reward after successful claiming
        if (isProvinceReward && provinceName != null) {
            provinceRewards[provinceName]?.remove(tournamentName)
            provinceName.members.forEach { memberId ->
                val member = Bukkit.getPlayer(memberId.key)
                member?.message("<white>${player.name} heeft de beloning voor $tournamentName geclaimd!")
            }
        } else {
            playerRewards[playerId]?.remove(tournamentName)
        }

        player.message("<green>Je hebt je beloningen voor ${tournamentName.replace("_", " ")} gekregen!")

        // Save changes to disk immediately
        saveRewards()
    }

    fun getRewardsFile(): File {
        return file
    }

    fun removeAllRewards(tournamentName: String) {
        playerRewards.values.forEach { rewards ->
            rewards.remove(tournamentName)
        }
        provinceRewards.values.forEach { rewards ->
            rewards.remove(tournamentName)
        }

        // Save after removing rewards
        saveRewards()
    }

    data class Reward(
        val score: Int,
        val position: Int,
        val levels: Int,
        val items: ArrayList<ItemStack>,
        val type: String,
        val objectiveType: String = "default"
    )
}