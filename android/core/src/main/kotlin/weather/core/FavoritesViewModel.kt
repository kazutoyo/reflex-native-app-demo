package weather.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FavoritesState(val status: Status = Status.idle, val items: List<Item> = emptyList()) {
  @Suppress("EnumEntryName")
  @Serializable
  enum class Status { idle, loaded }

  /** A null current means loading or the fetch failed (failed) */
  @Serializable
  data class Item(val city: City, val current: Current? = null, val failed: Boolean = false)
}

@Serializable
sealed interface FavoritesAction {
  @Serializable @SerialName("Appear") data object Appear : FavoritesAction
  @Serializable @SerialName("Open") data class Open(val id: Int) : FavoritesAction
}

/** Favorite cities and the current weather for each. */
class FavoritesViewModel internal constructor(private val deps: Deps, private val nav: Navigator) :
  ViewModel<FavoritesState, FavoritesAction, FavoritesViewModel.Msg>(
    FavoritesState(), FavoritesState.serializer(), FavoritesAction.serializer()
  ) {
  sealed interface Msg {
    data class ListLoaded(val cities: List<City>) : Msg
    data class CurrentLoaded(val id: Int, val current: Current?) : Msg
  }

  override fun update(state: FavoritesState, msg: Msg): FavoritesState = when (msg) {
    is Msg.ListLoaded -> {
      // Keep weather that was already fetched
      val known = state.items.associateBy { it.city.id }
      FavoritesState(FavoritesState.Status.loaded, msg.cities.map { known[it.id] ?: FavoritesState.Item(it) })
    }
    is Msg.CurrentLoaded -> state.copy(
      items = state.items.map {
        if (it.city.id == msg.id) it.copy(current = msg.current, failed = msg.current == null) else it
      }
    )
  }

  override val name = "Favorites"
  override val actions = listOf("Appear", "Open")

  override suspend fun send(action: FavoritesAction) {
    when (action) {
      FavoritesAction.Appear -> {
        // The user may change favorites in the detail screen and come back, so reload on every focus
        dispatch(Msg.ListLoaded(deps.favorites.list()))
        for (item in state.value.items.filter { it.current == null && !it.failed }) {
          val current = runCatching { deps.api.forecast(item.city.latitude, item.city.longitude).current }.getOrNull()
          dispatch(Msg.CurrentLoaded(item.city.id, current))
        }
      }
      is FavoritesAction.Open -> nav.push(Route.City(action.id))
    }
  }

  override suspend fun appear() = send(FavoritesAction.Appear)
}
