package cc.makin.coinmonitor

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
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
        get() = BASE_URL.copy(encodedPath = BASE_URL.encodedPath + this.id)

    suspend fun getPrice(httpClient: HttpClient): Result<Price> = runCatching {
        httpClient
            .request<HttpResponse>(this.url) { contentType(ContentType.Application.Json) }
            .receive<PancakeswapCoinResponse>()
    }
        .map { Price(it.data.price, PancakeswapCoinResponse.Data.CURRENCY) }

    @ExperimentalCoroutinesApi
    fun getPriceProvider(httpClient: HttpClient): PriceProvider = {
        flow {
            emit(getPrice(httpClient))
        }
    }

    companion object {
        private val BASE_URL: Url = Url("https://api.pancakeswap.info/api/v2/tokens/")
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
