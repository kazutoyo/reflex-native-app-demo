package weather.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import weather.core.AppRoot
import weather.core.CityAction
import weather.core.CityState
import weather.core.CityViewModel
import weather.core.FavoritesAction
import weather.core.FavoritesState
import weather.core.FavoritesViewModel
import weather.core.Forecast
import weather.core.SearchAction
import weather.core.SearchState
import weather.core.SearchViewModel
import weather.core.Tab
import weather.core.WeatherAction
import weather.core.WeatherState
import weather.core.WeatherViewModel
import weather.core.describeWeather

// Screens are presentation only. All logic lives in the core ViewModels. The View's only contract: read state and call send.

/** Sends from a button. */
@Composable
private fun <A> sender(send: suspend (A) -> Unit): (A) -> Unit {
  val scope = rememberCoroutineScope()
  return { action -> scope.launch { send(action) } }
}

@Composable
fun RootView(root: AppRoot) {
  val tab by root.tab.collectAsState()
  val paths by root.paths.collectAsState()
  val top = paths[tab]?.lastOrNull()
  BackHandler(enabled = top != null) { root.back() }

  MaterialTheme(colorScheme = lightColorScheme()) {
    Scaffold(
      bottomBar = {
        NavigationBar {
          listOf(Tab.Weather to "天気", Tab.Search to "検索", Tab.Favorites to "お気に入り").forEach { (t, label) ->
            NavigationBarItem(
              selected = tab == t,
              onClick = { root.selectTab(t) },
              icon = { Icon(when (t) { Tab.Weather -> Icons.Default.Cloud; Tab.Search -> Icons.Default.Search; Tab.Favorites -> Icons.Default.Star }, null) },
              label = { Text(label) },
            )
          }
        }
      },
    ) { padding ->
      Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
        when {
          top != null -> CityScreen(top, onBack = root::back)
          tab == Tab.Weather -> WeatherScreen(root.weather)
          tab == Tab.Search -> SearchScreen(root.search)
          else -> FavoritesScreen(root.favorites)
        }
      }
    }
  }
}

fun skyColors(code: Int?): List<Color> = when (code ?: 0) {
  in 0..2 -> listOf(Color(0xFF2973D9), Color(0xFF6BB8F5))
  in 3..48 -> listOf(Color(0xFF597394), Color(0xFF99ADC7))
  in 49..67, in 80..82 -> listOf(Color(0xFF334561), Color(0xFF6B8099))
  in 68..77, in 83..86 -> listOf(Color(0xFF61789A), Color(0xFFADBFD9))
  else -> listOf(Color(0xFF21243D), Color(0xFF52547A))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sky(code: Int?, title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = darkColorScheme()) {
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(skyColors(code)))) {
      TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = { if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る") } },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
      )
      Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) { content() }
    }
  }
}

@Composable
private fun ForecastCard(forecast: Forecast) {
  Text("${Math.round(forecast.current.temperature)}°", fontSize = 88.sp, fontWeight = FontWeight.Thin, color = Color.White)
  Text(describeWeather(forecast.current.code), style = MaterialTheme.typography.titleLarge, color = Color.White)
  forecast.daily.firstOrNull()?.let {
    Text("最高 ${Math.round(it.max)}° 最低 ${Math.round(it.min)}°", color = Color.White)
  }
  Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(24.dp)).padding(16.dp)) {
    Text("7日間の予報", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
    forecast.daily.forEachIndexed { i, day ->
      Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (i == 0) "今日" else day.date.drop(5).replace("-", "/"), Modifier.width(56.dp), color = Color.White)
        Text(describeWeather(day.code), Modifier.weight(1f), color = Color.White.copy(alpha = 0.7f))
        Text("${Math.round(day.min)}°", color = Color.White.copy(alpha = 0.7f))
        Text("${Math.round(day.max)}°", Modifier.width(44.dp), color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.End)
      }
    }
  }
}

@Composable
private fun MessageCard(title: String, message: String, actions: @Composable () -> Unit) {
  Column(
    Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp)).padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
    Text(message, color = Color.White.copy(alpha = 0.8f))
    actions()
  }
}

