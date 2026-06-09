package com.live2d.demo.full;

import com.live2d.sdk.cubism.framework.CubismFramework;
import com.live2d.sdk.cubism.framework.id.CubismId;
import com.live2d.sdk.cubism.framework.id.CubismIdManager;
import com.live2d.sdk.cubism.framework.model.CubismModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MouthSyncController {
    private static final String[] MOUTH_OPEN_CANDIDATES = new String[]{
            "ParamMouthOpenY",
            "PARAM_MOUTH_OPEN_Y",
            "MouthOpenY",
            "ParamMouthOpen",
            "PARAM_MOUTH_OPEN"
    };

    private static final String[] MOUTH_FORM_CANDIDATES = new String[]{
            "ParamMouthForm",
            "PARAM_MOUTH_FORM",
            "MouthForm"
    };

    private static final MouthSyncController INSTANCE = new MouthSyncController();

    public static MouthSyncController getInstance() {
        return INSTANCE;
    }

    private LAppModel boundModel;
    private String boundAvatarId = "";
    private String mouthOpenParamName = "";
    private String mouthFormParamName = "";
    private List<MouthFrame> activeFrames = new ArrayList<MouthFrame>();
    private boolean playing = false;
    private long startUptimeMs = 0L;
    private long endTimeMs = 0L;
    private long closeStartUptimeMs = 0L;
    private long closeDurationMs = 180L;
    private float closeFromOpen = 0.0f;
    private float closeFromForm = 0.0f;
    private float lastAppliedOpen = 0.0f;
    private float lastAppliedForm = 0.0f;

    private MouthSyncController() {
    }

    public synchronized void bindModel(LAppModel model, String avatarId) {
        boundModel = model;
        boundAvatarId = avatarId == null ? "" : avatarId;
        mouthOpenParamName = "";
        mouthFormParamName = "";
        activeFrames.clear();
        playing = false;
        startUptimeMs = 0L;
        endTimeMs = 0L;
        closeStartUptimeMs = 0L;
        lastAppliedOpen = 0.0f;
        lastAppliedForm = 0.0f;

        List<String> availableParams = model == null ? new ArrayList<String>() : model.getAvailableParameterIds();
        mouthOpenParamName = findParamName(availableParams, MOUTH_OPEN_CANDIDATES);
        mouthFormParamName = findParamName(availableParams, MOUTH_FORM_CANDIDATES);

        LAppPal.printLog("[Live2DParam] avatarId=" + boundAvatarId
                + ", mouthOpenParam=" + safeText(mouthOpenParamName, "<none>")
                + ", mouthFormParam=" + safeText(mouthFormParamName, "<none>"));
        LAppPal.printLog("[Live2DParam] availableParams.size=" + availableParams.size()
                + ", preview=" + buildParamPreview(availableParams));

        if (mouthOpenParamName.length() == 0) {
            LAppPal.printLog("[Live2DParam] warning avatarId=" + boundAvatarId + " has no mouthOpen parameter");
        }
        if (mouthFormParamName.length() == 0) {
            LAppPal.printLog("[Live2DParam] warning avatarId=" + boundAvatarId + " has no mouthForm parameter");
        }

        applyNow(0.0f, 0.0f);
    }

    public synchronized void startWithFrames(List<MouthFrame> frames, long audioStartUptimeMs) {
        activeFrames = normalizeFrames(frames);
        startUptimeMs = audioStartUptimeMs > 0 ? audioStartUptimeMs : android.os.SystemClock.uptimeMillis();
        endTimeMs = activeFrames.isEmpty() ? startUptimeMs : startUptimeMs + activeFrames.get(activeFrames.size() - 1).timeMs + 220L;
        playing = !activeFrames.isEmpty();
        closeStartUptimeMs = 0L;
    }

    public synchronized void startWithText(String text, long durationMs, long startUptimeMs) {
        activeFrames = buildPseudoFrames(text, durationMs);
        this.startUptimeMs = startUptimeMs > 0 ? startUptimeMs : android.os.SystemClock.uptimeMillis();
        endTimeMs = activeFrames.isEmpty() ? this.startUptimeMs : this.startUptimeMs + activeFrames.get(activeFrames.size() - 1).timeMs + 220L;
        playing = !activeFrames.isEmpty();
        closeStartUptimeMs = 0L;
    }

    public synchronized void update(long nowUptimeMs) {
        if (boundModel == null || !hasMouthOpenParam()) {
            return;
        }

        if (playing) {
            long elapsedMs = Math.max(0L, nowUptimeMs - startUptimeMs);
            MouthFrame frame = sampleFrame(elapsedMs);
            applyNow(frame.open, frame.form);
            if (nowUptimeMs >= endTimeMs) {
                playing = false;
                beginClose(nowUptimeMs);
            }
            return;
        }

        if (closeStartUptimeMs > 0L) {
            float progress = Math.min(1.0f, (nowUptimeMs - closeStartUptimeMs) / (float) closeDurationMs);
            float open = closeFromOpen * (1.0f - progress);
            float form = closeFromForm * (1.0f - progress);
            applyNow(open, form);
            if (progress >= 1.0f) {
                closeStartUptimeMs = 0L;
                applyNow(0.0f, 0.0f);
            }
            return;
        }

        if (lastAppliedOpen != 0.0f || lastAppliedForm != 0.0f) {
            applyNow(0.0f, 0.0f);
        }
    }

    public synchronized void stopAndReset() {
        playing = false;
        activeFrames.clear();
        beginClose(android.os.SystemClock.uptimeMillis());
    }

    public synchronized boolean hasMouthOpenParam() {
        return mouthOpenParamName != null && mouthOpenParamName.length() > 0;
    }

    private void beginClose(long nowUptimeMs) {
        closeFromOpen = lastAppliedOpen;
        closeFromForm = lastAppliedForm;
        closeStartUptimeMs = nowUptimeMs;
    }

    private void applyNow(float open, float form) {
        if (boundModel == null || boundModel.getModel() == null) {
            return;
        }

        float scaledOpen = clampOpen(open * 1.25f);
        float scaledForm = clampForm(form);

        CubismIdManager idManager = CubismFramework.getIdManager();
        if (idManager == null) {
            return;
        }

        CubismModel cubismModel = boundModel.getModel();
        applyParameter(cubismModel, idManager, mouthOpenParamName, scaledOpen, false);
        if (mouthFormParamName != null && mouthFormParamName.length() > 0) {
            applyParameter(cubismModel, idManager, mouthFormParamName, scaledForm, false);
        }

        lastAppliedOpen = scaledOpen;
        lastAppliedForm = scaledForm;
    }

    private void applyParameter(CubismModel model, CubismIdManager idManager, String paramName, float value, boolean additive) {
        if (model == null || idManager == null || paramName == null || paramName.length() == 0) {
            return;
        }
        try {
            CubismId id = idManager.getId(paramName);
            if (id == null || model.getParameterIndex(id) < 0) {
                return;
            }
            if (additive) {
                model.addParameterValue(id, value, 1.0f);
            } else {
                model.setParameterValue(id, value, 1.0f);
            }
        } catch (Exception ignored) {
        }
    }

    private MouthFrame sampleFrame(long elapsedMs) {
        if (activeFrames.isEmpty()) {
            return new MouthFrame(0L, 0.0f, 0.0f);
        }
        if (elapsedMs <= activeFrames.get(0).timeMs) {
            return activeFrames.get(0);
        }
        for (int i = 0; i < activeFrames.size() - 1; i++) {
            MouthFrame current = activeFrames.get(i);
            MouthFrame next = activeFrames.get(i + 1);
            if (elapsedMs >= current.timeMs && elapsedMs <= next.timeMs) {
                long duration = Math.max(1L, next.timeMs - current.timeMs);
                float ratio = (elapsedMs - current.timeMs) / (float) duration;
                float open = current.open + (next.open - current.open) * ratio;
                float form = current.form + (next.form - current.form) * ratio;
                return new MouthFrame(elapsedMs, open, form);
            }
        }
        return activeFrames.get(activeFrames.size() - 1);
    }

    private List<MouthFrame> normalizeFrames(List<MouthFrame> frames) {
        List<MouthFrame> normalized = new ArrayList<MouthFrame>();
        if (frames != null) {
            normalized.addAll(frames);
        }
        Collections.sort(normalized, new Comparator<MouthFrame>() {
            @Override
            public int compare(MouthFrame left, MouthFrame right) {
                return Long.compare(left.timeMs, right.timeMs);
            }
        });
        return normalized;
    }

    private List<MouthFrame> buildPseudoFrames(String text, long durationMs) {
        List<MouthFrame> frames = new ArrayList<MouthFrame>();
        String safeText = text == null ? "" : text.trim();
        if (safeText.length() == 0) {
            safeText = "数字人导览";
        }

        long targetDuration = Math.max(1200L, Math.min(15000L, durationMs > 0 ? durationMs : safeText.length() * 180L));
        long cursor = 0L;
        float smoothOpen = 0.18f;

        for (int i = 0; i < safeText.length(); i++) {
            char c = safeText.charAt(i);
            if (isPausePunctuation(c)) {
                frames.add(new MouthFrame(cursor, smoothOpen * 0.35f, 0.0f));
                cursor += getPauseDurationMs(c);
                continue;
            }

            float targetOpen = 0.15f + ((i % 5) * 0.14f);
            targetOpen += (Math.abs(c) % 4) * 0.05f;
            if (targetOpen > 0.85f) {
                targetOpen = 0.85f;
            }
            smoothOpen = smoothOpen + (targetOpen - smoothOpen) * 0.55f;
            frames.add(new MouthFrame(cursor, smoothOpen, 0.0f));
            cursor += 90L + (Math.abs(c) % 31);
        }

        if (cursor <= 0L) {
            cursor = targetDuration;
        }
        float scale = targetDuration / (float) cursor;
        for (MouthFrame frame : frames) {
            frame.timeMs = Math.round(frame.timeMs * scale);
        }
        frames.add(new MouthFrame(targetDuration, 0.08f, 0.0f));
        frames.add(new MouthFrame(targetDuration + 180L, 0.0f, 0.0f));
        return normalizeFrames(frames);
    }

    private boolean isPausePunctuation(char c) {
        return c == '，' || c == '、' || c == '：' || c == '；'
                || c == ',' || c == ';' || c == ':'
                || c == '。' || c == '！' || c == '？'
                || c == '.' || c == '!' || c == '?';
    }

    private long getPauseDurationMs(char c) {
        if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
            return 300L;
        }
        return 150L;
    }

    private String findParamName(List<String> availableParams, String[] candidates) {
        if (availableParams == null || candidates == null) {
            return "";
        }
        for (String candidate : candidates) {
            for (String availableParam : availableParams) {
                if (candidate.equals(availableParam)) {
                    return availableParam;
                }
            }
        }
        for (String candidate : candidates) {
            for (String availableParam : availableParams) {
                if (candidate.equalsIgnoreCase(availableParam)) {
                    return availableParam;
                }
            }
        }
        return "";
    }

    private float clampOpen(float value) {
        if (value < 0.0f) {
            return 0.0f;
        }
        if (value > 1.0f) {
            return 1.0f;
        }
        return value;
    }

    private float clampForm(float value) {
        if (value < -1.0f) {
            return -1.0f;
        }
        if (value > 1.0f) {
            return 1.0f;
        }
        return value;
    }

    private String safeText(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }

    private String buildParamPreview(List<String> availableParams) {
        if (availableParams == null || availableParams.isEmpty()) {
            return "[]";
        }
        int previewSize = Math.min(8, availableParams.size());
        return availableParams.subList(0, previewSize).toString();
    }

    public static class MouthFrame {
        public long timeMs;
        public float open;
        public float form;

        public MouthFrame(long timeMs, float open, float form) {
            this.timeMs = timeMs;
            this.open = open;
            this.form = form;
        }
    }
}
