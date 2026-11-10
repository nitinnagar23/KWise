package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Theme-aware replacement for `Color(0xAARRGGBB)` literals that were written for the light theme.
 *
 * Light theme: returns the literal unchanged.
 * Dark theme (guidelines §6.1): returns an equivalent that keeps the hue but is readable on dark surfaces:
 *   near-white fills        -> dark surface / fill / tint
 *   light borders           -> dark border
 *   dark slate or navy text -> light ink / muted text
 *   dark green/red/amber/blue accents -> lightened to a mid-tone that works as text and as a fill with white text
 * Reading KwThemeState.isDark inside composition makes callers recompose when the theme changes.
 */
fun kw(argb: Long): Color {
    if (!KwThemeState.isDark) return Color(argb)
    return Color(darkCache.getOrPut(argb) { toDark(argb) })
}

private val darkCache = ConcurrentHashMap<Long, Long>()

private fun lin(c: Double): Double = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
private fun lum(r: Int, g: Int, b: Int): Double =
    0.2126 * lin(r / 255.0) + 0.7152 * lin(g / 255.0) + 0.0722 * lin(b / 255.0)

private fun toDark(argb: Long): Long {
    val a = ((argb shr 24) and 0xFF).toInt()
    val r = ((argb shr 16) and 0xFF).toInt()
    val g = ((argb shr 8) and 0xFF).toInt()
    val b = (argb and 0xFF).toInt()
    if (a == 0 || (r == 0 && g == 0 && b == 0) || (r == 255 && g == 255 && b == 255)) return argb

    val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
    val chroma = (mx - mn) / 255.0
    val y = lum(r, g, b)

    val (nr, ng, nb) = if (chroma <= 0.17) {
        val v = when {
            y >= 0.97 -> 0x1A2330
            y >= 0.80 -> 0x243040
            y >= 0.55 -> 0x2A3646
            y >= 0.12 -> 0x9AA8BA
            y >= 0.06 -> 0xB4C0D0
            else -> 0xE8EEF5
        }
        Triple((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)
    } else {
        val (h, s, _) = rgbToHsl(r, g, b)
        when {
            y >= 0.80 -> hslToRgb(h, min(s * 0.5, 0.40), 0.16)
            y >= 0.55 -> hslToRgb(h, min(s * 0.5, 0.40), 0.27)
            y >= 0.215 -> Triple(r, g, b)
            else -> {
                var l = rgbToHsl(r, g, b).third
                var out = Triple(r, g, b)
                val sat = min(s, 0.75)
                while (l < 0.75) {
                    out = hslToRgb(h, sat, l)
                    if (lum(out.first, out.second, out.third) >= 0.26) break
                    l += 0.02
                }
                out
            }
        }
    }
    return (a.toLong() shl 24) or (nr.toLong() shl 16) or (ng.toLong() shl 8) or nb.toLong()
}

private fun rgbToHsl(r: Int, g: Int, b: Int): Triple<Double, Double, Double> {
    val rf = r / 255.0; val gf = g / 255.0; val bf = b / 255.0
    val mx = max(rf, max(gf, bf)); val mn = min(rf, min(gf, bf))
    val l = (mx + mn) / 2
    val d = mx - mn
    if (d == 0.0) return Triple(0.0, 0.0, l)
    val s = if (l > 0.5) d / (2 - mx - mn) else d / (mx + mn)
    val h = when (mx) {
        rf -> ((gf - bf) / d + (if (gf < bf) 6 else 0))
        gf -> ((bf - rf) / d + 2)
        else -> ((rf - gf) / d + 4)
    } / 6.0
    return Triple(h, s, l)
}

private fun hslToRgb(h: Double, s: Double, l: Double): Triple<Int, Int, Int> {
    if (s == 0.0) { val v = (l * 255).toInt(); return Triple(v, v, v) }
    val q = if (l < 0.5) l * (1 + s) else l + s - l * s
    val p = 2 * l - q
    fun hue(t0: Double): Double {
        var t = t0
        if (t < 0) t += 1.0
        if (t > 1) t -= 1.0
        return when {
            t < 1.0 / 6 -> p + (q - p) * 6 * t
            t < 1.0 / 2 -> q
            t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
            else -> p
        }
    }
    fun ch(v: Double) = (abs(v) * 255).toInt().coerceIn(0, 255)
    return Triple(ch(hue(h + 1.0 / 3)), ch(hue(h)), ch(hue(h - 1.0 / 3)))
}

/** White card/sheet background in light theme, dark surface (§6.1) in dark theme. */
val KwSurface: Color get() = if (KwThemeState.isDark) Color(0xFF1A2330) else Color.White
