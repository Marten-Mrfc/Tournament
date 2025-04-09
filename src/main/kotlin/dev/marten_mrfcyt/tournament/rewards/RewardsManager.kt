package dev.marten_mrfcyt.tournament.rewards

import dev.marten_mrfcyt.tournament.Tournament
import dev.marten_mrfcyt.tournament.utils.getKingdomMembers
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

class RewardsManager private constructor() {
    private val logger = Logger.getLogger(RewardsManager::class.java.name)
    private val file = File("plugins/Tournament/playerrewards.yml")
    private val config = YamlConfiguration.loadConfiguration(file)

    private val playerRewards = ConcurrentHashMap<UUID, MutableMap<String, Reward>>()
    private val provinceRewards = ConcurrentHashMap<String, MutableMap<String, Reward>>()

    companion object {
        private lateinit var instance: RewardsManager

        fun getInstance(): RewardsManager {
            if (!::instance.isInitialized) {
                instance = RewardsManager()
            }
            return instance
        }
    }

    fun loadRewards() {
        logger.info("Loading rewards from disk...")
        playerRewards.clear()
        provinceRewards.clear()

        val playersSection = config.getConfigurationSection("players")
        playersSection?.getKeys(false)?.forEach { playerId ->
            val uuid = UUID.fromString(playerId)
            val playerMap = playerRewards.computeIfAbsent(uuid) { mutableMapOf() }

            val tournamentSection = playersSection.getConfigurationSection(playerId)
            tournamentSection?.getKeys(false)?.forEach { tournamentName ->
                val section = tournamentSection.getConfigurationSection(tournamentName)
                if (section != null) {
                    playerMap[tournamentName] = loadRewardFromSection(section)
                }
            }
        }

        val provincesSection = config.getConfigurationSection("provincie")
        provincesSection?.getKeys(false)?.forEach { provinceName ->
            val provinceMap = provinceRewards.computeIfAbsent(provinceName) { mutableMapOf() }

            val tournamentSection = provincesSection.getConfigurationSection(provinceName)
            tournamentSection?.getKeys(false)?.forEach { tournamentName ->
                val section = tournamentSection.getConfigurationSection(tournamentName)
                if (section != null) {
                    provinceMap[tournamentName] = loadRewardFromSection(section)
                }
            }
        }

        logger.info("Loaded ${playerRewards.size} player rewards and ${provinceRewards.size} province rewards")
    }

    fun saveRewards() {
        logger.info("Saving rewards to disk...")

        config.getKeys(false).forEach { key ->
            config.set(key, null)
        }

        playerRewards.forEach { (uuid, rewards) ->
            rewards.forEach { (tournamentName, reward) ->
                val path = "players.${uuid}.$tournamentName"
                saveRewardToSection(config.createSection(path), reward)
            }
        }

        provinceRewards.forEach { (province, rewards) ->
            rewards.forEach { (tournamentName, reward) ->
                val path = "provincie.$province.$tournamentName"
                saveRewardToSection(config.createSection(path), reward)
            }
        }

        config.save(file)
        logger.info("Rewards saved successfully")
    }

    private fun loadRewardFromSection(section: ConfigurationSection): Reward {
        val score = section.getInt("score")
        val position = section.getInt("position")
        val levels = section.getInt("levels", 0)
        val items = section.getList("items")?.filterIsInstance<ItemStack>() ?: listOf()
        val type = section.getString("type") ?: "player"

        return Reward(score, position, levels, ArrayList(items), type)
    }

    private fun saveRewardToSection(section: ConfigurationSection, reward: Reward) {
        section.set("score", reward.score)
        section.set("position", reward.position)
        section.set("levels", reward.levels)
        section.set("items", reward.items)
        section.set("type", reward.type)
    }

    fun addPlayerReward(playerId: UUID, tournamentName: String, reward: Reward) {
        val playerMap = playerRewards.computeIfAbsent(playerId) { mutableMapOf() }
        playerMap[tournamentName] = reward
    }

