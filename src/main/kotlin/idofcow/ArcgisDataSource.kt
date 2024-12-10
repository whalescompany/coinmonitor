package cc.makin.coinmonitor.idofcow

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.client.statement.*
import io.ktor.http.ContentType
import io.ktor.http.contentType

data class ArcgisDataSource(
    val httpClient: HttpClient,
) {
    suspend fun getBasicIdOfCowStats() = runCatching {
        httpClient
            .request(BASIC_STATS_URL) { contentType(ContentType.Application.Json) }
            .bodyAsText()
            .let { parseBasicStats(JsonParser.parseString(it).asJsonObject) }
    }

    private fun parseBasicStats(root: JsonObject) = run {
        val attributes = root
            .get("features").asJsonArray
            .get(0).asJsonObject
            .get("attributes").asJsonObject

        IdOfCowStats(
            zakazeniaDzienne = attributes.get("ZAKAZENIA_DZIENNE").asInt
        )
    }

    companion object {
        private const val BASIC_STATS_URL = "https://services-eu1.arcgis.com/zk7YlClTgerl62BY/arcgis/rest/services/" +
                "global_corona_actual_widok3/FeatureServer/0/query?f=json&cacheHint=true&" +
                "resultOffset=0&resultRecordCount=1&where=1%3D1&outFields=*&resultType=standard&" +
                "returnGeometry=false&spatialRel=esriSpatialRelIntersects"
    }
}
