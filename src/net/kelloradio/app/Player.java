package net.kelloradio.app;

import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;

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

    int audioStreamType = AudioManager.STREAM_ALARM;

    String name = "";
    String url = "";

    public boolean stopped = false; // user has stopped playback
    public MediaPlayer mediaPlayer = null;

    public boolean buffering = false;
    public int info = 0;
    public int error = 0;
    public int bufferingPercent = 0;
    public Exception exception = null;

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
        exception = null;
        error = 0;
        info = 0;
        buffering = false;
        bufferingPercent = 0;
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnInfoListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setAudioStreamType(audioStreamType);
        try {
            mediaPlayer.setDataSource(this.url);
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            exception = e;
            stop();
            view.update();
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

    public boolean error() {
        return exception != null || error != 0;
    }

    public String getMessage() {
        if (exception != null) {
            return exception.getMessage();
        } else {
            return Integer.toString(error);
        }
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

    public int getAudioStreamType(String pref, int defaultValue) {
        int type;
        try {
            type = Integer.parseInt(pref);
        } catch (Exception e) {
            return defaultValue;
        }
        switch (type) {
        case AudioManager.STREAM_ALARM: break;
        case AudioManager.STREAM_DTMF:
            if (Build.VERSION.SDK_INT < 5)
                return defaultValue;
            break;
        case AudioManager.STREAM_MUSIC: break;
        case AudioManager.STREAM_NOTIFICATION: break;
        case AudioManager.STREAM_RING: break;
        case AudioManager.STREAM_SYSTEM: break;
        case AudioManager.STREAM_VOICE_CALL: break;
        case AudioManager.USE_DEFAULT_STREAM_TYPE: break;
        default:
            return defaultValue;
        }
        return type;
    }

    public void load(SharedPreferences settings) {
        audioStreamType = getAudioStreamType(settings.getString("pref_audio_stream_type", ""), AudioManager.STREAM_ALARM);
        name = settings.getString("name", "");
        url = settings.getString("url", "");
    }

    public void save(SharedPreferences settings, SharedPreferences.Editor editor) {
        editor.putString("pref_audio_stream_type", Integer.toString(audioStreamType));
        editor.putString("name", name);
        editor.putString("url", url);
    }
}
