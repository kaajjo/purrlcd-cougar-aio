package purrlcd.resources

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString

class StringResourcesTest {
    @Test
    fun packagedStringsSelectLocaleFormatArgumentsAndFallBackToEnglish() = runBlocking {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
            assertEquals("Применить", getString(Res.string.apply))
            assertEquals("3 сек", getString(Res.string.duration_seconds, 3))
            assertEquals("37%", getString(Res.string.settings_brightness_value, 37))

            Locale.setDefault(Locale.forLanguageTag("en-US"))
            assertEquals("Apply", getString(Res.string.apply))
            assertEquals("3 s", getString(Res.string.duration_seconds, 3))
            assertEquals("37%", getString(Res.string.settings_brightness_value, 37))

            Locale.setDefault(Locale.forLanguageTag("fr-FR"))
            assertEquals("Apply", getString(Res.string.apply))
            assertEquals("3 s", getString(Res.string.duration_seconds, 3))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
