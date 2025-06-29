package dev.marten_mrfcyt.tournament.menus

import dev.marten_mrfcyt.tournament.Tournament
import dev.marten_mrfcyt.tournament.rewards.RewardsManager
import dev.marten_mrfcyt.tournament.tournaments.TournamentManager
import mlib.api.gui.GuiSize
import mlib.api.gui.types.builder.StandardGuiBuilder
import mlib.api.utilities.asMini
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player

class RewardsMenu {

    fun open(player: Player) {
        val rewardsManager = RewardsManager.getInstance()
        val logger = Tournament.instance.logger

        rewardsManager.loadRewards()

        val playerId = player.uniqueId
        val playerRewards = rewardsManager.getPlayerRewards(playerId)

        val provinceRewards = if (player.hasPermission("turnament")) {
            val rewards = rewardsManager.getProvinceRewardsForPlayer(playerId)
            logger.info("Found ${rewards.size} province rewards for ${player.name}")
            rewards
        } else {
            logger.info("Player does not have permission: tournament.claimreward.provincie")
            emptyMap()
        }

        val gui = StandardGuiBuilder()
            .title("Rewards".asMini())
            .size(GuiSize.ROW_FIVE)
            .setup { standardGui ->
                standardGui.fill(Material.GRAY_STAINED_GLASS_PANE) {
                    name(" ".asMini())
                    onClick { event -> event.isCancelled = true }
                }

                var slotIndex = 11
                playerRewards.entries.forEachIndexed { index, (tournamentName, reward) ->
                    if (slotIndex < 36) { // Limit to available slots
                        standardGui.item(Material.PAPER) {
                            name("<b><gold>${tournamentName.replace("_", " ").uppercase()}</gold></b>".asMini())
                            description(listOf(
                                "".asMini(),
                                "<dark_gray>Jouw Score</dark_gray>".asMini(),
                                "<white>- </white><gray>${reward.score}".asMini(),
                                "".asMini(),
                                "<dark_gray>Positie</dark_gray>".asMini(),
                                "<white>- </white><gray>#${reward.position + 1}".asMini(),
                                "".asMini(),
                                "<b><green>KLIK OM TE CLAIMEN</green></b>".asMini()
                            ))
                            slots(slotIndex)

                            onClick { event ->
                                event.isCancelled = true
                                rewardsManager.claimReward(player, tournamentName, false)
                                Bukkit.getScheduler().runTaskLater(Tournament.instance, Runnable { open(player) }, 1L)
                            }
                        }
                        slotIndex++
                    }
                }

                provinceRewards.entries.forEachIndexed { index, (provincie, provinceData) ->
                    if (slotIndex < 36) { // Limit to available slots
                        val (tournamentName, reward) = provinceData

                        standardGui.item(Material.GOLDEN_HELMET) {
                            name("<b><gold>${provincie.name}: ${tournamentName.replace("_", " ").uppercase()}</gold></b>".asMini())
                            description(listOf(
                                "".asMini(),
                                "<dark_gray>Provincie Score</dark_gray>".asMini(),
                                "<white>- </white><gray>${reward.score}".asMini(),
                                "".asMini(),
                                "<dark_gray>Positie</dark_gray>".asMini(),
                                "<white>- </white><gray>#${reward.position + 1}".asMini(),
                                "".asMini(),
                                "<b><green>KLIK OM TE CLAIMEN</green></b>".asMini()
                            ))
                            slots(slotIndex)

                            onClick { event ->
                                event.isCancelled = true
                                rewardsManager.claimReward(player, tournamentName, true, provincie)
                                Bukkit.getScheduler().runTaskLater(Tournament.instance, Runnable { open(player) }, 1L)
                            }
                        }
                        slotIndex++
                    }
                }
                if (playerRewards.isEmpty() && provinceRewards.isEmpty()) {
                    standardGui.item(Material.BARRIER) {
                        name("<b><red>Geen beloningen beschikbaar</red></b>".asMini())
                        description(listOf(
                            "".asMini(),
                            "<gray>Je hebt momenteel geen beloningen".asMini(),
                            "<gray>om te claimen.".asMini()
                        ))
                        slots(22)
                        onClick { event ->
                            event.isCancelled = true
                        }
                    }
                }
                standardGui.item(Material.ARROW) {
                    name("<b><green>Terug</green></b>".asMini())
                    description(listOf(
                        "".asMini(),
                        "<gray>Ga terug naar het hoofdmenu.".asMini()
                    ))
                    slots(36)
                    onClick { event ->
                        event.isCancelled = true
                        TournamentListMenu(TournamentManager()).open(player)
                    }
                }
            }
            .build()

        gui.open(player)
    }
}