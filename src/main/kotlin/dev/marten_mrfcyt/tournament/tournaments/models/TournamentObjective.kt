package dev.marten_mrfcyt.tournament.tournaments.models

import org.bukkit.Material
import org.bukkit.entity.EntityType

data class TournamentObjective(
    val type: ObjectiveType,
    val target: String
) {
    companion object {
        fun fromString(value: String): TournamentObjective {
            val parts = value.split(":")
            if (parts.size != 2) {
                throw IllegalArgumentException("Invalid objective format: $value")
            }

            val type = ObjectiveType.fromString(parts[0])
            val target = parts[1]

            return TournamentObjective(type, target)
        }

        fun toString(objective: TournamentObjective): String {
            return "${ObjectiveType.toString(objective.type)}:${objective.target}"
        }
    }

    fun getDisplayName(): String {
        return when (type) {
            ObjectiveType.MINE_BLOCK -> try {
                Material.valueOf(target).name.lowercase().replace("_", " ")
            } catch (_: IllegalArgumentException) {
                target
            }
            ObjectiveType.KILL_ENTITY -> try {
                EntityType.valueOf(target).name.lowercase().replace("_", " ")
            } catch (_: IllegalArgumentException) {
                target
            }
        }
    }

    fun matches(other: String): Boolean {
        return toString(this) == other
    }
}