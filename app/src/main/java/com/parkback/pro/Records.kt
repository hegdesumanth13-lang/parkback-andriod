package com.parkback.pro

import android.Manifest
import android.content.*
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.SubcomposeAsyncImage
import java.io.File
import java.time.LocalDate

@Composable
fun Photo(path: String?, mod: Modifier, crop: Boolean = false) {
    if (path == null || !File(path).isFile)
        Box(mod, contentAlignment = Alignment.Center) { Text("Photo unavailable") }
    else
        SubcomposeAsyncImage(
            model = File(path),
            contentDescription = "Saved parking photo",
            modifier = mod,
            contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
            error = { Box(contentAlignment = Alignment.Center) { Text("Unable to open photo") } },
        )
}

@Composable
fun History(vm: ParkingModel, records: List<Spot>?, open: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf<String?>(null) }
    var clear by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val matches =
        remember(records, query, day) {
            records
                .orEmpty()
                .filter {
                    (day == null || localDay(it.started).toString() == day) &&
                        listOf(
                                it.notes,
                                it.label(),
                                stamp(it.started),
                                localDay(it.started).toString(),
                                it.coordinates(),
                            )
                            .any { text -> text.contains(query.trim(), true) }
                }
                .groupBy { localDay(it.started) }
        }
    if (clear)
        Confirm(
            "Clear completed history?",
            "All completed parking records and their photos will be deleted. Your active spot stays saved.",
            "Clear history",
            { clear = false },
            { vm.delete(null) },
        )
    LazyColumn(
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    query,
                    { query = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Search notes, places or dates") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    TextButton({
                        val date = day?.let(LocalDate::parse) ?: LocalDate.now()
                        android.app
                            .DatePickerDialog(
                                context,
                                { _, y, m, d -> day = LocalDate.of(y, m + 1, d).toString() },
                                date.year,
                                date.monthValue - 1,
                                date.dayOfMonth,
                            )
                            .show()
                    }) {
                        Text(day ?: "Filter by date")
                    }
                    if (day != null) TextButton({ day = null }) { Text("Reset date") }
                }
            }
        }
        if (records == null) item { Loading() }
        else if (matches.isEmpty())
            item {
                Panel {
                    Title(
                        if (records.isEmpty()) "Your parking history starts here" else "No matches"
                    )
                    Text(
                        if (records.isEmpty()) "Save a spot on Home to see your first session."
                        else "Try another date or search term."
                    )
                }
            }
        matches.forEach { (date, entries) ->
            item(key = "day-$date") {
                Text(date.toString(), style = MaterialTheme.typography.titleMedium)
            }
            items(entries, key = { it.id }) { s ->
                ElevatedCard(
                    onClick = { open(s.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            if (s.active == 1) "Currently parked"
                            else "Parked for ${elapsed((s.ended ?: s.started)-s.started)}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(stamp(s.started), style = MaterialTheme.typography.titleMedium)
                        Text(s.coordinates())
                        if (s.label().isNotBlank()) Text(s.label())
                        if (s.notes.isNotBlank()) Text(s.notes, maxLines = 3)
                        s.photo?.let { Photo(it, Modifier.fillMaxWidth().height(110.dp), true) }
                    }
                }
            }
        }
        if (records.orEmpty().any { it.active == null })
            item { TextButton({ clear = true }) { Text("Clear completed history") } }
    }
}

