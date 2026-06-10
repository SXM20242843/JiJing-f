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
            "ParamMouthOpen",
            "MouthOpen",
            "MouthOpenY",
            "ParamMouthY",
            "ParamMouth_A",
            "PARAM_MOUTH_A",
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
    private LAppModel pendingBindModel;
    private String boundAvatarId = "";
    private String pendingBindAvatarId = "";
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
    private long lastApplyLogUptimeMs = 0L;
    private long lastSkipLogUptimeMs = 0L;
    private long lastBindDelayLogUptimeMs = 0L;
    private StartSummary lastStartSummary = new StartSummary();

    private MouthSyncController() {
    }

    public synchronized void bindModel(LAppModel model, String avatarId) {
        String safeAvatarId = avatarId == null ? "" : avatarId;
        CubismIdManager idManager = CubismFramework.getIdManager();
        if (model == null || model.getModel() == null || idManager == null) {
            pendingBindModel = model;
            pendingBindAvatarId = safeAvatarId;
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastBindDelayLogUptimeMs >= 500L) {
                lastBindDelayLogUptimeMs = now;
                LAppPal.printLog("[MouthSyncBind] delay avatarId=" + safeAvatarId
                        + ", model=" + describeModel(model)
                        + ", reason=model_or_idManager_not_ready");
            }
            return;
        }

        pendingBindModel = null;
        pendingBindAvatarId = "";
        boundModel = model;
        boundAvatarId = safeAvatarId;
        mouthOpenParamName = "";
        mouthFormParamName = "";

        List<String> availableParams = model.getAvailableParameterIds();
        List<String> availableMouthCandidates = findAvailableMouthCandidates(availableParams);
        mouthOpenParamName = findParamName(availableParams, MOUTH_OPEN_CANDIDATES);
        mouthFormParamName = findParamName(availableParams, MOUTH_FORM_CANDIDATES);

        LAppPal.printLog("[MouthSyncBind] avatarId=" + boundAvatarId);
        LAppPal.printLog("[MouthSyncBind] model=" + describeModel(boundModel));
        LAppPal.printLog("[MouthSyncBind] openId=" + safeText(mouthOpenParamName, "<none>"));
        LAppPal.printLog("[MouthSyncBind] formId=" + safeText(mouthFormParamName, "<none>"));
        LAppPal.printLog("[MouthSyncBind] available mouth candidates=" + availableMouthCandidates);

        if (mouthOpenParamName.length() == 0) {
            LAppPal.printLog("[MouthSyncBind] no mouth open parameter found, avatarId=" + boundAvatarId);
        }

        if (!playing && closeStartUptimeMs <= 0L) {
            applyNow(0.0f, 0.0f);
        }
    }

    public synchronized void startWithFrames(List<MouthFrame> frames, long audioStartUptimeMs) {
        startWithFrames(frames, 0L, audioStartUptimeMs);
    }

    public synchronized void startWithFrames(List<MouthFrame> frames, long audioDurationMs, long audioStartUptimeMs) {
        lastStartSummary = new StartSummary();
        activeFrames = normalizeFrames(frames, audioDurationMs, lastStartSummary);
        startUptimeMs = audioStartUptimeMs > 0 ? audioStartUptimeMs : android.os.SystemClock.uptimeMillis();
        endTimeMs = activeFrames.isEmpty() ? startUptimeMs : startUptimeMs + activeFrames.get(activeFrames.size() - 1).timeMs + 220L;
        playing = !activeFrames.isEmpty();
        closeStartUptimeMs = 0L;
        lastApplyLogUptimeMs = 0L;
        lastSkipLogUptimeMs = 0L;

        LAppPal.printLog("[MouthSyncStart] frames=" + activeFrames.size()
                + ", durationMs=" + audioDurationMs
                + ", firstTimeMs=" + lastStartSummary.firstTimeMs
                + ", lastTimeMs=" + lastStartSummary.lastTimeMs
                + ", maxOpen=" + formatFloat(lastStartSummary.maxOpen)
                + ", timeUnit=" + lastStartSummary.timeUnit);
    }

    public synchronized void startWithText(String text, long durationMs, long startUptimeMs) {
        lastStartSummary = new StartSummary();
        activeFrames = normalizeFrames(buildPseudoFrames(text, durationMs), durationMs, lastStartSummary);
        this.startUptimeMs = startUptimeMs > 0 ? startUptimeMs : android.os.SystemClock.uptimeMillis();
        endTimeMs = activeFrames.isEmpty() ? this.startUptimeMs : this.startUptimeMs + activeFrames.get(activeFrames.size() - 1).timeMs + 220L;
        playing = !activeFrames.isEmpty();
        closeStartUptimeMs = 0L;
        lastApplyLogUptimeMs = 0L;
        lastSkipLogUptimeMs = 0L;

        LAppPal.printLog("[MouthSyncStart] frames=" + activeFrames.size()
                + ", durationMs=" + durationMs
                + ", firstTimeMs=" + lastStartSummary.firstTimeMs
                + ", lastTimeMs=" + lastStartSummary.lastTimeMs
                + ", maxOpen=" + formatFloat(lastStartSummary.maxOpen)
                + ", timeUnit=" + lastStartSummary.timeUnit);
    }

    public synchronized void update(long nowUptimeMs) {
        retryPendingBindIfReady();

        if (boundModel == null || !hasMouthOpenParam()) {
            logApplySkip(nowUptimeMs);
            return;
        }

        if (playing) {
            long elapsedMs = Math.max(0L, nowUptimeMs - startUptimeMs);
            SampledFrame sampledFrame = sampleFrame(elapsedMs);
            applyNow(sampledFrame.frame.open, sampledFrame.frame.form);
            logApply(nowUptimeMs, elapsedMs, sampledFrame.index, sampledFrame.frame.open);
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

    public synchronized boolean isPlaying() {
        return playing;
    }

    private void retryPendingBindIfReady() {
        if (pendingBindModel == null) {
            return;
        }
        CubismIdManager idManager = CubismFramework.getIdManager();
        if (idManager == null || pendingBindModel.getModel() == null) {
            return;
        }
        bindModel(pendingBindModel, pendingBindAvatarId);
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

        CubismIdManager idManager = CubismFramework.getIdManager();
        if (idManager == null) {
            return;
        }

        float safeOpen = clampOpen(open);
        float safeForm = clampForm(form);

        CubismModel cubismModel = boundModel.getModel();
        boolean appliedOpen = applyParameter(cubismModel, idManager, mouthOpenParamName, safeOpen);
        if (appliedOpen) {
            lastAppliedOpen = safeOpen;
        }
        if (mouthFormParamName != null && mouthFormParamName.length() > 0) {
            if (applyParameter(cubismModel, idManager, mouthFormParamName, safeForm)) {
                lastAppliedForm = safeForm;
            }
        }
    }

    private boolean applyParameter(CubismModel model, CubismIdManager idManager, String paramName, float value) {
        if (model == null || idManager == null || paramName == null || paramName.length() == 0) {
            return false;
        }
        try {
            CubismId id = idManager.getId(paramName);
            if (id == null) {
                return false;
            }
            int parameterIndex = model.getParameterIndex(id);
            if (parameterIndex < 0 || parameterIndex >= model.getParameterCount()) {
                return false;
            }
            model.setParameterValue(parameterIndex, value, 1.0f);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private SampledFrame sampleFrame(long elapsedMs) {
        if (activeFrames.isEmpty()) {
            return new SampledFrame(new MouthFrame(0L, 0.0f, 0.0f), 0);
        }
        if (elapsedMs <= activeFrames.get(0).timeMs) {
            return new SampledFrame(activeFrames.get(0), 0);
        }
        for (int i = 0; i < activeFrames.size() - 1; i++) {
            MouthFrame current = activeFrames.get(i);
            MouthFrame next = activeFrames.get(i + 1);
            if (elapsedMs >= current.timeMs && elapsedMs <= next.timeMs) {
                long duration = Math.max(1L, next.timeMs - current.timeMs);
                float ratio = (elapsedMs - current.timeMs) / (float) duration;
                float open = current.open + (next.open - current.open) * ratio;
                float form = current.form + (next.form - current.form) * ratio;
                return new SampledFrame(new MouthFrame(elapsedMs, open, form), i);
            }
        }
        return new SampledFrame(activeFrames.get(activeFrames.size() - 1), activeFrames.size() - 1);
    }

    private List<MouthFrame> normalizeFrames(List<MouthFrame> frames, long audioDurationMs, StartSummary summary) {
        List<MouthFrame> rawFrames = new ArrayList<MouthFrame>();
        if (frames != null) {
            rawFrames.addAll(frames);
        }

        boolean hasAnyRealTime = false;
        boolean hasMissingTime = false;
        double maxFrameTime = 0.0d;
        for (int i = 0; i < rawFrames.size(); i++) {
            MouthFrame frame = rawFrames.get(i);
            if (frame == null || !frame.hasTimeField) {
                hasMissingTime = true;
                maxFrameTime = Math.max(maxFrameTime, i * 40.0d);
                continue;
            }
            hasAnyRealTime = true;
            maxFrameTime = Math.max(maxFrameTime, frame.rawTime);
        }

        String timeUnit = "fallback";
        boolean seconds = false;
        if (hasAnyRealTime) {
            double audioDurationSeconds = audioDurationMs > 0L ? audioDurationMs / 1000.0d : 0.0d;
            if (maxFrameTime <= 300.0d || (audioDurationSeconds > 0.0d && maxFrameTime <= audioDurationSeconds + 5.0d)) {
                seconds = true;
                timeUnit = "seconds";
            } else {
                timeUnit = "millis";
            }
        }
        if (hasMissingTime && !hasAnyRealTime) {
            timeUnit = "fallback";
        }

        List<MouthFrame> normalized = new ArrayList<MouthFrame>();
        for (int i = 0; i < rawFrames.size(); i++) {
            MouthFrame frame = rawFrames.get(i);
            if (frame == null) {
                continue;
            }
            double rawTime = frame.hasTimeField ? frame.rawTime : i * 40.0d;
            if (!frame.hasTimeField) {
                LAppPal.printLog("[MouthSyncStart] warning missing time field at index=" + i + ", fallbackMs=" + Math.round(rawTime));
            }
            if (!frame.hasOpenField) {
                LAppPal.printLog("[MouthSyncStart] warning missing open field at index=" + i + ", fallbackOpen=0");
            }
            long timeMs = Math.max(0L, Math.round(seconds && frame.hasTimeField ? rawTime * 1000.0d : rawTime));
            float open = clampOpen(frame.open * 1.25f);
            float form = clampForm(frame.form);
            normalized.add(new MouthFrame(timeMs, open, form, true, frame.hasOpenField));
            summary.maxOpen = Math.max(summary.maxOpen, open);
        }

        Collections.sort(normalized, new Comparator<MouthFrame>() {
            @Override
            public int compare(MouthFrame left, MouthFrame right) {
                return Long.compare(left.timeMs, right.timeMs);
            }
        });

        summary.timeUnit = timeUnit;
        if (!normalized.isEmpty()) {
            summary.firstTimeMs = normalized.get(0).timeMs;
            summary.lastTimeMs = normalized.get(normalized.size() - 1).timeMs;
        }
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
            frame.rawTime = Math.round(frame.rawTime * scale);
            frame.timeMs = Math.round(frame.timeMs * scale);
        }
        frames.add(new MouthFrame(targetDuration, 0.08f, 0.0f));
        frames.add(new MouthFrame(targetDuration + 180L, 0.0f, 0.0f));
        return frames;
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

    private List<String> findAvailableMouthCandidates(List<String> availableParams) {
        List<String> result = new ArrayList<String>();
        if (availableParams == null) {
            return result;
        }
        for (String availableParam : availableParams) {
            if (availableParam == null) {
                continue;
            }
            String lower = availableParam.toLowerCase();
            if (lower.contains("mouth") || lower.contains("lip")) {
                result.add(availableParam);
            }
        }
        for (String candidate : MOUTH_OPEN_CANDIDATES) {
            addIfAvailable(result, availableParams, candidate);
        }
        for (String candidate : MOUTH_FORM_CANDIDATES) {
            addIfAvailable(result, availableParams, candidate);
        }
        return result;
    }

    private void addIfAvailable(List<String> result, List<String> availableParams, String candidate) {
        for (String availableParam : availableParams) {
            if (candidate.equals(availableParam) && !result.contains(availableParam)) {
                result.add(availableParam);
                return;
            }
        }
    }

    private void logApply(long nowUptimeMs, long elapsedMs, int frameIndex, float open) {
        if (nowUptimeMs - lastApplyLogUptimeMs < 500L) {
            return;
        }
        lastApplyLogUptimeMs = nowUptimeMs;
        LAppPal.printLog("[MouthSyncApply] elapsedMs=" + elapsedMs
                + ", frameIndex=" + frameIndex
                + ", open=" + formatFloat(open)
                + ", openId=" + mouthOpenParamName
                + ", model=" + describeModel(boundModel));
    }

    private void logApplySkip(long nowUptimeMs) {
        if (!playing && closeStartUptimeMs <= 0L) {
            return;
        }
        if (nowUptimeMs - lastSkipLogUptimeMs < 500L) {
            return;
        }
        lastSkipLogUptimeMs = nowUptimeMs;
        LAppPal.printLog("[MouthSyncApply] skip reason=no_model_or_open_id");
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

    private String describeModel(LAppModel model) {
        if (model == null) {
            return "<null>";
        }
        return model.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(model));
    }

    private String formatFloat(float value) {
        return String.format(java.util.Locale.US, "%.3f", value);
    }

    private static class SampledFrame {
        final MouthFrame frame;
        final int index;

        SampledFrame(MouthFrame frame, int index) {
            this.frame = frame;
            this.index = index;
        }
    }

    private static class StartSummary {
        long firstTimeMs = 0L;
        long lastTimeMs = 0L;
        float maxOpen = 0.0f;
        String timeUnit = "fallback";
    }

    public static class MouthFrame {
        public long timeMs;
        public double rawTime;
        public float open;
        public float form;
        public boolean hasTimeField;
        public boolean hasOpenField;

        public MouthFrame(long timeMs, float open, float form) {
            this(timeMs, open, form, true, true);
        }

        public MouthFrame(double rawTime, float open, float form, boolean hasTimeField, boolean hasOpenField) {
            this.rawTime = rawTime;
            this.timeMs = Math.max(0L, Math.round(rawTime));
            this.open = open;
            this.form = form;
            this.hasTimeField = hasTimeField;
            this.hasOpenField = hasOpenField;
        }
    }
}
