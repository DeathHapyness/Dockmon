package dev.rique.guardiao.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import dev.rique.guardiao.MainActivity

object Kiosk {
    private fun dpm(ctx: Context) = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun admin(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)

    fun isOwner(ctx: Context) = dpm(ctx).isDeviceOwnerApp(ctx.packageName)

    fun apply(activity: Activity, brightness: Float) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = brightness }
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        if (!isOwner(activity)) return
        val dpm = dpm(activity)
        val admin = admin(activity)
        dpm.setLockTaskPackages(admin, arrayOf(activity.packageName))
        dpm.setStatusBarDisabled(admin, true)
        dpm.setKeyguardDisabled(admin, true)
        dpm.setGlobalSetting(
            admin, Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
            (BatteryManager.BATTERY_PLUGGED_AC or BatteryManager.BATTERY_PLUGGED_USB or BatteryManager.BATTERY_PLUGGED_WIRELESS).toString(),
        )
        val home = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(admin, home, ComponentName(activity, MainActivity::class.java))
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) activity.startLockTask()
    }

    fun exit(activity: Activity) {
        if (isOwner(activity)) {
            val dpm = dpm(activity)
            val admin = admin(activity)
            dpm.setStatusBarDisabled(admin, false)
            dpm.setKeyguardDisabled(admin, false)
            dpm.clearPackagePersistentPreferredActivities(admin, activity.packageName)
        }
        runCatching { activity.stopLockTask() }
        activity.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
