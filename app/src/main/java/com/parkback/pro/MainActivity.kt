package com.parkback.pro

import android.Manifest
import android.content.*
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

class MainActivity : ComponentActivity() {
    private val model by viewModels<ParkingModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ParkBack(model, window) }
    }
}

private val light =
    lightColorScheme(
        primary = Color(0xFF006D64),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB3F4E9),
        background = Color(0xFFF8FAFC),
        surface = Color.White,
        onSurface = Color(0xFF0B132B),
    )
private val night =
    darkColorScheme(
        primary = Color(0xFF56DBCA),
        onPrimary = Color(0xFF003731),
        primaryContainer = Color(0xFF144941),
        background = Color(0xFF080F1C),
        surface = Color(0xFF121E30),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParkBack(vm: ParkingModel, window: android.view.Window) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val dark = theme == "Dark" || (theme == "System" && isSystemInDarkTheme())
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = if (dark) night else light) {
        val nav = rememberNavController()
        val stack by nav.currentBackStackEntryAsState()
        val route = stack?.destination?.route ?: "Home"
        val tabs =
            listOf(
                "Home" to Icons.Default.DirectionsCar,
                "History" to Icons.Default.History,
                "Settings" to Icons.Default.Settings,
            )
        val data by vm.records.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        val message by vm.message.collectAsStateWithLifecycle()
        LaunchedEffect(message) {
            message?.let {
                snackbar.showSnackbar(it)
                vm.message.value = null
            }
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (route) {
                                "Home" -> "ParkBack Pro"
                                "detail/{id}" -> "Parking details"
                                "photo/{id}" -> "Parking photo"
                                else -> route
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        if (tabs.none { it.first == route })
                            IconButton({ nav.popBackStack() }) {
                                Icon(Icons.Default.ArrowBack, "Back")
                            }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (tabs.any { it.first == route })
                    NavigationBar {
                        tabs.forEach { (tab, icon) ->
                            NavigationBarItem(
                                route == tab,
                                {
                                    nav.navigate(tab) {
                                        popUpTo("Home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                { Icon(icon, null) },
                                label = { Text(tab) },
                            )
                        }
                    }
            },
        ) { padding ->
            NavHost(nav, "Home", Modifier.fillMaxSize().padding(padding)) {
                composable("Home") {
                    if (data == null) Loading()
                    else
                        Home(vm, data!!.firstOrNull { it.active == 1 }, dark) {
                            nav.navigate("detail/$it")
                        }
                }
                composable("History") { History(vm, data) { nav.navigate("detail/$it") } }
                composable("Settings") { Appearance(theme, vm::appearance) }
                composable(
                    "detail/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments!!.getLong("id")
                    val s = data?.find { it.id == id }
                    if (data == null) Loading()
                    else if (s != null)
                        Details(vm, s, dark, { nav.navigate("photo/$id") }, { nav.popBackStack() })
                    else Text("This record has been removed.", Modifier.padding(20.dp))
                }
                composable(
                    "photo/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    Photo(
                        data?.find { it.id == entry.arguments!!.getLong("id") }?.photo,
                        Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(1.dp))
        content()
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            content = content,
        )
    }
}

@Composable
fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
fun Confirm(title: String, body: String, action: String, close: () -> Unit, yes: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton({
                close()
                yes()
            }) {
                Text(action)
            }
        },
        dismissButton = { TextButton(close) { Text("Cancel") } },
    )
}

fun Context.open(intent: Intent): Boolean =
    try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

fun navigate(context: Context, s: Spot, vm: ParkingModel) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(directions(s.latitude, s.longitude)))
    if (
        !context.open(Intent(intent).setPackage("com.google.android.apps.maps")) &&
            !context.open(intent)
    )
        vm.message.value = "Install a browser or Google Maps to open walking directions."
}

fun accuracy(value: Float?): String =
    value?.let {
        val status =
            when {
                it <= 15 -> "Good"
                it <= 50 -> "Moderate"
                else -> "Poor"
            }
        "$status · ±${it.roundToInt()} m"
    } ?: "Accuracy unavailable"

