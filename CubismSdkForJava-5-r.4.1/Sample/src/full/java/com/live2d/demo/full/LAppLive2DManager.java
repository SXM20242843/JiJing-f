/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.demo.full;

import com.live2d.sdk.cubism.framework.math.CubismMatrix44;
import com.live2d.sdk.cubism.framework.motion.ACubismMotion;
import com.live2d.sdk.cubism.framework.motion.IBeganMotionCallback;
import com.live2d.sdk.cubism.framework.motion.IFinishedMotionCallback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.content.res.AssetManager;

import static com.live2d.demo.LAppDefine.*;

public class LAppLive2DManager {
    private static final String EXTRA_AVATAR_ID = "avatarId";
    private static final String EXTRA_CLOTHES_MODE = "clothesMode";

    private static final String AVATAR_DEFAULT = "guide_female_01";
    private static final String CLOTHES_NONE = "";
    private static final String CLOTHES_DRESS = "dress";
    private static final String CLOTHES_UNIFORM = "uniform";

    public static LAppLive2DManager getInstance() {
        if (s_instance == null) {
            s_instance = new LAppLive2DManager();
        }
        return s_instance;
    }

    public static void releaseInstance() {
        s_instance = null;
    }

    private static final class ModelEntry {
        final String avatarId;
        final String displayName;
        final String modelDirName;
        final String modelJsonName;
        final String defaultClothesMode;

        ModelEntry(String avatarId,
                   String displayName,
                   String modelDirName,
                   String modelJsonName,
                   String defaultClothesMode) {
            this.avatarId = avatarId;
            this.displayName = displayName;
            this.modelDirName = modelDirName;
            this.modelJsonName = modelJsonName;
            this.defaultClothesMode = defaultClothesMode == null ? "" : defaultClothesMode;
        }
    }

    public void releaseAllModel() {
        for (LAppModel model : models) {
            model.deleteModel();
        }
        models.clear();
    }

    public void setUpModel() {
        modelEntries.clear();

        // haru：默认连衣裙版
        addModelIfExists(
                "guide_default_01",
                "Haru 连衣裙版",
                "haru",
                "haru.model3.json",
                CLOTHES_DRESS
        );

        // haru：显式连衣裙版，给后台配置用
        addModelIfExists(
                "guide_haru_dress",
                "Haru 连衣裙版",
                "haru",
                "haru.model3.json",
                CLOTHES_DRESS
        );

        // haru：制服版，同一个 haru.model3.json，只切换部件透明度
        addModelIfExists(
                "guide_haru_uniform",
                "Haru 制服版",
                "haru",
                "haru.model3.json",
                CLOTHES_UNIFORM
        );

        // greeter：完整模型切换
        addModelIfExists(
                "guide_female_01",
                "文旅女导游",
                "haru_greeter",
                "haru_greeter_t05.model3.json",
                CLOTHES_NONE
        );

        // koharu：完整模型切换
        addModelIfExists(
                "guide_female_02",
                "小晴",
                "koharu",
                "koharu.model3.json",
                CLOTHES_NONE
        );

        // haru：第三个女性数字人 ID，复用制服版资源作为兜底
        addModelIfExists(
                "guide_female_03",
                "文旅女导游制服版",
                "haru",
                "haru.model3.json",
                CLOTHES_UNIFORM
        );

        // haruto：完整模型切换
        addModelIfExists(
                "guide_male_01",
                "小航",
                "haruto",
                "haruto.model3.json",
                CLOTHES_NONE
        );

        if (modelEntries.isEmpty()) {
            throw new IllegalStateException("assets 中没有找到可用的 Live2D 模型资源。");
        }
    }

    private void addModelIfExists(String avatarId,
                                  String displayName,
                                  String modelDirName,
                                  String modelJsonName,
                                  String defaultClothesMode) {
        if (hasAssetFile(modelDirName, modelJsonName)) {
            modelEntries.add(new ModelEntry(
                    avatarId,
                    displayName,
                    modelDirName,
                    modelJsonName,
                    defaultClothesMode
            ));

            if (DEBUG_LOG_ENABLE) {
                LAppPal.printLog("注册 Live2D 模型成功: avatarId=" + avatarId
                        + ", clothesMode=" + defaultClothesMode
                        + ", dir=" + modelDirName
                        + ", json=" + modelJsonName);
            }
        } else {
            LAppPal.printLog("跳过 Live2D 模型，文件不存在: "
                    + modelDirName + "/" + modelJsonName);
        }
    }

