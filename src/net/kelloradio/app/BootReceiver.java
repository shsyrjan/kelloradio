package net.kelloradio.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class BootReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            MyAlarmManager alarm = new MyAlarmManager();
            alarm.load(settings);
            alarm.updateAlarm(context);
        }
    }
}
