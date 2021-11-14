package cc.makin.coinmonitor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

typealias CoinResultFlow = Flow<CoinResult>

sealed interface CoinResult {
    val name: String

    data class Ok(
        override val name: String,
        val price: Price,
    ) : CoinResult

    data class Error(
        override val name: String,
        val error: Any,
    ) : CoinResult
}

data class OfflineCoin(
    val name: String,
    val priceProvider: PriceProvider,
)

@ExperimentalTime
fun OfflineCoin.getOnline(scope: CoroutineScope) =
    this.priceProvider
        .repeatable(Duration.seconds(15), scope = scope)
        .invoke(scope)
        .map { result -> toOnline(result) }

private fun OfflineCoin.toOnline(priceResult: Result<Price>) =
    priceResult.fold(
        onSuccess = { price -> CoinResult.Ok(name, price) },
        onFailure = { throwable -> CoinResult.Error(this.name, throwable) },
    )
