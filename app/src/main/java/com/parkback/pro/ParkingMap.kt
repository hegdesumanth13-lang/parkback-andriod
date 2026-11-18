package com.parkback.pro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.location.Location
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.*
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.*
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.*

private fun markerIcon(context: Context, car: Boolean): Icon {
    val size = (38 * context.resources.displayMetrics.density).roundToInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bitmap)
    c.scale(size / 40f, size / 40f)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = android.graphics.Color.WHITE
    c.drawCircle(20f, 20f, 19f, p)
    p.color = if (car) 0xff0B625B.toInt() else 0xff2563EB.toInt()
    c.drawCircle(20f, 20f, 16f, p)
    p.color = android.graphics.Color.WHITE
    if (car) {
        c.drawRoundRect(10f, 18f, 30f, 26f, 2f, 2f, p)
        c.drawRoundRect(13f, 13f, 27f, 22f, 3f, 3f, p)
        c.drawRect(12f, 25f, 15f, 28f, p)
        c.drawRect(25f, 25f, 28f, 28f, p)
        p.color = 0xff0B625B.toInt()
        c.drawRect(15f, 15f, 25f, 18f, p)
    } else c.drawCircle(20f, 20f, 5f, p)
    return IconFactory.getInstance(context).fromBitmap(bitmap)
}

@Suppress("DEPRECATION")
@Composable
fun ParkingMap(spot: Spot?, here: Location?, dark: Boolean, details: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var retry by remember { mutableIntStateOf(0) }
    key(retry, dark) {
        val view = remember {
            MapLibre.getInstance(context)
            MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true))
                .apply { onCreate(null) }
        }
        var map by remember { mutableStateOf<MapLibreMap?>(null) }
        var error by remember { mutableStateOf(false) }
        var center by remember { mutableIntStateOf(0) }
        val open by rememberUpdatedState(details)
        DisposableEffect(view, lifecycle) {
            val observer = LifecycleEventObserver { _, e ->
                when (e) {
                    Lifecycle.Event.ON_START -> view.onStart()
                    Lifecycle.Event.ON_RESUME -> view.onResume()
                    Lifecycle.Event.ON_PAUSE -> view.onPause()
                    Lifecycle.Event.ON_STOP -> view.onStop()
                    else -> Unit
                }
            }
            var alive = true
            val failure = MapView.OnDidFailLoadingMapListener { if (alive) error = true }
            val tileFailure =
                MapView.OnTileActionListener { operation, _, _, _, _, _, _ ->
                    if (operation == org.maplibre.android.tile.TileOperation.Error)
                        view.post { if (alive) error = true }
                }
            view.addOnTileActionListener(tileFailure)
            view.addOnDidFailLoadingMapListener(failure)
            lifecycle.addObserver(observer)
            view.getMapAsync { m ->
                if (alive) {
                    val style = if (dark) "dark" else "liberty"
                    m.setStyle("https://tiles.openfreemap.org/styles/$style") { if (alive) map = m }
                    m.setOnMarkerClickListener { marker ->
                        if (marker.title == "Parked vehicle") {
                            open()
                            true
                        } else false
                    }
                }
            }
            onDispose {
                alive = false
                lifecycle.removeObserver(observer)
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onPause()
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStop()
                view.removeOnTileActionListener(tileFailure)
                view.removeOnDidFailLoadingMapListener(failure)
                view.onDestroy()
            }
        }
        val parked = spot?.let { LatLng(it.latitude, it.longitude) }
        val live = here?.let { LatLng(it.latitude, it.longitude) }
        val car = remember { markerIcon(context, true) }
        val user = remember { markerIcon(context, false) }
        LaunchedEffect(map, parked, live) {
            val m = map ?: return@LaunchedEffect
            m.clear()
            if (parked != null && live != null)
                m.addPolyline(
                    PolylineOptions().add(parked, live).color(0xff14B8A6.toInt()).width(3f)
                )
            if (live != null)
                m.addMarker(MarkerOptions().position(live).icon(user).title("Your position"))
            if (parked != null)
                m.addMarker(MarkerOptions().position(parked).icon(car).title("Parked vehicle"))
        }
        LaunchedEffect(map, parked, live != null, center) {
            val m = map ?: return@LaunchedEffect
            if (
                parked != null &&
                    live != null &&
                    here != null &&
                    spot != null &&
                    distance(here, spot) > 35
            ) {
                val bounds = LatLngBounds.Builder().include(parked).include(live).build()
                m.easeCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        bounds,
                        (36 * context.resources.displayMetrics.density).toInt(),
                    )
                )
            } else
                (parked ?: live)?.let { m.easeCamera(CameraUpdateFactory.newLatLngZoom(it, 17.0)) }
        }
        Box(Modifier.fillMaxWidth().height(230.dp)) {
            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
            if (error)
                FilledTonalButton({ retry++ }, Modifier.align(Alignment.Center)) {
                    Text("Map unavailable · Retry")
                }
            else
                FilledTonalButton({ center++ }, Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                    Text("Recenter")
                }
        }
    }
    Text(
        "OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors",
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(8.dp),
    )
}
