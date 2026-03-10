package com.devtrack.ui.i18n

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Internationalization singleton.
 * Loads translation keys from resource bundles and provides a simple `t(key)` lookup.
 *
 * Exposes a reactive [localeState] StateFlow so Compose UI can recompose when locale changes.
 * Usage in composables: `val locale by I18n.localeState.collectAsState()` at the top of
 * the composition tree, then use `I18n.t("key")` anywhere — the collectAsState ensures
 * recomposition propagates down.
 */
object I18n {
    private val logger = LoggerFactory.getLogger(I18n::class.java)
    private var currentLocale: Locale = Locale.FRENCH
    private var bundle: ResourceBundle = loadBundle(currentLocale)

    /**
     * Reactive locale state. Collecting this in a composable ensures the UI re-renders
     * when the locale changes via [setLocale].
     */
    private val _localeState = MutableStateFlow(currentLocale)
    val localeState: StateFlow<Locale> = _localeState.asStateFlow()

    private fun loadBundle(locale: Locale): ResourceBundle {
        return try {
            ResourceBundle.getBundle("i18n.messages", locale)
        } catch (e: MissingResourceException) {
            logger.warn("Missing resource bundle for locale $locale, falling back to French")
            try {
                ResourceBundle.getBundle("i18n.messages", Locale.FRENCH)
            } catch (e2: MissingResourceException) {
                logger.error("No resource bundle found at all", e2)
                // Return an empty bundle as fallback
                object : ResourceBundle() {
                    override fun handleGetObject(key: String): Any? = null
                    override fun getKeys(): Enumeration<String> =
                        Collections.enumeration(emptyList())
                }
            }
        }
    }

    /**
     * Get the current locale.
     */
    fun getLocale(): Locale = currentLocale

    /**
     * Set the locale and reload translations.
     * This also updates [localeState], triggering recomposition in any composable
     * that collects it.
     */
    fun setLocale(locale: Locale) {
        currentLocale = locale
        bundle = loadBundle(locale)
        _localeState.value = locale
        logger.info("Locale changed to: {}", locale)
    }

    /**
     * Set locale from a language tag string (e.g., "fr", "en").
     */
    fun setLocale(languageTag: String) {
        setLocale(Locale.forLanguageTag(languageTag))
    }

    /**
     * Translate a key. Returns the key itself if no translation is found.
     */
    fun t(key: String): String {
        return try {
            bundle.getString(key)
        } catch (e: MissingResourceException) {
            logger.debug("Missing translation key: {}", key)
            key
        }
    }

    /**
     * Translate a key with format arguments.
     */
    fun t(key: String, vararg args: Any): String {
        val template = t(key)
        return try {
            String.format(currentLocale, template, *args)
        } catch (e: IllegalFormatException) {
            logger.warn("Invalid format for key '{}': {}", key, e.message)
            template
        }
    }
}
