package com.live2d.demo.full;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MouthSyncManager {
    private static final MouthSyncManager INSTANCE = new MouthSyncManager();

    private final List<MouthFrame> frames = new ArrayList<>();

    private volatile boolean playing = false;
    private volatile long startTimeMs = 0L;

    private MouthSyncManager() {
    }

    public static MouthSyncManager getInstance() {
        return INSTANCE;
    }

    public synchronized void start(List<MouthFrame> mouthFrames) {
        frames.clear();

        if (mouthFrames != null && mouthFrames.size() > 0) {
            frames.addAll(mouthFrames);
            Collections.sort(frames, new Comparator<MouthFrame>() {
                @Override
                public int compare(MouthFrame a, MouthFrame b) {
                    return a.t - b.t;
                }
            });
        }

        startTimeMs = System.currentTimeMillis();
        playing = true;
    }

    public synchronized void stop() {
        playing = false;
        frames.clear();
        startTimeMs = 0L;
    }

    public boolean isPlaying() {
        return playing;
    }

    public float getCurrentMouthOpen() {
        if (!playing) {
            return 0.0f;
        }

        if (frames.size() == 0) {
            return 0.0f;
        }

        long elapsed = System.currentTimeMillis() - startTimeMs;

        MouthFrame first = frames.get(0);
        MouthFrame last = frames.get(frames.size() - 1);

        if (elapsed < first.t) {
            return clamp(first.open);
        }

        if (elapsed > last.t + 150) {
            return 0.0f;
        }

        for (int i = 0; i < frames.size() - 1; i++) {
            MouthFrame current = frames.get(i);
            MouthFrame next = frames.get(i + 1);

            if (elapsed >= current.t && elapsed <= next.t) {
                int duration = next.t - current.t;

                if (duration <= 0) {
                    return clamp(current.open);
                }

                float ratio = (elapsed - current.t) * 1.0f / duration;
                float value = current.open + (next.open - current.open) * ratio;

                return clamp(value);
            }
        }

        return clamp(last.open);
    }

    public float getCurrentMouthForm() {
        if (!playing || frames.size() == 0) {
            return 0.0f;
        }

        long elapsed = System.currentTimeMillis() - startTimeMs;

        MouthFrame first = frames.get(0);
        MouthFrame last = frames.get(frames.size() - 1);

        if (elapsed < first.t) {
            return clampForm(first.form);
        }

        if (elapsed > last.t + 150) {
            return 0.0f;
        }

        for (int i = 0; i < frames.size() - 1; i++) {
            MouthFrame current = frames.get(i);
            MouthFrame next = frames.get(i + 1);

            if (elapsed >= current.t && elapsed <= next.t) {
                int duration = next.t - current.t;

                if (duration <= 0) {
                    return clampForm(current.form);
                }

                float ratio = (elapsed - current.t) * 1.0f / duration;
                float value = current.form + (next.form - current.form) * ratio;

                return clampForm(value);
            }
        }

        return clampForm(last.form);
    }

    private float clamp(float value) {
        if (value < 0.0f) return 0.0f;
        if (value > 1.0f) return 1.0f;
        return value;
    }

    private float clampForm(float value) {
        if (value < -1.0f) return -1.0f;
        if (value > 1.0f) return 1.0f;
        return value;
    }

    public static class MouthFrame {
        public int t;
        public float open;
        public float form;

        public MouthFrame(int t, float open, float form) {
            this.t = t;
            this.open = open;
            this.form = form;
        }
    }
}