package cc.makin.coinmonitor.discord

import cc.makin.coinmonitor.CoinResult
import cc.makin.coinmonitor.replayState
import cc.makin.coinmonitor.format
import dev.kord.common.entity.ActivityType
import dev.kord.common.entity.DiscordBotActivity
import dev.kord.common.entity.PresenceStatus
import dev.kord.core.gateway.MasterGateway
import dev.kord.gateway.UpdateStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.withTimeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

private val logger: Logger = LoggerFactory.getLogger("DiscordCoinAsStatus")

@FlowPreview
@ExperimentalTime
suspend fun StateFlow<CoinResult>.representAsStatus(gateway: MasterGateway) =
    this
        .sample(Duration.seconds(15))
        .replayState()
        .map { this.value.toUpdateStatus() }
        .flowOn(Dispatchers.IO)
        .conflate()
        .collect {
            withTimeout(Duration.seconds(10)) {
                runCatching {
                    gateway.sendAll(it)
                }.onFailure { logger.error("Failed to update status!", it) }
            }
        }

private fun CoinResult.toUpdateStatus() =
    UpdateStatus(
        status = PresenceStatus.Online,
        afk = false,
        activities = when (this) {
            is CoinResult.Ok -> listOf(this.toDiscordActivity())
            is CoinResult.Error -> emptyList()
        },
        since = null,
    )

private fun CoinResult.Ok.toDiscordActivity() =
    DiscordBotActivity("${this.name} " + this.price.format(), ActivityType.Game)
