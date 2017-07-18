package net.kelloradio.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.widget.TextView;
import android.widget.Button;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;

public class MainActivity extends Activity
    implements
        View.OnClickListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnInfoListener,
        MediaPlayer.OnBufferingUpdateListener
{
    class Channel
    {
        String name = "";
        String url = "";
        boolean starred = false;
    }

    ArrayList<Channel> channels = new ArrayList<Channel>();

    String name = "";
    String url = "";

    boolean stopped = false; // user has stopped playback
    MediaPlayer mediaPlayer = null;

    boolean buffering = false;
    int info = 0;
    int error = 0;
    int bufferingPercent = 0;

    View clickedView = null;
    boolean dirty = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initView();
        if (savedInstanceState != null) {
            stopped = savedInstanceState.getBoolean("stopped", false);
        }
        checkFirstRun();
        load();
        updateView();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!stopped)
            play();
        updateView();
    }

    @Override
    public void onStop() {
        super.onStop();
        stop();
        updateView();
    }

    @Override
    public void onPause() {
        super.onPause();
        save();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("stopped", mediaPlayer == null);
        super.onSaveInstanceState(outState);
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
        name = settings.getString("name", "");
        url = settings.getString("url", "");
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
        editor.putString("name", name);
        editor.putString("url", url);
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
        TextView play = (TextView)findViewById(R.id.play);
        play.setOnClickListener(this);
    }

    public void updateView() {
        updatePlayView();
        updateChannelsView();

        if (dirty) {
            dirty = false;
            updateView();
        }
    }

    public void updatePlayView() {
        TextView play = (TextView)findViewById(R.id.play);
        TextView stream = (TextView)findViewById(R.id.stream);
        TextView status = (TextView)findViewById(R.id.status);
        if (mediaPlayer != null) {
            play.setText(getString(R.string.stop_text));
            stream.setText(name);
            StringBuilder b = new StringBuilder();
            b.append("[");

            if (buffering) {
                b.append(getString(R.string.buffering));
                if (bufferingPercent > 0) {
                    b.append(" ");
                    b.append(bufferingPercent);
                    b.append("%");
                }
            } else {
                if (mediaPlayer.isPlaying()) {
                    b.append(getString(R.string.playing));
                } else {
                    b.append(getString(R.string.connecting));
                }
            }

            if (info == 0) {
            } else if (info == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            } else if (info == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            } else {
                b.append(" ");
                b.append(info);
            }

            b.append("]");

            status.setText(b.toString());
        } else {
            play.setText(getString(R.string.play_text));
            stream.setText(name);
            StringBuilder b = new StringBuilder();
            b.append("[");
            if (error != 0) {
                b.append(getString(R.string.error));
                b.append(error);
            } else {
                b.append(getString(R.string.stopped));
            }
            b.append("]");
            status.setText(b.toString());
        }
    }

    public void updateChannelsView() {
        ViewGroup channelsView = (ViewGroup)findViewById(R.id.channels);
        int i = 0;
        for (; i < channels.size(); ++i) {
            Channel channel = channels.get(i);
            if (i >= channelsView.getChildCount()) {
                Button newButton = new Button(this);
                newButton.setOnClickListener(this);
                channelsView.addView(newButton);
            }
            Button chButton = (Button)channelsView.getChildAt(i);
            if (chButton == clickedView) {
                clickedView = null;
                name = channel.name;
                url = channel.url;
                play();
                dirty = true;
            }
            chButton.setText(channel.name);
        }
        channelsView.removeViews(i, channelsView.getChildCount() - i);
    }

    public void addTestChannel(View view) {
        Channel channel = new Channel();
        channel.name = getString(R.string.ch_0_name);
        channel.url = getString(R.string.ch_0_url);
        channels.add(channel);
        updateView();
    }

    public void removeLastChannel(View view) {
        if (channels.size() > 0) {
            channels.remove(channels.size() - 1);
        }
        updateView();
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

    public void onClick(View view) {
        View play = findViewById(R.id.play);
        if (view == play) {
            togglePlay();
        } else {
            clickedView = view;
        }
        updateView();
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
            stop();
        }
    }

    public boolean onError(MediaPlayer mp, int what, int extra) {
        stop();
        error = what;
        updateView();
        return false;
    }

    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        info = what;
        if (info == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            buffering = true;
        } else if (info == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            buffering = false;
        }
        updateView();
        return false;
    }

    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        bufferingPercent = percent;
        updateView();
    }

    public void onPrepared(MediaPlayer mp) {
        mp.start();
        updateView();
    }
}
