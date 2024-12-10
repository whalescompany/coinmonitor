package cc.makin.coinmonitor.discord

import cc.makin.coinmonitor.Ath
import cc.makin.coinmonitor.format
import dev.kord.common.entity.Snowflake
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.service.ChannelService
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun discordChannelAthInformerAsync(
    scope: CoroutineScope,
    channelsIds: List<Snowflake>,
    channelService: ChannelService,
): (previousAth: Ath, nextAth: Ath) -> Unit = { previousAth, nextAth ->
    channelsIds.forEach { channelId ->
        scope.launch(CoroutineName("DiscordChannelAthInformer")) {
            channelService.createMessage(channelId) {
                embeds?.add(discordAthEmbed(previousAth, nextAth))
            }
        }
    }
}

fun discordAthEmbed(previousAth: Ath, nextAth: Ath) = EmbedBuilder().apply {
    title = "ath"

    fields = mutableListOf(
        EmbedBuilder.Field().apply {
            name = "previous"
            value = previousAth.price.format()
            inline = true
        },
        EmbedBuilder.Field().apply {
            name = "next"
            value = nextAth.price.format()
            inline = true
        },
    )
}
