package com.trikset.gamepad

/**
 * Reactive connection state of the [SenderService], exposed as a
 * [androidx.lifecycle.ViewModel]-backed [kotlinx.coroutines.flow.StateFlow] (Campaign 3 P2).
 * Consumers collect it with [androidx.lifecycle.repeatOnLifecycle] instead of the former one-shot
 * disconnect callback.
 */
sealed interface ConnectionState {
  data object Connecting : ConnectionState

  data object Connected : ConnectionState

  data class Disconnected(val reason: String) : ConnectionState
}
