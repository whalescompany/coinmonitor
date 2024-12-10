package cc.makin.coinmonitor

import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.request.request
import io.ktor.http.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import java.util.Currency

typealias PriceProvider = FlowProvider<Result<Price>>

//
// Pancakeswap
//

data class PancakeswapCoin(
    val id: String,
) {
    private val url: Url
        get() = BASE_URL.clone()
            .apply { encodedPath += id }
            .build()

    suspend fun getPrice(httpClient: HttpClient): Result<Price> = runCatching {
        httpClient.request(this.url) { contentType(ContentType.Application.Json) }.body<PancakeswapCoinResponse>()
    }.map { Price(it.data.price, PancakeswapCoinResponse.Data.CURRENCY) }

    @ExperimentalCoroutinesApi
    fun getPriceProvider(httpClient: HttpClient): PriceProvider = {
        flow {
            emit(getPrice(httpClient))
        }
    }

    companion object {
        private val BASE_URL: URLBuilder = URLBuilder("https://api.pancakeswap.info/api/v2/tokens/")
    }
}

private data class PancakeswapCoinResponse(
    val updatedAt: Long,
    val data: Data,
) {
    data class Data(
        val name: String,
        val symbol: String,
        val price: Double,
    ) {
        companion object {
            internal val CURRENCY: Currency = Currency.getInstance("USD")
        }
    }
}
