package id.a3.pos.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class A3DeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "A3 Kiosk: Device Admin Diaktifkan", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "A3 Kiosk: Device Admin Dinonaktifkan", Toast.LENGTH_SHORT).show()
    }
}
