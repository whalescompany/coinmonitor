package cc.makin.coinmonitor

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.io.File
import java.io.FileOutputStream
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@Suppress("BlockingMethodInNonBlockingContext")
@FlowPreview
@ExperimentalTime
fun CoroutineScope.savePriceLogPersistent(flow: CoinResultFlow, clock: Clock = Clock.System) =
    launch(Dispatchers.IO + CoroutineName("Money generator logger")) {
        val logFile = File("pricelog.csv")
        logFile.renameTo(File("${clock.now().epochSeconds}_pricelog.csv"))
        logFile.createNewFile()

        val fileOutput = FileOutputStream(logFile, true)

        fileOutput.use { fileOutputStream ->
            fileOutputStream.bufferedWriter().use { writer ->
                writer.write("date,price\n")

                flow
                    .mapNotNull { it as? CoinResult.Ok }
                    .map { it.toLogCsvEntry(clock) }
                    .onEach { writer.write(it); writer.newLine() }
                    .sample(Duration.seconds(5))
                    .collect { writer.flush() }
            }
        }
    }

// todo:
//  mzoe warto byloby te adnotacje zastapic ustawieniem ale nie chce mi sie tera bo i tak gradle ejst
//  do przerubki kubus pochatek bez ivony
@FlowPreview
@ExperimentalTime
internal fun CoinResult.Ok.toLogCsvEntry(clock: Clock = Clock.System) =
    "\"${clock.now()}\",\"${this.price.value}\""
