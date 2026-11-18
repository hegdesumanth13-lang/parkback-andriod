package com.parkback.pro

import android.app.Application
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ParkingModel(app: Application) : AndroidViewModel(app) {
    private val appContext = app as ParkApp
    private val db = appContext.db
    private val dao = db.spots()
    val gps = Gps(app)
    val records =
        dao.observe()
            .map<List<Spot>, List<Spot>?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val key = stringPreferencesKey("appearance")
    val theme =
        app.preferences.data
            .map { it[key] ?: "System" }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "System")
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    private fun perform(block: suspend () -> Unit) =
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = e.localizedMessage ?: "Unable to complete this action."
            }
        }

    fun appearance(value: String) = perform { appContext.preferences.edit { it[key] = value } }

    fun save(expected: Long?) {
        if (busy.value) return
        busy.value = true
        perform {
            try {
                val fix = gps.fix()
                dao.park(
                    Spot(
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                        started = System.currentTimeMillis(),
                        accuracy = fix.accuracy.takeIf { fix.hasAccuracy() },
                    ),
                    expected,
                )
                expected?.let { cancelReminder(appContext, it) }
                message.value = "Parking spot saved."
            } finally {
                busy.value = false
            }
        }
    }

    fun edit(id: Long, floor: String, pillar: String, zone: String, notes: String) = perform {
        dao.edit(id, floor.trim(), pillar.trim(), zone.trim(), notes.trim())
    }

    fun photo(id: Long, uri: Uri) = perform {
        withContext(Dispatchers.IO) {
            val dir = File(appContext.filesDir, "photos").apply { mkdirs() }
            val dest = File.createTempFile("parking-", ".image", dir)
            try {
                val input =
                    requireNotNull(appContext.contentResolver.openInputStream(uri)) {
                        "Photo unavailable."
                    }
                input.use { src -> dest.outputStream().use { out -> src.copyTo(out) } }
                val options =
                    android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(dest.path, options)
                require(options.outWidth > 0) { "This image format could not be read." }
                val old =
                    db.withTransaction {
                        val previous = dao.get(id)?.photo
                        check(dao.picture(id, dest.path) == 1) { "Parking record was removed." }
                        previous
                    }
                old?.let { File(it).delete() }
            } catch (e: Exception) {
                dest.delete()
                throw e
            }
        }
    }

    fun delete(id: Long?) = perform {
        val removed =
            db.withTransaction {
                if (id == null) dao.past().also { dao.clearHistory() }
                else listOfNotNull(dao.get(id)).also { dao.delete(id) }
            }
        withContext(Dispatchers.IO) {
            removed.forEach { s ->
                s.photo?.let { File(it).delete() }
                cancelReminder(appContext, s.id)
            }
        }
    }

    fun reminder(id: Long, minutes: Long?) = perform {
        val whenDue = minutes?.let { System.currentTimeMillis() + it * 60_000L }
        check(dao.remind(id, whenDue) == 1) { "This parking session has ended." }
        if (whenDue == null) cancelReminder(appContext, id)
        else
            try {
                scheduleReminder(appContext, id, whenDue)
            } catch (e: Exception) {
                dao.remind(id, null)
                throw e
            }
    }
}
