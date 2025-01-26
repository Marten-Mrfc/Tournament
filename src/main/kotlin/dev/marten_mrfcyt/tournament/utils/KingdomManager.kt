package dev.marten_mrfcyt.tournament.utils

import com.gufli.kingdomcraft.api.KingdomCraftProvider
import java.util.UUID

    fun getKingdoms(): List<String> {
        return KingdomCraftProvider.get().kingdoms.map { it.name }
    }
    fun getKingdomMembers(kingdom: String): List<UUID> {
        return KingdomCraftProvider.get().kingdoms.find { it.name == kingdom }?.members?.map { it.key } ?: listOf()
    }