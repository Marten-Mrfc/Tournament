package dev.marten_mrfcyt.tournament.menus

import dev.marten_mrfcyt.tournament.Tournament
import dev.marten_mrfcyt.tournament.utils.asMini
import dev.marten_mrfcyt.tournament.utils.getKingdomMembers
import dev.marten_mrfcyt.tournament.utils.getKingdoms
import dev.marten_mrfcyt.tournament.utils.message
import dev.marten_mrfcyt.tournament.utils.setCustomValue
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.io.File
import java.util.UUID
import kotlin.collections.set
import kotlin.text.contains
import kotlin.toString

class RewardsMenu {

    fun open(player: Player) {
        val rewardsConfig = YamlConfiguration.loadConfiguration(File("plugins/Tournament/playerrewards.yml"))
        val playerId = player.uniqueId.toString()
        println(playerId)
        val playerRewards = rewardsConfig.getConfigurationSection("players.$playerId")?.getKeys(false) ?: emptySet()
        println(playerRewards)
        val provinceRewards: List<String> = rewardsConfig.getConfigurationSection("provincie")?.getKeys(false)?.flatMap { province ->
            println("Checking province: $province")
            getKingdoms().find { it == province }?.let {
                println("Found province: $it")
                getKingdomMembers(it).mapNotNull { memberId ->
                    if (memberId == player.uniqueId) {
                        println("Found member: $memberId")
                        rewardsConfig.getConfigurationSection("provincie.$province")?.getKeys(false)?.map { key ->
                            "$province: $key"
                        }
                    } else {
                        null
                    }
                }.flatten().also { members ->
                    if (members.isNotEmpty()) {
                        // Add your thing here
                        println("Adding thing for province: $province")
                    }
                }
            } ?: emptyList()
        } ?: emptyList()

        val inventory: Inventory = Bukkit.createInventory(null, 9 * 5, "Rewards".asMini())

        val fillerItem = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val fillerMeta: ItemMeta = fillerItem.itemMeta
        fillerMeta.displayName(" ".asMini())
        for (i in 0 until inventory.size) {
            inventory.setItem(i, fillerItem)
        }
        fillerItem.itemMeta = fillerMeta
        for (i in 19 until 26) {
            inventory.setItem(i, null)
        }

        (playerRewards + provinceRewards).forEachIndexed { index, rewardKey ->
            println(rewardKey)
            val score = rewardsConfig.getInt("$rewardKey.score")
            val item = ItemStack(Material.PAPER)
            val meta: ItemMeta = item.itemMeta
            meta.displayName("<b><gold>${rewardKey.replace("_", " ").uppercase()}</gold></b>".asMini())
            meta.lore(listOf(
                "".asMini(),
                "<dark_gray>Jouw Score</dark_gray>".asMini(),
                "<white>- </white><gray>$score".asMini(),
                "".asMini(),
                "<b><green>KLIK OM TE CLAIMEN</green></b>".asMini()
            ))
            setCustomValue(meta, Tournament.instance, "tournament", rewardKey.substringAfter(": "))
            setCustomValue(meta, Tournament.instance, "gui", "rewards")
            item.itemMeta = meta
            inventory.setItem(19 + index, item)
        }
        player.openInventory(inventory)
    }

    fun claimReward(player: Player, tournamentName: String) {
        println("Claiming reward for $tournamentName")
        val rewardsConfig = YamlConfiguration.loadConfiguration(File("plugins/Tournament/playerrewards.yml"))
        val playerId = player.uniqueId.toString()
        val rewardKey = "players.$playerId.$tournamentName"
        println("Player reward key: $rewardKey")

        val playerProvincie = getKingdoms().find { kingdom ->
            val members = getKingdomMembers(kingdom)
            println("Checking kingdom: $kingdom with members: $members")
            members.contains(UUID.fromString(playerId))
        }
        println("Player province: $playerProvincie")

        val provinceKey = "provincie.$playerProvincie.$tournamentName"
        println("Province reward key: $provinceKey")

        // Check if the reward is for a province
        val isProvinceReward = rewardsConfig.contains(provinceKey)
        println("Is province reward: $isProvinceReward")

        val inventoryItems = if (isProvinceReward) {
            rewardsConfig.getList("$provinceKey.items")?.filterIsInstance<ItemStack>()
        } else {
            rewardsConfig.getList("$rewardKey.items")?.filterIsInstance<ItemStack>()
        } ?: run {
            println("No items found for reward key")
            return
        }

        val remainingItems = mutableListOf<ItemStack>()
        inventoryItems.forEach { item ->
            val result = player.inventory.addItem(item)
            if (result.isNotEmpty()) {
                remainingItems.addAll(result.values)
            }
        }
        if (remainingItems.isNotEmpty()) {
            Bukkit.getScheduler().runTask(Tournament.instance, Runnable {
                remainingItems.forEach { remainingItem ->
                    player.world.dropItemNaturally(player.location, remainingItem)
                }
            })
            player.message("Jammer genoeg had je niet genoeg ruimte in je inventory voor alle items en zijn ze op de grond gevallen.")
        }
        player.giveExpLevels(rewardsConfig.getInt(if (isProvinceReward) "$provinceKey.levels" else "$rewardKey.levels"))

        if (isProvinceReward) {
            rewardsConfig.set("provincie.$playerProvincie.$tournamentName", null)
        } else {
            rewardsConfig.set(rewardKey, null)
        }
        rewardsConfig.save(File("plugins/Tournament/playerrewards.yml"))
        player.message("Je hebt je beloningen voor ${tournamentName.replace("_", " ")} gekregen!")
    }}