package br.uff.hist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildConfigTest {
    @Test
    fun defaultDebugBaseUrlPointsToEmulatorHost() {
        assertEquals("http://10.0.2.2:8080/v1/", BuildConfig.DEFAULT_DEBUG_API_BASE_URL)
    }

    @Test
    fun baseUrlIsAnHttpUrlEndingInV1EvenWithLocalOverride() {
        val url = BuildConfig.API_BASE_URL
        assertTrue(url, url.startsWith("http://") || url.startsWith("https://"))
        assertTrue(url, url.endsWith("/v1/"))
    }
}
