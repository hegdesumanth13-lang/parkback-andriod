package com.parkback.pro

import android.location.Location
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParkingDeviceTest {
    @Test
    fun distanceUsesDeviceGeodesic() {
        val origin =
            Location("test").apply {
                latitude = 0.0
                longitude = 0.0
            }
        val s = Spot(latitude = 0.0, longitude = 0.001, started = 1, accuracy = null)
        assertEquals(111.32f, distance(origin, s), 0.5f)
        assertEquals(0f, distance(s.position(), s), 0.1f)
    }

    @Test
    fun databaseSurvivesReopenAndPreservesActiveSession() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "parking-test-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, ParkingDb::class.java, name).build()
        var db = open()
        try {
            var store = db.spots()
            val s = Spot(latitude = 12.94, longitude = 77.69, started = 10, accuracy = 8f)
            val first = store.park(s, null)
            store.edit(first, "B2", "A14", "East", "Blue stairs")
            val second = store.park(s.copy(started = 20), first)
            assertTrue(runCatching { store.park(s, first) }.isFailure)
            db.close()
            db = open()
            store = db.spots()
            assertEquals(listOf(second, first), store.observe().first().map { it.id })
            assertEquals("A14", store.get(first)!!.pillar)
            assertEquals(20L, store.get(first)!!.ended!!)
            assertEquals(second, store.current()!!.id)
            val third = store.park(s.copy(started = 30), second)
            store.delete(second)
            assertNull(store.get(second))
            store.clearHistory()
            assertEquals(listOf(third), store.observe().first().map { it.id })
            store.delete(third)
            assertNull(store.current())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
