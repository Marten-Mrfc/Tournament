package dev.marten_mrfcyt.tournament.utils

import com.gufli.kingdomcraft.api.KingdomCraftProvider
import com.gufli.kingdomcraft.api.domain.Kingdom
import org.bukkit.Bukkit

fun getKingdoms(): Set<Kingdom> {
    // Check if KingdomCraft is loaded before trying to access the API
    if (Bukkit.getPluginManager().getPlugin("KingdomCraft") == null) {
        throw IllegalStateException("KingdomCraft plugin is not loaded")
    }

    return try {
        KingdomCraftProvider.get().kingdoms
    } catch (e: IllegalStateException) {
        // Handle the case where the API isn't ready yet
        emptySet()
    }
}