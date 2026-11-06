package weather.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchState(
  val query: String = "",
  val status: Status = Status.idle,
  val results: List<City> = emptyList(),
  val error: String? = null,
  val favorites: List<City> = emptyList(),
) {
  @Suppress("EnumEntryName")
  @Serializable
  enum class Status { idle, loading, loaded, error }
}

@Serializable
sealed interface SearchAction {
  @Serializable @SerialName("Appear") data object Appear : SearchAction
  @Serializable @SerialName("ChangeQuery") data class ChangeQuery(val query: String) : SearchAction
  @Serializable @SerialName("Submit") data object Submit : SearchAction
  @Serializable @SerialName("Open") data class Open(val id: Int) : SearchAction
}

/** City search. Shows favorites when the query is empty. */
class SearchViewModel internal constructor(private val deps: Deps, private val nav: Navigator) :
  ViewModel<SearchState, SearchAction, SearchViewModel.Msg>(
    SearchState(), SearchState.serializer(), SearchAction.serializer()
  ) {
  sealed interface Msg {
    data class FavoritesLoaded(val favorites: List<City>) : Msg
    data class QueryChanged(val query: String) : Msg
    data object Searching : Msg
    data class Found(val results: List<City>) : Msg
    data class Failed(val message: String) : Msg
  }

  override fun update(state: SearchState, msg: Msg): SearchState = when (msg) {
    is Msg.FavoritesLoaded -> state.copy(favorites = msg.favorites)
    is Msg.QueryChanged -> state.copy(query = msg.query)
    Msg.Searching -> state.copy(status = SearchState.Status.loading, error = null)
    is Msg.Found -> state.copy(status = SearchState.Status.loaded, results = msg.results)
    is Msg.Failed -> state.copy(status = SearchState.Status.error, error = msg.message)
  }

  override val name = "Search"
  override val actions = listOf("Appear", "ChangeQuery", "Submit", "Open")

  override suspend fun send(action: SearchAction) {
    when (action) {
      // Reload on every focus to reflect favorites changed in the detail screen
      SearchAction.Appear -> dispatch(Msg.FavoritesLoaded(deps.favorites.list()))
      is SearchAction.ChangeQuery -> dispatch(Msg.QueryChanged(action.query))
      SearchAction.Submit -> {
        val query = state.value.query.trim()
        if (query.isEmpty()) return
        dispatch(Msg.Searching)
        try {
          dispatch(Msg.Found(deps.api.searchCities(query)))
        } catch (e: Exception) {
          dispatch(Msg.Failed(errorMessage(e)))
        }
      }
      is SearchAction.Open -> nav.push(Route.City(action.id))
    }
  }

  override suspend fun appear() = send(SearchAction.Appear)
}
