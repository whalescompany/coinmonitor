package cc.makin.coinmonitor.cli

import cc.makin.coinmonitor.*
import cc.makin.coinmonitor.discord.discordChannelAthInformerAsync
import cc.makin.coinmonitor.discord.discordChannelIdOfCowInformer
import cc.makin.coinmonitor.discord.representAsStatus
import cc.makin.coinmonitor.discord.liveDiscordBanner
import cc.makin.coinmonitor.discord.render
import cc.makin.coinmonitor.idofcow.ArcgisDataSource
import cc.makin.coinmonitor.idofcow.IdOfCowStats
import cc.makin.coinmonitor.telegram.idOfCowStatsCommand
import cc.makin.coinmonitor.telegram.telegramAthInformer
import cc.makin.coinmonitor.telegram.telegramIdOfCowInformer
import cc.makin.coinmonitor.tradingview.KtorTradingViewWs
import cc.makin.coinmonitor.tradingview.TradingView
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.entities.ChatId
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.event.gateway.ReadyEvent
import io.ktor.client.HttpClient
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

private val logger = LoggerFactory.getLogger("CoinMonitor")

private val DISCORD_NOTIFICATIONS_CHANNELS_ID = listOf(
    Snowflake(1155743628778541056), // hustlers university - #asdf
)
private val TELEGRAM_NOTIFICATION_CHANNELS_ID = listOf<ChatId>()

private val DISCORD_BANNERS_CHANNELS_ID = listOf(
    Snowflake(1155743628778541056), // hustlers university - #asdf
)

// SPORO troszkie sie narobilo Xd
@ExperimentalStdlibApi
@ExperimentalCoroutinesApi
@FlowPreview
@ExperimentalTime
fun main() = runBlocking(CoroutineName("Main")) {
    val discordToken = System.getenv("DISCORD_TOKEN")
    val telegramToken = System.getenv("TELEGRAM_TOKEN")
    if (discordToken.isNullOrEmpty() && telegramToken.isNullOrEmpty()) {
        logger.error("DISCORD_TOKEN or TELEGRAM_TOKEN environment variable not found.")
        exitProcess(1)
    } else {
        runApp(discordToken, telegramToken)
    }
}

data class DataSources(
    val idOfCowStatsFlow: StateFlow<IdOfCowStats>,
    val coinsFlow: List<StateFlow<CoinResult>>,
    val mainCoinFlow: StateFlow<CoinResult>,
)

@ExperimentalStdlibApi
@FlowPreview
@ExperimentalCoroutinesApi
@ExperimentalTime
private suspend fun createIdOfCowStatsFlow(httpClient: HttpClient, scope: CoroutineScope) =
    ArcgisDataSource(httpClient)
        .let<ArcgisDataSource, FlowProvider<Result<IdOfCowStats>>> { dataSource ->
            {
                flow {
                    emit(dataSource.getBasicIdOfCowStats())
                }
            }
        }
        .repeatable(1.minutes, scope = scope)
        .invoke(scope)
        .mapNotNull { it.logResult().getOrNull() }
        .stateIn(scope = scope)

@ExperimentalStdlibApi
@FlowPreview
@ExperimentalCoroutinesApi
@ExperimentalTime
private suspend fun createDataSources(scope: CoroutineScope): DataSources {
    val httpClient = HttpClient() {
        install(UserAgent) {
            agent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/95.0.4638.69 Safari/537.36"
        }
        install(ContentNegotiation) {
            json()
        }
        install(WebSockets)
    }

    val idOfCowStatsFlow = createIdOfCowStatsFlow(httpClient, scope)

    val tradingViewWs = KtorTradingViewWs.start(httpClient)
        .stateIn(scope = scope)
    val coinsOffline = getMonitorableCoinsXd(tradingViewWs)
    val coinsFlows = coinsOffline
        .map { offlineCoin -> offlineCoin.getOnline(scope = scope) }
        .map { flow -> flow.logResult() }
        .map { flow -> flow.stateIn(scope = scope) }
    val mainCoinFlow = coinsFlows.first()

    return DataSources(
        idOfCowStatsFlow,
        coinsFlows,
        mainCoinFlow,
    )
}

