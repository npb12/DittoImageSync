package ditto.live.pics

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class Prefs (context: Context) {

    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    enum class AppPrefKeys {
        username,
    }

    var userName : String?
        get() = prefs.getString(AppPrefKeys.username.name, null)
        set(value) = prefs.edit().putString(AppPrefKeys.username.name, value).apply()
}