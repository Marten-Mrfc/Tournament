package dev.marten_mrfcyt.tournament

import com.earth2me.essentials.Essentials
import dev.marten_mrfcyt.tournament.rewards.RewardsManager
import dev.marten_mrfcyt.tournament.tournaments.PlayerProgress
import dev.marten_mrfcyt.tournament.tournaments.TournamentEndChecker
import dev.marten_mrfcyt.tournament.tournaments.TournamentHandler
import dev.marten_mrfcyt.tournament.tournaments.TournamentManager
import mlib.api.architecture.KotlinPlugin
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.event.Listener

class Tournament : KotlinPlugin() {
    companion object {
        lateinit var instance: Tournament
        lateinit var essentials: Essentials
    }

    private lateinit var tournamentEndChecker: TournamentEndChecker

    /**
     * Create a namespaced key for this plugin
     */
    fun namespacedKey(key: String): NamespacedKey {
        return NamespacedKey(this, key)
    }

    override fun onEnable() {
        logger.info("-------------------------------")
        logger.info("--- Tournaments is starting ---")
        instance = this
        super.onEnable()
        config.options().copyDefaults()
        saveDefaultConfig()
        essentials = (server.pluginManager.getPlugin("Essentials") as Essentials?)!!

        // Initialize the rewards manager
        try {
            // Initialize the rewards manager
            logger.info("Loading rewards data...")
            RewardsManager.getInstance().loadRewards()
        } catch (e: IllegalStateException) {
            logger.warning("Failed to load rewards: ${e.message}")
            logger.warning("Will try again later when KingdomCraft is fully loaded")
            // Schedule a delayed task to try loading rewards again
            Bukkit.getScheduler().runTaskLater(this, Runnable {
                try {
                    RewardsManager.getInstance().loadRewards()
                    logger.info("Rewards loaded successfully on retry")
                } catch (e: Exception) {
                    logger.severe("Failed to load rewards on retry: ${e.message}")
                }
            }, 100L) // Wait 5 seconds (100 ticks) before retrying
        }

        logger.info("Registering commands")
        tournamentCommands()
        logger.info("Commands registered successfully!")
        logger.info("Registering events")
        registerEvents(
            TournamentHandler(TournamentManager()),
        )
        logger.info("Loaded ${TournamentManager().getTournaments().size} tournaments")

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, Runnable {
            TournamentHandler.locations.clear()
        }, 100, 1728000)

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, Runnable {
            RewardsManager.getInstance().saveRewards()
            PlayerProgress.getInstance().saveProgressToFile()
        }, 0, 20 * 60 * 5)
        logger.info("Starting TournamentEndChecker")
        tournamentEndChecker = TournamentEndChecker(TournamentManager())
        tournamentEndChecker.startChecking()
        logger.info("TournamentEndChecker started successfully!")
        logger.info("--- Tournaments has started ---")
        logger.info("-------------------------------")
    }

    override fun onDisable() {
        logger.info("Saving rewards data...")
        RewardsManager.getInstance().saveRewards()

        // Save player progress
        PlayerProgress.getInstance().saveProgressToFileSync()
        logger.info("Tournament data saved successfully")

        tournamentEndChecker.stopChecking()
        logger.info("Tournaments has stopped")
    }

    private fun registerEvents(vararg listeners: Listener) {
        val pluginManager = Bukkit.getPluginManager()
        listeners.forEach { listener ->
            pluginManager.registerEvents(listener, this)
        }
        logger.info("${listeners.size} events registered successfully!")
    }
}