    private boolean hasAssetFile(String modelDirName, String modelJsonName) {
        final AssetManager assets = LAppDelegate.getInstance()
                .getActivity()
                .getResources()
                .getAssets();

        try {
            String[] files = assets.list(modelDirName);
            if (files == null) {
                return false;
            }

            for (String file : files) {
                if (modelJsonName.equals(file)) {
                    return true;
                }
            }
        } catch (IOException e) {
            LAppPal.printLog("检查模型资源失败: " + modelDirName + "/" + modelJsonName);
        }

        return false;
    }

    public void onUpdate() {
        int width = LAppDelegate.getInstance().getWindowWidth();
        int height = LAppDelegate.getInstance().getWindowHeight();

        for (int i = 0; i < models.size(); i++) {
            LAppModel model = models.get(i);

            if (model.getModel() == null) {
                LAppPal.printLog("模型为空，无法绘制。");
                continue;
            }

            projection.loadIdentity();

            if (model.getModel().getCanvasWidth() > 1.0f && width < height) {
                model.getModelMatrix().setWidth(2.0f);
                projection.scale(1.0f, (float) width / (float) height);
            } else {
                projection.scale((float) height / (float) width, 1.0f);
            }

            if (viewMatrix != null) {
                viewMatrix.multiplyByMatrix(projection);
            }

            LAppDelegate.getInstance().getView().preModelDraw(model);
            model.update();
            model.draw(projection);
            LAppDelegate.getInstance().getView().postModelDraw(model);
        }
    }

    public void onDrag(float x, float y) {
        for (int i = 0; i < models.size(); i++) {
            LAppModel model = getModel(i);
            if (model != null) {
                model.setDragging(x, y);
            }
        }
    }

    public void onTap(float x, float y) {
        if (DEBUG_LOG_ENABLE) {
            LAppPal.printLog("点击位置: {" + x + ", y: " + y + "}");
        }

        for (int i = 0; i < models.size(); i++) {
            LAppModel model = models.get(i);

            if (model.hitTest(HitAreaName.HEAD.getId(), x, y)) {
                if (DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("点击区域: " + HitAreaName.HEAD.getId());
                }

                model.setRandomExpression();
            } else if (model.hitTest(HitAreaName.BODY.getId(), x, y)) {
                if (DEBUG_LOG_ENABLE) {
                    LAppPal.printLog("点击区域: " + HitAreaName.BODY.getId());
                }

                model.startRandomMotion(
                        MotionGroup.TAP_BODY.getId(),
                        Priority.NORMAL.getPriority(),
                        finishedMotion,
                        beganMotion
                );
            }
        }
    }

    public void nextScene() {
        if (modelEntries.isEmpty()) {
            setUpModel();
        }

        final int number = (currentModel + 1) % modelEntries.size();
        changeScene(number);
    }

    public void changeSceneByAvatarId(String avatarId) {
        changeSceneByAvatarId(avatarId, "");
    }

    public void changeSceneByAvatarId(String avatarId, String requestedClothesMode) {
        String normalizedAvatarId = normalizeAvatarId(avatarId);
        int index = findModelIndexByAvatarId(normalizedAvatarId);

        if (index < 0) {
            LAppPal.printLog("未知 avatarId: " + avatarId + "，自动切换到默认数字人。");
            index = findModelIndexByAvatarId(AVATAR_DEFAULT);
        }

        if (index < 0) {
            index = 0;
        }

        changeScene(index, requestedClothesMode);
    }

    private int findModelIndexByAvatarId(String avatarId) {
        if (avatarId == null) {
            return -1;
        }

        for (int i = 0; i < modelEntries.size(); i++) {
            if (avatarId.equals(modelEntries.get(i).avatarId)) {
                return i;
            }
        }

        return -1;
    }

