package dev.omalauncher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/**
 * A monospace font for text whose characters must line up: the editor, the extra keys, a widget's
 * `font: mono` and an old widget's canvas text. LauncherFont sets it: [LauncherStyle.font] when that is
 * monospace (Android's, Geist Mono or a Linux family), Geist Mono when it's the proportional Geist.
 */
object MonoFont {
    var family by mutableStateOf<FontFamily>(FontFamily.Monospace)
}

/** [Mono] in [MonoFont.family]: the same text style, but always monospace. */
val FixedMono: TextStyle
    get() {
        val mono = Mono
        val family = MonoFont.family
        fixedMonoCache?.let { if (it.first === mono && it.second === family) return it.third }
        return mono.copy(fontFamily = family).also { fixedMonoCache = Triple(mono, family, it) }
    }

/** [FixedMono] for the last [Mono] and font, as it's read for every piece of text. */
private var fixedMonoCache: Triple<TextStyle, FontFamily, TextStyle>? = null
