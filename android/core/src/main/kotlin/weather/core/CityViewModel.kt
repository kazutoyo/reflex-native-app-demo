@file:OptIn(ExperimentalSerializationApi::class)

package weather.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("status")
sealed interface CityState {
  @Serializable @SerialName("idle") data object Idle : CityState
  @Serializable @SerialName("loading") data object Loading : CityState
  @Serializable @SerialName("loaded")
  data class Loaded(val city: City, val forecast: Forecast, val favorite: Boolean) : CityState
  @Serializable @SerialName("error") data class Error(val message: String) : CityState
}

@Serializable
sealed interface CityAction {
  @Serializable @SerialName("Appear") data object Appear : CityAction
  @Serializable @SerialName("Retry") data object Retry : CityAction
  @Serializable @SerialName("ToggleFavorite") data object ToggleFavorite : CityAction
}

/** City details (weekly forecast and favorite). Back is lent by the stack, so it is not defined here. */
class CityViewModel internal constructor(val id: Int, private val deps: Deps) :
  ViewModel<CityState, CityAction, CityViewModel.Msg>(CityState.Idle, CityState.serializer(), CityAction.serializer()) {
  sealed interface Msg {
    data object Loading : Msg
    data class Loaded(val city: City, val forecast: Forecast, val favorite: Boolean) : Msg
    data class Failed(val message: String) : Msg
    data class FavoriteChanged(val favorite: Boolean) : Msg
  }

  override fun update(state: CityState, msg: Msg): CityState = when (msg) {
    Msg.Loading -> CityState.Loading
    is Msg.Loaded -> CityState.Loaded(msg.city, msg.forecast, msg.favorite)
    is Msg.Failed -> CityState.Error(msg.message)
    is Msg.FavoriteChanged -> if (state is CityState.Loaded) state.copy(favorite = msg.favorite) else state
  }

  override val name = "City"
  override val actions = listOf("Appear", "Retry", "ToggleFavorite")

  private suspend fun load() {
    dispatch(Msg.Loading)
    try {
      val city = deps.api.city(id)
      val forecast = deps.api.forecast(city.latitude, city.longitude)
      dispatch(Msg.Loaded(city, forecast, deps.favorites.list().any { it.id == id }))
    } catch (e: Exception) {
      dispatch(Msg.Failed(errorMessage(e)))
    }
  }

  override suspend fun send(action: CityAction) {
    when (action) {
      CityAction.Appear -> if (state.value == CityState.Idle) load()
      CityAction.Retry -> load()
      CityAction.ToggleFavorite -> {
        val s = state.value as? CityState.Loaded ?: return
        val others = deps.favorites.list().filter { it.id != id }
        deps.favorites.save(if (s.favorite) others else others + s.city)
        dispatch(Msg.FavoriteChanged(!s.favorite))
      }
    }
  }

  override suspend fun appear() = send(CityAction.Appear)
}
