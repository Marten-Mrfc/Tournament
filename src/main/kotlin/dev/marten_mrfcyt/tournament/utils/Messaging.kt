package dev.marten_mrfcyt.tournament.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

private val mm = MiniMessage.builder().build()

fun String.asMini(player: Player? = null): Component {
    return mm.deserialize("<!i>$this")
}

fun CommandSender.sendMini(message: String, player: Player? = null) {
    sendMessage(message.asMini())
}

fun CommandSender.error(message: String, player: Player? = null) {
    val formattedMessage = "<red><bold>Error</bold><gray> | <white> $message"
    sendMessage(formattedMessage.asMini())
}

fun CommandSender.message(message: String, player: Player? = null) {
    val formattedMessage = "<gold><bold>Tournaments</bold><gray> | <white> $message"
    sendMessage(formattedMessage.asMini())
}