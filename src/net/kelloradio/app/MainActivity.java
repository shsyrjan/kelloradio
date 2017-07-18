package net.kelloradio.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.widget.TextView;
import android.view.View;

public class MainActivity extends Activity
    implements
        View.OnClickListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnInfoListener,
        MediaPlayer.OnBufferingUpdateListener
{
    String name = "";
    String url = "";

    boolean stopped = false; // user has stopped playback
    MediaPlayer mediaPlayer = null;

    boolean buffering = false;
    int info = 0;
    int error = 0;
    int bufferingPercent = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            stopped = savedInstanceState.getBoolean("stopped", false);
        }
        init();
        load();
        update();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!stopped)
            play();
        update();
    }

    @Override
    public void onStop() {
        super.onStop();
        stop();
        update();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("stopped", mediaPlayer == null);
        super.onSaveInstanceState(outState);
    }

    public void load() {
        SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
        name = settings.getString("name", getString(R.string.ch_0_name));
        url = settings.getString("url", getString(R.string.ch_0_url));
    }

    public void save() {
        SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("name", name);
        editor.putString("url", url);
        editor.commit();
    }

    public void init() {
        setContentView(R.layout.main);
        TextView play = (TextView)findViewById(R.id.play);
        play.setOnClickListener(this);
        TextView ch0 = (TextView)findViewById(R.id.ch0);
        ch0.setOnClickListener(this);
        TextView ch1 = (TextView)findViewById(R.id.ch1);
        ch1.setOnClickListener(this);
    }

    public void update() {
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

    public void ch0() {
        name = getString(R.string.ch_0_name);
        url = getString(R.string.ch_0_url);
        save();
        play();
    }

    public void ch1() {
        name = getString(R.string.ch_1_name);
        url = getString(R.string.ch_1_url);
        save();
        play();
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
        View ch0 = findViewById(R.id.ch0);
        View ch1 = findViewById(R.id.ch1);
        if (view == play) {
            togglePlay();
        } else if (view == ch0) {
            ch0();
        } else if (view == ch1) {
            ch1();
        } else {
        }
        update();
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
        update();
        return false;
    }

    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        info = what;
        if (info == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            buffering = true;
        } else if (info == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            buffering = false;
        }
        update();
        return false;
    }

    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        bufferingPercent = percent;
        update();
    }

    public void onPrepared(MediaPlayer mp) {
        mp.start();
        update();
    }
}
