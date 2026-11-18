package com.parkback.pro

import android.app.Application
import android.content.Context
import android.location.Location
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import java.util.Locale
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "spots", indices = [Index(value = ["active"], unique = true)])
data class Spot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val started: Long,
    val accuracy: Float?,
    val active: Int? = 1,
    val ended: Long? = null,
    val floor: String = "",
    val pillar: String = "",
    val zone: String = "",
    val notes: String = "",
    val photo: String? = null,
    val reminder: Long? = null,
) {
    fun position() =
        Location("parking").apply {
            latitude = this@Spot.latitude
            longitude = this@Spot.longitude
        }

    fun label() = listOf(floor, pillar, zone).filter(String::isNotBlank).joinToString(" · ")

    fun coordinates() = String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
}

fun distance(here: Location, spot: Spot) = here.distanceTo(spot.position())

@Dao
abstract class ParkingStore {
    @Query("SELECT * FROM spots ORDER BY started DESC, id DESC")
    abstract fun observe(): Flow<List<Spot>>

    @Query("SELECT * FROM spots WHERE id=:id") abstract suspend fun get(id: Long): Spot?

    @Query("SELECT * FROM spots WHERE active=1 LIMIT 1") abstract suspend fun current(): Spot?

    @Insert protected abstract suspend fun insert(spot: Spot): Long

    @Query("UPDATE spots SET ended=MAX(started,:now), active=NULL, reminder=NULL WHERE active=1")
    protected abstract suspend fun finish(now: Long)

    @Transaction
    open suspend fun park(spot: Spot, expected: Long?): Long {
        require(validCoordinates(spot.latitude, spot.longitude))
        check(current()?.id == expected) { "Active parking changed. Try saving again." }
        finish(spot.started)
        return insert(spot)
    }

    @Query("UPDATE spots SET floor=:floor,pillar=:pillar,zone=:zone,notes=:notes WHERE id=:id")
    abstract suspend fun edit(id: Long, floor: String, pillar: String, zone: String, notes: String)

    @Query("UPDATE spots SET photo=:path WHERE id=:id")
    abstract suspend fun picture(id: Long, path: String): Int

    @Query("UPDATE spots SET reminder=:time WHERE id=:id AND active=1")
    abstract suspend fun remind(id: Long, time: Long?): Int

    @Query("DELETE FROM spots WHERE id=:id") abstract suspend fun delete(id: Long)

    @Query("DELETE FROM spots WHERE active IS NULL") abstract suspend fun clearHistory()

    @Query("SELECT * FROM spots WHERE active IS NULL") abstract suspend fun past(): List<Spot>
}

@Database(entities = [Spot::class], version = 1, exportSchema = false)
abstract class ParkingDb : RoomDatabase() {
    abstract fun spots(): ParkingStore
}

val Context.preferences by preferencesDataStore("settings")

class ParkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    val db by lazy { Room.databaseBuilder(this, ParkingDb::class.java, "parking.db").build() }
}

internal inline fun <reified T : Any> Context.service(): T =
    requireNotNull(getSystemService(T::class.java))
