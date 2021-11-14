package cc.makin.coinmonitor.tradingview

import arrow.core.Either
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * # Packet encoder & decoder.
 *
 * Every WebSocket text frame contains packets. Most of the time only one per frame, but
 * sometimes it is possible that server merged 2 or more packets into one frame.
 * Every packet is prepended with packet length.
 *
 * Possible packet content formats:
 *  * JSON: packet content encoded in JSON
 *  * ping/pong: static string - ``~h~``
 *  * other // not implemented
 *
 * ## Example WebSocket frame:
 * > ``~m~27~m~{"some_obfuscated": "json"}~m~4~m~~h~2``
 *
 * ### Explanation of first packet with header format:
 * > "~m~27~m~{"some_obfuscated": "json"}
 * ``~m~`` - static magic
 * ``27`` - length of the packet content (in this case of JSON content)
 * ``~m~`` - static magic
 * ``{"some_obfuscated": "json"}`` - json
 */
internal object PacketCodec {
    private val PACKET_LENGTH_PATTERN = Regex("~m~(\\d+)~m~")

    /**
     * @return encoded [OutgoingPacket] without packet header!
     */
    fun encodePacket(packet: OutgoingPacket) = when (packet) {
        is OutgoingPacket.Quote.CreateSession -> JsonObject().also { root ->
            root.addProperty("m", "quote_create_session")
            root.add("p", packet.sessionId.toJsonArray())
        }
        is OutgoingPacket.Quote.AddSymbols -> JsonObject().also { root ->
            root.addProperty("m", "quote_add_symbols")
            root.add("p", (listOf(packet.sessionId) + packet.symbols).toJsonArray())
        }
    }

    fun decodePacketsWithHeaders(rawPacketsWithHeaders: String) =
        PACKET_LENGTH_PATTERN.findAll(rawPacketsWithHeaders)
            .mapNotNull { matchResult ->
                matchResult.groupValues[1].toIntOrNull()?.let { bodyLength ->
                    decodePacket(
                        bodyLength = bodyLength,
                        bodyIndex = matchResult.range.last + 1,
                        buffer = rawPacketsWithHeaders,
                    )
                }
            }
            .toList()

    private fun decodePacket(bodyLength: Int, bodyIndex: Int, buffer: String) = run {
        val packetContentRaw = buffer.substring(bodyIndex, endIndex = bodyIndex + bodyLength)

        Either.catch {
            when {
                packetContentRaw.isEmpty() -> null
                packetContentRaw.startsWith('~') -> decodeTildePacket(packetContentRaw)
                else -> decodeJsonPacket(packetContentRaw)
            }
        }.mapLeft { ex -> ex to packetContentRaw }
    }

    private fun decodeJsonPacketContent(packetType: String, jsonContent: JsonElement): IncomingPacket.Json.Content? =
        when (packetType) {
            "qsd" -> decodeCoinUpdate(jsonContent.asJsonObject)
            else -> null
        }

    private fun decodeTildePacket(packetRaw: String) =
        if (packetRaw.length > 3 && packetRaw[0] == '~' && packetRaw[2] == '~') {
            when (packetRaw[1]) {
                'h' -> IncomingPacket.Ping(packetRaw.substring(3).toInt())
                else -> null
            }
        } else {
            null
        }

    private fun decodeJsonPacket(packetRaw: String) = run {
        val root = JsonParser.parseString(packetRaw).asJsonObject
        val listenerResponse = root.get("p")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
        if (listenerResponse != null && listenerResponse.size() >= 2) {
            val content = decodeJsonPacketContent(
                packetType = root.get("m").asString, jsonContent = listenerResponse.get(1))
            content?.toIncomingPacket(listenerResponse)
        } else {
            null
        }
    }

    private fun IncomingPacket.Json.Content.toIncomingPacket(listenerResponse: JsonArray) =
        IncomingPacket.Json(
            listenerId = listenerResponse.get(0).asString,
            content = this,
        )

    private fun decodeCoinUpdate(content: JsonObject) =
        IncomingPacket.Json.Content.CoinUpdate(
            coinId = content.get("n").asString,
            price = content.get("v").asJsonObject.get("lp")?.asDouble,
        )
}

private fun String.toJsonArray() =
    JsonArray(1)
        .also { array -> array.add(this) }

private fun List<String>.toJsonArray() =
    JsonArray(this.size)
        .also { array ->
            this.forEach { symbol -> array.add(symbol) }
        }
