package br.uff.hist.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    private val a = GeoPoint(-22.5269, -41.9450)
    private val b = GeoPoint(-22.5300, -41.9400)

    @Test fun samePointIsZero() = assertEquals(0.0, distanceMeters(a, a), 0.0)

    @Test fun oneDegreeOfLongitudeAtEquator() =
        assertEquals(111_195.0, distanceMeters(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0)), 1.0)

    @Test fun isSymmetric() = assertEquals(distanceMeters(a, b), distanceMeters(b, a), 0.0)
}
