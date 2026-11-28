package app.georgia.offline.data

import android.content.Context

data class SafetyChecklistItem(
    val id: String,
    val title: String,
    val detail: String
)

data class SafetyGuidanceItem(
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
        SafetyChecklistItem("phone_emergency", "Телефон и связь", "Заряд, пауэрбанк и сохранённый экстренный номер"),
        SafetyChecklistItem("emergency_contact", "Экстренный контакт", "Сохрани номер близкого человека в панели экстренной помощи"),
        SafetyChecklistItem("shared_route", "План поездки", "Сообщи близким направление и ожидаемое время возвращения"),
        SafetyChecklistItem("road_conditions", "Дорожные условия", "Проверь погоду, ограничения и сезонную доступность дорог"),
        SafetyChecklistItem("repair_kit", "Ремкомплект", "Ремкомплект для шин, насос или компрессор, инструмент и светоотражающий жилет")
    )

    val incidentSteps = listOf(
        SafetyGuidanceItem("scene", "Сначала безопасность", "Остановись вне потока, обозначь место только если это безопасно. Не приближайся к огню, дыму или утечке топлива."),
        SafetyGuidanceItem("call", "Вызови 112", "Сообщи точные координаты или ориентир, направление движения, число пострадавших, их состояние и опасности на месте. Отвечай на вопросы оператора."),
        SafetyGuidanceItem("movement", "Не перемещай пострадавшего", "При подозрении на травму головы, шеи или позвоночника не двигай человека, если нет непосредственной угрозы от огня, транспорта или другой опасности."),
        SafetyGuidanceItem("helmet", "Не снимай мотошлем без необходимости", "Снятие может усугубить травму шеи. Делай это только если без этого невозможно обеспечить дыхательные пути или провести жизненно важную помощь; по возможности дождись обученных спасателей."),
        SafetyGuidanceItem("bleeding", "Останови сильное наружное кровотечение", "Прижимай рану чистой тканью или перевязочным материалом. Не откладывай вызов 112 ради самостоятельной обработки."),
        SafetyGuidanceItem("wait", "Оставайся рядом до помощи", "Следи за сознанием и дыханием, защищай пострадавшего от холода и дождя. Не давай еду или напитки и выполняй указания оператора 112.")
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
