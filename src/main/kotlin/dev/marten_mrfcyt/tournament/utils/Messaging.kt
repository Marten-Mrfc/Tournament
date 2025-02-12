package dev.marten_mrfcyt.tournament.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender

private val mm = MiniMessage.builder().build()

fun String.asMini(): Component {
    return mm.deserialize("<!i>$this")
}

fun CommandSender.sendMini(message: String) {
    sendMessage(message.asMini())
}

fun CommandSender.error(message: String) {
    val formattedMessage = "<red><bold>Error</bold><gray> | <white> $message"
    sendMessage(formattedMessage.asMini())
}

fun CommandSender.message(message: String) {
    val formattedMessage = "<gold><bold>Tournaments</bold><gray> | <white> $message"
    sendMessage(formattedMessage.asMini())
}