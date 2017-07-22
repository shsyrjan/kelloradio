package net.kelloradio.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.Bundle;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.TimePicker;
import android.widget.Toast;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Stack;
import android.provider.Settings;

class Player extends Object
    implements
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnInfoListener,
        MediaPlayer.OnBufferingUpdateListener
{
    interface IView
    {
        public void update();
    }

    IView view = null;

    String name = "";
    String url = "";

    public boolean stopped = false; // user has stopped playback
    public MediaPlayer mediaPlayer = null;

    public boolean buffering = false;
    public int info = 0;
    public int error = 0;
    public int bufferingPercent = 0;

    Player(IView view) {
        this.view = view;
    }

    public void set(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public void togglePlay() {
        if (mediaPlayer == null) {
            stopped = false;
            play();
        } else {
            stopped = true;
            stop();
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    public void play() {
        stop();
        error = 0;
        info = 0;
        buffering = false;
        bufferingPercent = 0;
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnInfoListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        try {
            mediaPlayer.setDataSource(this.url);
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            error = 1;
            stop();
        }
    }

    public void resume() {
        if (!stopped && !playing())
            play();
    }

    public boolean started() {
        return mediaPlayer != null;
    }

    public boolean playing() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public boolean onError(MediaPlayer mp, int what, int extra) {
        stop();
        error = what;
        view.update();
        return false;
    }

    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        info = what;
        if (info == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            buffering = true;
        } else if (info == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            buffering = false;
        }
        view.update();
        return false;
    }

    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        bufferingPercent = percent;
        view.update();
    }

    public void onPrepared(MediaPlayer mp) {
        mp.start();
        view.update();
    }

    void saveInstanceState(Bundle outState) {
        outState.putBoolean("stopped", mediaPlayer == null);
    }

    void restoreInstanceState(Bundle savedInstanceState) {
        stopped = savedInstanceState.getBoolean("stopped", false);
    }
}

public class MainActivity extends Activity
    implements
        View.OnClickListener,
        Player.IView
{
    static final long ONE_SECOND = 1000;
    static final long ONE_MINUTE = 1000*60;
    static final long ONE_HOUR   = 1000*60*60;
    static final long ONE_DAY    = 1000*60*60*24;

    static final String SETTINGS = "MainActivity";

    Player player = new Player(this);

    class Channel
    {
        String name = "";
        String url = "";
        boolean starred = false;

        Channel() {
        }

        Channel(Channel other) {
            assign(other);
        }

        void assign(Channel other) {
            this.name = other.name;
            this.url = other.url;
            this.starred = other.starred;
        }
    }

    ArrayList<Channel> channels = new ArrayList<Channel>();
    Channel targetChannel = null;
    Channel editedChannel = new Channel();
    Channel newChannel = new Channel();

    public enum State {
        MAIN,
        ALARM_EDIT,
        CHANNEL_LIST,
        CHANNEL_EDIT
    }

    public Stack<State> stateStack = new Stack<State>();

    View clickedView = null;
    boolean dirty = false;

    TimePicker timePicker = null;
    boolean alarmSet = false;
    int hour = 6;
    int minute = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initView();
        if (savedInstanceState != null) {
            player.restoreInstanceState(savedInstanceState);
        }
        checkFirstRun();
        load();
        updateAlarm();
        updateView();
    }

    @Override
    public void onStart() {
        super.onStart();
        player.resume();
        updateView();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
        save();
        if (player.playing()) {
            if (!requestVisibleBehind(true)) {
                player.stop();
            }
        } else {
            requestVisibleBehind(false);
        }
    }

    @Override
    public void onVisibleBehindCanceled() {
        player.stop();
        super.onVisibleBehindCanceled();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        player.saveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (getState() == State.MAIN) {
            super.onBackPressed();
        } else {
            back();
        }
        updateView();
    }

    public void debug(String m) {
        TextView tv = ((TextView)findViewById(R.id.debug));
        tv.setText(m);
        tv.setVisibility(View.VISIBLE);
        updateView();
    }

    public void update() {
        updateView();
    }

    public void back() {
        if (!stateStack.empty())
            stateStack.pop();
    }

    public void setState(State newState) {
        stateStack.push(newState);
    }

    public State getState() {
        if (stateStack.empty())
            return State.MAIN;
        return stateStack.peek();
    }

    public void checkFirstRun() {
        SharedPreferences settings = getSharedPreferences(SETTINGS, Context.MODE_PRIVATE);
        boolean first_run = settings.getBoolean("first_run", true);
        if (first_run) {
            SharedPreferences.Editor editor = settings.edit();
            editor.putString("name", getString(R.string.stock_ch_0_name));
            editor.putString("url", getString(R.string.stock_ch_0_url));

            editor.putInt("ch_num", 2);
            editor.putString("ch_0_name", getString(R.string.stock_ch_0_name));
            editor.putString("ch_0_url", getString(R.string.stock_ch_0_url));
            editor.putBoolean("ch_0_starred", true);

            editor.putString("ch_1_name", getString(R.string.stock_ch_1_name));
            editor.putString("ch_1_url", getString(R.string.stock_ch_1_url));
            editor.putBoolean("ch_1_starred", true);

            editor.putBoolean("first_run", false);
            editor.commit();
        }
    }

    public void load() {
        SharedPreferences settings = getSharedPreferences(SETTINGS, Context.MODE_PRIVATE);
        player.name = settings.getString("name", "");
        player.url = settings.getString("url", "");
        int ch_num = settings.getInt("ch_num", 0);
        channels.clear();
        for (int i = 0; i < ch_num; ++i) {
            Channel channel = new Channel();
            String ch_i = "ch_" + Integer.toString(i);
            channel.name = settings.getString(ch_i + "_name", "");
            channel.url = settings.getString(ch_i + "_url", "");
            channel.starred = settings.getBoolean(ch_i + "_starred", false);
            channels.add(channel);
        }

        // alarm
        hour = settings.getInt("hour", 6);
        minute = settings.getInt("minute", 0);
        alarmSet = settings.getBoolean("alarm_set", false);
    }

    public void save() {
        SharedPreferences settings = getSharedPreferences(SETTINGS, Context.MODE_PRIVATE);
        int old_ch_num = settings.getInt("ch_num", 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("name", player.name);
        editor.putString("url", player.url);
        editor.putInt("ch_num", channels.size());
        int i = 0;
        for (i = 0; i < channels.size(); ++i) {
            Channel channel = channels.get(i);
            String ch_i = "ch_" + Integer.toString(i);
            editor.putString(ch_i + "_name", channel.name);
            editor.putString(ch_i + "_url", channel.url);
            editor.putBoolean(ch_i + "_starred", channel.starred);
        }
        for (; i < old_ch_num; ++i) {
            String ch_i = "ch_" + Integer.toString(i);
            if (settings.contains(ch_i + "_name"))
                editor.remove(ch_i + "_name");
            if (settings.contains(ch_i + "_url"))
                editor.remove(ch_i + "_url");
            if (settings.contains(ch_i + "_starred"))
                editor.remove(ch_i + "_starred");
        }

        // alarm
        editor.putInt("hour", hour);
        editor.putInt("minute", minute);
        editor.putBoolean("alarm_set", alarmSet);

        editor.commit();
    }

    public void initView() {
        setContentView(R.layout.main);
        findViewById(R.id.play).setOnClickListener(this);
        findViewById(R.id.channel_edit_ok_button).setOnClickListener(this);
        findViewById(R.id.channel_edit_cancel_button).setOnClickListener(this);
        findViewById(R.id.channel_edit_try_play_button).setOnClickListener(this);
        findViewById(R.id.channel_edit_remove_button).setOnClickListener(this);
    }

    public boolean clicked(View view) {
        if (view == clickedView) {
            clickedView = null;
            dirty = true;
            return true;
        }
        return false;
    }

    public void updateView() {

        findViewById(R.id.top_view).setVisibility((getState() != State.ALARM_EDIT) ? View.VISIBLE : View.GONE);
        findViewById(R.id.set_alarm_view).setVisibility((getState() == State.ALARM_EDIT) ? View.VISIBLE : View.GONE);
        findViewById(R.id.main_view).setVisibility((getState() == State.MAIN) ? View.VISIBLE : View.GONE);
        findViewById(R.id.channel_list_view).setVisibility((getState() == State.CHANNEL_LIST) ? View.VISIBLE : View.GONE);
        findViewById(R.id.channel_edit_view).setVisibility((getState() == State.CHANNEL_EDIT) ? View.VISIBLE : View.GONE);

        updatePlayView();
        updateChannelsView();
        updateChannelListView();
        updateChannelEditView();
        updateAlarmView();

        if (dirty) {
            dirty = false;
            updateView();
        }
    }

    public void updatePlayView() {
        TextView play = (TextView)findViewById(R.id.play);
        if (clicked(play)) {
            player.togglePlay();
        }
        TextView stream = (TextView)findViewById(R.id.stream);
        TextView status = (TextView)findViewById(R.id.status);
        if (player.started()) {
            play.setText(getString(R.string.stop_text));
            stream.setText(player.name);
            StringBuilder b = new StringBuilder();
            b.append("[");

            if (player.buffering) {
                b.append(getString(R.string.buffering));
                if (player.bufferingPercent > 0) {
                    b.append(" ");
                    b.append(player.bufferingPercent);
                    b.append("%");
                }
            } else {
                if (player.playing()) {
                    b.append(getString(R.string.playing));
                } else {
                    b.append(getString(R.string.connecting));
                }
            }

            if (player.info == 0) {
            } else if (player.info == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            } else if (player.info == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            } else {
                b.append(" ");
                b.append(player.info);
            }

            b.append("]");

            status.setText(b.toString());
        } else {
            play.setText(getString(R.string.play_text));
            stream.setText(player.name);
            StringBuilder b = new StringBuilder();
            b.append("[");
            if (player.error != 0) {
                b.append(getString(R.string.error));
                b.append(player.error);
            } else {
                b.append(getString(R.string.stopped));
            }
            b.append("]");
            status.setText(b.toString());
        }
    }

    public ViewGroup newStarredChannelItem() {
        LinearLayout item = new LinearLayout(this);
        Button text = new Button(this);
        text.setOnClickListener(this);
        item.addView(text);
        TextView star = new TextView(this);
        star.setOnClickListener(this);
        item.addView(star);
        return item;
    }

    public void updateChannelsView() {
        ViewGroup channelsView = (ViewGroup)findViewById(R.id.channels);
        int i = 0;
        int j = 0;
        for (; i < channels.size(); ++i) {
            Channel channel = channels.get(i);
            if (!channel.starred)
                continue;
            if (j >= channelsView.getChildCount()) {
                channelsView.addView(newStarredChannelItem());
            }
            ViewGroup starredItem = (ViewGroup)channelsView.getChildAt(j);
            Button channelText = (Button)starredItem.getChildAt(0);
            if (clicked(channelText)) {
                player.set(channel.name, channel.url);
                player.play();
            }
            channelText.setText(channel.name);
            TextView starText = (TextView)starredItem.getChildAt(1);
            starText.setVisibility(View.VISIBLE);
            starText.setText(R.string.starred);
            ++j;
        }

        // the last item navigates to full channel list
        if (j >= channelsView.getChildCount()) {
            channelsView.addView(newStarredChannelItem());
        }
        ViewGroup starredItem = (ViewGroup)channelsView.getChildAt(j);
        Button channelText = (Button)starredItem.getChildAt(0);
        channelText.setText(R.string.more_channels);
        if (clicked(channelText)) {
            setState(State.CHANNEL_LIST);
        }
        TextView starText = (TextView)starredItem.getChildAt(1);
        starText.setVisibility(View.GONE);
        ++j;

        channelsView.removeViews(j, channelsView.getChildCount() - j);
    }

    public ViewGroup newChannelItem() {
        LinearLayout item = new LinearLayout(this);
        Button text = new Button(this);
        text.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        text.setOnClickListener(this);
        item.addView(text);
        Button star = new Button(this);
        star.setOnClickListener(this);
        item.addView(star);
        Button edit = new Button(this);
        edit.setOnClickListener(this);
        item.addView(edit);
        return item;
    }

    public void updateChannelListView() {
        ViewGroup channelList = (ViewGroup)findViewById(R.id.channel_list);
        int i = 0;
        for (; i < channels.size(); ++i) {
            Channel channel = channels.get(i);
            if (i >= channelList.getChildCount()) {
                channelList.addView(newChannelItem());
            }
            ViewGroup channelItem = (ViewGroup)channelList.getChildAt(i);

            Button channelText = (Button)channelItem.getChildAt(0);
            if (clicked(channelText)) {
                player.set(channel.name, channel.url);
                player.play();
            }
            channelText.setText(channel.name);

            Button starButton = (Button)channelItem.getChildAt(1);
            if (clicked(starButton)) {
                channel.starred = channel.starred ? false : true;
            }
            if (channel.starred) {
                starButton.setText(R.string.starred);
            } else {
                starButton.setText(R.string.not_starred);
            }

            Button editButton = (Button)channelItem.getChildAt(2);
            if (clicked(editButton)) {
                editChannel(channel);
            }
            editButton.setText(getString(R.string.edit));
        }
        channelList.removeViews(i, channelList.getChildCount() - i);
    }

    public void updateChannelEditView() {
        Button ok = (Button)findViewById(R.id.channel_edit_ok_button);
        EditText nameInput = (EditText)findViewById(R.id.channel_edit_name_input);
        EditText urlInput = (EditText)findViewById(R.id.channel_edit_url_input);

        Button cancel = (Button)findViewById(R.id.channel_edit_cancel_button);
        Button tryPlayButton = (Button)findViewById(R.id.channel_edit_try_play_button);
        Button removeButton = (Button)findViewById(R.id.channel_edit_remove_button);
        removeButton.setVisibility((targetChannel == newChannel) ? View.GONE : View.VISIBLE);
        String name = nameInput.getText().toString();
        String url = urlInput.getText().toString().trim();

        if (clicked(ok)) {
            editedChannel.name = name;
            editedChannel.url = url;

            targetChannel.assign(editedChannel);
            if (targetChannel == newChannel) {
                channels.add(new Channel(newChannel));
            } else {
            }
            back();
        }
        if (clicked(cancel)) {
            back();
        }
        if (clicked(tryPlayButton)) {
            player.set(name, url);
            player.play();
        }
        if (clicked(removeButton)) {
            if (targetChannel != newChannel) {
                channels.remove(targetChannel);
            }
            back();
        }
    }

    public String time24(int hour, int minute) {
        StringBuffer sb = new StringBuffer();
        if (hour < 10)
            sb.append("0");
        sb.append(Integer.toString(hour));
        sb.append(":");
        if (minute < 10)
            sb.append("0");
        sb.append(minute);
        return sb.toString();
    }

    public String time12(int hour, int minute) {
        StringBuffer sb = new StringBuffer();
        if (hour == 0 || hour == 12) {
            sb.append("12");
        } else {
            if (hour % 12 < 10)
                sb.append("0");
            sb.append(Integer.toString(hour % 12));
        }
        sb.append(":");
        if (minute < 10)
            sb.append("0");
        sb.append(Integer.toString(minute));
        sb.append(" ");
        sb.append(hour < 12 ? "AM" : "PM");
        return sb.toString();
    }

    public void updateAlarmView() {
        TextView alarmTime = (TextView)findViewById(R.id.alarm_time);
        if (alarmSet) {
            if (isTime24()) {
                alarmTime.setText(time24(hour, minute));
            } else {
                alarmTime.setText(time12(hour, minute));
            }
        } else {
            alarmTime.setText("--:--");
        }
    }

    public void editChannel(Channel channel) {
        targetChannel = channel;
        editedChannel.assign(channel);
        EditText nameInput = (EditText)findViewById(R.id.channel_edit_name_input);
        nameInput.setText(editedChannel.name);
        EditText urlInput = (EditText)findViewById(R.id.channel_edit_url_input);
        urlInput.setText(editedChannel.url);
        setState(State.CHANNEL_EDIT);
    }

    public void onAddChannel(View view) {
        newChannel.name = "";
        newChannel.url = "";
        newChannel.starred = false;
        editChannel(newChannel);
        updateView();
    }

    public static Calendar getTimeUntil(int hour, int minutes) {
        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minutes);
        next.set(Calendar.SECOND, 0);
        if (next.before(now) || next.equals(now)) {
            // the next time is tomorrow
            next.roll(Calendar.DATE, true);
        }
        return next;
    }

    public void alarmToast() {
        Calendar alarmTime = getTimeUntil(hour, minute);
        Calendar now = Calendar.getInstance();
        long diff = alarmTime.getTimeInMillis() - now.getTimeInMillis();
        long hours = diff/ONE_HOUR;
        long minutes = (diff/ONE_MINUTE) % 60;
        long seconds = (diff/ONE_SECOND) % 60;

        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.time_until_next_alarm));
        sb.append("\n");

        if (diff >= ONE_HOUR) {
            sb.append(hours);
            sb.append(" ");
            if (hours < 2) {
                sb.append(getString(R.string.hour));
            } else {
                sb.append(getString(R.string.hours));
            }
            sb.append(" ");
        }

        if (diff >= ONE_MINUTE) {
            sb.append(minutes);
            sb.append(" ");
            if (minutes < 2) {
                sb.append(getString(R.string.minute));
            } else {
                sb.append(getString(R.string.minutes));
            }
        }

        if (diff < ONE_MINUTE) {
            sb.append(seconds);
            sb.append(" ");
            if (seconds < 2) {
                sb.append(getString(R.string.second));
            } else {
                sb.append(getString(R.string.seconds));
            }
        }

        Toast.makeText(this, sb.toString(), Toast.LENGTH_LONG).show();
    }

    public boolean isTime24() {
        return Settings.System.getInt(getContentResolver(), Settings.System.TIME_12_24, 24) == 24;
    }

    public TimePicker newTimePicker() {
        TimePicker tp = new TimePicker(this);
        tp.setIs24HourView(isTime24());
        tp.setHour(hour);
        tp.setMinute(minute);
        return tp;
    }

    public void onSetAlarm(View view) {
        timePicker = newTimePicker();
        ViewGroup alarmTimePickerContainer = (ViewGroup)findViewById(R.id.alarm_time_picker_container);
        alarmTimePickerContainer.removeAllViews();
        alarmTimePickerContainer.addView(timePicker);
        setState(State.ALARM_EDIT);
        updateView();
    }

    public void onSetAlarmOk(View view) {
        hour = timePicker.getHour();
        minute = timePicker.getMinute();
        alarmSet = true;
        updateAlarm();
        back();
        alarmToast();
        updateView();
    }

    public void onRemoveAlarm(View view) {
        alarmSet = false;
        updateAlarm();
        back();
        Toast.makeText(this, getString(R.string.alarm_removed), Toast.LENGTH_LONG).show();
        updateView();
    }

    public void onSetAlarmCancel(View view) {
        back();
        updateView();
    }

    public void onBack(View view) {
        back();
        updateView();
    }

    public void onClick(View view) {
        clickedView = view;
        updateView();
    }

    public void updateAlarm() {
        if (alarmSet) {
            setAlarm(this, getTimeUntil(hour, minute));
        } else {
            cancelAlarm(this);
        }
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
}
