package app.georgia.offline.domain

/**
 * Mapsforge MapDataStore accepts one preferred label language per map reader. If legacy
 * settings provide more than one enabled language, choose a stable fallback order.
 */
object MapLanguageResolver {
    private val priority = listOf("en", "ru", "ka")

    fun preferredLanguage(enabledLanguages: Set<String>): String? =
        priority.firstOrNull(enabledLanguages::contains)
}
