package com.campuslife

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.campuslife.data.AppViewModel
import com.campuslife.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CampusTheme { CampusApp() } }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("feed", "Афиша", Icons.Filled.Event),
    Tab("mine", "Записи", Icons.Filled.ConfirmationNumber),
    Tab("favs", "Избранное", Icons.Filled.Favorite),
    Tab("profile", "Профиль", Icons.Filled.Person),
)

@Composable
fun CampusApp(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val snack = remember { SnackbarHostState() }
    val msg by vm.message.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()

    LaunchedEffect(msg) {
        msg?.let { snack.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (route in tabs.map { it.route }) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, t.label) }, label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "feed", modifier = Modifier.padding(bottom = pad.calculateBottomPadding())) {
            composable("feed") { FeedScreen(vm, { nav.navigate("detail/$it") }, { nav.navigate("create") }) }
            composable("mine") { MineScreen(vm) { nav.navigate("detail/$it") } }
            composable("favs") {
                ListScreen("Избранное", favs, "В избранном пока пусто. Нажмите ♥ на карточке события.", { nav.navigate("detail/$it") }, vm)
            }
            composable("profile") { ProfileScreen(vm) }
            composable("create") { CreateScreen(vm) { nav.popBackStack() } }
            composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                DetailScreen(vm, it.arguments!!.getLong("id")) { nav.popBackStack() }
            }
        }
    }
}
