package dev.marten_mrfcyt.tournament.tournaments.models

import org.bukkit.inventory.Inventory

data class Tournament(
    val name: String,
    val description: String,
    val endTime: String,
    val objective: TournamentObjective,
    val target: TournamentTarget,
    val inventory: Inventory? = null,
    val levels: Int? = null
) {
    constructor(
        name: String,
        description: String,
        endTime: String,
        objectiveString: String,
        targetString: String,
        inventory: Inventory? = null,
        levels: Int? = null
    ) : this(
        name,
        description,
        endTime,
        TournamentObjective.fromString(objectiveString),
        TournamentTarget.fromString(targetString),
        inventory,
        levels
    )
}