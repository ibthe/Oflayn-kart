package app.georgia.offline.domain

/**
 * Mapsforge MapDataStore currently accepts one preferred label language per map reader.
 * The map file itself can contain several language variants; this helper picks a stable
 * preference when the user has enabled more than one. A future multi-label overlay will
 * render all enabled variants simultaneously.
 */
object MapLanguageResolver {
    private val priority = listOf("en", "ru", "ka")

    fun preferredLanguage(enabledLanguages: Set<String>): String? =
        priority.firstOrNull(enabledLanguages::contains)
}