    fun addProvinceReward(province: String, tournamentName: String, reward: Reward) {
        val provinceMap = provinceRewards.computeIfAbsent(province) { mutableMapOf() }
        provinceMap[tournamentName] = reward
    }

    fun getPlayerRewards(playerId: UUID): Map<String, Reward> {
        return playerRewards[playerId]?.toMap() ?: emptyMap()
    }

    fun getProvinceRewardsForPlayer(playerId: UUID): Map<String, Pair<String, Reward>> {
        val playerKingdom = getKingdoms().find { kingdom ->
            getKingdomMembers(kingdom).contains(playerId)
        } ?: return emptyMap()

        return provinceRewards[playerKingdom]?.map { (tournamentName, reward) ->
            tournamentName to (playerKingdom to reward)
        }?.toMap() ?: emptyMap()
    }

    fun claimReward(player: Player, tournamentName: String, isProvinceReward: Boolean, provinceName: String? = null) {
        val playerId = player.uniqueId

        val reward = if (isProvinceReward) {
            val provinceName = provinceName ?: getKingdoms().find { kingdom ->
                getKingdomMembers(kingdom).contains(playerId)
            } ?: run {
                logger.warning("No province found for player ${player.name}")
                return
            }

            provinceRewards[provinceName]?.remove(tournamentName) ?: run {
                logger.warning("No province reward found for $provinceName:$tournamentName")
                return
            }
        } else {
            playerRewards[playerId]?.remove(tournamentName) ?: run {
                logger.warning("No player reward found for ${player.name}:$tournamentName")
                return
            }
        }

        val rewardItems = ArrayList(reward.items)

        if (Tournament.instance.config.contains("${reward.position + 1}-eco")) {
            val ecoAmount = Tournament.instance.config.getInt("${reward.position + 1}-eco")
            if (ecoAmount > 0) {
                Tournament.essentials.getUser(player).money += ecoAmount.toBigDecimal()
                player.message("<green>+$ecoAmount gulden!")
            }
        }

        if (Tournament.instance.config.contains("${reward.position + 1}-diamonds")) {
            val diamonds = Tournament.instance.config.getInt("${reward.position + 1}-diamonds")
            if (diamonds > 0) {
                rewardItems.add(ItemStack(Material.DIAMOND, diamonds))
            }
        }

        val remainingItems = mutableListOf<ItemStack>()
        rewardItems.forEach { item ->
            val result = player.inventory.addItem(item)
            if (result.isNotEmpty()) {
                remainingItems.addAll(result.values)
            }
        }

        if (remainingItems.isNotEmpty()) {
            Bukkit.getScheduler().runTask(Tournament.instance, Runnable {
                remainingItems.forEach { item ->
                    player.world.dropItemNaturally(player.location, item)
                }
            })
            player.message("<red>Jammer genoeg had je niet genoeg ruimte in je inventory voor alle items en zijn ze op de grond gevallen.")
        }

        if (reward.levels > 0) {
            player.giveExpLevels(reward.levels)
            player.message("<green>+${reward.levels} experience levels!")
        }
        if(isProvinceReward && provinceName != null) {
            getKingdomMembers(provinceName).forEach { memberId ->
                val member = Bukkit.getPlayer(memberId)
                member?.message("<white>${player.name} heeft de beloning voor $tournamentName geclaimd!")
            }
        }
        player.message("<green>Je hebt je beloningen voor ${tournamentName.replace("_", " ")} gekregen!")
        Tournament.instance.logger.info("Player ${player.name} claimed reward for $tournamentName (${reward.score} points, position ${reward.position + 1}) ${if (isProvinceReward) "for province $provinceName" else ""}")
    }

    fun removeAllRewards(tournamentName: String) {
        playerRewards.values.forEach { rewards ->
            rewards.remove(tournamentName)
        }
        provinceRewards.values.forEach { rewards ->
            rewards.remove(tournamentName)
        }
    }

    data class Reward(
        val score: Int,
        val position: Int,
        val levels: Int,
        val items: ArrayList<ItemStack>,
        val type: String
    )
}