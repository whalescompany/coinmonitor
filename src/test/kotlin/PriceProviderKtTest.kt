import cc.makin.coinmonitor.PancakeswapCoin
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.features.json.JsonFeature
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

internal class PriceProviderKtTest {
    @Test
    fun `get pancakeswap coin price`() {
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel("""{"updated_at":1635972306729,"data":
                    |{"name":"DogeBonk.com","symbol":"DOBO","price":"0.0000000749948486698015356238864047869",
                    |"price_BNB":"0.0000000001333263252451539685477435033461"}}""".trimMargin()),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(JsonFeature)
        }

        val coin = PancakeswapCoin("0xae2df9f730c54400934c06a17462c41c08a06ed8")
        runBlocking {
            val priceResult = coin.getPrice(httpClient)
            assert(priceResult.getOrThrow().value == 0.0000000749948486698015356238864047869)
        }
    }
}
