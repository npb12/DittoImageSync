package ditto.live.pics

import android.bluetooth.BluetoothAdapter
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import java.util.*


class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)
        val usernameET = findViewById<EditText>(R.id.username)

        if (BluetoothAdapter.getDefaultAdapter() != null) {
            usernameET.setText(BluetoothAdapter.getDefaultAdapter().getName())
        } else {
            usernameET.setText(UUID.randomUUID().toString())
        }

        findViewById<Button>(R.id.login).setOnClickListener {
            val username = usernameET.text.toString()
            prefs.userName = username
            finish()
        }
    }
}