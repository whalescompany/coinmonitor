package idofcow

import cc.makin.coinmonitor.idofcow.ArcgisDataSource
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

class ArcgisDataSourceTest {
    @Test
    fun `test get basic stats`() = runBlocking {
        val validResponse = """
            {
              "objectIdFieldName": "OBJECTID",
              "features": [
                {
                  "attributes": {
                    "OBJECTID": 1,
                    "Data": 1636709427879,
                    "ZAKAZENIA_DZIENNE": 12965,
                    "ZGONY_DZIENNE": 31,
                    "ZGONY_COVID": 7,
                    "ZGONY_WSPOLISTNIEJACE": 24,
                    "KWARANTANNA": 410504,
                    "TESTY": 49375,
                    "TESTY_POZYTYWNE": 13870,
                    "ZLECENIA_POZ": 570,
                    "LICZBA_OZDROWIENCOW": 10473,
                    "AKTUALNE_ZAKAZENIA": 323035,
                    "DATA_SHOW": "12.11.2021 10:30",
                    "LICZBA_ZGONOW": 78555,
                    "WSZYSCY_OZDROWIENCY": 2774179,
                    "LICZBA_ZAKAZEN": 3175769
                  }
                }
              ]
            }
        """.trimIndent()

        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel(validResponse),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(JsonFeature)
        }
        val dataSource = ArcgisDataSource(httpClient)

        val result = dataSource.getBasicIdOfCowStats()
        assert(result.getOrThrow().zakazeniaDzienne == 12965) { "zakazeniaDzienne not equal" }
    }
}
