@file:OptIn(ExperimentalSerializationApi::class)

package weather.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("status")
sealed interface WeatherState {
  @Serializable @SerialName("idle") data object Idle : WeatherState
  @Serializable @SerialName("loading") data object Loading : WeatherState
  @Serializable @SerialName("permissionDenied") data class PermissionDenied(val canAskAgain: Boolean) : WeatherState
  @Serializable @SerialName("loaded") data class Loaded(val forecast: Forecast, val place: String?) : WeatherState
  @Serializable @SerialName("error") data class Error(val message: String) : WeatherState
}

@Serializable
sealed interface WeatherAction {
  @Serializable @SerialName("Appear") data object Appear : WeatherAction
  @Serializable @SerialName("Retry") data object Retry : WeatherAction
  @Serializable @SerialName("OpenSettings") data object OpenSettings : WeatherAction
  @Serializable @SerialName("OpenSearch") data object OpenSearch : WeatherAction
}

/** Weather at the current location. Location permission -> current location -> weather API. */
class WeatherViewModel internal constructor(private val deps: Deps, private val nav: Navigator) :
  ViewModel<WeatherState, WeatherAction, WeatherViewModel.Msg>(
    WeatherState.Idle, WeatherState.serializer(), WeatherAction.serializer()
  ) {
  sealed interface Msg {
    data object Loading : Msg
    data class Denied(val canAskAgain: Boolean) : Msg
    data class Loaded(val forecast: Forecast, val place: String?) : Msg
    data class Failed(val message: String) : Msg
  }

  override fun update(state: WeatherState, msg: Msg): WeatherState = when (msg) {
    Msg.Loading -> WeatherState.Loading
    is Msg.Denied -> WeatherState.PermissionDenied(msg.canAskAgain)
    is Msg.Loaded -> WeatherState.Loaded(msg.forecast, msg.place)
    is Msg.Failed -> WeatherState.Error(msg.message)
  }

  override val name = "Weather"
  override val actions = listOf("Appear", "Retry", "OpenSettings", "OpenSearch")

  private suspend fun load() {
    dispatch(Msg.Loading)
    val permission = deps.location.requestPermission()
    if (!permission.granted) return dispatch(Msg.Denied(permission.canAskAgain))
    try {
      val coords = deps.location.currentPosition()
      val forecast = deps.api.forecast(coords.latitude, coords.longitude)
      val place = runCatching { deps.location.placeName(coords) }.getOrNull() // Show the weather even if the place name is unavailable
      dispatch(Msg.Loaded(forecast, place))
    } catch (e: Exception) {
      dispatch(Msg.Failed(errorMessage(e)))
    }
  }

  override suspend fun send(action: WeatherAction) {
    when (action) {
      // Once loaded, do not refetch every time the user switches tabs.
      // If permission was denied, reload: the user may have granted it in Settings and come back
      WeatherAction.Appear -> if (state.value is WeatherState.Idle || state.value is WeatherState.PermissionDenied) load()
      WeatherAction.Retry -> load()
      WeatherAction.OpenSettings -> deps.location.openSettings()
      WeatherAction.OpenSearch -> nav.selectTab(Tab.Search)
    }
  }

  override suspend fun appear() = send(WeatherAction.Appear)
}
