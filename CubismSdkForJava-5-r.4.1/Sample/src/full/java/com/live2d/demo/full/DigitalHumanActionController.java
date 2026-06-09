package com.live2d.demo.full;

import com.live2d.demo.LAppDefine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DigitalHumanActionController {
    private static final DigitalHumanActionController INSTANCE = new DigitalHumanActionController();

    public static DigitalHumanActionController getInstance() {
        return INSTANCE;
    }

    private LAppModel boundModel;
    private String boundAvatarId = "";
    private Map<String, Integer> motionGroupCounts;
    private List<String> expressionNames;

    private DigitalHumanActionController() {
    }

    public synchronized void bindModel(LAppModel model, String avatarId) {
        boundModel = model;
        boundAvatarId = avatarId == null ? "" : avatarId;
        motionGroupCounts = model == null ? null : model.getMotionGroupCounts();
        expressionNames = model == null ? new ArrayList<String>() : model.getExpressionNames();
        LAppPal.printLog("[DigitalHumanCapability] avatarId=" + boundAvatarId
                + ", motions=" + motionGroupCounts
                + ", expressions=" + expressionNames);
    }

    public synchronized void apply(String action, String emotion) {
        if (boundModel == null) {
            return;
        }

        String actualAction = firstNotBlank(action).toLowerCase(Locale.ROOT);
        String actualEmotion = firstNotBlank(emotion).toLowerCase(Locale.ROOT);

        String motionGroup = resolveMotionGroup(actualAction);
        if (motionGroup.length() > 0 && boundModel.startRandomMotionIfAvailable(motionGroup, LAppDefine.Priority.NORMAL.getPriority())) {
            LAppPal.printLog("[DigitalHumanAction] action=" + safeLabel(actualAction, "idle")
                    + " mappedTo=" + safeGroupLabel(motionGroup) + "[random]");
        } else {
            returnToIdle();
            LAppPal.printLog("[DigitalHumanAction] action=" + safeLabel(actualAction, "idle")
                    + " not supported, fallback=Idle");
        }

        String expression = resolveExpression(actualEmotion);
        if (expression.length() > 0 && boundModel.setExpressionIfAvailable(expression)) {
            LAppPal.printLog("[DigitalHumanAction] emotion=" + safeLabel(actualEmotion, "neutral")
                    + " mappedTo=" + expression);
        } else if (actualEmotion.length() > 0) {
            LAppPal.printLog("[DigitalHumanAction] emotion=" + actualEmotion + " not supported, fallback=idle");
        }
    }

    public synchronized void returnToIdle() {
        if (boundModel == null) {
            return;
        }
        boundModel.startRandomMotionIfAvailable(LAppDefine.MotionGroup.IDLE.getId(), LAppDefine.Priority.IDLE.getPriority());
    }

    private String resolveMotionGroup(String action) {
        if ("explain".equals(action)) {
            return findFirstMotionGroup("TapBody", "Tap", "FlickRight", "FlickLeft", "FlickUp", "Idle", "");
        }
        if ("welcome".equals(action)) {
            return findFirstMotionGroup("TapBody", "Tap", "Flick", "FlickRight", "");
        }
        if ("thinking".equals(action)) {
            return findFirstMotionGroup("FlickUp", "Flick", "Tap", "Idle", "");
        }
        if ("listen".equals(action)) {
            return findFirstMotionGroup("Idle", "");
        }
        if ("idle".equals(action)) {
            return findFirstMotionGroup("Idle", "");
        }
        return findFirstMotionGroup("TapBody", "Tap", "Flick", "FlickRight", "FlickLeft", "FlickUp", "FlickDown", "Shake", "Idle", "");
    }

    private String resolveExpression(String emotion) {
        if ("warm".equals(emotion) || "happy".equals(emotion)) {
            return findFirstExpression("smile", "happy", "soft");
        }
        if ("sad".equals(emotion)) {
            return findFirstExpression("sad");
        }
        if ("surprised".equals(emotion) || "surprise".equals(emotion)) {
            return findFirstExpression("surprise", "smile");
        }
        return "";
    }

    private String findFirstMotionGroup(String... candidates) {
        if (motionGroupCounts == null || motionGroupCounts.isEmpty() || candidates == null) {
            return "";
        }
        for (String candidate : candidates) {
            for (Map.Entry<String, Integer> entry : motionGroupCounts.entrySet()) {
                String group = entry.getKey();
                Integer count = entry.getValue();
                if (count == null || count <= 0) {
                    continue;
                }
                if (candidate.equals(group)) {
                    return group;
                }
            }
        }
        for (String candidate : candidates) {
            for (Map.Entry<String, Integer> entry : motionGroupCounts.entrySet()) {
                String group = entry.getKey();
                Integer count = entry.getValue();
                if (count == null || count <= 0) {
                    continue;
                }
                if (candidate.equalsIgnoreCase(group == null ? "" : group)) {
                    return group == null ? "" : group;
                }
            }
        }
        return "";
    }

    private String findFirstExpression(String... keywords) {
        if (expressionNames == null || expressionNames.isEmpty() || keywords == null) {
            return "";
        }
        for (String keyword : keywords) {
            for (String expressionName : expressionNames) {
                if (expressionName != null && expressionName.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) {
                    return expressionName;
                }
            }
        }
        return "";
    }

    private String firstNotBlank(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeLabel(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }

    private String safeGroupLabel(String value) {
        return value == null || value.length() == 0 ? "<default>" : value;
    }
}
