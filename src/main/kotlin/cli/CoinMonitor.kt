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
import cc.makin.coinmonitor.format
import cc.makin.coinmonitor.getOnline
import cc.makin.coinmonitor.idofcow.ArcgisDataSource
import cc.makin.coinmonitor.idofcow.IdOfCowStats
import cc.makin.coinmonitor.loadAth
import cc.makin.coinmonitor.persistentDiff
import cc.makin.coinmonitor.repeatable
import cc.makin.coinmonitor.savePriceLogPersistent
import cc.makin.coinmonitor.storeAth
import cc.makin.coinmonitor.telegram.idOfCowStatsCommand
import cc.makin.coinmonitor.tradingview.KtorTradingViewWs
import cc.makin.coinmonitor.tradingview.TradingView
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.channel
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.optional
import dev.kord.core.Kord
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.rest.json.request.EmbedFieldRequest
import dev.kord.rest.json.request.EmbedRequest
import dev.kord.rest.json.request.MessageCreateRequest
import dev.kord.rest.json.request.MultipartMessageCreateRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.UserAgent
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.websocket.WebSockets
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
import java.util.Currency
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

private val logger = LoggerFactory.getLogger("CoinMonitor")

private val DISCORD_NOTIFICATIONS_CHANNELS_ID = listOf(
    Snowflake(902208506604707840),
    Snowflake(905195804157968454),
)
private val TELEGRAM_NOTIFICATION_CHANNEL_ID = ChatId.fromId(-1001772220530)

// SPORO troszkie sie narobilo Xd
@ExperimentalStdlibApi
@ExperimentalCoroutinesApi
@FlowPreview
@ExperimentalTime
fun main() = runBlocking(CoroutineName("Main")) {
    val discordToken = System.getenv("DISCORD_TOKEN")
    val telegramToken = System.getenv("TELEGRAM_TOKEN")
    if (discordToken.isNullOrEmpty() || telegramToken.isNullOrEmpty()) {
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
        .repeatable(Duration.minutes(1), scope = scope)
        .invoke(scope)
        .mapNotNull { it.logResult().getOrNull() }
        .stateIn(scope = scope)

@ExperimentalStdlibApi
@FlowPreview
@ExperimentalCoroutinesApi
@ExperimentalTime
private suspend fun createDataSources(scope: CoroutineScope): DataSources {
    val httpClient = createHttpClient()

    val idOfCowStatsFlow = createIdOfCowStatsFlow(httpClient, scope)

    val tradingViewWs = KtorTradingViewWs.start(httpClient)
        .stateIn(scope = scope)
    val coinsOffline = getMonitorableCoinsXd(tradingViewWs, httpClient)
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
private suspend fun runApp(discordToken: String, telegramToken: String) = coroutineScope {
    logger.info("Starting coin monitor...")

    val dataSources = createDataSources(scope = this)

    val kord = Kord(discordToken)
    launch(CoroutineName("KordConnection")) { kord.login() }
    kord.events.filterIsInstance<ReadyEvent>().take(1).collect()

    val telegramBot = bot {
        token = telegramToken
        dispatch {
            channel {
                println("update: $update")
            }
            idOfCowStatsCommand(dataSources.idOfCowStatsFlow)
        }
    }

    launch(Dispatchers.IO + CoroutineName("TelegramPolling")) {
        telegramBot.startPolling()
    }

    launch(CoroutineName("Save price log")) {
        savePriceLogPersistent(dataSources.mainCoinFlow)
    }

    launch(CoroutineName("DiscordBanner")) {
        startLiveDiscordBanner(
            name = "ceny shituw",
            channelId = Snowflake(905195804157968454),
            channelService = kord.rest.channel,
            coins = dataSources.coinsFlow,
            idOfCowStats = dataSources.idOfCowStatsFlow,
        )
    }

    launch(CoroutineName("Status")) {
        dataSources.mainCoinFlow
            .representAsStatus(gateway = kord.gateway)
    }

    launch(CoroutineName("AthInformer")) {
        val informers = listOf(
            discordChannelAthInformerAsync(
                scope = this,
                DISCORD_NOTIFICATIONS_CHANNELS_ID,
                kord.rest.channel,
            ),
            telegramAthInformer(telegramBot),
        )
        startAthInformer(dataSources.mainCoinFlow) { previous, current ->
            informers.forEach { launch { it.invoke(previous, current) } }
        }
    }

    launch(CoroutineName("IdOfCowInformer")) {
        val informers = listOf(
            discordIdOfCowInformer(kord),
            telegramIdOfCowInformer(telegramBot)
        )

        dataSources.idOfCowStatsFlow
            .persistentDiff(
                fileName = "idofcowstats.txt",
                deserialize = { IdOfCowStats(zakazeniaDzienne = it.toIntOrNull() ?: 0) },
                serialize = { it.zakazeniaDzienne.toString() },
                scope = this,
            )
            .collect { diff ->
                informers.forEach { informer -> launch { informer.invoke(diff) } }
            }
    }

    logger.info("Bot loaded successfully")
}

private fun telegramAthInformer(telegramBot: Bot) = { previous: Ath, current: Ath ->
    telegramBot.sendMessage(
        TELEGRAM_NOTIFICATION_CHANNEL_ID,
        """
            *ATH LRC* ;)
            
            *previous*: ${previous.price.format()}
            *current*: ${current.price.format()}
        """.trimIndent(),
        parseMode = ParseMode.MARKDOWN,
    )
}

private fun CoroutineScope.discordIdOfCowInformer(kord: Kord): (Pair<IdOfCowStats, IdOfCowStats>) -> Unit =
    { (previous, current) ->
        val message = createIdOfCowDiscordMessage(previous, current)

        DISCORD_NOTIFICATIONS_CHANNELS_ID
            .forEach { launch { kord.rest.channel.createMessage(it, message) } }
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

private fun telegramIdOfCowInformer(telegramBot: Bot): (Pair<IdOfCowStats, IdOfCowStats>) -> Unit =
    { (previous, current) ->
        telegramBot.sendMessage(
            TELEGRAM_NOTIFICATION_CHANNEL_ID,
            """
                *ID OF COW UPDATED*: ${current.zakazeniaDzienne}
                *previous*: ${previous.zakazeniaDzienne}
            """.trimIndent(),
            parseMode = ParseMode.MARKDOWN,
        )
    }

@FlowPreview
@ExperimentalTime
private suspend fun startAthInformer(
    mainCoinFlow: StateFlow<CoinResult>,
    inform: (previous: Ath, current: Ath) -> Unit,
) {
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

            inform.invoke(previous, current)

            coroutineScope {
                launch {
                    runCatching {
                        storeAth("main_coin", current)
                    }.onFailure { th -> logger.error("Failed to store ath", th) }
                }
            }
        }
}

private fun createHttpClient() = HttpClient(CIO) {
    install(UserAgent) {
        agent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/95.0.4638.69 Safari/537.36"
    }
    install(JsonFeature)
    install(WebSockets)
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
        name = "GENZIE",
        PancakeswapCoin(id = "0x8c4c5bf2a4d327741dcbe51b41a50954213894a8")
            .getPriceProvider(httpClient),
    ),
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
