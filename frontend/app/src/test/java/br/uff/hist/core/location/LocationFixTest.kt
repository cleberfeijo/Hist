package br.uff.hist.core.location

import android.location.Location
import br.uff.hist.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class LocationFixTest {
    private fun location(lat: Double = -22.5, lon: Double = -41.9, accuracy: Float = 12f) =
        Location("test").apply {
            latitude = lat
            longitude = lon
            this.accuracy = accuracy
        }

    @Test
    fun copiesPointAndAccuracy() {
        val fix = location(-22.5269, -41.9450, 7.5f).toFix()
        assertEquals(GeoPoint(-22.5269, -41.9450), fix.point)
        assertEquals(7.5f, fix.accuracyM, 0f)
        assertFalse(fix.isMock)
    }

    @Test
    fun mockFlagOnApi31Plus() {
        val loc = location().apply { isMock = true }
        assertTrue(loc.toFix().isMock)
    }

    @Test
    @Config(sdk = [30])
    fun mockFlagOnApi30UsesIsFromMockProvider() {
        val loc = location()
        Location::class.java.getMethod("setIsFromMockProvider", Boolean::class.javaPrimitiveType).invoke(loc, true)
        assertTrue(loc.toFix().isMock)
    }
}
