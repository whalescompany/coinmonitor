package tradingview

import cc.makin.coinmonitor.tradingview.PacketCodec
import org.junit.jupiter.api.Test

internal class PacketCodecTest {
    @Test
    fun `decode one packet with header`() {
        val data = """~m~158~m~{"m":"qsd","p":["Q_SESSION_ID",
            |{"n":"COINBASE:LRCUSD","s":"ok","v":{"trade_loaded":true,"bid_size":0.084,"bid":2.586,
            |"ask_size":190.274835,"ask":2.5903}}]}""".trimMargin()
        val decodedPackets = PacketCodec.decodePacketsWithHeaders(rawPacketsWithHeaders = data)
        assert(decodedPackets.size == 1) { "data contains only one packet" }
        val result = decodedPackets.first()

//        val packet = decodedPackets.first().orNull()!!
//        assert(packet) {}
    }

    @Test
    fun `decode multiple packets with headers in one frame`() {
        val data = """~m~158~m~{"m":"qsd","p":["Q_SESSION_ID",
            |{"n":"COINBASE:LRCUSD","s":"ok","v":{"trade_loaded":true,"bid_size":0.084,"bid":2.586,
            |"ask_size":190.274835,"ask":2.5903}}]}
            |~m~251~m~{"m":"qsd","p":["qs_oxUVcVKyLU1z",
            |{"n":"={\"symbol\":\"COINBASE:LRCUSD\",\"currency-id\":\"USD\",
            |\"adjustment\":\"splits\",\"session\":\"extended\"}","s":"ok",
            |"v":{"trade_loaded":true,"bid_size":0.084,"bid":2.586,"ask_size":190.274835,"ask":2.5903}}]}
            |~m~112~m~{"m":"qsd","p":["qs_BDRp2BwYWTGe",{"n":"TVC:GOLD","s":"ok","v":
            |{"lp_time":1636480465,"lp":1830.514,"ch":6.63}}]}""".trimMargin()
        val decodedPackets = PacketCodec.decodePacketsWithHeaders(rawPacketsWithHeaders = data)
        assert(decodedPackets.size == 3) { "data contains three packets" }
    }
}
