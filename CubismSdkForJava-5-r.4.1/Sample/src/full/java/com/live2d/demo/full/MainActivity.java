/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.demo.full;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.MapView;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.ServiceSettings;
import com.amap.api.services.route.BusRouteResultV2;
import com.amap.api.services.route.DriveRouteResultV2;
import com.amap.api.services.route.RideRouteResultV2;
import com.amap.api.services.route.RouteSearchV2;
import com.amap.api.services.route.WalkPath;
import com.amap.api.services.route.WalkRouteResultV2;
import com.amap.api.services.route.WalkStep;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String TAG = "Live2DGuide";
    private static final String GUIDE_SOURCE = "native-live2d-guide";

    // 改成你当前小后端电脑的真实 IPv4 地址
    private static final String GUIDE_CHAT_URL = "http://10.120.215.131:8080/api/guide/chat";
    private static final String GUIDE_VOICE_CHAT_URL = "http://10.120.215.131:8080/api/guide/voice/chat";
    // 旧路线推荐接口保留常量但不再主动调用；路线问题统一走 Chat 接口。
    private static final String GUIDE_ROUTE_RECOMMEND_URL = "http://10.120.215.131:8080/api/guide/route/recommend";
    private static final String DEFAULT_BEHAVIOR_EVENT_PATH = "/api/app/behavior/event";
    private static final String DEFAULT_SPOT_ENTER_PATH = "/api/visit/spot/enter";
    private static final String DEFAULT_SPOT_LEAVE_PATH = "/api/visit/spot/leave";
    private static final String DEFAULT_VISIT_END_PATH = "/api/visit/end";
    private static final String DEFAULT_APP_VISIT_END_PATH = "/api/app/visit/end";
    private static final String DEMO_LOCATION_SOURCE = "demo-route-node";
    private static final String DEMO_TRIGGER = "route-node-demo";
    private static final String ROUTE_EVENT_SOURCE = "android_live2d";

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 2001;

    // 只作为兜底欢迎语 / audioUrl 为空时使用。正式回答优先播放后端 audioUrl。
    private static final boolean ENABLE_NATIVE_TTS = true;
    private static final long TTS_INIT_TIMEOUT_MS = 3000L;

    private GLSurfaceView glSurfaceView;
    private GLRenderer glRenderer;
    private FrameLayout rootLayout;

    private TextView titleText;
    private TextView targetText;
    private TextView onlineText;
    private Button endVisitButton;
    private TextView guideStateText;
    private TextView lastQuestionText;
    private TextView answerText;
    private ScrollView answerScrollView;

    private LinearLayout answerBoxContainer;
    private final StringBuilder chatTranscriptBuilder = new StringBuilder();
    private boolean chatHistoryStarted = false;

    private EditText questionInput;

    private Button quickBtn1;
    private Button quickBtn2;
    private Button quickBtn3;
    private Button voiceMainButton;
    private Button sendButton;
    private LinearLayout voiceGuidePanel;
    private LinearLayout routeStartRow;
    private TextView routeStartText;
    private Button routeStartSwitchButton;
    private Button simulateLingshanButton;

    // 地图 / 路线状态卡片
    private TextView mapCardTitleText;
    private TextView mapCardStatusText;
    private TextView mapCardDescText;
    private TextView mapCardMetaText;
    private Button mapCardButton;
    private Button nearbyGuideButton;

    // AI 路线推荐卡片
    private LinearLayout routeCardContainer;
    private FrameLayout routeExpandedOverlay;
    private TextView routeCardTitleText;
    private TextView routeCardReasonText;
    private TextView routeCardMetaText;
    private TextView routeCardStartNextText;
    private FrameLayout routeMapHolder;
    private TextView routeMapPlaceholderText;
    private ScrollView routeNodesScrollView;
    private LinearLayout routeNodesContainer;
    private LinearLayout routeDemoController;
    private LinearLayout routeCardActionRow;
    private TextView routeDemoStatusText;
    private Button routeDemoNextButton;
    private Button routeDemoLeaveButton;
    private Button routeDemoEndButton;
    private Button routeExpandButton;
    private Button routeCloseButton;
    private Button routeChangeStartButton;
    private Button routeStartButton;
    private boolean routeCardExpanded = false;
    private boolean routeMapModeActive = false;
    private boolean routeDemoRequesting = false;
    private int routeDemoNodeIndex = -1;
    private RouteInfo currentRoute;
    private RouteNode currentDemoRouteNode;
    private RoutePreviewData currentRoutePreview;
    private boolean routeGuideActive = false;
    private int currentRouteNodeIndex = -1;
    private final List<RouteNode> activeRouteNodes = new ArrayList<>();
    private MapView routeMapView;
    private AMap routeAMap;
    private boolean pendingRouteNavigationRefresh = false;
    private boolean routeMapPrivacyReady = false;
    private int routePreviewRequestSeq = 0;
    private final List<Polyline> routePreviewPolylines = new ArrayList<>();
    private final List<Marker> routePreviewMarkers = new ArrayList<>();
    private Handler mainHandler;
    private FrameLayout routeMapModeOverlay;
    private LinearLayout routeMapTopBar;
    private LinearLayout routeMapSideTools;
    private LinearLayout routeMapBottomSheetView;
    private TextView routeMapModeTitleText;
    private TextView routeMapModeEmptyText;
    private TextView routeBottomTitleText;
    private TextView routeBottomSummaryText;
    private TextView routeBottomMetaText;
    private LinearLayout routeBottomNodesContainer;
    private boolean routeNavigationModeActive = false;
    private FrameLayout routeNavigationOverlay;
    private TextView navigationInstructionText;
    private TextView navigationSubText;
    private TextView navigationStatusText;
    private final Set<String> shownRoutePlanIds = new HashSet<>();

    private TextToSpeech textToSpeech;
    private MediaPlayer mediaPlayer;
    private MediaRecorder mediaRecorder;

    private File currentAudioFile;

    private boolean ttsInitialized = false;
    private boolean ttsInitializing = false;
    private boolean requesting = false;
    private boolean hasPlayedWelcome = false;
    private boolean recording = false;
    private boolean voiceFlowActive = false;

    private long recordStartTime = 0L;

    private String pendingTtsText = "";
    private String pendingTtsReason = "";
    private long pendingTtsDurationMs = 0L;
    private String currentTtsUtteranceId = "";

    private String sessionId = "";
    private String conversationId = "";

    // uni-app 登录会话ID，只用于记录来源，不再当作 AI 对话ID
    private String appAuthSessionId = "";

    // 来自 uni-app 的真实身份参数
    private String userId = "";
    private String appUserId = "";
    private String loginUserId = "";
    private String visitorId = "";

    // uni-app 传入的登录 token，所有需要登录用户的后端请求都复用它。
    private String authToken = "";

    private String from = "";
    private String entry = "";
    private String visitId = "";
    private String parkId = "";
    private String parkName = "";
    private String scenicName = "";
    private String scenicId = "";
    private String groupSize = "";
    private String travelType = "";
    private String visitPreference = "";

    private String contextType = "";
    private String contextName = "";
    private String autoQuestion = "";

    private String areaName = "";
    private String areaId = "";
    private String areaCode = "";
    private String spotName = "";
    private String spotId = "";

    // 路线推荐前使用的起点，不代表游客已经到达景点
    private String routeStartType = "park_entrance";
    private String routeStartCurrentSpotId = "";
    private String routeStartCurrentSpotName = "景区入口";
    private String routeStartLatitude = "";
    private String routeStartLongitude = "";
    private String routeStartSpotsParkKey = "";
    private boolean routeStartSpotsLoading = false;
    private final List<RouteStartSpot> routeStartSpots = new ArrayList<>();
    private FrameLayout routeStartPickerOverlay;
    private LinearLayout routeStartOptionsContainer;
    private RouteStartSpot pendingRouteStartSpot;

    // GPS / 现场导览扩展参数
    private String mode = "";
    private String trigger = "";
    private String distance = "";
    private String latitude = "";
    private String longitude = "";

    // 数字人形象 / 音色配置参数
    private String avatarId = "guide_female_01";
    private String avatarName = "灵灵";
    private String modelPath = "guide_female_01";
    private String digitalHumanConfigJson = "";

    private String clothesMode = "";
    private String voiceId = "zhitian_emo";
    private String voiceName = "知甜";
    private String welcomeText = "";
    private String behaviorBackendBaseUrl = "";
    private String behaviorEventPath = DEFAULT_BEHAVIOR_EVENT_PATH;
    private String spotEnterPath = DEFAULT_SPOT_ENTER_PATH;
    private String spotLeavePath = DEFAULT_SPOT_LEAVE_PATH;
    private String visitEndPath = DEFAULT_VISIT_END_PATH;
    private boolean allowEndVisit = false;
    private boolean isOnsiteGuide = false;
    private boolean startVisitGuide = false;
    private boolean endingVisit = false;
    private boolean guideEnded = false;
    private FrameLayout endVisitDialogOverlay;
    private final GuideContext guideContext = new GuideContext();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(Looper.getMainLooper());

        handleGuideIntent(getIntent());
        resetSession();

        glSurfaceView = new GLSurfaceView(this);

        // 关键：防止录音权限弹窗 / Activity 短暂 pause 后 Live2D 黑屏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            glSurfaceView.setPreserveEGLContextOnPause(true);
        }

        glSurfaceView.setEGLContextClientVersion(2);

        glRenderer = new GLRenderer();
        glSurfaceView.setRenderer(glRenderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        glSurfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, final MotionEvent event) {
                final float pointX = event.getX();
                final float pointY = event.getY();

                glSurfaceView.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                LAppDelegate.getInstance().onTouchBegan(pointX, pointY);
                                break;
                            case MotionEvent.ACTION_UP:
                                LAppDelegate.getInstance().onTouchEnd(pointX, pointY);
                                break;
                            case MotionEvent.ACTION_MOVE:
                                LAppDelegate.getInstance().onTouchMoved(pointX, pointY);
                                break;
                            default:
                                break;
                        }
                    }
                });

                return true;
            }
        });

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.rgb(238, 247, 255));

        rootLayout.addView(
                glSurfaceView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        addTopStatusBar(rootLayout);
        addVoiceGuidePanel(rootLayout);
        addRouteRecommendCard(rootLayout, savedInstanceState);

        setContentView(rootLayout);

        bindEvents();
        initTextToSpeechIfNeeded("onCreate");
        showWelcomeMessage();

        if (shouldRunAutoQuestionOnLaunch()) {
            questionInput.setText(autoQuestion);
            askGuide(autoQuestion);
        }

        hideSystemBars();
    }

    private void addTopStatusBar(FrameLayout rootLayout) {
        LinearLayout topPanel = new LinearLayout(this);
        topPanel.setOrientation(LinearLayout.VERTICAL);
        topPanel.setPadding(dp(14), dp(8), dp(14), dp(8));
        topPanel.setBackground(createRoundBg(Color.argb(188, 255, 255, 255), dp(16)));

        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        firstRow.setGravity(Gravity.CENTER_VERTICAL);

        titleText = new TextView(this);
        titleText.setText(getGuideTitleText());
        titleText.setTextColor(Color.rgb(18, 48, 78));
        titleText.setTextSize(16);
        titleText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        onlineText = new TextView(this);
        onlineText.setText(getOnlineStatusText());
        onlineText.setTextColor(isOnsiteMode() ? Color.rgb(24, 179, 104) : Color.rgb(47, 128, 237));
        onlineText.setTextSize(12);
        onlineText.setGravity(Gravity.RIGHT);

        firstRow.addView(titleText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        firstRow.addView(onlineText, new LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.WRAP_CONTENT));

        endVisitButton = new Button(this);
        endVisitButton.setText("结束导览");
        endVisitButton.setTextSize(12);
        endVisitButton.setTextColor(Color.rgb(180, 72, 28));
        endVisitButton.setAllCaps(false);
        endVisitButton.setPadding(0, 0, 0, 0);
        endVisitButton.setBackground(createStrokeRoundBg(
                Color.rgb(255, 247, 237),
                Color.rgb(251, 146, 60),
                dp(14)
        ));
        endVisitButton.setVisibility(shouldShowEndVisitButton() ? View.VISIBLE : View.GONE);

        LinearLayout.LayoutParams endParams = new LinearLayout.LayoutParams(dp(76), dp(30));
        endParams.leftMargin = dp(6);
        firstRow.addView(endVisitButton, endParams);

        targetText = new TextView(this);
        targetText.setText(getTopTargetText());
        targetText.setTextColor(Color.rgb(95, 116, 138));
        targetText.setTextSize(11);
        targetText.setPadding(0, dp(3), 0, 0);

        topPanel.addView(firstRow);
        topPanel.addView(targetText);

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        topParams.gravity = Gravity.TOP;
        topParams.leftMargin = dp(16);
        topParams.rightMargin = dp(16);
        topParams.topMargin = dp(12);

        rootLayout.addView(topPanel, topParams);
    }

    private void addVoiceGuidePanel(FrameLayout rootLayout) {
        LinearLayout panel = new LinearLayout(this);
        voiceGuidePanel = panel;
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(12), dp(16), dp(12));
        panel.setBackground(createRoundBg(Color.argb(232, 255, 255, 255), dp(24)));

        LinearLayout panelTitleRow = new LinearLayout(this);
        panelTitleRow.setOrientation(LinearLayout.HORIZONTAL);
        panelTitleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView guideTitle = new TextView(this);
        guideTitle.setText("我可以这样帮你");
        guideTitle.setTextColor(Color.rgb(24, 48, 72));
        guideTitle.setTextSize(15);
        guideTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        guideStateText = new TextView(this);
        guideStateText.setText("等待提问");
        guideStateText.setTextColor(Color.rgb(105, 128, 150));
        guideStateText.setTextSize(12);
        guideStateText.setGravity(Gravity.RIGHT);

        panelTitleRow.addView(guideTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        panelTitleRow.addView(guideStateText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        panel.addView(panelTitleRow);

        // 常驻地图卡片会遮挡数字人；路线信息只在 AI 路线推荐成功后弹出展示。
        // addGuideMapCard(panel);

        routeStartRow = new LinearLayout(this);
        routeStartRow.setOrientation(LinearLayout.HORIZONTAL);
        routeStartRow.setGravity(Gravity.CENTER_VERTICAL);
        routeStartRow.setPadding(0, dp(7), 0, 0);

        routeStartText = new TextView(this);
        routeStartText.setTextColor(Color.rgb(65, 83, 105));
        routeStartText.setTextSize(12);
        routeStartText.setSingleLine(true);

        routeStartSwitchButton = createMiniActionButton("切换");
        LinearLayout.LayoutParams routeStartTextParams = new LinearLayout.LayoutParams(0, dp(30), 1);
        routeStartRow.addView(routeStartText, routeStartTextParams);

        LinearLayout.LayoutParams routeStartButtonParams = new LinearLayout.LayoutParams(dp(58), dp(30));
        routeStartButtonParams.leftMargin = dp(8);
        routeStartRow.addView(routeStartSwitchButton, routeStartButtonParams);
        panel.addView(routeStartRow);
        updateRouteStartRow();

        simulateLingshanButton = createMiniActionButton("模拟到达当前景点");
        LinearLayout.LayoutParams simulateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(32)
        );
        simulateParams.topMargin = dp(6);
        panel.addView(simulateLingshanButton, simulateParams);
        updateSimulatedSpotButton();

        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        quickRow.setPadding(0, dp(8), 0, dp(6));

        quickBtn1 = createQuickButton("介绍景区");
        quickBtn2 = createQuickButton("推荐路线");
        quickBtn3 = createQuickButton("特色亮点");

        LinearLayout.LayoutParams q1Params = new LinearLayout.LayoutParams(0, dp(34), 1);
        q1Params.rightMargin = dp(6);

        LinearLayout.LayoutParams q2Params = new LinearLayout.LayoutParams(0, dp(34), 1);
        q2Params.leftMargin = dp(3);
        q2Params.rightMargin = dp(3);

        LinearLayout.LayoutParams q3Params = new LinearLayout.LayoutParams(0, dp(34), 1);
        q3Params.leftMargin = dp(6);

        quickRow.addView(quickBtn1, q1Params);
        quickRow.addView(quickBtn2, q2Params);
        quickRow.addView(quickBtn3, q3Params);
        panel.addView(quickRow);

        lastQuestionText = new TextView(this);
        lastQuestionText.setText("长按说话，让数字人为你讲解");
        lastQuestionText.setTextColor(Color.rgb(98, 116, 138));
        lastQuestionText.setTextSize(12);
        lastQuestionText.setPadding(dp(2), 0, dp(2), dp(5));
        panel.addView(lastQuestionText);

        LinearLayout answerBox = new LinearLayout(this);
        answerBoxContainer = answerBox;
        answerBox.setOrientation(LinearLayout.VERTICAL);
        answerBox.setPadding(dp(12), dp(8), dp(12), dp(8));
        answerBox.setBackground(createStrokeRoundBg(
                Color.argb(210, 248, 251, 255),
                Color.argb(65, 47, 128, 237),
                dp(18)
        ));

        TextView answerLabel = new TextView(this);
        answerLabel.setText("对话记录");
        answerLabel.setTextColor(Color.rgb(47, 128, 237));
        answerLabel.setTextSize(12);
        answerLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        answerScrollView = new ScrollView(this);
        answerScrollView.setFillViewport(false);

        answerText = new TextView(this);
        answerText.setTextColor(Color.rgb(42, 60, 80));
        answerText.setTextSize(14);
        answerText.setLineSpacing(dp(4), 1.0f);
        answerText.setPadding(0, dp(5), 0, 0);

        answerScrollView.addView(
                answerText,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT
                )
        );

        answerBox.addView(answerLabel);
        answerBox.addView(answerScrollView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));
        panel.addView(answerBox);

        voiceMainButton = new Button(this);
        voiceMainButton.setText("🎙 长按说话");
        voiceMainButton.setTextSize(16);
        voiceMainButton.setTextColor(Color.WHITE);
        voiceMainButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        voiceMainButton.setAllCaps(false);
        voiceMainButton.setBackground(createRoundBg(Color.rgb(47, 128, 237), dp(25)));

        LinearLayout.LayoutParams voiceMainParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        voiceMainParams.topMargin = dp(9);
        panel.addView(voiceMainButton, voiceMainParams);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(0, dp(8), 0, 0);

        questionInput = new EditText(this);
        questionInput.setHint("也可以输入文字问题...");
        questionInput.setTextSize(14);
        questionInput.setSingleLine(true);
        questionInput.setPadding(dp(12), 0, dp(12), 0);
        questionInput.setBackground(createRoundBg(Color.rgb(245, 249, 255), dp(18)));

        sendButton = new Button(this);
        sendButton.setText("发送");
        sendButton.setTextSize(14);
        sendButton.setTextColor(Color.WHITE);
        sendButton.setAllCaps(false);
        sendButton.setBackground(createRoundBg(Color.rgb(35, 135, 230), dp(18)));

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        inputParams.rightMargin = dp(8);
        inputRow.addView(questionInput, inputParams);

        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(72), dp(40));
        inputRow.addView(sendButton, sendParams);
        panel.addView(inputRow);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        panelParams.gravity = Gravity.BOTTOM;
        panelParams.leftMargin = dp(14);
        panelParams.rightMargin = dp(14);
        panelParams.bottomMargin = dp(18);

        rootLayout.addView(panel, panelParams);
    }

    private void addGuideMapCard(LinearLayout panel) {
        LinearLayout mapCard = new LinearLayout(this);
        mapCard.setOrientation(LinearLayout.VERTICAL);
        mapCard.setPadding(dp(12), dp(8), dp(12), dp(8));
        mapCard.setBackground(createStrokeRoundBg(
                Color.argb(218, 240, 249, 255),
                Color.argb(80, 47, 128, 237),
                dp(18)
        ));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        mapCardTitleText = new TextView(this);
        mapCardTitleText.setText(getMapCardTitleText());
        mapCardTitleText.setTextColor(Color.rgb(24, 48, 72));
        mapCardTitleText.setTextSize(13);
        mapCardTitleText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        mapCardStatusText = new TextView(this);
        mapCardStatusText.setText(getMapCardStatusText());
        mapCardStatusText.setTextColor(isOnsiteMode() ? Color.rgb(22, 163, 74) : Color.rgb(47, 128, 237));
        mapCardStatusText.setTextSize(11);
        mapCardStatusText.setGravity(Gravity.RIGHT);

        titleRow.addView(mapCardTitleText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        titleRow.addView(mapCardStatusText, new LinearLayout.LayoutParams(dp(92), LinearLayout.LayoutParams.WRAP_CONTENT));
        mapCard.addView(titleRow);

        mapCardDescText = new TextView(this);
        mapCardDescText.setText(getMapCardDescText());
        mapCardDescText.setTextColor(Color.rgb(65, 83, 105));
        mapCardDescText.setTextSize(12);
        mapCardDescText.setPadding(0, dp(4), 0, 0);
        mapCard.addView(mapCardDescText);

        mapCardMetaText = new TextView(this);
        mapCardMetaText.setText(getMapCardMetaText());
        mapCardMetaText.setTextColor(Color.rgb(112, 128, 145));
        mapCardMetaText.setTextSize(11);
        mapCardMetaText.setPadding(0, dp(3), 0, dp(6));
        mapCard.addView(mapCardMetaText);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);

        mapCardButton = createMiniActionButton("查看地图");
        nearbyGuideButton = createMiniActionButton("讲解附近");

        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, dp(32), 1);
        leftParams.rightMargin = dp(6);
        actionRow.addView(mapCardButton, leftParams);

        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, dp(32), 1);
        rightParams.leftMargin = dp(6);
        actionRow.addView(nearbyGuideButton, rightParams);

        mapCard.addView(actionRow);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(8);
        cardParams.bottomMargin = dp(4);

        panel.addView(mapCard, cardParams);
    }

    private void addRouteRecommendCard(FrameLayout rootLayout, Bundle savedInstanceState) {
        routeCardContainer = new LinearLayout(this);
        routeCardContainer.setOrientation(LinearLayout.VERTICAL);
        routeCardContainer.setPadding(dp(12), dp(6), dp(12), dp(6));
        routeCardContainer.setVisibility(View.GONE);
        routeCardContainer.setBackground(createStrokeRoundBg(
                Color.argb(245, 255, 255, 255),
                Color.argb(100, 47, 128, 237),
                dp(20)
        ));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        routeCardTitleText = new TextView(this);
        routeCardTitleText.setTextColor(Color.rgb(18, 48, 78));
        routeCardTitleText.setTextSize(14);
        routeCardTitleText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        routeCardTitleText.setSingleLine(true);
        titleRow.addView(routeCardTitleText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        routeExpandButton = createMiniActionButton("查看路线");
        routeCloseButton = createMiniActionButton("关闭");

        LinearLayout.LayoutParams expandParams = new LinearLayout.LayoutParams(dp(72), dp(28));
        expandParams.rightMargin = dp(6);
        titleRow.addView(routeExpandButton, expandParams);
        titleRow.addView(routeCloseButton, new LinearLayout.LayoutParams(dp(52), dp(28)));
        routeCardContainer.addView(titleRow);

        routeCardReasonText = new TextView(this);
        routeCardReasonText.setTextColor(Color.rgb(65, 83, 105));
        routeCardReasonText.setTextSize(11);
        routeCardReasonText.setSingleLine(true);
        routeCardReasonText.setPadding(0, dp(3), 0, 0);
        routeCardContainer.addView(routeCardReasonText);

        routeCardMetaText = new TextView(this);
        routeCardMetaText.setTextColor(Color.rgb(95, 112, 130));
        routeCardMetaText.setTextSize(11);
        routeCardMetaText.setSingleLine(true);
        routeCardMetaText.setPadding(0, dp(2), 0, 0);
        routeCardContainer.addView(routeCardMetaText);

        routeCardStartNextText = new TextView(this);
        routeCardStartNextText.setTextColor(Color.rgb(82, 98, 118));
        routeCardStartNextText.setTextSize(11);
        routeCardStartNextText.setSingleLine(true);
        routeCardStartNextText.setPadding(0, dp(2), 0, 0);
        routeCardContainer.addView(routeCardStartNextText);

        routeMapHolder = new FrameLayout(this);
        routeMapHolder.setBackground(createStrokeRoundBg(
                Color.rgb(236, 246, 255),
                Color.argb(70, 47, 128, 237),
                dp(14)
        ));

        initRouteMapView(savedInstanceState);
        if (routeMapView != null) {
            routeMapHolder.addView(routeMapView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
            routeMapView.setVisibility(View.GONE);
        }

        routeMapPlaceholderText = new TextView(this);
        routeMapPlaceholderText.setText("正在生成高德步行路线...");
        routeMapPlaceholderText.setTextColor(Color.rgb(47, 128, 237));
        routeMapPlaceholderText.setTextSize(12);
        routeMapPlaceholderText.setGravity(Gravity.CENTER);
        routeMapPlaceholderText.setPadding(dp(8), 0, dp(8), 0);
        routeMapHolder.addView(routeMapPlaceholderText, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(88));
        mapParams.topMargin = dp(8);
        mapParams.bottomMargin = dp(8);
        routeCardContainer.addView(routeMapHolder, mapParams);

        routeNodesScrollView = new ScrollView(this);
        routeNodesContainer = new LinearLayout(this);
        routeNodesContainer.setOrientation(LinearLayout.VERTICAL);
        routeNodesScrollView.addView(
                routeNodesContainer,
                new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT)
        );
        routeCardContainer.addView(routeNodesScrollView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(0)));

        routeDemoController = new LinearLayout(this);
        routeDemoController.setOrientation(LinearLayout.VERTICAL);
        routeDemoController.setPadding(dp(10), dp(7), dp(10), dp(8));
        routeDemoController.setVisibility(View.GONE);
        routeDemoController.setBackground(createStrokeRoundBg(
                Color.rgb(248, 252, 248),
                Color.argb(70, 24, 179, 104),
                dp(12)
        ));

        routeDemoStatusText = new TextView(this);
        routeDemoStatusText.setTextColor(Color.rgb(46, 86, 64));
        routeDemoStatusText.setTextSize(12);
        routeDemoStatusText.setLineSpacing(dp(2), 1.0f);
        routeDemoController.addView(routeDemoStatusText);

        LinearLayout demoActionRow = new LinearLayout(this);
        demoActionRow.setOrientation(LinearLayout.HORIZONTAL);
        demoActionRow.setGravity(Gravity.CENTER_VERTICAL);
        demoActionRow.setPadding(0, dp(6), 0, 0);

        routeDemoNextButton = createMiniActionButton("模拟到达下一站");
        routeDemoLeaveButton = createMiniActionButton("讲解当前景点");
        routeDemoEndButton = createMiniActionButton("结束本条路线");

        LinearLayout.LayoutParams demoNextParams = new LinearLayout.LayoutParams(0, dp(32), 1.35f);
        demoNextParams.rightMargin = dp(5);
        demoActionRow.addView(routeDemoNextButton, demoNextParams);

        LinearLayout.LayoutParams demoLeaveParams = new LinearLayout.LayoutParams(0, dp(32), 1.15f);
        demoLeaveParams.leftMargin = dp(5);
        demoLeaveParams.rightMargin = dp(5);
        demoActionRow.addView(routeDemoLeaveButton, demoLeaveParams);

        LinearLayout.LayoutParams demoEndParams = new LinearLayout.LayoutParams(0, dp(32), 1.0f);
        demoEndParams.leftMargin = dp(5);
        demoActionRow.addView(routeDemoEndButton, demoEndParams);

        routeDemoController.addView(demoActionRow);

        LinearLayout.LayoutParams demoParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        demoParams.topMargin = dp(8);
        routeCardContainer.addView(routeDemoController, demoParams);

        routeCardActionRow = new LinearLayout(this);
        routeCardActionRow.setOrientation(LinearLayout.HORIZONTAL);
        routeCardActionRow.setGravity(Gravity.CENTER_VERTICAL);

        routeChangeStartButton = createMiniActionButton("更换起点");
        LinearLayout.LayoutParams changeStartParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        changeStartParams.rightMargin = dp(8);
        routeCardActionRow.addView(routeChangeStartButton, changeStartParams);

        routeStartButton = new Button(this);
        routeStartButton.setText("开始导航");
        routeStartButton.setTextSize(13);
        routeStartButton.setTextColor(Color.WHITE);
        routeStartButton.setAllCaps(false);
        routeStartButton.setBackground(createRoundBg(Color.rgb(47, 128, 237), dp(18)));
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(0, dp(44), 1.25f);
        routeCardActionRow.addView(routeStartButton, startParams);

        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        actionParams.topMargin = dp(4);
        routeCardContainer.addView(routeCardActionRow, actionParams);

        routeExpandButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentRoute == null) return;
                enterRouteMapMode(currentRoute);
            }
        });

        routeCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentRoute != null) {
                    writeRouteCardEvent("map_card_close", currentRoute, null);
                }
                hideRouteCard();
            }
        });

        routeChangeStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleRouteStartSwitchClick();
            }
        });

        routeStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleRouteStartGuideClick();
            }
        });

        routeDemoNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                simulateArriveNextNode();
            }
        });

        routeDemoLeaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                askGuideForCurrentNode(getCurrentActiveRouteNode());
            }
        });

        routeDemoEndButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishSimulatedRouteGuide();
            }
        });

        if (voiceGuidePanel != null) {
            LinearLayout.LayoutParams routeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            routeParams.topMargin = dp(6);
            routeParams.bottomMargin = dp(6);

            int insertIndex = Math.min(3, voiceGuidePanel.getChildCount());
            voiceGuidePanel.addView(routeCardContainer, insertIndex, routeParams);
        } else {
            FrameLayout.LayoutParams routeParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            );
            routeParams.gravity = Gravity.BOTTOM;
            routeParams.leftMargin = dp(14);
            routeParams.rightMargin = dp(14);
            routeParams.bottomMargin = dp(146);
            rootLayout.addView(routeCardContainer, routeParams);
        }
    }

    private void initRouteMapView(Bundle savedInstanceState) {
        try {
            MapsInitializer.updatePrivacyShow(this, true, true);
            MapsInitializer.updatePrivacyAgree(this, true);
            routeMapPrivacyReady = true;

            routeMapView = new MapView(this);
            routeMapView.onCreate(savedInstanceState);
            routeAMap = routeMapView.getMap();
            if (routeAMap != null) {
                UiSettings settings = routeAMap.getUiSettings();
                if (settings != null) {
                    settings.setZoomControlsEnabled(false);
                    settings.setScaleControlsEnabled(false);
                    settings.setCompassEnabled(false);
                    settings.setMyLocationButtonEnabled(false);
                }
                routeAMap.setOnMarkerClickListener(new AMap.OnMarkerClickListener() {
                    @Override
                    public boolean onMarkerClick(Marker marker) {
                        Object markerObject = marker == null ? null : marker.getObject();
                        if (markerObject instanceof RouteNode) {
                            onRouteNodeClick((RouteNode) markerObject);
                        }
                        return false;
                    }
                });
                routeAMap.setOnMapLoadedListener(new AMap.OnMapLoadedListener() {
                    @Override
                    public void onMapLoaded() {
                        handleRouteMapReadyRedraw();
                    }
                });
                handleRouteMapReadyRedraw();
            }
        } catch (Throwable e) {
            routeMapView = null;
            routeAMap = null;
            routeMapPrivacyReady = false;
            Log.e(TAG, "初始化高德路线预览 MapView 失败", e);
        }
    }

    private void bindEvents() {
        View.OnClickListener quickClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Button button = (Button) v;
                String question = button.getText().toString();
                questionInput.setText(question);
                askGuide(question);
            }
        };

        quickBtn1.setOnClickListener(quickClick);
        quickBtn2.setOnClickListener(quickClick);
        quickBtn3.setOnClickListener(quickClick);

        voiceMainButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startVoiceRecord();
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        stopVoiceRecordAndUpload();
                        return true;
                    default:
                        return true;
                }
            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String question = questionInput.getText().toString().trim();
                if (question.length() == 0) {
                    showToast("请先输入你想咨询的问题");
                    return;
                }
                askGuide(question);
            }
        });

        if (mapCardButton != null) {
            mapCardButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    handleMapCardClick();
                }
            });
        }

        if (nearbyGuideButton != null) {
            nearbyGuideButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String question = buildNearbyGuideQuestion();
                    questionInput.setText(question);
                    askGuide(question);
                }
            });
        }

        if (routeStartSwitchButton != null) {
            routeStartSwitchButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    handleRouteStartSwitchClick();
                }
            });
        }

        if (simulateLingshanButton != null) {
            simulateLingshanButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    handleSimulateArriveLingshanBuddha();
                }
            });
        }

        if (endVisitButton != null) {
            endVisitButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showEndVisitConfirmDialog();
                }
            });
        }
    }

    private void askGuide(final String rawQuestion) {
        askGuideInternal(rawQuestion, true);
    }

    private void askGuideInternal(final String rawQuestion, final boolean appendUserToChat) {
        askGuideInternal(rawQuestion, appendUserToChat, false);
    }

    private void askGuideInternal(final String rawQuestion, final boolean appendUserToChat, final boolean suppressRoute) {
        final String question = rawQuestion == null ? "" : rawQuestion.trim();
        final boolean allowRouteResponse = shouldAllowRouteResponse(question, suppressRoute);

        if (question.length() == 0) {
            showToast("请先输入你想咨询的问题");
            return;
        }

        if (!ensureGuideAuthReady()) {
            return;
        }

        if (guideEnded) {
            showToast("本次现场导览已结束");
            return;
        }

        if (requesting) {
            showToast("数字人正在处理，请稍候");
            return;
        }

        requesting = true;
        hideKeyboard();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                guideStateText.setText("正在思考");
                voiceMainButton.setText("AI 正在思考...");
                updateSimulatedSpotButton();
                questionInput.setText("");
                lastQuestionText.setText("游客提问：" + question);
                if (appendUserToChat) {
                    appendUserMessage(question);
                }
                applyDigitalHumanState("thinking", "neutral");
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;

                try {
                    URL url = new URL(getGuideTextRequestUrl(question));
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(30000);
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    connection.setRequestProperty("Accept", "application/json");
                    applyAuthorizationHeader(connection);

                    JSONObject requestJson = buildRequestJson(question, suppressRoute);

                    OutputStream outputStream = connection.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
                    writer.write(requestJson.toString());
                    writer.flush();
                    writer.close();
                    outputStream.close();

                    int responseCode = connection.getResponseCode();
                    InputStream inputStream = responseCode >= 200 && responseCode < 300 ? connection.getInputStream() : connection.getErrorStream();
                    String responseText = readStream(inputStream);

                    if (responseCode >= 200 && responseCode < 300) {
                        final GuideResponse guideResponse = parseGuideResponse(responseText);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (guideEnded) {
                                    return;
                                }
                                requesting = false;
                                voiceFlowActive = false;
                                updateSimulatedSpotButton();
                                guideStateText.setText("讲解完成");
                                voiceMainButton.setText("🎙 长按说话");

                                if (guideResponse.questionText != null && guideResponse.questionText.trim().length() > 0) {
                                    lastQuestionText.setText("游客提问：" + guideResponse.questionText);
                                }

                                showGuideAnswer(guideResponse.answer);
                                updateQuickButtons(guideResponse.suggestions);
                                handleGuideRouteResponse(guideResponse.route, allowRouteResponse, "text");

                                if (guideResponse.conversationId != null && guideResponse.conversationId.trim().length() > 0) {
                                    conversationId = guideResponse.conversationId.trim();
                                }

                                playAnswerVoice(guideResponse);
                                forceRenderLive2D();
                            }
                        });
                    } else {
                        final String errorText = formatBackendErrorMessage(responseText, "请求失败，请稍后重试");
                        Log.e(TAG, "文本问答 HTTP 失败 code=" + responseCode + ", response=" + responseText);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (guideEnded) {
                                    return;
                                }
                                requesting = false;
                                voiceFlowActive = false;
                                updateSimulatedSpotButton();
                                guideStateText.setText("请求失败");
                                voiceMainButton.setText("🎙 长按说话");
                                showGuideAnswer(errorText);
                                returnDigitalHumanToIdle();
                            }
                        });
                    }

                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (guideEnded) {
                                return;
                            }
                            requesting = false;
                            voiceFlowActive = false;
                            updateSimulatedSpotButton();
                            guideStateText.setText("连接异常");
                            voiceMainButton.setText("🎙 长按说话");
                            showGuideAnswer("暂时无法连接后端服务。\n\n请检查：\n1. 小后端是否正在运行\n2. 手机和电脑是否在同一个局域网\n3. GUIDE_CHAT_URL 是否是当前电脑 IPv4 地址\n4. AndroidManifest 是否允许 HTTP 网络请求");
                            returnDigitalHumanToIdle();
                        }
                    });
                    Log.e(TAG, "文本问答失败", e);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        }).start();
    }

    private String getGuideTextRequestUrl(String question) {
        if (isRouteIntentQuestion(question)) {
            Log.d(TAG, "文本问答命中路线意图，统一使用 Chat 接口并在请求体追加 route=true: " + GUIDE_CHAT_URL);
        }
        return GUIDE_CHAT_URL;
    }

    private boolean shouldAllowRouteResponse(String question, boolean suppressRoute) {
        return !suppressRoute && isRouteIntentQuestion(question);
    }

    private void handleGuideRouteResponse(RouteInfo route, boolean allowRouteResponse, String source) {
        if (route == null) {
            return;
        }
        if (!allowRouteResponse) {
            Log.d(TAG, "忽略非路线意图返回的 route，不刷新当前路线卡片 source=" + source
                    + ", routeName=" + safeString(route.routeName)
                    + ", nodes=" + (route.nodes == null ? 0 : route.nodes.size()));
            return;
        }
        showRouteCardIfNeeded(route);
    }

    private boolean isRouteIntentQuestion(String question) {
        String text = question == null ? "" : question.trim();
        if (!isOnsiteMode() && ("route".equals(contextType) || "route_planning".equals(mode))) {
            return true;
        }
        return text.contains("推荐路线")
                || text.contains("帮我规划路线")
                || text.contains("规划路线")
                || text.contains("路线规划")
                || text.contains("游览路线")
                || text.contains("游览顺序")
                || text.contains("怎么走")
                || text.contains("导航")
                || text.contains("怎么逛")
                || text.contains("如何逛")
                || text.contains("推荐游览")
                || text.contains("接下来去哪")
                || text.contains("下一站去哪");
    }

    private JSONObject buildRequestJson(String question) throws Exception {
        return buildRequestJson(question, false);
    }

    private JSONObject buildRequestJson(String question, boolean suppressRoute) throws Exception {
        JSONObject requestJson = new JSONObject();

        String realUserId = getEffectiveNativeUserId();
        String realSessionId = firstNotEmpty(sessionId, conversationId);
        String realConversationId = firstNotEmpty(conversationId, sessionId);
        boolean routeIntent = shouldAllowRouteResponse(question, suppressRoute);

        requestJson.put("sessionId", safeString(realSessionId));
        requestJson.put("session_id", safeString(realSessionId));

        // APP 登录会话ID单独传，避免污染 AI 对话ID
        requestJson.put("authSessionId", safeString(appAuthSessionId));
        requestJson.put("auth_session_id", safeString(appAuthSessionId));
        requestJson.put("app_session_id", safeString(appAuthSessionId));

        requestJson.put("userId", safeString(realUserId));
        requestJson.put("user_id", safeString(realUserId));

        requestJson.put("loginUserId", safeString(loginUserId));
        requestJson.put("login_user_id", safeString(loginUserId));

        requestJson.put("visitorId", safeString(visitorId));
        requestJson.put("visitor_id", safeString(visitorId));

        requestJson.put("conversationId", safeString(realConversationId));
        requestJson.put("conversation_id", safeString(realConversationId));

        requestJson.put("question", question);
        requestJson.put("scenicName", getTargetName());
        requestJson.put("scenic_name", getTargetName());
        requestJson.put("needVoice", true);
        requestJson.put("need_voice", true);
        requestJson.put("enableTts", true);
        requestJson.put("enable_tts", true);
        requestJson.put("voice", safeString(getEffectiveVoiceId()));
        requestJson.put("voiceId", safeString(getEffectiveVoiceId()));
        requestJson.put("voice_id", safeString(getEffectiveVoiceId()));

        requestJson.put("avatarId", safeString(avatarId));
        requestJson.put("avatar_id", safeString(avatarId));
        requestJson.put("avatarName", safeString(avatarName));
        requestJson.put("avatar_name", safeString(avatarName));
        requestJson.put("modelPath", safeString(modelPath));
        requestJson.put("model_path", safeString(modelPath));
        requestJson.put("digitalHumanConfig", safeString(digitalHumanConfigJson));
        requestJson.put("digital_human_config", safeString(digitalHumanConfigJson));
        requestJson.put("clothesMode", safeString(clothesMode));
        requestJson.put("clothes_mode", safeString(clothesMode));
        requestJson.put("inputType", "text");
        requestJson.put("input_type", "text");
        requestJson.put("enableContext", true);
        requestJson.put("enable_context", true);
        requestJson.put("scenicId", safeString(scenicId));
        requestJson.put("contextType", safeString(contextType));
        requestJson.put("contextName", safeString(contextName));
        requestJson.put("entry", safeString(entry));
        appendGuideContextParams(requestJson, realUserId, "text");

        requestJson.put("areaCode", safeString(areaCode));
        requestJson.put("area_code", safeString(areaCode));
        putLongOrString(requestJson, "areaId", areaId);
        putLongOrString(requestJson, "area_id", areaId);
        requestJson.put("areaName", safeString(areaName));
        requestJson.put("area_name", safeString(areaName));

        String sceneCode = firstNotEmpty(spotId, scenicId);
        String sceneName = firstNotEmpty(spotName, scenicName);

        requestJson.put("sceneCode", safeString(sceneCode));
        requestJson.put("scene_code", safeString(sceneCode));
        requestJson.put("sceneName", safeString(sceneName));
        requestJson.put("scene_name", safeString(sceneName));

        requestJson.put("currentSpotId", safeString(spotId));
        requestJson.put("current_spot_id", safeString(spotId));
        requestJson.put("currentSpotName", safeString(spotName));
        requestJson.put("current_spot_name", safeString(spotName));
        requestJson.put("currentLatitude", safeString(latitude));
        requestJson.put("current_latitude", safeString(latitude));
        requestJson.put("currentLongitude", safeString(longitude));
        requestJson.put("current_longitude", safeString(longitude));

        requestJson.put("mode", safeString(mode));
        requestJson.put("trigger", safeString(trigger));
        requestJson.put("distance", safeString(distance));
        requestJson.put("sourcePage", GUIDE_SOURCE);
        requestJson.put("source_page", GUIDE_SOURCE);
        requestJson.put("routeIntent", routeIntent);
        requestJson.put("route_intent", routeIntent);
        if (!routeIntent) {
            requestJson.put("route", false);
            requestJson.put("suppressRoute", true);
            requestJson.put("suppress_route", true);
            requestJson.put("requestType", "spot_explain");
            requestJson.put("request_type", "spot_explain");
        }
        appendRouteStartParams(requestJson, routeIntent, !routeIntent);

        // 路线推荐时只传 routeStart*；不把手机 GPS 当作景区内路线起点。
        if (!routeIntent) {
            requestJson.put("latitude", safeString(latitude));
            requestJson.put("longitude", safeString(longitude));
        }

        Log.d(TAG, "文本问答真实身份参数 realUserId=" + realUserId
                + ", appUserId=" + appUserId
                + ", loginUserId=" + loginUserId
                + ", visitorId=" + visitorId
                + ", appAuthSessionId=" + appAuthSessionId
                + ", sessionId=" + realSessionId
                + ", conversationId=" + realConversationId
                + ", visitId=" + visitId
                + ", groupSize=" + groupSize
                + ", travelType=" + travelType
                + ", visitPreference=" + visitPreference);
        logAiQuestion(question);

        return requestJson;
    }

    private void appendRouteStartParams(JSONObject requestJson, boolean routeIntent) throws Exception {
        appendRouteStartParams(requestJson, routeIntent, !routeIntent);
    }

    private void appendRouteStartParams(JSONObject requestJson, boolean routeIntent, boolean suppressRoute) throws Exception {
        String startType = firstNotEmpty(routeStartType, "park_entrance");
        String startSpotId = "current_spot".equals(startType) ? routeStartCurrentSpotId : "";
        String startSpotName = "current_spot".equals(startType)
                ? firstNotEmpty(routeStartCurrentSpotName, "当前景点")
                : "景区入口";

        if (routeIntent) {
            requestJson.put("route", true);
            requestJson.put("enablePersonalization", true);
            requestJson.put("enable_personalization", true);
            requestJson.put("currentSpotId", safeString(startSpotId));
            requestJson.put("current_spot_id", safeString(startSpotId));
            requestJson.put("currentSpotName", safeString(startSpotName));
            requestJson.put("current_spot_name", safeString(startSpotName));
        }
        requestJson.put("routeStartType", startType);
        requestJson.put("route_start_type", startType);
        requestJson.put("startSpotId", safeString(startSpotId));
        requestJson.put("start_spot_id", safeString(startSpotId));
        requestJson.put("startSpotName", safeString(startSpotName));
        requestJson.put("start_spot_name", safeString(startSpotName));
        requestJson.put("routeStartLatitude", safeString(routeStartLatitude));
        requestJson.put("route_start_latitude", safeString(routeStartLatitude));
        requestJson.put("routeStartLongitude", safeString(routeStartLongitude));
        requestJson.put("route_start_longitude", safeString(routeStartLongitude));
        requestJson.put("startLatitude", safeString(routeStartLatitude));
        requestJson.put("start_latitude", safeString(routeStartLatitude));
        requestJson.put("startLongitude", safeString(routeStartLongitude));
        requestJson.put("start_longitude", safeString(routeStartLongitude));
        if (routeIntent) {
            requestJson.put("currentSpotId", safeString(startSpotId));
            requestJson.put("current_spot_id", safeString(startSpotId));
            requestJson.put("currentSpotName", safeString(startSpotName));
            requestJson.put("current_spot_name", safeString(startSpotName));
            requestJson.put("latitude", safeString(routeStartLatitude));
            requestJson.put("longitude", safeString(routeStartLongitude));
        }

        JSONObject clientContext = new JSONObject();
        clientContext.put("routeStartType", startType);
        clientContext.put("currentSpotId", safeString(startSpotId));
        clientContext.put("currentSpotName", safeString(startSpotName));
        clientContext.put("routeStartLatitude", safeString(routeStartLatitude));
        clientContext.put("routeStartLongitude", safeString(routeStartLongitude));
        clientContext.put("routeIntent", routeIntent);
        clientContext.put("suppressRoute", suppressRoute);
        if (routeIntent) {
            clientContext.put("routeTrigger", "manual");
        } else {
            clientContext.put("requestType", "spot_explain");
        }
        requestJson.put("clientContext", clientContext);
        requestJson.put("client_context", clientContext);

        Log.d(TAG, "[RouteStart] routeStartType=" + startType
                + ", currentSpotId=" + startSpotId
                + ", currentSpotName=" + startSpotName
                + ", routeStartLatitude=" + routeStartLatitude
                + ", routeStartLongitude=" + routeStartLongitude
                + ", routeIntent=" + routeIntent);
    }

    private void appendGuideContextParams(JSONObject requestJson, String effectiveUserId, String inputType) throws Exception {
        String requestUserId = firstNotEmpty(guideContext.userId, effectiveUserId);
        requestJson.put("visitId", safeString(guideContext.visitId));
        requestJson.put("visit_id", safeString(guideContext.visitId));
        requestJson.put("userId", safeString(requestUserId));
        requestJson.put("user_id", safeString(requestUserId));
        requestJson.put("parkId", safeString(guideContext.parkId));
        requestJson.put("park_id", safeString(guideContext.parkId));
        requestJson.put("parkName", safeString(guideContext.parkName));
        requestJson.put("park_name", safeString(guideContext.parkName));
        requestJson.put("scenicId", safeString(guideContext.scenicId));
        requestJson.put("scenic_id", safeString(guideContext.scenicId));
        requestJson.put("scenicName", safeString(guideContext.scenicName));
        requestJson.put("scenic_name", safeString(guideContext.scenicName));
        requestJson.put("entry", safeString(guideContext.entry));
        requestJson.put("contextType", safeString(guideContext.contextType));
        requestJson.put("context_type", safeString(guideContext.contextType));
        requestJson.put("source", GUIDE_SOURCE);
        requestJson.put("inputType", inputType);
        requestJson.put("input_type", inputType);
        requestJson.put("groupSize", safeString(groupSize));
        requestJson.put("group_size", safeString(groupSize));
        requestJson.put("travelType", safeString(travelType));
        requestJson.put("travel_type", safeString(travelType));
        requestJson.put("visitPreference", safeString(visitPreference));
        requestJson.put("visit_preference", safeString(visitPreference));
    }

    private void startVoiceRecord() {
        if (guideEnded) {
            showToast("本次现场导览已结束");
            return;
        }

        voiceFlowActive = true;

        if (requesting) {
            voiceFlowActive = false;
            showToast("数字人正在处理，请稍候");
            return;
        }

        if (recording) {
            return;
        }

        if (!hasRecordPermission()) {
            requestRecordPermission();
            return;
        }

        try {
            stopCurrentAudio();
            hideKeyboard();

            File dir = new File(getCacheDir(), "voice");
            if (!dir.exists()) {
                boolean ignored = dir.mkdirs();
            }

            currentAudioFile = new File(dir, "guide_voice_" + System.currentTimeMillis() + ".m4a");

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioSamplingRate(16000);
            mediaRecorder.setAudioEncodingBitRate(64000);
            mediaRecorder.setOutputFile(currentAudioFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();

            recording = true;
            recordStartTime = System.currentTimeMillis();

            guideStateText.setText("正在聆听");
            voiceMainButton.setText("松开发送");
            lastQuestionText.setText("正在录音，请说出你的问题");
            appendSystemMessage("正在聆听你的语音问题...");
            applyDigitalHumanState("listen", "neutral");

            if (glSurfaceView != null) {
                glSurfaceView.requestRender();
            }

        } catch (Exception e) {
            voiceFlowActive = false;
            recording = false;
            recordStartTime = 0L;
            releaseRecorder();
            guideStateText.setText("录音失败");
            voiceMainButton.setText("🎙 长按说话");
            showToast("录音启动失败");
            returnDigitalHumanToIdle();
            Log.e(TAG, "录音启动失败", e);
        }
    }

    private void stopVoiceRecordAndUpload() {
        if (guideEnded) {
            recording = false;
            releaseRecorder();
            showToast("本次现场导览已结束");
            return;
        }

        if (!recording) {
            return;
        }

        recording = false;
        long duration = System.currentTimeMillis() - recordStartTime;
        recordStartTime = 0L;

        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
            }
        } catch (Exception e) {
            Log.e(TAG, "录音停止异常", e);
        } finally {
            releaseRecorder();
        }

        if (duration < 600) {
            voiceFlowActive = false;
            guideStateText.setText("等待提问");
            voiceMainButton.setText("🎙 长按说话");
            lastQuestionText.setText("录音时间太短，请重新长按说话");
            appendSystemMessage("录音时间太短，请重新长按说话。");
            if (currentAudioFile != null && currentAudioFile.exists()) {
                boolean ignored = currentAudioFile.delete();
            }
            if (glSurfaceView != null) {
                glSurfaceView.requestRender();
            }
            return;
        }

        if (currentAudioFile == null || !currentAudioFile.exists()) {
            voiceFlowActive = false;
            guideStateText.setText("录音失败");
            voiceMainButton.setText("🎙 长按说话");
            showToast("没有获取到录音文件");
            return;
        }

        uploadVoiceToGuide(currentAudioFile);
    }

    private void releaseRecorder() {
        try {
            if (mediaRecorder != null) {
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
            }
        } catch (Exception ignored) {
            mediaRecorder = null;
        }
    }

    private boolean hasRecordPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestRecordPermission() {
        voiceFlowActive = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        } else {
            voiceFlowActive = false;
        }
    }

    private void uploadVoiceToGuide(final File audioFile) {
        if (guideEnded) {
            voiceFlowActive = false;
            showToast("本次现场导览已结束");
            return;
        }

        if (audioFile == null || !audioFile.exists()) {
            voiceFlowActive = false;
            showToast("录音文件不存在");
            return;
        }

        if (!ensureGuideAuthReady()) {
            voiceFlowActive = false;
            return;
        }

        requesting = true;
        voiceFlowActive = true;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                guideStateText.setText("正在识别");
                voiceMainButton.setText("正在识别语音...");
                lastQuestionText.setText("正在识别你的语音问题");
                appendSystemMessage("正在识别你的语音问题...");
                if (glSurfaceView != null) {
                    glSurfaceView.requestRender();
                }
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;

                try {
                    String boundary = "----Live2DGuideBoundary" + System.currentTimeMillis();
                    URL url = new URL(GUIDE_VOICE_CHAT_URL);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(60000);
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    connection.setUseCaches(false);
                    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                    connection.setRequestProperty("Accept", "application/json");
                    applyAuthorizationHeader(connection);

                    DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());

                    String realUserId = getEffectiveNativeUserId();
                    String realSessionId = firstNotEmpty(sessionId, conversationId);
                    String realConversationId = firstNotEmpty(conversationId, sessionId);

                    writeFormField(outputStream, boundary, "sessionId", realSessionId);
                    writeFormField(outputStream, boundary, "session_id", realSessionId);

                    // APP 登录会话ID单独传，避免污染 AI 对话ID
                    writeFormField(outputStream, boundary, "authSessionId", safeString(appAuthSessionId));
                    writeFormField(outputStream, boundary, "auth_session_id", safeString(appAuthSessionId));
                    writeFormField(outputStream, boundary, "app_session_id", safeString(appAuthSessionId));

                    writeFormField(outputStream, boundary, "userId", realUserId);
                    writeFormField(outputStream, boundary, "user_id", realUserId);

                    writeFormField(outputStream, boundary, "loginUserId", loginUserId);
                    writeFormField(outputStream, boundary, "login_user_id", loginUserId);

                    writeFormField(outputStream, boundary, "visitorId", visitorId);
                    writeFormField(outputStream, boundary, "visitor_id", visitorId);

                    if (realConversationId != null && realConversationId.trim().length() > 0) {
                        writeFormField(outputStream, boundary, "conversationId", realConversationId);
                        writeFormField(outputStream, boundary, "conversation_id", realConversationId);
                    }

                    writeFormField(outputStream, boundary, "scenicName", getTargetName());
                    writeFormField(outputStream, boundary, "scenic_name", getTargetName());
                    writeGuideContextFormFields(outputStream, boundary, realUserId, "voice");
                    writeFormField(outputStream, boundary, "areaCode", safeString(areaCode));
                    writeFormField(outputStream, boundary, "area_code", safeString(areaCode));
                    writeFormField(outputStream, boundary, "areaName", safeString(areaName));
                    writeFormField(outputStream, boundary, "area_name", safeString(areaName));

                    String sceneCode = firstNotEmpty(spotId, scenicId);
                    String sceneName = firstNotEmpty(spotName, scenicName);

                    writeFormField(outputStream, boundary, "sceneCode", safeString(sceneCode));
                    writeFormField(outputStream, boundary, "scene_code", safeString(sceneCode));
                    writeFormField(outputStream, boundary, "sceneName", safeString(sceneName));
                    writeFormField(outputStream, boundary, "scene_name", safeString(sceneName));

                    writeFormField(outputStream, boundary, "currentSpotId", safeString(spotId));
                    writeFormField(outputStream, boundary, "current_spot_id", safeString(spotId));
                    writeFormField(outputStream, boundary, "currentSpotName", safeString(spotName));
                    writeFormField(outputStream, boundary, "current_spot_name", safeString(spotName));

                    writeFormField(outputStream, boundary, "voice", getEffectiveVoiceId());
                    writeFormField(outputStream, boundary, "voiceId", getEffectiveVoiceId());
                    writeFormField(outputStream, boundary, "voice_id", getEffectiveVoiceId());

                    writeFormField(outputStream, boundary, "avatarId", safeString(avatarId));
                    writeFormField(outputStream, boundary, "avatar_id", safeString(avatarId));
                    writeFormField(outputStream, boundary, "avatarName", safeString(avatarName));
                    writeFormField(outputStream, boundary, "avatar_name", safeString(avatarName));
                    writeFormField(outputStream, boundary, "modelPath", safeString(modelPath));
                    writeFormField(outputStream, boundary, "model_path", safeString(modelPath));
                    writeFormField(outputStream, boundary, "digitalHumanConfig", safeString(digitalHumanConfigJson));
                    writeFormField(outputStream, boundary, "digital_human_config", safeString(digitalHumanConfigJson));
                    writeFormField(outputStream, boundary, "clothesMode", safeString(clothesMode));
                    writeFormField(outputStream, boundary, "clothes_mode", safeString(clothesMode));
                    writeFormField(outputStream, boundary, "enableContext", "true");
                    writeFormField(outputStream, boundary, "enable_context", "true");
                    writeFormField(outputStream, boundary, "needVoice", "true");
                    writeFormField(outputStream, boundary, "need_voice", "true");
                    writeFormField(outputStream, boundary, "enableTts", "true");
                    writeFormField(outputStream, boundary, "enable_tts", "true");

                    writeFormField(outputStream, boundary, "mode", safeString(mode));
                    writeFormField(outputStream, boundary, "trigger", safeString(trigger));
                    writeFormField(outputStream, boundary, "distance", safeString(distance));
                    writeFormField(outputStream, boundary, "latitude", safeString(latitude));
                    writeFormField(outputStream, boundary, "longitude", safeString(longitude));

                    Log.d(TAG, "语音问答真实身份参数 realUserId=" + realUserId
                            + ", appUserId=" + appUserId
                            + ", loginUserId=" + loginUserId
                            + ", visitorId=" + visitorId
                            + ", appAuthSessionId=" + appAuthSessionId
                            + ", sessionId=" + realSessionId
                            + ", conversationId=" + realConversationId
                            + ", visitId=" + visitId
                            + ", groupSize=" + groupSize
                            + ", travelType=" + travelType
                            + ", visitPreference=" + visitPreference);
                    logAiQuestion("[voice]");

                    writeFileField(outputStream, boundary, "audio", audioFile, "audio/mp4");
                    outputStream.writeBytes("--" + boundary + "--\r\n");
                    outputStream.flush();
                    outputStream.close();

                    int responseCode = connection.getResponseCode();
                    InputStream inputStream = responseCode >= 200 && responseCode < 300 ? connection.getInputStream() : connection.getErrorStream();
                    String responseText = readStream(inputStream);

                    if (responseCode >= 200 && responseCode < 300) {
                        final GuideResponse guideResponse = parseGuideResponse(responseText);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (guideEnded) {
                                    return;
                                }
                                requesting = false;
                                voiceFlowActive = false;
                                guideStateText.setText("识别完成");
                                voiceMainButton.setText("🎙 长按说话");

                                if (guideResponse.questionText != null
                                        && guideResponse.questionText.trim().length() > 0
                                        && !"null".equalsIgnoreCase(guideResponse.questionText.trim())) {
                                    lastQuestionText.setText("游客提问：" + guideResponse.questionText);
                                    appendUserMessage(guideResponse.questionText);
                                } else {
                                    lastQuestionText.setText("语音提问已识别");
                                    appendSystemMessage("语音提问已识别。");
                                }

                                showGuideAnswer(guideResponse.answer);
                                updateQuickButtons(guideResponse.suggestions);
                                handleGuideRouteResponse(
                                        guideResponse.route,
                                        isRouteIntentQuestion(guideResponse.questionText),
                                        "voice"
                                );

                                if (guideResponse.conversationId != null && guideResponse.conversationId.trim().length() > 0) {
                                    conversationId = guideResponse.conversationId.trim();
                                }

                                playAnswerVoice(guideResponse);
                                forceRenderLive2D();
                            }
                        });
                    } else {
                        final String errorText = formatBackendErrorMessage(responseText, "语音请求失败，请稍后重试");
                        Log.e(TAG, "语音问答 HTTP 失败 code=" + responseCode + ", response=" + responseText);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (guideEnded) {
                                    return;
                                }
                                requesting = false;
                                voiceFlowActive = false;
                                guideStateText.setText("识别失败");
                                voiceMainButton.setText("🎙 长按说话");
                                showGuideAnswer(errorText);
                                returnDigitalHumanToIdle();
                                forceRenderLive2D();
                            }
                        });
                    }

                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (guideEnded) {
                                return;
                            }
                            requesting = false;
                            voiceFlowActive = false;
                            guideStateText.setText("语音异常");
                            voiceMainButton.setText("🎙 长按说话");
                            showGuideAnswer("语音识别请求失败。\n\n请检查：\n1. 小后端语音接口是否启动\n2. GUIDE_VOICE_CHAT_URL 是否正确\n3. 后端是否能接收 multipart/form-data\n4. AI 端阿里云语音识别是否正常\n5. AI 端是否支持 m4a / audio/mp4 格式");
                            returnDigitalHumanToIdle();
                            forceRenderLive2D();
                        }
                    });
                    Log.e(TAG, "上传语音失败", e);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                    try {
                        if (audioFile.exists()) {
                            boolean ignored = audioFile.delete();
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }).start();
    }

    private void writeGuideContextFormFields(DataOutputStream outputStream, String boundary, String effectiveUserId, String inputType) throws Exception {
        String requestUserId = firstNotEmpty(guideContext.userId, effectiveUserId);
        writeFormField(outputStream, boundary, "visitId", safeString(guideContext.visitId));
        writeFormField(outputStream, boundary, "visit_id", safeString(guideContext.visitId));
        writeFormField(outputStream, boundary, "userId", safeString(requestUserId));
        writeFormField(outputStream, boundary, "user_id", safeString(requestUserId));
        writeFormField(outputStream, boundary, "parkId", safeString(guideContext.parkId));
        writeFormField(outputStream, boundary, "park_id", safeString(guideContext.parkId));
        writeFormField(outputStream, boundary, "parkName", safeString(guideContext.parkName));
        writeFormField(outputStream, boundary, "park_name", safeString(guideContext.parkName));
        writeFormField(outputStream, boundary, "scenicId", safeString(guideContext.scenicId));
        writeFormField(outputStream, boundary, "scenic_id", safeString(guideContext.scenicId));
        writeFormField(outputStream, boundary, "scenicName", safeString(guideContext.scenicName));
        writeFormField(outputStream, boundary, "scenic_name", safeString(guideContext.scenicName));
        writeFormField(outputStream, boundary, "groupSize", safeString(groupSize));
        writeFormField(outputStream, boundary, "group_size", safeString(groupSize));
        writeFormField(outputStream, boundary, "travelType", safeString(travelType));
        writeFormField(outputStream, boundary, "travel_type", safeString(travelType));
        writeFormField(outputStream, boundary, "visitPreference", safeString(visitPreference));
        writeFormField(outputStream, boundary, "visit_preference", safeString(visitPreference));
        writeFormField(outputStream, boundary, "entry", safeString(guideContext.entry));
        writeFormField(outputStream, boundary, "contextType", safeString(guideContext.contextType));
        writeFormField(outputStream, boundary, "context_type", safeString(guideContext.contextType));
        writeFormField(outputStream, boundary, "source", GUIDE_SOURCE);
        writeFormField(outputStream, boundary, "inputType", inputType);
        writeFormField(outputStream, boundary, "input_type", inputType);
    }

    private void writeFormField(DataOutputStream outputStream, String boundary, String name, String value) throws Exception {
        if (value == null) {
            value = "";
        }
        outputStream.writeBytes("--" + boundary + "\r\n");
        outputStream.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n");
        outputStream.writeBytes("Content-Type: text/plain; charset=UTF-8\r\n");
        outputStream.writeBytes("\r\n");
        outputStream.write(value.getBytes(StandardCharsets.UTF_8));
        outputStream.writeBytes("\r\n");
    }

    private void writeFileField(DataOutputStream outputStream, String boundary, String fieldName, File file, String mimeType) throws Exception {
        outputStream.writeBytes("--" + boundary + "\r\n");
        outputStream.writeBytes("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + file.getName() + "\"\r\n");
        outputStream.writeBytes("Content-Type: " + mimeType + "\r\n");
        outputStream.writeBytes("\r\n");

        FileInputStream fileInputStream = new FileInputStream(file);
        byte[] buffer = new byte[4096];
        int length;
        while ((length = fileInputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
        fileInputStream.close();
        outputStream.writeBytes("\r\n");
    }

    private GuideResponse parseGuideResponse(String responseText) {
        GuideResponse result = new GuideResponse();

        try {
            JSONObject root = new JSONObject(responseText);
            JSONObject data = null;
            if (root.has("data") && !root.isNull("data")) {
                Object dataObj = root.get("data");
                if (dataObj instanceof JSONObject) {
                    data = (JSONObject) dataObj;
                }
            }

            JSONObject source = data != null ? data : root;
            JSONObject audioJson = getJsonObject(source, "audio");
            JSONObject mouthJson = getJsonObject(source, "mouth");
            JSONObject digitalHumanJson = getJsonObject(source, "digitalHuman", "digital_human");

            int businessCode = root.optInt("code", 200);
            String businessMsg = firstNotEmpty(
                    root.optString("msg", ""),
                    root.optString("message", "")
            );
            if (!(businessCode == 0 || businessCode == 200) && data == null) {
                if (businessMsg.contains("用户不存在")
                        || businessMsg.contains("登录")
                        || businessMsg.contains("token")
                        || businessMsg.contains("Token")) {
                    result.answer = "登录状态异常，请返回首页重新进入导览";
                } else {
                    result.answer = businessMsg.length() > 0 ? businessMsg : "请求失败，请稍后重试";
                }
                Log.e(TAG, "AI 接口业务失败 code=" + businessCode
                        + ", msg=" + businessMsg
                        + ", response=" + responseText);
                return result;
            }

            result.questionText = source.optString("questionText", "");
            if (result.questionText == null || result.questionText.trim().length() == 0) {
                result.questionText = source.optString("question_text", "");
            }
            if (result.questionText == null || result.questionText.trim().length() == 0) {
                result.questionText = source.optString("question", "");
            }
            if ("null".equalsIgnoreCase(result.questionText == null ? "" : result.questionText.trim())) {
                result.questionText = "";
            }

            result.answer = source.optString("answer", "");
            if (result.answer == null || result.answer.trim().length() == 0) {
                result.answer = source.optString("content", "");
            }

            result.audioUrl = firstNotEmpty(
                    getJsonText(source, "audioUrl", "audio_url"),
                    getJsonText(audioJson, "url", "audioUrl", "audio_url")
            );
            result.audioStatus = firstNotEmpty(
                    getJsonText(source, "audioStatus", "audio_status"),
                    getJsonText(audioJson, "status", "audioStatus", "audio_status")
            );
            result.ttsTaskId = firstNotEmpty(
                    getJsonText(source, "ttsTaskId", "tts_task_id", "taskId", "task_id"),
                    getJsonText(audioJson, "taskId", "task_id")
            );
            result.audioDurationMs = parseLongOrDefault(firstNotEmpty(
                    getJsonText(source, "audioDurationMs", "audio_duration_ms", "durationMs", "duration_ms"),
                    getJsonText(audioJson, "audioDurationMs", "audio_duration_ms", "durationMs", "duration_ms", "duration")
            ), 0L);
            result.action = getJsonText(source, "action");
            result.actionCode = firstNotEmpty(
                    getJsonText(source, "actionCode", "action_code"),
                    getJsonText(digitalHumanJson, "actionCode", "action_code")
            );
            if (result.action.length() == 0) {
                result.action = getJsonText(digitalHumanJson, "action");
            }
            result.emotion = getJsonText(source, "emotion");
            result.emotionCode = firstNotEmpty(
                    getJsonText(source, "emotionCode", "emotion_code"),
                    getJsonText(digitalHumanJson, "emotionCode", "emotion_code")
            );
            if (result.emotion.length() == 0) {
                result.emotion = getJsonText(digitalHumanJson, "emotion");
            }

            result.conversationId = source.optString("conversationId", "");
            if (result.conversationId == null || result.conversationId.trim().length() == 0) {
                result.conversationId = source.optString("conversation_id", "");
            }

            result.messageId = source.optString("messageId", "");
            if (result.messageId == null || result.messageId.trim().length() == 0) {
                result.messageId = source.optString("message_id", "");
            }

            result.ttsError = source.optString("ttsError", "");
            if (result.ttsError == null || result.ttsError.trim().length() == 0) {
                result.ttsError = source.optString("tts_error", "");
            }

            JSONArray suggestionsArray = source.optJSONArray("suggestions");
            if (suggestionsArray != null) {
                for (int i = 0; i < suggestionsArray.length(); i++) {
                    String item = suggestionsArray.optString(i, "");
                    if (item != null && item.trim().length() > 0) {
                        result.suggestions.add(item.trim());
                    }
                }
            }

            JSONArray mouthFramesArray = getJsonArray(source, "mouthFrames", "mouth_frames");
            if (mouthFramesArray == null) {
                mouthFramesArray = getJsonArray(mouthJson, "frames", "mouthFrames", "mouth_frames");
            }
            if (mouthFramesArray == null) {
                mouthFramesArray = getJsonArray(audioJson, "frames", "mouthFrames", "mouth_frames", "mouthFrames");
            }
            if (mouthFramesArray != null) {
                for (int i = 0; i < mouthFramesArray.length(); i++) {
                    JSONObject frame = mouthFramesArray.optJSONObject(i);
                    if (frame == null) continue;
                    String timeText = firstNotEmpty(getJsonText(
                            frame,
                            "timeMs",
                            "time_ms",
                            "timestampMs",
                            "timestamp_ms",
                            "offsetMs",
                            "offset_ms",
                            "time",
                            "timestamp",
                            "t"
                    ));
                    boolean hasTimeField = timeText.length() > 0;
                    double rawTime = hasTimeField ? parseDoubleOrDefault(timeText, i * 40.0d) : i * 40.0d;
                    String openText = firstNotEmpty(getJsonText(
                            frame,
                            "open",
                            "value",
                            "mouthOpen",
                            "mouth_open",
                            "mouthOpenY",
                            "mouth_open_y",
                            "openY",
                            "open_y",
                            "ParamMouthOpenY",
                            "paramMouthOpenY"
                    ));
                    boolean hasOpenField = openText.length() > 0;
                    double openValue = hasOpenField ? parseDoubleOrDefault(openText, 0.0d) : 0.0d;
                    double formValue = parseDoubleOrDefault(firstNotEmpty(
                            getJsonText(frame, "form", "mouthForm", "mouth_form")
                    ), 0.0d);
                    result.mouthFrames.add(new MouthSyncManager.MouthFrame((int) Math.round(rawTime), (float) openValue, (float) formValue));
                    result.controllerMouthFrames.add(new MouthSyncController.MouthFrame(
                            rawTime,
                            (float) openValue,
                            (float) formValue,
                            hasTimeField,
                            hasOpenField
                    ));
                }
            }

            result.route = parseRouteFromAiResponse(source);
            if (result.route == null && source != root) {
                result.route = parseRouteFromAiResponse(root);
            }
            if (result.route != null) {
                Log.d(TAG, "解析到路线卡片 routeName=" + result.route.routeName
                        + ", mapAction=" + result.route.mapAction
                        + ", nodes=" + result.route.nodes.size());
            }

            Log.d(TAG, "解析到 questionText: " + result.questionText);
            Log.d(TAG, "解析到 audioUrl: " + result.audioUrl);
            Log.d(TAG, "解析到 mouthFrames 数量: " + result.mouthFrames.size());
            Log.d(TAG, "[GuideResponse] answer=" + safeLogText(result.answer)
                    + ", audioUrl=" + safeString(result.audioUrl)
                    + ", audioStatus=" + safeString(result.audioStatus));
            Log.d(TAG, "[AudioPlay] parsed audioUrl=" + safeString(result.audioUrl)
                    + ", answerLength=" + safeString(result.answer).length()
                    + ", mouthFrames=" + result.mouthFrames.size());
            Log.d(TAG, "[GuideResponse] audioStatus=" + safeString(result.audioStatus)
                    + ", audioUrl=" + safeString(result.audioUrl)
                    + ", mouthFrames=" + result.mouthFrames.size()
                    + ", action=" + firstNotEmpty(result.action, result.actionCode)
                    + ", emotion=" + firstNotEmpty(result.emotion, result.emotionCode));

            if (result.answer == null || result.answer.trim().length() == 0) {
                String fallbackMsg = firstNotEmpty(root.optString("msg", ""), root.optString("message", ""));
                if (fallbackMsg.length() > 0) {
                    if (fallbackMsg.contains("用户不存在")
                            || fallbackMsg.contains("登录")
                            || fallbackMsg.contains("token")
                            || fallbackMsg.contains("Token")) {
                        result.answer = "登录状态异常，请返回首页重新进入导览";
                    } else {
                        result.answer = fallbackMsg;
                    }
                } else {
                    result.answer = responseText;
                }
            }

            return result;
        } catch (Exception e) {
            result.answer = responseText;
            return result;
        }
    }

    private RouteInfo parseRouteFromAiResponse(JSONObject data) {
        JSONObject routeJson = findRouteJson(data);
        if (routeJson == null) {
            return null;
        }
        return parseRouteInfo(routeJson);
    }

    private JSONObject findRouteJson(JSONObject object) {
        if (object == null) {
            return null;
        }

        String[] keys = new String[]{
                "route",
                "routePlan",
                "route_plan",
                "route_recommendation",
                "routeRecommendation"
        };

        for (String key : keys) {
            JSONObject candidate = object.optJSONObject(key);
            if (candidate == null) {
                continue;
            }

            JSONObject nestedRoute = candidate.optJSONObject("route");
            if (nestedRoute != null) {
                return nestedRoute;
            }
            return candidate;
        }

        return null;
    }

    private RouteInfo parseRouteInfo(JSONObject routeJson) {
        RouteInfo route = new RouteInfo();
        route.planId = getJsonText(routeJson, "planId", "plan_id", "routePlanId", "route_plan_id");
        route.routeName = getJsonText(routeJson, "routeName", "route_name", "name", "title");
        route.reason = getJsonText(routeJson, "reason", "recommendReason", "recommend_reason");
        route.totalDistanceM = getJsonText(routeJson, "totalDistanceM", "total_distance_m", "distanceM", "distance_m");
        route.estimatedDurationMin = getJsonText(routeJson, "estimatedDurationMin", "estimated_duration_min", "durationMin", "duration_min");
        route.mapAction = getJsonText(routeJson, "mapAction", "map_action");
        route.routeMapReady = getJsonText(routeJson, "routeMapReady", "route_map_ready", "mapReady", "map_ready");
        route.rawPolylinePoints.addAll(parseRoutePolyline(routeJson));

        JSONArray nodesArray = getJsonArray(routeJson,
                "nodes",
                "spots",
                "routeNodes",
                "route_nodes",
                "spotList",
                "spot_list",
                "scenicSpots",
                "scenic_spots"
        );

        if (nodesArray != null) {
            for (int i = 0; i < nodesArray.length(); i++) {
                JSONObject nodeJson = nodesArray.optJSONObject(i);
                if (nodeJson == null) continue;

                RouteNode node = new RouteNode();
                node.order = firstNotEmpty(getJsonText(nodeJson, "order", "sort", "index"), String.valueOf(i + 1));
                node.orderNumber = parseIntOrDefault(node.order, i + 1);
                node.id = getJsonText(nodeJson, "spotId", "spot_id", "scenicId", "scenic_id", "sceneCode", "scene_code", "id");
                node.scenicId = getJsonText(nodeJson, "scenicId", "scenic_id", "sceneCode", "scene_code", "id");
                node.spotId = getJsonText(nodeJson, "spotId", "spot_id", "id", "scenicId", "scenic_id");
                node.name = getJsonText(nodeJson, "spotName", "spot_name", "scenicName", "scenic_name", "name", "title");
                node.spotName = firstNotEmpty(getJsonText(nodeJson, "spotName", "spot_name"), node.name);
                node.scenicName = firstNotEmpty(getJsonText(nodeJson, "scenicName", "scenic_name", "name", "title"), node.name);
                node.guideText = getJsonText(nodeJson, "guideText", "guide_text", "description", "desc");
                node.recommendedStayMin = getJsonText(nodeJson, "recommendedStayMin", "recommended_stay_min", "stayMin", "stay_min");
                node.latitude = getJsonText(nodeJson, "latitude", "lat");
                node.longitude = getJsonText(nodeJson, "longitude", "lng", "lon");
                normalizeRouteNodeLocation(node);
                route.nodes.add(node);
            }
        }

        normalizeRouteNodes(route);

        return route;
    }

    private JSONArray getJsonArray(JSONObject object, String... keys) {
        if (object == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            JSONArray array = object.optJSONArray(key);
            if (array != null) {
                return array;
            }
        }
        return null;
    }

    private JSONObject getJsonObject(JSONObject object, String... keys) {
        if (object == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null || !object.has(key) || object.isNull(key)) {
                continue;
            }
            Object value = object.opt(key);
            if (value instanceof JSONObject) {
                return (JSONObject) value;
            }
        }
        return null;
    }

    private List<LatLng> parseRoutePolyline(JSONObject routeJson) {
        List<LatLng> result = new ArrayList<>();
        if (routeJson == null) {
            return result;
        }
        Object polylineObject = null;
        String[] keys = new String[]{"routePolyline", "route_polyline", "mapPolyline", "map_polyline", "roadPolyline", "road_polyline", "polyline", "polylinePoints", "polyline_points", "path", "paths"};
        for (String key : keys) {
            if (routeJson.has(key) && !routeJson.isNull(key)) {
                polylineObject = routeJson.opt(key);
                break;
            }
        }
        appendRoutePolylineValue(result, polylineObject);
        return result;
    }

    private void appendRoutePolylineValue(List<LatLng> points, Object value) {
        if (points == null || value == null) {
            return;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                appendRoutePolylineValue(points, array.opt(i));
            }
            return;
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            double lat = parseDoubleOrNaN(getJsonText(object, "latitude", "lat"));
            double lon = parseDoubleOrNaN(getJsonText(object, "longitude", "lng", "lon"));
            if (isValidCoordinate(lat, lon)) {
                appendLatLngDedup(points, new LatLng(lat, lon));
            }
            return;
        }

        String text = String.valueOf(value).trim();
        if (text.length() == 0 || "null".equalsIgnoreCase(text)) {
            return;
        }
        String normalized = text.replace("|", ";").replace(" ", ";");
        String[] parts = normalized.split(";");
        for (String part : parts) {
            String item = part == null ? "" : part.trim();
            if (item.length() == 0) {
                continue;
            }
            String[] pair = item.split(",");
            if (pair.length < 2) {
                continue;
            }
            double first = parseDoubleOrNaN(pair[0]);
            double second = parseDoubleOrNaN(pair[1]);
            if (isValidCoordinate(first, second)) {
                appendLatLngDedup(points, new LatLng(first, second));
            } else if (isValidCoordinate(second, first)) {
                appendLatLngDedup(points, new LatLng(second, first));
            }
        }
    }

    private String getJsonText(JSONObject object, String... keys) {
        if (object == null || keys == null) {
            return "";
        }

        for (String key : keys) {
            if (key == null || !object.has(key) || object.isNull(key)) {
                continue;
            }
            Object value = object.opt(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (text.length() > 0 && !"null".equalsIgnoreCase(text)) {
                    return text;
                }
            }
        }

        return "";
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(safeString(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private long parseLongOrDefault(String value, long defaultValue) {
        try {
            return Long.parseLong(safeString(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void normalizeRouteNodes(RouteInfo route) {
        if (route == null || route.nodes == null) {
            return;
        }
        for (int i = 0; i < route.nodes.size(); i++) {
            RouteNode node = route.nodes.get(i);
            if (node == null) {
                continue;
            }
            if (node.orderNumber <= 0) {
                node.orderNumber = parseIntOrDefault(node.order, i + 1);
            }
            if (safeString(node.order).length() == 0) {
                node.order = String.valueOf(node.orderNumber);
            }
            node.id = firstNotEmpty(node.id, node.spotId, node.scenicId);
            node.name = firstNotEmpty(node.name, node.spotName, node.scenicName, "路线节点");
            node.spotName = firstNotEmpty(node.spotName, node.name);
            node.scenicName = firstNotEmpty(node.scenicName, node.name);
            fillNodeLocationFromKnownSpots(node);
            normalizeRouteNodeLocation(node);
        }
        Collections.sort(route.nodes, new Comparator<RouteNode>() {
            @Override
            public int compare(RouteNode left, RouteNode right) {
                int leftOrder = left == null ? 0 : left.orderNumber;
                int rightOrder = right == null ? 0 : right.orderNumber;
                return leftOrder - rightOrder;
            }
        });
        for (int i = 0; i < route.nodes.size(); i++) {
            RouteNode node = route.nodes.get(i);
            if (node != null) {
                node.orderNumber = i + 1;
                node.order = String.valueOf(i + 1);
            }
        }
    }

    private void normalizeRouteNodeLocation(RouteNode node) {
        if (node == null) {
            return;
        }
        double lat = parseDoubleOrNaN(node.latitude);
        double lon = parseDoubleOrNaN(node.longitude);
        node.hasLocation = isValidCoordinate(lat, lon);
        if (node.hasLocation) {
            node.latitudeValue = lat;
            node.longitudeValue = lon;
            node.latitude = String.valueOf(lat);
            node.longitude = String.valueOf(lon);
        }
    }

    private void fillNodeLocationFromKnownSpots(RouteNode node) {
        if (node == null || hasRouteNodeLocation(node)) {
            return;
        }

        RouteStartSpot matchedSpot = findKnownSpotForRouteNode(node);
        if (matchedSpot != null) {
            node.latitude = firstNotEmpty(node.latitude, matchedSpot.latitude);
            node.longitude = firstNotEmpty(node.longitude, matchedSpot.longitude);
            node.spotId = firstNotEmpty(node.spotId, matchedSpot.spotId);
            node.scenicId = firstNotEmpty(node.scenicId, matchedSpot.scenicId);
            node.id = firstNotEmpty(node.id, node.spotId, node.scenicId);
            normalizeRouteNodeLocation(node);
            return;
        }

        String nodeId = firstNotEmpty(node.spotId, node.scenicId, node.id);
        String nodeName = getRouteNodeName(node);
        boolean sameCurrentId = nodeId.length() > 0 && nodeId.equals(firstNotEmpty(spotId, scenicId));
        boolean sameCurrentName = nodeName.length() > 0 && nodeName.equals(firstNotEmpty(spotName, scenicName));
        if ((sameCurrentId || sameCurrentName) && isValidCoordinate(parseDoubleOrNaN(latitude), parseDoubleOrNaN(longitude))) {
            node.latitude = latitude;
            node.longitude = longitude;
            normalizeRouteNodeLocation(node);
        }
    }

    private boolean hasRouteNodeLocation(RouteNode node) {
        if (node == null) {
            return false;
        }
        if (node.hasLocation) {
            return true;
        }
        return isValidCoordinate(parseDoubleOrNaN(node.latitude), parseDoubleOrNaN(node.longitude));
    }

    private RouteStartSpot findKnownSpotForRouteNode(RouteNode node) {
        if (node == null) {
            return null;
        }
        String nodeId = firstNotEmpty(node.spotId, node.scenicId, node.id);
        String nodeName = getRouteNodeName(node);
        for (RouteStartSpot spot : routeStartSpots) {
            if (spot == null) {
                continue;
            }
            String spotIdText = firstNotEmpty(spot.spotId, spot.scenicId);
            if (nodeId.length() > 0 && nodeId.equals(spotIdText)) {
                return spot;
            }
            if (nodeName.length() > 0 && nodeName.equals(firstNotEmpty(spot.spotName))) {
                return spot;
            }
        }
        return null;
    }

    private boolean showRouteCardIfNeeded(RouteInfo route) {
        return showRouteCapsule(route);
    }

    private boolean showRouteCapsule(RouteInfo route) {
        if (route == null) {
            return false;
        }
        boolean hasNodes = route.nodes != null && route.nodes.size() > 0;
        if (!"show_route_card".equals(route.mapAction) && !hasNodes) {
            Log.d(TAG, "忽略路线卡片 mapAction=" + route.mapAction + ", nodes=0");
            return false;
        }

        currentRoute = route;
        normalizeRouteNodes(route);
        currentRoutePreview = createInitialRoutePreview(route);
        route.preview = currentRoutePreview;
        routeCardExpanded = false;
        resetRouteDemoState();
        renderRouteCard(route);
        if (routeCardContainer != null) {
            routeCardContainer.setVisibility(View.VISIBLE);
        }
        buildRoutePreviewWithAmap(route);
        trackRouteEventOnce("map_card_show", route, null);
        trackRouteEvent("route_view", route, null);
        return true;
    }

    private void hideRouteCard() {
        exitRouteMapModeInternal(true);
        dismissRouteExpandedPanel();

        if (routeCardContainer != null) {
            routeCardContainer.setVisibility(View.GONE);
        }

        if (answerBoxContainer != null) {
            answerBoxContainer.setVisibility(View.VISIBLE);
        }

        currentRoute = null;
        currentRoutePreview = null;
        resetRouteDemoState();
        clearRouteMapOverlays();
    }

    private RoutePreviewData createInitialRoutePreview(RouteInfo route) {
        RoutePreviewData preview = new RoutePreviewData();
        preview.routeName = formatRouteCardTitle(route);
        preview.nodes = route == null || route.nodes == null ? new ArrayList<RouteNode>() : new ArrayList<>(route.nodes);
        preview.routePreviewStartPoint = resolveRoutePreviewStartPoint(route);

        List<RouteNode> planningNodes = buildRoutePlanningNodes(preview.routePreviewStartPoint, route);
        int validLocationCount = countLocatedNodes(planningNodes);
        if (validLocationCount < 2) {
            preview.calculating = false;
            preview.amapRouteReady = false;
            preview.partialFallback = true;
            preview.message = "当前路线缺少景点坐标，暂按推荐顺序模拟导览";
            preview.polylinePoints = collectLocatedLatLngs(planningNodes);
            return preview;
        }

        preview.calculating = true;
        preview.message = "正在生成高德步行路线...";
        preview.polylinePoints = collectLocatedLatLngs(planningNodes);
        return preview;
    }

    private void buildRoutePreviewWithAmap(final RouteInfo route) {
        if (route == null) {
            return;
        }

        normalizeRouteNodes(route);
        RoutePreviewData preview = route.preview;
        if (preview == null || preview != currentRoutePreview) {
            preview = createInitialRoutePreview(route);
            route.preview = preview;
            currentRoutePreview = preview;
        }

        if (route.rawPolylinePoints != null && route.rawPolylinePoints.size() >= 2) {
            preview.calculating = false;
            preview.amapRouteReady = true;
            preview.partialFallback = false;
            preview.message = "已生成高德步行路线";
            preview.polylinePoints.clear();
            preview.polylinePoints.addAll(route.rawPolylinePoints);
            Log.d(TAG, "[BackendRoute] 使用后端高德WebService路线: ready=true, points="
                    + route.rawPolylinePoints.size());
            updateRouteCard(preview);
            drawRouteOnMap(preview);
            if (routeExpandedOverlay != null) {
                showRouteExpandedPanel(false);
            }
            return;
        }

        final int requestSeq = ++routePreviewRequestSeq;
        final List<RouteNode> planningNodes = buildRoutePlanningNodes(preview.routePreviewStartPoint, route);
        final List<RouteNode> locatedNodes = filterLocatedNodes(planningNodes);

        if (locatedNodes.size() < 2) {
            preview.calculating = false;
            preview.amapRouteReady = false;
            preview.partialFallback = true;
            preview.message = "当前路线缺少景点坐标，暂按推荐顺序模拟导览";
            drawFallbackPolyline(preview);
            updateRouteCard(preview);
            return;
        }

        preview.calculating = true;
        preview.amapRouteReady = false;
        preview.partialFallback = false;
        preview.message = "正在生成高德步行路线...";
        preview.polylinePoints = collectLocatedLatLngs(locatedNodes);
        updateRouteCard(preview);
        drawFallbackPolyline(preview);

        if (routeAMap == null || routeMapView == null) {
            preview.calculating = false;
            preview.amapRouteReady = false;
            preview.partialFallback = true;
            preview.message = "高德路线暂不可用，已按推荐节点顺序展示";
            updateRouteCard(preview);
            drawRouteOnMap(preview);
            return;
        }

        requestWalkingRouteSegments(requestSeq, preview, locatedNodes);
    }

    private void requestWalkingRouteSegments(int requestSeq, RoutePreviewData preview, List<RouteNode> locatedNodes) {
        try {
            ServiceSettings.updatePrivacyShow(this, true, true);
            ServiceSettings.updatePrivacyAgree(this, true);
        } catch (Throwable e) {
            Log.e(TAG, "设置高德搜索隐私合规状态失败", e);
        }

        int segmentCount = locatedNodes.size() - 1;
        if (segmentCount <= 0) {
            preview.calculating = false;
            preview.message = "当前路线缺少景点坐标，暂按推荐顺序模拟导览";
            updateRouteCard(preview);
            return;
        }

        final RouteSegmentRequestState state = new RouteSegmentRequestState();
        state.requestSeq = requestSeq;
        state.totalSegmentCount = segmentCount;
        state.preview = preview;
        state.segmentNodes = locatedNodes;

        for (int i = 0; i < segmentCount; i++) {
            final int segmentIndex = i;
            final RouteNode fromNode = locatedNodes.get(i);
            final RouteNode toNode = locatedNodes.get(i + 1);
            final LatLng from = toLatLng(fromNode);
            final LatLng to = toLatLng(toNode);

            if (from == null || to == null) {
                finishRouteSegment(state, segmentIndex, buildFallbackSegment(from, to));
                continue;
            }

            final boolean[] completedByCallback = new boolean[]{false};
            if (mainHandler != null) {
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!completedByCallback[0]) {
                            completedByCallback[0] = true;
                            finishRouteSegment(state, segmentIndex, buildFallbackSegment(from, to));
                        }
                    }
                }, 12000L);
            }

            try {
                RouteSearchV2 routeSearch = new RouteSearchV2(this);
                state.searches.add(routeSearch);
                routeSearch.setRouteSearchListener(new RouteSearchV2.OnRouteSearchListener() {
                    @Override
                    public void onDriveRouteSearched(DriveRouteResultV2 result, int errorCode) {
                    }

                    @Override
                    public void onBusRouteSearched(BusRouteResultV2 result, int errorCode) {
                    }

                    @Override
                    public void onWalkRouteSearched(WalkRouteResultV2 result, int errorCode) {
                        if (completedByCallback[0]) {
                            return;
                        }
                        completedByCallback[0] = true;
                        RouteSegmentResult segmentResult;
                        if (errorCode == AMapException.CODE_AMAP_SUCCESS) {
                            segmentResult = parseWalkRouteResult(result, from, to);
                        } else {
                            Log.e(TAG, "高德步行路线规划失败 segment=" + segmentIndex + ", errorCode=" + errorCode);
                            segmentResult = buildFallbackSegment(from, to);
                        }
                        finishRouteSegment(state, segmentIndex, segmentResult);
                    }

                    @Override
                    public void onRideRouteSearched(RideRouteResultV2 result, int errorCode) {
                    }
                });

                RouteSearchV2.FromAndTo fromAndTo = new RouteSearchV2.FromAndTo(
                        new LatLonPoint(from.latitude, from.longitude),
                        new LatLonPoint(to.latitude, to.longitude)
                );
                RouteSearchV2.WalkRouteQuery query = new RouteSearchV2.WalkRouteQuery(fromAndTo);
                routeSearch.calculateWalkRouteAsyn(query);
            } catch (Throwable e) {
                Log.e(TAG, "发起高德步行路线规划异常 segment=" + segmentIndex, e);
                completedByCallback[0] = true;
                finishRouteSegment(state, segmentIndex, buildFallbackSegment(from, to));
            }
        }
    }

    private RouteSegmentResult parseWalkRouteResult(WalkRouteResultV2 result, LatLng from, LatLng to) {
        if (result == null || result.getPaths() == null || result.getPaths().size() == 0) {
            return buildFallbackSegment(from, to);
        }
        Object pathObject = result.getPaths().get(0);
        if (!(pathObject instanceof WalkPath)) {
            return buildFallbackSegment(from, to);
        }

        WalkPath path = (WalkPath) pathObject;
        RouteSegmentResult segmentResult = new RouteSegmentResult();
        if (path.getSteps() != null) {
            for (Object stepObject : path.getSteps()) {
                if (!(stepObject instanceof WalkStep)) {
                    continue;
                }
                WalkStep step = (WalkStep) stepObject;
                segmentResult.distanceMeter += step.getDistance();
                segmentResult.durationSecond += Math.max(0L, (long) step.getDuration());
                if (step.getPolyline() == null) {
                    continue;
                }
                for (Object pointObject : step.getPolyline()) {
                    if (!(pointObject instanceof LatLonPoint)) {
                        continue;
                    }
                    LatLonPoint point = (LatLonPoint) pointObject;
                    appendLatLngDedup(segmentResult.points, new LatLng(point.getLatitude(), point.getLongitude()));
                }
            }
        }

        if (segmentResult.points.size() < 2) {
            return buildFallbackSegment(from, to);
        }
        if (segmentResult.distanceMeter <= 0) {
            segmentResult.distanceMeter = calculateDistanceMeters(from, to);
        }
        if (segmentResult.durationSecond <= 0) {
            segmentResult.durationSecond = estimateWalkDurationSecond(segmentResult.distanceMeter);
        }
        segmentResult.fallbackLine = false;
        return segmentResult;
    }

    private RouteSegmentResult buildFallbackSegment(LatLng from, LatLng to) {
        RouteSegmentResult segmentResult = new RouteSegmentResult();
        segmentResult.fallbackLine = true;
        if (from != null) {
            appendLatLngDedup(segmentResult.points, from);
        }
        if (to != null) {
            appendLatLngDedup(segmentResult.points, to);
        }
        segmentResult.distanceMeter = calculateDistanceMeters(from, to);
        segmentResult.durationSecond = estimateWalkDurationSecond(segmentResult.distanceMeter);
        return segmentResult;
    }

    private void finishRouteSegment(final RouteSegmentRequestState state, final int segmentIndex, final RouteSegmentResult result) {
        if (state == null) {
            return;
        }
        synchronized (state) {
            if (state.results.containsKey(segmentIndex)) {
                return;
            }
            state.results.put(segmentIndex, result);
            state.completedSegmentCount++;
            if (state.completedSegmentCount < state.totalSegmentCount) {
                return;
            }
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!canUpdateRouteUi() || state.requestSeq != routePreviewRequestSeq || state.preview != currentRoutePreview) {
                    return;
                }
                mergeRouteSegments(state);
            }
        });
    }

    private void mergeRouteSegments(RouteSegmentRequestState state) {
        RoutePreviewData preview = state.preview;
        preview.polylinePoints.clear();
        preview.totalDistanceMeter = 0f;
        preview.totalDurationSecond = 0L;

        boolean hasFallback = false;
        boolean allFallback = true;
        for (int i = 0; i < state.totalSegmentCount; i++) {
            RouteSegmentResult segmentResult = state.results.get(i);
            if (segmentResult == null) {
                LatLng from = toLatLng(state.segmentNodes.get(i));
                LatLng to = toLatLng(state.segmentNodes.get(i + 1));
                segmentResult = buildFallbackSegment(from, to);
            }
            preview.totalDistanceMeter += segmentResult.distanceMeter;
            preview.totalDurationSecond += segmentResult.durationSecond;
            hasFallback = hasFallback || segmentResult.fallbackLine;
            allFallback = allFallback && segmentResult.fallbackLine;
            for (LatLng point : segmentResult.points) {
                appendLatLngDedup(preview.polylinePoints, point);
            }
        }

        preview.calculating = false;
        preview.amapRouteReady = !allFallback;
        preview.partialFallback = hasFallback;
        state.searches.clear();
        if (allFallback) {
            preview.message = "高德路线暂不可用，已按推荐节点顺序展示";
        } else if (hasFallback) {
            preview.message = "部分路段未获取到高德路线，已按节点直线展示";
        } else {
            preview.message = "已生成高德步行路线";
        }

        updateRouteCard(preview);
        drawRouteOnMap(preview);
        if (routeExpandedOverlay != null) {
            showRouteExpandedPanel(false);
        }
    }

    private void drawFallbackPolyline(RoutePreviewData preview) {
        if (preview == null) {
            return;
        }
        drawRouteOnMap(preview);
    }

    private void drawRouteOnMap(RoutePreviewData preview) {
        if (routeAMap == null || preview == null) {
            if (routeNavigationModeActive || routeGuideActive) {
                requestPendingRouteNavigationRefresh();
            }
            updateRouteMapPlaceholder(preview, false);
            return;
        }

        clearRouteMapOverlays();

        List<LatLng> boundsPoints = getRouteDrawablePoints(currentRoute);
        drawRoutePolyline(currentRoute);
        drawRouteMarkers(preview.nodes);

        RouteNode startPoint = preview.routePreviewStartPoint;
        // 只有当“游客当前位置/手动模拟起点”和路线第一个景点不是同一个点时，才额外画一个“我”。
        // 否则地图上会同时出现“我”和路线节点“起”，看起来像两个起点。
        boolean drawSeparateStartMarker = shouldDrawSeparateRouteStartMarker(startPoint, preview.nodes);
        if (drawSeparateStartMarker) {
            LatLng latLng = toLatLng(startPoint);
            if (latLng != null) {
                Marker marker = routeAMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title("当前位置：" + getRouteNodeName(startPoint))
                        .icon(createRouteMarkerIcon("我", Color.rgb(245, 158, 11), BitmapDescriptorFactory.HUE_YELLOW)));
                marker.setObject(startPoint);
                routePreviewMarkers.add(marker);
                boundsPoints.add(latLng);
            }
        }
        Log.d(TAG, "[RouteMarker] currentMe=" + getRouteNodeName(getCurrentMeMarkerNode(currentRoute, preview))
                + ", from=" + getCurrentMeMarkerSource(currentRoute, preview)
                + ", skipStartMe=" + !drawSeparateStartMarker);

        updateRouteMapPlaceholder(preview, boundsPoints.size() > 0);
        fitRouteBounds(boundsPoints);
    }

    private boolean shouldDrawSeparateRouteStartMarker(RouteNode startPoint, List<RouteNode> routeNodes) {
        if (startPoint == null || !startPoint.hasLocation) {
            return false;
        }

        RouteNode currentNode = getCurrentMeMarkerNode(currentRoute, currentRoutePreview);
        if (currentNode != null) {
            return false;
        }

        if (routeNodes == null || routeNodes.size() == 0) {
            return true;
        }

        RouteNode firstNode = routeNodes.get(0);
        return !isSameRouteNodeOrLocation(startPoint, firstNode);
    }

    private boolean isSameRouteNodeOrLocation(RouteNode left, RouteNode right) {
        if (left == null || right == null) {
            return false;
        }
        if (isSameRouteNode(left, right)) {
            return true;
        }
        LatLng leftLatLng = toLatLng(left);
        LatLng rightLatLng = toLatLng(right);
        return leftLatLng != null
                && rightLatLng != null
                && calculateDistanceMeters(leftLatLng, rightLatLng) <= 10f;
    }

    private boolean hasCurrentRouteNodeForMap() {
        return getCurrentMeMarkerNode(currentRoute, currentRoutePreview) != null;
    }

    private RouteNode getCurrentMeMarkerNode(RouteInfo route, RoutePreviewData preview) {
        RouteNode activeNode = getCurrentActiveRouteNode();
        if (activeNode != null) {
            return activeNode;
        }

        RouteNode previewStart = preview == null ? null : preview.routePreviewStartPoint;
        if (previewStart != null && previewStart.hasLocation) {
            return previewStart;
        }

        if (route != null && route.nodes != null && route.nodes.size() > 0) {
            return route.nodes.get(0);
        }

        return null;
    }

    private String getCurrentMeMarkerSource(RouteInfo route, RoutePreviewData preview) {
        RouteNode activeNode = getCurrentActiveRouteNode();
        if (activeNode != null) {
            return "activeRouteNode";
        }

        RouteNode previewStart = preview == null ? null : preview.routePreviewStartPoint;
        if (previewStart != null && previewStart.hasLocation) {
            return "previewStart";
        }

        if (route != null && route.nodes != null && route.nodes.size() > 0) {
            return "firstNode";
        }

        return "none";
    }

    private void drawRouteMarkers(List<RouteNode> nodes) {
        if (routeAMap == null) {
            return;
        }
        List<RouteNode> safeNodes = nodes == null ? new ArrayList<RouteNode>() : nodes;
        RouteNode currentNode = getCurrentMeMarkerNode(currentRoute, currentRoutePreview);
        String currentSource = getCurrentMeMarkerSource(currentRoute, currentRoutePreview);
        boolean currentMeDrawn = false;
        for (int i = 0; i < safeNodes.size(); i++) {
            RouteNode node = safeNodes.get(i);
            if (node == null || !node.hasLocation) {
                continue;
            }
            LatLng latLng = toLatLng(node);
            if (latLng == null) {
                continue;
            }
            float hue;
            int markerColor;
            String markerLabel;
            boolean hasSeparateStart = shouldDrawSeparateRouteStartMarker(
                    currentRoutePreview == null ? null : currentRoutePreview.routePreviewStartPoint,
                    safeNodes
            );
            boolean isCurrentNode = currentNode != null && isSameRouteNodeOrLocation(node, currentNode);
            if (isCurrentNode && !currentMeDrawn) {
                hue = BitmapDescriptorFactory.HUE_YELLOW;
                markerColor = Color.rgb(245, 158, 11);
                markerLabel = "我";
                currentMeDrawn = true;
            } else if (i == 0 && !hasSeparateStart) {
                hue = BitmapDescriptorFactory.HUE_GREEN;
                markerColor = Color.rgb(34, 197, 94);
                markerLabel = "起";
            } else if (i == safeNodes.size() - 1) {
                hue = BitmapDescriptorFactory.HUE_RED;
                markerColor = Color.rgb(239, 68, 68);
                markerLabel = "终";
            } else {
                hue = BitmapDescriptorFactory.HUE_AZURE;
                markerColor = Color.rgb(47, 128, 237);
                markerLabel = String.valueOf(i + 1);
            }
            Marker marker = routeAMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title((i + 1) + ". " + getRouteNodeName(node))
                    .snippet(formatRouteNodeMeta(node))
                    .icon(createRouteMarkerIcon(markerLabel, markerColor, hue)));
            marker.setObject(node);
            routePreviewMarkers.add(marker);
        }

        LatLng currentLatLng = toLatLng(currentNode);
        if (currentLatLng != null && !currentMeDrawn) {
            Marker marker = routeAMap.addMarker(new MarkerOptions()
                    .position(currentLatLng)
                    .title("当前位置：" + getRouteNodeName(currentNode))
                    .icon(createRouteMarkerIcon("我", Color.rgb(245, 158, 11), BitmapDescriptorFactory.HUE_YELLOW)));
            marker.setObject(currentNode);
            routePreviewMarkers.add(marker);
            currentMeDrawn = true;
        }
        Log.d(TAG, "[RouteMarker] currentMe=" + getRouteNodeName(currentNode)
                + ", from=" + currentSource
                + ", drawn=" + currentMeDrawn);
    }

    private com.amap.api.maps.model.BitmapDescriptor createRouteMarkerIcon(String label, int color, float fallbackHue) {
        try {
            int size = dp(30);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            circlePaint.setColor(color);
            canvas.drawCircle(size / 2f, size / 2f, size / 2.5f, circlePaint);

            Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setColor(Color.WHITE);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(2));
            canvas.drawCircle(size / 2f, size / 2f, size / 2.5f - dp(1), strokePaint);

            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextSize(dp(label != null && label.length() > 1 ? 11 : 13));
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(firstNotEmpty(label, "?"), size / 2f, baseline, textPaint);
            return BitmapDescriptorFactory.fromBitmap(bitmap);
        } catch (Throwable e) {
            return BitmapDescriptorFactory.defaultMarker(fallbackHue);
        }
    }

    private void drawRoutePolyline(RouteInfo route) {
        if (routeAMap == null) {
            return;
        }
        List<LatLng> points = getRouteDrawablePoints(route);
        if (points.size() < 2) {
            return;
        }
        boolean fallback = currentRoutePreview != null && currentRoutePreview.partialFallback;
        Polyline polyline = routeAMap.addPolyline(new PolylineOptions()
                .addAll(points)
                .width(dp(6))
                .color(fallback ? Color.rgb(245, 158, 11) : Color.rgb(47, 128, 237)));
        routePreviewPolylines.add(polyline);
    }

    private List<LatLng> getRouteDrawablePoints(RouteInfo route) {
        List<LatLng> result = new ArrayList<>();
        if (route != null && route.rawPolylinePoints != null && route.rawPolylinePoints.size() >= 2) {
            result.addAll(route.rawPolylinePoints);
            return result;
        }
        if (currentRoutePreview != null
                && currentRoutePreview.polylinePoints != null
                && currentRoutePreview.polylinePoints.size() >= 2) {
            result.addAll(currentRoutePreview.polylinePoints);
            return result;
        }
        if (route != null && route.nodes != null) {
            for (RouteNode node : route.nodes) {
                LatLng latLng = toLatLng(node);
                if (latLng != null) {
                    appendLatLngDedup(result, latLng);
                }
            }
        }
        return result;
    }

    private void clearRouteMapOverlays() {
        Log.d(TAG, "[RouteMap] clear overlays polyline=" + routePreviewPolylines.size()
                + ", markers=" + routePreviewMarkers.size());
        for (Polyline polyline : routePreviewPolylines) {
            if (polyline != null) {
                polyline.remove();
            }
        }
        routePreviewPolylines.clear();

        for (Marker marker : routePreviewMarkers) {
            if (marker != null) {
                marker.remove();
            }
        }
        routePreviewMarkers.clear();
    }

    private void updateRouteMapPlaceholder(RoutePreviewData preview, boolean hasMapContent) {
        if (routeMapView != null) {
            routeMapView.setVisibility(hasMapContent ? View.VISIBLE : View.GONE);
        }
        if (routeMapPlaceholderText != null) {
            routeMapPlaceholderText.setVisibility(hasMapContent ? View.GONE : View.VISIBLE);
            routeMapPlaceholderText.setText(preview == null ? "路线地图暂未生成" : preview.message);
        }
        if (routeMapModeEmptyText != null) {
            routeMapModeEmptyText.setVisibility(hasMapContent ? View.GONE : View.VISIBLE);
            String emptyText = preview != null && preview.calculating
                    ? "正在生成高德步行路线..."
                    : "暂无可绘制路线，已为你保留节点讲解顺序";
            routeMapModeEmptyText.setText(hasMapContent ? "" : emptyText);
        }
    }

    private void fitRouteBounds(List<LatLng> points) {
        if (routeAMap == null || points == null || points.size() == 0) {
            return;
        }
        try {
            if (points.size() == 1) {
                routeAMap.moveCamera(CameraUpdateFactory.newLatLngZoom(points.get(0), 16f));
                return;
            }
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            for (LatLng point : points) {
                builder.include(point);
            }
            routeAMap.moveCamera(CameraUpdateFactory.newLatLngBoundsRect(
                    builder.build(),
                    dp(38),
                    dp(38),
                    dp(routeMapModeActive ? 92 : 38),
                    dp(routeMapModeActive ? 260 : 82)
            ));
        } catch (Throwable e) {
            Log.e(TAG, "调整路线预览地图视野失败", e);
        }
    }

    private void updateRouteCard(RoutePreviewData preview) {
        if (preview == null || currentRoute == null) {
            return;
        }
        if (routeCardReasonText != null) {
            routeCardReasonText.setText(formatRecommendedRouteText(currentRoute));
        }
        if (routeCardMetaText != null) {
            routeCardMetaText.setText(formatRouteMeta(currentRoute));
        }
        if (routeCardStartNextText != null) {
            routeCardStartNextText.setText(getRouteMapStatusText());
        }
        if (routeStartButton != null) {
            boolean hasNodes = currentRoute.nodes != null && currentRoute.nodes.size() > 0;
            routeStartButton.setText(routeGuideActive ? "导航中" : "开始导航");
            routeStartButton.setEnabled(!guideEnded && hasNodes && !routeGuideActive && !preview.calculating);
        }
        if (routeCardActionRow != null) {
            routeCardActionRow.setVisibility(View.VISIBLE);
        }
        if (routeMapHolder != null && !routeMapModeActive) {
            routeMapHolder.setVisibility(View.GONE);
        }
        if (routeMapModeActive) {
            updateRouteMapPlaceholder(preview, routeMapView != null && preview.polylinePoints != null && preview.polylinePoints.size() > 0);
            showRouteBottomSheet(currentRoute);
        }
        updateRouteDemoController(currentRoute);
    }

    private boolean canUpdateRouteUi() {
        if (isFinishing()) {
            return false;
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !isDestroyed();
    }

    private RouteNode resolveRoutePreviewStartPoint(RouteInfo route) {
        RouteNode selectedStart = createRouteStartNode();
        if (selectedStart != null && selectedStart.hasLocation) {
            return selectedStart;
        }

        RouteNode intentStart = createIntentCurrentSpotNode();
        if (intentStart != null && intentStart.hasLocation) {
            return intentStart;
        }

        RouteNode firstLocated = findFirstLocatedRouteNode(route);
        if (firstLocated != null) {
            return copyRouteNode(firstLocated);
        }

        if (selectedStart != null) {
            return selectedStart;
        }
        if (intentStart != null) {
            return intentStart;
        }
        if (route != null && route.nodes != null && route.nodes.size() > 0) {
            return copyRouteNode(route.nodes.get(0));
        }
        RouteNode entrance = new RouteNode();
        entrance.name = "景区入口";
        entrance.spotName = "景区入口";
        return entrance;
    }

    private RouteNode createRouteStartNode() {
        RouteNode node = new RouteNode();
        if ("current_spot".equals(routeStartType)) {
            node.id = firstNotEmpty(routeStartCurrentSpotId);
            node.spotId = firstNotEmpty(routeStartCurrentSpotId);
            node.name = firstNotEmpty(routeStartCurrentSpotName, "当前起点");
            node.spotName = node.name;
            node.latitude = routeStartLatitude;
            node.longitude = routeStartLongitude;
            fillNodeLocationFromKnownSpots(node);
            normalizeRouteNodeLocation(node);
            return node;
        }
        node.name = firstNotEmpty(routeStartCurrentSpotName, "景区入口");
        node.spotName = node.name;
        node.latitude = routeStartLatitude;
        node.longitude = routeStartLongitude;
        normalizeRouteNodeLocation(node);
        return node;
    }

    private RouteNode createIntentCurrentSpotNode() {
        String currentName = firstNotEmpty(spotName, scenicName, contextName);
        String currentId = firstNotEmpty(spotId, scenicId);
        if (currentName.length() == 0 && currentId.length() == 0 && latitude.length() == 0 && longitude.length() == 0) {
            return null;
        }
        RouteNode node = new RouteNode();
        node.id = currentId;
        node.spotId = currentId;
        node.scenicId = currentId;
        node.name = firstNotEmpty(currentName, "当前起点");
        node.spotName = node.name;
        node.scenicName = node.name;
        node.latitude = latitude;
        node.longitude = longitude;
        fillNodeLocationFromKnownSpots(node);
        normalizeRouteNodeLocation(node);
        return node;
    }

    private RouteNode createIntentActiveSpotNodeForLeave() {
        if (firstNotEmpty(spotId, spotName).length() == 0) {
            return null;
        }
        return createIntentCurrentSpotNode();
    }

    private RouteNode findFirstLocatedRouteNode(RouteInfo route) {
        if (route == null || route.nodes == null) {
            return null;
        }
        for (RouteNode node : route.nodes) {
            if (node != null && node.hasLocation) {
                return node;
            }
        }
        return null;
    }

    private RouteNode copyRouteNode(RouteNode source) {
        if (source == null) {
            return null;
        }
        RouteNode node = new RouteNode();
        node.id = source.id;
        node.order = source.order;
        node.orderNumber = source.orderNumber;
        node.scenicId = source.scenicId;
        node.spotId = source.spotId;
        node.name = source.name;
        node.spotName = source.spotName;
        node.scenicName = source.scenicName;
        node.guideText = source.guideText;
        node.recommendedStayMin = source.recommendedStayMin;
        node.latitude = source.latitude;
        node.longitude = source.longitude;
        node.latitudeValue = source.latitudeValue;
        node.longitudeValue = source.longitudeValue;
        node.hasLocation = source.hasLocation;
        return node;
    }

    private List<RouteNode> buildRoutePlanningNodes(RouteNode startPoint, RouteInfo route) {
        List<RouteNode> result = new ArrayList<>();
        if (startPoint != null && startPoint.hasLocation) {
            result.add(startPoint);
        }
        if (route != null && route.nodes != null) {
            for (RouteNode node : route.nodes) {
                if (node == null) {
                    continue;
                }
                if (startPoint != null && isSameRouteNode(startPoint, node)) {
                    if (result.size() == 0 && node.hasLocation) {
                        result.add(node);
                    }
                    continue;
                }
                result.add(node);
            }
        }
        return result;
    }

    private boolean isSameRouteNode(RouteNode left, RouteNode right) {
        if (left == null || right == null) {
            return false;
        }
        String leftId = firstNotEmpty(left.id, left.spotId, left.scenicId);
        String rightId = firstNotEmpty(right.id, right.spotId, right.scenicId);
        if (leftId.length() > 0 && leftId.equals(rightId)) {
            return true;
        }
        String leftName = getRouteNodeName(left);
        String rightName = getRouteNodeName(right);
        return leftName.length() > 0 && leftName.equals(rightName);
    }

    private boolean routeContainsNode(RouteInfo route, RouteNode target) {
        if (route == null || route.nodes == null || target == null) {
            return false;
        }
        for (RouteNode node : route.nodes) {
            if (isSameRouteNode(node, target)) {
                return true;
            }
        }
        return false;
    }

    private List<RouteNode> filterLocatedNodes(List<RouteNode> nodes) {
        List<RouteNode> result = new ArrayList<>();
        if (nodes == null) {
            return result;
        }
        for (RouteNode node : nodes) {
            if (node != null && node.hasLocation) {
                result.add(node);
            }
        }
        return result;
    }

    private int countLocatedNodes(List<RouteNode> nodes) {
        return filterLocatedNodes(nodes).size();
    }

    private List<LatLng> collectLocatedLatLngs(List<RouteNode> nodes) {
        List<LatLng> result = new ArrayList<>();
        if (nodes == null) {
            return result;
        }
        for (RouteNode node : nodes) {
            LatLng latLng = toLatLng(node);
            if (latLng != null) {
                appendLatLngDedup(result, latLng);
            }
        }
        return result;
    }

    private LatLng toLatLng(RouteNode node) {
        if (node == null) {
            return null;
        }
        normalizeRouteNodeLocation(node);
        if (!node.hasLocation) {
            return null;
        }
        return new LatLng(node.latitudeValue, node.longitudeValue);
    }

    private void appendLatLngDedup(List<LatLng> points, LatLng point) {
        if (points == null || point == null) {
            return;
        }
        if (points.size() > 0) {
            LatLng last = points.get(points.size() - 1);
            if (Math.abs(last.latitude - point.latitude) < 0.0000001d
                    && Math.abs(last.longitude - point.longitude) < 0.0000001d) {
                return;
            }
        }
        points.add(point);
    }

    private float calculateDistanceMeters(LatLng from, LatLng to) {
        if (from == null || to == null) {
            return 0f;
        }
        double earthRadius = 6371000.0;
        double dLat = Math.toRadians(to.latitude - from.latitude);
        double dLon = Math.toRadians(to.longitude - from.longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(from.latitude)) * Math.cos(Math.toRadians(to.latitude))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (float) (earthRadius * c);
    }

    private long estimateWalkDurationSecond(float distanceMeter) {
        if (distanceMeter <= 0) {
            return 0L;
        }
        return Math.max(60L, (long) (distanceMeter / 1.15f));
    }

    private void renderRouteCard(final RouteInfo route) {
        if (route == null || routeCardContainer == null) {
            return;
        }

        updateRouteCardLayoutForState();

        if (answerBoxContainer != null) {
            answerBoxContainer.setVisibility(View.VISIBLE);
        }

        routeCardExpanded = false;
        if (routeNodesScrollView != null) {
            LinearLayout.LayoutParams nodesParams = (LinearLayout.LayoutParams) routeNodesScrollView.getLayoutParams();
            if (nodesParams != null) {
                nodesParams.height = dp(0);
                routeNodesScrollView.setLayoutParams(nodesParams);
            }
            routeNodesScrollView.setVisibility(View.GONE);
        }

        routeCardTitleText.setText(formatRouteCardTitle(route));
        routeCardReasonText.setText(formatRecommendedRouteText(route));
        routeCardReasonText.setVisibility(View.VISIBLE);
        routeCardMetaText.setText(formatRouteMeta(route));
        if (routeCardStartNextText != null) {
            routeCardStartNextText.setText(getRouteMapStatusText());
            routeCardStartNextText.setVisibility(View.VISIBLE);
        }
        if (routeMapHolder != null) {
            routeMapHolder.setVisibility(View.GONE);
        }
        routeExpandButton.setText("查看路线");
        if (routeCloseButton != null) {
            routeCloseButton.setVisibility(View.GONE);
        }
        if (routeCardActionRow != null) {
            routeCardActionRow.setVisibility(View.VISIBLE);
        }
        if (routeChangeStartButton != null) {
            routeChangeStartButton.setVisibility(View.GONE);
        }
        if (routeStartButton != null) {
            routeStartButton.setText(routeGuideActive ? "导航中" : "开始导航");
            routeStartButton.setEnabled(!guideEnded
                    && route.nodes != null
                    && route.nodes.size() > 0
                    && !routeGuideActive
                    && (currentRoutePreview == null || !currentRoutePreview.calculating));
        }
        updateRouteDemoController(route);

        routeNodesContainer.removeAllViews();
        if (route.nodes.size() == 0) {
            TextView emptyText = new TextView(this);
            emptyText.setText("暂未生成实景地图，已为你保留节点讲解顺序");
            emptyText.setTextColor(Color.rgb(112, 128, 145));
            emptyText.setTextSize(12);
            emptyText.setGravity(Gravity.CENTER);
            routeNodesContainer.addView(emptyText, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));
            updateRouteDemoController(route);
            return;
        }

        for (int i = 0; i < route.nodes.size(); i++) {
            RouteNode node = route.nodes.get(i);
            routeNodesContainer.addView(createRouteNodeView(route, node));
        }

        if (routeExpandedOverlay != null) {
            showRouteExpandedPanel(false);
        }
    }

    private View createRouteNodeView(final RouteInfo route, final RouteNode node) {
        LinearLayout nodeView = new LinearLayout(this);
        nodeView.setOrientation(LinearLayout.VERTICAL);
        nodeView.setPadding(dp(10), dp(7), dp(10), dp(7));
        nodeView.setBackground(createStrokeRoundBg(
                Color.rgb(247, 251, 255),
                Color.argb(54, 47, 128, 237),
                dp(12)
        ));
        nodeView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                writeRouteCardEvent("route_spot_click", route, node);
                showToast("已选择：" + firstNotEmpty(node.spotName, node.scenicName, "路线节点"));
            }
        });

        TextView titleText = new TextView(this);
        titleText.setText(node.order + ". " + firstNotEmpty(node.spotName, node.scenicName, "未命名景点"));
        titleText.setTextColor(Color.rgb(24, 48, 72));
        titleText.setTextSize(13);
        titleText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nodeView.addView(titleText);

        TextView guideTextView = new TextView(this);
        guideTextView.setText(firstNotEmpty(node.guideText, "暂无讲解提示"));
        guideTextView.setTextColor(Color.rgb(65, 83, 105));
        guideTextView.setTextSize(12);
        guideTextView.setLineSpacing(dp(3), 1.0f);
        guideTextView.setPadding(0, dp(3), 0, 0);
        nodeView.addView(guideTextView);

        TextView metaText = new TextView(this);
        metaText.setText(formatRouteNodeMeta(node));
        metaText.setTextColor(Color.rgb(112, 128, 145));
        metaText.setTextSize(11);
        metaText.setPadding(0, dp(3), 0, 0);
        nodeView.addView(metaText);

        LinearLayout.LayoutParams nodeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        nodeParams.bottomMargin = dp(6);
        nodeView.setLayoutParams(nodeParams);
        return nodeView;
    }

    private void showRouteExpandedPanel(boolean writeEvent) {
        if (currentRoute == null || rootLayout == null) {
            return;
        }

        dismissRouteExpandedPanel();
        if (writeEvent) {
            writeRouteCardEvent("map_card_expand", currentRoute, null);
        }

        final RouteInfo route = currentRoute;
        routeExpandedOverlay = new FrameLayout(this);
        routeExpandedOverlay.setClickable(true);
        routeExpandedOverlay.setBackgroundColor(Color.argb(118, 0, 0, 0));
        routeExpandedOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismissRouteExpandedPanel();
            }
        });

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(14), dp(16), dp(14));
        panel.setClickable(true);
        panel.setBackground(createRoundBg(Color.WHITE, dp(24)));
        panel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 消费面板内部点击，避免触发遮罩关闭。
            }
        });

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(formatRouteCardTitle(route));
        title.setTextColor(Color.rgb(18, 48, 78));
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setSingleLine(true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button collapseButton = createMiniActionButton("收起");
        Button closeButton = createMiniActionButton("关闭");
        LinearLayout.LayoutParams collapseParams = new LinearLayout.LayoutParams(dp(54), dp(30));
        collapseParams.rightMargin = dp(6);
        titleRow.addView(collapseButton, collapseParams);
        titleRow.addView(closeButton, new LinearLayout.LayoutParams(dp(54), dp(30)));
        panel.addView(titleRow);

        TextView status = new TextView(this);
        status.setText(getRouteMapStatusText());
        status.setTextColor(Color.rgb(65, 83, 105));
        status.setTextSize(12);
        status.setPadding(0, dp(6), 0, 0);
        panel.addView(status);

        TextView meta = new TextView(this);
        meta.setText(formatRouteMeta(route));
        meta.setTextColor(Color.rgb(95, 112, 130));
        meta.setTextSize(12);
        meta.setPadding(0, dp(4), 0, 0);
        panel.addView(meta);

        LinearLayout startRow = new LinearLayout(this);
        startRow.setOrientation(LinearLayout.HORIZONTAL);
        startRow.setGravity(Gravity.CENTER_VERTICAL);
        startRow.setPadding(0, dp(8), 0, dp(8));

        TextView startText = new TextView(this);
        startText.setText("当前起点：" + getRoutePreviewStartName());
        startText.setTextColor(Color.rgb(42, 60, 80));
        startText.setTextSize(12);
        startText.setSingleLine(true);
        startRow.addView(startText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button changeStartButton = createMiniActionButton("切换起点");
        startRow.addView(changeStartButton, new LinearLayout.LayoutParams(dp(78), dp(30)));
        panel.addView(startRow);

        FrameLayout expandedMapHolder = new FrameLayout(this);
        expandedMapHolder.setBackground(createStrokeRoundBg(
                Color.rgb(236, 246, 255),
                Color.argb(70, 47, 128, 237),
                dp(14)
        ));
        TextView expandedMapText = new TextView(this);
        expandedMapText.setText(currentRoutePreview == null ? "正在生成高德步行路线..." : currentRoutePreview.message);
        expandedMapText.setTextColor(Color.rgb(47, 128, 237));
        expandedMapText.setTextSize(12);
        expandedMapText.setGravity(Gravity.CENTER);
        expandedMapHolder.addView(expandedMapText, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        attachRouteMapToExpandedHolder(expandedMapHolder, expandedMapText);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(170));
        previewParams.bottomMargin = dp(8);
        panel.addView(expandedMapHolder, previewParams);

        ScrollView nodesScroll = new ScrollView(this);
        LinearLayout nodesContainer = new LinearLayout(this);
        nodesContainer.setOrientation(LinearLayout.VERTICAL);
        nodesScroll.addView(nodesContainer, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        if (route.nodes == null || route.nodes.size() == 0) {
            TextView empty = new TextView(this);
            empty.setText("暂未生成实景地图，已为你保留节点讲解顺序");
            empty.setTextColor(Color.rgb(112, 128, 145));
            empty.setTextSize(12);
            empty.setGravity(Gravity.CENTER);
            nodesContainer.addView(empty, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(70)));
        } else {
            for (int i = 0; i < route.nodes.size(); i++) {
                nodesContainer.addView(createRouteNodeView(route, route.nodes.get(i)));
            }
        }
        panel.addView(nodesScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        Button startButton = new Button(this);
        startButton.setText(routeGuideActive ? "导航中" : "开始导航");
        startButton.setTextSize(14);
        startButton.setTextColor(Color.WHITE);
        startButton.setAllCaps(false);
        startButton.setBackground(createRoundBg(Color.rgb(47, 128, 237), dp(18)));
        startButton.setEnabled(!guideEnded
                && !routeGuideActive
                && route.nodes != null
                && route.nodes.size() > 0
                && (currentRoutePreview == null || !currentRoutePreview.calculating));
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        startParams.topMargin = dp(10);
        panel.addView(startButton, startParams);

        collapseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismissRouteExpandedPanel();
            }
        });
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                writeRouteCardEvent("map_card_close", route, null);
                dismissRouteExpandedPanel();
                hideRouteCard();
            }
        });
        changeStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleRouteStartSwitchClick();
            }
        });
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleRouteStartGuideClick();
            }
        });

        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        rootLayout.addView(routeExpandedOverlay, overlayParams);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                calculateRouteCardExpandedHeight()
        );
        panelParams.gravity = Gravity.BOTTOM;
        panelParams.leftMargin = dp(12);
        panelParams.rightMargin = dp(12);
        panelParams.bottomMargin = dp(12);
        routeExpandedOverlay.addView(panel, panelParams);
        if (routeMapView != null && currentRoutePreview != null) {
            routeMapView.post(new Runnable() {
                @Override
                public void run() {
                    drawRouteOnMap(currentRoutePreview);
                }
            });
        }
    }

    private void dismissRouteExpandedPanel() {
        restoreRouteMapToCardHolder();
        if (routeExpandedOverlay != null && rootLayout != null) {
            rootLayout.removeView(routeExpandedOverlay);
        }
        routeExpandedOverlay = null;
        routeCardExpanded = false;
    }

    private void attachRouteMapToExpandedHolder(FrameLayout expandedMapHolder, TextView fallbackText) {
        if (routeMapView == null || expandedMapHolder == null) {
            if (fallbackText != null) {
                fallbackText.setVisibility(View.VISIBLE);
            }
            return;
        }
        ViewGroup parent = (ViewGroup) routeMapView.getParent();
        if (parent != null) {
            parent.removeView(routeMapView);
        }
        expandedMapHolder.addView(routeMapView, 0, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        boolean hasContent = currentRoutePreview != null
                && currentRoutePreview.polylinePoints != null
                && currentRoutePreview.polylinePoints.size() > 0;
        routeMapView.setVisibility(hasContent ? View.VISIBLE : View.GONE);
        if (fallbackText != null) {
            fallbackText.setVisibility(hasContent ? View.GONE : View.VISIBLE);
        }
        if (currentRoutePreview != null) {
            drawRouteOnMap(currentRoutePreview);
        }
    }

    private void restoreRouteMapToCardHolder() {
        if (routeMapView == null || routeMapHolder == null) {
            return;
        }
        ViewGroup parent = (ViewGroup) routeMapView.getParent();
        if (parent != routeMapHolder) {
            if (parent != null) {
                parent.removeView(routeMapView);
            }
            routeMapHolder.addView(routeMapView, 0, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
        }
        if (currentRoutePreview != null) {
            drawRouteOnMap(currentRoutePreview);
        }
    }

    private void enterRouteMapMode(RouteInfo route) {
        if (route == null || rootLayout == null) {
            showToast("暂无可查看的路线");
            return;
        }
        if (route.nodes == null || route.nodes.size() == 0) {
            showToast("暂无可展示路线");
            return;
        }

        currentRoute = route;
        dismissRouteExpandedPanel();
        exitRouteMapModeInternal(false);
        routeNavigationModeActive = false;
        routeMapModeActive = true;
        Log.d(TAG, "[RouteMap] enterPreview route=" + formatRouteCardTitle(route)
                + ", nodes=" + (route.nodes == null ? 0 : route.nodes.size()));

        routeMapModeOverlay = new FrameLayout(this);
        routeMapModeOverlay.setBackgroundColor(Color.rgb(231, 238, 244));
        routeMapModeOverlay.setClickable(true);

        FrameLayout mapArea = new FrameLayout(this);
        mapArea.setBackgroundColor(Color.rgb(224, 232, 238));
        routeMapModeEmptyText = new TextView(this);
        routeMapModeEmptyText.setText("正在生成高德步行路线...");
        routeMapModeEmptyText.setGravity(Gravity.CENTER);
        routeMapModeEmptyText.setTextSize(14);
        routeMapModeEmptyText.setTextColor(Color.rgb(82, 98, 118));
        mapArea.addView(routeMapModeEmptyText, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        attachRouteMapToExpandedHolder(mapArea, routeMapModeEmptyText);
        routeMapModeOverlay.addView(mapArea, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout topBar = new LinearLayout(this);
        routeMapTopBar = topBar;
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        topBar.setBackgroundColor(Color.argb(238, 255, 255, 255));

        Button backButton = createMiniActionButton("返回导览");
        topBar.addView(backButton, new LinearLayout.LayoutParams(dp(82), dp(36)));

        routeMapModeTitleText = new TextView(this);
        routeMapModeTitleText.setText(formatRouteCardTitle(route));
        routeMapModeTitleText.setTextColor(Color.rgb(24, 48, 72));
        routeMapModeTitleText.setTextSize(16);
        routeMapModeTitleText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        routeMapModeTitleText.setGravity(Gravity.CENTER);
        topBar.addView(routeMapModeTitleText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView spacer = new TextView(this);
        topBar.addView(spacer, new LinearLayout.LayoutParams(dp(82), dp(1)));

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(56)
        );
        topParams.gravity = Gravity.TOP;
        routeMapModeOverlay.addView(topBar, topParams);

        LinearLayout sideTools = new LinearLayout(this);
        routeMapSideTools = sideTools;
        sideTools.setOrientation(LinearLayout.VERTICAL);
        sideTools.setPadding(0, 0, 0, 0);
        Button overviewButton = createMiniActionButton("全览");
        Button refreshButton = createMiniActionButton("刷新");
        Button locateButton = createMiniActionButton("定位");
        sideTools.addView(overviewButton, new LinearLayout.LayoutParams(dp(52), dp(34)));
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(dp(52), dp(34));
        refreshParams.topMargin = dp(8);
        sideTools.addView(refreshButton, refreshParams);
        LinearLayout.LayoutParams locateParams = new LinearLayout.LayoutParams(dp(52), dp(34));
        locateParams.topMargin = dp(8);
        sideTools.addView(locateButton, locateParams);
        FrameLayout.LayoutParams sideParams = new FrameLayout.LayoutParams(
                dp(52),
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        sideParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        sideParams.rightMargin = dp(12);
        routeMapModeOverlay.addView(sideTools, sideParams);

        LinearLayout bottomSheet = createRouteBottomSheetView();
        routeMapBottomSheetView = bottomSheet;
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(238)
        );
        bottomParams.gravity = Gravity.BOTTOM;
        bottomParams.leftMargin = dp(10);
        bottomParams.rightMargin = dp(10);
        bottomParams.bottomMargin = dp(10);
        routeMapModeOverlay.addView(bottomSheet, bottomParams);

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exitRouteMapMode();
            }
        });
        overviewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fitRouteBounds(getRouteDrawablePoints(currentRoute));
            }
        });
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentRoute != null) {
                    buildRoutePreviewWithAmap(currentRoute);
                }
            }
        });
        locateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                moveToCurrentRouteLocation();
            }
        });

        rootLayout.addView(routeMapModeOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        if (voiceGuidePanel != null) {
            voiceGuidePanel.setVisibility(View.GONE);
        }

        renderRouteOnMap(route);
        showRouteBottomSheet(route);
        trackRouteEvent("route_map_open", route, null);
    }

    private LinearLayout createRouteBottomSheetView() {
        LinearLayout bottomSheet = new LinearLayout(this);
        bottomSheet.setOrientation(LinearLayout.VERTICAL);
        bottomSheet.setPadding(dp(16), dp(12), dp(16), dp(12));
        bottomSheet.setBackground(createRoundBg(Color.WHITE, dp(22)));

        routeBottomTitleText = new TextView(this);
        routeBottomTitleText.setTextColor(Color.rgb(18, 48, 78));
        routeBottomTitleText.setTextSize(16);
        routeBottomTitleText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        routeBottomTitleText.setSingleLine(true);
        bottomSheet.addView(routeBottomTitleText);

        routeBottomSummaryText = new TextView(this);
        routeBottomSummaryText.setTextColor(Color.rgb(65, 83, 105));
        routeBottomSummaryText.setTextSize(12);
        routeBottomSummaryText.setSingleLine(true);
        routeBottomSummaryText.setPadding(0, dp(4), 0, 0);
        bottomSheet.addView(routeBottomSummaryText);

        routeBottomMetaText = new TextView(this);
        routeBottomMetaText.setTextColor(Color.rgb(47, 128, 237));
        routeBottomMetaText.setTextSize(12);
        routeBottomMetaText.setPadding(0, dp(4), 0, dp(6));
        bottomSheet.addView(routeBottomMetaText);

        HorizontalScrollView nodeScroll = new HorizontalScrollView(this);
        nodeScroll.setHorizontalScrollBarEnabled(false);
        routeBottomNodesContainer = new LinearLayout(this);
        routeBottomNodesContainer.setOrientation(LinearLayout.HORIZONTAL);
        nodeScroll.addView(routeBottomNodesContainer, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.MATCH_PARENT
        ));
        bottomSheet.addView(nodeScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
        ));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setPadding(0, dp(10), 0, 0);

        Button startButton = new Button(this);
        startButton.setText("开始导航");
        startButton.setTextSize(14);
        startButton.setTextColor(Color.WHITE);
        startButton.setAllCaps(false);
        startButton.setBackground(createRoundBg(Color.rgb(47, 128, 237), dp(18)));

        Button backButton = createMiniActionButton("返回导览");
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(0, dp(40), 1.4f);
        startParams.rightMargin = dp(8);
        actionRow.addView(startButton, startParams);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        backParams.leftMargin = dp(8);
        actionRow.addView(backButton, backParams);
        bottomSheet.addView(actionRow);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startRouteNavigation(currentRoute);
            }
        });
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exitRouteMapMode();
            }
        });

        return bottomSheet;
    }

    private void exitRouteMapMode() {
        exitRouteMapModeInternal(true);
    }

    private void exitRouteMapModeInternal(boolean restoreGuideUi) {
        boolean wasRouteMapModeActive = routeMapModeActive;
        exitNavigationMode();
        if (routeMapModeOverlay != null && rootLayout != null) {
            rootLayout.removeView(routeMapModeOverlay);
        }
        routeMapModeOverlay = null;
        routeMapTopBar = null;
        routeMapSideTools = null;
        routeMapBottomSheetView = null;
        routeMapModeTitleText = null;
        routeMapModeEmptyText = null;
        routeBottomTitleText = null;
        routeBottomSummaryText = null;
        routeBottomMetaText = null;
        routeBottomNodesContainer = null;
        routeMapModeActive = false;
        restoreRouteMapToCardHolder();
        if (restoreGuideUi && voiceGuidePanel != null) {
            voiceGuidePanel.setVisibility(View.VISIBLE);
        }
        if (currentRoute != null) {
            renderRouteCard(currentRoute);
        }
        if (wasRouteMapModeActive && restoreGuideUi && currentRoute != null) {
            trackRouteEvent("route_map_close", currentRoute, null);
        }
    }

    private void renderRouteOnMap(RouteInfo route) {
        if (route == null) {
            return;
        }
        if (route.preview == null) {
            route.preview = currentRoutePreview == null ? createInitialRoutePreview(route) : currentRoutePreview;
        }
        currentRoutePreview = route.preview;
        drawRouteOnMap(currentRoutePreview);
        showRouteBottomSheet(route);
    }

    private void showRouteBottomSheet(final RouteInfo route) {
        if (route == null || routeBottomTitleText == null) {
            return;
        }
        routeBottomTitleText.setText(formatRouteCardTitle(route));
        if (routeBottomSummaryText != null) {
            routeBottomSummaryText.setText(formatRecommendedRouteText(route));
        }
        if (routeBottomMetaText != null) {
            routeBottomMetaText.setText(formatRouteMeta(route));
        }
        if (routeBottomNodesContainer != null) {
            routeBottomNodesContainer.removeAllViews();
            if (route.nodes == null || route.nodes.size() == 0) {
                TextView empty = new TextView(this);
                empty.setText("暂无路线节点");
                empty.setTextColor(Color.rgb(112, 128, 145));
                empty.setTextSize(12);
                routeBottomNodesContainer.addView(empty, new LinearLayout.LayoutParams(dp(120), dp(42)));
            } else {
                for (int i = 0; i < route.nodes.size(); i++) {
                    final RouteNode node = route.nodes.get(i);
                    final int nodeIndex = i;
                    boolean selected = currentRouteNodeIndex == i;
                    TextView nodeChip = new TextView(this);
                    nodeChip.setText((i + 1) + ". " + getRouteNodeName(node));
                    nodeChip.setTextSize(12);
                    nodeChip.setTextColor(selected ? Color.WHITE : Color.rgb(35, 100, 170));
                    nodeChip.setGravity(Gravity.CENTER);
                    nodeChip.setSingleLine(true);
                    nodeChip.setPadding(dp(10), 0, dp(10), 0);
                    nodeChip.setBackground(createStrokeRoundBg(
                            selected ? Color.rgb(47, 128, 237) : Color.rgb(236, 246, 255),
                            selected ? Color.rgb(47, 128, 237) : Color.argb(80, 47, 128, 237),
                            dp(16)
                    ));
                    nodeChip.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            currentRouteNodeIndex = nodeIndex;
                            onRouteNodeClick(node);
                        }
                    });
                    LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(dp(126), dp(36));
                    chipParams.rightMargin = dp(8);
                    routeBottomNodesContainer.addView(nodeChip, chipParams);
                }
            }
        }
    }

    private void startRouteNavigation(RouteInfo route) {
        if (route == null || route.nodes == null || route.nodes.size() == 0) {
            showToast("暂无可开始的路线");
            return;
        }
        currentRoute = route;
        boolean firstTime = !routeGuideActive || activeRouteNodes.size() == 0 || currentRouteNodeIndex < 0;
        if (!initializeRouteNavigationState(route)) {
            showToast("当前路线没有可导航的节点");
            return;
        }
        if (!routeMapModeActive) {
            enterRouteMapMode(route);
        }
        if (!routeMapModeActive || routeMapModeOverlay == null) {
            showToast("地图暂不可用，已按推荐节点顺序展示");
            return;
        }
        trackRouteEvent("navigation_start", route, null);
        if (routeAMap == null) {
            requestPendingRouteNavigationRefresh();
        }
        renderRouteCard(route);
        updateRouteDemoController(route);
        Log.d(TAG, "[RouteNav] start firstTime=" + firstTime
                + ", currentIndex=" + currentRouteNodeIndex
                + ", current=" + getRouteNodeName(getCurrentNavigationNode(route))
                + ", next=" + getRouteNodeName(getNextNavigationNode(route)));
        enterNavigationMode(route);
    }

    private boolean initializeRouteNavigationState(RouteInfo route) {
        if (route == null || route.nodes == null || route.nodes.size() == 0) {
            return false;
        }

        rebuildActiveRouteNodes(route);
        if (activeRouteNodes.size() == 0) {
            return false;
        }

        int index = resolveRouteNavigationStartIndex(route);
        if (index < 0) {
            index = 0;
        }
        if (index >= activeRouteNodes.size()) {
            index = activeRouteNodes.size() - 1;
        }

        routeGuideActive = true;
        currentRouteNodeIndex = index;
        routeDemoNodeIndex = index;
        currentDemoRouteNode = activeRouteNodes.get(index);
        applyDemoNodeToCurrentContext(currentDemoRouteNode);
        return true;
    }

    private void rebuildActiveRouteNodes(RouteInfo route) {
        activeRouteNodes.clear();
        if (route == null || route.nodes == null) {
            return;
        }
        for (RouteNode node : route.nodes) {
            if (node == null) {
                continue;
            }
            if (activeRouteNodes.size() > 0
                    && isSameRouteNodeOrLocation(activeRouteNodes.get(activeRouteNodes.size() - 1), node)) {
                continue;
            }
            activeRouteNodes.add(node);
        }
    }

    private int resolveRouteNavigationStartIndex(RouteInfo route) {
        if (route == null || route.nodes == null || route.nodes.size() == 0) {
            return -1;
        }

        int currentDemoIndex = findRouteNodeIndex(activeRouteNodes, currentDemoRouteNode);
        if (currentDemoIndex >= 0) {
            return currentDemoIndex;
        }

        if (routeGuideActive && currentRouteNodeIndex >= 0 && currentRouteNodeIndex < activeRouteNodes.size()) {
            return currentRouteNodeIndex;
        }

        RouteNode previewStart = currentRoutePreview == null ? null : currentRoutePreview.routePreviewStartPoint;
        int previewStartIndex = findRouteNodeIndex(activeRouteNodes, previewStart);
        if (previewStartIndex >= 0) {
            return previewStartIndex;
        }

        return 0;
    }

    private int findRouteNodeIndex(List<RouteNode> nodes, RouteNode target) {
        if (nodes == null || target == null) {
            return -1;
        }
        for (int i = 0; i < nodes.size(); i++) {
            RouteNode node = nodes.get(i);
            if (node != null && isSameRouteNodeOrLocation(node, target)) {
                return i;
            }
        }
        return -1;
    }

    private void enterNavigationMode(final RouteInfo route) {
        if (route == null || routeMapModeOverlay == null) {
            return;
        }

        exitNavigationMode();
        routeNavigationModeActive = true;
        if (currentRouteNodeIndex < 0) {
            currentRouteNodeIndex = resolveNavigationNodeIndex(route);
        }

        if (routeMapTopBar != null) routeMapTopBar.setVisibility(View.GONE);
        if (routeMapSideTools != null) routeMapSideTools.setVisibility(View.GONE);
        if (routeMapBottomSheetView != null) routeMapBottomSheetView.setVisibility(View.GONE);

        routeNavigationOverlay = new FrameLayout(this);
        routeNavigationOverlay.setClickable(false);

        LinearLayout instructionCard = new LinearLayout(this);
        instructionCard.setOrientation(LinearLayout.HORIZONTAL);
        instructionCard.setGravity(Gravity.CENTER_VERTICAL);
        instructionCard.setPadding(dp(14), dp(10), dp(14), dp(10));
        instructionCard.setBackground(createRoundBg(Color.argb(236, 20, 24, 30), dp(16)));

        TextView navIcon = new TextView(this);
        navIcon.setText("导航");
        navIcon.setTextColor(Color.WHITE);
        navIcon.setTextSize(13);
        navIcon.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        navIcon.setGravity(Gravity.CENTER);
        navIcon.setBackground(createRoundBg(Color.rgb(47, 128, 237), dp(14)));
        instructionCard.addView(navIcon, new LinearLayout.LayoutParams(dp(46), dp(30)));

        LinearLayout instructionTextColumn = new LinearLayout(this);
        instructionTextColumn.setOrientation(LinearLayout.VERTICAL);
        instructionTextColumn.setPadding(dp(10), 0, dp(8), 0);

        navigationInstructionText = new TextView(this);
        navigationInstructionText.setTextColor(Color.WHITE);
        navigationInstructionText.setTextSize(17);
        navigationInstructionText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        instructionTextColumn.addView(navigationInstructionText);

        navigationSubText = new TextView(this);
        navigationSubText.setTextColor(Color.rgb(210, 222, 235));
        navigationSubText.setTextSize(12);
        navigationSubText.setPadding(0, dp(3), 0, 0);
        instructionTextColumn.addView(navigationSubText);
        instructionCard.addView(instructionTextColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        navigationStatusText = new TextView(this);
        navigationStatusText.setTextColor(Color.rgb(134, 239, 172));
        navigationStatusText.setTextSize(12);
        navigationStatusText.setGravity(Gravity.RIGHT);
        instructionCard.addView(navigationStatusText, new LinearLayout.LayoutParams(dp(82), LinearLayout.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams instructionParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(76)
        );
        instructionParams.gravity = Gravity.TOP;
        instructionParams.leftMargin = dp(10);
        instructionParams.rightMargin = dp(10);
        instructionParams.topMargin = dp(14);
        routeNavigationOverlay.addView(instructionCard, instructionParams);

        LinearLayout sideButtons = new LinearLayout(this);
        sideButtons.setOrientation(LinearLayout.VERTICAL);
        Button overviewButton = createMiniActionButton("全览");
        Button refreshButton = createMiniActionButton("刷新");
        Button explainButton = createMiniActionButton("讲解当前点");
        sideButtons.addView(overviewButton, new LinearLayout.LayoutParams(dp(78), dp(34)));
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(dp(78), dp(34));
        refreshParams.topMargin = dp(8);
        sideButtons.addView(refreshButton, refreshParams);
        LinearLayout.LayoutParams explainParams = new LinearLayout.LayoutParams(dp(78), dp(34));
        explainParams.topMargin = dp(8);
        sideButtons.addView(explainButton, explainParams);

        FrameLayout.LayoutParams sideParams = new FrameLayout.LayoutParams(dp(78), FrameLayout.LayoutParams.WRAP_CONTENT);
        sideParams.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        sideParams.leftMargin = dp(12);
        routeNavigationOverlay.addView(sideButtons, sideParams);

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(dp(12), dp(10), dp(12), dp(10));
        bottomBar.setBackground(createRoundBg(Color.argb(238, 28, 34, 42), dp(18)));

        Button exitButton = createMiniActionButton("退出地图");
        Button continueButton = new Button(this);
        continueButton.setText("继续导览");
        continueButton.setTextSize(14);
        continueButton.setTextColor(Color.WHITE);
        continueButton.setAllCaps(false);
        continueButton.setBackground(createRoundBg(Color.rgb(47, 128, 237), dp(18)));
        Button fullButton = createMiniActionButton("全览");

        LinearLayout.LayoutParams exitParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        exitParams.rightMargin = dp(8);
        bottomBar.addView(exitButton, exitParams);
        LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(0, dp(40), 1.15f);
        continueParams.leftMargin = dp(4);
        continueParams.rightMargin = dp(4);
        bottomBar.addView(continueButton, continueParams);
        LinearLayout.LayoutParams fullParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        fullParams.leftMargin = dp(8);
        bottomBar.addView(fullButton, fullParams);

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(78)
        );
        bottomParams.gravity = Gravity.BOTTOM;
        bottomParams.leftMargin = dp(10);
        bottomParams.rightMargin = dp(10);
        bottomParams.bottomMargin = dp(10);
        routeNavigationOverlay.addView(bottomBar, bottomParams);

        overviewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fitRouteBounds(getRouteDrawablePoints(route));
            }
        });
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                buildRoutePreviewWithAmap(route);
                drawNavigationRouteState(route);
            }
        });
        explainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                askGuideForCurrentNode(getCurrentNavigationNode(route));
            }
        });
        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exitRouteMapMode();
            }
        });
        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exitRouteMapMode();
            }
        });
        fullButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fitRouteBounds(getRouteDrawablePoints(route));
            }
        });

        routeMapModeOverlay.addView(routeNavigationOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        RouteNode currentNode = getCurrentNavigationNode(route);
        RouteNode nextNode = getNextNavigationNode(route);
        updateNavigationInstruction(currentNode, nextNode);
        drawNavigationRouteState(route);
        updateRouteDemoController(route);
    }

    private void exitNavigationMode() {
        if (routeNavigationOverlay != null && routeMapModeOverlay != null) {
            routeMapModeOverlay.removeView(routeNavigationOverlay);
        }
        routeNavigationOverlay = null;
        navigationInstructionText = null;
        navigationSubText = null;
        navigationStatusText = null;
        routeNavigationModeActive = false;
        if (routeMapModeActive) {
            if (routeMapTopBar != null) routeMapTopBar.setVisibility(View.VISIBLE);
            if (routeMapSideTools != null) routeMapSideTools.setVisibility(View.VISIBLE);
            if (routeMapBottomSheetView != null) routeMapBottomSheetView.setVisibility(View.VISIBLE);
            if (currentRoutePreview != null) {
                drawRouteOnMap(currentRoutePreview);
            }
        }
    }

    private void updateNavigationInstruction(RouteNode currentNode, RouteNode nextNode) {
        if (navigationInstructionText != null) {
            navigationInstructionText.setText("请前往下一个景点");
        }
        if (navigationSubText != null) {
            String nextName = nextNode == null ? "路线终点" : getRouteNodeName(nextNode);
            String distanceText = formatDistanceBetweenNodes(currentNode, nextNode);
            navigationSubText.setText("下一站：" + nextName + " · " + distanceText);
        }
        if (navigationStatusText != null) {
            navigationStatusText.setText(routeAMap == null ? "定位中" : "GPS 信号正常");
        }
    }

    private int resolveNavigationNodeIndex(RouteInfo route) {
        if (route == null || route.nodes == null || route.nodes.size() == 0) {
            return -1;
        }
        if (currentDemoRouteNode != null) {
            for (int i = 0; i < route.nodes.size(); i++) {
                if (isSameRouteNode(currentDemoRouteNode, route.nodes.get(i))) {
                    return i;
                }
            }
        }
        return 0;
    }

    private RouteNode getCurrentNavigationNode(RouteInfo route) {
        if (routeGuideActive) {
            RouteNode activeNode = getCurrentActiveRouteNode();
            if (activeNode != null) {
                return activeNode;
            }
        }
        if (route == null || route.nodes == null || route.nodes.size() == 0) {
            return null;
        }
        int index = currentRouteNodeIndex < 0 ? resolveNavigationNodeIndex(route) : currentRouteNodeIndex;
        if (index < 0) index = 0;
        if (index >= route.nodes.size()) index = route.nodes.size() - 1;
        return route.nodes.get(index);
    }

    private RouteNode getNextNavigationNode(RouteInfo route) {
        if (routeGuideActive) {
            return getNextActiveRouteNode();
        }
        if (route == null || route.nodes == null || route.nodes.size() == 0) {
            return null;
        }
        int index = currentRouteNodeIndex < 0 ? resolveNavigationNodeIndex(route) : currentRouteNodeIndex;
        int nextIndex = Math.max(0, index) + 1;
        if (nextIndex >= route.nodes.size()) {
            return null;
        }
        return route.nodes.get(nextIndex);
    }

    private String formatDistanceBetweenNodes(RouteNode currentNode, RouteNode nextNode) {
        LatLng current = toLatLng(currentNode);
        LatLng next = toLatLng(nextNode);
        float distance = calculateDistanceMeters(current, next);
        if (distance <= 0) {
            return "距离待确认";
        }
        if (distance >= 1000f) {
            return String.format(Locale.CHINA, "约 %.1f 公里", distance / 1000f);
        }
        return "约 " + Math.round(distance) + " 米";
    }

    private void requestPendingRouteNavigationRefresh() {
        pendingRouteNavigationRefresh = true;
        Log.d(TAG, "[RouteNav] pendingMapRefresh=true");
    }

    private void handleRouteMapReadyRedraw() {
        if (!pendingRouteNavigationRefresh || routeAMap == null || currentRoute == null) {
            return;
        }
        pendingRouteNavigationRefresh = false;
        Log.d(TAG, "[RouteNav] mapReady redraw");
        if (routeNavigationModeActive || routeGuideActive) {
            drawNavigationRouteState(currentRoute);
            updateRouteDemoController(currentRoute);
        } else if (currentRoutePreview != null) {
            drawRouteOnMap(currentRoutePreview);
        }
    }

    private void drawNavigationRouteState(RouteInfo route) {
        if (route == null) {
            return;
        }
        if (routeGuideActive && (activeRouteNodes.size() == 0
                || currentRouteNodeIndex < 0
                || currentRouteNodeIndex >= activeRouteNodes.size())) {
            initializeRouteNavigationState(route);
        }

        RouteNode currentNode = firstNotNull(
                getCurrentActiveRouteNode(),
                currentRoutePreview == null ? null : currentRoutePreview.routePreviewStartPoint,
                route.nodes == null || route.nodes.size() == 0 ? null : route.nodes.get(0)
        );
        RouteNode nextNode = getNextNavigationNode(route);
        updateNavigationInstruction(currentNode, nextNode);

        if (routeAMap == null) {
            requestPendingRouteNavigationRefresh();
            return;
        }
        clearRouteMapOverlays();
        drawRoutePolyline(route);
        drawRouteMarkers(route.nodes);

        Log.d(TAG, "[RouteNav] draw full route current=" + getRouteNodeName(currentNode)
                + ", next=" + getRouteNodeName(nextNode));
        fitRouteBounds(getRouteDrawablePoints(route));
    }

    private void refreshRouteMapForRouteState() {
        if (!routeMapModeActive || routeAMap == null || currentRoute == null) {
            return;
        }
        if (routeNavigationModeActive) {
            drawNavigationRouteState(currentRoute);
        } else if (currentRoutePreview != null) {
            drawRouteOnMap(currentRoutePreview);
        }
    }

    private void onRouteNodeClick(RouteNode node) {
        if (node == null) {
            return;
        }
        if (currentRoute != null && currentRoute.nodes != null) {
            for (int i = 0; i < currentRoute.nodes.size(); i++) {
                if (isSameRouteNode(node, currentRoute.nodes.get(i))) {
                    currentRouteNodeIndex = i;
                    break;
                }
            }
        }
        LatLng latLng = toLatLng(node);
        if (routeAMap != null && latLng != null) {
            routeAMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f));
            for (Marker marker : routePreviewMarkers) {
                if (marker != null && marker.getObject() instanceof RouteNode
                        && isSameRouteNode(node, (RouteNode) marker.getObject())) {
                    marker.showInfoWindow();
                    break;
                }
            }
        }
        if (routeNavigationModeActive) {
            updateNavigationInstruction(node, getNextNavigationNode(currentRoute));
            drawNavigationRouteState(currentRoute);
        } else {
            showRouteBottomSheet(currentRoute);
        }
        showToast((currentRouteNodeIndex + 1) + ". " + getRouteNodeName(node) + " · 点击讲解该景点");
        trackRouteEvent("route_spot_click", currentRoute, node);
    }

    private void moveToCurrentRouteLocation() {
        RouteNode node = firstNotNull(getCurrentActiveRouteNode(),
                currentDemoRouteNode,
                currentRoutePreview == null ? null : currentRoutePreview.routePreviewStartPoint);
        LatLng latLng = toLatLng(node);
        if (routeAMap != null && latLng != null) {
            routeAMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f));
        } else {
            showToast("暂无当前位置坐标");
        }
    }

    private void handleRouteStartGuideClick() {
        if (currentRoute == null) {
            return;
        }
        if (guideEnded) {
            showToast("本次现场导览已结束");
            return;
        }
        if (currentRoutePreview != null && currentRoutePreview.calculating) {
            showToast("正在生成高德步行路线，请稍候");
            return;
        }
        startRouteNavigation(currentRoute);
    }

    private void updateRouteCardLayoutForState() {
        if (routeCardContainer == null) {
            return;
        }

        routeCardContainer.setPadding(dp(12), dp(8), dp(12), dp(8));

        android.view.ViewGroup.LayoutParams rawParams = routeCardContainer.getLayoutParams();

        if (rawParams instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) rawParams;
            params.width = LinearLayout.LayoutParams.MATCH_PARENT;
            params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            params.topMargin = dp(6);
            params.bottomMargin = dp(6);
            routeCardContainer.setLayoutParams(params);
            return;
        }

        if (rawParams instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) rawParams;
            params.width = FrameLayout.LayoutParams.MATCH_PARENT;
            params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.BOTTOM;
            params.leftMargin = dp(14);
            params.rightMargin = dp(14);
            params.bottomMargin = dp(146);
            routeCardContainer.setLayoutParams(params);
        }
    }

    private int calculateRouteCardExpandedHeight() {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int maxHeight = (int) (screenHeight * 0.55f);
        return Math.min(dp(500), maxHeight);
    }

    private int getRouteCardBottomMargin() {
        return dp(146);
    }

    private String getRouteMapStatusText() {
        RoutePreviewData preview = currentRoutePreview;
        if (preview == null) {
            return "正在生成高德步行路线...";
        }
        if (preview.calculating) {
            return "正在生成高德步行路线...";
        }
        if (preview.amapRouteReady && preview.partialFallback) {
            return "部分路段未获取到高德路线，已按节点直线展示";
        }
        if (preview.amapRouteReady) {
            return "已生成高德步行路线";
        }
        if (preview.partialFallback && preview.polylinePoints != null && preview.polylinePoints.size() >= 2) {
            return "高德路线暂不可用，已按推荐节点顺序展示";
        }
        return "当前路线缺少景点坐标，暂按推荐顺序模拟导览";
    }

    private String formatRouteCardTitle(RouteInfo route) {
        if (route == null) {
            return "AI 推荐节点导览路线";
        }
        String routeNameText = firstNotEmpty(route.routeName);
        if (routeNameText.length() > 0) {
            return routeNameText;
        }
        if (route.nodes != null && route.nodes.size() >= 2) {
            String start = getRouteNodeName(route.nodes.get(0));
            String end = getRouteNodeName(route.nodes.get(route.nodes.size() - 1));
            return start + " - " + end + "路线";
        }
        return "AI 推荐节点导览路线";
    }

    private String formatRouteStartNextText(RouteInfo route) {
        if (routeGuideActive) {
            RouteNode current = getCurrentActiveRouteNode();
            RouteNode next = getNextActiveRouteNode();
            return "路线导览中 · 当前：" + (current == null ? "当前起点" : getRouteNodeName(current))
                    + " · 下一站：" + (next == null ? "暂无下一站" : getRouteNodeName(next));
        }
        return "当前起点：" + getRoutePreviewStartName() + " · 推荐游览：" + formatRecommendedRouteText(route);
    }

    private RouteNode getNextRouteNodeForDisplay(RouteInfo route) {
        if (route == null || route.nodes == null || route.nodes.size() == 0) {
            return null;
        }

        if (routeDemoNodeIndex >= 0) {
            int nextIndex = routeDemoNodeIndex + 1;
            return nextIndex < route.nodes.size() ? route.nodes.get(nextIndex) : null;
        }

        if ("current_spot".equals(routeStartType)) {
            int startIndex = findRouteNodeIndexByRouteStart(route);
            if (startIndex >= 0) {
                int nextIndex = startIndex + 1;
                return nextIndex < route.nodes.size() ? route.nodes.get(nextIndex) : null;
            }
        }

        return route.nodes.get(0);
    }

    private int findRouteNodeIndexByRouteStart(RouteInfo route) {
        if (route == null || route.nodes == null) {
            return -1;
        }
        String currentId = safeString(routeStartCurrentSpotId).trim();
        String currentName = safeString(routeStartCurrentSpotName).trim();
        for (int i = 0; i < route.nodes.size(); i++) {
            RouteNode node = route.nodes.get(i);
            if (node == null) continue;
            String nodeId = firstNotEmpty(node.spotId, node.scenicId).trim();
            String nodeName = getRouteNodeName(node).trim();
            if (currentId.length() > 0 && currentId.equals(nodeId)) {
                return i;
            }
            if (currentName.length() > 0 && currentName.equals(nodeName)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isRouteMapReady(RouteInfo route) {
        return currentRoutePreview != null && currentRoutePreview.amapRouteReady;
    }

    private String formatRouteMeta(RouteInfo route) {
        RoutePreviewData preview = currentRoutePreview;
        if (preview != null) {
            if (preview.calculating) {
                int nodeCount = route == null || route.nodes == null ? 0 : route.nodes.size();
                return "正在计算距离与时间 · " + nodeCount + " 个景点";
            }
            if (preview.totalDistanceMeter > 0 || preview.totalDurationSecond > 0) {
                int nodeCount = route == null || route.nodes == null ? 0 : route.nodes.size();
                return "全程约 " + formatDistanceText(preview.totalDistanceMeter)
                        + " · 预计 " + formatDurationMinuteText(preview.totalDurationSecond)
                        + " · " + nodeCount + " 个景点";
            }
        }
        if (route == null) {
            return "比赛演示中将按节点模拟前进";
        }
        StringBuilder builder = new StringBuilder();
        if (route.totalDistanceM.length() > 0) {
            builder.append("全程约 ").append(route.totalDistanceM).append(" 米");
        }
        if (route.estimatedDurationMin.length() > 0) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append("预计 ").append(route.estimatedDurationMin).append(" 分钟");
        }
        if (route.nodes.size() > 0) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append(route.nodes.size()).append(" 个景点");
        }
        if (builder.length() == 0) {
            return "比赛演示中将按节点模拟前进";
        }
        return builder.toString() + " · 比赛演示中将按节点模拟前进";
    }

    private String getRoutePreviewStartName() {
        if (currentRoutePreview != null && currentRoutePreview.routePreviewStartPoint != null) {
            return getRouteNodeName(currentRoutePreview.routePreviewStartPoint);
        }
        return firstNotEmpty(routeStartCurrentSpotName, spotName, "景区入口");
    }

    private String formatRecommendedRouteText(RouteInfo route) {
        if (route == null || route.nodes == null || route.nodes.size() == 0) {
            return "暂无推荐节点";
        }
        StringBuilder builder = new StringBuilder();
        int maxCount = Math.min(route.nodes.size(), 4);
        for (int i = 0; i < maxCount; i++) {
            if (i > 0) {
                builder.append(" -> ");
            }
            builder.append(getRouteNodeName(route.nodes.get(i)));
        }
        if (route.nodes.size() > maxCount) {
            builder.append(" -> ...");
        }
        return builder.toString();
    }

    private String formatDistanceText(float distanceMeter) {
        if (distanceMeter >= 1000f) {
            return String.format(Locale.CHINA, "%.2f 公里", distanceMeter / 1000f);
        }
        return Math.round(distanceMeter) + " 米";
    }

    private String formatDurationMinuteText(long durationSecond) {
        long minutes = Math.max(1L, (durationSecond + 59L) / 60L);
        return minutes + " 分钟";
    }

    private String formatRouteNodeMeta(RouteNode node) {
        StringBuilder builder = new StringBuilder();
        if (node.recommendedStayMin.length() > 0) {
            builder.append("建议停留 ").append(node.recommendedStayMin).append(" 分钟");
        }
        if (node.latitude.length() > 0 || node.longitude.length() > 0) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append("坐标 ").append(firstNotEmpty(node.latitude, "-"))
                    .append(", ").append(firstNotEmpty(node.longitude, "-"));
        }
        return builder.length() == 0 ? "节点信息待完善" : builder.toString();
    }

    private void resetRouteDemoState() {
        routeDemoRequesting = false;
        routeDemoNodeIndex = -1;
        currentDemoRouteNode = null;
        routeGuideActive = false;
        currentRouteNodeIndex = -1;
        activeRouteNodes.clear();
        updateRouteDemoController(currentRoute);
    }

    private void updateRouteDemoController(RouteInfo route) {
        if (routeDemoController == null) {
            return;
        }

        boolean hasNodes = route != null && route.nodes != null && route.nodes.size() > 0;
        routeDemoController.setVisibility(hasNodes && routeGuideActive ? View.VISIBLE : View.GONE);
        if (!hasNodes || !routeGuideActive) {
            return;
        }
        routeDemoController.setPadding(
                dp(10),
                dp(7),
                dp(10),
                dp(8)
        );

        if (routeDemoStatusText != null) {
            RouteNode current = getCurrentActiveRouteNode();
            RouteNode next = getNextActiveRouteNode();
            String statusText = "路线导览中\n当前：" + (current == null ? "当前起点" : getRouteNodeName(current))
                    + "\n下一站：" + (next == null ? "暂无下一站" : getRouteNodeName(next));
            routeDemoStatusText.setText(statusText);
            routeDemoStatusText.setVisibility(View.VISIBLE);
        }

        boolean hasNext = getNextActiveRouteNode() != null;
        boolean canUseButtons = !routeDemoRequesting;

        if (routeDemoNextButton != null) {
            routeDemoNextButton.setVisibility(View.VISIBLE);
            routeDemoNextButton.setText(hasNext ? "模拟到达下一站" : "已到达终点");
            routeDemoNextButton.setEnabled(canUseButtons && hasNext);
        }
        if (routeDemoLeaveButton != null) {
            routeDemoLeaveButton.setVisibility(View.VISIBLE);
            routeDemoLeaveButton.setText("讲解当前景点");
            routeDemoLeaveButton.setEnabled(canUseButtons && getCurrentActiveRouteNode() != null);
        }
        if (routeDemoEndButton != null) {
            routeDemoEndButton.setVisibility(View.VISIBLE);
            routeDemoEndButton.setText("结束本条路线");
            routeDemoEndButton.setEnabled(canUseButtons);
        }
    }

    private void startSimulatedRouteGuide() {
        if (currentRoute == null || currentRoute.nodes == null || currentRoute.nodes.size() == 0) {
            showToast("请先获取路线推荐节点");
            return;
        }
        if (guideEnded) {
            showToast("本次现场导览已结束");
            return;
        }

        activeRouteNodes.clear();
        RouteNode startPoint = currentRoutePreview == null ? null : currentRoutePreview.routePreviewStartPoint;
        if (startPoint != null && !routeContainsNode(currentRoute, startPoint)) {
            activeRouteNodes.add(startPoint);
        }
        for (RouteNode node : currentRoute.nodes) {
            if (node == null) {
                continue;
            }
            if (activeRouteNodes.size() > 0 && isSameRouteNode(activeRouteNodes.get(activeRouteNodes.size() - 1), node)) {
                continue;
            }
            activeRouteNodes.add(node);
        }

        if (activeRouteNodes.size() == 0) {
            showToast("当前路线没有可模拟的节点");
            return;
        }

        routeGuideActive = true;
        currentRouteNodeIndex = 0;
        routeDemoNodeIndex = 0;
        currentDemoRouteNode = activeRouteNodes.get(0);
        applyDemoNodeToCurrentContext(currentDemoRouteNode);
        dismissRouteExpandedPanel();
        renderRouteCard(currentRoute);

        RouteNode current = getCurrentActiveRouteNode();
        RouteNode next = getNextActiveRouteNode();
        String currentName = current == null ? getRoutePreviewStartName() : getRouteNodeName(current);
        String nextName = next == null ? "本路线终点" : getRouteNodeName(next);
        Log.d(TAG, "[RouteNav] start current=" + currentName + ", next=" + nextName);
        refreshRouteMapForRouteState();
        speakRouteGuideText("好的，我们从" + currentName + "出发，下一站前往" + nextName + "。比赛演示中我会按节点模拟前进。");
    }

    private void simulateArriveNextNode() {
        if (!routeGuideActive) {
            startSimulatedRouteGuide();
            return;
        }
        if (routeDemoRequesting) {
            showToast("路线导览正在处理，请稍候");
            return;
        }
        if (!ensureOnsiteCoreWriteReady()) {
            return;
        }
        RouteNode next = getNextActiveRouteNode();
        if (next == null) {
            showToast("已完成本条路线");
            updateRouteDemoController(currentRoute);
            return;
        }

        final RouteInfo route = currentRoute;
        final RouteNode previousNode = firstNotNull(getCurrentActiveRouteNode(), currentDemoRouteNode);
        final RouteNode nextNode = next;
        final int nextIndex = currentRouteNodeIndex + 1;
        Log.d(TAG, "[RouteDemo] arrive previous=" + getRouteNodeName(previousNode)
                + ", next=" + getRouteNodeName(nextNode));

        routeDemoRequesting = true;
        updateRouteDemoController(route);

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean leaveOk = true;
                if (previousNode != null && !isSameRouteNode(previousNode, nextNode)) {
                    leaveOk = postDemoSpotLeave(previousNode);
                    if (leaveOk) {
                        writeRouteDemoBehaviorEvent("spot_leave", route, previousNode);
                    }
                }

                final boolean previousLeaveOk = leaveOk;
                final boolean enterOk = postDemoSpotEnter(nextNode);
                if (enterOk) {
                    writeRouteDemoBehaviorEvent("spot_enter", route, nextNode);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        routeDemoRequesting = false;
                        if (enterOk) {
                            currentRouteNodeIndex = nextIndex;
                            routeDemoNodeIndex = currentRouteNodeIndex;
                            currentDemoRouteNode = nextNode;
                            applyDemoNodeToCurrentContext(nextNode);
                            renderRouteCard(route);
                            refreshRouteMapForRouteState();
                            speakRouteGuideText("已到达" + getRouteNodeName(nextNode) + "，下面为你讲解这里的特色。");
                            if (!previousLeaveOk) {
                                showToast("已到达新景点，但上一景点离开记录写入失败");
                            }
                            askGuideForCurrentNode(nextNode);
                        } else {
                            renderRouteCard(route);
                            showToast("到达景点记录写入失败，请查看 Logcat");
                        }
                    }
                });
            }
        }).start();
    }

    private void finishSimulatedRouteGuide() {
        boolean completed = routeGuideActive && getNextActiveRouteNode() == null;
        if (!routeGuideActive) {
            resetRouteDemoState();
            renderRouteCard(currentRoute);
            showRouteGuideEndedState(false);
            return;
        }
        exitNavigationMode();
        routeGuideActive = false;
        routeNavigationModeActive = false;
        routeDemoRequesting = false;
        currentRouteNodeIndex = -1;
        routeDemoNodeIndex = currentRoute == null || currentRoute.nodes == null ? -1 : currentRoute.nodes.size();
        currentDemoRouteNode = null;
        activeRouteNodes.clear();
        renderRouteCard(currentRoute);
        showRouteGuideEndedState(completed);
        if (completed) {
            speakRouteGuideText("本次推荐路线已完成，你可以继续提问或结束导览。");
        } else {
            speakRouteGuideText("已结束本条路线，你可以继续提问或重新开始路线导航。");
        }
    }

    private void showRouteGuideEndedState(boolean completed) {
        if (routeCardStartNextText != null) {
            routeCardStartNextText.setText(completed ? "路线导览已结束" : "已退出路线导航");
            routeCardStartNextText.setVisibility(View.VISIBLE);
        }
        if (routeStartButton != null) {
            boolean hasNodes = currentRoute != null && currentRoute.nodes != null && currentRoute.nodes.size() > 0;
            routeStartButton.setText("重新开始导航");
            routeStartButton.setEnabled(!guideEnded
                    && hasNodes
                    && (currentRoutePreview == null || !currentRoutePreview.calculating));
        }
        if (routeDemoController != null) {
            routeDemoController.setVisibility(View.GONE);
        }
    }

    private void speakRouteGuideText(String text) {
        if (guideStateText != null) {
            guideStateText.setText("路线导览中");
        }
        if (lastQuestionText != null) {
            lastQuestionText.setText("路线导览");
        }
        showGuideAnswer(text);
        applyDigitalHumanState("explain", "warm");
        speakText(text);
    }

    private void askGuideForCurrentNode(RouteNode node) {
        if (node == null) {
            showToast("当前没有可讲解的景点");
            return;
        }
        String nodeName = getRouteNodeName(node);
        if (nodeName.length() == 0 || "景区入口".equals(nodeName) || "当前起点".equals(nodeName)) {
            showToast("当前起点暂无景点讲解");
            return;
        }
        if (requesting) {
            showToast("数字人正在处理，请稍候");
            return;
        }
        askGuideInternal("请讲解一下【" + nodeName + "】", true, true);
    }

    private RouteNode getCurrentActiveRouteNode() {
        if (!routeGuideActive || currentRouteNodeIndex < 0 || currentRouteNodeIndex >= activeRouteNodes.size()) {
            return null;
        }
        return activeRouteNodes.get(currentRouteNodeIndex);
    }

    private RouteNode getNextActiveRouteNode() {
        if (!routeGuideActive) {
            return null;
        }
        int nextIndex = currentRouteNodeIndex + 1;
        if (nextIndex < 0 || nextIndex >= activeRouteNodes.size()) {
            return null;
        }
        return activeRouteNodes.get(nextIndex);
    }

    private void handleDemoArriveNextSpot() {
        if (guideEnded) {
            showToast("本次现场导览已结束");
            return;
        }
        if (routeDemoRequesting) {
            showToast("路线节点演示正在处理，请稍候");
            return;
        }
        if (currentRoute == null || currentRoute.nodes == null || currentRoute.nodes.size() == 0) {
            showToast("请先获取路线推荐节点");
            return;
        }
        if (!ensureOnsiteCoreWriteReady()) {
            return;
        }

        final int nextIndex = routeDemoNodeIndex + 1;
        if (nextIndex >= currentRoute.nodes.size()) {
            showToast("已到达最后一个路线节点");
            return;
        }

        final RouteInfo route = currentRoute;
        final RouteNode previousNode = currentDemoRouteNode;
        final RouteNode nextNode = currentRoute.nodes.get(nextIndex);
        if (getRouteNodeScenicId(nextNode).length() == 0) {
            showToast("路线节点缺少 scenicId，无法写入到达记录");
            Log.e(TAG, "[RouteNodeDemo] 节点缺少 scenicId: spotId=" + safeString(nextNode.spotId)
                    + ", spotName=" + getRouteNodeName(nextNode));
            return;
        }

        routeDemoRequesting = true;
        updateRouteDemoController(route);

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean leaveOk = true;
                if (previousNode != null) {
                    leaveOk = postDemoSpotLeave(previousNode);
                    if (leaveOk) {
                        writeRouteDemoBehaviorEvent("spot_leave", route, previousNode);
                    }
                }

                final boolean previousLeaveOk = leaveOk;
                final boolean enterOk = postDemoSpotEnter(nextNode);
                if (enterOk) {
                    writeRouteDemoBehaviorEvent("spot_enter", route, nextNode);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        routeDemoRequesting = false;
                        if (enterOk) {
                            routeDemoNodeIndex = nextIndex;
                            currentDemoRouteNode = nextNode;
                            applyDemoNodeToCurrentContext(nextNode);
                            renderRouteCard(route);
                            showDemoArrivalPrompt(nextNode);
                            if (!previousLeaveOk) {
                                showToast("已到达新景点，但上一景点离开记录写入失败");
                            }
                        } else {
                            if (previousNode != null && previousLeaveOk) {
                                currentDemoRouteNode = null;
                            }
                            renderRouteCard(route);
                            showToast("到达景点记录写入失败，请查看 Logcat");
                        }
                    }
                });
            }
        }).start();
    }

    private void handleDemoLeaveCurrentSpot() {
        if (guideEnded) {
            showToast("本次现场导览已结束");
            return;
        }
        if (routeDemoRequesting) {
            showToast("路线节点演示正在处理，请稍候");
            return;
        }
        if (currentRoute == null || currentDemoRouteNode == null) {
            showToast("当前没有可离开的演示景点");
            return;
        }
        if (!ensureOnsiteCoreWriteReady()) {
            return;
        }

        final RouteInfo route = currentRoute;
        final RouteNode leavingNode = currentDemoRouteNode;
        routeDemoRequesting = true;
        updateRouteDemoController(route);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean leaveOk = postDemoSpotLeave(leavingNode);
                if (leaveOk) {
                    writeRouteDemoBehaviorEvent("spot_leave", route, leavingNode);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        routeDemoRequesting = false;
                        if (leaveOk) {
                            currentDemoRouteNode = null;
                            renderRouteCard(route);
                            showToast("已离开：" + getRouteNodeName(leavingNode));
                        } else {
                            renderRouteCard(route);
                            showToast("离开景点记录写入失败，请查看 Logcat");
                        }
                    }
                });
            }
        }).start();
    }

    private void handleDemoEndVisit() {
        if (guideEnded) {
            showToast("本次现场导览已结束");
            return;
        }
        if (routeDemoRequesting) {
            showToast("路线节点演示正在处理，请稍候");
            return;
        }
        if (currentRoute == null || currentRoute.nodes == null || currentRoute.nodes.size() == 0) {
            showToast("请先获取路线推荐节点");
            return;
        }
        if (!ensureOnsiteCoreWriteReady()) {
            return;
        }

        final RouteInfo route = currentRoute;
        final RouteNode leavingNode = currentDemoRouteNode;
        routeDemoRequesting = true;
        updateRouteDemoController(route);

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean leaveOk = true;
                if (leavingNode != null) {
                    leaveOk = postDemoSpotLeave(leavingNode);
                    if (leaveOk) {
                        writeRouteDemoBehaviorEvent("spot_leave", route, leavingNode);
                    }
                }

                final boolean currentLeaveOk = leaveOk;
                final boolean endOk = postDemoVisitEnd(leavingNode);
                if (endOk) {
                    writeRouteDemoBehaviorEvent("visit_end", route, leavingNode);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        routeDemoRequesting = false;
                        if (endOk) {
                            currentDemoRouteNode = null;
                            routeDemoNodeIndex = route.nodes.size();
                            renderRouteCard(route);
                            String text = "本次路线节点演示游览已结束。";
                            showGuideAnswer(text);
                            speakText(text);
                            if (!currentLeaveOk) {
                                showToast("游览已结束，但当前景点离开记录写入失败");
                            }
                        } else {
                            renderRouteCard(route);
                            showToast("结束游览写入失败，请查看 Logcat");
                        }
                    }
                });
            }
        }).start();
    }

    private void showDemoArrivalPrompt(RouteNode node) {
        String nodeName = getRouteNodeName(node);
        String prompt = "你已到达" + nodeName + "，我来为你讲解";
        if (guideStateText != null) {
            guideStateText.setText("路线节点演示");
        }
        if (lastQuestionText != null) {
            lastQuestionText.setText("路线节点演示：到达" + nodeName);
        }
        showGuideAnswer(prompt);
        applyDigitalHumanState("explain", "warm");
        speakText(prompt);
    }

    private void applyDemoNodeToCurrentContext(RouteNode node) {
        if (node == null) {
            return;
        }

        spotId = firstNotEmpty(node.spotId, spotId);
        spotName = firstNotEmpty(node.spotName, node.scenicName, spotName);
        scenicId = firstNotEmpty(node.scenicId, scenicId);
        scenicName = firstNotEmpty(node.scenicName, node.spotName, scenicName);
        routeStartType = "current_spot";
        routeStartCurrentSpotId = firstNotEmpty(node.spotId, node.scenicId, node.id, routeStartCurrentSpotId);
        routeStartCurrentSpotName = firstNotEmpty(getRouteNodeName(node), routeStartCurrentSpotName, "当前景点");
        routeStartLatitude = firstNotEmpty(node.latitude, routeStartLatitude);
        routeStartLongitude = firstNotEmpty(node.longitude, routeStartLongitude);
        updateGuideContext();

        if (targetText != null) {
            targetText.setText(getTopTargetText());
        }
        updateMapCardText();
    }

    private boolean postDemoSpotEnter(RouteNode node) {
        try {
            if (!canWriteCoreVisitData("[RouteNodeDemo][enter]")) {
                return false;
            }

            String scenicCode = getRouteNodeScenicId(node);
            String nodeName = getRouteNodeName(node);
            if (scenicCode.length() == 0 && nodeName.length() == 0) {
                Log.e(TAG, "[RouteNodeDemo][enter] scenicId 和 spotName 均为空，跳过写入");
                return false;
            }

            JSONObject requestJson = new JSONObject();
            putLongOrString(requestJson, "visitId", guideContext.visitId);
            putLongOrString(requestJson, "visit_id", guideContext.visitId);
            requestJson.put("userId", safeString(getEffectiveNativeUserId()));
            requestJson.put("user_id", safeString(getEffectiveNativeUserId()));
            requestJson.put("parkId", safeString(firstNotEmpty(guideContext.parkId, parkId, areaCode)));
            requestJson.put("park_id", safeString(firstNotEmpty(guideContext.parkId, parkId, areaCode)));
            requestJson.put("scenicId", scenicCode);
            requestJson.put("scenic_id", scenicCode);
            requestJson.put("spotId", safeString(firstNotEmpty(node.spotId, node.id, node.scenicId)));
            requestJson.put("spot_id", safeString(firstNotEmpty(node.spotId, node.id, node.scenicId)));
            requestJson.put("spotName", nodeName);
            requestJson.put("spot_name", nodeName);
            requestJson.put("scenicName", nodeName);
            requestJson.put("scenic_name", nodeName);
            requestJson.put("enterSource", DEMO_LOCATION_SOURCE);
            requestJson.put("enter_source", DEMO_LOCATION_SOURCE);
            requestJson.put("locationSource", DEMO_LOCATION_SOURCE);
            requestJson.put("location_source", DEMO_LOCATION_SOURCE);
            requestJson.put("trigger", DEMO_TRIGGER);
            requestJson.put("source", GUIDE_SOURCE);
            putCoordinateIfPresent(requestJson, "latitude", node.latitude);
            putCoordinateIfPresent(requestJson, "longitude", node.longitude);

            Log.d(TAG, "[RouteNodeDemo][enter] visitId=" + guideContext.visitId
                    + ", scenicId=" + scenicCode
                    + ", spotName=" + nodeName
                    + ", locationSource=" + DEMO_LOCATION_SOURCE
                    + ", trigger=" + DEMO_TRIGGER);
            return postJsonForSuccess(buildVisitApiUrl(spotEnterPath), requestJson, "[RouteNodeDemo][enter]");
        } catch (Exception e) {
            Log.e(TAG, "[RouteNodeDemo][enter] 构建请求失败", e);
            return false;
        }
    }

    private boolean postDemoSpotLeave(RouteNode node) {
        try {
            if (!canWriteCoreVisitData("[RouteNodeDemo][leave]")) {
                return false;
            }

            String scenicCode = getRouteNodeScenicId(node);
            String nodeName = getRouteNodeName(node);
            if (scenicCode.length() == 0 && nodeName.length() == 0) {
                Log.e(TAG, "[RouteNodeDemo][leave] scenicId 和 spotName 均为空，跳过写入");
                return false;
            }

            JSONObject requestJson = new JSONObject();
            putLongOrString(requestJson, "visitId", guideContext.visitId);
            putLongOrString(requestJson, "visit_id", guideContext.visitId);
            requestJson.put("userId", safeString(getEffectiveNativeUserId()));
            requestJson.put("user_id", safeString(getEffectiveNativeUserId()));
            requestJson.put("scenicId", scenicCode);
            requestJson.put("scenic_id", scenicCode);
            requestJson.put("spotId", safeString(firstNotEmpty(node.spotId, node.id, node.scenicId)));
            requestJson.put("spot_id", safeString(firstNotEmpty(node.spotId, node.id, node.scenicId)));
            requestJson.put("spotName", nodeName);
            requestJson.put("spot_name", nodeName);
            requestJson.put("scenicName", nodeName);
            requestJson.put("scenic_name", nodeName);
            requestJson.put("locationSource", DEMO_LOCATION_SOURCE);
            requestJson.put("location_source", DEMO_LOCATION_SOURCE);
            requestJson.put("trigger", DEMO_TRIGGER);
            requestJson.put("source", GUIDE_SOURCE);

            Log.d(TAG, "[RouteNodeDemo][leave] visitId=" + guideContext.visitId
                    + ", scenicId=" + scenicCode
                    + ", spotName=" + nodeName
                    + ", locationSource=" + DEMO_LOCATION_SOURCE
                    + ", trigger=" + DEMO_TRIGGER);
            return postJsonForSuccess(buildVisitApiUrl(spotLeavePath), requestJson, "[RouteNodeDemo][leave]");
        } catch (Exception e) {
            Log.e(TAG, "[RouteNodeDemo][leave] 构建请求失败", e);
            return false;
        }
    }

    private boolean postDemoVisitEnd(RouteNode node) {
        try {
            if (!canWriteCoreVisitData("[RouteNodeDemo][end]")) {
                return false;
            }

            JSONObject requestJson = new JSONObject();
            putLongOrString(requestJson, "visitId", guideContext.visitId);
            putLongOrString(requestJson, "visit_id", guideContext.visitId);
            requestJson.put("userId", safeString(getEffectiveNativeUserId()));
            requestJson.put("user_id", safeString(getEffectiveNativeUserId()));
            requestJson.put("endSource", DEMO_LOCATION_SOURCE);
            requestJson.put("end_source", DEMO_LOCATION_SOURCE);
            requestJson.put("locationSource", DEMO_LOCATION_SOURCE);
            requestJson.put("location_source", DEMO_LOCATION_SOURCE);
            requestJson.put("trigger", DEMO_TRIGGER);
            requestJson.put("source", GUIDE_SOURCE);
            if (node != null) {
                putCoordinateIfPresent(requestJson, "latitude", node.latitude);
                putCoordinateIfPresent(requestJson, "longitude", node.longitude);
            } else {
                putCoordinateIfPresent(requestJson, "latitude", latitude);
                putCoordinateIfPresent(requestJson, "longitude", longitude);
            }

            Log.d(TAG, "[RouteNodeDemo][end] visitId=" + guideContext.visitId
                    + ", locationSource=" + DEMO_LOCATION_SOURCE
                    + ", trigger=" + DEMO_TRIGGER);
            return postJsonForSuccess(buildVisitApiUrl(visitEndPath), requestJson, "[RouteNodeDemo][end]");
        } catch (Exception e) {
            Log.e(TAG, "[RouteNodeDemo][end] 构建请求失败", e);
            return false;
        }
    }

    private void showEndVisitConfirmDialog() {
        if (!shouldShowEndVisitButton()) {
            showToast("当前不是现场导览模式");
            return;
        }

        if (rootLayout == null) {
            showToast("页面尚未准备好，请稍后再试");
            return;
        }

        dismissEndVisitDialog();
        endVisitDialogOverlay = new FrameLayout(this);
        endVisitDialogOverlay.setClickable(true);
        endVisitDialogOverlay.setBackgroundColor(Color.argb(128, 0, 0, 0));

        LinearLayout dialogCard = new LinearLayout(this);
        dialogCard.setOrientation(LinearLayout.VERTICAL);
        dialogCard.setPadding(dp(20), dp(18), dp(20), dp(16));
        dialogCard.setClickable(true);
        dialogCard.setBackground(createRoundBg(Color.WHITE, dp(24)));

        TextView title = new TextView(this);
        title.setText("结束本次导览？");
        title.setTextColor(Color.rgb(18, 48, 78));
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        dialogCard.addView(title);

        TextView message = new TextView(this);
        message.setText("结束后将停止本次现场导览记录，并为你生成游玩报告。");
        message.setTextColor(Color.rgb(65, 83, 105));
        message.setTextSize(14);
        message.setLineSpacing(dp(4), 1.0f);
        message.setPadding(0, dp(10), 0, dp(16));
        dialogCard.addView(message);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER_VERTICAL);

        Button cancelButton = createMiniActionButton("继续导览");
        Button confirmButton = new Button(this);
        confirmButton.setText("结束并查看报告");
        confirmButton.setTextSize(14);
        confirmButton.setTextColor(Color.WHITE);
        confirmButton.setAllCaps(false);
        confirmButton.setPadding(0, 0, 0, 0);
        confirmButton.setBackground(createRoundBg(Color.rgb(47, 128, 237), dp(18)));

        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        cancelParams.rightMargin = dp(8);
        buttonRow.addView(cancelButton, cancelParams);

        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, dp(40), 1.25f);
        confirmParams.leftMargin = dp(8);
        buttonRow.addView(confirmButton, confirmParams);
        dialogCard.addView(buttonRow);

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismissEndVisitDialog();
            }
        });
        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismissEndVisitDialog();
                handleNativeEndCurrentVisit();
            }
        });

        rootLayout.addView(endVisitDialogOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.gravity = Gravity.CENTER;
        cardParams.leftMargin = dp(28);
        cardParams.rightMargin = dp(28);
        endVisitDialogOverlay.addView(dialogCard, cardParams);
    }

    private void dismissEndVisitDialog() {
        if (endVisitDialogOverlay != null && rootLayout != null) {
            rootLayout.removeView(endVisitDialogOverlay);
        }
        endVisitDialogOverlay = null;
    }

    private void handleNativeEndCurrentVisit() {
        if (!shouldShowEndVisitButton()) {
            showToast("当前不是现场导览模式");
            return;
        }

        if (endingVisit) {
            showToast("正在结束导览，请稍候");
            return;
        }

        if (!ensureOnsiteCoreWriteReady()) {
            return;
        }

        endingVisit = true;

        if (endVisitButton != null) {
            endVisitButton.setEnabled(false);
            endVisitButton.setText("结束中");
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean leaveOk = true;
                final RouteNode leavingNode = firstNotNull(currentDemoRouteNode, createIntentActiveSpotNodeForLeave());
                if (leavingNode != null) {
                    leaveOk = postDemoSpotLeave(leavingNode);
                    if (leaveOk) {
                        writeRouteDemoBehaviorEvent("spot_leave", currentRoute, leavingNode);
                    }
                }
                final boolean activeSpotLeaveOk = leaveOk;
                final VisitEndResult endResult = postNativeVisitEnd();
                final boolean endOk = endResult != null && endResult.success;
                final String finalVisitId = endResult == null
                        ? firstNotEmpty(visitId, guideContext.visitId)
                        : firstNotEmpty(endResult.visitId, visitId, guideContext.visitId, endResult.reportVisitId);
                final String finalReportVisitId = endResult == null
                        ? finalVisitId
                        : firstNotEmpty(endResult.reportVisitId, finalVisitId);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        endingVisit = false;

                        if (endOk) {
                            guideEnded = true;
                            allowEndVisit = false;
                            isOnsiteGuide = false;
                            startVisitGuide = false;
                            mode = "ended";
                            stopNativeGuideAfterEnd();
                            updateGuideContext();
                            currentDemoRouteNode = null;

                            if (endVisitButton != null) {
                                endVisitButton.setVisibility(View.GONE);
                            }

                            if (titleText != null) {
                                titleText.setText(getGuideTitleText());
                            }

                            if (onlineText != null) {
                                onlineText.setText(getOnlineStatusText());
                                onlineText.setTextColor(Color.rgb(47, 128, 237));
                            }

                            if (targetText != null) {
                                targetText.setText("本次现场导览已结束");
                            }

                            updateRouteStartRow();

                            String text = "本次现场导览已结束，正在为你打开游玩报告。";
                            showGuideAnswer(text);
                            showToast("本次现场导览已结束");
                            if (!activeSpotLeaveOk) {
                                showToast("导览已结束，但当前景点离开记录写入失败");
                            }
                            notifyUniAppOpenVisitReport(finalVisitId, finalReportVisitId);
                            finish();
                        } else {
                            if (endVisitButton != null) {
                                endVisitButton.setEnabled(true);
                                endVisitButton.setText("结束导览");
                            }
                            showToast("结束导览失败，请重试");
                        }
                    }
                });
            }
        }).start();
    }

    private VisitEndResult postNativeVisitEnd() {
        try {
            if (!canWriteCoreVisitData("[NativeVisitEnd]")) {
                VisitEndResult invalidResult = new VisitEndResult();
                invalidResult.success = false;
                return invalidResult;
            }

            JSONObject requestJson = new JSONObject();

            putLongOrString(requestJson, "visitId", guideContext.visitId);
            putLongOrString(requestJson, "visit_id", guideContext.visitId);
            putLongOrString(requestJson, "areaId", firstNotEmpty(areaId, scenicId, guideContext.scenicId, parkId, areaCode));
            putLongOrString(requestJson, "area_id", firstNotEmpty(areaId, scenicId, guideContext.scenicId, parkId, areaCode));
            requestJson.put("areaName", safeString(firstNotEmpty(areaName, scenicName, guideContext.scenicName, parkName)));
            requestJson.put("area_name", safeString(firstNotEmpty(areaName, scenicName, guideContext.scenicName, parkName)));
            requestJson.put("endReason", "USER_MANUAL_END");
            requestJson.put("end_reason", "USER_MANUAL_END");

            requestJson.put("userId", safeString(getEffectiveNativeUserId()));
            requestJson.put("user_id", safeString(getEffectiveNativeUserId()));

            requestJson.put("parkId", safeString(firstNotEmpty(guideContext.parkId, parkId, areaCode)));
            requestJson.put("park_id", safeString(firstNotEmpty(guideContext.parkId, parkId, areaCode)));

            requestJson.put("parkName", safeString(firstNotEmpty(guideContext.parkName, parkName, areaName)));
            requestJson.put("park_name", safeString(firstNotEmpty(guideContext.parkName, parkName, areaName)));

            requestJson.put("endSource", "native-live2d-manual");
            requestJson.put("end_source", "native-live2d-manual");
            requestJson.put("locationSource", "native-live2d-manual");
            requestJson.put("location_source", "native-live2d-manual");
            requestJson.put("trigger", "native-end-visit");
            requestJson.put("source", GUIDE_SOURCE);

            putCoordinateIfPresent(requestJson, "latitude", latitude);
            putCoordinateIfPresent(requestJson, "longitude", longitude);

            Log.d(TAG, "[NativeVisitEnd] visitId=" + guideContext.visitId
                    + ", userId=" + getEffectiveNativeUserId()
                    + ", areaId=" + firstNotEmpty(areaId, scenicId, guideContext.scenicId, parkId, areaCode)
                    + ", trigger=native-end-visit");

            VisitEndResult appResult = postJsonForVisitEndResult(
                    buildVisitApiUrl(DEFAULT_APP_VISIT_END_PATH),
                    requestJson,
                    "[NativeVisitEnd][app]"
            );
            if (appResult != null && appResult.success) {
                return appResult;
            }

            Log.e(TAG, "[NativeVisitEnd] /api/app/visit/end 未确认成功，fallback 到 /api/visit/end");
            return postJsonForVisitEndResult(
                    buildVisitApiUrl(DEFAULT_VISIT_END_PATH),
                    requestJson,
                    "[NativeVisitEnd][fallback]"
            );
        } catch (Exception e) {
            Log.e(TAG, "[NativeVisitEnd] 构建结束导览请求失败", e);
            VisitEndResult result = new VisitEndResult();
            result.success = false;
            return result;
        }
    }

    private String getNativeVisitEndPath() {
        return firstNotEmpty(visitEndPath, DEFAULT_VISIT_END_PATH);
    }

    private VisitEndResult postJsonForVisitEndResult(String urlText, JSONObject requestJson, String logPrefix) {
        VisitEndResult result = new VisitEndResult();
        result.visitId = firstNotEmpty(visitId, guideContext.visitId);
        result.reportVisitId = result.visitId;

        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlText);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            applyAuthorizationHeader(connection);

            Log.d(TAG, logPrefix + " request url=" + urlText
                    + ", requestJson=" + requestJson
                    + ", hasAuthorization=" + (buildAuthorizationHeader().length() > 0));

            OutputStream outputStream = connection.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            writer.write(requestJson.toString());
            writer.flush();
            writer.close();
            outputStream.close();

            int responseCode = connection.getResponseCode();
            String responseText = readStream(responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());

            Log.d(TAG, logPrefix + " response httpStatus=" + responseCode
                    + ", url=" + urlText
                    + ", responseBody=" + responseText);

            if (responseCode >= 200 && responseCode < 300) {
                fillVisitEndResultIds(result, responseText);
                result.success = isVisitEndBusinessSuccess(responseText, firstNotEmpty(guideContext.visitId, visitId));
                Log.d(TAG, logPrefix + " POST 返回 url=" + urlText
                        + ", success=" + result.success
                        + ", reportVisitId=" + result.reportVisitId
                        + ", response=" + responseText);
                return result;
            }

            Log.e(TAG, logPrefix + " POST 失败 code=" + responseCode
                    + ", url=" + urlText
                    + ", body=" + requestJson
                    + ", response=" + responseText);
            return result;
        } catch (Exception e) {
            Log.e(TAG, logPrefix + " POST 异常 url=" + urlText + ", body=" + requestJson, e);
            return result;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean isVisitEndBusinessSuccess(String responseText, String expectedVisitId) {
        try {
            JSONObject root = new JSONObject(safeString(responseText));
            JSONObject data = root.optJSONObject("data");
            JSONObject source = data == null ? root : data;
            if (isZeroCode(root) || root.optBoolean("success", false) || source.optBoolean("success", false)) {
                return true;
            }
            String status = firstNotEmpty(
                    getJsonText(source, "status", "visitStatus", "visit_status", "state"),
                    getJsonText(root, "status", "visitStatus", "visit_status", "state")
            ).trim().toUpperCase(Locale.CHINA);
            if ("COMPLETED".equals(status)
                    || "ENDED".equals(status)
                    || "FINISHED".equals(status)
                    || "DONE".equals(status)) {
                return true;
            }

            if (firstNotEmpty(
                    getJsonText(source, "endTime", "end_time", "endedAt", "ended_at"),
                    getJsonText(root, "endTime", "end_time", "endedAt", "ended_at")
            ).length() > 0) {
                return true;
            }

            String responseVisitId = firstNotEmpty(
                    getJsonText(source, "visitId", "visit_id", "id"),
                    getJsonText(root, "visitId", "visit_id", "id")
            );
            String message = firstNotEmpty(
                    getJsonText(root, "message", "msg"),
                    getJsonText(source, "message", "msg")
            );
            return isSameVisitIdText(responseVisitId, expectedVisitId) && isSuccessMessage(message);
        } catch (Exception e) {
            Log.e(TAG, "[NativeVisitEnd] 解析结束导览业务结果失败 response=" + responseText, e);
            return false;
        }
    }

    private boolean isZeroCode(JSONObject object) {
        if (object == null || !object.has("code") || object.isNull("code")) {
            return false;
        }
        return "0".equals(getJsonText(object, "code"));
    }

    private boolean isSuccessMessage(String message) {
        String text = safeString(message).trim().toLowerCase(Locale.ROOT);
        return "success".equals(text)
                || "ok".equals(text)
                || text.contains("成功");
    }

    private boolean isSameVisitIdText(String left, String right) {
        String leftText = safeString(left).trim();
        String rightText = safeString(right).trim();
        return leftText.length() > 0 && rightText.length() > 0 && leftText.equals(rightText);
    }

    private void fillVisitEndResultIds(VisitEndResult result, String responseText) {
        if (result == null) {
            return;
        }
        try {
            JSONObject root = new JSONObject(safeString(responseText));
            JSONObject data = root.optJSONObject("data");
            JSONObject source = data == null ? root : data;
            String finalVisitId = firstNotEmpty(
                    getJsonText(source, "reportVisitId", "report_visit_id"),
                    getJsonText(source, "visitId", "visit_id", "id"),
                    getJsonText(root, "reportVisitId", "report_visit_id"),
                    getJsonText(root, "visitId", "visit_id", "id"),
                    result.reportVisitId,
                    result.visitId
            );
            result.reportVisitId = finalVisitId;
            result.visitId = firstNotEmpty(
                    getJsonText(source, "visitId", "visit_id", "id"),
                    getJsonText(root, "visitId", "visit_id", "id"),
                    result.visitId,
                    finalVisitId
            );
        } catch (Exception ignored) {
            result.reportVisitId = firstNotEmpty(result.reportVisitId, result.visitId, visitId, guideContext.visitId);
        }
    }

    private void notifyUniAppOpenVisitReport(String visitIdForReportPage, String reportVisitId) {
        String finalVisitId = firstNotEmpty(visitIdForReportPage, visitId, guideContext.visitId, reportVisitId);
        String finalReportVisitId = firstNotEmpty(reportVisitId, finalVisitId);
        String reportPage = "/pages/visit/report?visitId=" + urlEncode(finalVisitId);

        Intent resultIntent = new Intent();
        resultIntent.putExtra("guideEnded", true);
        resultIntent.putExtra("visitId", finalVisitId);
        resultIntent.putExtra("visit_id", finalVisitId);
        resultIntent.putExtra("reportVisitId", finalReportVisitId);
        resultIntent.putExtra("report_visit_id", finalReportVisitId);
        resultIntent.putExtra("openReport", true);
        resultIntent.putExtra("open_report", true);
        resultIntent.putExtra("openPage", reportPage);
        resultIntent.putExtra("open_page", reportPage);
        resultIntent.putExtra("targetPage", reportPage);
        resultIntent.putExtra("target_page", reportPage);
        resultIntent.putExtra("source", "native-live2d");
        setResult(Activity.RESULT_OK, resultIntent);

        // 不再硬编码启动 uni-app Activity。
        // 调试基座 / 正式包名不同，强行 setClassName 容易 ActivityNotFound。
        // 这里只通过 setResult + finish 交给 uni-app 的 onShow/onActivityResult 处理报告跳转。
        Log.d(TAG, "[NativeVisitEnd] setResult return to uni-app, reportPage=" + reportPage
                + ", reportVisitId=" + finalReportVisitId);
    }

    private void openUniAppVisitReport(String visitIdForReportPage, String reportVisitId, String reportPage) {
        // 保留方法签名用于兼容旧调用，但不再主动拉起 uni-app Activity。
        // uni-app 调试基座和正式包名不同，硬编码 setClassName 会导致 ActivityNotFoundException。
        // 结束导览成功后统一依赖 notifyUniAppOpenVisitReport() 的 setResult + finish 返回。
        Log.d(TAG, "[NativeVisitEnd] skip explicit uni-app startActivity, reportPage=" + reportPage
                + ", reportVisitId=" + reportVisitId);
    }

    private void stopNativeGuideAfterEnd() {
        requesting = false;
        voiceFlowActive = false;
        recording = false;
        routeDemoRequesting = false;
        currentDemoRouteNode = null;
        if (currentRoute != null && currentRoute.nodes != null) {
            routeDemoNodeIndex = currentRoute.nodes.size();
        }
        dismissEndVisitDialog();
        dismissRouteExpandedPanel();
        updateRouteDemoController(currentRoute);
        releaseRecorder();
        stopCurrentAudio();
        if (guideStateText != null) {
            guideStateText.setText("讲解完成");
        }
        if (voiceMainButton != null) {
            voiceMainButton.setText("🎙 长按说话");
        }
        if (lastQuestionText != null) {
            lastQuestionText.setText("本次路线节点演示已结束");
        }
        if (targetText != null) {
            targetText.setText("本次现场导览已结束");
        }
        if (answerText != null) {
            showGuideAnswer("本次现场导览已结束。");
        }
    }

    private void writeRouteDemoBehaviorEvent(String eventName, RouteInfo route, RouteNode node) {
        String spotIdText = node == null ? "" : safeString(node.spotId);
        String spotNameText = node == null ? "" : getRouteNodeName(node);
        Log.d(TAG, "[RouteNodeDemoEvent] event=" + eventName
                + ", source=" + GUIDE_SOURCE
                + ", visitId=" + guideContext.visitId
                + ", userId=" + guideContext.userId
                + ", spotId=" + spotIdText
                + ", spotName=" + spotNameText
                + ", planId=" + (route == null ? "" : safeString(route.planId))
                + ", routeName=" + (route == null ? "" : safeString(route.routeName))
                + ", locationSource=" + DEMO_LOCATION_SOURCE
                + ", trigger=" + DEMO_TRIGGER);
        postRouteDemoBehaviorEvent(eventName, route, node, spotIdText, spotNameText);
    }

    private void postRouteDemoBehaviorEvent(final String eventName, final RouteInfo route, final RouteNode node,
                                            final String spotIdText, final String spotNameText) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    String eventUrl = buildBehaviorEventUrl();
                    JSONObject requestJson = new JSONObject();
                    requestJson.put("eventType", safeString(eventName));
                    requestJson.put("event_type", safeString(eventName));
                    requestJson.put("eventName", safeString(eventName));
                    requestJson.put("event_name", safeString(eventName));
                    requestJson.put("userId", safeString(guideContext.userId));
                    requestJson.put("user_id", safeString(guideContext.userId));
                    putLongOrString(requestJson, "visitId", guideContext.visitId);
                    putLongOrString(requestJson, "visit_id", guideContext.visitId);
                    requestJson.put("sessionId", safeString(firstNotEmpty(sessionId, conversationId, appAuthSessionId)));
                    requestJson.put("session_id", safeString(firstNotEmpty(sessionId, conversationId, appAuthSessionId)));
                    putLongIfPossible(requestJson, "areaId", firstNotEmpty(areaCode, guideContext.parkId, parkId));
                    putLongIfPossible(requestJson, "area_id", firstNotEmpty(areaCode, guideContext.parkId, parkId));
                    requestJson.put("areaCode", safeString(firstNotEmpty(areaCode, guideContext.parkId, parkId)));
                    requestJson.put("area_code", safeString(firstNotEmpty(areaCode, guideContext.parkId, parkId)));
                    putLongIfPossible(requestJson, "spotId", spotIdText);
                    putLongIfPossible(requestJson, "spot_id", spotIdText);
                    requestJson.put("sceneCode", node == null ? "" : safeString(getRouteNodeScenicId(node)));
                    requestJson.put("scene_code", node == null ? "" : safeString(getRouteNodeScenicId(node)));
                    requestJson.put("parkName", safeString(firstNotEmpty(guideContext.parkName, parkName, areaName)));
                    requestJson.put("park_name", safeString(firstNotEmpty(guideContext.parkName, parkName, areaName)));
                    requestJson.put("spotName", safeString(spotNameText));
                    requestJson.put("spot_name", safeString(spotNameText));
                    requestJson.put("planId", route == null ? "" : safeString(route.planId));
                    requestJson.put("plan_id", route == null ? "" : safeString(route.planId));
                    requestJson.put("routeName", route == null ? "" : safeString(route.routeName));
                    requestJson.put("route_name", route == null ? "" : safeString(route.routeName));
                    requestJson.put("entityType", node == null ? "VISIT" : "SPOT");
                    requestJson.put("entity_type", node == null ? "VISIT" : "SPOT");
                    requestJson.put("entityId", firstNotEmpty(spotIdText, node == null ? "" : getRouteNodeScenicId(node), guideContext.visitId));
                    requestJson.put("entity_id", firstNotEmpty(spotIdText, node == null ? "" : getRouteNodeScenicId(node), guideContext.visitId));
                    requestJson.put("source", GUIDE_SOURCE);
                    requestJson.put("sourcePage", GUIDE_SOURCE);
                    requestJson.put("source_page", GUIDE_SOURCE);

                    if (node != null) {
                        putCoordinateIfPresent(requestJson, "latitude", node.latitude);
                        putCoordinateIfPresent(requestJson, "longitude", node.longitude);
                    }

                    JSONObject extra = new JSONObject();
                    extra.put("locationSource", DEMO_LOCATION_SOURCE);
                    extra.put("location_source", DEMO_LOCATION_SOURCE);
                    extra.put("trigger", DEMO_TRIGGER);
                    extra.put("source", GUIDE_SOURCE);
                    extra.put("routeNodeOrder", node == null ? "" : safeString(node.order));
                    extra.put("route_node_order", node == null ? "" : safeString(node.order));
                    requestJson.put("extra", extra);

                    URL url = new URL(eventUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(8000);
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    connection.setRequestProperty("Accept", "application/json");
                    applyAuthorizationHeader(connection);

                    OutputStream outputStream = connection.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
                    writer.write(requestJson.toString());
                    writer.flush();
                    writer.close();
                    outputStream.close();

                    int responseCode = connection.getResponseCode();
                    String responseText = readStream(responseCode >= 200 && responseCode < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream());

                    if (responseCode >= 200 && responseCode < 300) {
                        Log.d(TAG, "[RouteNodeDemoEvent] 上报成功 event=" + eventName + ", response=" + responseText);
                    } else {
                        Log.e(TAG, "[RouteNodeDemoEvent] 上报失败 event=" + eventName
                                + ", code=" + responseCode
                                + ", url=" + eventUrl
                                + ", response=" + responseText);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "[RouteNodeDemoEvent] 上报异常 event=" + eventName
                            + ", url=" + buildBehaviorEventUrl(), e);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        }).start();
    }

    private boolean postJsonForSuccess(String urlText, JSONObject requestJson, String logPrefix) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlText);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            applyAuthorizationHeader(connection);

            OutputStream outputStream = connection.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            writer.write(requestJson.toString());
            writer.flush();
            writer.close();
            outputStream.close();

            int responseCode = connection.getResponseCode();
            String responseText = readStream(responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());

            if (responseCode >= 200 && responseCode < 300) {
                Log.d(TAG, logPrefix + " POST 成功 url=" + urlText + ", response=" + responseText);
                return true;
            }

            Log.e(TAG, logPrefix + " POST 失败 code=" + responseCode
                    + ", url=" + urlText
                    + ", body=" + requestJson
                    + ", response=" + responseText);
            return false;
        } catch (Exception e) {
            Log.e(TAG, logPrefix + " POST 异常 url=" + urlText + ", body=" + requestJson, e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String buildVisitApiUrl(String path) {
        return buildBackendApiUrl(path);
    }

    private String buildBackendApiUrl(String path) {
        String finalPath = firstNotEmpty(path);
        if (finalPath.startsWith("http://") || finalPath.startsWith("https://")) {
            return finalPath;
        }

        String baseUrl = firstNotEmpty(behaviorBackendBaseUrl, getBaseUrlFromFullUrl(GUIDE_CHAT_URL));
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!finalPath.startsWith("/")) {
            finalPath = "/" + finalPath;
        }
        return baseUrl + finalPath;
    }

    private String getRouteNodeName(RouteNode node) {
        if (node == null) {
            return "路线节点";
        }
        return firstNotEmpty(node.name, node.spotName, node.scenicName, "路线节点");
    }

    private String getRouteNodeScenicId(RouteNode node) {
        if (node == null) {
            return "";
        }
        return firstNotEmpty(node.scenicId, node.spotId, node.id);
    }

    private void putLongOrString(JSONObject object, String key, String value) throws Exception {
        String text = safeString(value).trim();
        if (text.length() == 0) {
            object.put(key, "");
            return;
        }

        try {
            object.put(key, Long.parseLong(text));
        } catch (NumberFormatException e) {
            object.put(key, text);
        }
    }

    private void putLongIfPossible(JSONObject object, String key, String value) throws Exception {
        String text = safeString(value).trim();
        if (text.length() == 0) {
            return;
        }

        try {
            object.put(key, Long.parseLong(text));
        } catch (NumberFormatException ignored) {
        }
    }

    private void putCoordinateIfPresent(JSONObject object, String key, String value) throws Exception {
        String text = safeString(value).trim();
        if (text.length() == 0) {
            return;
        }

        try {
            object.put(key, Double.parseDouble(text));
        } catch (NumberFormatException ignored) {
        }
    }

    private void resetRouteStartSelection() {
        String currentName = firstNotEmpty(spotName, scenicName);
        String currentId = firstNotEmpty(spotId, scenicId);
        if (currentName.length() > 0 || currentId.length() > 0 || (latitude.length() > 0 && longitude.length() > 0)) {
            routeStartType = "current_spot";
            routeStartCurrentSpotId = currentId;
            routeStartCurrentSpotName = firstNotEmpty(currentName, "当前起点");
            routeStartLatitude = latitude;
            routeStartLongitude = longitude;
        } else {
            routeStartType = "park_entrance";
            routeStartCurrentSpotId = "";
            routeStartCurrentSpotName = "景区入口";
            routeStartLatitude = "";
            routeStartLongitude = "";
        }
        routeStartSpots.clear();
        routeStartSpotsParkKey = "";
        routeStartSpotsLoading = false;
        updateRouteStartRow();
    }

    private void updateRouteStartRow() {
        if (routeStartRow == null) {
            return;
        }

        boolean visible = isOnsiteMode();
        routeStartRow.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            return;
        }

        if (routeStartText != null) {
            routeStartText.setText("当前起点：" + firstNotEmpty(routeStartCurrentSpotName, "景区入口"));
        }
        if (routeStartSwitchButton != null) {
            routeStartSwitchButton.setEnabled(!routeStartSpotsLoading);
            routeStartSwitchButton.setText(routeStartSpotsLoading ? "加载中" : "切换");
        }
        updateSimulatedSpotButton();
    }

    private void updateSimulatedSpotButton() {
        if (simulateLingshanButton == null) {
            return;
        }
        boolean visible = isOnsiteMode() && !guideEnded;
        if (routeGuideActive) {
            visible = false;
        }
        simulateLingshanButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        simulateLingshanButton.setEnabled(visible && !routeDemoRequesting && !requesting);
        simulateLingshanButton.setText(routeDemoRequesting ? "模拟到达中..." : "模拟到达当前景点");
    }

    private void handleRouteStartSwitchClick() {
        if (!isOnsiteMode()) {
            return;
        }
        if (routeStartSpotsLoading) {
            showToast("正在加载景点列表，请稍候");
            return;
        }

        String parkKey = getRouteStartParkKey();
        if (parkKey.length() == 0) {
            showToast("缺少 parkId，暂时无法获取景点列表");
            return;
        }

        if (parkKey.equals(routeStartSpotsParkKey) && routeStartSpots.size() > 0) {
            showRouteStartChooser();
            return;
        }

        loadRouteStartSpotsAndShowDialog(parkKey);
    }

    private void handleSimulateArriveLingshanBuddha() {
        if (!isOnsiteMode()) {
            showToast("当前不是现场导览模式");
            return;
        }
        if (guideEnded) {
            showToast("本次现场导览已结束");
            return;
        }
        if (routeDemoRequesting || requesting) {
            showToast("数字人正在处理，请稍候");
            return;
        }
        if (!ensureOnsiteCoreWriteReady()) {
            return;
        }

        final RouteNode targetNode = createLingshanBuddhaDemoNode();
        final RouteNode previousNode = firstNotNull(currentDemoRouteNode, createIntentCurrentSpotNode());
        if (previousNode != null && isSameRouteNode(previousNode, targetNode)) {
            askGuideAfterSpotEntered(targetNode);
            return;
        }

        routeDemoRequesting = true;
        updateSimulatedSpotButton();
        updateRouteDemoController(currentRoute);

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean leaveOk = true;
                if (previousNode != null) {
                    leaveOk = postDemoSpotLeave(previousNode);
                    if (leaveOk) {
                        writeRouteDemoBehaviorEvent("spot_leave", currentRoute, previousNode);
                    }
                }

                final boolean previousLeaveOk = leaveOk;
                final boolean enterOk = postDemoSpotEnter(targetNode);
                if (enterOk) {
                    writeRouteDemoBehaviorEvent("spot_enter", currentRoute, targetNode);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        routeDemoRequesting = false;
                        updateSimulatedSpotButton();
                        if (enterOk) {
                            currentDemoRouteNode = targetNode;
                            applyDemoNodeToCurrentContext(targetNode);
                            updateRouteStartRow();
                            appendSystemMessage("已到达灵山大佛，正在为你讲解。");
                            showToast("已模拟到达：" + getRouteNodeName(targetNode));
                            if (!previousLeaveOk) {
                                showToast("已到达新景点，但上一景点离开记录写入失败");
                            }
                            askGuideAfterSpotEntered(targetNode);
                        } else {
                            showToast("模拟到达景点失败，请检查 spot enter 接口");
                        }
                    }
                });
            }
        }).start();
    }

    private RouteNode createLingshanBuddhaDemoNode() {
        RouteNode node = new RouteNode();
        node.name = "灵山大佛";
        node.spotName = "灵山大佛";
        node.scenicName = "灵山大佛";

        RouteStartSpot matchedSpot = findKnownSpotByName("灵山大佛");
        if (matchedSpot != null) {
            node.id = firstNotEmpty(matchedSpot.spotId, matchedSpot.scenicId);
            node.spotId = matchedSpot.spotId;
            node.scenicId = matchedSpot.scenicId;
            node.latitude = matchedSpot.latitude;
            node.longitude = matchedSpot.longitude;
            normalizeRouteNodeLocation(node);
            return node;
        }

        if ("灵山大佛".equals(spotName) || "灵山大佛".equals(scenicName)) {
            node.id = firstNotEmpty(spotId, scenicId);
            node.spotId = spotId;
            node.scenicId = scenicId;
            node.latitude = latitude;
            node.longitude = longitude;
        }
        normalizeRouteNodeLocation(node);
        return node;
    }

    private RouteStartSpot findKnownSpotByName(String name) {
        String targetName = safeString(name);
        if (targetName.length() == 0) {
            return null;
        }
        for (RouteStartSpot spot : routeStartSpots) {
            if (spot != null && targetName.equals(firstNotEmpty(spot.spotName))) {
                return spot;
            }
        }
        return null;
    }

    private void askGuideAfterSpotEntered(RouteNode node) {
        if (node == null) {
            return;
        }
        String nodeName = getRouteNodeName(node);
        String question = "我已经到达" + nodeName + "，请讲解这里的特色和游览看点。";
        if (questionInput != null) {
            questionInput.setText(question);
        }
        askGuideInternal(question, false, true);
    }

    private String getRouteStartParkKey() {
        return firstNotEmpty(guideContext.parkId, parkId, areaCode);
    }

    private void loadRouteStartSpotsAndShowDialog(final String parkKey) {
        routeStartSpotsLoading = true;
        updateRouteStartRow();

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    String urlText = buildParkSpotsUrl(parkKey);
                    Log.d(TAG, "[RouteStart] 加载景点列表 url=" + urlText);

                    URL url = new URL(urlText);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(8000);
                    connection.setDoInput(true);
                    connection.setRequestProperty("Accept", "application/json");
                    applyAuthorizationHeader(connection);

                    int responseCode = connection.getResponseCode();
                    String responseText = readStream(responseCode >= 200 && responseCode < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream());

                    if (responseCode >= 200 && responseCode < 300) {
                        final List<RouteStartSpot> spots = parseRouteStartSpots(responseText);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                routeStartSpotsLoading = false;
                                routeStartSpots.clear();
                                routeStartSpots.addAll(spots);
                                routeStartSpotsParkKey = parkKey;
                                updateRouteStartRow();
                                if (spots.size() == 0) {
                                    showToast("当前景区暂无可选景点，可从景区入口开始");
                                }
                                showRouteStartChooser();
                            }
                        });
                    } else {
                        Log.e(TAG, "[RouteStart] 景点列表加载失败 code=" + responseCode
                                + ", url=" + urlText
                                + ", response=" + responseText);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                routeStartSpotsLoading = false;
                                updateRouteStartRow();
                                showToast("景点列表加载失败，请稍后再试");
                            }
                        });
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "[RouteStart] 景点列表加载异常", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            routeStartSpotsLoading = false;
                            updateRouteStartRow();
                            showToast("景点列表加载异常，请检查后端服务");
                        }
                    });
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        }).start();
    }

    private String buildParkSpotsUrl(String parkKey) {
        String path = "/api/app/parks/" + urlEncode(parkKey) + "/spots";
        String finalAreaId = firstNotEmpty(areaId);
        if (finalAreaId.length() > 0) {
            path += "?areaId=" + urlEncode(finalAreaId);
        }
        return buildBackendApiUrl(path);
    }

    private List<RouteStartSpot> parseRouteStartSpots(String responseText) {
        List<RouteStartSpot> result = new ArrayList<>();
        try {
            String text = responseText == null ? "" : responseText.trim();
            JSONArray array = null;

            if (text.startsWith("[")) {
                array = new JSONArray(text);
            } else {
                JSONObject root = new JSONObject(text);
                array = root.optJSONArray("data");
                if (array == null && root.optJSONObject("data") != null) {
                    JSONObject dataObject = root.optJSONObject("data");
                    array = dataObject.optJSONArray("list");
                    if (array == null) array = dataObject.optJSONArray("items");
                    if (array == null) array = dataObject.optJSONArray("records");
                }
                if (array == null) {
                    array = root.optJSONArray("list");
                }
            }

            if (array == null) {
                return result;
            }

            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;

                RouteStartSpot spot = new RouteStartSpot();
                spot.spotId = getJsonText(item, "spotId", "spot_id", "id");
                spot.scenicId = getJsonText(item, "scenicId", "scenic_id", "sceneCode", "scene_code");
                spot.spotName = getJsonText(item, "spotName", "spot_name", "name", "scenicName", "scenic_name");
                spot.latitude = getJsonText(item, "latitude", "lat");
                spot.longitude = getJsonText(item, "longitude", "lng", "lon");

                if (spot.spotName.length() > 0) {
                    result.add(spot);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "[RouteStart] 解析景点列表失败 response=" + responseText, e);
        }
        return result;
    }

    private void showRouteStartChooser() {
        if (!isOnsiteMode()) {
            return;
        }

        if (rootLayout == null) {
            showToast("页面尚未准备好，请稍后再试");
            return;
        }

        dismissRouteStartPicker();
        pendingRouteStartSpot = findCurrentRouteStartSpot();

        routeStartPickerOverlay = new FrameLayout(this);
        routeStartPickerOverlay.setClickable(true);
        routeStartPickerOverlay.setBackgroundColor(Color.argb(118, 0, 0, 0));
        routeStartPickerOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismissRouteStartPicker();
            }
        });

        LinearLayout pickerPanel = new LinearLayout(this);
        pickerPanel.setOrientation(LinearLayout.VERTICAL);
        pickerPanel.setPadding(dp(18), dp(16), dp(18), dp(14));
        pickerPanel.setClickable(true);
        pickerPanel.setBackground(createRoundBg(Color.WHITE, dp(24)));
        pickerPanel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 消费点击，避免点到面板内部时关闭蒙层。
            }
        });

        TextView titleText = new TextView(this);
        titleText.setText("选择路线起点");
        titleText.setTextColor(Color.rgb(24, 48, 72));
        titleText.setTextSize(17);
        titleText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        pickerPanel.addView(titleText);

        TextView subtitleText = new TextView(this);
        subtitleText.setText("已根据 GPS 为你推荐附近起点");
        subtitleText.setTextColor(Color.rgb(112, 128, 145));
        subtitleText.setTextSize(12);
        subtitleText.setPadding(0, dp(4), 0, dp(10));
        pickerPanel.addView(subtitleText);

        ScrollView optionsScrollView = new ScrollView(this);
        routeStartOptionsContainer = new LinearLayout(this);
        routeStartOptionsContainer.setOrientation(LinearLayout.VERTICAL);
        optionsScrollView.addView(
                routeStartOptionsContainer,
                new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT)
        );
        pickerPanel.addView(optionsScrollView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(238)));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setPadding(0, dp(12), 0, 0);

        Button cancelButton = createMiniActionButton("取消");
        Button confirmButton = new Button(this);
        confirmButton.setText("确认起点");
        confirmButton.setTextSize(14);
        confirmButton.setTextColor(Color.WHITE);
        confirmButton.setAllCaps(false);
        confirmButton.setBackground(createRoundBg(Color.rgb(47, 128, 237), dp(18)));

        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(38), 1);
        cancelParams.rightMargin = dp(8);
        actionRow.addView(cancelButton, cancelParams);

        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, dp(38), 1.2f);
        actionRow.addView(confirmButton, confirmParams);
        pickerPanel.addView(actionRow);

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismissRouteStartPicker();
            }
        });
        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RouteStartSpot selected = pendingRouteStartSpot;
                dismissRouteStartPicker();
                if (selected == null) {
                    applyRouteStartEntrance();
                } else {
                    applyRouteStartSpot(selected);
                }
            }
        });

        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        rootLayout.addView(routeStartPickerOverlay, overlayParams);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        panelParams.gravity = Gravity.BOTTOM;
        panelParams.leftMargin = dp(12);
        panelParams.rightMargin = dp(12);
        panelParams.bottomMargin = dp(12);
        routeStartPickerOverlay.addView(pickerPanel, panelParams);

        renderRouteStartPickerOptions();
    }

    private void dismissRouteStartPicker() {
        if (routeStartPickerOverlay != null && rootLayout != null) {
            rootLayout.removeView(routeStartPickerOverlay);
        }
        routeStartPickerOverlay = null;
        routeStartOptionsContainer = null;
    }

    private void renderRouteStartPickerOptions() {
        if (routeStartOptionsContainer == null) {
            return;
        }
        routeStartOptionsContainer.removeAllViews();

        RouteStartSpot recommendedSpot = findRecommendedRouteStartSpot();
        routeStartOptionsContainer.addView(createRouteStartOptionView(null, false, ""));

        for (RouteStartSpot spot : routeStartSpots) {
            boolean recommended = isSameRouteStartSpot(spot, recommendedSpot);
            String distanceText = formatRouteStartDistance(spot);
            routeStartOptionsContainer.addView(createRouteStartOptionView(spot, recommended, distanceText));
        }
    }

    private View createRouteStartOptionView(final RouteStartSpot spot, boolean recommended, String distanceText) {
        boolean selected = isPendingRouteStartSpot(spot);
        LinearLayout optionView = new LinearLayout(this);
        optionView.setOrientation(LinearLayout.VERTICAL);
        optionView.setPadding(dp(13), dp(9), dp(13), dp(9));
        optionView.setBackground(createStrokeRoundBg(
                selected ? Color.rgb(232, 243, 255) : Color.WHITE,
                selected ? Color.rgb(47, 128, 237) : Color.rgb(226, 234, 242),
                dp(14)
        ));
        optionView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pendingRouteStartSpot = spot;
                renderRouteStartPickerOptions();
            }
        });

        TextView nameText = new TextView(this);
        nameText.setText(spot == null ? "景区入口" : firstNotEmpty(spot.spotName, "未命名景点"));
        nameText.setTextColor(selected ? Color.rgb(30, 100, 180) : Color.rgb(24, 48, 72));
        nameText.setTextSize(14);
        nameText.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        optionView.addView(nameText);

        TextView metaText = new TextView(this);
        metaText.setText(buildRouteStartOptionMeta(spot, selected, recommended, distanceText));
        metaText.setTextColor(selected ? Color.rgb(47, 128, 237) : Color.rgb(112, 128, 145));
        metaText.setTextSize(11);
        metaText.setPadding(0, dp(3), 0, 0);
        optionView.addView(metaText);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(8);
        optionView.setLayoutParams(params);
        return optionView;
    }

    private String buildRouteStartOptionMeta(RouteStartSpot spot, boolean selected, boolean recommended, String distanceText) {
        if (selected) {
            return "当前选中";
        }
        if (recommended) {
            return "推荐" + (distanceText.length() > 0 ? " · " + distanceText : "");
        }
        if (spot == null) {
            return "从景区入口开始生成路线";
        }
        return "可设为当前路线起点";
    }

    private boolean isPendingRouteStartSpot(RouteStartSpot spot) {
        if (pendingRouteStartSpot == null || spot == null) {
            return pendingRouteStartSpot == null && spot == null;
        }
        return isSameRouteStartSpot(pendingRouteStartSpot, spot);
    }

    private RouteStartSpot findCurrentRouteStartSpot() {
        if (!"current_spot".equals(routeStartType)) {
            return null;
        }
        for (RouteStartSpot spot : routeStartSpots) {
            if (isRouteStartSpotCurrent(spot)) {
                return spot;
            }
        }
        return null;
    }

    private boolean isRouteStartSpotCurrent(RouteStartSpot spot) {
        if (spot == null) {
            return false;
        }
        String currentId = safeString(routeStartCurrentSpotId).trim();
        String spotIdText = firstNotEmpty(spot.spotId, spot.scenicId).trim();
        if (currentId.length() > 0 && currentId.equals(spotIdText)) {
            return true;
        }
        return routeStartCurrentSpotName.length() > 0
                && routeStartCurrentSpotName.equals(firstNotEmpty(spot.spotName, ""));
    }

    private RouteStartSpot findRecommendedRouteStartSpot() {
        RouteStartSpot result = null;
        int bestDistance = -1;
        for (RouteStartSpot spot : routeStartSpots) {
            int distanceMeters = calculateDistanceMeters(latitude, longitude, spot.latitude, spot.longitude);
            if (distanceMeters < 0) {
                continue;
            }
            if (bestDistance < 0 || distanceMeters < bestDistance) {
                bestDistance = distanceMeters;
                result = spot;
            }
        }
        return result;
    }

    private boolean isSameRouteStartSpot(RouteStartSpot left, RouteStartSpot right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        String leftId = firstNotEmpty(left.spotId, left.scenicId).trim();
        String rightId = firstNotEmpty(right.spotId, right.scenicId).trim();
        if (leftId.length() > 0 && leftId.equals(rightId)) {
            return true;
        }
        return firstNotEmpty(left.spotName, "").equals(firstNotEmpty(right.spotName, ""));
    }

    private String formatRouteStartDistance(RouteStartSpot spot) {
        if (spot == null) {
            return "";
        }
        int distanceMeters = calculateDistanceMeters(latitude, longitude, spot.latitude, spot.longitude);
        if (distanceMeters < 0) {
            return "";
        }
        if (distanceMeters >= 1000) {
            return "距你约 " + String.format(Locale.CHINA, "%.1f", distanceMeters / 1000.0f) + " 公里";
        }
        return "距你约 " + distanceMeters + " 米";
    }

    private int calculateDistanceMeters(String lat1Text, String lon1Text, String lat2Text, String lon2Text) {
        double lat1 = parseDoubleOrNaN(lat1Text);
        double lon1 = parseDoubleOrNaN(lon1Text);
        double lat2 = parseDoubleOrNaN(lat2Text);
        double lon2 = parseDoubleOrNaN(lon2Text);
        if (Double.isNaN(lat1) || Double.isNaN(lon1) || Double.isNaN(lat2) || Double.isNaN(lon2)) {
            return -1;
        }

        double earthRadius = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) (earthRadius * c + 0.5d);
    }

    private double parseDoubleOrNaN(String value) {
        try {
            return Double.parseDouble(safeString(value).trim());
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    private double parseDoubleOrDefault(String value, double defaultValue) {
        double parsed = parseDoubleOrNaN(value);
        return Double.isNaN(parsed) ? defaultValue : parsed;
    }

    private boolean isValidCoordinate(double lat, double lon) {
        return !Double.isNaN(lat)
                && !Double.isNaN(lon)
                && lat >= -90.0d
                && lat <= 90.0d
                && lon >= -180.0d
                && lon <= 180.0d
                && !(Math.abs(lat) < 0.000001d && Math.abs(lon) < 0.000001d);
    }

    private void applyRouteStartEntrance() {
        routeStartType = "park_entrance";
        routeStartCurrentSpotId = "";
        routeStartCurrentSpotName = "景区入口";
        routeStartLatitude = "";
        routeStartLongitude = "";
        updateRouteStartRow();
        refreshRouteCardForRouteStartChanged();
        Log.d(TAG, "[RouteStart] 已选择景区入口，不写入 spot_enter");
        showToast("路线起点已设为景区入口");
    }

    private void applyRouteStartSpot(RouteStartSpot spot) {
        if (spot == null) {
            applyRouteStartEntrance();
            return;
        }

        routeStartType = "current_spot";
        routeStartCurrentSpotId = firstNotEmpty(spot.spotId, spot.scenicId);
        routeStartCurrentSpotName = firstNotEmpty(spot.spotName, "当前景点");
        routeStartLatitude = safeString(spot.latitude);
        routeStartLongitude = safeString(spot.longitude);
        updateRouteStartRow();
        refreshRouteCardForRouteStartChanged();
        Log.d(TAG, "[RouteStart] 已选择当前起点 routeStartType=" + routeStartType
                + ", currentSpotId=" + routeStartCurrentSpotId
                + ", currentSpotName=" + routeStartCurrentSpotName
                + ", routeStartLatitude=" + routeStartLatitude
                + ", routeStartLongitude=" + routeStartLongitude
                + "，不写入 spot_enter");
        showToast("路线起点已设为：" + routeStartCurrentSpotName);
    }

    private void refreshRouteCardForRouteStartChanged() {
        if (currentRoute != null && routeCardContainer != null && routeCardContainer.getVisibility() == View.VISIBLE) {
            resetRouteDemoState();
            currentRoutePreview = createInitialRoutePreview(currentRoute);
            currentRoute.preview = currentRoutePreview;
            renderRouteCard(currentRoute);
            buildRoutePreviewWithAmap(currentRoute);
        }
    }

    private void writeRouteCardEvent(String eventName, RouteInfo route, RouteNode node) {
        trackRouteEvent(eventName, route, node);
    }

    private void trackRouteEventOnce(String eventName, RouteInfo route, RouteNode node) {
        if (!hasRouteEventWriteContext()) {
            Log.d(TAG, "[RouteCardEvent] 缺少 visitId/userId/token，跳过一次性事件 event=" + eventName);
            return;
        }
        String routeKey = getRoutePlanEventKey(route);
        if (routeKey.length() > 0) {
            if (shownRoutePlanIds.contains(routeKey)) {
                return;
            }
            shownRoutePlanIds.add(routeKey);
        }
        trackRouteEvent(eventName, route, node);
    }

    private void trackRouteEvent(String eventName, RouteInfo route, RouteNode node) {
        if (!hasRouteEventWriteContext()) {
            Log.d(TAG, "[RouteCardEvent] 缺少 visitId/userId/token，跳过事件上报 event=" + eventName);
            return;
        }
        String spotId = node == null ? "" : safeString(node.spotId);
        String spotName = node == null ? "" : getRouteNodeName(node);
        Log.d(TAG, "[RouteCardEvent] event=" + eventName
                + ", source=" + ROUTE_EVENT_SOURCE
                + ", visitId=" + guideContext.visitId
                + ", userId=" + guideContext.userId
                + ", spotId=" + spotId
                + ", spotName=" + spotName
                + ", planId=" + (route == null ? "" : safeString(route.planId))
                + ", routeName=" + (route == null ? "" : safeString(route.routeName)));
        postRouteCardEvent(eventName, route, node, spotId, spotName);
    }

    private boolean hasRouteEventVisitId() {
        return safeString(guideContext.visitId).trim().length() > 0 || safeString(visitId).trim().length() > 0;
    }

    private boolean hasRouteEventWriteContext() {
        if (!hasRouteEventVisitId()) {
            return false;
        }
        if (!hasAuthToken()) {
            return false;
        }
        String realUserId = getEffectiveNativeUserId();
        if (realUserId.length() == 0) {
            return false;
        }
        guideContext.visitId = firstNotEmpty(guideContext.visitId, visitId);
        guideContext.userId = realUserId;
        return true;
    }

    private void trackRouteEvent(String eventName, JSONObject extra) {
        trackRouteEvent(eventName, currentRoute, null);
    }

    private String getRoutePlanEventKey(RouteInfo route) {
        if (route == null) {
            return "";
        }
        String planId = safeString(route.planId);
        if (planId.length() > 0) {
            return planId;
        }
        StringBuilder builder = new StringBuilder(firstNotEmpty(route.routeName, formatRouteCardTitle(route), "route"));
        if (route.nodes != null) {
            for (RouteNode node : route.nodes) {
                builder.append("|")
                        .append(firstNotEmpty(node == null ? "" : node.spotId, node == null ? "" : node.scenicId))
                        .append(":")
                        .append(node == null ? "" : getRouteNodeName(node));
            }
        }
        return "local_" + Math.abs(builder.toString().hashCode());
    }

    private void postRouteCardEvent(final String eventName, final RouteInfo route, final RouteNode node, final String spotId, final String spotName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    String eventUrl = buildBehaviorEventUrl();
                    JSONObject requestJson = new JSONObject();
                    requestJson.put("eventType", safeString(eventName));
                    requestJson.put("event_type", safeString(eventName));
                    requestJson.put("userId", safeString(guideContext.userId));
                    requestJson.put("user_id", safeString(guideContext.userId));
                    putLongOrString(requestJson, "visitId", guideContext.visitId);
                    putLongOrString(requestJson, "visit_id", guideContext.visitId);
                    requestJson.put("sessionId", safeString(firstNotEmpty(sessionId, conversationId)));
                    requestJson.put("session_id", safeString(firstNotEmpty(sessionId, conversationId)));
                    putLongIfPossible(requestJson, "areaId", firstNotEmpty(areaId, guideContext.scenicId, scenicId, areaCode, guideContext.parkId, parkId));
                    putLongIfPossible(requestJson, "area_id", firstNotEmpty(areaId, guideContext.scenicId, scenicId, areaCode, guideContext.parkId, parkId));
                    requestJson.put("areaCode", safeString(firstNotEmpty(areaCode, guideContext.parkId, parkId)));
                    requestJson.put("area_code", safeString(firstNotEmpty(areaCode, guideContext.parkId, parkId)));
                    requestJson.put("parkName", safeString(firstNotEmpty(guideContext.parkName, parkName, areaName)));
                    requestJson.put("park_name", safeString(firstNotEmpty(guideContext.parkName, parkName, areaName)));
                    putLongIfPossible(requestJson, "spotId", spotId);
                    putLongIfPossible(requestJson, "spot_id", spotId);
                    requestJson.put("sceneCode", node == null ? "" : safeString(getRouteNodeScenicId(node)));
                    requestJson.put("scene_code", node == null ? "" : safeString(getRouteNodeScenicId(node)));
                    requestJson.put("spotName", safeString(spotName));
                    requestJson.put("spot_name", safeString(spotName));
                    requestJson.put("planId", route == null ? "" : safeString(route.planId));
                    requestJson.put("plan_id", route == null ? "" : safeString(route.planId));
                    requestJson.put("routeName", route == null ? "" : safeString(route.routeName));
                    requestJson.put("route_name", route == null ? "" : safeString(route.routeName));
                    requestJson.put("source", ROUTE_EVENT_SOURCE);
                    requestJson.put("sourcePage", ROUTE_EVENT_SOURCE);
                    requestJson.put("source_page", ROUTE_EVENT_SOURCE);

                    if (node != null) {
                        putCoordinateIfPresent(requestJson, "latitude", node.latitude);
                        putCoordinateIfPresent(requestJson, "longitude", node.longitude);
                    }

                    URL url = new URL(eventUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(8000);
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    connection.setRequestProperty("Accept", "application/json");
                    applyAuthorizationHeader(connection);

                    OutputStream outputStream = connection.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
                    writer.write(requestJson.toString());
                    writer.flush();
                    writer.close();
                    outputStream.close();

                    int responseCode = connection.getResponseCode();
                    String responseText = readStream(responseCode >= 200 && responseCode < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream());

                    if (responseCode >= 200 && responseCode < 300) {
                        Log.d(TAG, "[RouteCardEvent] 上报成功 event=" + eventName + ", response=" + responseText);
                    } else {
                        Log.e(TAG, "[RouteCardEvent] 上报失败 event=" + eventName
                                + ", code=" + responseCode
                                + ", url=" + eventUrl
                                + ", response=" + responseText);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "[RouteCardEvent] 上报异常 event=" + eventName
                            + ", url=" + buildBehaviorEventUrl(), e);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        }).start();
    }

    private String buildBehaviorEventUrl() {
        String path = firstNotEmpty(behaviorEventPath, DEFAULT_BEHAVIOR_EVENT_PATH);
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }

        String baseUrl = firstNotEmpty(behaviorBackendBaseUrl, getBaseUrlFromFullUrl(GUIDE_CHAT_URL));
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return baseUrl + path;
    }

    private String getBaseUrlFromFullUrl(String fullUrl) {
        try {
            URL url = new URL(fullUrl);
            String baseUrl = url.getProtocol() + "://" + url.getHost();
            if (url.getPort() > 0) {
                baseUrl += ":" + url.getPort();
            }
            return baseUrl;
        } catch (Exception e) {
            return "";
        }
    }

    private void appendUserMessage(String text) {
        appendChatMessage("游客", text, "你的问题已收到");
    }

    private void appendAiMessage(String text) {
        appendChatMessage("AI 导游", text, "暂无讲解内容");
    }

    private void appendSystemMessage(String text) {
        appendChatMessage("系统", text, "状态已更新");
    }

    private void appendChatMessage(String role, String text, String fallback) {
        String content = firstNotEmpty(text, fallback).trim();
        if (content.length() == 0 || answerText == null) {
            return;
        }
        if (chatHistoryStarted) {
            chatTranscriptBuilder.append("\n\n");
        }
        chatHistoryStarted = true;
        chatTranscriptBuilder.append(firstNotEmpty(role, "消息")).append("：").append(content);
        answerText.setText(chatTranscriptBuilder.toString());
        scrollChatToBottom();
    }

    private void resetChatMessages() {
        chatTranscriptBuilder.setLength(0);
        chatHistoryStarted = false;
        if (answerText != null) {
            answerText.setText("");
        }
    }

    private void scrollChatToBottom() {
        if (answerScrollView == null) {
            return;
        }
        answerScrollView.post(new Runnable() {
            @Override
            public void run() {
                answerScrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void showGuideAnswer(String answer) {
        appendAiMessage(answer);
    }

    private String formatBackendErrorMessage(String responseText, String defaultMessage) {
        try {
            JSONObject root = new JSONObject(safeString(responseText));
            String msg = firstNotEmpty(root.optString("msg", ""), root.optString("message", ""));
            if (msg.contains("用户不存在")
                    || msg.contains("登录")
                    || msg.contains("token")
                    || msg.contains("Token")) {
                return "登录状态异常，请返回首页重新进入导览";
            }
            return msg.length() > 0 ? msg : firstNotEmpty(defaultMessage, "请求失败，请稍后重试");
        } catch (Exception ignored) {
            return firstNotEmpty(defaultMessage, "请求失败，请稍后重试");
        }
    }

    private void updateQuickButtons(List<String> suggestions) {
        if (suggestions == null || suggestions.size() == 0) {
            return;
        }
        if (suggestions.size() > 0) quickBtn1.setText(suggestions.get(0));
        if (suggestions.size() > 1) quickBtn2.setText(suggestions.get(1));
        if (suggestions.size() > 2) quickBtn3.setText(suggestions.get(2));
    }

    private void showWelcomeMessage() {
        if (guideStateText != null) guideStateText.setText("等待提问");
        if (voiceMainButton != null) voiceMainButton.setText("🎙 长按说话");
        if (lastQuestionText != null) lastQuestionText.setText("长按说话，让数字人为你讲解");

        String visualText;

        if (isOnsiteMode()) {
            visualText = "欢迎来到" + getTargetName() + "，我是你的 AI 数字人导游。接下来我可以为你讲解景点、推荐路线，也会记录本次游玩过程。你可以点击“模拟到达当前景点”，也可以直接向我提问。";
        } else if ("scenic_explain".equals(mode)) {
            visualText = "正在为你讲解" + getTargetName() + "。\n\n你可以继续询问景区特色、游览顺序、开放服务和拍照打卡点。";
        } else if ("spot_explain".equals(mode)) {
            visualText = "正在为你讲解" + getTargetName() + "。\n\n你可以继续询问历史背景、建筑特色、拍照点和游览建议。";
        } else if ("route_planning".equals(mode)) {
            visualText = "我会结合你的本次出行信息，为你规划更合适的游览路线。\n\n你也可以继续补充游玩时长、同行人数和偏好。";
        } else {
            visualText = "您好，我是你的 AI 景区导览助手。\n\n你可以长按下方语音按钮直接提问，也可以选择快捷问题开始导览。";
        }

        resetChatMessages();
        appendAiMessage(visualText);
        applyDigitalHumanState("welcome", "warm");

        // 如果真正会自动请求后端 TTS，这里不再播放原生欢迎语，避免两个声音叠在一起。
        if (!shouldRunAutoQuestionOnLaunch()) {
            playWelcomeVoiceOnce();
        }
    }

    private void playWelcomeVoiceOnce() {
        if (hasPlayedWelcome) return;
        hasPlayedWelcome = true;

        String target = getTargetName();

        if (welcomeText != null && welcomeText.trim().length() > 0) {
            speakText(welcomeText.trim());
            return;
        }

        String welcomeText;

        if (isOnsiteMode()) {
            welcomeText = "欢迎来到" + target + "，我是你的 AI 数字人导游。接下来我可以为你讲解景点、推荐路线，也会记录本次游玩过程。你可以点击模拟到达当前景点，也可以直接向我提问。";
        } else if ("scenic_explain".equals(mode)) {
            welcomeText = "您好，我是您的AI景区讲解助手。现在将为您讲解" + target + "。";
        } else if ("spot_explain".equals(mode)) {
            welcomeText = "您好，我是您的AI景点讲解员。现在将为您介绍" + target + "。";
        } else if ("route_planning".equals(mode)) {
            welcomeText = "您好，我会结合您的本次出行信息，为您规划更合适的游览路线。";
        } else if ("park".equals(contextType)) {
            welcomeText = "您好，我是您的AI景区导览助手。现在将为您讲解" + target + "。";
        } else if ("scenic".equals(contextType)) {
            welcomeText = "您好，我是您的AI景点讲解员。现在将为您介绍" + target + "。";
        } else {
            welcomeText = "您好，我是您的AI数字人导览助手。您可以通过语音向我提问。";
        }

        speakText(welcomeText);
    }

    private void handleGuideIntent(Intent intent) {
        if (intent == null) return;

        from = getSafeExtra(intent, "from");
        entry = getSafeExtra(intent, "entry");

        userId = firstNotEmpty(
                getSafeExtra(intent, "userId"),
                getSafeExtra(intent, "user_id"),
                getSafeExtra(intent, "currentUserId"),
                getSafeExtra(intent, "current_user_id")
        );

        appUserId = userId;

        loginUserId = firstNotEmpty(
                getSafeExtra(intent, "login_user_id"),
                getSafeExtra(intent, "loginUserId")
        );

        visitorId = firstNotEmpty(
                getSafeExtra(intent, "visitor_id"),
                getSafeExtra(intent, "visitorId")
        );

        authToken = firstNotEmpty(
                getSafeExtra(intent, "token"),
                getSafeExtra(intent, "accessToken"),
                getSafeExtra(intent, "access_token"),
                getSafeExtra(intent, "authToken"),
                getSafeExtra(intent, "auth_token"),
                getSafeExtra(intent, "Authorization"),
                getSafeExtra(intent, "authorization")
        );

        String intentSessionId = firstNotEmpty(
                getSafeExtra(intent, "session_id"),
                getSafeExtra(intent, "sessionId")
        );

        // 注意：uni-app 传来的 session_id 是 APP 登录会话，不再直接当作 AI 对话 sessionId
        if (intentSessionId.length() > 0) {
            appAuthSessionId = intentSessionId;
        }

        String intentConversationId = firstNotEmpty(
                getSafeExtra(intent, "guide_conversation_id"),
                getSafeExtra(intent, "guideConversationId"),
                getSafeExtra(intent, "conversation_id"),
                getSafeExtra(intent, "conversationId")
        );

        // 只有明确是 AI 对话ID时才沿用；APP 登录 session_ / visitor_ 不用于 AI 对话
        if (isUsableGuideConversationId(intentConversationId)) {
            conversationId = intentConversationId;
        } else {
            conversationId = "";
        }

        // 每次从 uni-app 打开数字人，AI 对话 sessionId 重新生成
        sessionId = "";

        visitId = firstNotEmpty(
                getSafeExtra(intent, "visitId"),
                getSafeExtra(intent, "visit_id")
        );

        parkId = firstNotEmpty(
                getSafeExtra(intent, "parkId"),
                getSafeExtra(intent, "park_id")
        );

        parkName = firstNotEmpty(
                getSafeExtra(intent, "parkName"),
                getSafeExtra(intent, "park_name")
        );

        scenicName = firstNotEmpty(
                getSafeExtra(intent, "scenicName"),
                getSafeExtra(intent, "scenic_name"),
                getSafeExtra(intent, "scene_name"),
                getSafeExtra(intent, "sceneName")
        );

        scenicId = firstNotEmpty(
                getSafeExtra(intent, "scenicId"),
                getSafeExtra(intent, "scenic_id"),
                getSafeExtra(intent, "scene_code"),
                getSafeExtra(intent, "sceneCode")
        );

        groupSize = firstNotEmpty(
                getSafeExtra(intent, "travelPeopleCount"),
                getSafeExtra(intent, "travel_people_count"),
                getSafeExtra(intent, "groupSize"),
                getSafeExtra(intent, "group_size")
        );

        travelType = firstNotEmpty(
                getSafeExtra(intent, "travelType"),
                getSafeExtra(intent, "travel_type")
        );

        visitPreference = firstNotEmpty(
                getSafeExtra(intent, "travelPreference"),
                getSafeExtra(intent, "travel_preference"),
                getSafeExtra(intent, "visitPreference"),
                getSafeExtra(intent, "visit_preference")
        );

        contextType = firstNotEmpty(
                getSafeExtra(intent, "contextType"),
                getSafeExtra(intent, "context_type")
        );
        contextName = getSafeExtra(intent, "contextName");
        autoQuestion = firstNotEmpty(
                getSafeExtra(intent, "autoQuestion"),
                getSafeExtra(intent, "auto_question")
        );

        areaName = firstNotEmpty(
                getSafeExtra(intent, "areaName"),
                getSafeExtra(intent, "area_name"),
                scenicName,
                parkName
        );

        areaId = firstNotEmpty(
                getSafeExtra(intent, "areaId"),
                getSafeExtra(intent, "area_id"),
                scenicId
        );

        areaCode = firstNotEmpty(
                getSafeExtra(intent, "areaCode"),
                getSafeExtra(intent, "area_code"),
                parkId
        );

        spotName = firstNotEmpty(
                getSafeExtra(intent, "spotName"),
                getSafeExtra(intent, "current_spot_name"),
                getSafeExtra(intent, "currentSpotName")
        );

        spotId = firstNotEmpty(
                getSafeExtra(intent, "spotId"),
                getSafeExtra(intent, "current_spot_id"),
                getSafeExtra(intent, "currentSpotId")
        );

        mode = firstNotEmpty(
                getSafeExtra(intent, "guideMode"),
                getSafeExtra(intent, "guide_mode"),
                getSafeExtra(intent, "mode")
        );
        String startVisitGuideText = getSafeExtra(intent, "startVisitGuide");
        startVisitGuide = parseBooleanLike(startVisitGuideText);

        String onsiteGuideText = firstNotEmpty(
                getSafeExtra(intent, "isOnsiteGuide"),
                getSafeExtra(intent, "is_onsite_guide")
        );
        String allowEndVisitText = firstNotEmpty(
                getSafeExtra(intent, "allowEndVisit"),
                getSafeExtra(intent, "allow_end_visit")
        );
        boolean intentAllowEndVisit = allowEndVisitText.length() > 0 && parseBooleanLike(allowEndVisitText);
        isOnsiteGuide = "onsite".equalsIgnoreCase(safeString(mode).trim())
                || parseBooleanLike(onsiteGuideText)
                || (safeString(visitId).trim().length() > 0 && intentAllowEndVisit);
        allowEndVisit = allowEndVisitText.length() > 0 ? intentAllowEndVisit : isOnsiteGuide;
        endingVisit = false;
        guideEnded = false;
        trigger = getSafeExtra(intent, "trigger");
        distance = getSafeExtra(intent, "distance");
        latitude = firstNotEmpty(
                getSafeExtra(intent, "current_latitude"),
                getSafeExtra(intent, "currentLatitude"),
                getSafeExtra(intent, "latitude"),
                getSafeExtra(intent, "lat")
        );
        longitude = firstNotEmpty(
                getSafeExtra(intent, "current_longitude"),
                getSafeExtra(intent, "currentLongitude"),
                getSafeExtra(intent, "longitude"),
                getSafeExtra(intent, "lng"),
                getSafeExtra(intent, "lon")
        );

        String intentAvatarId = firstNotEmpty(
                getSafeExtra(intent, "avatarId"),
                getSafeExtra(intent, "avatar_id")
        );
        String intentModelPath = firstNotEmpty(
                getSafeExtra(intent, "modelPath"),
                getSafeExtra(intent, "model_path")
        );
        digitalHumanConfigJson = firstNotEmpty(
                getSafeExtra(intent, "digitalHumanConfigJson"),
                getSafeExtra(intent, "digital_human_config_json"),
                getSafeExtra(intent, "digitalHumanConfig"),
                getSafeExtra(intent, "digital_human_config")
        );
        String configAvatarId = getDigitalHumanConfigText(digitalHumanConfigJson, "avatarId", "avatar_id");
        String configModelPath = getDigitalHumanConfigText(digitalHumanConfigJson, "modelPath", "model_path");
        String configVoiceId = getDigitalHumanConfigText(digitalHumanConfigJson, "voiceId", "voice_id");

        avatarId = firstNotEmpty(
                firstSupportedAvatarId(intentAvatarId, intentModelPath, configAvatarId, configModelPath),
                "guide_female_01"
        );
        modelPath = firstNotEmpty(
                firstSupportedAvatarId(intentModelPath, intentAvatarId, configModelPath, configAvatarId),
                avatarId,
                "guide_female_01"
        );

        Log.d(TAG, "[DigitalHuman] intent avatarId=" + intentAvatarId
                + ", intent modelPath=" + intentModelPath);
        Log.d(TAG, "[DigitalHuman] config avatarId=" + configAvatarId
                + ", config modelPath=" + configModelPath);
        Log.d(TAG, "[DigitalHuman] final avatarId=" + avatarId);
        Log.d(TAG, "[DigitalHuman] final modelPath=" + modelPath);

        avatarName = firstNotEmpty(
                getSafeExtra(intent, "avatarName"),
                getSafeExtra(intent, "avatar_name"),
                "灵灵"
        );

        clothesMode = firstNotEmpty(
                getSafeExtra(intent, "clothesMode"),
                getSafeExtra(intent, "clothes_mode"),
                getSafeExtra(intent, "avatarClothesMode"),
                getSafeExtra(intent, "avatar_clothes_mode")
        );

        String intentVoiceId = firstNotEmpty(
                getSafeExtra(intent, "voiceId"),
                getSafeExtra(intent, "voice_id")
        );
        voiceId = firstNotEmpty(
                intentVoiceId,
                configVoiceId,
                "zhitian_emo"
        );

        voiceName = firstNotEmpty(
                getSafeExtra(intent, "voiceName"),
                getSafeExtra(intent, "voice_name"),
                "知甜"
        );

        welcomeText = firstNotEmpty(
                getSafeExtra(intent, "welcomeText"),
                getSafeExtra(intent, "welcome_text")
        );

        behaviorBackendBaseUrl = firstNotEmpty(
                getSafeExtra(intent, "backendBaseUrl"),
                getSafeExtra(intent, "apiBaseUrl"),
                behaviorBackendBaseUrl,
                getBaseUrlFromFullUrl(GUIDE_CHAT_URL)
        );
        behaviorEventPath = firstNotEmpty(
                getSafeExtra(intent, "behaviorEventPath"),
                behaviorEventPath,
                DEFAULT_BEHAVIOR_EVENT_PATH
        );
        spotEnterPath = firstNotEmpty(
                getSafeExtra(intent, "spotEnterPath"),
                getSafeExtra(intent, "spot_enter_path"),
                spotEnterPath,
                DEFAULT_SPOT_ENTER_PATH
        );
        spotLeavePath = firstNotEmpty(
                getSafeExtra(intent, "spotLeavePath"),
                getSafeExtra(intent, "spot_leave_path"),
                spotLeavePath,
                DEFAULT_SPOT_LEAVE_PATH
        );
        visitEndPath = firstNotEmpty(
                getSafeExtra(intent, "visitEndPath"),
                getSafeExtra(intent, "visit_end_path"),
                visitEndPath,
                DEFAULT_VISIT_END_PATH
        );

        resetRouteStartSelection();
        updateGuideContext();
        logGuideContext();

        Log.d(TAG, "from = " + from);
        Log.d(TAG, "entry = " + entry);
        Log.d(TAG, "userId=" + userId);
        Log.d(TAG, "visitId=" + visitId);
        Log.d(TAG, "parkId=" + parkId);
        Log.d(TAG, "parkName=" + parkName);
        Log.d(TAG, "groupSize=" + groupSize);
        Log.d(TAG, "travelType=" + travelType);
        Log.d(TAG, "visitPreference=" + visitPreference);
        Log.d(TAG, "appUserId = " + appUserId);
        Log.d(TAG, "loginUserId = " + loginUserId);
        Log.d(TAG, "visitorId = " + visitorId);
        Log.d(TAG, "authToken = " + maskTokenForLog(authToken));
        Log.d(TAG, "appAuthSessionId = " + appAuthSessionId);
        Log.d(TAG, "sessionId = " + sessionId);
        Log.d(TAG, "conversationId = " + conversationId);
        Log.d(TAG, "scenicName = " + scenicName);
        Log.d(TAG, "scenicId = " + scenicId);
        Log.d(TAG, "scenicName=" + scenicName);
        Log.d(TAG, "scenicId=" + scenicId);
        Log.d(TAG, "contextType = " + contextType);
        Log.d(TAG, "contextName = " + contextName);
        Log.d(TAG, "autoQuestion = " + autoQuestion);
        Log.d(TAG, "areaName = " + areaName);
        Log.d(TAG, "areaId = " + areaId);
        Log.d(TAG, "areaCode = " + areaCode);
        Log.d(TAG, "spotName = " + spotName);
        Log.d(TAG, "spotId = " + spotId);
        Log.d(TAG, "mode = " + mode);
        Log.d(TAG, "isOnsiteGuide = " + isOnsiteGuide);
        Log.d(TAG, "startVisitGuide = " + startVisitGuide);
        Log.d(TAG, "allowEndVisit = " + allowEndVisit);
        Log.d(TAG, "trigger = " + trigger);
        Log.d(TAG, "distance = " + distance);
        Log.d(TAG, "latitude = " + latitude);
        Log.d(TAG, "longitude = " + longitude);
        Log.d(TAG, "action = " + intent.getAction());
        Log.d(TAG, "avatarId = " + avatarId);
        Log.d(TAG, "avatarName = " + avatarName);
        Log.d(TAG, "modelPath = " + modelPath);
        Log.d(TAG, "voiceId = " + voiceId);
        Log.d(TAG, "voiceName = " + voiceName);
        Log.d(TAG, "welcomeText = " + welcomeText);
        Log.d(TAG, "digitalHumanConfigJson = " + digitalHumanConfigJson);
        Log.d(TAG, "clothesMode = " + clothesMode);
        Log.d(TAG, "behaviorBackendBaseUrl = " + behaviorBackendBaseUrl);
        Log.d(TAG, "behaviorEventPath = " + behaviorEventPath);
        Log.d(TAG, "spotEnterPath = " + spotEnterPath);
        Log.d(TAG, "spotLeavePath = " + spotLeavePath);
        Log.d(TAG, "visitEndPath = " + visitEndPath);
        Log.d(TAG, "authToken = " + maskTokenForLog(authToken));
        logPersonalizedGuideWelcome();
    }

    private String getSafeExtra(Intent intent, String key) {
        if (intent == null || key == null) {
            return "";
        }

        Bundle extras = intent.getExtras();
        if (extras == null || !extras.containsKey(key)) {
            return "";
        }

        Object value = extras.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String firstNotEmpty(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }

        return "";
    }

    private RouteNode firstNotNull(RouteNode... nodes) {
        if (nodes == null) {
            return null;
        }
        for (RouteNode node : nodes) {
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    private String normalizeAvatarId(String value) {
        String text = firstNotEmpty(value, "guide_female_01");

        if ("guide_default_01".equals(text)) {
            return "guide_female_01";
        }

        if (isSupportedAvatarId(text)) {
            return text;
        }

        return "guide_female_01";
    }

    private String firstSupportedAvatarId(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String text = firstNotEmpty(value);
            if (isSupportedAvatarId(text)) {
                return normalizeAvatarId(text);
            }
        }
        return "";
    }

    private boolean isSupportedAvatarId(String value) {
        String text = firstNotEmpty(value);

        if ("guide_default_01".equals(text)) {
            return true;
        }

        return "guide_female_01".equals(text)
                || "guide_female_02".equals(text)
                || "guide_female_03".equals(text)
                || "guide_male_01".equals(text);
    }

    private String getDigitalHumanConfigText(String configJson, String... keys) {
        String text = safeString(configJson).trim();
        if (text.length() == 0 || keys == null) {
            return "";
        }

        try {
            JSONObject root = new JSONObject(text);
            String directValue = getJsonText(root, keys);
            if (directValue.length() > 0) {
                return directValue;
            }

            String[] objectKeys = new String[]{
                    "digitalHuman",
                    "digital_human",
                    "digitalHumanConfig",
                    "digital_human_config",
                    "avatar",
                    "data",
                    "config"
            };
            for (String objectKey : objectKeys) {
                JSONObject child = root.optJSONObject(objectKey);
                String childValue = getJsonText(child, keys);
                if (childValue.length() > 0) {
                    return childValue;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "[DigitalHuman] parse config failed: " + text, e);
        }

        return "";
    }

    private boolean parseBooleanLike(String value) {
        if (value == null) {
            return false;
        }
        String text = value.trim().toLowerCase(Locale.ROOT);
        return "true".equals(text)
                || "1".equals(text)
                || "yes".equals(text)
                || "y".equals(text);
    }

    private boolean shouldRunAutoQuestionOnLaunch() {
        String question = autoQuestion == null ? "" : autoQuestion.trim();
        if (question.length() == 0) {
            return false;
        }
        if (isOnsiteMode()) {
            Log.d(TAG, "现场导览启动阶段忽略自动问题，避免打开页面后请求 AI: " + question);
            return false;
        }
        return true;
    }

    private String getEffectiveVoiceId() {
        return firstNotEmpty(voiceId, "zhitian_emo");
    }

    private boolean isUsableGuideConversationId(String value) {
        if (value == null || value.trim().length() == 0) {
            return false;
        }

        String id = value.trim();

        // APP 登录会话 / 游客ID / 用户ID 都不能当 AI 对话ID
        if (id.startsWith("session_")
                || id.startsWith("visitor_")
                || id.startsWith("tourist_")
                || id.startsWith("android-live2d-")) {
            return false;
        }

        return true;
    }

    private String getEffectiveNativeUserId() {
        return firstValidBackendUserId(
                loginUserId,
                userId,
                appUserId,
                visitorId
        );
    }

    private String firstValidBackendUserId(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            String id = safeString(value).trim();
            if (isValidBackendUserId(id)) {
                return id;
            }
        }

        return "";
    }

    private boolean isValidBackendUserId(String value) {
        String id = safeString(value).trim();
        if (id.length() == 0) {
            return false;
        }

        String lower = id.toLowerCase(Locale.ROOT);
        return !"anonymous".equals(lower)
                && !lower.startsWith("anonymous")
                && !lower.startsWith("visitor_")
                && !lower.startsWith("android-live2d-");
    }

    private boolean hasAuthToken() {
        return safeString(authToken).trim().length() > 0;
    }

    private String buildAuthorizationHeader() {
        String token = safeString(authToken).trim();
        if (token.length() == 0) {
            return "";
        }
        if (token.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            return token;
        }
        return "Bearer " + token;
    }

    private void applyAuthorizationHeader(HttpURLConnection connection) {
        if (connection == null) {
            return;
        }

        String authorization = buildAuthorizationHeader();
        if (authorization.length() > 0) {
            connection.setRequestProperty("Authorization", authorization);
        }
    }

    private boolean ensureGuideAuthReady() {
        if (!hasAuthToken()) {
            showGuideAuthError("登录状态失效，请返回首页重新进入导览");
            Log.e(TAG, "[Auth] token 为空，不能请求需要登录态的导览接口");
            return false;
        }

        String realUserId = getEffectiveNativeUserId();
        if (realUserId.length() == 0) {
            showGuideAuthError("用户信息缺失，请返回首页重新进入导览");
            Log.e(TAG, "[Auth] userId 无效，不能请求需要登录态的导览接口"
                    + ", userId=" + userId
                    + ", loginUserId=" + loginUserId
                    + ", appUserId=" + appUserId
                    + ", visitorId=" + visitorId);
            return false;
        }

        if (isOnsiteMode() && safeString(visitId).trim().length() == 0) {
            showGuideAuthError("缺少 visitId，请重新从首页开启导览");
            Log.e(TAG, "[Auth] 现场导览缺少 visitId");
            return false;
        }

        return true;
    }

    private boolean ensureOnsiteCoreWriteReady() {
        updateGuideContext();

        if (safeString(visitId).trim().length() == 0
                || safeString(guideContext.visitId).trim().length() == 0) {
            showGuideAuthError("缺少 visitId，请重新从首页开启导览");
            Log.e(TAG, "[Auth] 现场导览核心写入缺少 visitId");
            return false;
        }

        if (!hasAuthToken()) {
            showGuideAuthError("登录状态失效，请返回首页重新进入导览");
            Log.e(TAG, "[Auth] token 为空，不能写入现场导览核心数据");
            return false;
        }

        String realUserId = getEffectiveNativeUserId();
        if (realUserId.length() == 0) {
            showGuideAuthError("用户信息缺失，请返回首页重新进入导览");
            Log.e(TAG, "[Auth] userId 无效，不能写入现场导览核心数据"
                    + ", userId=" + userId
                    + ", loginUserId=" + loginUserId
                    + ", appUserId=" + appUserId
                    + ", visitorId=" + visitorId);
            return false;
        }

        return true;
    }

    private boolean canWriteCoreVisitData(String logPrefix) {
        String prefix = firstNotEmpty(logPrefix, "[CoreVisitWrite]");
        String finalVisitId = firstNotEmpty(guideContext.visitId, visitId);
        String realUserId = getEffectiveNativeUserId();

        if (safeString(finalVisitId).trim().length() == 0) {
            Log.e(TAG, prefix + " 跳过写入：缺少 visitId");
            return false;
        }

        if (!hasAuthToken()) {
            Log.e(TAG, prefix + " 跳过写入：token 为空");
            return false;
        }

        if (realUserId.length() == 0) {
            Log.e(TAG, prefix + " 跳过写入：userId 无效"
                    + ", userId=" + userId
                    + ", loginUserId=" + loginUserId
                    + ", appUserId=" + appUserId
                    + ", visitorId=" + visitorId);
            return false;
        }

        guideContext.visitId = safeString(finalVisitId);
        guideContext.userId = realUserId;
        return true;
    }

    private void showGuideAuthError(String message) {
        String text = firstNotEmpty(message, "登录状态异常，请返回首页重新进入导览");
        if (guideStateText != null) {
            guideStateText.setText("登录异常");
        }
        if (voiceMainButton != null) {
            voiceMainButton.setText("🎙 长按说话");
        }
        if (answerText != null) {
            showGuideAnswer(text);
        }
        showToast(text);
    }

    private String maskTokenForLog(String token) {
        String text = safeString(token).trim();
        if (text.length() == 0) {
            return "(empty)";
        }
        if (text.length() <= 12) {
            return "*** len=" + text.length();
        }
        return text.substring(0, 6) + "..." + text.substring(text.length() - 4)
                + " len=" + text.length();
    }

    private void updateGuideContext() {
        guideContext.visitId = safeString(visitId);
        guideContext.userId = getEffectiveNativeUserId();
        guideContext.parkId = firstNotEmpty(parkId, areaCode);
        guideContext.parkName = firstNotEmpty(parkName, areaName);
        guideContext.scenicId = firstNotEmpty(scenicId, spotId);
        guideContext.scenicName = firstNotEmpty(scenicName, spotName, getTargetName());
        guideContext.entry = safeString(entry);
        guideContext.contextType = safeString(contextType);
        guideContext.autoQuestion = safeString(autoQuestion);
    }

    private void logGuideContext() {
        Log.d(TAG, "[GuideContext] visitId=" + guideContext.visitId
                + ", userId=" + guideContext.userId
                + ", parkId=" + guideContext.parkId
                + ", parkName=" + guideContext.parkName
                + ", scenicId=" + guideContext.scenicId
                + ", scenicName=" + guideContext.scenicName);
    }

    private void logAiQuestion(String question) {
        String finalQuestion = question == null ? "" : question;
        Log.d(TAG, "[AI Question] visitId=" + guideContext.visitId
                + ", userId=" + guideContext.userId
                + ", parkId=" + guideContext.parkId
                + ", scenicId=" + guideContext.scenicId
                + ", question=" + finalQuestion);
    }

    private void logPersonalizedGuideWelcome() {
        if (groupSize.length() == 0
                && travelType.length() == 0
                && visitPreference.length() == 0) {
            return;
        }

        String target = firstNotEmpty(parkName, getTargetName());
        StringBuilder builder = new StringBuilder();
        builder.append("欢迎来到").append(target);

        if (travelType.length() > 0) {
            builder.append("，我看到你这次是").append(travelType);
        }

        if (groupSize.length() > 0) {
            builder.append("，同行人数").append(groupSize);
        }

        if (visitPreference.length() > 0) {
            builder.append("，偏好").append(visitPreference);
        }

        builder.append("，我会为你推荐更合适的导览路线。");
        Log.d(TAG, "personalizedWelcome=" + builder);
    }

    private void resetSession() {
        // 如果外部明确传入可用的 AI 对话ID，就沿用它；否则每次新开数字人生成新的 guide 会话
        if (isUsableGuideConversationId(conversationId)) {
            sessionId = conversationId.trim();
            Log.d(TAG, "沿用已有 AI 对话 sessionId = " + sessionId
                    + ", appAuthSessionId = " + appAuthSessionId);
            return;
        }

        String base = getTargetName();
        if (base == null || base.trim().length() == 0) {
            base = "general";
        }

        sessionId = "guide-" + Math.abs(base.hashCode()) + "-" + System.currentTimeMillis();
        conversationId = sessionId;

        Log.d(TAG, "生成新的 AI 对话 sessionId = " + sessionId
                + ", appAuthSessionId = " + appAuthSessionId);
    }

    private String getTargetName() {
        if (scenicName != null && scenicName.trim().length() > 0) return scenicName.trim();
        if (spotName != null && spotName.trim().length() > 0) return spotName.trim();
        if (areaName != null && areaName.trim().length() > 0) return areaName.trim();
        if (contextName != null && contextName.trim().length() > 0) return contextName.trim();
        return "通用导览";
    }

    private boolean isOnsiteMode() {
        return isOnsiteGuide;
    }

    private boolean shouldShowEndVisitButton() {
        return isOnsiteMode()
                && allowEndVisit
                && !guideEnded;
    }

    private String getGuideTitleText() {
        if (isOnsiteMode()) {
            return "现场导览模式 · " + getTargetName();
        }
        if ("scenic_explain".equals(mode)) {
            return "AI 景区讲解";
        }
        if ("spot_explain".equals(mode)) {
            return "AI 景点讲解";
        }
        if ("route_planning".equals(mode)) {
            return "AI 路线规划助手";
        }
        return "AI 数字人导览助手";
    }

    private String getOnlineStatusText() {
        if (isOnsiteMode()) {
            return "● 现场";
        }
        return "● 在线";
    }

    private String getTopTargetText() {
        String target = getTargetName();

        if (isOnsiteMode()) {
            String text = "现场导览模式 · 已进入：" + target;

            String distanceText = safeString(distance).trim();
            if (distanceText.length() > 0 && !"null".equalsIgnoreCase(distanceText)) {
                text += " · 距离中心约 " + formatTopDistanceText(distanceText);
            }

            return text;
        }

        if ("scenic_explain".equals(mode)) {
            return "当前景区：" + target;
        }

        if ("spot_explain".equals(mode)) {
            return "当前景点：" + target;
        }

        if ("route_planning".equals(mode)) {
            return "正在为 " + target + " 规划路线";
        }

        if ("park".equals(contextType)) return "当前景区：" + target;
        if ("scenic".equals(contextType)) return "当前景点：" + target;
        if ("route".equals(contextType)) return "智能路线推荐";

        return "行前咨询模式 · 当前导览：" + target;
    }

    private String formatTopDistanceText(String distanceText) {
        String text = safeString(distanceText).trim();
        if (text.endsWith("米")
                || text.endsWith("m")
                || text.endsWith("M")
                || text.endsWith("公里")
                || text.endsWith("km")
                || text.endsWith("KM")) {
            return text;
        }
        return text + " 米";
    }

    private boolean fromMapEntry() {
        return "map-park-guide".equals(entry)
                || "map-scenic-guide".equals(entry)
                || "map".equals(trigger);
    }

    private String getAreaDisplayName() {
        if (areaName != null && areaName.trim().length() > 0) {
            return areaName.trim();
        }

        if ("park".equals(contextType) && contextName != null && contextName.trim().length() > 0) {
            return contextName.trim();
        }

        if (isOnsiteMode()) {
            return getTargetName();
        }

        return "当前景区";
    }

    private String getSpotDisplayName() {
        if (spotName != null && spotName.trim().length() > 0) {
            return spotName.trim();
        }

        if ("scenic".equals(contextType) && contextName != null && contextName.trim().length() > 0) {
            return contextName.trim();
        }

        if ("scenic".equals(contextType) && scenicName != null && scenicName.trim().length() > 0) {
            return scenicName.trim();
        }

        return "";
    }

    private String getMapCardTitleText() {
        if ("scenic".equals(contextType) || getSpotDisplayName().length() > 0) {
            return "景点位置与讲解";
        }

        if (isOnsiteMode()) {
            return "现场地图与路线";
        }

        if ("park".equals(contextType)) {
            return "景区地图与路线";
        }

        return "导览地图";
    }

    private String getMapCardStatusText() {
        if (isOnsiteMode()) {
            return "GPS 已识别";
        }

        if (fromMapEntry()) {
            return "地图已同步";
        }

        return "可查看";
    }

    private String getMapCardDescText() {
        String area = getAreaDisplayName();
        String spot = getSpotDisplayName();

        if (spot.length() > 0) {
            return "当前讲解：" + spot + "，所属景区：" + area;
        }

        if (isOnsiteMode()) {
            return "已识别你位于「" + area + "」，可结合当前位置进行现场讲解。";
        }

        if ("park".equals(contextType)) {
            return "当前景区：" + area + "，可继续咨询景区介绍、游览顺序和服务信息。";
        }

        return "可从景区详情或现场地图进入具体景区导览。";
    }

    private String getMapCardMetaText() {
        if (latitude != null && latitude.trim().length() > 0
                && longitude != null && longitude.trim().length() > 0) {
            return "定位：已同步 · 路线：等待景点坐标完善";
        }

        if (fromMapEntry()) {
            return "地图：已同步 · 路线：等待景点坐标完善";
        }

        return "定位：未同步 · 可返回 APP 查看景区地图";
    }

    private void updateMapCardText() {
        if (mapCardTitleText != null) {
            mapCardTitleText.setText(getMapCardTitleText());
        }

        if (mapCardStatusText != null) {
            mapCardStatusText.setText(getMapCardStatusText());
            mapCardStatusText.setTextColor(isOnsiteMode() ? Color.rgb(22, 163, 74) : Color.rgb(47, 128, 237));
        }

        if (mapCardDescText != null) {
            mapCardDescText.setText(getMapCardDescText());
        }

        if (mapCardMetaText != null) {
            mapCardMetaText.setText(getMapCardMetaText());
        }

        if (mapCardButton != null) {
            mapCardButton.setText("查看地图");
        }
    }

    private void handleMapCardClick() {
        showToast("请返回即境 APP 首页，点击「现场地图」查看景区地图");
    }

    private String getMapParkId() {
        if (areaCode != null && areaCode.trim().length() > 0) {
            return areaCode.trim();
        }

        if (scenicId != null && scenicId.trim().length() > 0 && scenicId.trim().startsWith("AREA_")) {
            return scenicId.trim();
        }

        if ("park".equals(contextType) && scenicId != null && scenicId.trim().length() > 0) {
            return scenicId.trim();
        }

        String target = getTargetName();
        String area = getAreaDisplayName();

        if ("灵山胜境".equals(target) || "灵山胜境".equals(area)) {
            return "AREA_0001";
        }

        if ("拈花湾禅意小镇".equals(target) || "拈花湾禅意小镇".equals(area)) {
            return "AREA_0002";
        }

        return "";
    }

    private void openUniAppMap(String parkId, String parkName) {
        try {
            String finalParkId = parkId == null ? "" : parkId.trim();
            String finalParkName = parkName == null ? "" : parkName.trim();

            String dataUrl =
                    "digitalhuman://map"
                            + "?parkId=" + urlEncode(finalParkId)
                            + "&areaCode=" + urlEncode(finalParkId)
                            + "&areaName=" + urlEncode(finalParkName)
                            + "&latitude=" + urlEncode(latitude)
                            + "&longitude=" + urlEncode(longitude)
                            + "&source=native-live2d";

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setClassName("com.rjb.digitalhuman", "io.dcloud.PandoraEntry");
            intent.setData(Uri.parse(dataUrl));

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION
            );

            showToast("正在打开景区地图");
            startActivity(intent);

            overridePendingTransition(0, 0);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "打开即境地图失败", e);
            showToast("打开地图失败，请确认即境已安装");
        }
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private String buildNearbyGuideQuestion() {
        String area = getAreaDisplayName();
        String spot = getSpotDisplayName();

        if (spot.length() > 0) {
            return "我现在正在了解" + spot + "，请继续介绍它附近还值得游览的景点和推荐游览顺序。";
        }

        if (isOnsiteMode()) {
            return "我已经到达" + area + "，请结合现场导览模式，介绍我附近值得优先游览的景点。";
        }

        return "请结合" + area + "，为我推荐几个值得优先游览的景点，并说明游览顺序。";
    }

    private void initTextToSpeechIfNeeded(final String reason) {
        Log.d(TAG, "[TTS] init requested reason=" + (reason == null ? "" : reason)
                + ", tts=" + (textToSpeech != null)
                + ", initialized=" + ttsInitialized
                + ", initializing=" + ttsInitializing);
        if (!ENABLE_NATIVE_TTS) return;
        if (ttsInitialized && textToSpeech != null) {
            Log.d(TAG, "[TTS] init skip already initialized");
            return;
        }
        if (ttsInitializing) {
            Log.d(TAG, "[TTS] init skip already initializing");
            return;
        }
        if (textToSpeech != null) {
            Log.w(TAG, "[TTS] broken instance detected, recreate");
            try {
                textToSpeech.stop();
            } catch (Exception e) {
                Log.e(TAG, "[TTS] stop broken instance failed", e);
            }
            try {
                textToSpeech.shutdown();
            } catch (Exception e) {
                Log.e(TAG, "[TTS] shutdown broken instance failed", e);
            }
            textToSpeech = null;
            ttsInitialized = false;
            ttsInitializing = false;
        }

        final String initReason = reason == null ? "" : reason;
        ttsInitializing = true;
        Log.d(TAG, "[TTS] init start reason=" + initReason);
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (ttsInitializing && !ttsInitialized) {
                    Log.e(TAG, "[TTS] init timeout, reset broken instance");
                    try {
                        if (textToSpeech != null) {
                            textToSpeech.stop();
                            textToSpeech.shutdown();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "[TTS] shutdown timeout instance failed", e);
                    }
                    textToSpeech = null;
                    ttsInitializing = false;
                    ttsInitialized = false;

                    if (pendingTtsText != null && pendingTtsText.trim().length() > 0) {
                        initTextToSpeechIfNeeded("timeout_retry");
                    }
                }
            }
        }, TTS_INIT_TIMEOUT_MS);
        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    if (textToSpeech == null) {
                        ttsInitialized = false;
                        ttsInitializing = false;
                        return;
                    }

                    int languageResult = textToSpeech.setLanguage(Locale.CHINA);
                    if (languageResult == TextToSpeech.LANG_MISSING_DATA
                            || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        languageResult = textToSpeech.setLanguage(Locale.SIMPLIFIED_CHINESE);
                    }
                    if (languageResult == TextToSpeech.LANG_MISSING_DATA
                            || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        languageResult = textToSpeech.setLanguage(Locale.CHINESE);
                    }
                    if (languageResult == TextToSpeech.LANG_MISSING_DATA
                            || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        languageResult = textToSpeech.setLanguage(Locale.getDefault());
                    }
                    Log.d(TAG, "[TTS] setLanguage result=" + languageResult);

                    textToSpeech.setSpeechRate(1.0f);
                    textToSpeech.setPitch(1.0f);
                    textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String utteranceId) {
                            Log.d(TAG, "[TTS] onStart utteranceId=" + utteranceId);
                        }

                        @Override
                        public void onDone(String utteranceId) {
                            Log.d(TAG, "[TTS] onDone utteranceId=" + utteranceId);
                            handleTtsFinished(utteranceId);
                        }

                        @Override
                        public void onError(String utteranceId) {
                            Log.e(TAG, "[TTS] onError utteranceId=" + utteranceId);
                            handleTtsFinished(utteranceId);
                        }
                    });

                    ttsInitialized = true;
                    ttsInitializing = false;
                    Log.d(TAG, "[TTS] onInit success, reason=" + initReason);

                    String pending = pendingTtsText;
                    String reasonText = pendingTtsReason;
                    if (pending != null && pending.trim().length() > 0) {
                        pendingTtsText = "";
                        pendingTtsReason = "";
                        pendingTtsDurationMs = 0L;
                        Log.d(TAG, "[TTS] flush pending text length=" + pending.length());
                        speakTextInternal(pending, reasonText != null && reasonText.length() > 0
                                ? reasonText
                                : "pending_flush");
                    }
                } else {
                    ttsInitialized = false;
                    ttsInitializing = false;
                    Log.w(TAG, "[TTS] onInit failed status=" + status);
                    try {
                        if (textToSpeech != null) {
                            textToSpeech.shutdown();
                        }
                    } catch (Exception ignored) {
                    }
                    textToSpeech = null;
                }
            }
        });
    }

    private void speakText(String text) {
        if (!ENABLE_NATIVE_TTS) return;
        if (text == null || text.trim().length() == 0) return;

        final String textToSpeak = text.trim();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "[TTS] speak requested textLength=" + textToSpeak.length()
                        + ", initialized=" + ttsInitialized
                        + ", initializing=" + ttsInitializing);

                if (!ttsInitialized || textToSpeech == null) {
                    long estimatedDurationMs = estimateSpeechDurationMs(textToSpeak);
                    pendingTtsText = textToSpeak;
                    pendingTtsReason = "fallback_or_pending_audio";
                    pendingTtsDurationMs = estimatedDurationMs;
                    Log.d(TAG, "[TTS] pending text saved length=" + textToSpeak.length());
                    initTextToSpeechIfNeeded("speakText");
                    startMouthSyncWithText(textToSpeak, estimatedDurationMs, android.os.SystemClock.uptimeMillis(), "");
                    Log.d(TAG, "[MouthSync] audioUrl=, frames=0, fallbackText=" + safeLogText(textToSpeak));
                    return;
                }

                speakTextInternal(textToSpeak, "direct");
            }
        });
    }

    private void speakTextInternal(String text, String reason) {
        if (!ENABLE_NATIVE_TTS) return;
        if (text == null || text.trim().length() == 0) return;
        if (textToSpeech == null) {
            Log.w(TAG, "[TTS] speak internal skipped, textToSpeech=null");
            return;
        }

        final String textToSpeak = text.trim();
        final String speakReason = reason == null || reason.trim().length() == 0
                ? "direct"
                : reason;
        final String utteranceId = "guide_tts_" + System.currentTimeMillis();
        currentTtsUtteranceId = utteranceId;
        long estimatedDurationMs = pendingTtsDurationMs > 0L
                ? pendingTtsDurationMs
                : estimateSpeechDurationMs(textToSpeak);
        startMouthSyncWithText(textToSpeak, estimatedDurationMs, android.os.SystemClock.uptimeMillis(), "");
        Log.d(TAG, "[MouthSync] audioUrl=, frames=0, fallbackText=" + safeLogText(textToSpeak));
        Log.d(TAG, "[TTS] speak internal textLength=" + textToSpeak.length()
                + ", reason=" + speakReason);

        int speakResult;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Bundle params = new Bundle();
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
            speakResult = textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
        } else {
            HashMap<String, String> params = new HashMap<String, String>();
            params.put(TextToSpeech.Engine.KEY_PARAM_VOLUME, "1.0");
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            speakResult = textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params);
        }

        Log.d(TAG, "[TTS] speak internal result=" + speakResult);

        if (speakResult == TextToSpeech.ERROR) {
            Log.e(TAG, "[TTS] speak internal failed result=" + speakResult);
            stopMouthSync();
            returnDigitalHumanToIdle();
            return;
        }

        if (mainHandler != null) {
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (utteranceId.equals(currentTtsUtteranceId) && mediaPlayer == null) {
                        handleTtsFinished(utteranceId);
                    }
                }
            }, estimatedDurationMs + 700L);
        }
    }

    private void playAnswerVoice(GuideResponse guideResponse) {
        if (guideResponse == null) return;
        if (guideEnded) {
            Log.d(TAG, "[AudioPlay] guide ended, skip voice playback");
            return;
        }
        String audioUrl = guideResponse.audioUrl == null ? "" : guideResponse.audioUrl.trim();
        String audioStatus = guideResponse.audioStatus == null ? "" : guideResponse.audioStatus.trim();
        Log.d(TAG, "[AudioPlay] final response audioStatus=" + safeString(audioStatus)
                + ", audioUrl=" + safeString(audioUrl)
                + ", ttsTaskId=" + safeString(guideResponse.ttsTaskId)
                + ", mouthFrames=" + (guideResponse.mouthFrames == null ? 0 : guideResponse.mouthFrames.size()));
        if (audioUrl.length() > 0) {
            playAudioUrl(audioUrl, guideResponse);
            return;
        }
        if ("PENDING".equalsIgnoreCase(audioStatus)) {
            Log.d(TAG, "[AudioPlay] audio pending, fallback local tts");
            applyGuideResponseDigitalHuman(guideResponse, "explain", "warm");
            speakText(guideResponse.answer);
            return;
        }
        if (guideResponse.ttsError != null && guideResponse.ttsError.trim().length() > 0) {
            Log.w(TAG, "TTS error from AI service: " + guideResponse.ttsError);
        }
        Log.d(TAG, "[TTS] fallback speak");
        applyGuideResponseDigitalHuman(guideResponse, "explain", "warm");
        speakText(guideResponse.answer);
    }

    private void playAudioUrl(String audioUrl, final GuideResponse guideResponse) {
        try {
            stopCurrentAudio();
            String finalUrl = resolveAudioUrl(audioUrl);
            Log.d(TAG, "[AudioPlay] rawUrl=" + safeString(audioUrl));
            Log.d(TAG, "[AudioPlay] resolvedUrl=" + safeString(finalUrl));
            if (finalUrl.length() == 0) {
                Log.d(TAG, "[TTS] fallback speak");
                applyGuideResponseDigitalHuman(guideResponse, "explain", "warm");
                speakText(guideResponse == null ? "" : guideResponse.answer);
                return;
            }
            final String finalAudioUrl = finalUrl;
            final List<MouthSyncManager.MouthFrame> mouthFrames = guideResponse == null ? null : guideResponse.mouthFrames;
            final List<MouthSyncController.MouthFrame> controllerMouthFrames = guideResponse == null ? null : guideResponse.controllerMouthFrames;
            Log.d(TAG, "播放后端 TTS 音频: " + finalUrl);
            Log.d(TAG, "开始播放音频，mouthFrames 数量: " + (mouthFrames == null ? 0 : mouthFrames.size()));

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setVolume(1f, 1f);
            mediaPlayer.setDataSource(finalUrl);
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    Log.d(TAG, "[AudioPlay] onPrepared start");
                    guideStateText.setText("正在讲解");
                    voiceMainButton.setText("数字人正在讲解...");
                    try {
                        mp.start();
                    } catch (Exception e) {
                        Log.e(TAG, "[AudioPlay] start failed", e);
                        stopMouthSync();
                        returnDigitalHumanToIdle();
                        return;
                    }
                    long startUptimeMs = android.os.SystemClock.uptimeMillis();
                    long durationMs = guideResponse == null ? 0L : guideResponse.audioDurationMs;
                    if (durationMs <= 0L) {
                        try {
                            durationMs = mp.getDuration();
                        } catch (Exception ignored) {
                        }
                    }
                    if (hasMouthFrames(mouthFrames, controllerMouthFrames)) {
                        startMouthSyncWithFrames(mouthFrames, controllerMouthFrames, durationMs, startUptimeMs);
                        Log.d(TAG, "[MouthSync] audioUrl=" + finalAudioUrl
                                + ", frames=" + Math.max(mouthFrames == null ? 0 : mouthFrames.size(), controllerMouthFrames == null ? 0 : controllerMouthFrames.size())
                                + ", fallbackText=");
                    } else {
                        String answer = guideResponse == null ? "" : guideResponse.answer;
                        startMouthSyncWithText(answer, durationMs, startUptimeMs, finalAudioUrl);
                        Log.d(TAG, "[MouthSync] audioUrl=" + finalAudioUrl
                                + ", frames=0, fallbackText=" + safeLogText(answer));
                    }
                    applyGuideResponseDigitalHuman(guideResponse, "explain", "warm");
                    forceRenderLive2D();
                }
            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Log.d(TAG, "[AudioPlay] onCompletion");
                    stopMouthSync();
                    returnDigitalHumanToIdle();
                    guideStateText.setText("讲解完成");
                    voiceMainButton.setText("🎙 长按说话");
                    stopCurrentAudio();
                }
            });
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    stopMouthSync();
                    returnDigitalHumanToIdle();
                    Log.e(TAG, "[AudioPlay] onError what=" + what
                            + ", extra=" + extra
                            + ", url=" + finalAudioUrl);
                    guideStateText.setText("语音播放失败");
                    voiceMainButton.setText("🎙 长按说话");
                    stopCurrentAudio();
                    applyGuideResponseDigitalHuman(guideResponse, "explain", "warm");
                    Log.d(TAG, "[TTS] fallback speak");
                    speakText(guideResponse == null ? "" : guideResponse.answer);
                    return true;
                }
            });
            Log.d(TAG, "[AudioPlay] start prepareAsync");
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "播放音频异常", e);
            guideStateText.setText("语音播放异常");
            voiceMainButton.setText("🎙 长按说话");
            stopMouthSync();
            returnDigitalHumanToIdle();
            stopCurrentAudio();
            applyGuideResponseDigitalHuman(guideResponse, "explain", "warm");
            Log.d(TAG, "[TTS] fallback speak");
            speakText(guideResponse == null ? "" : guideResponse.answer);
        }
    }

    private boolean hasMouthFrames(List<MouthSyncManager.MouthFrame> mouthFrames) {
        return mouthFrames != null && mouthFrames.size() > 0;
    }

    private boolean hasMouthFrames(List<MouthSyncManager.MouthFrame> mouthFrames,
                                   List<MouthSyncController.MouthFrame> controllerMouthFrames) {
        return hasMouthFrames(mouthFrames)
                || (controllerMouthFrames != null && controllerMouthFrames.size() > 0);
    }

    private void startMouthSyncWithFrames(List<MouthSyncManager.MouthFrame> frames,
                                          List<MouthSyncController.MouthFrame> controllerFrames,
                                          long durationMs,
                                          long startUptimeMs) {
        List<MouthSyncManager.MouthFrame> safeFrames = frames == null
                ? new ArrayList<MouthSyncManager.MouthFrame>()
                : new ArrayList<MouthSyncManager.MouthFrame>(frames);
        MouthSyncManager.getInstance().start(safeFrames);
        List<MouthSyncController.MouthFrame> safeControllerFrames = controllerFrames == null || controllerFrames.size() == 0
                ? toControllerMouthFrames(safeFrames)
                : new ArrayList<MouthSyncController.MouthFrame>(controllerFrames);
        MouthSyncController.getInstance().startWithFrames(safeControllerFrames, durationMs, startUptimeMs);
    }

    private void startMouthSyncWithText(String text, long durationMs, long startUptimeMs, String audioUrl) {
        if (text == null || text.trim().length() == 0) {
            return;
        }
        long safeDurationMs = durationMs > 0L ? durationMs : estimateSpeechDurationMs(text);
        List<MouthSyncManager.MouthFrame> pseudoFrames = buildPseudoMouthFrames(text, safeDurationMs);
        MouthSyncManager.getInstance().start(pseudoFrames);
        MouthSyncController.getInstance().startWithText(text, safeDurationMs, startUptimeMs);
    }

    private List<MouthSyncController.MouthFrame> toControllerMouthFrames(List<MouthSyncManager.MouthFrame> frames) {
        List<MouthSyncController.MouthFrame> result = new ArrayList<MouthSyncController.MouthFrame>();
        if (frames == null) {
            return result;
        }
        for (MouthSyncManager.MouthFrame frame : frames) {
            if (frame == null) {
                continue;
            }
            result.add(new MouthSyncController.MouthFrame(frame.t, frame.open, frame.form));
        }
        return result;
    }

    private List<MouthSyncManager.MouthFrame> buildPseudoMouthFrames(String text, long durationMs) {
        List<MouthSyncManager.MouthFrame> frames = new ArrayList<MouthSyncManager.MouthFrame>();
        String safeText = text == null ? "" : text.trim();
        if (safeText.length() == 0) {
            safeText = "数字人导览";
        }

        long targetDuration = Math.max(900L, Math.min(18000L, durationMs > 0L ? durationMs : estimateSpeechDurationMs(safeText)));
        long cursor = 0L;
        float smoothOpen = 0.18f;

        for (int i = 0; i < safeText.length(); i++) {
            char c = safeText.charAt(i);
            if (isSpeechPausePunctuation(c)) {
                frames.add(new MouthSyncManager.MouthFrame((int) cursor, smoothOpen * 0.35f, 0.0f));
                cursor += getSpeechPauseDurationMs(c);
                continue;
            }

            float targetOpen = 0.16f + ((i % 5) * 0.13f) + ((Math.abs(c) % 4) * 0.04f);
            if (targetOpen > 0.9f) {
                targetOpen = 0.9f;
            }
            smoothOpen = smoothOpen + (targetOpen - smoothOpen) * 0.55f;
            frames.add(new MouthSyncManager.MouthFrame((int) cursor, smoothOpen, 0.0f));
            cursor += 85L + (Math.abs(c) % 35);
        }

        if (cursor <= 0L) {
            cursor = targetDuration;
        }
        float scale = targetDuration / (float) cursor;
        for (MouthSyncManager.MouthFrame frame : frames) {
            frame.t = Math.round(frame.t * scale);
        }
        frames.add(new MouthSyncManager.MouthFrame((int) targetDuration, 0.08f, 0.0f));
        frames.add(new MouthSyncManager.MouthFrame((int) (targetDuration + 180L), 0.0f, 0.0f));
        return frames;
    }

    private long estimateSpeechDurationMs(String text) {
        String safeText = text == null ? "" : text.trim();
        if (safeText.length() == 0) {
            return 1200L;
        }
        long base = safeText.length() * 180L;
        int pauseCount = 0;
        for (int i = 0; i < safeText.length(); i++) {
            if (isSpeechPausePunctuation(safeText.charAt(i))) {
                pauseCount++;
            }
        }
        return Math.max(1200L, Math.min(20000L, base + pauseCount * 180L));
    }

    private boolean isSpeechPausePunctuation(char c) {
        return c == '，' || c == '、' || c == '：' || c == '；'
                || c == ',' || c == ';' || c == ':'
                || c == '。' || c == '！' || c == '？'
                || c == '.' || c == '!' || c == '?';
    }

    private long getSpeechPauseDurationMs(char c) {
        if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
            return 300L;
        }
        return 150L;
    }

    private void stopMouthSync() {
        MouthSyncManager.getInstance().stop();
        MouthSyncController.getInstance().stopAndReset();
    }

    private void handleTtsFinished(final String utteranceId) {
        if (utteranceId == null || !utteranceId.equals(currentTtsUtteranceId)) {
            return;
        }
        currentTtsUtteranceId = "";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stopMouthSync();
                returnDigitalHumanToIdle();
                if (!guideEnded) {
                    if (guideStateText != null) {
                        guideStateText.setText("讲解完成");
                    }
                    if (voiceMainButton != null) {
                        voiceMainButton.setText("🎙 长按说话");
                    }
                }
            }
        });
    }

    private void applyGuideResponseDigitalHuman(GuideResponse guideResponse, String fallbackAction, String fallbackEmotion) {
        String action = guideResponse == null ? "" : firstNotEmpty(guideResponse.action, guideResponse.actionCode);
        String emotion = guideResponse == null ? "" : firstNotEmpty(guideResponse.emotion, guideResponse.emotionCode);
        applyDigitalHumanState(firstNotEmpty(action, fallbackAction), firstNotEmpty(emotion, fallbackEmotion));
    }

    private void applyDigitalHumanState(String action, String emotion) {
        String safeAction = firstNotEmpty(action, "idle");
        String safeEmotion = firstNotEmpty(emotion, "neutral");
        Log.d(TAG, "[DigitalHumanAction] action=" + safeAction + ", emotion=" + safeEmotion);
        try {
            DigitalHumanActionController.getInstance().apply(safeAction, safeEmotion);
        } catch (Throwable e) {
            Log.e(TAG, "[DigitalHumanAction] apply failed action=" + safeAction + ", emotion=" + safeEmotion, e);
        }
        forceRenderLive2D();
    }

    private void returnDigitalHumanToIdle() {
        try {
            DigitalHumanActionController.getInstance().returnToIdle();
            Log.d(TAG, "[DigitalHumanAction] action=idle, emotion=neutral");
        } catch (Throwable e) {
            Log.e(TAG, "[DigitalHumanAction] return idle failed", e);
        }
        forceRenderLive2D();
    }

    private String safeLogText(String text) {
        String value = safeString(text).replace("\n", " ").replace("\r", " ").trim();
        return value.length() > 36 ? value.substring(0, 36) + "..." : value;
    }

    private String resolveAudioUrl(String audioUrl) {
        if (audioUrl == null) return "";
        String url = audioUrl.trim();
        try {
            URL chatUrl = new URL(GUIDE_CHAT_URL);
            String baseUrl = chatUrl.getProtocol() + "://" + chatUrl.getHost();
            if (chatUrl.getPort() > 0) baseUrl += ":" + chatUrl.getPort();

            if (url.startsWith("//")) {
                return chatUrl.getProtocol() + ":" + url;
            }

            if (url.startsWith("http://") || url.startsWith("https://")) {
                URL parsed = new URL(url);
                String host = parsed.getHost();
                if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) {
                    String hostBase = chatUrl.getProtocol() + "://" + chatUrl.getHost();
                    if (parsed.getPort() > 0) {
                        hostBase += ":" + parsed.getPort();
                    } else if (chatUrl.getPort() > 0) {
                        hostBase += ":" + chatUrl.getPort();
                    }
                    String file = parsed.getFile();
                    return hostBase + (file == null ? "" : file);
                }
                return url;
            }

            if (url.startsWith("/")) return baseUrl + url;
            return baseUrl + "/" + url;
        } catch (Exception e) {
            return url;
        }
    }

    private void stopCurrentAudio() {
        stopMouthSync();
        currentTtsUtteranceId = "";
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception ignored) {
            mediaPlayer = null;
        }
        try {
            if (textToSpeech != null) textToSpeech.stop();
        } catch (Exception ignored) {
        }
    }

    private String readStream(InputStream inputStream) throws Exception {
        if (inputStream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) builder.append(line);
        reader.close();
        return builder.toString();
    }

    private Button createQuickButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setTextColor(Color.rgb(30, 100, 180));
        button.setAllCaps(false);
        button.setBackground(createRoundBg(Color.rgb(232, 243, 255), dp(18)));
        return button;
    }

    private Button createMiniActionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setTextColor(Color.rgb(30, 100, 180));
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setBackground(createRoundBg(Color.rgb(225, 240, 255), dp(16)));
        return button;
    }

    private GradientDrawable createRoundBg(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable createStrokeRoundBg(int color, int strokeColor, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private void scrollAnswerToTop() {
        if (answerScrollView == null) return;
        answerScrollView.post(new Runnable() {
            @Override
            public void run() {
                answerScrollView.fullScroll(View.FOCUS_UP);
            }
        });
    }

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void hideKeyboard() {
        try {
            InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            View currentFocus = getCurrentFocus();
            if (manager != null && currentFocus != null) {
                manager.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        } else {
            if (getWindow().getInsetsController() != null) {
                getWindow().getInsetsController().hide(
                        WindowInsets.Type.navigationBars() | WindowInsets.Type.statusBars()
                );
                getWindow().getInsetsController().setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        }
    }

    private void forceRenderLive2D() {
        try {
            if (glSurfaceView != null) {
                glSurfaceView.onResume();
                glSurfaceView.requestRender();
            }
        } catch (Exception e) {
            Log.e(TAG, "强制刷新 Live2D 失败", e);
        }
    }

    private void applyAvatarToLive2D(final String reason) {
        final String finalAvatarId = normalizeAvatarId(firstNotEmpty(avatarId, modelPath, "guide_female_01"));
        final String finalClothesMode = firstNotEmpty(clothesMode, "");

        if (glSurfaceView != null) {
            glSurfaceView.queueEvent(new Runnable() {
                @Override
                public void run() {
                    try {
                        LAppLive2DManager.getInstance().changeSceneByAvatarId(finalAvatarId, finalClothesMode);
                        Log.d(TAG, "已切换 Live2D 数字人: avatarId=" + finalAvatarId
                                + ", clothesMode=" + finalClothesMode
                                + ", reason=" + reason);

                        if (glSurfaceView != null) {
                            glSurfaceView.requestRender();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "切换 Live2D 数字人失败: avatarId=" + finalAvatarId
                                + ", clothesMode=" + finalClothesMode
                                + ", reason=" + reason, e);
                    }
                }
            });
        } else {
            Log.d(TAG, "glSurfaceView 为空，暂不切换数字人: avatarId=" + finalAvatarId
                    + ", clothesMode=" + finalClothesMode
                    + ", reason=" + reason);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            voiceFlowActive = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showToast("录音权限已开启，请重新长按说话");
            } else {
                showToast("需要录音权限才能使用语音导览");
            }
            forceRenderLive2D();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleGuideIntent(intent);
        resetSession();
        hasPlayedWelcome = false;
        voiceFlowActive = false;

        // Activity 被复用打开时，根据新的 avatarId 重新切换数字人
        applyAvatarToLive2D("onNewIntent");

        if (titleText != null) titleText.setText(getGuideTitleText());
        if (onlineText != null) {
            onlineText.setText(getOnlineStatusText());
            onlineText.setTextColor(isOnsiteMode() ? Color.rgb(24, 179, 104) : Color.rgb(47, 128, 237));
        }
        if (targetText != null) targetText.setText(getTopTargetText());
        if (endVisitButton != null) {
            endVisitButton.setVisibility(shouldShowEndVisitButton() ? View.VISIBLE : View.GONE);
            endVisitButton.setEnabled(true);
            endVisitButton.setText("结束导览");
        }
        updateMapCardText();
        updateRouteStartRow();
        updateSimulatedSpotButton();
        if (guideStateText != null) guideStateText.setText("已切换导览对象");
        if (questionInput != null) questionInput.setText("");
        if (answerText != null) showWelcomeMessage();

        if (shouldRunAutoQuestionOnLaunch()) {
            questionInput.setText(autoQuestion);
            askGuide(autoQuestion);
        }
    }

    @Override
    public void onBackPressed() {
        if (routeMapModeActive) {
            exitRouteMapMode();
            return;
        }
        if (guideEnded) {
            finish();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onStart() {
        super.onStart();
        LAppDelegate.getInstance().onStart(this);

        // Activity 首次启动时，根据 uni-app 传入的 avatarId 切换数字人
        applyAvatarToLive2D("onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (routeMapView != null) {
            routeMapView.onResume();
        }
        forceRenderLive2D();
        hideSystemBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (routeMapView != null) {
            routeMapView.onPause();
        }
        if (voiceFlowActive || recording) {
            Log.d(TAG, "语音流程中，跳过 Live2D onPause，避免黑屏");
            return;
        }
        if (glSurfaceView != null) glSurfaceView.onPause();
        LAppDelegate.getInstance().onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (routeMapView != null) {
            routeMapView.onSaveInstanceState(outState);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (voiceFlowActive || recording) {
            Log.d(TAG, "语音流程中，跳过 Live2D onStop，避免黑屏");
            return;
        }
        LAppDelegate.getInstance().onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseRecorder();
        stopCurrentAudio();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        ttsInitialized = false;
        ttsInitializing = false;
        pendingTtsText = "";
        pendingTtsReason = "";
        pendingTtsDurationMs = 0L;
        if (routeMapView != null) {
            routeMapView.onDestroy();
            routeMapView = null;
            routeAMap = null;
        }
        LAppDelegate.getInstance().onDestroy();
    }

    private static class GuideResponse {
        String questionText = "";
        String answer = "";
        String audioUrl = "";
        String audioStatus = "";
        String ttsTaskId = "";
        long audioDurationMs = 0L;
        String conversationId = "";
        String messageId = "";
        String ttsError = "";
        String action = "";
        String actionCode = "";
        String emotion = "";
        String emotionCode = "";
        RouteInfo route;
        List<String> suggestions = new ArrayList<>();
        List<MouthSyncManager.MouthFrame> mouthFrames = new ArrayList<>();
        List<MouthSyncController.MouthFrame> controllerMouthFrames = new ArrayList<>();
    }

    private static class VisitEndResult {
        boolean success = false;
        String visitId = "";
        String reportVisitId = "";
    }

    private static class RouteInfo {
        String planId = "";
        String routeName = "";
        String reason = "";
        String totalDistanceM = "";
        String estimatedDurationMin = "";
        String mapAction = "";
        String routeMapReady = "";
        RoutePreviewData preview;
        List<LatLng> rawPolylinePoints = new ArrayList<>();
        List<RouteNode> nodes = new ArrayList<>();
    }

    private static class RouteNode {
        String id = "";
        String name = "";
        String order = "";
        int orderNumber = 0;
        String scenicId = "";
        String spotId = "";
        String spotName = "";
        String scenicName = "";
        String guideText = "";
        String recommendedStayMin = "";
        String latitude = "";
        String longitude = "";
        double latitudeValue = Double.NaN;
        double longitudeValue = Double.NaN;
        boolean hasLocation = false;
    }

    private static class RoutePreviewData {
        String routeName = "";
        RouteNode routePreviewStartPoint;
        List<RouteNode> nodes = new ArrayList<>();
        List<LatLng> polylinePoints = new ArrayList<>();
        float totalDistanceMeter = 0f;
        long totalDurationSecond = 0L;
        boolean amapRouteReady = false;
        boolean partialFallback = false;
        boolean calculating = false;
        String message = "";
    }

    private static class RouteSegmentResult {
        List<LatLng> points = new ArrayList<>();
        float distanceMeter = 0f;
        long durationSecond = 0L;
        boolean fallbackLine = false;
    }

    private static class RouteSegmentRequestState {
        int requestSeq = 0;
        int totalSegmentCount = 0;
        int completedSegmentCount = 0;
        RoutePreviewData preview;
        List<RouteNode> segmentNodes = new ArrayList<>();
        Map<Integer, RouteSegmentResult> results = new HashMap<>();
        List<RouteSearchV2> searches = new ArrayList<>();
    }

    private static class RouteStartSpot {
        String spotId = "";
        String scenicId = "";
        String spotName = "";
        String latitude = "";
        String longitude = "";
    }

    private static class GuideContext {
        String visitId = "";
        String userId = "";
        String parkId = "";
        String parkName = "";
        String scenicId = "";
        String scenicName = "";
        String entry = "";
        String contextType = "";
        String autoQuestion = "";
    }
}
