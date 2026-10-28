package dev.omalauncher.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Every theme in a light and a dark variant, the one shown picked by
 * [Style.APPEARANCE] (Android's dark theme, by default). A theme's
 * counterpart, in order:
 *
 * 1. the theme its colors.toml names: `light_variant = "…"` in a dark theme,
 *    `dark_variant = "…"` in a light one;
 * 2. a theme whose colors.toml names it so;
 * 3. its pair in [PAIRS], for themes that don't name each other.
 *
 * Only a theme that's installed and of the other mode counts. A theme
 * without one gets its other variant [derive]d from its own colors.
 */
object ThemeVariants {
    /** In a dark theme's colors.toml: its light counterpart's directory name. */
    const val LIGHT_KEY = "light_variant"

    /** In a light theme's colors.toml: its dark counterpart's directory name. */
    const val DARK_KEY = "dark_variant"

    /** Light and dark themes made as a pair, dark first: Omarchy's and the Nothing ones. */
    val PAIRS: List<Pair<String, String>> =
        listOf(
            "catppuccin" to "catppuccin-latte",
            "nothing-dark" to "nothing-light",
            "vantablack" to "white",
        )

    fun isLight(colors: Map<String, String>) = colors["mode"] == "light"

    /** Whether the dark variant shows, for [Style.APPEARANCE]'s [appearance] and whether Android's dark theme is on. */
    fun wantsDark(
        appearance: String,
        systemDark: Boolean,
    ) = when (appearance) {
        Style.APPEARANCE_LIGHT -> false
        Style.APPEARANCE_DARK -> true
        else -> systemDark
    }

    /**
     * The name of [name]'s theme of the other mode, or null without one.
     * [colorsOf] gives an installed theme's colors (null for one that isn't);
     * [others] lists the themes to search for one naming [name], and may be
     * empty where reading them all costs too much.
     */
    fun counterpart(
        name: String,
        colors: Map<String, String>,
        colorsOf: (String) -> Map<String, String>?,
        others: () -> Sequence<Pair<String, Map<String, String>>> = { emptySequence() },
    ): String? {
        val light = isLight(colors)

        fun fits(other: String) = other != name && colorsOf(other)?.let { isLight(it) != light } == true
        colors[if (light) DARK_KEY else LIGHT_KEY]?.trim()?.takeIf { fits(it) }?.let { return it }
        // A dark theme names its light counterpart with LIGHT_KEY, so a light theme looks for that one.
        val back = if (light) LIGHT_KEY else DARK_KEY
        others().firstOrNull { (other, c) -> other != name && isLight(c) != light && c[back]?.trim() == name }?.let { return it.first }
        return PAIRS
            .firstNotNullOfOrNull { (dark, lightName) ->
                when (name) {
                    dark -> lightName
                    lightName -> dark
                    else -> null
                }
            }?.takeIf { fits(it) }
    }

    /** What shows for a theme: [name]'s directory with [colors], [derived] when they're computed rather than its own. */
    class Resolved(
        val name: String,
        val colors: Map<String, String>,
        val derived: Boolean,
    )

    /** [name]'s variant for [dark]: itself, its [counterpart], or [derive]d from it. */
    fun resolve(
        name: String,
        colors: Map<String, String>,
        dark: Boolean,
        colorsOf: (String) -> Map<String, String>?,
        others: () -> Sequence<Pair<String, Map<String, String>>> = { emptySequence() },
    ): Resolved {
        if (isLight(colors) != dark) return Resolved(name, colors, false)
        counterpart(name, colors, colorsOf, others)?.let { other -> colorsOf(other)?.let { return Resolved(other, it, false) } }
        return Resolved(name, derive(colors, light = !dark), true)
    }

    private const val WHITE = "#ffffff"
    private const val BLACK = "#000000"

    /** The colors that carry meaning; each keeps its hue and is only pushed to stay readable on the new background. */
    private val HUES =
        listOf("accent", "red", "green", "yellow", "blue", "magenta", "cyan", "orange", "brown").let { names ->
            names + names.map { "bright_$it" }
        }

    /**
     * A theme's other variant from its [colors] ([ThemeColors.resolve]d):
     * the background takes a tint of the old foreground, pushed nearly to
     * white (for [light]) or black; the foreground is the old background,
     * kept far from the new one; surface, secondary text, border and
     * selection are steps between the two; the accent and the other hues
     * stay, pushed toward the foreground until they read on the background.
     * Colors that aren't #rrggbb leave the theme as it was.
     */
    fun derive(
        colors: Map<String, String>,
        light: Boolean,
    ): Map<String, String> =
        runCatching {
            val oldBackground = checkNotNull(colors["background"])
            val oldForeground = checkNotNull(colors["foreground"])
            val extreme = if (light) WHITE else BLACK
            val ink = if (light) BLACK else WHITE
            var background = ThemeColors.mix(oldForeground, extreme, if (light) 0.85 else 0.8)
            repeat(10) { if (light && luminance(background) < 0.75 || !light && luminance(background) > 0.03) background = ThemeColors.mix(background, extreme, 0.3) }
            val foreground = readable(oldBackground, background, 7.0, ink)
            val raw = HashMap<String, String>()
            for (key in HUES) colors[key]?.let { raw[key] = readable(it, background, 3.0, ink) }
            raw["mode"] = if (light) "light" else "dark"
            raw["background"] = background
            raw["foreground"] = foreground
            raw["lighter_background"] = ThemeColors.mix(background, foreground, if (light) 0.06 else 0.08)
            raw["dark_background"] = ThemeColors.mix(background, BLACK, if (light) 0.06 else 0.25)
            raw["darker_background"] = ThemeColors.mix(background, BLACK, if (light) 0.12 else 0.5)
            raw["dark_foreground"] = readable(ThemeColors.mix(foreground, background, 0.45), background, 3.0, ink)
            raw["light_foreground"] = ThemeColors.mix(foreground, background, 0.1)
            raw["bright_foreground"] = ThemeColors.mix(foreground, ink, 0.5)
            raw["muted"] = ThemeColors.mix(background, foreground, 0.22)
            raw["selection"] = ThemeColors.mix(background, foreground, 0.15)
            ThemeColors.resolve(raw)
        }.getOrDefault(colors)

    /** [color], mixed toward [ink] until it has [ratio] contrast with [background]. */
    private fun readable(
        color: String,
        background: String,
        ratio: Double,
        ink: String,
    ): String {
        var c = color
        repeat(10) {
            if (contrast(c, background) >= ratio) return c
            c = ThemeColors.mix(c, ink, 0.2)
        }
        return c
    }

    /** WCAG's relative luminance of #rrggbb, 0 for black to 1 for white. */
    fun luminance(hex: String): Double {
        fun channel(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val c = ThemeColors.rgb(hex)
        return 0.2126 * channel(c[0]) + 0.7152 * channel(c[1]) + 0.0722 * channel(c[2])
    }

    /** WCAG's contrast ratio of two #rrggbb colors, from 1 (the same) to 21 (black on white). */
    fun contrast(
        a: String,
        b: String,
    ): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    /** [colors] as a colors.toml, for Linux to read a derived variant's. */
    fun toToml(colors: Map<String, String>): String =
        buildString {
            append("# Derived by Omalauncher from the theme's other variant; see ThemeVariants.kt.\n")
            for ((k, v) in colors.toSortedMap()) if ('"' !in v && '\n' !in v && '\\' !in v) append(k).append(" = \"").append(v).append("\"\n")
        }
}
