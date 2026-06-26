package com.nfclock.plugin;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * NFC 音效（与 xhky control 一致：贴卡 / 成功 / 失败）
 */
public class NFCLockSoundHelper {

    private static final String TAG = "NFCLockSoundHelper";

    public static final int SOUND_NFC = 1;
    public static final int SOUND_SUCCESS = 2;
    public static final int SOUND_ERROR = 3;

    private SoundPool soundPool;
    private final Map<Integer, Integer> soundIds = new HashMap<>();

    public void init(Context context) {
        if (soundPool != null) {
            return;
        }
        soundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        load(context, SOUND_NFC, "sound/sound_nfc.mp3");
        load(context, SOUND_SUCCESS, "sound/sound_success.mp3");
        load(context, SOUND_ERROR, "sound/sound_error.mp3");
    }

    private void load(Context context, int type, String assetPath) {
        try {
            AssetFileDescriptor fd = context.getAssets().openFd(assetPath);
            soundIds.put(type, soundPool.load(fd, 1));
        } catch (IOException e) {
            Log.e(TAG, "加载音效失败: " + assetPath, e);
        }
    }

    public void play(int type) {
        if (soundPool == null) {
            return;
        }
        Integer soundId = soundIds.get(type);
        if (soundId != null && soundId > 0) {
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        soundIds.clear();
    }
}
