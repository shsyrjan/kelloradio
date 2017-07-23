package net.kelloradio.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import java.util.Calendar;

class MyAlarmManager
{
    static final long ONE_SECOND = 1000;
    static final long ONE_MINUTE = 1000*60;
    static final long ONE_HOUR   = 1000*60*60;
    static final long ONE_DAY    = 1000*60*60*24;

    boolean alarmSet = false;
    int hour = 6;
    int minute = 0;

    public void load(SharedPreferences settings) {
        hour = settings.getInt("hour", 6);
        minute = settings.getInt("minute", 0);
        alarmSet = settings.getBoolean("alarm_set", false);
    }

    public void save(SharedPreferences settings, SharedPreferences.Editor editor) {
        editor.putInt("hour", hour);
        editor.putInt("minute", minute);
        editor.putBoolean("alarm_set", alarmSet);
    }

    public int getHour() {
        return hour;
    }

    public int getMinute() {
        return minute;
    }

    public boolean isSet() {
        return alarmSet;
    }

    public void set(int hour, int minute) {
        this.hour = hour;
        this.minute = minute;
        this.alarmSet = true;
    }

    public void remove() {
        this.alarmSet = false;
    }

    public Calendar getNextTime() {
        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        if (next.before(now) || next.equals(now)) {
            // the next time is tomorrow
            next.roll(Calendar.DATE, true);
        }
        return next;
    }

    public static PendingIntent getAlarmIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT);
    }

    public static void setAlarm(Context context, Calendar alarmTime) {
        AlarmManager alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.setRepeating(
            AlarmManager.RTC_WAKEUP,
            alarmTime.getTimeInMillis(),
            ONE_DAY,
            getAlarmIntent(context));

        setBootReceiverEnabled(context, true);
    }

    public static void cancelAlarm(Context context) {
        AlarmManager alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.cancel(getAlarmIntent(context));
        setBootReceiverEnabled(context, false);
    }

    public static void setBootReceiverEnabled(Context context, boolean enabled) {
        ComponentName receiver = new ComponentName(context, BootReceiver.class);
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(
            receiver,
            enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP);
    }

    public void updateAlarm(Context context) {
        if (alarmSet) {
            setAlarm(context, getNextTime());
        } else {
            cancelAlarm(context);
        }
    }
}
