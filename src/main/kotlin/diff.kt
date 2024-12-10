package cc.makin.coinmonitor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

fun <T> Flow<T>.diff(initialPreviousValue: T): Flow<Pair<T, T>> = run {
    @Suppress("VariableUsage")
    var previousValue = initialPreviousValue
    this@diff
        .map { newValue -> previousValue to newValue }
        .onEach { previousValue = it.second }
}

@FlowPreview
@ExperimentalTime
suspend fun <T> Flow<T>.persistentDiff(
    fileName: String,
    deserialize: (serialized: String) -> T,
    serialize: (T) -> String,
    scope: CoroutineScope,
): Flow<Pair<T, T>> = run {
    val persistenceFile = File(".", fileName)

    @Suppress("VariableUsage")
    var previousValue = withContext(Dispatchers.IO) {
        if (persistenceFile.exists()) {
            deserialize(persistenceFile.readText())
        } else {
            deserialize("")
        }
    }

    this@persistentDiff
        .map { newValue -> previousValue to newValue }
        .filter { it.first != it.second }
        .onEach { (_, newValue) ->
            previousValue = newValue
        }
        .sample(10.seconds)
        .onEach { (_, newValue) ->
            scope.launch(Dispatchers.IO) {
                persistenceFile.writeText(serialize(newValue))
            }
        }
}
