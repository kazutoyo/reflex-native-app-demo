package weather.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A node in Reflex's Introspection. The CLI and the in-app server see only this shape. */
interface Node {
  val name: String
  /** Everything the user can do goes into actions */
  val actions: List<String>
  /** Everything the user can see goes into state */
  fun stateJson(): JsonElement
  /** Builds an Action from the action name and JSON input and sends it. Rejects input that does not match the type before sending */
  suspend fun perform(action: String, input: JsonObject)
  /** Called by AppRoot when the node gains focus */
  suspend fun appear()
}

/** Elm-style ViewModel. Only update changes state, and only send is exposed. */
abstract class ViewModel<S, A, M>(
  initial: S,
  private val stateSerializer: KSerializer<S>,
  private val actionSerializer: KSerializer<A>,
) : Node {
  private val mutableState = MutableStateFlow(initial)
  val state: StateFlow<S> = mutableState.asStateFlow()

  protected abstract fun update(state: S, msg: M): S
  protected fun dispatch(msg: M) {
    mutableState.value = update(mutableState.value, msg)
  }

  abstract suspend fun send(action: A)

  override fun stateJson() = CoreJson.encodeToJsonElement(stateSerializer, state.value)

  override suspend fun perform(action: String, input: JsonObject) {
    val decoded = try {
      CoreJson.decodeFromJsonElement(actionSerializer, JsonObject(input + ("type" to JsonPrimitive(action))))
    } catch (e: Exception) {
      throw CoreError("$action の入力が正しくない: $input")
    }
    send(decoded)
  }
}
