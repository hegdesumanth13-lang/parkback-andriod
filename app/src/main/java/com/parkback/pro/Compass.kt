package com.parkback.pro

import android.hardware.*
import android.location.Location
import android.view.Surface
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun Compass(here: Location, spot: Spot) {
    val context = LocalContext.current
    val manager = remember { context.service<SensorManager>() }
    val sensor = remember { manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var heading by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(sensor, lifecycle) {
        val listener =
            object : SensorEventListener {
                override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {
                    if (accuracy == 0) heading = null
                }

                @Suppress("DEPRECATION")
                override fun onSensorChanged(e: SensorEvent) {
                    if (e.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                        heading = null
                        return
                    }
                    val matrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(matrix, e.values)
                    val axes =
                        when (context.service<WindowManager>().defaultDisplay.rotation) {
                            Surface.ROTATION_90 ->
                                SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                            Surface.ROTATION_180 ->
                                SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                            Surface.ROTATION_270 ->
                                SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                        }
                    val adjusted = FloatArray(9)
                    SensorManager.remapCoordinateSystem(matrix, axes.first, axes.second, adjusted)
                    heading =
                        Math.toDegrees(
                                SensorManager.getOrientation(adjusted, FloatArray(3))[0].toDouble()
                            )
                            .toFloat()
                }
            }
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME ->
                    sensor?.let {
                        manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
                    }
                Lifecycle.Event.ON_PAUSE -> manager.unregisterListener(listener)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            manager.unregisterListener(listener)
        }
    }
    val declination =
        GeomagneticField(
                here.latitude.toFloat(),
                here.longitude.toFloat(),
                here.altitude.toFloat(),
                here.time,
            )
            .declination
    val near = distance(here, spot) <= maxOf(here.accuracy, spot.accuracy ?: 0f, 3f)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (heading != null && !near)
            Icon(
                Icons.Default.Navigation,
                "Direction to vehicle",
                Modifier.size(30.dp)
                    .rotate(here.bearingTo(spot.position()) - heading!! - declination),
            )
        Text(
            when {
                sensor == null -> "Compass unavailable on this phone."
                near -> "Within GPS accuracy range. Check your pillar or photo."
                heading == null -> "Compass needs calibration. Move the phone in a figure eight."
                else -> "Toward vehicle · Hold phone flat. Metal can affect the compass."
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
