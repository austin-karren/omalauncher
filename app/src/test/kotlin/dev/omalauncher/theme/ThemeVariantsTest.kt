package dev.omalauncher.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeVariantsTest {
    private val tokyo = ThemeColors.resolve(mapOf("mode" to "dark", "background" to "#1a1b26", "foreground" to "#a9b1d6", "accent" to "#7aa2f7", "red" to "#f7768e"))
    private val latte = ThemeColors.resolve(mapOf("mode" to "light", "background" to "#eff1f5", "foreground" to "#4c4f69", "accent" to "#1e66f5"))
    private val mocha = ThemeColors.resolve(mapOf("mode" to "dark", "background" to "#1e1e2e", "foreground" to "#cdd6f4", "accent" to "#89b4fa"))

    private fun lookup(vararg themes: Pair<String, Map<String, String>>): (String) -> Map<String, String>? = themes.toMap()::get

    @Test fun knownPairsResolveBothWays() {
        val colorsOf = lookup("catppuccin" to mocha, "catppuccin-latte" to latte)
        assertEquals("catppuccin-latte", ThemeVariants.resolve("catppuccin", mocha, dark = false, colorsOf).name)
        assertEquals("catppuccin", ThemeVariants.resolve("catppuccin-latte", latte, dark = true, colorsOf).name)
        assertEquals("catppuccin", ThemeVariants.resolve("catppuccin", mocha, dark = true, colorsOf).name)
    }

    @Test fun aThemeNamesItsCounterpart() {
        val mine = mocha + (ThemeVariants.LIGHT_KEY to "my-light")
        val colorsOf = lookup("mine" to mine, "my-light" to latte)
        assertEquals("my-light", ThemeVariants.counterpart("mine", mine, colorsOf))
        // The light one finds it from the dark one's colors.toml.
        assertEquals("mine", ThemeVariants.counterpart("my-light", latte, colorsOf) { sequenceOf("mine" to mine) })
        // A counterpart of the same mode, or one that isn't installed, doesn't count.
        assertNull(ThemeVariants.counterpart("x", mocha + (ThemeVariants.LIGHT_KEY to "mine"), colorsOf))
        assertNull(ThemeVariants.counterpart("x", mocha + (ThemeVariants.LIGHT_KEY to "gone"), colorsOf))
    }

    @Test fun derivesAReadableOtherVariant() {
        val r = ThemeVariants.resolve("tokyo-night", tokyo, dark = false, lookup("tokyo-night" to tokyo))
        assertTrue(r.derived)
        assertEquals("tokyo-night", r.name)
        val c = r.colors
        assertEquals("light", c["mode"])
        assertTrue(ThemeVariants.luminance(c.getValue("background")) > 0.75)
        assertTrue(ThemeVariants.contrast(c.getValue("foreground"), c.getValue("background")) >= 7.0)
        assertTrue(ThemeVariants.contrast(c.getValue("accent"), c.getValue("background")) >= 3.0)
        assertTrue(ThemeVariants.contrast(c.getValue("red"), c.getValue("background")) >= 3.0)
        // What ThemePalette reads is all there.
        for (key in listOf("lighter_background", "dark_foreground", "muted", "color0", "cursor")) assertTrue(key, c[key] != null)
        val back = ThemeVariants.derive(latte, light = false)
        assertEquals("dark", back["mode"])
        assertTrue(ThemeVariants.luminance(back.getValue("background")) < 0.05)
    }

    @Test fun appearance() {
        assertTrue(ThemeVariants.wantsDark(Style.APPEARANCE_SYSTEM, systemDark = true))
        assertFalse(ThemeVariants.wantsDark(Style.APPEARANCE_SYSTEM, systemDark = false))
        assertFalse(ThemeVariants.wantsDark(Style.APPEARANCE_LIGHT, systemDark = true))
        assertTrue(ThemeVariants.wantsDark(Style.APPEARANCE_DARK, systemDark = false))
    }

    @Test fun appearanceInStyleToml() {
        assertEquals(Style.APPEARANCE_SYSTEM, StyleFile.decode(Toml.parse("theme = \"tokyo-night\"")).style[Style.APPEARANCE])
        val decoded = StyleFile.decode(Toml.parse("appearance = \"Dark\""))
        assertEquals(Style.APPEARANCE_DARK, decoded.style[Style.APPEARANCE])
        assertEquals(1, StyleFile.decode(Toml.parse("appearance = \"dim\"")).problems.size)
    }
}
