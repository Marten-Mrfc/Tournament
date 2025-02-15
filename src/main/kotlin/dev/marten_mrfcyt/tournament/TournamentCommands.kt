package dev.marten_mrfcyt.tournament

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import dev.marten_mrfcyt.tournament.menus.RewardsMenu
import dev.marten_mrfcyt.tournament.menus.TournamentListMenu
import dev.marten_mrfcyt.tournament.tournaments.PlayerProgress
import dev.marten_mrfcyt.tournament.tournaments.Tournament
import dev.marten_mrfcyt.tournament.tournaments.TournamentManager
import dev.marten_mrfcyt.tournament.utils.*
import lirand.api.dsl.command.builders.LiteralDSLBuilder
import lirand.api.dsl.command.builders.command
import lirand.api.extensions.inventory.meta
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun Plugin.tournamentCommands() = command("tournament") {
    setup()
    executes { TournamentListMenu(TournamentManager()).open(source as Player) }
}
@Suppress("UnstableApiUsage")
private fun LiteralDSLBuilder.setup() {
    val tournamentManager = TournamentManager()
    literal("info") {
        requiresPermissions("tournament.info")
        executes {
            val commands = listOf(
                "/tournament: Open menu with all the current tournaments.",
                "/tournament add <gray><name> <description> <hourly,daily,weekly> <objective> <player/kingdom> <levels><white>: Add a tournament with levels",
                "/tournament add <gray><name> <description> <hourly,daily,weekly> <objective> <player/kingdom><white>: Add a tournament without levels",
                "/tournament finish <gray><id><white>: Finish a tournament, give rewards.",
                "/tournament list: List all objectives with their id",
                "/tournament delete <gray><id><white>: Delete a tournament, no rewards"
            )
            source.message("<gray>---<reset> <gold>Tournaments<reset> <gray>---")
            source.message("<white>Version: <gold>${plugin.pluginMeta.version}")
            source.message("<white>Author: <gold>${plugin.pluginMeta.authors}")
            source.message("<gray>---<reset> <gold>Commands<reset> <gray>---")
            commands.forEach { command -> source.sendMini("<green>* <white>$command") }
        }
    }
    literal("claimreward") {
        executes {
            val player = source as? Player ?: return@executes
            RewardsMenu().open(player)
        }
    }
    literal("add") {
        requiresPermissions("tournament.add")
        argument("name", StringArgumentType.string()) {
            argument("description", StringArgumentType.string()) {
                argument("type", StringArgumentType.word()) {
                    suggests { builder ->
                        builder.suggest("hourly").suggest("daily").suggest("weekly").buildFuture()
                    }
                    argument("objectiveType", StringArgumentType.word()) {
                        suggests { builder ->
                            builder.suggest("mineblock").suggest("killentity").buildFuture()
                        }
                        argument("objective", StringArgumentType.string()) {
                            suggests { builder ->
                                when (getArgument("objectiveType", String::class.java)) {
                                    "mineblock" -> Material.entries.forEach { builder.suggest(it.name) }
                                    "killentity" -> EntityType.entries.forEach { builder.suggest(it.name) }
                                }
                                builder.buildFuture()
                            }
                            argument("target", StringArgumentType.word()) {
                                suggests { builder ->
                                    builder.suggest("player").suggest("provincie").buildFuture()
                                }
                                argument("levels", IntegerArgumentType.integer()) {
                                    executes {
                                        val name = getArgument<String>("name")
                                        val description = getArgument<String>("description")
                                        val type = getArgument<String>("type")
                                        val objective = "${getArgument<String>("objectiveType")}:${getArgument<String>("objective")}"
                                        val target = getArgument<String>("target")
                                        val levels = getArgument<Int>("levels")
                                        val source = source as? Player ?: return@executes
                                        val tournament = Tournament(name, description, calculateEndTime(type), objective, target, source.inventory, levels)
                                        tournamentManager.saveTournament(tournament)
                                        source.message("<green>Adding tournament with levels")
                                    }
                                }
                                executes {
                                    val name = getArgument<String>("name")
                                    val description = getArgument<String>("description")
                                    val objective = "${getArgument<String>("objectiveType")}:${getArgument<String>("objective")}"
                                    val type = getArgument<String>("type")
                                    val target = getArgument<String>("target")
                                    val source = source as? Player ?: return@executes
                                    val tournament = Tournament(name, description, calculateEndTime(type), objective, target, source.inventory)
                                    tournamentManager.saveTournament(tournament)
                                    source.message("<green>Adding tournament without levels")
                                }
                            }
                            executes { source.error("Specify the target (player/kingdom) for the tournament.") }
                        }
                        executes { source.error("Specify the objective value for the tournament.") }
                    }
                    executes { source.error("Specify the objective type for the tournament.") }
                }
                executes { source.error("Specify the type (hourly/daily/weekly) for the tournament.") }
            }
        }
    }
    literal("finish") {
        requiresPermissions("tournament.finish")
        argument("name", StringArgumentType.word()) {
            suggests { builder ->
                tournamentManager.getTournamentsNames().forEach { builder.suggest(it) }
                builder.buildFuture()
            }
            executes {
                source.message("<green>Finishing tournament")
                PlayerProgress.getInstance().giveRewards(getArgument("name"))
                source.message("<green>Removing tournament")
                tournamentManager.removeTournament(getArgument("name"))
            }
        }
    }
    literal("list") {
        requiresPermissions("tournament.list")
        executes {
            source.message("<gray>---<reset> <gold>Listing all tournaments<reset> <gray>---")
            tournamentManager.getTournaments().forEach { source.message("<green>* <white>${it.name}, ${it.description}, ${it.objective}, ${it.target}, ${it.levels}, ${it.endTime}") }
        }
    }
    literal("remove") {
        requiresPermissions("tournament.remove")
        argument("name", StringArgumentType.word()) {
            suggests { builder ->
                tournamentManager.getTournamentsNames().forEach { builder.suggest(it) }
                builder.buildFuture()
            }
            executes {
                source.message("<green>Removing tournament")
                tournamentManager.removeTournament(getArgument("name"))
            }
        }
    }
}

private fun calculateEndTime(duration: String): String {
    val now = LocalDateTime.now()
    val endTime = when (duration) {
        "hourly" -> now.plusHours(1)
        "daily" -> now.plusDays(1)
        "weekly" -> now.plusWeeks(1)
        else -> now
    }
    return endTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}