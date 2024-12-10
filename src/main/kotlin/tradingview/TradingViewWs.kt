package cc.makin.coinmonitor.tradingview

import com.google.gson.Gson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

interface TradingView {
    suspend fun getPrice(symbol: String): Flow<Double>
}

/**
 * [TradingView] implementation using ktor as backend.
 */
@ExperimentalTime
@ExperimentalCoroutinesApi
data class KtorTradingViewWs(
    val webSocketSession: ClientWebSocketSession,
    val scope: CoroutineScope,
    val incomingPackets: MutableSharedFlow<IncomingPacket> = MutableSharedFlow(),
) : TradingView {
    init {
        incomingPackets
            .filterIsInstance<IncomingPacket.Ping>()
            .onEach { sendPong(it.id) }
            .launchIn(scope)
    }

    override suspend fun getPrice(symbol: String): Flow<Double> = run {
        val listenerId = UUID.randomUUID().toString()

        sendOutgoingPacket(OutgoingPacket.Quote.CreateSession(listenerId))
        sendOutgoingPacket(OutgoingPacket.Quote.AddSymbols(listenerId, listOf(symbol)))

        incomingPackets
            .takeWhile { webSocketSession.isActive }
            .filterIsInstance<IncomingPacket.Json<*>>()
            .filter { it.listenerId == listenerId }
            .mapNotNull { (it.content as? IncomingPacket.Json.Content.CoinUpdate)?.price }
//            .map { priceValue  }
    }

    private suspend fun sendPong(pingId: Int) {
        logger.debug("Sending pong with id: $pingId.")
        val packet = "~h~$pingId"
        sendRawPacket(packet)
    }

    private suspend fun sendOutgoingPacket(packet: OutgoingPacket) {
        val packetJsonObj = PacketCodec.encodePacket(packet)
        val packetJsonSerialized = gson.toJson(packetJsonObj)
        logger.debug("sending outgoing packet: $packetJsonSerialized")
        sendRawPacket(packetJsonSerialized)
    }

    private suspend fun sendRawPacket(content: String) {
        val packetWithHeader = "~m~${content.length}~m~" + content
        webSocketSession.send(Frame.Text(packetWithHeader))
    }

    tailrec suspend fun handle() {
        val incoming = withTimeoutOrNull(30.seconds) {
            runCatching {
                webSocketSession.incoming.receive()
            }.onFailure { ex ->
                logger.info("WebSocket receive channel has been closed", ex)
            }.getOrNull()
        }

        incoming?.let { handleFrame(it) }

        if (this.webSocketSession.isActive && this.webSocketSession.incoming.isClosedForReceive.not()) {
            this.handle()
        } else {
            logger.info("Handler finished due to websocket inactivity.")
        }
    }

    private suspend fun handleFrame(it: Frame) =
        runCatching {
            when (it) {
                is Frame.Text -> handleTextFrame(it)
                else -> {
                }
            }
        }.onFailure { ex ->
            logger.error("Unexpected error caught while handling incomming message.", ex)
        }

    private suspend fun handleTextFrame(frame: Frame.Text) {
        val text = frame.readText()
        logger.debug("Text frame received: $text")
        PacketCodec.decodePacketsWithHeaders(text).forEach {
            it.fold(
                { (ex, packetRaw) -> logger.error("Unexpected exception while decoding packet: '$packetRaw'", ex) },
                { packet -> packet?.let { incomingPackets.emit(packet) } }
            )
        }
    }

    @ExperimentalCoroutinesApi
    companion object {
        private const val URL = "wss://data.tradingview.com/socket.io/websocket"

        @ExperimentalTime
        private val RECONNECT_DELAY = 5.seconds

        private val logger = LoggerFactory.getLogger(KtorTradingViewWs::class.java)
        private val gson = Gson()

        @ExperimentalTime
        suspend fun start(httpClient: HttpClient) = callbackFlow {
            start(httpClient)
        }

        @ExperimentalTime
        private tailrec suspend fun ProducerScope<TradingView>.start(httpClient: HttpClient) {
            withContext(Dispatchers.IO + CoroutineName(KtorTradingViewWs::class.java.simpleName)) {
                logger.info("Creating ws connection")
                try {
                    httpClient.webSocket(request = {
                        url(URL)
                        header(HttpHeaders.Origin, "https://www.tradingview.com")
                    }) {
                        this@webSocket.send(
                            Frame.Text("""~m~54~m~{"m":"set_auth_token","p":["unauthorized_user_token"]}"""))

                        val state = KtorTradingViewWs(this, this)
                        this@start.send(state)
                        state.handle()
                        this.close()
                    }
                } catch (ex: Throwable) {
                    logger.info("WebSocket connection unknown exception.", ex)
                }

                logger.info("Websocket session finished. " +
                        "Trying to reconnect in ${RECONNECT_DELAY.inWholeSeconds} seconds.")

                delay(RECONNECT_DELAY)
            }

            logger.info("Reconnecting...")
            this.start(httpClient)
        }
    }
}
