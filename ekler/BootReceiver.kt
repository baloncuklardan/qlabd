package com.abdullahql.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.abdullahql.app.service.LocationForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences(
            LocationForegroundService.PREFS, Context.MODE_PRIVATE
        )
        val wasSharing = prefs.getBoolean(LocationForegroundService.KEY_SHARING_ENABLED, false)

        // Yalnızca kullanıcı daha önce "Paylaşımı Başlat" düğmesine bastıysa
        // servis cihaz yeniden başladığında otomatik olarak devam eder.
        if (wasSharing) {
            LocationForegroundService.start(context)
        }
    }
}
