package cc.makin.coinmonitor.telegram

import cc.makin.coinmonitor.idofcow.IdOfCowStats
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import kotlinx.coroutines.flow.StateFlow

fun Dispatcher.idOfCowStatsCommand(idOfCowStatsFlow: StateFlow<IdOfCowStats>) {
    command("idofcow") {
        println("id of cow")

        bot.sendMessage(
            ChatId.fromId(message.chat.id),
            "*zakazenia dzienne*: " + idOfCowStatsFlow.value.zakazeniaDzienne,
            parseMode = ParseMode.MARKDOWN,
        )
    }
}

fun telegramIdOfCowInformer(telegramBot: Bot, chatIds: List<ChatId>): (Pair<IdOfCowStats, IdOfCowStats>) -> Unit =
    { (previous, current) ->
        val message = """
            *ID OF COW UPDATED*: ${current.zakazeniaDzienne}
            *previous*: ${previous.zakazeniaDzienne}
        """.trimIndent()

        chatIds.forEach { chatId -> telegramBot.sendMessage(chatId, message, parseMode = ParseMode.MARKDOWN) }
    }
