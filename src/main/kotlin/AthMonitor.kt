package cc.makin.coinmonitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Currency
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

private val logger = LoggerFactory.getLogger("AthInformer")

data class Ath(
    val price: Price,
    // date etc...
)

data class AthUpdate(
    val previous: Ath,
    val current: Ath,
)

@FlowPreview
@ExperimentalTime
fun Flow<CoinResult.Ok>.ath(initialPreviousAth: Ath) = run {
    @Suppress("VariableUsage")
    var previousAth = initialPreviousAth
    this
        .filter { it.price > previousAth.price }
        .map { Ath(it.price) }
        .onEach { previousAth = it }
}

// todo przemontowac na persistentDiff
suspend fun storeAth(id: String, ath: Ath) = withContext(Dispatchers.IO) {
    val file = File("$id.ath")
    file.writeText(ath.price.value.toString() + "\n")
}

suspend fun loadAth(id: String): Double? = withContext(Dispatchers.IO) {
    val file = File("$id.ath")
    if (file.exists()) {
        val previousPrice = file.readText()
        previousPrice.toDouble()
    } else {
        null
    }
}

@FlowPreview
@ExperimentalTime
suspend fun monitorAth(
    mainCoinFlow: StateFlow<CoinResult>,
    onAth: (previous: Ath, current: Ath) -> Unit,
) {
    val initialPreviousAthUsd = loadAth("main_coin")
        ?: 0.0
    val initialPreviousAth = Ath(Price(initialPreviousAthUsd, Currency.getInstance("USD")))

    mainCoinFlow
        .mapNotNull { it as? CoinResult.Ok }
        .ath(initialPreviousAth = initialPreviousAth)
        .debounce(Duration.minutes(5))
        .diff(initialPreviousValue = initialPreviousAth)
        .map { (previous, new) -> AthUpdate(previous, new) }
        .collect { (previous, current) ->
            logger.info("New ath! Previous: $previous, current: $current")

            onAth.invoke(previous, current)

            coroutineScope {
                launch {
                    runCatching {
                        storeAth("main_coin", current)
                    }.onFailure { th -> logger.error("Failed to store ath", th) }
                }
            }
        }
}
