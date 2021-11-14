package cc.makin.coinmonitor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

fun <T> Flow<T>.replayState() = flow {
    emit(null)
    emitAll(this@replayState)
}

// resend state flow on collect
fun <T> StateFlow<T>.replayState() = flow {
    emit(this@replayState.value)
    emitAll(this@replayState)
}

suspend inline fun <T, S> Flow<T>.collectState(
    initialPreviousState: S,
    crossinline action: suspend (previousState: S, newValue: T) -> S,
) {
    @Suppress("VariableUsage")
    var previousState = initialPreviousState
    this@collectState
        .collect { newValue -> previousState = action.invoke(previousState, newValue) }
}

typealias FlowProvider<T> = CoroutineScope.() -> Flow<T>

@ExperimentalTime
fun <T> FlowProvider<T>.repeatable(every: Duration, scope: CoroutineScope): FlowProvider<T> = {
    flow {
        while (scope.isActive) {
            emitAll(this@repeatable.invoke(scope))
            delay(every)
        }
    }
}
