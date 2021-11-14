package cc.makin.coinmonitor.cli

import cc.makin.coinmonitor.Ath
import cc.makin.coinmonitor.AthUpdate
import cc.makin.coinmonitor.CoinResult
import cc.makin.coinmonitor.CoinResultFlow
import cc.makin.coinmonitor.FlowProvider
import cc.makin.coinmonitor.OfflineCoin
import cc.makin.coinmonitor.PancakeswapCoin
import cc.makin.coinmonitor.Price
import cc.makin.coinmonitor.ath
import cc.makin.coinmonitor.diff
import cc.makin.coinmonitor.discord.discordChannelAthInformerAsync
import cc.makin.coinmonitor.discord.representAsStatus
import cc.makin.coinmonitor.discord.startLiveDiscordBanner
import cc.makin.coinmonitor.getOnline
import cc.makin.coinmonitor.idofcow.ArcgisDataSource
import cc.makin.coinmonitor.idofcow.IdOfCowStats
import cc.makin.coinmonitor.loadAth
import cc.makin.coinmonitor.repeatable
import cc.makin.coinmonitor.savePriceLogPersistent
import cc.makin.coinmonitor.storeAth
import cc.makin.coinmonitor.tradingview.KtorTradingViewWs
import cc.makin.coinmonitor.tradingview.TradingView
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.UserAgent
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.websocket.WebSockets
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.Currency
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

private val logger = LoggerFactory.getLogger("CoinMonitor")

// SPORO troszkie sie narobilo Xd
@ExperimentalStdlibApi
@ExperimentalCoroutinesApi
@FlowPreview
@ExperimentalTime
fun main() = runBlocking(CoroutineName("Main")) {
    val botToken = System.getenv("BOT_TOKEN")
    if (botToken.isNullOrEmpty()) {
        logger.error("BOT_TOKEN environment variable not found.")
        exitProcess(1)
    } else {
        runApp(botToken)
    }
}

@ExperimentalStdlibApi
@FlowPreview
@ExperimentalCoroutinesApi
@ExperimentalTime
private suspend fun runApp(botToken: String) = coroutineScope {
    logger.info("Starting coin monitor...")

    val kord = Kord(botToken)
    val kordJob = async(CoroutineName("KordConnection")) { kord.login() }

    val httpClient = HttpClient(CIO) {
        install(UserAgent) {
            agent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/95.0.4638.69 Safari/537.36"
        }
        install(JsonFeature)
        install(WebSockets)
    }

    val idOfCowStatsFlow = ArcgisDataSource(httpClient)
        .let<ArcgisDataSource, FlowProvider<Result<IdOfCowStats>>> { dataSource ->
            {
                flow {
                    emit(dataSource.getBasicIdOfCowStats())
                }
            }
        }
        .repeatable(Duration.minutes(1), scope = this)
        .invoke(this)
        .mapNotNull { it.logResult().getOrNull() }
        .stateIn(scope = this)

    val tradingViewWs = KtorTradingViewWs.start(httpClient)
        .stateIn(scope = this)
    val coinsOffline = getMonitorableCoinsXd(tradingViewWs, httpClient)
    val coinsFlows = coinsOffline
        .map { offlineCoin -> offlineCoin.getOnline(scope = this) }
        .map { flow -> flow.logResult() }
        .map { flow -> flow.stateIn(scope = this) }
    val mainCoinFlow = coinsFlows.first()

    launch(CoroutineName("Save price log")) {
        savePriceLogPersistent(mainCoinFlow)
    }

    launch(CoroutineName("Banner")) {
        startLiveDiscordBanner(
            name = "ceny shituw",
            channelId = Snowflake(905195804157968454),
            channelService = kord.rest.channel,
            coins = coinsFlows,
            idOfCowStats = idOfCowStatsFlow,
        )
    }

    launch(CoroutineName("Status")) {
        mainCoinFlow
            .representAsStatus(gateway = kord.gateway)
    }

    launch(CoroutineName("AthInformer")) {
        val initialPreviousAthUsd = loadAth("main_coin")
            ?: 0.0
        val initialPreviousAth = Ath(Price(initialPreviousAthUsd, Currency.getInstance("USD")))

        mainCoinFlow
            .mapNotNull { it as? CoinResult.Ok }
            .ath(initialPreviousAth = initialPreviousAth)
            .debounce(Duration.seconds(5))
            .diff(initialPreviousValue = initialPreviousAth)
            .map { (previous, new) -> AthUpdate(previous, new) }
            .collect { (previous, current) ->
                logger.info("New ath! Previous: $previous, current: $current")
                discordChannelAthInformerAsync(
                    scope = this,
                    listOf(Snowflake(902208506604707840), Snowflake(905195804157968454)),
                    kord.rest.channel,
                ).invoke(previous, current)

                launch {
                    runCatching {
                        storeAth("main_coin", current)
                    }.onFailure { th -> logger.error("Failed to store ath", th) }
                }
            }
    }

    logger.info("Logging in")
    kordJob.join()
}

@FlowPreview
@ExperimentalCoroutinesApi
@ExperimentalTime
private suspend fun getMonitorableCoinsXd(
    tradingViewWs: StateFlow<TradingView>,
    httpClient: HttpClient,
) = listOf(
    OfflineCoin(
        name = "Loopring",
    ) {
        tradingViewWs
            .map { it.getPrice(symbol = "COINBASE:LRCUSD") }
            .flattenConcat()
    },
    OfflineCoin(
        name = "Gamestop",
    ) {
        tradingViewWs
            .map { it.getPrice(symbol = "NYSE:GME") }
            .flattenConcat()
    },
    OfflineCoin(
        name = "TESLA, INC",
    ) {
        tradingViewWs
            .map { it.getPrice(symbol = "NASDAQ:TSLA") }
            .flattenConcat()
    },
    OfflineCoin(
        name = "NAKD",
    ) {
        tradingViewWs
            .map { it.getPrice(symbol = "NASDAQ:NAKD") }
            .flattenConcat()
    },
    OfflineCoin(
        name = "Solana",
    ) {
        tradingViewWs
            .map { it.getPrice(symbol = "FTX:SOLUSD") }
            .flattenConcat()
    },
    OfflineCoin(
        name = "Dogebonk",
        PancakeswapCoin(id = "0xae2df9f730c54400934c06a17462c41c08a06ed8")
            .getPriceProvider(httpClient),
    ),
)

private fun Result<IdOfCowStats>.logResult() =
    this.onFailure { logger.error("Failed to fetch id of cow stats", it) }

private fun CoinResultFlow.logResult() =
    this.onEach { it.logResult() }

private fun CoinResult.logResult() = when (this) {
    is CoinResult.Error -> logger.error("Failed to fetch coin '$name' online data. Error: $error")
    is CoinResult.Ok -> logger.debug("Updated coin '$name' online data successfully.")
}