@Composable
fun WeatherScreen(vm: WeatherViewModel) {
  val state by vm.state.collectAsState()
  val send = sender(vm::send)
  val loaded = state as? WeatherState.Loaded
  Sky(loaded?.forecast?.current?.code, loaded?.place ?: "現在地") {
    when (val s = state) {
      WeatherState.Idle, WeatherState.Loading -> CircularProgressIndicator(Modifier.padding(top = 120.dp), color = Color.White)
      is WeatherState.Loaded -> ForecastCard(s.forecast)
      is WeatherState.PermissionDenied -> MessageCard("位置情報が許可されていません", "現在地の天気を表示するには、位置情報の利用を許可してください。") {
        if (s.canAskAgain) Button({ send(WeatherAction.Retry) }) { Text("もう一度許可する") }
        else Button({ send(WeatherAction.OpenSettings) }) { Text("設定を開く") }
        OutlinedButton({ send(WeatherAction.OpenSearch) }) { Text("都市を検索する") }
      }
      is WeatherState.Error -> MessageCard("天気を取得できませんでした", s.message) {
        Button({ send(WeatherAction.Retry) }) { Text("再試行") }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: SearchViewModel) {
  val state by vm.state.collectAsState()
  val send = sender(vm::send)
  // The View owns the text being typed. Round-tripping through the ViewModel reorders characters when typing fast
  var query by remember { mutableStateOf(state.query) }
  val searched = state.status != SearchState.Status.idle && state.query.isNotBlank()
  Column(Modifier.fillMaxSize()) {
    TopAppBar(title = { Text("検索", fontWeight = FontWeight.Bold) })
    OutlinedTextField(
      value = query,
      onValueChange = { query = it; send(SearchAction.ChangeQuery(it)) },
      placeholder = { Text("都市名（例：Tokyo）") },
      singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      keyboardActions = KeyboardActions(onSearch = { send(SearchAction.Submit) }),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    when {
      state.status == SearchState.Status.loading -> CircularProgressIndicator(Modifier.padding(24.dp).align(Alignment.CenterHorizontally))
      state.status == SearchState.Status.error -> Text("検索できませんでした：${state.error}", Modifier.padding(24.dp))
      searched && state.results.isEmpty() -> Text("見つかりませんでした", Modifier.padding(24.dp))
      !searched -> Text("都市名で検索して、天気を見られます", Modifier.padding(24.dp), color = Color.Gray)
    }
    LazyColumn {
      items(if (searched) state.results else emptyList(), key = { it.id }) { city ->
        ListItem(
          headlineContent = { Text(city.name) },
          supportingContent = { Text(city.region) },
          trailingContent = { if (state.favorites.any { it.id == city.id }) Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107)) },
          modifier = Modifier.clickable { send(SearchAction.Open(city.id)) },
        )
      }
    }
  }
}

@Composable
fun CityScreen(vm: CityViewModel, onBack: () -> Unit) {
  val state by vm.state.collectAsState()
  val send = sender(vm::send)
  val loaded = state as? CityState.Loaded
  Sky(loaded?.forecast?.current?.code, loaded?.city?.name ?: "", onBack, actions = {
    if (loaded != null) IconButton({ send(CityAction.ToggleFavorite) }) {
      Icon(if (loaded.favorite) Icons.Default.Star else Icons.Default.StarBorder, "お気に入り",
        tint = if (loaded.favorite) Color(0xFFFFC107) else Color.White)
    }
  }) {
    when (val s = state) {
      CityState.Idle, CityState.Loading -> CircularProgressIndicator(Modifier.padding(top = 120.dp), color = Color.White)
      is CityState.Loaded -> {
        Text(s.city.region, color = Color.White.copy(alpha = 0.75f))
        ForecastCard(s.forecast)
      }
      is CityState.Error -> MessageCard("天気を取得できませんでした", s.message) {
        Button({ send(CityAction.Retry) }) { Text("再試行") }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(vm: FavoritesViewModel) {
  val state by vm.state.collectAsState()
  val send = sender(vm::send)
  Column(Modifier.fillMaxSize()) {
    TopAppBar(title = { Text("お気に入り", fontWeight = FontWeight.Bold) })
    if (state.status == FavoritesState.Status.loaded && state.items.isEmpty()) {
      Text("お気に入りはまだありません。都市の詳細で ☆ を押すと、ここに並びます", Modifier.padding(24.dp), color = Color.Gray)
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      items(state.items, key = { it.city.id }) { item ->
        Row(
          Modifier.fillMaxWidth().height(110.dp)
            .background(Brush.verticalGradient(skyColors(item.current?.code)), RoundedCornerShape(22.dp))
            .clickable { send(FavoritesAction.Open(item.city.id)) }.padding(16.dp),
        ) {
          Column(Modifier.weight(1f)) {
            Text(item.city.name, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(item.city.region, color = Color.White, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            Text(item.current?.let { describeWeather(it.code) } ?: if (item.failed) "取得できませんでした" else "", color = Color.White)
          }
          when {
            item.current != null -> Text("${Math.round(item.current!!.temperature)}°", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light)
            !item.failed -> CircularProgressIndicator(color = Color.White)
          }
        }
      }
    }
  }
}
