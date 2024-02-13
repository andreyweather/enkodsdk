package com.enkod.enkodpushlibrary

import android.app.NotificationChannel
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.enkod.enkodpushlibrary.EnkodPushLibrary.defaultIconId
import java.util.*

val variables = Variables
internal fun NotificationCompat.Builder.setIcon(context: Context, data: String?): NotificationCompat.Builder {
    fun defaultResID() = context.getResourceFromMeta("com.google.firebase.messaging.default_notification_icon", defaultIconId)

    if (data != null){
        EnkodPushLibrary.logInfo("icon is loaded from push($data)")
        val resID = EnkodPushLibrary.getResourceId(context, data, "drawable", context.packageName)
        if(resID > 0){
            setSmallIcon(resID)
        }else {
            setSmallIcon(defaultResID())
        }

    }else{
        EnkodPushLibrary.logInfo("loaded default icon")
        setSmallIcon(defaultResID())
    }
    return this
}
/*

internal fun NotificationCompat.Builder.setColor(context: Context, data: String?): NotificationCompat.Builder {
    fun defaultResID() = context.getResourceFromMeta("com.google.firebase.messaging.default_notification_color", R.color.white)
    if (data != null){
        EnkodPushLibrary.logInfo("color is loaded from push($data)")
        color = try {
            Color.parseColor(data)
        }catch (e: Exception){
            e.printStackTrace()
            defaultResID()
        }
    }else{
        color = defaultResID()
        EnkodPushLibrary.logInfo("can't load color $data")
    }
    return this
}

 */

private fun Context.getResourceFromMeta(path: String, default: Int): Int {
     return packageManager
        .getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        .run {
            metaData.getInt(path, default)
        }
}




internal fun NotificationCompat.Builder.setVibrate(boolean: Boolean): NotificationCompat.Builder {
    if(boolean){
        EnkodPushLibrary.logInfo("Vibration is on")
        setVibrate(EnkodPushLibrary.vibrationPattern)
    }else {
        EnkodPushLibrary.logInfo("Vibration is off")
        setVibrate(longArrayOf())
    }
    return this
}

internal fun NotificationCompat.Builder.setLights(
    ledColor: String?,
    ledOnMs: String?,
    ledOffMs: String?
): NotificationCompat.Builder {
    if(ledColor != null){
        EnkodPushLibrary.logInfo("Light is on with params: color is ${ledColor}, on MS are $ledOnMs, off MS are $ledOffMs")
        setLights(
            Color.parseColor(ledColor),
            ledOnMs?.toIntOrNull() ?: 100,
            ledOffMs?.toIntOrNull() ?: 100
        )
    }else{
        EnkodPushLibrary.logInfo("Light colors are off")
    }
    return this
}

internal fun NotificationCompat.Builder.setSound(defaultSound: Boolean): NotificationCompat.Builder {
    if(defaultSound){
        EnkodPushLibrary.logInfo("Sound is on")
        setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
    }else {
        EnkodPushLibrary.logInfo("Sound is off")
    }
    return this
}

@RequiresApi(Build.VERSION_CODES.O)
internal fun NotificationChannel.enableLights(s: String?) {
    if (s.isNullOrEmpty()) { return }
    try {
        lightColor = Color.parseColor(s)
        enableLights(true)
    }catch (e: Exception){
        e.printStackTrace()
    }
}

@RequiresApi(Build.VERSION_CODES.O)
internal fun NotificationChannel.setSound(hasSound: Boolean) {
    if(hasSound) {
        setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
        )
    }else{
        setSound(null, null)
    }
}

internal fun NotificationCompat.Builder.addActions(context: Context, map: Map<String, String>): NotificationCompat.Builder {
    Log.d("addActions", "add")
    for (i in 1 .. 3){
        if(map.containsKey("${variables.actionButtonText}$i")) {
            val intent = EnkodPushLibrary.getIntent(
                context = context,
                data = map,
                field = map["${variables.actionButtonIntent}$i"] ?: "",
                url = map["${variables.actionButtonsUrl}$i"] ?: ""
            )

            val text = "${variables.actionButtonText}$i"
            addAction(0, map[text], intent)
        }
    }
    return this
}