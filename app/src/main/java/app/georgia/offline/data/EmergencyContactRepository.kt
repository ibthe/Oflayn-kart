package app.georgia.offline.data

import android.content.Context

data class EmergencyContact(val name: String, val phone: String)

/** One optional emergency contact, stored only in the app's private preferences. */
object EmergencyContactRepository {
    private const val PREFS = "oflayn_emergency_contact"
    private const val KEY_NAME = "name"
    private const val KEY_PHONE = "phone"

    fun load(context: Context): EmergencyContact? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_NAME, null)?.trim().orEmpty()
        val phone = prefs.getString(KEY_PHONE, null)?.trim().orEmpty()
        return EmergencyContact(name, phone).takeIf { isValid(it.name, it.phone) }
    }

    fun save(context: Context, name: String, phone: String): Boolean {
        val contact = EmergencyContact(name.trim().take(60), phone.trim().take(32))
        if (!isValid(contact.name, contact.phone)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAME, contact.name)
            .putString(KEY_PHONE, contact.phone)
            .apply()
        return true
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun isValid(name: String, phone: String): Boolean {
        if (name.isBlank()) return false
        val compact = phone.filter { it.isDigit() || it == '+' }
        val digits = compact.count(Char::isDigit)
        return digits in 6..15 && compact.count { it == '+' } <= 1 &&
            (compact.indexOf('+') <= 0)
    }
}
