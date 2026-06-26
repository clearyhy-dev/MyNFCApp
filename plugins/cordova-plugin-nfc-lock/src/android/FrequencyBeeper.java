package com.nfclock.plugin;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * 充电过程蜂鸣（与 xhky control FrequencyBeeper 一致：频率/间隔随档位变化）
 */
public class FrequencyBeeper {

    private static final String TAG = "FrequencyBeeper";
    private static final int[] FREQUENCIES = {250, 500, 750, 1000, 1250};
    private static final int[] INTERVALS = {300, 250, 200, 150, 100};
    private static final int SAMPLE_RATE = 44100;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioTrack audioTrack;
    private NFCLockRSSILevel currentLevel;

    public void startBeeping(NFCLockRSSILevel level) {
        stopBeeping();
        currentLevel = level;
        ensureAudioTrack();
        if (audioTrack == null || audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack 初始化失败");
            return;
        }
        audioTrack.play();
        playNextBeep();
    }

    public void stopBeeping() {
        handler.removeCallbacksAndMessages(null);
        currentLevel = null;
        if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            try {
                audioTrack.stop();
                audioTrack.flush();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    public void release() {
        stopBeeping();
        if (audioTrack != null) {
            audioTrack.release();
            audioTrack = null;
        }
    }

    private void ensureAudioTrack() {
        if (audioTrack != null) {
            return;
        }
        int bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (bufferSize <= 0) {
            return;
        }
        audioTrack = new AudioTrack(
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE);
    }

    private void playNextBeep() {
        if (currentLevel == null || audioTrack == null) {
            return;
        }
        int index = currentLevel.getLevel() - 1;
        int frequency = FREQUENCIES[index];
        int intervalMs = INTERVALS[index];
        short[] tone = generateTone(frequency, intervalMs);
        audioTrack.write(tone, 0, tone.length);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                playNextBeep();
            }
        }, intervalMs);
    }

    private short[] generateTone(int frequencyHz, int durationMs) {
        int sampleCount = (durationMs * SAMPLE_RATE) / 1000;
        short[] samples = new short[sampleCount];
        double step = (frequencyHz * 2 * Math.PI) / SAMPLE_RATE;
        for (int i = 0; i < sampleCount; i++) {
            samples[i] = (short) (Math.sin(i * step) * 32767.0d);
        }
        return samples;
    }
}
