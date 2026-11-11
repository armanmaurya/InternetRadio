package com.armanmaurya.internetradio.ui.shared.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppColorTest {

    @Test
    fun testAllAppColorEntriesProvideValidColorSchemes() {
        assertEquals(9, AppColor.entries.size)

        for (color in AppColor.entries) {
            val lightScheme = color.getLightColorScheme()
            val darkScheme = color.getDarkColorScheme()

            assertNotNull("Light scheme for $color should not be null", lightScheme)
            assertNotNull("Dark scheme for $color should not be null", darkScheme)

            assertEquals(lightScheme, color.getColorScheme(darkTheme = false))
            assertEquals(darkScheme, color.getColorScheme(darkTheme = true))

            // Check that primary colors are not unspecified
            assertTrue("Primary light for $color should have alpha > 0", lightScheme.primary.alpha > 0f)
            assertTrue("Primary dark for $color should have alpha > 0", darkScheme.primary.alpha > 0f)
        }
    }

    @Test
    fun testPresetPrimaryColors() {
        assertEquals(Color(0xFF7B1FA2), AppColor.PURPLE.previewColor)
        assertEquals(Color(0xFF0066CC), AppColor.BLUE.previewColor)
        assertEquals(Color(0xFF00796B), AppColor.TEAL.previewColor)
        assertEquals(Color(0xFF2E7D32), AppColor.GREEN.previewColor)
        assertEquals(Color(0xFFB26A00), AppColor.YELLOW.previewColor)
        assertEquals(Color(0xFFE91E63), AppColor.PINK.previewColor)
        assertEquals(Color(0xFFC62828), AppColor.RED.previewColor)
        assertEquals(Color(0xFF00BCD4), AppColor.CUSTOM.previewColor)

        // Ensure every preset has distinct secondaryContainer and surfaceContainerLow
        for (color in AppColor.entries) {
            val light = color.getLightColorScheme()
            val dark = color.getDarkColorScheme()

            assertTrue("Light primary should be defined for $color", light.primary.alpha > 0f)
            assertTrue("Dark primary should be defined for $color", dark.primary.alpha > 0f)

            // Verify secondaryContainer does not merge with surfaceContainerLow
            org.junit.Assert.assertNotEquals(
                "secondaryContainer should not equal surfaceContainerLow in light mode for $color",
                light.secondaryContainer,
                light.surfaceContainerLow
            )
            org.junit.Assert.assertNotEquals(
                "secondaryContainer should not equal surfaceContainerLow in dark mode for $color",
                dark.secondaryContainer,
                dark.surfaceContainerLow
            )
        }

        val customLight = AppColor.CUSTOM.getColorScheme(darkTheme = false, customColorArgb = 0xFF00BCD4.toInt())
        val customDark = AppColor.CUSTOM.getColorScheme(darkTheme = true, customColorArgb = 0xFF00BCD4.toInt())
        assertTrue(customLight.primary.alpha > 0f)
        assertTrue(customDark.primary.alpha > 0f)
    }

    @Test
    fun testPreviewColors() {
        for (color in AppColor.entries) {
            val lightPreview = color.getPreviewColors(darkTheme = false)
            val darkPreview = color.getPreviewColors(darkTheme = true)

            assertEquals(4, lightPreview.size)
            assertEquals(4, darkPreview.size)

            for (c in lightPreview) {
                assertTrue("Light preview color should have alpha > 0", c.alpha > 0f)
            }
            for (c in darkPreview) {
                assertTrue("Dark preview color should have alpha > 0", c.alpha > 0f)
            }
        }
    }

    @Test
    fun testGreyscaleCustomColorsUseMonochromeScheme() {
        for (argb in listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF808080.toInt(), 0xFF202020.toInt(), 0xFFF0F0F0.toInt())) {
            assertEquals(
                AppColor.MONOCHROME.getLightColorScheme(),
                AppColor.CUSTOM.getColorScheme(darkTheme = false, customColorArgb = argb)
            )
            assertEquals(
                AppColor.MONOCHROME.getDarkColorScheme(),
                AppColor.CUSTOM.getColorScheme(darkTheme = true, customColorArgb = argb)
            )
        }
    }

    @Test
    fun testColorfulCustomColorDoesNotUseMonochromeScheme() {
        val blue = 0xFF0066CC.toInt()
        org.junit.Assert.assertNotEquals(
            AppColor.MONOCHROME.getDarkColorScheme(),
            AppColor.CUSTOM.getColorScheme(darkTheme = true, customColorArgb = blue)
        )
        org.junit.Assert.assertNotEquals(
            AppColor.MONOCHROME.getLightColorScheme(),
            AppColor.CUSTOM.getColorScheme(darkTheme = false, customColorArgb = blue)
        )
    }

    @Test
    fun testLookupByNameFallback() {
        val foundValid = AppColor.entries.find { it.name == "BLUE" } ?: AppColor.PURPLE
        assertEquals(AppColor.BLUE, foundValid)

        val fallbackInvalid = AppColor.entries.find { it.name == "NON_EXISTENT" } ?: AppColor.PURPLE
        assertEquals(AppColor.PURPLE, fallbackInvalid)

        val fallbackNull = AppColor.entries.find { it.name == null } ?: AppColor.PURPLE
        assertEquals(AppColor.PURPLE, fallbackNull)
    }

    @Test
    fun testGreyscaleColorsHaveNoHue() {
        for (argb in listOf(0xFF000000, 0xFFFFFFFF, 0xFF808080, 0xFF202020, 0xFFF0F0F0)) {
            org.junit.Assert.assertNull("Expected no hue for %08X".format(argb), hueOfColor(argb.toInt()))
        }
    }

    @Test
    fun testHueOfColor() {
        assertEquals(0f, hueOfColor(0xFFFF0000.toInt())!!, 0.5f)
        assertEquals(120f, hueOfColor(0xFF00FF00.toInt())!!, 0.5f)
        assertEquals(240f, hueOfColor(0xFF0000FF.toInt())!!, 0.5f)
        assertEquals(210f, hueOfColor(0xFF0066CC.toInt())!!, 0.5f)
    }

    @Test
    fun testCustomColorFromHueIsNeverGreyscaleAndKeepsHue() {
        var hue = 0f
        while (hue < 360f) {
            val argb = customColorFromHue(hue)
            val roundTrip = hueOfColor(argb)
            org.junit.Assert.assertNotNull("Custom colour for hue $hue must not be greyscale", roundTrip)
            val diff = kotlin.math.abs(roundTrip!! - hue).let { minOf(it, 360f - it) }
            assertTrue("Hue $hue round-tripped to $roundTrip", diff < 1.5f)
            hue += 15f
        }
    }
}
