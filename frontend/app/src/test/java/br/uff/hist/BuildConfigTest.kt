package br.uff.hist

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildConfigTest {
    @Test
    fun debugBaseUrlPointsToEmulatorHost() {
        assertEquals("http://10.0.2.2:8080/v1/", BuildConfig.API_BASE_URL)
    }
}
