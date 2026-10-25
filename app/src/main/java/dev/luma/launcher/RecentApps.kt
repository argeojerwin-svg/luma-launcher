package dev.luma.launcher

/** Launcher launches only. Android does not provide viewing history to this app. */
object RecentApps {
    private const val KEY = "recentApps"
    private const val LIMIT = 8

    fun enabled(prefs: Preferences) = prefs.number("recentEnabled", 1) == 1

    fun record(prefs: Preferences, id: String) {
        if (enabled(prefs)) prefs.putList(KEY, (listOf(id) + prefs.list(KEY).filterNot { it == id }).take(LIMIT))
    }

    fun installed(prefs: Preferences, apps: List<AppEntry>): List<AppEntry> {
        val byId = apps.associateBy { it.id }
        return prefs.list(KEY).mapNotNull { byId[it] }.filter { AppVisibility.visible(it, prefs) }
    }

    fun reconcile(prefs: Preferences, apps: List<AppEntry>) {
        val installed = apps.map { it.id }.toSet()
        val current = prefs.list(KEY)
        val retained = current.filter { it in installed }.distinct().take(LIMIT)
        if (retained != current) prefs.putList(KEY, retained)
    }

    fun clear(prefs: Preferences) = prefs.putList(KEY, emptyList())
}