@Composable
fun Details(vm: ParkingModel, s: Spot, dark: Boolean, photoScreen: () -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    var edit by rememberSaveable(s.id) { mutableStateOf(false) }
    var remove by rememberSaveable(s.id) { mutableStateOf(false) }
    var rem by rememberSaveable(s.id) { mutableStateOf(false) }
    var pending by rememberSaveable(s.id) { mutableStateOf<Long?>(null) }
    var captureFile by rememberSaveable(s.id) { mutableStateOf<String?>(null) }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val f = captureFile?.let(::File)
            if (success && f != null)
                vm.photo(s.id, Uri.fromFile(f)).invokeOnCompletion { f.delete() }
            else f?.delete()
            captureFile = null
        }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { vm.photo(s.id, it) }
        }
    val notifications =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok && canNotify(context)) pending?.let { vm.reminder(s.id, it) }
            else vm.message.value = "Notifications are disabled. Enable them in Android settings."
            pending = null
        }
    if (remove)
        Confirm(
            "Delete this parking record?",
            "Its saved coordinates, photo and reminder will be removed.",
            "Delete",
            { remove = false },
            {
                vm.delete(s.id)
                back()
            },
        )
    if (edit)
        NoteEditor(s, { edit = false }) { f, p, z, n ->
            vm.edit(s.id, f, p, z, n)
            edit = false
        }
    if (rem)
        ReminderEditor({ rem = false }) { minutes ->
            rem = false
            if (
                Build.VERSION.SDK_INT >= 33 &&
                    !context.granted(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                pending = minutes
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else vm.reminder(s.id, minutes)
        }
    Page {
        Panel {
            Title(
                s.label().ifBlank {
                    if (s.active == 1) "Current parking spot" else "Past parking spot"
                }
            )
            Text(stamp(s.started))
            Text(s.coordinates())
            Text("Saved fix · ${accuracy(s.accuracy)}")
            s.ended?.let { Text("Parking duration · ${elapsed(it-s.started)}") }
        }
        Card(shape = RoundedCornerShape(18.dp)) {
            ParkingMap(s, null, dark) {
                vm.message.value = stamp(s.started) + " · " + s.coordinates()
            }
        }
        Button({ navigate(context, s, vm) }, Modifier.fillMaxWidth()) {
            Text("Navigate with Google Maps")
        }
        Panel {
            Title("Parking notes")
            for ((label, value) in
                listOf("Floor" to s.floor, "Pillar" to s.pillar, "Zone" to s.zone)) if (
                value.isNotBlank()
            )
                Text("$label · $value")
            Text(s.notes.ifBlank { "Add a landmark to help find your vehicle." })
            TextButton({ edit = true }) { Text("Edit floor, pillar & notes") }
        }
        Panel {
            Title("Parking photo")
            s.photo?.let {
                Photo(
                    it,
                    Modifier.fillMaxWidth().height(160.dp).clickable(onClick = photoScreen),
                    true,
                )
            }
            Row {
                TextButton({
                    try {
                        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                        val f = File.createTempFile("park-", ".jpg", dir)
                        captureFile = f.path
                        camera.launch(
                            FileProvider.getUriForFile(context, "${context.packageName}.photos", f)
                        )
                    } catch (e: Exception) {
                        captureFile?.let { File(it).delete() }
                        captureFile = null
                        vm.message.value = "Camera unavailable. Choose a photo instead."
                    }
                }) {
                    Text("Take photo")
                }
                TextButton({
                    try {
                        picker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        vm.message.value = "No image picker available."
                    }
                }) {
                    Text("Choose photo")
                }
            }
        }
        if (s.active == 1)
            Panel {
                Title("Parking reminder")
                Text(
                    s.reminder?.let { "Scheduled for ${stamp(it)}" }
                        ?: "Set a reminder before your parking expires."
                )
                Text(
                    "Android may delay reminders to conserve battery.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row {
                    TextButton({ rem = true }) {
                        Text(if (s.reminder == null) "Set reminder" else "Change time")
                    }
                    if (s.reminder != null)
                        TextButton({ vm.reminder(s.id, null) }) { Text("Cancel reminder") }
                }
                if (!canNotify(context))
                    TextButton({
                        context.open(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }) {
                        Text("Notification settings")
                    }
            }
        Panel {
            Title("Share this spot")
            Row {
                TextButton({
                    val link =
                        "https://www.google.com/maps/search/?api=1&query=${s.latitude},${s.longitude}"
                    val body =
                        listOf("Parked vehicle", s.label(), s.notes, link)
                            .filter(String::isNotBlank)
                            .joinToString("\n")
                    context.open(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, body),
                            "Share parking spot",
                        )
                    )
                }) {
                    Text("Share location")
                }
                TextButton({
                    context
                        .service<ClipboardManager>()
                        .setPrimaryClip(
                            ClipData.newPlainText("Parking coordinates", s.coordinates())
                        )
                    vm.message.value = "Coordinates copied."
                }) {
                    Text("Copy coordinates")
                }
            }
        }
        TextButton({ remove = true }) {
            Text("Delete this record", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun NoteEditor(s: Spot, close: () -> Unit, save: (String, String, String, String) -> Unit) {
    var f by rememberSaveable { mutableStateOf(s.floor) }
    var p by rememberSaveable { mutableStateOf(s.pillar) }
    var z by rememberSaveable { mutableStateOf(s.zone) }
    var n by rememberSaveable { mutableStateOf(s.notes) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Parking details") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(f, { f = it }, label = { Text("Floor") }, singleLine = true)
                OutlinedTextField(p, { p = it }, label = { Text("Pillar") }, singleLine = true)
                OutlinedTextField(z, { z = it }, label = { Text("Zone") }, singleLine = true)
                OutlinedTextField(n, { n = it }, label = { Text("Notes") }, maxLines = 5)
            }
        },
        confirmButton = { TextButton({ save(f, p, z, n) }) { Text("Save") } },
        dismissButton = { TextButton(close) { Text("Cancel") } },
    )
}

@Composable
private fun ReminderEditor(close: () -> Unit, save: (Long) -> Unit) {
    var value by rememberSaveable { mutableStateOf("30") }
    val minutes = value.toLongOrNull()?.takeIf { it in 1..525600 }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Remind me in") },
        text = {
            Column {
                Row {
                    TextButton({ value = "30" }) { Text("30 minutes") }
                    TextButton({ value = "60" }) { Text("1 hour") }
                }
                OutlinedTextField(
                    value,
                    { value = it },
                    label = { Text("Minutes") },
                    singleLine = true,
                    isError = minutes == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton({ minutes?.let(save) }, enabled = minutes != null) { Text("Schedule") }
        },
        dismissButton = { TextButton(close) { Text("Cancel") } },
    )
}