@Composable
private fun Home(vm: ParkingModel, s: Spot?, dark: Boolean, details: (Long) -> Unit) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var revision by remember { mutableIntStateOf(0) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var expected by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var live by remember { mutableStateOf<Location?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var locStatus by remember { mutableStateOf("Waiting for GPS") }
    val hasFix = live?.takeIf { it.recent() }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            revision++
            permissionDenied = !ctx.located()
            if (!permissionDenied) vm.save(expected)
        }
    val capture = {
        expected = s?.id
        if (ctx.located()) vm.save(expected)
        else
            permission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
    }
    LaunchedEffect(lifecycle, revision) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                launch {
                    while (isActive) {
                        now = System.currentTimeMillis()
                        delay(3000)
                    }
                }
                if (ctx.located() && vm.gps.enabled()) {
                    locStatus = "Acquiring GPS"
                    try {
                        vm.gps.watch().collect { live = it }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        locStatus = "GPS unavailable"
                    }
                } else
                    locStatus =
                        if (!ctx.located()) "Location access needed" else "Device location is off"
                awaitCancellation()
            } finally {
                live = null
            }
        }
    }
    if (confirm)
        Confirm(
            "Replace active spot?",
            "Your previous spot, notes and photo will stay in History.",
            "Replace",
            { confirm = false },
            capture,
        )
    Page {
        Column(
            Modifier.fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF0B132B), Color(0xFF154B50))),
                    RoundedCornerShape(24.dp),
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.LocalParking, null, tint = Color(0xFF79E8D8))
                Text(
                    if (s == null) "PARKING ASSISTANT" else "ACTIVE PARKING",
                    color = Color(0xFF9FECE2),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                if (s == null) "Where did you park?"
                else
                    hasFix?.let {
                        val meters = distance(it, s)
                        if (meters < 1000) "${meters.roundToInt()} m"
                        else String.format(Locale.getDefault(), "%.2f km", meters / 1000)
                    } ?: "Vehicle parked",
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    s == null -> "Save your spot before you walk away."
                    hasFix != null -> "Straight-line distance to your vehicle"
                    else -> "Waiting for a current position to measure distance."
                },
                color = Color(0xFFE2F2F0),
            )
            if (s != null) Text("Parked ${elapsed(now-s.started)} ago", color = Color.White)
        }
        Button(
            { if (s == null) capture() else confirm = true },
            Modifier.fillMaxWidth().heightIn(min = 54.dp),
            enabled = !busy,
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(if (busy) "Getting a fresh location…" else "Save My Parking Spot")
        }
        if (s != null)
            OutlinedButton(
                { navigate(ctx, s, vm) },
                Modifier.fillMaxWidth().heightIn(min = 50.dp),
            ) {
                Icon(Icons.Default.DirectionsWalk, null)
                Spacer(Modifier.width(8.dp))
                Text("Navigate with Google Maps")
            }
        if (permissionDenied && !ctx.located())
            Panel {
                Text("Location access is needed to save your position.")
                TextButton({
                    ctx.open(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${ctx.packageName}"),
                        )
                    )
                }) {
                    Text("Open app settings")
                }
            }
        if (!vm.gps.enabled())
            TextButton({ ctx.open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                Text("Turn on device location")
            }
        if (s != null || hasFix != null)
            Card(shape = RoundedCornerShape(20.dp)) {
                ParkingMap(s, hasFix, dark) { s?.let { details(it.id) } }
            }
        Panel {
            Title(if (s == null) "Location status" else "Saved parking spot")
            if (s != null) {
                Text(stamp(s.started))
                Text(s.coordinates())
                Text("Saved fix · ${accuracy(s.accuracy)}")
                if (s.label().isNotEmpty()) Text(s.label(), fontWeight = FontWeight.Bold)
                TextButton({ details(s.id) }) { Text("Details, photo & reminders") }
            }
            Text(
                if (hasFix != null)
                    "Live fix · ${accuracy(hasFix.accuracy.takeIf { hasFix.hasAccuracy() })}"
                else locStatus
            )
            if (ctx.located() && !ctx.granted(Manifest.permission.ACCESS_FINE_LOCATION))
                Text("Approximate access. Enable precise location for a closer fix.")
            if (hasFix == null || (hasFix.accuracy > 50) || (s?.accuracy ?: 0f) > 50)
                Text(
                    "GPS can drift underground. Save a pillar or floor to help find your vehicle.",
                    style = MaterialTheme.typography.bodySmall,
                )
            if (s != null && hasFix != null) Compass(hasFix, s)
        }
    }
}

@Composable
private fun Appearance(theme: String, change: (String) -> Unit) {
    Page {
        Title("Appearance")
        Panel {
            listOf("System", "Light", "Dark").forEach { option ->
                Row(
                    Modifier.fillMaxWidth().clickable { change(option) }.heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(theme == option, null)
                    Spacer(Modifier.width(14.dp))
                    Text(if (option == "System") "Follow system" else option)
                }
            }
        }
        Panel {
            Title("On this device")
            Text(
                "Parking history and photos are stored on this phone. The map loads tiles from OpenFreeMap. Walking directions open in Google Maps."
            )
        }
    }
}
