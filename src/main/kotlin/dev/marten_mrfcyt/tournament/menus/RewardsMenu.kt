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

class RewardsMenu {

    fun open(player: Player) {
        val rewardsConfig = YamlConfiguration.loadConfiguration(File("plugins/Tournament/playerrewards.yml"))
        val playerId = player.uniqueId.toString()
        val playerRewards: Map<String, String> = rewardsConfig.getConfigurationSection("players.$playerId")?.getKeys(false)?.associate { key ->
            key to "players.$playerId.$key"
        } ?: emptyMap()
        val provinceRewards: Map<String, String> = rewardsConfig.getConfigurationSection("provincie")?.getKeys(false)?.flatMap { province ->
            getKingdoms().find { it == province }?.let {
                getKingdomMembers(it).mapNotNull { memberId ->
                    if (memberId == player.uniqueId && player.hasPermission("tournament.claimreward.provincie")) {
                        rewardsConfig.getConfigurationSection("provincie.$province")?.getKeys(false)?.map { key ->
                            "$province: $key" to "provincie.$province.$key"
                        }
                    } else {
                        null
                    }
                }.flatten()
            } ?: emptyList()
        }?.toMap() ?: emptyMap()

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
        var index = 0
        (playerRewards + provinceRewards).forEach { (rewardKey, rewardPath) ->
            val score = rewardsConfig.getInt("$rewardPath.score")
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
            index++
        }
        player.openInventory(inventory)
    }

    fun claimReward(player: Player, tournamentName: String) {
        val rewardsConfig = YamlConfiguration.loadConfiguration(File("plugins/Tournament/playerrewards.yml"))
        val playerId = player.uniqueId.toString()
        val rewardKey = "players.$playerId.$tournamentName"

        val playerProvincie = getKingdoms().find { kingdom ->
            val members = getKingdomMembers(kingdom)
            members.contains(UUID.fromString(playerId))
        }

        val provinceKey = "provincie.$playerProvincie.$tournamentName"

        val isProvinceReward = rewardsConfig.contains(provinceKey)

        var inventoryItems = if (isProvinceReward) {
            rewardsConfig.getList("$provinceKey.items")?.filterIsInstance<ItemStack>()
        } else {
            rewardsConfig.getList("$rewardKey.items")?.filterIsInstance<ItemStack>()
        } ?: run {
            println("No items found for reward key")
            return
        }
        inventoryItems = ArrayList(inventoryItems)

        val position = if (isProvinceReward) {
            rewardsConfig.getInt("$provinceKey.position") + 1
        } else {
            rewardsConfig.getInt("$rewardKey.position") + 1
        }
        if(Tournament.instance.config.contains("$position-eco") && Tournament.instance.config.getInt("$position-eco") != 0) {
            Tournament.essentials.getUser(player).money += Tournament.instance.config.getInt("$position-eco").toBigDecimal()
        }
        if(Tournament.instance.config.contains("$position-diamonds") && Tournament.instance.config.getInt("$position-diamonds") != 0) {
            inventoryItems.add(ItemStack(Material.DIAMOND, Tournament.instance.config.getInt("$position-diamonds")))
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