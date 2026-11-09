package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import java.util.Locale

val LocalAppLanguage = compositionLocalOf { "en" }

fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return ctx as? Activity
}

fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return ctx as? FragmentActivity
}

@Composable
fun ProvideAppLanguage(
    languageCode: String,
    content: @Composable () -> Unit
) {
    androidx.compose.runtime.LaunchedEffect(languageCode) {
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
    }

    CompositionLocalProvider(
        LocalAppLanguage provides languageCode,
        content = content
    )
}

@Composable
@ReadOnlyComposable
fun tr(en: String, hi: String): String {
    return if (LocalAppLanguage.current == "hi") hi else en
}

fun Context.withLocale(languageCode: String): Context {
    val locale = Locale.forLanguageTag(languageCode)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}
