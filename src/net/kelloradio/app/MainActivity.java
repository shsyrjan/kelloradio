package net.kelloradio.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.TimePicker;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
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
        if (!stopped)
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
        ((TextView)findViewById(R.id.debug)).setText(m);
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
        SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
        boolean first_run = settings.getBoolean("first_run", true);
        if (first_run) {
            SharedPreferences.Editor editor = settings.edit();
            editor.putString("name", getString(R.string.ch_0_name));
            editor.putString("url", getString(R.string.ch_0_url));

            editor.putInt("ch_num", 2);
            editor.putString("ch_0_name", getString(R.string.ch_0_name));
            editor.putString("ch_0_url", getString(R.string.ch_0_url));
            editor.putBoolean("ch_0_starred", true);

            editor.putString("ch_1_name", getString(R.string.ch_1_name));
            editor.putString("ch_1_url", getString(R.string.ch_1_url));
            editor.putBoolean("ch_1_starred", true);

            editor.putBoolean("first_run", false);
            editor.commit();
        }
    }

    public void load() {
        SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
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
    }

    public void save() {
        SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
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
        TextView star = new TextView(this);
        star.setOnClickListener(this);
        item.addView(star);
        Button text = new Button(this);
        text.setOnClickListener(this);
        item.addView(text);
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
            if (i >= channelsView.getChildCount()) {
                channelsView.addView(newStarredChannelItem());
            }
            ViewGroup starredItem = (ViewGroup)channelsView.getChildAt(j);
            TextView starText = (TextView)starredItem.getChildAt(0);
            starText.setText(R.string.starred);
            Button channelText = (Button)starredItem.getChildAt(1);
            if (clicked(channelText)) {
                player.set(channel.name, channel.url);
                player.play();
            }
            channelText.setText(channel.name);
            ++j;
        }
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

    public void onMoreChannels(View view) {
        setState(State.CHANNEL_LIST);
        updateView();
    }

    public void onAddChannel(View view) {
        newChannel.name = "";
        newChannel.url = "";
        newChannel.starred = false;
        editChannel(newChannel);
        updateView();
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
        back();
        updateView();
    }

    public void onRemoveAlarm(View view) {
        alarmSet = false;
        back();
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