@ExperimentalStdlibApi
@FlowPreview
@ExperimentalCoroutinesApi
@ExperimentalTime
private suspend fun runApp(discordToken: String, telegramToken: String?) = coroutineScope {
    logger.info("Starting coin monitor...")

    val dataSources = createDataSources(scope = this)

    val kord = Kord(discordToken)
    launch(CoroutineName("KordConnection")) { kord.login() }
    kord.events.filterIsInstance<ReadyEvent>().take(1).collect()

    val telegramClient = if (telegramToken != null) {
        val telegramClient = bot {
            token = telegramToken
            dispatch {
                idOfCowStatsCommand(dataSources.idOfCowStatsFlow)
            }
        }
        launch(Dispatchers.IO + CoroutineName("TelegramPolling")) {
            telegramClient.startPolling()
        }
        telegramClient
    } else {
        null
    }

    launch(CoroutineName("DiscordBanner")) {
        val banner = liveDiscordBanner(
            name = "ceny shituw",
            coins = dataSources.coinsFlow,
            idOfCowStats = dataSources.idOfCowStatsFlow,
        )
        DISCORD_BANNERS_CHANNELS_ID.forEach { channelId ->
            launch(CoroutineName("DiscordBannerRenderer #${channelId}")) {
                banner.render(channelId, kord.rest.channel)
            }
        }
    }

    launch(CoroutineName("DiscordStatus")) {
        dataSources.mainCoinFlow
            .representAsStatus(gateway = kord.gateway)
    }

    launch(CoroutineName("AthInformer")) {
        val informers = listOfNotNull(
            discordChannelAthInformerAsync(scope = this, DISCORD_NOTIFICATIONS_CHANNELS_ID, kord.rest.channel),
            telegramClient?.let { telegramAthInformer(it, TELEGRAM_NOTIFICATION_CHANNELS_ID) },
        )
        monitorAth(dataSources.mainCoinFlow) { previous, current ->
            informers.forEach { launch { it.invoke(previous, current) } }
        }
    }

    launch(CoroutineName("IdOfCowInformer")) {
        val informers = listOfNotNull(
            discordChannelIdOfCowInformer(kord.rest.channel, DISCORD_NOTIFICATIONS_CHANNELS_ID),
            telegramClient?.let { telegramIdOfCowInformer(it, TELEGRAM_NOTIFICATION_CHANNELS_ID) },
        )

        dataSources.idOfCowStatsFlow
            .persistentDiff(
                fileName = "idofcowstats.txt",
                deserialize = { IdOfCowStats(zakazeniaDzienne = it.toIntOrNull() ?: 0) },
                serialize = { it.zakazeniaDzienne.toString() },
                scope = this,
            )
            .collect { diff ->
                informers.forEach { informer ->
                    runCatching {
                        informer.invoke(diff)
                    }.onFailure { th -> logger.error("Could not notify about new id of cow stats.", th) }
                }
            }
    }

    logger.info("Bot loaded successfully")
}

@FlowPreview
@ExperimentalCoroutinesApi
@ExperimentalTime
private suspend fun getMonitorableCoinsXd(
    tradingViewWs: StateFlow<TradingView>,
) = listOf(
    Triple("BTC/USD", "BITSTAMP:BTCUSD", CURRENCY_USD),
    Triple("ETH/USD", "BITSTAMP:ETHUSD", CURRENCY_USD),
    Triple("LTC/USD", "COINBASE:LTCUSD", CURRENCY_USD),
    Triple("USD/PLN", "FX_IDC:USDPLN", CURRENCY_PLN)
)
    .map { (name, symbol, currency) ->
        OfflineCoin(name) {
            tradingViewWs
                .map { it.getPrice(symbol) }
                .flattenConcat()
                .map { priceValue -> Result.success(Price(priceValue, currency)) }
        }
    }

private fun Result<IdOfCowStats>.logResult() =
    this.onFailure { logger.error("Failed to fetch id of cow stats", it) }

private fun CoinResultFlow.logResult() =
    this.onEach { it.logResult() }

private fun CoinResult.logResult() = when (this) {
    is CoinResult.Error -> logger.error("Failed to fetch coin '$name' online data. Error: $error")
    is CoinResult.Ok -> logger.debug("Updated coin '$name' online data successfully.")
}
