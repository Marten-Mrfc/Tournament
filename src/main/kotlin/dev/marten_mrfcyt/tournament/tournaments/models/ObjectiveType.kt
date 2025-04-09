package dev.marten_mrfcyt.tournament.tournaments.models

enum class ObjectiveType {
    MINE_BLOCK,
    KILL_ENTITY;

    companion object {
        fun fromString(value: String): ObjectiveType {
            return when (value.lowercase()) {
                "mineblock" -> MINE_BLOCK
                "killentity" -> KILL_ENTITY
                else -> throw IllegalArgumentException("Unknown objective type: $value")
            }
        }

        fun toString(type: ObjectiveType): String {
            return when (type) {
                MINE_BLOCK -> "mineblock"
                KILL_ENTITY -> "killentity"
            }
        }
    }
}