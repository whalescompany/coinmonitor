package cc.makin.coinmonitor.telegram

import cc.makin.coinmonitor.Ath
import cc.makin.coinmonitor.format
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode

fun telegramAthInformer(telegramBot: Bot, chatsIds: List<ChatId>) = { previous: Ath, current: Ath ->
    val message = createTelegramAthMessage(previous, current)
    chatsIds.forEach { chatId ->
        telegramBot.sendMessage(
            chatId,
            message,
            parseMode = ParseMode.MARKDOWN,
        )
    }
}

private fun createTelegramAthMessage(
    previous: Ath,
    current: Ath,
) = """
        *ATH LRC* ;)
        
        *previous*: ${previous.price.format()}
        *current*: ${current.price.format()}
    """.trimIndent()
