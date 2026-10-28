package de.briefampel.app.ui

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import de.briefampel.app.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class MainActivityTest {
    @Test fun `launcher creates its real application and viewmodel factory`() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.create().get()
            val model = ViewModelProvider(activity)[BriefampelViewModel::class.java]
            assertEquals(Screen.INPUT, model.state.value.screen)
            assertFalse(model.state.value.input.hasContent)
        }
    }

    @Test fun `android text sharing imports the original without starting an analysis`() {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Text aus der Mail-App")
        }
        Robolectric.buildActivity(MainActivity::class.java, share).use { controller ->
            val activity = controller.create().get()
            val state = ViewModelProvider(activity)[BriefampelViewModel::class.java].state.value
            assertEquals("Text aus der Mail-App", state.input.text)
            assertFalse(state.loading)
            assertNull(state.result)
        }
    }
}
