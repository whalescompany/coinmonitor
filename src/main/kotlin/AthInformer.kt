package cc.makin.coinmonitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.ExperimentalTime

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
fun Flow<CoinResult.Ok>.ath(initialPreviousAth: Ath) =
    this
        .filter { it.price > initialPreviousAth.price }
        .map { Ath(it.price) }

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
