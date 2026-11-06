package weather.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * App root. Owns the tab navigator and a stack per tab.
 * Navigation is also a state change, so the whole app can be walked without creating Views.
 * scope is where Appear runs (Main in the app, runBlocking in the CLI).
 */
class AppRoot(private val deps: Deps, private val scope: CoroutineScope) : Navigator {
  private val mutableTab = MutableStateFlow(Tab.Weather)
  val tab: StateFlow<Tab> = mutableTab.asStateFlow()

  /** Root screen of each tab. Kept when switching tabs */
  val weather = WeatherViewModel(deps, this)
  val search = SearchViewModel(deps, this)
  val favorites = FavoritesViewModel(deps, this)

  /** Stack per tab (screens pushed on top of the root) */
  private val mutablePaths = MutableStateFlow(mapOf(Tab.Search to emptyList<CityViewModel>(), Tab.Favorites to emptyList()))
  val paths: StateFlow<Map<Tab, List<CityViewModel>>> = mutablePaths.asStateFlow()

  private var focusedNode: Node? = null
  private var pending: Job? = null

  // Focus

  /** Top of the selected tab's stack. The "deepest node" in Reflex. */
  val focused: Node
    get() = when (tab.value) {
      Tab.Weather -> weather
      Tab.Search -> paths.value[Tab.Search]!!.lastOrNull() ?: search
      Tab.Favorites -> paths.value[Tab.Favorites]!!.lastOrNull() ?: favorites
    }

  val path: String
    get() = when (tab.value) {
      Tab.Weather -> "/"
      Tab.Search -> paths.value[Tab.Search]!!.lastOrNull()?.let { "/cities/${it.id}" } ?: "/search"
      Tab.Favorites -> paths.value[Tab.Favorites]!!.lastOrNull()?.let { "/favorites/${it.id}" } ?: "/favorites"
    }

  val canGoBack: Boolean get() = paths.value[tab.value]?.isNotEmpty() == true

  /** Sends Appear when focus moves to another screen (same rule in the app and the CLI) */
  private fun focusChanged() {
    val node = focused
    if (node === focusedNode) return
    focusedNode = node
    val previous = pending
    pending = scope.launch {
      previous?.join()
      node.appear()
    }
  }

  /** Waits until the Appear started by a navigation finishes */
  suspend fun idle() {
    while (true) {
      val job = pending ?: return
      job.join()
      if (pending === job) pending = null
    }
  }

  // Navigator

  override fun push(route: Route) {
    val tab = tab.value
    if (route !is Route.City || tab == Tab.Weather) return
    setPath(paths.value[tab]!! + CityViewModel(route.id, deps), tab)
  }

  override fun selectTab(tab: Tab) {
    mutableTab.value = tab
    focusChanged()
  }

  fun back() {
    if (!canGoBack) return
    setPath(paths.value[tab.value]!!.dropLast(1), tab.value)
  }

  fun setPath(path: List<CityViewModel>, tab: Tab) {
    mutablePaths.value = paths.value + (tab to path)
    focusChanged()
  }

  /** Deep link */
  fun open(url: String) {
    val parts = url.split("/").filter { it.isNotEmpty() }
    val id = parts.getOrNull(1)?.toIntOrNull()
    when {
      parts.firstOrNull() == "search" -> show(Tab.Search, emptyList())
      parts.firstOrNull() == "cities" && id != null -> show(Tab.Search, listOf(CityViewModel(id, deps)))
      parts.firstOrNull() == "favorites" && id == null -> show(Tab.Favorites, emptyList())
      parts.firstOrNull() == "favorites" && id != null -> show(Tab.Favorites, listOf(CityViewModel(id, deps)))
      else -> mutableTab.value = Tab.Weather
    }
    focusChanged()
  }

  private fun show(tab: Tab, path: List<CityViewModel>) {
    mutableTab.value = tab
    mutablePaths.value = paths.value + (tab to path)
  }

  // Introspection

  /** Actions of the current screen plus those lent by the navigator (Back, SelectTab) */
  val actions: List<String> get() = focused.actions + (if (canGoBack) listOf("Back") else emptyList()) + "SelectTab"

  @Serializable private data class SelectTabInput(val tab: Tab)

  /** Sends by action name. Rejects actions that neither the current screen nor its parents have */
  suspend fun perform(action: String, input: JsonObject) {
    when {
      action == "Back" && canGoBack -> back()
      action == "SelectTab" -> {
        val decoded = try {
          CoreJson.decodeFromJsonElement(SelectTabInput.serializer(), input)
        } catch (e: Exception) {
          throw CoreError("SelectTab の入力が正しくない: $input")
        }
        selectTab(decoded.tab)
      }
      action in focused.actions -> focused.perform(action, input)
      else -> throw CoreError("${focused.name} に $action はない: ${actions.joinToString(", ")}")
    }
    idle()
  }
}

/** Result of ls. Both the CLI and the in-app server return this shape. */
fun AppRoot.snapshot() = kotlinx.serialization.json.buildJsonObject {
  put("path", kotlinx.serialization.json.JsonPrimitive(path))
  put("name", kotlinx.serialization.json.JsonPrimitive(focused.name))
  put("state", focused.stateJson())
  put("actions", kotlinx.serialization.json.JsonArray(actions.map { kotlinx.serialization.json.JsonPrimitive(it) }))
}
