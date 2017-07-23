package net.kelloradio.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.TimePicker;
import android.widget.Toast;
import android.view.View;
import android.view.ViewGroup;
import java.util.Calendar;
import java.util.Stack;
import android.provider.Settings;

public class MainActivity extends Activity
    implements
        View.OnClickListener,
        Player.IView,
        Handler.Callback
{
    static final String SETTINGS = "MainActivity";

    Player player = new Player(this);
    ChannelStore channels = new ChannelStore();
    ChannelStore.Channel channelEditor = null;
    MyAlarmManager alarm = new MyAlarmManager();

    static final int MSG_UPDATE_VIEW = 1;
    Handler handler = new Handler(this);

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initView();
        if (savedInstanceState != null) {
            player.restoreInstanceState(savedInstanceState);
        }
        checkFirstRun();
        load();
        alarm.updateAlarm(this);
        setVolumeControlStream(AudioManager.STREAM_ALARM);
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
        requestUpdate();
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

            channels.stockReset(this, settings, editor);

            editor.putBoolean("first_run", false);
            editor.commit();
        }
    }

    public void load() {
        SharedPreferences settings = getSharedPreferences(SETTINGS, Context.MODE_PRIVATE);
        player.name = settings.getString("name", "");
        player.url = settings.getString("url", "");
        channels.load(settings);
        alarm.load(settings);
    }

    public void save() {
        SharedPreferences settings = getSharedPreferences(SETTINGS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("name", player.name);
        editor.putString("url", player.url);
        channels.save(settings, editor);
        alarm.save(settings, editor);

        if (Build.VERSION.SDK_INT >= 9) {
            editor.apply();
        } else {
            editor.commit();
        }
    }

    public void initView() {
        setContentView(R.layout.main);
        findViewById(R.id.play).setOnClickListener(this);
        findViewById(R.id.channel_edit_ok_button).setOnClickListener(this);
        findViewById(R.id.channel_edit_cancel_button).setOnClickListener(this);
        findViewById(R.id.channel_edit_try_play_button).setOnClickListener(this);
        findViewById(R.id.channel_edit_remove_button).setOnClickListener(this);
    }

    public void update() {
        requestUpdate();
    }

    public void requestUpdate() {
        if (!handler.hasMessages(MSG_UPDATE_VIEW)) {
            handler.sendEmptyMessage(MSG_UPDATE_VIEW);
        }
    }

    public boolean handleMessage(Message msg) {
        if (msg.what == MSG_UPDATE_VIEW) {
            updateView();
            return true;
        }
        return false;
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
            requestUpdate();
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
            }

            b.append("]");

            status.setText(b.toString());
        } else {
            play.setText(getString(R.string.play_text));
            stream.setText(player.name);
            StringBuilder b = new StringBuilder();
            b.append("[");
            if (player.error()) {
                b.append(getString(R.string.error));
                b.append(": ");
                b.append(player.getMessage());
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
            ChannelStore.Channel channel = channels.get(i);
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
            ChannelStore.Channel channel = channels.get(i);
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
                save();
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
        removeButton.setVisibility(channels.adding() ? View.GONE : View.VISIBLE);
        String name = nameInput.getText().toString();
        String url = urlInput.getText().toString().trim();

        if (clicked(ok)) {
            channelEditor.name = name;
            channelEditor.url = url;
            channels.commit();
            save();
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
            channels.removeEditorTarget();
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
        if (alarm.isSet()) {
            if (isTime24()) {
                alarmTime.setText(time24(alarm.getHour(), alarm.getMinute()));
            } else {
                alarmTime.setText(time12(alarm.getHour(), alarm.getMinute()));
            }
        } else {
            alarmTime.setText("--:--");
        }
    }

    public void updateChannelEditControls() {
        EditText nameInput = (EditText)findViewById(R.id.channel_edit_name_input);
        nameInput.setText(channelEditor.name);
        EditText urlInput = (EditText)findViewById(R.id.channel_edit_url_input);
        urlInput.setText(channelEditor.url);
        setState(State.CHANNEL_EDIT);
    }

    public void editChannel(ChannelStore.Channel channel) {
        channelEditor = channels.edit(channel);
        updateChannelEditControls();
    }

    public void onAddChannel(View view) {
        channelEditor = channels.editNew();
        updateChannelEditControls();
        updateView();
    }

    public void alarmToast() {
        Calendar alarmTime = alarm.getNextTime();
        Calendar now = Calendar.getInstance();
        long diff = alarmTime.getTimeInMillis() - now.getTimeInMillis();
        long hours = diff/MyAlarmManager.ONE_HOUR;
        long minutes = (diff/MyAlarmManager.ONE_MINUTE) % 60;
        long seconds = (diff/MyAlarmManager.ONE_SECOND) % 60;

        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.time_until_next_alarm));
        sb.append("\n");

        if (diff >= MyAlarmManager.ONE_HOUR) {
            sb.append(hours);
            sb.append(" ");
            if (hours == 1) {
                sb.append(getString(R.string.hour));
            } else {
                sb.append(getString(R.string.hours));
            }
            sb.append(" ");
        }

        if (diff >= MyAlarmManager.ONE_MINUTE) {
            sb.append(minutes);
            sb.append(" ");
            if (minutes == 1) {
                sb.append(getString(R.string.minute));
            } else {
                sb.append(getString(R.string.minutes));
            }
        }

        if (diff < MyAlarmManager.ONE_MINUTE) {
            sb.append(seconds);
            sb.append(" ");
            if (seconds == 1) {
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
        tp.setHour(alarm.getHour());
        tp.setMinute(alarm.getMinute());
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
        alarm.set(timePicker.getHour(), timePicker.getMinute());;
        save();
        alarm.updateAlarm(this);
        back();
        alarmToast();
        updateView();
    }

    public void onRemoveAlarm(View view) {
        alarm.remove();
        save();
        alarm.updateAlarm(this);
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
}
