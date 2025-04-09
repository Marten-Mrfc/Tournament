package dev.marten_mrfcyt.tournament

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import dev.marten_mrfcyt.tournament.menus.RewardsMenu
import dev.marten_mrfcyt.tournament.menus.TournamentListMenu
import dev.marten_mrfcyt.tournament.tournaments.*
import dev.marten_mrfcyt.tournament.tournaments.models.ObjectiveType
import dev.marten_mrfcyt.tournament.tournaments.models.Tournament
import dev.marten_mrfcyt.tournament.tournaments.models.TournamentObjective
import dev.marten_mrfcyt.tournament.tournaments.models.TournamentTarget
import mlib.api.commands.builders.LiteralDSLBuilder
import mlib.api.commands.builders.command
import mlib.api.utilities.error
import mlib.api.utilities.message
import mlib.api.utilities.sendMini
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
                "/tournament add <gray><name> <description> <hourly,daily,weekly> <MINE_BLOCK/KILL_ENTITY> <target> <PLAYER/PROVINCE> <levels><white>: Add a tournament with levels",
                "/tournament add <gray><name> <description> <hourly,daily,weekly> <MINE_BLOCK/KILL_ENTITY> <target> <PLAYER/PROVINCE><white>: Add a tournament without levels",
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
                argument("duration", StringArgumentType.word()) {
                    suggests { builder ->
                        builder.suggest("hourly").suggest("daily").suggest("weekly").buildFuture()
                    }
                    argument("objectiveType", StringArgumentType.word()) {
                        suggests { builder ->
                            // Use enum values directly for better type safety
                            ObjectiveType.entries.forEach { builder.suggest(it.name) }
                            builder.buildFuture()
                        }
                        argument("target", StringArgumentType.string()) {
                            suggests { builder ->
                                when (getArgument("objectiveType", String::class.java)) {
                                    ObjectiveType.MINE_BLOCK.name -> Material.entries.forEach { builder.suggest(it.name) }
                                    ObjectiveType.KILL_ENTITY.name -> EntityType.entries.forEach { builder.suggest(it.name) }
                                }
                                builder.buildFuture()
                            }
                            argument("tournamentTarget", StringArgumentType.word()) {
                                suggests { builder ->
                                    // Use enum values directly for better type safety
                                    TournamentTarget.entries.forEach { builder.suggest(it.name) }
                                    builder.buildFuture()
                                }
                                argument("levels", IntegerArgumentType.integer()) {
                                    executes {
                                        val player = source as? Player ?: return@executes

                                        try {
                                            val name = getArgument<String>("name")
                                            val description = getArgument<String>("description")
                                            val duration = getArgument<String>("duration")

                                            // Parse objective type from enum
                                            val objectiveTypeStr = getArgument<String>("objectiveType")
                                            val objectiveType = ObjectiveType.valueOf(objectiveTypeStr)

                                            val targetStr = getArgument<String>("target")
                                            val tournamentTargetStr = getArgument<String>("tournamentTarget")
                                            val tournamentTarget = TournamentTarget.valueOf(tournamentTargetStr)
                                            val levels = getArgument<Int>("levels")

                                            // Create objective using the new data class
                                            val objective = TournamentObjective(objectiveType, targetStr)

                                            val tournament = Tournament(
                                                name,
                                                description,
                                                calculateEndTime(duration),
                                                objective,
                                                tournamentTarget,
                                                player.inventory,
                                                levels
                                            )

                                            tournamentManager.saveTournament(tournament)
                                            player.message("<green>Added tournament with levels: <white>$name")
                                        } catch (e: IllegalArgumentException) {
                                            player.error("<red>Invalid argument: ${e.message}")
                                        }
                                    }
                                }
                                executes {
                                    val player = source as? Player ?: return@executes

                                    try {
                                        val name = getArgument<String>("name")
                                        val description = getArgument<String>("description")
                                        val duration = getArgument<String>("duration")

                                        // Parse objective type from enum
                                        val objectiveTypeStr = getArgument<String>("objectiveType")
                                        val objectiveType = ObjectiveType.valueOf(objectiveTypeStr)

                                        val targetStr = getArgument<String>("target")
                                        val tournamentTargetStr = getArgument<String>("tournamentTarget")
                                        val tournamentTarget = TournamentTarget.valueOf(tournamentTargetStr)

                                        // Create objective using the new data class
                                        val objective = TournamentObjective(objectiveType, targetStr)

                                        val tournament = Tournament(
                                            name,
                                            description,
                                            calculateEndTime(duration),
                                            objective,
                                            tournamentTarget,
                                            player.inventory
                                        )

                                        tournamentManager.saveTournament(tournament)
                                        player.message("<green>Added tournament without levels: <white>$name")
                                    } catch (e: IllegalArgumentException) {
                                        player.error("<red>Invalid argument: ${e.message}")
                                    }
                                }
                            }
                            executes { source.error("Specify the tournament target (PLAYER/PROVINCE).") }
                        }
                        executes { source.error("Specify the target value for the chosen objective type.") }
                    }
                    executes { source.error("Specify the objective type (MINE_BLOCK/KILL_ENTITY).") }
                }
                executes { source.error("Specify the duration (hourly/daily/weekly) for the tournament.") }
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
                source.message("<green>Finishing tournament...")
                PlayerProgress.getInstance().giveRewards(getArgument("name"))
                source.message("<green>Removing tournament...")
                tournamentManager.removeTournament(getArgument("name"))
                source.message("<green>Tournament finished successfully!")
            }
        }
    }

    literal("list") {
        requiresPermissions("tournament.list")
        executes {
            source.message("<gray>---<reset> <gold>Listing all tournaments<reset> <gray>---")
            val tournaments = tournamentManager.getTournaments()

            if (tournaments.isEmpty()) {
                source.message("<yellow>No active tournaments found.")
            } else {
                tournaments.forEach { tournament ->
                    val objectiveName = tournament.objective.getDisplayName()
                    source.message("<green>* <white>${tournament.name}")
                    source.message("  <gray>Description: <white>${tournament.description}")
                    source.message("  <gray>Objective: <white>${tournament.objective.type} - $objectiveName")
                    source.message("  <gray>Target: <white>${tournament.target}")
                    source.message("  <gray>Levels: <white>${tournament.levels ?: "None"}")
                    source.message("  <gray>Ends at: <white>${tournament.endTime}")
                }
            }
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
                val tournamentId = getArgument<String>("name")
                source.message("<green>Removing tournament: <white>$tournamentId")
                tournamentManager.removeTournament(tournamentId)
                source.message("<green>Tournament removed successfully!")
            }
        }
    }
}

private fun calculateEndTime(duration: String): String {
    val now = LocalDateTime.now()
    val endTime = when (duration.lowercase()) {
        "hourly" -> now.plusHours(1)
        "daily" -> now.plusDays(1)
        "weekly" -> now.plusWeeks(1)
        else -> now
    }
    return endTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}