    private String normalizeAvatarId(String avatarId) {
        if (avatarId == null || avatarId.trim().length() == 0) {
            return AVATAR_DEFAULT;
        }

        String text = avatarId.trim();

        if ("guide_default_01".equals(text)) {
            return AVATAR_DEFAULT;
        }

        if ("guide_female_01".equals(text)
                || "guide_female_02".equals(text)
                || "guide_female_03".equals(text)
                || "guide_male_01".equals(text)) {
            return text;
        }

        return AVATAR_DEFAULT;
    }

    private boolean isSupportedAvatarId(String avatarId) {
        if (avatarId == null || avatarId.trim().length() == 0) {
            return false;
        }

        String text = avatarId.trim();

        return "guide_default_01".equals(text)
                || "guide_female_01".equals(text)
                || "guide_female_02".equals(text)
                || "guide_female_03".equals(text)
                || "guide_male_01".equals(text);
    }

    public void changeScene(int index) {
        changeScene(index, "");
    }

    public void changeScene(int index, String requestedClothesMode) {
        if (modelEntries.isEmpty()) {
            setUpModel();
        }

        if (index < 0 || index >= modelEntries.size()) {
            index = 0;
        }

        currentModel = index;

        ModelEntry entry = modelEntries.get(index);
        String finalClothesMode = resolveClothesMode(entry, requestedClothesMode);
        currentClothesMode = finalClothesMode;

        if (DEBUG_LOG_ENABLE) {
            LAppPal.printLog("当前模型索引: " + currentModel);
            LAppPal.printLog("当前 avatarId: " + entry.avatarId);
            LAppPal.printLog("当前数字人名称: " + entry.displayName);
            LAppPal.printLog("当前模型目录: " + entry.modelDirName);
            LAppPal.printLog("当前模型 JSON: " + entry.modelJsonName);
            LAppPal.printLog("当前 clothesMode: " + finalClothesMode);
        }

        String modelPath = ResourcePath.ROOT.getPath() + entry.modelDirName + "/";
        String modelJsonName = entry.modelJsonName;

        releaseAllModel();

        LAppModel mainModel = new LAppModel();
        mainModel.loadAssets(modelPath, modelJsonName);
        mainModel.setClothesMode(finalClothesMode);
        models.add(mainModel);

        LAppView.RenderingTarget useRenderingTarget;
        if (USE_RENDER_TARGET) {
            useRenderingTarget = LAppView.RenderingTarget.VIEW_FRAME_BUFFER;
        } else if (USE_MODEL_RENDER_TARGET) {
            useRenderingTarget = LAppView.RenderingTarget.MODEL_FRAME_BUFFER;
        } else {
            useRenderingTarget = LAppView.RenderingTarget.NONE;
        }

        if (USE_RENDER_TARGET || USE_MODEL_RENDER_TARGET) {
            LAppModel secondModel = new LAppModel();
            secondModel.loadAssets(modelPath, modelJsonName);
            secondModel.setClothesMode(finalClothesMode);
            secondModel.getModelMatrix().translateX(0.2f);
            models.add(secondModel);
        }

        LAppDelegate.getInstance().getView().switchRenderingTarget(useRenderingTarget);

        float[] clearColor = {0.0f, 0.0f, 0.0f};
        LAppDelegate.getInstance().getView().setRenderingTargetClearColor(
                clearColor[0],
                clearColor[1],
                clearColor[2]
        );
    }

    private String resolveClothesMode(ModelEntry entry, String requestedClothesMode) {
        String requested = normalizeClothesMode(requestedClothesMode);

        if (requested.length() > 0) {
            return requested;
        }

        if (entry == null) {
            return "";
        }

        return normalizeClothesMode(entry.defaultClothesMode);
    }

