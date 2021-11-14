package cc.makin.coinmonitor.tradingview

sealed interface IncomingPacket {
    data class Ping(val id: Int) : IncomingPacket

    data class Json<C : Json.Content>(
        val listenerId: String?,
        val content: C,
    ) : IncomingPacket {
        sealed interface Content {
            data class CoinUpdate(
                val coinId: String,
                val price: Double?,
            ) : Content
        }
    }
}

sealed interface OutgoingPacket {
    sealed interface Quote : OutgoingPacket {
        data class CreateSession(val sessionId: String) : Quote
        data class AddSymbols(val sessionId: String, val symbols: List<String>) : Quote
    }
}
