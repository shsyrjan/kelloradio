package net.kelloradio.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;

public class ChannelStore
{
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

    public void stockReset(Context context, SharedPreferences settings, SharedPreferences.Editor editor) {
        editor.putInt("ch_num", 6);
        editor.putString("ch_0_name", context.getString(R.string.stock_ch_0_name));
        editor.putString("ch_0_url", context.getString(R.string.stock_ch_0_url));
        editor.putBoolean("ch_0_starred", true);
        editor.putString("ch_1_name", context.getString(R.string.stock_ch_1_name));
        editor.putString("ch_1_url", context.getString(R.string.stock_ch_1_url));
        editor.putBoolean("ch_1_starred", true);
        editor.putString("ch_2_name", context.getString(R.string.stock_ch_2_name));
        editor.putString("ch_2_url", context.getString(R.string.stock_ch_2_url));
        editor.putBoolean("ch_2_starred", true);
        editor.putString("ch_3_name", context.getString(R.string.stock_ch_3_name));
        editor.putString("ch_3_url", context.getString(R.string.stock_ch_3_url));
        editor.putBoolean("ch_3_starred", false);
        editor.putString("ch_4_name", context.getString(R.string.stock_ch_4_name));
        editor.putString("ch_4_url", context.getString(R.string.stock_ch_4_url));
        editor.putBoolean("ch_4_starred", false);
        editor.putString("ch_5_name", context.getString(R.string.stock_ch_5_name));
        editor.putString("ch_5_url", context.getString(R.string.stock_ch_5_url));
        editor.putBoolean("ch_5_starred", false);
    }

    public void load(SharedPreferences settings) {
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

    public void save(SharedPreferences settings, SharedPreferences.Editor editor) {
        int old_ch_num = settings.getInt("ch_num", 0);
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
    }

    public int size() {
        return channels.size();
    }

    public Channel get(int i) {
        return channels.get(i);
    }

    public void remove(Channel channel) {
        channels.remove(channel);
    }

    public Channel edit(Channel channel) {
        targetChannel = channel;
        editedChannel.assign(channel);
        return editedChannel;
    }

    public void commit() {
        targetChannel.assign(editedChannel);
        if (targetChannel == newChannel) {
            channels.add(new Channel(newChannel));
        }
    }

    public Channel editNew() {
        newChannel.name = "";
        newChannel.url = "";
        newChannel.starred = false;
        return edit(newChannel);
    }

    public boolean adding() {
        return (targetChannel == newChannel);
    }

    public void removeEditorTarget() {
        if (targetChannel != null && targetChannel != newChannel) {
            channels.remove(targetChannel);
        }
    }
}
