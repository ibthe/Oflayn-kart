package app.georgia.offline.domain

/**
 * Stable render order for independent map-label language layers.
 * An empty list means Mapsforge's default/original name preference.
 */
object MapLanguageResolver {
    private val priority = listOf("en", "ru", "ka")

    fun orderedLanguages(enabledLanguages: Set<String>): List<String> =
        priority.filter(enabledLanguages::contains)

    /** Backwards-compatible helper for consumers that still require one preferred language. */
    fun preferredLanguage(enabledLanguages: Set<String>): String? =
        orderedLanguages(enabledLanguages).firstOrNull()
}
