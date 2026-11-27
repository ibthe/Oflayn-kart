package app.georgia.offline.data

import android.content.Context

data class SafetyChecklistItem(
    val id: String,
    val title: String,
    val detail: String
)

/** A small pre-ride checklist stored locally; it never sends or shares user data. */
object SafetyChecklistRepository {
    val items = listOf(
        SafetyChecklistItem("helmet", "Шлем и экипировка", "Шлем, перчатки, защита и подходящая одежда"),
        SafetyChecklistItem("tires", "Шины и колёса", "Давление, протектор, крепления и видимые повреждения"),
        SafetyChecklistItem("brakes_lights", "Тормоза и свет", "Тормозные рычаги, стоп-сигнал, фары и поворотники"),
        SafetyChecklistItem("fuel", "Топливо и запас хода", "Топливо, масло и ожидаемый запас хода"),
        SafetyChecklistItem("documents", "Документы", "Права, регистрация, страховка и документы поездки"),
        SafetyChecklistItem("water_first_aid", "Вода и аптечка", "Вода, базовая аптечка и необходимый инструмент"),
        SafetyChecklistItem("offline_map", "Офлайн-карта", "Нужный регион загружен и маршрут проверен"),
        SafetyChecklistItem("phone_emergency", "Телефон и связь", "Заряд, пауэрбанк и сохранённый экстренный номер")
    )

    private const val PREFS = "oflayn_safety_checklist"
    private const val KEY_CHECKED = "checked_ids"

    fun load(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_CHECKED, emptySet())
            ?.toSet()
            .orEmpty()
            .intersect(items.map { it.id }.toSet())

    fun save(context: Context, checkedIds: Set<String>) {
        val validIds = checkedIds.intersect(items.map { it.id }.toSet())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_CHECKED, validIds)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CHECKED)
            .apply()
    }
}