    private String normalizeClothesMode(String mode) {
        if (mode == null) {
            return "";
        }

        String value = mode.trim();

        if ("uniform".equalsIgnoreCase(value)
                || "school_uniform".equalsIgnoreCase(value)
                || "haru_uniform".equalsIgnoreCase(value)
                || "制服".equals(value)) {
            return CLOTHES_UNIFORM;
        }

        if ("dress".equalsIgnoreCase(value)
                || "onepiece".equalsIgnoreCase(value)
                || "one_piece".equalsIgnoreCase(value)
                || "haru_dress".equalsIgnoreCase(value)
                || "连衣裙".equals(value)
                || "ワンピース".equals(value)) {
            return CLOTHES_DRESS;
        }

        return value;
    }

    private String getInitialAvatarIdFromIntent() {
        try {
            String avatarId = LAppDelegate.getInstance()
                    .getActivity()
                    .getIntent()
                    .getStringExtra(EXTRA_AVATAR_ID);

            if (avatarId != null && avatarId.trim().length() > 0 && isSupportedAvatarId(avatarId.trim())) {
                return avatarId.trim();
            }

            String modelPath = LAppDelegate.getInstance()
                    .getActivity()
                    .getIntent()
                    .getStringExtra("modelPath");

            if (modelPath == null || modelPath.trim().length() == 0) {
                modelPath = LAppDelegate.getInstance()
                        .getActivity()
                        .getIntent()
                        .getStringExtra("model_path");
            }

            if (modelPath != null && modelPath.trim().length() > 0) {
                return modelPath.trim();
            }
        } catch (Exception e) {
            LAppPal.printLog("读取 Intent 中的 avatarId 失败，使用默认数字人。");
        }

        return AVATAR_DEFAULT;
    }

    private String getInitialClothesModeFromIntent() {
        try {
            String clothesMode = LAppDelegate.getInstance()
                    .getActivity()
                    .getIntent()
                    .getStringExtra(EXTRA_CLOTHES_MODE);

            if (clothesMode != null && clothesMode.trim().length() > 0) {
                return clothesMode.trim();
            }

            String clothesModeSnake = LAppDelegate.getInstance()
                    .getActivity()
                    .getIntent()
                    .getStringExtra("clothes_mode");

            if (clothesModeSnake != null && clothesModeSnake.trim().length() > 0) {
                return clothesModeSnake.trim();
            }
        } catch (Exception e) {
            LAppPal.printLog("读取 Intent 中的 clothesMode 失败，使用模型默认服装。");
        }

        return "";
    }

    public LAppModel getModel(int number) {
        if (number < models.size()) {
            return models.get(number);
        }
        return null;
    }

    public int getCurrentModel() {
        return currentModel;
    }

    public String getCurrentAvatarId() {
        if (currentModel >= 0 && currentModel < modelEntries.size()) {
            return modelEntries.get(currentModel).avatarId;
        }
        return AVATAR_DEFAULT;
    }

    public String getCurrentAvatarName() {
        if (currentModel >= 0 && currentModel < modelEntries.size()) {
            return modelEntries.get(currentModel).displayName;
        }
        return "灵灵";
    }

    public String getCurrentClothesMode() {
        return currentClothesMode;
    }

    public int getModelNum() {
        if (models == null) {
            return 0;
        }
        return models.size();
    }

    private static class BeganMotion implements IBeganMotionCallback {
        @Override
        public void execute(ACubismMotion motion) {
            LAppPal.printLog("动作开始播放: " + motion);
        }
    }

    private static final BeganMotion beganMotion = new BeganMotion();

    private static class FinishedMotion implements IFinishedMotionCallback {
        @Override
        public void execute(ACubismMotion motion) {
            LAppPal.printLog("动作播放结束: " + motion);
        }
    }

    private static final FinishedMotion finishedMotion = new FinishedMotion();

    private static LAppLive2DManager s_instance;

    private LAppLive2DManager() {
        setUpModel();

        String avatarId = getInitialAvatarIdFromIntent();
        String clothesMode = getInitialClothesModeFromIntent();

        changeSceneByAvatarId(avatarId, clothesMode);
    }

    private final List<LAppModel> models = new ArrayList<>();

    private int currentModel;
    private String currentClothesMode = "";

    private final List<ModelEntry> modelEntries = new ArrayList<>();

    private final CubismMatrix44 viewMatrix = CubismMatrix44.create();
    private final CubismMatrix44 projection = CubismMatrix44.create();
}
