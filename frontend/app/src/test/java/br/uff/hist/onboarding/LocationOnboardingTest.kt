package br.uff.hist.onboarding

import android.Manifest
import android.content.Intent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import br.uff.hist.core.designsystem.HistTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocationOnboardingTest {
    @get:Rule
    val compose = createComposeRule()

    private class FakeRegistry(private val granted: Boolean) : ActivityResultRegistry() {
        var requested: List<String> = emptyList()

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            @Suppress("UNCHECKED_CAST")
            requested = (input as Array<String>).toList()
            val result = Intent()
                .putExtra(ActivityResultContracts.RequestMultiplePermissions.EXTRA_PERMISSIONS, input)
                .putExtra(
                    ActivityResultContracts.RequestMultiplePermissions.EXTRA_PERMISSION_GRANT_RESULTS,
                    IntArray(input.size) { if (granted) 0 else -1 },
                )
            dispatchResult(requestCode, android.app.Activity.RESULT_OK, result)
        }
    }

    private fun run(granted: Boolean): Pair<FakeRegistry, Int> {
        val registry = FakeRegistry(granted)
        var done = 0
        compose.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                    override val activityResultRegistry = registry
                },
            ) {
                HistTheme { LocationOnboardingScreen(onDone = { done++ }) }
            }
        }
        compose.onNodeWithText("Descubra a história ao seu redor").assertIsDisplayed()
        compose.onNodeWithText("Continuar").performClick()
        compose.waitForIdle()
        return registry to done
    }

    @Test
    fun deniedPermissionStillFinishes() {
        val (registry, done) = run(granted = false)
        assertEquals(1, done)
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            registry.requested,
        )
    }

    @Test
    fun grantedPermissionFinishes() {
        val (_, done) = run(granted = true)
        assertEquals(1, done)
    }
}
