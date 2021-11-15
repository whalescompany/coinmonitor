package cc.makin.coinmonitor.discord

import cc.makin.coinmonitor.idofcow.IdOfCowStats
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.optional
import dev.kord.core.Kord
import dev.kord.rest.json.request.EmbedFieldRequest
import dev.kord.rest.json.request.EmbedRequest
import dev.kord.rest.json.request.MessageCreateRequest
import dev.kord.rest.json.request.MultipartMessageCreateRequest
import dev.kord.rest.service.ChannelService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun CoroutineScope.discordChannelIdOfCowInformer(
    channelService: ChannelService,
    channels: List<Snowflake>,
): (Pair<IdOfCowStats, IdOfCowStats>) -> Unit =
    { (previous, current) ->
        val message = createIdOfCowDiscordMessage(previous, current)

        channels.forEach { launch { channelService.createMessage(it, message) } }
    }

private fun createIdOfCowDiscordMessage(
    previous: IdOfCowStats,
    current: IdOfCowStats,
) = MultipartMessageCreateRequest(
    request = MessageCreateRequest(
        embeds = listOf(
            EmbedRequest(
                title = "id krowy update".optional(),
                fields = listOf(
                    EmbedFieldRequest(name = "previous", value = previous.zakazeniaDzienne.toString()),
                    EmbedFieldRequest(name = "current", value = current.zakazeniaDzienne.toString()),
                ).optional(),
            )
        ).optional(),
    )
)
