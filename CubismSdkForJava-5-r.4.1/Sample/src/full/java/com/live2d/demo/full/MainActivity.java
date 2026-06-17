/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.demo.full;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.NdefMessage;
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
import java.io.IOException;
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
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

public class MainActivity extends Activity {
    private static final String TAG = "Live2DGuide";
    private static final String GUIDE_SOURCE = "native-live2d-guide";

    // 改成你当前小后端电脑的真实 IPv4 地址
    private static final String GUIDE_CHAT_URL = "http://10.18.112.131:8080/api/guide/chat";
    private static final String GUIDE_VOICE_CHAT_URL = "http://10.18.112.131:8080/api/guide/voice/chat";
    // 旧路线推荐接口保留常量但不再主动调用；路线问题统一走 Chat 接口。
    private static final String GUIDE_ROUTE_RECOMMEND_URL = "http://10.18.112.131:8080/api/guide/route/recommend";
    private static final String DEFAULT_BEHAVIOR_EVENT_PATH = "/api/app/behavior/event";
    private static final String DEFAULT_SPOT_ENTER_PATH = "/api/visit/spot/enter";
    private static final String DEFAULT_SPOT_LEAVE_PATH = "/api/visit/spot/leave";
    private static final String DEFAULT_VISIT_END_PATH = "/api/visit/end";
    private static final String DEFAULT_APP_VISIT_END_PATH = "/api/app/visit/end";
    private static final String DEMO_LOCATION_SOURCE = "demo-route-node";
    private static final String DEMO_TRIGGER = "route-node-demo";
    private static final String ROUTE_EVENT_SOURCE = "android_live2d";
    private static final String ROUTE_MODE_DYNAMIC_IN_PARK = "DYNAMIC_IN_PARK";
    private static final String ROUTE_MODE_OFFICIAL_TEMPLATE = "OFFICIAL_TEMPLATE";
    private static final String VISIT_STATUS_IN_PARK = "IN_PARK";
    private static final String VISIT_STATUS_NOT_ARRIVED = "NOT_ARRIVED";
    private static final long SPEAKING_MOTION_INTERVAL_MS = 6500L;
    private static final long OFFLINE_PACKAGE_CHECK_INTERVAL_MS = 10L * 60L * 1000L;
    private static final String OFFLINE_PACKAGE_PREFS = "offline_package_check";
    private static final String PREF_LAST_OFFLINE_PACKAGE_CHECK_AREA_ID = "lastOfflinePackageCheckAreaId";
    private static final String PREF_LAST_OFFLINE_PACKAGE_CHECK_AT = "lastOfflinePackageCheckAt";
    private static final long OFFLINE_NFC_SYNC_INTERVAL_MS = 30L * 1000L;

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 2001;

    // 只作为兜底欢迎语 / audioUrl 为空时使用。正式回答优先播放后端 audioUrl。
    private static final boolean ENABLE_NATIVE_TTS = true;
    private static final long TTS_INIT_TIMEOUT_MS = 3000L;
    private static final MediaType GUIDE_CHAT_JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final long SPEECH_MISSING_FALLBACK_DELAY_MS = 6000L;

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
    private String lastReportedRouteUseId = "";

    private TextToSpeech textToSpeech;
    private MediaPlayer mediaPlayer;
    private MediaRecorder mediaRecorder;
    private final OkHttpClient guideSseHttpClient = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build();
    private Call currentGuideSseCall;
    private final Object speechQueueLock = new Object();
    private final TreeMap<Integer, SpeechChunk> pendingSpeechChunks = new TreeMap<Integer, SpeechChunk>();
    private final Set<Integer> skippedSpeechChunkIndexes = new HashSet<Integer>();
    private final Set<String> receivedSpeechChunkKeys = new HashSet<String>();
    private final Set<String> playedSpeechChunkKeys = new HashSet<String>();
    private final Set<String> acceptedMessageIds = new HashSet<String>();
    private final Set<String> canceledMessageIds = new HashSet<String>();
    private final StringBuilder currentAnswerBuilder = new StringBuilder();

    private File currentAudioFile;

    private boolean ttsInitialized = false;
    private boolean ttsInitializing = false;
    private boolean requesting = false;
    private boolean hasPlayedWelcome = false;
    private boolean recording = false;
    private boolean voiceFlowActive = false;
    private boolean backendAudioSpeaking = false;
    private boolean textGenerating = false;
    private boolean speechGenerating = false;
    private boolean speechPlaying = false;
    private boolean serverDone = false;
    private boolean speechDoneReceived = false;
    private boolean currentSseAnswerCommitted = false;
    private boolean routeReadyThisTurn = false;
    private long backendAudioStartMs = 0L;
    private long lastSpeakingMotionMs = 0L;
    private int nextSpeechIndexToPlay = 0;
    private int currentSpeechChunkCount = 0;
    private int missingFallbackScheduledIndex = -1;
    private String missingFallbackScheduledMessageId = "";
    private int activeSseRoundSeq = 0;
    private String contextMessageId = "";
    private String responseMessageId = "";
    private String currentMessageId = "";
    private String canceledMessageId = "";
    private String currentTraceId = "";
    private String currentSseQuestion = "";

    private final Runnable speakingMotionRunnable = new Runnable() {
        @Override
        public void run() {
            handleSpeakingMotionTick();
        }
    };

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
    private String estimatedDuration = "";
    private String availableMinutes = "";

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
    private String visitStatus = "";
    private String isInsideArea = "";
    private String locationContext = "";

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
    private String aiBaseUrl = "";
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

    // ==================== NFC / Offline Guide ====================
    private boolean nfcOfflineGuideEnabled = true;
    private boolean nfcInitialized = false;
    private NfcAdapter nfcAdapter;
    private PendingIntent nfcPendingIntent;
    private IntentFilter[] nfcIntentFilters;
    private String[][] nfcTechLists;
    private boolean nfcReaderModeActive = false;

    private OfflineGuidePackageManager offlinePackageManager;
    private OfflineBehaviorQueue offlineBehaviorQueue;
    private OfflineNfcEventQueue offlineNfcEventQueue;
    private NetworkStateHelper networkStateHelper;
    private long lastOfflineNfcSyncAt = 0L;

    // NFC 识别后的当前景点上下文
    private String nfcCurrentSpotId = "";
    private String nfcCurrentSpotName = "";
    private String nfcSceneCode = "";
    private String nfcCurrentMarkerCode = "";
    private String nfcFallbackGuideTitle = "";
    private String nfcFallbackGuideSummary = "";
    private boolean nfcLocationActive = false;

    // NFC ReaderMode 去重
    private String lastNfcMarkerCode = "";
    private long lastNfcHandledAt = 0L;
    private static final long NFC_DEDUP_WINDOW_MS = 1500L;
    private String lastNfcGuideMarkerCode = "";
    private long lastNfcGuideTriggeredAt = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(Looper.getMainLooper());

        Log.d(TAG, "[NFC] MainActivity loaded, package=" + getPackageName()
                + ", nfcOfflineGuideEnabled=" + nfcOfflineGuideEnabled);

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

        // ==================== NFC / Offline Guide 初始化 ====================
        initNfcOfflineGuide();
        tryUpdateOfflinePackageIfNeeded();
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
                    writeRouteCardEvent("ROUTE_REJECT", currentRoute, null);
                }
                hideRouteCard("user_close_route_card");
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
                if (isRouteRecommendIntent(question)) {
                    requestRouteRecommendation(question, "button");
                    return;
                }
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
                if (isRouteRecommendIntent(question)) {
                    Log.d(TAG, "[RouteIntent] text input route request, use route button flow");
                    requestRouteRecommendation(question, "text_input");
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

    private void requestRouteRecommendation(final String rawQuestion, final String source) {
        String question = rawQuestion == null ? "" : rawQuestion.trim();
        if (question.length() == 0) {
            question = "推荐路线";
        }
        String routeSource = normalizeRouteRequestSource(source);
        Log.d(TAG, "[RouteRequest] source=" + routeSource);
        Log.d(TAG, "[RouteRequest] routeIntent=true, suppressRoute=false, requestType=route_recommend");
        askGuideInternal(question, true, false, routeSource);
    }

    private void askGuideInternal(final String rawQuestion, final boolean appendUserToChat) {
        askGuideInternal(rawQuestion, appendUserToChat, false);
    }

    private void askGuideInternal(final String rawQuestion, final boolean appendUserToChat, final boolean suppressRoute) {
        askGuideInternal(rawQuestion, appendUserToChat, suppressRoute, "text");
    }

    private void askGuideInternal(final String rawQuestion, final boolean appendUserToChat, final boolean suppressRoute, final String requestSource) {
        final String question = rawQuestion == null ? "" : rawQuestion.trim();
        final String routeRequestSource = normalizeRouteRequestSource(requestSource);
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

        if (requesting || isSseRoundActive()) {
            Log.d(TAG, "[SSE] cancel previous round before new question");
            cancelCurrentSseRound("new_question");
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

        startGuideChatSse(question, suppressRoute, routeRequestSource, allowRouteResponse);
    }

    private void startGuideChatSse(final String question,
                                   final boolean suppressRoute,
                                   final String routeRequestSource,
                                   final boolean allowRouteResponse) {
        try {
            URL url = new URL(getGuideTextRequestUrl(question));
            String requestUrl = url.toString();
            JSONObject requestJson = buildRequestJson(question, suppressRoute, routeRequestSource);
            logRouteContext(requestJson, question, allowRouteResponse);

            Log.d(TAG, "[SSE] request start url=" + requestUrl);
            Log.d(TAG, "[GuideChat] request body=" + sanitizeGuideChatRequestBody(requestJson));

            RequestBody body = RequestBody.create(GUIDE_CHAT_JSON_MEDIA_TYPE, requestJson.toString());
            Request.Builder builder = new Request.Builder()
                    .url(requestUrl)
                    .post(body)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream");
            applyAuthorizationHeader(builder);

            final Call call = guideSseHttpClient.newCall(builder.build());
            final int roundSeq;
            synchronized (speechQueueLock) {
                activeSseRoundSeq++;
                roundSeq = activeSseRoundSeq;
                currentGuideSseCall = call;
                resetCurrentSseRoundStateLocked();
            }
            Log.d(TAG, "[SSE] request round start roundSeq=" + roundSeq);

            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if (call.isCanceled() || !isCurrentGuideSseCall(call)) {
                        Log.d(TAG, "[SSE] request canceled or stale: " + e.getMessage());
                        return;
                    }
                    clearCurrentGuideSseCall(call);
                    Log.e(TAG, "[SSE] error code=network message=" + e.getMessage(), e);
                    handleGuideChatSseFailure(question, "暂时无法连接后端服务，请检查网络和小后端地址。", true);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!isCurrentGuideSseCall(call)) {
                            Log.d(TAG, "[SSE] ignore stale response");
                            return;
                        }
                        Log.d(TAG, "[GuideChat] httpStatus=" + response.code());
                        if (!response.isSuccessful()) {
                            Log.e(TAG, "[SSE] error code=" + response.code());
                            handleGuideChatSseFailure(question, "请求失败，请稍后重试。", false);
                            return;
                        }
                        ResponseBody responseBody = response.body();
                        if (responseBody == null) {
                            Log.e(TAG, "[SSE] error code=empty_body");
                            handleGuideChatSseFailure(question, "AI 服务暂时没有返回内容。", false);
                            return;
                        }
                        readGuideChatSse(responseBody.source(), question, allowRouteResponse, routeRequestSource, roundSeq);
                    } catch (IOException e) {
                        if (call.isCanceled() || !isCurrentGuideSseCall(call)) {
                            Log.d(TAG, "[SSE] stream canceled or stale: " + e.getMessage());
                            return;
                        }
                        Log.e(TAG, "[SSE] error code=stream message=" + e.getMessage(), e);
                        handleGuideChatSseFailure(question, "AI 流式响应中断，请稍后重试。", false);
                    } catch (Exception e) {
                        Log.e(TAG, "[SSE] error code=parse message=" + e.getMessage(), e);
                        handleGuideChatSseFailure(question, "AI 响应解析失败，请稍后重试。", false);
                    } finally {
                        clearCurrentGuideSseCall(call);
                        response.close();
                    }
                }
            });
        } catch (final Exception e) {
            Log.e(TAG, "[SSE] request build failed: " + e.getMessage(), e);
            handleGuideChatSseFailure(question, "请求构建失败，请稍后重试。", false);
        }
    }

    private void readGuideChatSse(BufferedSource source,
                                  String question,
                                  boolean allowRouteResponse,
                                  String routeRequestSource,
                                  int roundSeq) throws Exception {
        String eventName = "";
        StringBuilder dataBuilder = new StringBuilder();
        String line;
        while ((line = source.readUtf8Line()) != null) {
            if (line.length() == 0) {
                dispatchGuideSseEvent(eventName, dataBuilder.toString(), question, allowRouteResponse, routeRequestSource, roundSeq);
                eventName = "";
                dataBuilder.setLength(0);
                continue;
            }
            if (line.startsWith(":")) {
                continue;
            }
            if (line.startsWith("event:")) {
                eventName = stripSseValue(line.substring("event:".length()));
                continue;
            }
            if (line.startsWith("data:")) {
                if (dataBuilder.length() > 0) {
                    dataBuilder.append('\n');
                }
                dataBuilder.append(stripSseValue(line.substring("data:".length())));
            }
        }
        boolean dispatchLast = eventName.length() > 0 || dataBuilder.length() > 0;
        Log.d(TAG, "[SSE] stream eof dispatchLast=" + dispatchLast);
        if (eventName.length() > 0 || dataBuilder.length() > 0) {
            dispatchGuideSseEvent(eventName, dataBuilder.toString(), question, allowRouteResponse, routeRequestSource, roundSeq);
        }
        handleSseStreamEof(roundSeq);
    }

    private String stripSseValue(String value) {
        if (value == null) {
            return "";
        }
        if (value.startsWith(" ")) {
            return value.substring(1);
        }
        return value;
    }

    private void dispatchGuideSseEvent(String rawEvent,
                                       String rawData,
                                       String question,
                                       boolean allowRouteResponse,
                                       String routeRequestSource,
                                       int roundSeq) {
        String event = safeString(rawEvent).trim();
        if (event.length() == 0) {
            event = "message";
        }
        if ("message".equals(event) && "[DONE]".equals(safeString(rawData).trim())) {
            event = "done";
        }
        JSONObject data = parseSseDataObject(event, rawData);
        event = normalizeSseEventName(event, data);
        Log.d(TAG, "[SSE][Dispatch] event=" + event
                + " dataLen=" + safeString(rawData).length()
                + " currentMessageId=" + safeString(currentMessageId)
                + " roundSeq=" + roundSeq);
        if (looksLikeJson(rawData)) {
            Log.d(TAG, "[SSE][Dispatch] event=" + event
                    + " messageId=" + getSseEventMessageId(data)
                    + " chunkIndex=" + getJsonText(data, "chunkIndex", "chunk_index", "index")
                    + " chunkId=" + getJsonText(data, "chunkId", "chunk_id", "id"));
        }
        JSONObject eventData = unwrapSseEventData(data);
        if (!isCurrentSseRound(roundSeq, event)) {
            return;
        }

        if ("context".equals(event)) {
            handleSseContext(eventData, roundSeq);
        } else if ("asr_done".equals(event)) {
            handleSseAsrDone(eventData);
        } else if ("answer_delta".equals(event)) {
            handleSseAnswerDelta(eventData, roundSeq);
        } else if ("answer_done".equals(event)) {
            handleSseAnswerDone(eventData, roundSeq);
        } else if ("route_ready".equals(event)) {
            handleSseRouteReady(eventData, allowRouteResponse, routeRequestSource, roundSeq);
        } else if ("speech_chunk_ready".equals(event)) {
            handleSseSpeechChunkReady(eventData, roundSeq);
        } else if ("speech_done".equals(event)) {
            handleSseSpeechDone(eventData, roundSeq);
        } else if ("tts_error".equals(event)) {
            handleSseTtsError(eventData, roundSeq);
        } else if ("error".equals(event)) {
            String code = firstNotEmpty(getJsonText(eventData, "code", "errorCode", "error_code"), "error");
            String message = firstNotEmpty(getJsonText(eventData, "message", "msg", "error"), "AI 服务返回错误");
            Log.e(TAG, "[SSE] error code=" + code + ", message=" + safeLogText(message));
            handleGuideChatSseFailure(question, message, false);
        } else if ("done".equals(event)) {
            handleSseDone(eventData, roundSeq);
        } else {
            Log.d(TAG, "[SSE][Dispatch] unknown event=" + event + " rawPreview=" + rawPreview(rawData));
        }
    }

    private JSONObject parseSseDataObject(String event, String rawData) {
        String data = safeString(rawData).trim();
        JSONObject object = new JSONObject();
        if (data.length() == 0 || "[DONE]".equals(data)) {
            return object;
        }
        try {
            if (data.startsWith("{")) {
                return new JSONObject(data);
            }
            object.put("text", data);
        } catch (Exception e) {
            Log.e(TAG, "[SSE][Error] parse event=" + event
                    + " error=" + e.getMessage()
                    + " rawPreview=" + rawPreview(data));
        }
        return object;
    }

    private boolean looksLikeJson(String rawData) {
        String text = safeString(rawData).trim();
        return text.startsWith("{") && text.endsWith("}");
    }

    private String normalizeSseEventName(String event, JSONObject data) {
        String normalized = safeString(event).trim();
        if ("message".equals(normalized) || normalized.length() == 0) {
            String dataEvent = firstNotEmpty(
                    getJsonText(data, "event", "type", "eventType", "event_type"),
                    getJsonText(getJsonObject(data, "data", "payload"), "event", "type", "eventType", "event_type")
            );
            if (dataEvent.length() > 0) {
                normalized = dataEvent;
            }
        }
        String key = normalized.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace('.', '_');
        if ("answerdelta".equals(key)) return "answer_delta";
        if ("answerdone".equals(key)) return "answer_done";
        if ("routeready".equals(key) || "route_ready_event".equals(key)) return "route_ready";
        if ("speechchunkready".equals(key)) return "speech_chunk_ready";
        if ("speechdone".equals(key)) return "speech_done";
        return key;
    }

    private String getSseEventMessageId(JSONObject data) {
        return firstNotEmpty(
                getJsonText(data, "messageId", "message_id"),
                getJsonText(getJsonObject(data, "data", "payload"), "messageId", "message_id")
        );
    }

    private JSONObject unwrapSseEventData(JSONObject data) {
        if (data == null) {
            return new JSONObject();
        }
        JSONObject payload = getJsonObject(data, "data", "payload");
        if (payload == null) {
            return data;
        }
        try {
            JSONObject merged = new JSONObject(payload.toString());
            copyEnvelopeTextIfMissing(data, merged, "messageId", "messageId", "message_id");
            copyEnvelopeTextIfMissing(data, merged, "message_id", "messageId", "message_id");
            copyEnvelopeTextIfMissing(data, merged, "conversationId", "conversationId", "conversation_id");
            copyEnvelopeTextIfMissing(data, merged, "conversation_id", "conversationId", "conversation_id");
            copyEnvelopeTextIfMissing(data, merged, "traceId", "traceId", "trace_id");
            copyEnvelopeTextIfMissing(data, merged, "trace_id", "traceId", "trace_id");
            return merged;
        } catch (Exception e) {
            Log.w(TAG, "[SSE][Error] unwrap payload failed error=" + e.getMessage());
            return payload;
        }
    }

    private void copyEnvelopeTextIfMissing(JSONObject from, JSONObject to, String targetKey, String... sourceKeys) {
        if (from == null || to == null || to.has(targetKey)) {
            return;
        }
        String value = getJsonText(from, sourceKeys);
        if (value.length() > 0) {
            try {
                to.put(targetKey, value);
            } catch (Exception ignored) {
            }
        }
    }

    private String rawPreview(String rawData) {
        String text = safeString(rawData).replace('\r', ' ').replace('\n', ' ').trim();
        if (text.length() > 180) {
            return text.substring(0, 180) + "...";
        }
        return text;
    }

    private void handleSseContext(final JSONObject data, int roundSeq) {
        final String newConversationId = firstNotEmpty(
                getJsonText(data, "conversationId", "conversation_id"),
                conversationId
        );
        final String newMessageId = firstNotEmpty(
                getJsonText(data, "messageId", "message_id"),
                "sse_" + System.currentTimeMillis()
        );
        final String newTraceId = firstNotEmpty(getJsonText(data, "traceId", "trace_id"));

        synchronized (speechQueueLock) {
            if (!acceptSseEventMessageIdLocked("context", newMessageId, roundSeq)) {
                return;
            }
            conversationId = newConversationId;
            currentTraceId = newTraceId;
            currentAnswerBuilder.setLength(0);
            currentSseAnswerCommitted = false;
            routeReadyThisTurn = false;
            textGenerating = true;
            speechGenerating = true;
            serverDone = false;
            speechDoneReceived = false;
            speechPlaying = false;
            currentSpeechChunkCount = 0;
            nextSpeechIndexToPlay = 0;
            missingFallbackScheduledIndex = -1;
            missingFallbackScheduledMessageId = "";
            pendingSpeechChunks.clear();
            skippedSpeechChunkIndexes.clear();
            receivedSpeechChunkKeys.clear();
            playedSpeechChunkKeys.clear();
        }
        if (newConversationId.length() > 0) {
            rememberGuideConversationId(newConversationId);
        }
        Log.d(TAG, "[SSE] event=context/messageId=" + newMessageId
                + ", conversationId=" + newConversationId
                + ", traceId=" + newTraceId);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (guideEnded) {
                    return;
                }
                if (currentRoute != null || routeGuideActive || routeNavigationModeActive) {
                    Log.d(TAG, "[RouteNav] keep route card after spot explain");
                }
                stopMouthSync();
                guideStateText.setText("正在生成");
                voiceMainButton.setText("AI 正在回答...");
                renderStreamingAnswer();
                updateSimulatedSpotButton();
            }
        });
    }

    private void handleSseAsrDone(JSONObject data) {
        final String questionText = firstNotEmpty(
                getJsonText(data, "questionText", "question_text"),
                getJsonText(data, "rawQuestion", "raw_question")
        );
        Log.d(TAG, "[SSE] event=asr_done questionLength=" + questionText.length());
        if (questionText.length() == 0) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (lastQuestionText != null) {
                    lastQuestionText.setText("游客提问：" + questionText);
                }
            }
        });
    }

    private void handleSseAnswerDelta(JSONObject data, int roundSeq) {
        String messageId = getJsonText(data, "messageId", "message_id");
        if (isStaleSseMessage(messageId, "answer_delta", roundSeq)) {
            return;
        }
        final String delta = firstNotEmpty(
                getJsonText(data, "delta", "answerDelta", "answer_delta"),
                getJsonText(data, "text", "content")
        );
        int totalLen;
        synchronized (speechQueueLock) {
            totalLen = currentAnswerBuilder.length() + delta.length();
        }
        Log.d(TAG, "[SSE][Text] answer_delta len=" + delta.length() + " totalLen=" + totalLen);
        if (delta.length() == 0) {
            Log.d(TAG, "[SSE][Filter] ignore event=answer_delta reason=empty_delta");
            return;
        }
        synchronized (speechQueueLock) {
            currentAnswerBuilder.append(delta);
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                renderStreamingAnswer();
            }
        });
    }

    private void handleSseAnswerDone(JSONObject data, int roundSeq) {
        String messageId = getJsonText(data, "messageId", "message_id");
        if (isStaleSseMessage(messageId, "answer_done", roundSeq)) {
            return;
        }
        final String answer = firstNotEmpty(
                getJsonText(data, "answer", "text", "content"),
                currentAnswerBuilder.toString()
        );
        synchronized (speechQueueLock) {
            currentAnswerBuilder.setLength(0);
            currentAnswerBuilder.append(answer);
            textGenerating = false;
        }
        Log.d(TAG, "[SSE] event=answer_done length=" + answer.length());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                renderStreamingAnswer();
                finishCurrentSseRoundIfReady();
            }
        });
    }

    private void handleSseRouteReady(JSONObject data, boolean allowRouteResponse, String routeRequestSource, int roundSeq) {
        String messageId = getJsonText(data, "messageId", "message_id");
        if (isStaleSseMessage(messageId, "route_ready", roundSeq)) {
            return;
        }
        final RouteInfo route = parseRouteFromSseData(data);
        if (route != null) {
            route.routeIntent = route.routeIntent || allowRouteResponse;
        }
        final String routeFallbackText = buildRouteReadyFallbackText(data, route);
        synchronized (speechQueueLock) {
            routeReadyThisTurn = true;
        }
        Log.d(TAG, "[SSE][Route] route_ready received messageId=" + safeString(messageId)
                + " nodes=" + (route == null || route.nodes == null ? 0 : route.nodes.size())
                + " title=" + safeLogText(getRouteReadyTitle(data, route))
                + " summary=" + safeLogText(routeFallbackText));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (route != null) {
                    showRouteCardIfNeeded(route);
                } else {
                    Log.w(TAG, "[SSE][Route] route_ready has no parsable route payload");
                }
                ensureRouteReadyAnswerVisible(routeFallbackText);
            }
        });
    }

    private void ensureRouteReadyAnswerVisible(String routeFallbackText) {
        boolean shouldRender = false;
        synchronized (speechQueueLock) {
            if (currentAnswerBuilder.toString().trim().length() == 0) {
                currentAnswerBuilder.append(firstNotEmpty(
                        routeFallbackText,
                        "我已为你生成一条推荐路线，可以点击路线卡片查看详情。"
                ));
                shouldRender = true;
            }
        }
        if (shouldRender) {
            Log.d(TAG, "[SSE][Route] route_ready fallback answer rendered len="
                    + safeString(routeFallbackText).length());
            renderStreamingAnswer();
        } else {
            Log.d(TAG, "[SSE][Route] route_ready fallback skipped because answer bubble already has text");
        }
    }

    private String buildRouteReadyFallbackText(JSONObject data, RouteInfo route) {
        String text = firstNotEmpty(
                getJsonText(data, "title", "routeName", "route_name", "summary", "reason", "text", "answer", "content"),
                getJsonText(getJsonObject(data, "data", "payload"), "title", "routeName", "route_name", "summary", "reason", "text", "answer", "content"),
                getJsonText(getJsonObject(data, "route", "routeInfo", "route_info"), "title", "routeName", "route_name", "summary", "reason", "text", "answer", "content"),
                route == null ? "" : route.reason,
                route == null ? "" : route.routeName
        );
        if (text.length() == 0) {
            text = "我已为你生成一条推荐路线，可以点击路线卡片查看详情。";
        }
        return text;
    }

    private String getRouteReadyTitle(JSONObject data, RouteInfo route) {
        return firstNotEmpty(
                getJsonText(data, "title", "routeName", "route_name", "name"),
                getJsonText(getJsonObject(data, "data", "payload"), "title", "routeName", "route_name", "name"),
                route == null ? "" : route.routeName
        );
    }

    private void handleSseSpeechChunkReady(JSONObject data, int roundSeq) {
        final SpeechChunk chunk = parseSpeechChunk(data);
        if (chunk == null) {
            Log.w(TAG, "[SSE][Filter] ignore event=speech_chunk_ready reason=parse_empty_chunk");
            return;
        }
        chunk.roundSeq = roundSeq;
        Log.d(TAG, "[speech-receive] messageId=" + safeString(chunk.messageId)
                + " chunkId=" + safeString(chunk.chunkId)
                + " chunkIndex=" + chunk.chunkIndex
                + " audioUrl=" + safeString(chunk.audioUrl)
                + " isLast=" + chunk.isLast);
        if (isStaleSpeechChunk(chunk, "speech_chunk_ready", roundSeq)) {
            return;
        }
        String chunkKey = buildSpeechChunkKey(chunk);
        boolean duplicate;
        boolean lateChunk;
        boolean indexAlreadyPending;
        boolean queued;
        synchronized (speechQueueLock) {
            duplicate = receivedSpeechChunkKeys.contains(chunkKey) || playedSpeechChunkKeys.contains(chunkKey);
            lateChunk = chunk.chunkIndex < nextSpeechIndexToPlay;
            indexAlreadyPending = !duplicate && !lateChunk && pendingSpeechChunks.containsKey(chunk.chunkIndex);
            if (!duplicate) {
                receivedSpeechChunkKeys.add(chunkKey);
            }
            queued = !duplicate && !lateChunk && !indexAlreadyPending;
            if (queued) {
                pendingSpeechChunks.put(chunk.chunkIndex, chunk);
            }
            if (chunk.isLast && currentSpeechChunkCount <= 0) {
                currentSpeechChunkCount = chunk.chunkIndex + 1;
            }
            Log.d(TAG, "[speech-queue] expectedChunkIndex=" + nextSpeechIndexToPlay
                    + " pendingIndexes=" + getPendingIndexesTextLocked()
                    + " receivedKeysSize=" + receivedSpeechChunkKeys.size()
                    + " playedKeysSize=" + playedSpeechChunkKeys.size()
                    + " isPlaying=" + speechPlaying
                    + " duplicate=" + duplicate);
        }
        if (duplicate) {
            Log.d(TAG, "[SSE][Filter] ignore duplicate event=speech_chunk_ready key=" + chunkKey);
            return;
        }
        if (lateChunk) {
            Log.d(TAG, "[SSE][Filter] ignore event=speech_chunk_ready reason=late_chunk chunkIndex=" + chunk.chunkIndex
                    + " expectedChunkIndex=" + nextSpeechIndexToPlay);
            return;
        }
        if (indexAlreadyPending) {
            Log.d(TAG, "[SSE][Filter] ignore event=speech_chunk_ready reason=index_already_pending key=" + chunkKey
                    + " chunkIndex=" + chunk.chunkIndex);
            return;
        }
        Log.d(TAG, "[SSE][Audio] speech_chunk_ready queued key=" + chunkKey
                + " chunkIndex=" + chunk.chunkIndex
                + " queued=" + queued);
        logSseState("speech_chunk_ready");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tryPlayNextSpeechChunk();
            }
        });
    }

    private void handleSseSpeechDone(JSONObject data, int roundSeq) {
        String messageId = getJsonText(data, "messageId", "message_id");
        if (isStaleSseMessage(messageId, "speech_done", roundSeq)) {
            return;
        }
        int chunkCount = parseIntOrDefault(firstNotEmpty(
                getJsonText(data, "chunkCount", "chunk_count", "totalChunks", "total_chunks")
        ), 0);
        synchronized (speechQueueLock) {
            speechGenerating = false;
            speechDoneReceived = true;
            if (chunkCount > 0) {
                currentSpeechChunkCount = chunkCount;
            }
        }
        Log.d(TAG, "[SSE] event=speech_done chunkCount=" + chunkCount);
        logSseState("speech_done");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tryPlayNextSpeechChunk();
                finishCurrentSseRoundIfReady();
            }
        });
    }

    private void handleSseTtsError(JSONObject data, int roundSeq) {
        String messageId = getJsonText(data, "messageId", "message_id");
        if (isStaleSseMessage(messageId, "tts_error", roundSeq)) {
            return;
        }
        final int chunkIndex = parseIntOrDefault(firstNotEmpty(
                getJsonText(data, "chunkIndex", "chunk_index", "index")
        ), nextSpeechIndexToPlay);
        String message = firstNotEmpty(getJsonText(data, "message", "msg", "error"), "分段语音生成失败");
        synchronized (speechQueueLock) {
            skippedSpeechChunkIndexes.add(chunkIndex);
            advanceSkippedSpeechIndexesLocked();
        }
        Log.w(TAG, "[SSE] event=tts_error chunkIndex=" + chunkIndex + ", message=" + safeLogText(message));
        Log.w(TAG, "[SSE][Audio] skip chunkIndex=" + chunkIndex + " reason=tts_error");
        logSseState("tts_error");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showToast("部分语音生成失败，继续播放已生成内容");
                tryPlayNextSpeechChunk();
                finishCurrentSseRoundIfReady();
            }
        });
    }

    private void handleSseDone(JSONObject data, int roundSeq) {
        String messageId = firstNotEmpty(getJsonText(data, "messageId", "message_id"), currentMessageId);
        if (isStaleSseMessage(messageId, "done", roundSeq)) {
            return;
        }
        String answer = firstNotEmpty(getJsonText(data, "answer", "text", "content"));
        String doneConversationId = getJsonText(data, "conversationId", "conversation_id");
        if (doneConversationId.length() > 0) {
            rememberGuideConversationId(doneConversationId);
        }
        int chunkCount = parseIntOrDefault(firstNotEmpty(
                getJsonText(data, "speechChunkCount", "speech_chunk_count", "chunkCount", "chunk_count", "totalChunks", "total_chunks")
        ), 0);
        boolean speechDone = getBooleanCompat(data, "speechDone", "speech_done");
        synchronized (speechQueueLock) {
            boolean keepRouteReadyText = routeReadyThisTurn && currentAnswerBuilder.toString().trim().length() > 0;
            if (answer.length() > 0 && !keepRouteReadyText) {
                currentAnswerBuilder.setLength(0);
                currentAnswerBuilder.append(answer);
            } else if (answer.length() > 0) {
                Log.d(TAG, "[SSE][Route] done answer ignored to keep route_ready text len=" + currentAnswerBuilder.length());
            }
            serverDone = true;
            textGenerating = false;
            if (chunkCount > 0) {
                currentSpeechChunkCount = chunkCount;
            }
            if (speechDone || chunkCount > 0 || (pendingSpeechChunks.size() == 0 && !speechPlaying)) {
                speechGenerating = false;
            }
            if (speechDone) {
                speechDoneReceived = true;
            }
        }
        Log.d(TAG, "[SSE] event=done messageId=" + messageId);
        logSseState("done");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                renderStreamingAnswer();
                finishCurrentSseRoundIfReady();
            }
        });
    }

    private void handleSseStreamEof(int roundSeq) {
        if (!isCurrentSseRound(roundSeq, "stream_eof")) {
            return;
        }
        synchronized (speechQueueLock) {
            serverDone = true;
            textGenerating = false;
            speechGenerating = false;
        }
        Log.d(TAG, "[SSE] stream eof mark serverDone");
        logSseState("stream_eof");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                renderStreamingAnswer();
                tryPlayNextSpeechChunk();
                finishCurrentSseRoundIfReady();
            }
        });
    }

    private boolean isSseRoundActive() {
        synchronized (speechQueueLock) {
            return currentGuideSseCall != null
                    || textGenerating
                    || speechGenerating
                    || speechPlaying
                    || backendAudioSpeaking
                    || pendingSpeechChunks.size() > 0;
        }
    }

    private boolean isCurrentGuideSseCall(Call call) {
        synchronized (speechQueueLock) {
            return call != null && call == currentGuideSseCall;
        }
    }

    private void clearCurrentGuideSseCall(Call call) {
        synchronized (speechQueueLock) {
            if (call == currentGuideSseCall) {
                currentGuideSseCall = null;
            }
        }
    }

    private void resetCurrentSseRoundStateLocked() {
        contextMessageId = "";
        responseMessageId = "";
        currentMessageId = "";
        currentTraceId = "";
        currentSseQuestion = "";
        currentAnswerBuilder.setLength(0);
        currentSseAnswerCommitted = false;
        textGenerating = false;
        speechGenerating = false;
        speechPlaying = false;
        serverDone = false;
        speechDoneReceived = false;
        routeReadyThisTurn = false;
        currentSpeechChunkCount = 0;
        nextSpeechIndexToPlay = 0;
        missingFallbackScheduledIndex = -1;
        missingFallbackScheduledMessageId = "";
        pendingSpeechChunks.clear();
        skippedSpeechChunkIndexes.clear();
        receivedSpeechChunkKeys.clear();
        playedSpeechChunkKeys.clear();
        acceptedMessageIds.clear();
    }

    private void cancelCurrentSseRound(String reason) {
        Call callToCancel;
        synchronized (speechQueueLock) {
            callToCancel = currentGuideSseCall;
            currentGuideSseCall = null;
            canceledMessageId = firstNotEmpty(currentMessageId, canceledMessageId);
            if (currentMessageId.length() > 0) {
                canceledMessageIds.add(currentMessageId);
            }
            if (contextMessageId.length() > 0) {
                canceledMessageIds.add(contextMessageId);
            }
            if (responseMessageId.length() > 0) {
                canceledMessageIds.add(responseMessageId);
            }
            canceledMessageIds.addAll(acceptedMessageIds);
            activeSseRoundSeq++;
            currentMessageId = "pending_" + System.currentTimeMillis();
            contextMessageId = "";
            responseMessageId = "";
            currentTraceId = "";
            currentSseQuestion = "";
            currentAnswerBuilder.setLength(0);
            currentSseAnswerCommitted = false;
            textGenerating = false;
            speechGenerating = false;
            speechPlaying = false;
            serverDone = false;
            speechDoneReceived = false;
            routeReadyThisTurn = false;
            currentSpeechChunkCount = 0;
            nextSpeechIndexToPlay = 0;
            missingFallbackScheduledIndex = -1;
            missingFallbackScheduledMessageId = "";
            pendingSpeechChunks.clear();
            skippedSpeechChunkIndexes.clear();
            receivedSpeechChunkKeys.clear();
            playedSpeechChunkKeys.clear();
            acceptedMessageIds.clear();
        }
        if (callToCancel != null) {
            callToCancel.cancel();
        }
        Log.d(TAG, "[SSE] cancel round reason=" + reason
                + ", canceledMessageId=" + safeString(canceledMessageId));
        stopCurrentAudio();
        requesting = false;
        voiceFlowActive = false;
    }

    private boolean isStaleSseMessage(String messageId) {
        return isStaleSseMessage(messageId, "unknown", activeSseRoundSeq);
    }

    private boolean isStaleSseMessage(String messageId, String event) {
        return isStaleSseMessage(messageId, event, activeSseRoundSeq);
    }

    private boolean isStaleSseMessage(String messageId, String event, int roundSeq) {
        synchronized (speechQueueLock) {
            return !acceptSseEventMessageIdLocked(event, messageId, roundSeq);
        }
    }

    private boolean isCurrentSseRound(int roundSeq, String event) {
        synchronized (speechQueueLock) {
            if (roundSeq != activeSseRoundSeq) {
                Log.d(TAG, "[SSE][Filter] ignore old round event=" + event
                        + " eventRoundSeq=" + roundSeq
                        + " activeSseRoundSeq=" + activeSseRoundSeq);
                return false;
            }
            return true;
        }
    }

    private boolean acceptSseEventMessageIdLocked(String event, String messageId, int roundSeq) {
        if (roundSeq != activeSseRoundSeq) {
            Log.d(TAG, "[SSE][Filter] ignore old round event=" + event
                    + " eventRoundSeq=" + roundSeq
                    + " activeSseRoundSeq=" + activeSseRoundSeq);
            return false;
        }
        String msg = safeString(messageId).trim();
        if (msg.length() == 0) {
            Log.d(TAG, "[SSE][Filter] allow current round event=" + event
                    + " eventMessageId="
                    + " roundSeq=" + roundSeq);
            logSseMessageIdStateLocked(event, msg, true);
            return true;
        }
        if (canceledMessageIds.contains(msg) || msg.equals(safeString(canceledMessageId).trim())) {
            Log.d(TAG, "[SSE][Filter] ignore canceled messageId=" + msg
                    + " event=" + event
                    + " roundSeq=" + roundSeq);
            logSseMessageIdStateLocked(event, msg, false);
            return false;
        }

        if ("context".equals(event)) {
            contextMessageId = msg;
        } else if (responseMessageId.length() == 0) {
            responseMessageId = msg;
        }
        acceptedMessageIds.add(msg);
        currentMessageId = firstNotEmpty(responseMessageId, contextMessageId, msg, currentMessageId);
        Log.d(TAG, "[SSE][Filter] allow current round event=" + event
                + " eventMessageId=" + msg
                + " roundSeq=" + roundSeq);
        logSseMessageIdStateLocked(event, msg, true);
        return true;
    }

    private void logSseMessageIdStateLocked(String event, String eventMessageId, boolean accepted) {
        Log.d(TAG, "[SSE][MessageId] contextMessageId=" + safeString(contextMessageId)
                + " responseMessageId=" + safeString(responseMessageId)
                + " eventMessageId=" + safeString(eventMessageId)
                + " accepted=" + accepted
                + " acceptedSize=" + acceptedMessageIds.size()
                + " event=" + safeString(event));
    }

    private boolean isStaleSpeechChunk(SpeechChunk chunk, String event, int roundSeq) {
        if (chunk == null) {
            return false;
        }
        boolean stale = isStaleSseMessage(chunk.messageId, event, roundSeq);
        if (stale) {
            Log.d(TAG, "[speech-receive] stale messageId=" + safeString(chunk.messageId)
                    + " currentMessageId=" + safeString(currentMessageId)
                    + " chunkId=" + safeString(chunk.chunkId)
                    + " chunkIndex=" + chunk.chunkIndex);
        }
        return stale;
    }

    private String buildSpeechChunkKey(SpeechChunk chunk) {
        if (chunk == null) {
            return "";
        }
        String messageId = firstNotEmpty(chunk.messageId, currentMessageId).trim();
        String chunkId = safeString(chunk.chunkId).trim();
        if (chunkId.length() > 0) {
            return messageId + ":" + chunkId;
        }
        return messageId + ":" + chunk.chunkIndex + ":" + safeString(chunk.audioUrl).trim();
    }

    private String getPendingIndexesTextLocked() {
        if (pendingSpeechChunks.size() == 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (Integer index : pendingSpeechChunks.keySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append(index == null ? "null" : String.valueOf(index));
            first = false;
        }
        builder.append(']');
        return builder.toString();
    }

    private RouteInfo parseRouteFromSseData(JSONObject data) {
        if (data == null) {
            return null;
        }
        RouteInfo route = parseRouteFromAiResponse(data);
        if (route != null) {
            return attachRouteEnvelopeFields(route, data);
        }
        if (data.has("nodes") || data.has("spots") || data.has("routeName") || data.has("route_name")) {
            return attachRouteEnvelopeFields(parseRouteInfo(data), data);
        }
        JSONObject payload = getJsonObject(data, "data");
        if (payload != null) {
            route = parseRouteFromAiResponse(payload);
            if (route != null) {
                return attachRouteEnvelopeFields(route, data);
            }
            if (payload.has("nodes") || payload.has("spots") || payload.has("routeName") || payload.has("route_name")) {
                return attachRouteEnvelopeFields(parseRouteInfo(payload), data);
            }
        }
        return null;
    }

    private RouteInfo attachRouteEnvelopeFields(RouteInfo route, JSONObject envelope) {
        if (route == null || envelope == null) {
            return route;
        }
        route.routeIntent = route.routeIntent || getBooleanCompat(envelope, "routeIntent", "route_intent");
        if (route.visitStatus.length() == 0) {
            route.visitStatus = getJsonText(envelope, "visitStatus", "visit_status");
        }
        if (route.routeMode.length() == 0) {
            route.routeMode = getJsonText(envelope, "routeMode", "route_mode");
        }
        String envelopeRouteId = getJsonText(envelope, "routeId", "route_id");
        if (route.originalRouteId.length() == 0 && envelopeRouteId.length() > 0) {
            route.routeId = envelopeRouteId;
            route.originalRouteId = envelopeRouteId;
            route.localRouteIdGenerated = false;
        } else if (route.routeId.length() == 0) {
            route.routeId = envelopeRouteId;
            route.originalRouteId = envelopeRouteId;
            ensureRouteId(route);
        }
        return route;
    }

    private SpeechChunk parseSpeechChunk(JSONObject data) {
        if (data == null) {
            return null;
        }
        SpeechChunk chunk = new SpeechChunk();
        chunk.messageId = getJsonText(data, "messageId", "message_id");
        chunk.chunkId = getJsonText(data, "chunkId", "chunk_id", "id");
        chunk.chunkIndex = parseIntOrDefault(firstNotEmpty(
                getJsonText(data, "chunkIndex", "chunk_index", "index"),
                "0"
        ), 0);
        chunk.text = firstNotEmpty(getJsonText(data, "text", "content", "answer"), "");
        JSONObject audioJson = getJsonObject(data, "audio", "speech", "tts");
        chunk.audioUrl = firstNotEmpty(
                getJsonText(data, "audioUrl", "audio_url", "url"),
                getJsonText(audioJson, "url", "audioUrl", "audio_url")
        );
        chunk.durationMs = parseLongOrDefault(firstNotEmpty(
                getJsonText(data, "durationMs", "duration_ms", "audioDurationMs", "audio_duration_ms"),
                getJsonText(audioJson, "durationMs", "duration_ms", "audioDurationMs", "audio_duration_ms")
        ), 0L);
        chunk.isLast = getBooleanCompat(data, "isLast", "is_last", "last");
        chunk.action = firstNotEmpty(getJsonText(data, "action", "actionCode", "action_code"), "explain");
        chunk.emotion = firstNotEmpty(getJsonText(data, "emotion", "emotionCode", "emotion_code"), "warm");
        parseSpeechMouthFrames(data, chunk);
        return chunk;
    }

    private void parseSpeechMouthFrames(JSONObject data, SpeechChunk chunk) {
        JSONArray mouthFramesArray = getJsonArray(data, "mouthFrames", "mouth_frames");
        JSONObject mouthJson = getJsonObject(data, "mouth", "mouthSync", "mouth_sync");
        JSONObject audioJson = getJsonObject(data, "audio", "speech", "tts");
        if (mouthFramesArray == null) {
            mouthFramesArray = getJsonArray(mouthJson, "frames", "mouthFrames", "mouth_frames");
        }
        if (mouthFramesArray == null) {
            mouthFramesArray = getJsonArray(audioJson, "frames", "mouthFrames", "mouth_frames");
        }
        if (mouthFramesArray == null) {
            return;
        }
        for (int i = 0; i < mouthFramesArray.length(); i++) {
            JSONObject frame = mouthFramesArray.optJSONObject(i);
            if (frame == null) {
                continue;
            }
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
            chunk.mouthFrames.add(new MouthSyncManager.MouthFrame((int) Math.round(rawTime), (float) openValue, (float) formValue));
            chunk.controllerMouthFrames.add(new MouthSyncController.MouthFrame(
                    rawTime,
                    (float) openValue,
                    (float) formValue,
                    hasTimeField,
                    hasOpenField
            ));
        }
    }

    private void renderStreamingAnswer() {
        if (answerText == null) {
            return;
        }
        String answer;
        synchronized (speechQueueLock) {
            answer = currentAnswerBuilder.toString();
        }
        StringBuilder display = new StringBuilder(chatTranscriptBuilder.toString());
        if (answer.trim().length() > 0) {
            if (display.length() > 0) {
                display.append("\n\n");
            }
            display.append("AI 导游：").append(answer);
        }
        answerText.setText(display.toString());
        scrollChatToBottom();
    }

    private void commitCurrentSseAnswerIfNeeded() {
        String answer;
        boolean shouldCommit;
        synchronized (speechQueueLock) {
            shouldCommit = !currentSseAnswerCommitted;
            currentSseAnswerCommitted = true;
            answer = currentAnswerBuilder.toString();
        }
        if (shouldCommit && answer.trim().length() > 0) {
            appendAiMessage(answer);
        } else {
            renderStreamingAnswer();
        }
    }

    private void tryPlayNextSpeechChunk() {
        SpeechChunk chunk;
        synchronized (speechQueueLock) {
            advanceSkippedSpeechIndexesLocked();
            if (speechPlaying) {
                return;
            }
            chunk = pendingSpeechChunks.get(nextSpeechIndexToPlay);
            if (chunk == null) {
                scheduleSpeechMissingFallbackIfNeededLocked("try_play");
                return;
            }
            speechPlaying = true;
            if (missingFallbackScheduledIndex == nextSpeechIndexToPlay) {
                missingFallbackScheduledIndex = -1;
                missingFallbackScheduledMessageId = "";
            }
        }
        playSpeechChunk(chunk);
    }

    private void scheduleSpeechMissingFallbackIfNeededLocked(String reason) {
        if (speechPlaying
                || currentSpeechChunkCount <= 0
                || nextSpeechIndexToPlay >= currentSpeechChunkCount
                || pendingSpeechChunks.containsKey(nextSpeechIndexToPlay)
                || (!speechDoneReceived && !serverDone)) {
            return;
        }
        final int scheduledIndex = nextSpeechIndexToPlay;
        final String scheduledMessageId = safeString(currentMessageId);
        if (missingFallbackScheduledIndex == scheduledIndex
                && scheduledMessageId.equals(missingFallbackScheduledMessageId)) {
            return;
        }
        missingFallbackScheduledIndex = scheduledIndex;
        missingFallbackScheduledMessageId = scheduledMessageId;
        Log.d(TAG, "[SSE][Audio] schedule missing fallback reason=" + reason
                + " expectedChunkIndex=" + scheduledIndex
                + " expected=" + currentSpeechChunkCount
                + " delayMs=" + SPEECH_MISSING_FALLBACK_DELAY_MS
                + " pendingIndexes=" + getPendingIndexesTextLocked());
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                handleSpeechMissingFallback(scheduledMessageId, scheduledIndex);
            }
        }, SPEECH_MISSING_FALLBACK_DELAY_MS);
    }

    private void handleSpeechMissingFallback(String scheduledMessageId, int scheduledIndex) {
        boolean skipped = false;
        synchronized (speechQueueLock) {
            if (!safeString(currentMessageId).equals(safeString(scheduledMessageId))
                    || nextSpeechIndexToPlay != scheduledIndex
                    || speechPlaying
                    || currentSpeechChunkCount <= 0
                    || scheduledIndex >= currentSpeechChunkCount
                    || pendingSpeechChunks.containsKey(scheduledIndex)
                    || (!speechDoneReceived && !serverDone)) {
                Log.d(TAG, "[SSE][Audio] missing fallback canceled expectedChunkIndex=" + scheduledIndex
                        + " currentExpectedChunkIndex=" + nextSpeechIndexToPlay
                        + " pendingIndexes=" + getPendingIndexesTextLocked());
                return;
            }
            Log.w(TAG, "[speech-missing] expectedChunkIndex=" + scheduledIndex
                    + " expected=" + currentSpeechChunkCount
                    + " pendingIndexes=" + getPendingIndexesTextLocked());
            skippedSpeechChunkIndexes.add(scheduledIndex);
            nextSpeechIndexToPlay++;
            missingFallbackScheduledIndex = -1;
            missingFallbackScheduledMessageId = "";
            if (currentSpeechChunkCount > 0 && nextSpeechIndexToPlay >= currentSpeechChunkCount) {
                speechGenerating = false;
            }
            skipped = true;
        }
        if (skipped) {
            logSseState("speech_missing_skipped");
            tryPlayNextSpeechChunk();
            finishCurrentSseRoundIfReady();
        }
    }

    private void advanceSkippedSpeechIndexesLocked() {
        while (skippedSpeechChunkIndexes.contains(nextSpeechIndexToPlay)) {
            skippedSpeechChunkIndexes.remove(nextSpeechIndexToPlay);
            nextSpeechIndexToPlay++;
        }
    }

    private void playSpeechChunk(final SpeechChunk chunk) {
        if (chunk == null) {
            markSpeechChunkFinished(null, false);
            return;
        }
        if (isStaleSseMessage(chunk.messageId, "speech_chunk_play", chunk.roundSeq)) {
            markSpeechChunkFinished(chunk, false);
            return;
        }
        final String finalUrl = resolveAudioUrl(chunk.audioUrl);
        Log.d(TAG, "[SSE][Audio] playSpeechChunk messageId=" + safeString(chunk.messageId)
                + " chunkId=" + safeString(chunk.chunkId)
                + " chunkIndex=" + chunk.chunkIndex
                + " expectedChunkIndex=" + nextSpeechIndexToPlay);
        Log.d(TAG, "[SSE][Audio] raw audioUrl=" + safeString(chunk.audioUrl));
        Log.d(TAG, "[SSE][Audio] resolved audioUrl=" + safeString(finalUrl));
        if (finalUrl.length() == 0) {
            Log.w(TAG, "[SSE][Audio] skip chunkIndex=" + chunk.chunkIndex + " reason=empty_or_unresolved_url");
            markSpeechChunkFinished(chunk, false);
            return;
        }

        try {
            releaseMediaPlayerOnly();
            final GuideResponse guideResponse = new GuideResponse();
            guideResponse.answer = chunk.text;
            guideResponse.audioUrl = finalUrl;
            guideResponse.audioDurationMs = chunk.durationMs;
            guideResponse.mouthFrames = chunk.mouthFrames;
            guideResponse.controllerMouthFrames = chunk.controllerMouthFrames;
            guideResponse.action = firstNotEmpty(chunk.action, "explain");
            guideResponse.emotion = firstNotEmpty(chunk.emotion, "warm");

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setVolume(1f, 1f);
            mediaPlayer.setDataSource(finalUrl);
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    Log.d(TAG, "[SSE][Audio] onPrepared messageId=" + safeString(chunk.messageId)
                            + " chunkId=" + safeString(chunk.chunkId)
                            + " chunkIndex=" + chunk.chunkIndex);
                    if (isStaleSseMessage(chunk.messageId, "speech_chunk_prepared", chunk.roundSeq)) {
                        markSpeechChunkFinished(chunk, false);
                        return;
                    }
                    try {
                        guideStateText.setText("正在讲解");
                        voiceMainButton.setText("数字人正在讲解...");
                        mp.start();
                        Log.d(TAG, "[SSE][Audio] play start chunkIndex=" + chunk.chunkIndex);
                    } catch (Exception e) {
                        Log.e(TAG, "[SSE][Audio] play error chunkIndex=" + chunk.chunkIndex
                                + " url=" + finalUrl
                                + " stage=start", e);
                        markSpeechChunkFinished(chunk, false);
                        return;
                    }

                    long startUptimeMs = android.os.SystemClock.uptimeMillis();
                    long durationMs = chunk.durationMs;
                    if (durationMs <= 0L) {
                        try {
                            durationMs = mp.getDuration();
                        } catch (Exception ignored) {
                        }
                    }
                    if (hasMouthFrames(chunk.mouthFrames, chunk.controllerMouthFrames)) {
                        startMouthSyncWithFrames(chunk.mouthFrames, chunk.controllerMouthFrames, durationMs, startUptimeMs);
                    } else {
                        startMouthSyncWithText(chunk.text, durationMs, startUptimeMs, finalUrl);
                    }
                    startBackendAudioSpeaking(guideResponse);
                    forceRenderLive2D();
                }
            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Log.d(TAG, "[SSE][Audio] onCompletion messageId=" + safeString(chunk.messageId)
                            + " chunkId=" + safeString(chunk.chunkId)
                            + " chunkIndex=" + chunk.chunkIndex);
                    Log.d(TAG, "[SSE][Audio] play complete chunkIndex=" + chunk.chunkIndex);
                    markSpeechChunkFinished(chunk, true);
                }
            });
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e(TAG, "[SSE][Audio] onError messageId=" + safeString(chunk.messageId)
                            + " chunkId=" + safeString(chunk.chunkId)
                            + " chunkIndex=" + chunk.chunkIndex
                            + " url=" + finalUrl
                            + ", what=" + what
                            + ", extra=" + extra);
                    markSpeechChunkFinished(chunk, false);
                    return true;
                }
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "[SSE][Audio] play error chunkIndex=" + chunk.chunkIndex
                    + " url=" + finalUrl
                    + " stage=prepare", e);
            markSpeechChunkFinished(chunk, false);
        }
    }

    private void markSpeechChunkFinished(SpeechChunk chunk, boolean completed) {
        String chunkKey = buildSpeechChunkKey(chunk);
        if (isPlayedSpeechChunk(chunk)) {
            Log.d(TAG, "[SSE][Audio] duplicate chunk finish ignored key=" + chunkKey
                    + " chunkIndex=" + chunk.chunkIndex);
            finishCurrentSseRoundIfReady();
            return;
        }
        Log.d(TAG, "[SSE][Audio] markSpeechChunkFinished messageId=" + (chunk == null ? "" : safeString(chunk.messageId))
                + " chunkId=" + (chunk == null ? "" : safeString(chunk.chunkId))
                + " chunkIndex=" + (chunk == null ? -1 : chunk.chunkIndex)
                + " completed=" + completed);
        stopBackendAudioSpeaking();
        stopMouthSync();
        releaseMediaPlayerOnly();
        synchronized (speechQueueLock) {
            speechPlaying = false;
            if (chunk != null) {
                playedSpeechChunkKeys.add(chunkKey);
                pendingSpeechChunks.remove(chunk.chunkIndex);
            }
            if (chunk != null && chunk.chunkIndex == nextSpeechIndexToPlay) {
                nextSpeechIndexToPlay++;
            } else if (chunk != null && chunk.chunkIndex > nextSpeechIndexToPlay) {
                Log.w(TAG, "[SSE][Audio] finished chunk index ahead of expected, keep sequential advance chunkIndex="
                        + chunk.chunkIndex + " expectedChunkIndex=" + nextSpeechIndexToPlay);
                nextSpeechIndexToPlay++;
            } else if (chunk == null) {
                nextSpeechIndexToPlay++;
            }
            advanceSkippedSpeechIndexesLocked();
            if (currentSpeechChunkCount > 0 && nextSpeechIndexToPlay >= currentSpeechChunkCount) {
                speechGenerating = false;
            }
            Log.d(TAG, "[speech-queue] expectedChunkIndex=" + nextSpeechIndexToPlay
                    + " pendingIndexes=" + getPendingIndexesTextLocked()
                    + " receivedKeysSize=" + receivedSpeechChunkKeys.size()
                    + " playedKeysSize=" + playedSpeechChunkKeys.size()
                    + " isPlaying=" + speechPlaying
                    + " duplicate=false");
        }
        if (chunk != null) {
            Log.d(TAG, "[SSE] chunk finished index=" + chunk.chunkIndex + ", completed=" + completed);
        }
        logSseState("chunk_finished");
        tryPlayNextSpeechChunk();
        finishCurrentSseRoundIfReady();
    }

    private boolean isPlayedSpeechChunk(SpeechChunk chunk) {
        if (chunk == null) {
            return false;
        }
        synchronized (speechQueueLock) {
            return playedSpeechChunkKeys.contains(buildSpeechChunkKey(chunk));
        }
    }

    private void logSseState(String reason) {
        synchronized (speechQueueLock) {
            Log.d(TAG, "[SSE][State] reason=" + reason
                    + " serverDone=" + serverDone
                    + " textGenerating=" + textGenerating
                    + " speechGenerating=" + speechGenerating
                    + " speechPlaying=" + speechPlaying
                    + " queueSize=" + pendingSpeechChunks.size()
                    + " pendingUnplayed=" + getPendingUnplayedChunkCountLocked()
                    + " expectedChunkIndex=" + nextSpeechIndexToPlay
                    + " expectedSpeechChunkCount=" + currentSpeechChunkCount
                    + " speechDoneReceived=" + speechDoneReceived
                    + " pendingIndexes=" + getPendingIndexesTextLocked());
        }
    }

    private String getSseLifecycleState() {
        synchronized (speechQueueLock) {
            return "requesting=" + requesting
                    + " voiceFlowActive=" + voiceFlowActive
                    + " recording=" + recording
                    + " serverDone=" + serverDone
                    + " textGenerating=" + textGenerating
                    + " speechGenerating=" + speechGenerating
                    + " speechPlaying=" + speechPlaying
                    + " expectedChunkIndex=" + nextSpeechIndexToPlay
                    + " expectedSpeechChunkCount=" + currentSpeechChunkCount
                    + " pendingIndexes=" + getPendingIndexesTextLocked()
                    + " receivedKeysSize=" + receivedSpeechChunkKeys.size()
                    + " playedKeysSize=" + playedSpeechChunkKeys.size();
        }
    }

    private void finishCurrentSseRoundIfReady() {
        boolean ready;
        String notFinishedReason = "";
        synchronized (speechQueueLock) {
            int pendingUnplayed = getPendingUnplayedChunkCountLocked();
            Log.d(TAG, "[SSE][State] reason=finish_check"
                    + " serverDone=" + serverDone
                    + " textGenerating=" + textGenerating
                    + " speechGenerating=" + speechGenerating
                    + " speechPlaying=" + speechPlaying
                    + " expectedChunkIndex=" + nextSpeechIndexToPlay
                    + " expectedSpeechChunkCount=" + currentSpeechChunkCount
                    + " pendingUnplayed=" + pendingUnplayed
                    + " pendingIndexes=" + getPendingIndexesTextLocked());
            ready = serverDone
                    && !textGenerating
                    && !speechGenerating
                    && !speechPlaying
                    && isSpeechQueueDrainedLocked();
            if (!serverDone) {
                notFinishedReason = "server_not_done";
            } else if (textGenerating) {
                notFinishedReason = "text_generating";
            } else if (speechGenerating) {
                notFinishedReason = "speech_generating";
            } else if (speechPlaying) {
                notFinishedReason = "speech_playing";
            } else if (!isSpeechQueueDrainedLocked()) {
                notFinishedReason = "speech_queue_not_drained";
            }
            if (!ready) {
                scheduleSpeechMissingFallbackIfNeededLocked("finish_check");
            }
        }
        if (guideEnded) {
            Log.d(TAG, "[SSE][State] round not finished reason=guide_ended");
            return;
        }
        if (!ready) {
            Log.d(TAG, "[SSE][State] round not finished reason=" + notFinishedReason);
            return;
        }
        Log.d(TAG, "[SSE][State] round finished");
        commitCurrentSseAnswerIfNeeded();
        requesting = false;
        voiceFlowActive = false;
        updateSimulatedSpotButton();
        if (guideStateText != null) {
            guideStateText.setText("讲解完成");
        }
        if (voiceMainButton != null) {
            voiceMainButton.setText("🎙 长按说话");
        }
        stopBackendAudioSpeaking();
        stopMouthSync();
        returnDigitalHumanToIdle();
    }

    private boolean isSpeechQueueDrainedLocked() {
        if (getPendingUnplayedChunkCountLocked() > 0) {
            return false;
        }
        if (currentSpeechChunkCount > 0) {
            return nextSpeechIndexToPlay >= currentSpeechChunkCount;
        }
        return true;
    }

    private boolean hasPendingUnplayedChunksLocked() {
        return getPendingUnplayedChunkCountLocked() > 0;
    }

    private int getPendingUnplayedChunkCountLocked() {
        int count = 0;
        for (Integer index : pendingSpeechChunks.keySet()) {
            if (index != null && index >= nextSpeechIndexToPlay) {
                count++;
            }
        }
        return count;
    }

    private void handleGuideChatSseFailure(final String question, final String fallbackMessage, boolean networkError) {
        final boolean useNfcFallback = shouldUseNfcGuideFallback(question);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (guideEnded) {
                    return;
                }
                Call callToCancel;
                synchronized (speechQueueLock) {
                    callToCancel = currentGuideSseCall;
                    currentGuideSseCall = null;
                    if (currentMessageId.length() > 0) {
                        canceledMessageIds.add(currentMessageId);
                    }
                    if (contextMessageId.length() > 0) {
                        canceledMessageIds.add(contextMessageId);
                    }
                    if (responseMessageId.length() > 0) {
                        canceledMessageIds.add(responseMessageId);
                    }
                    canceledMessageIds.addAll(acceptedMessageIds);
                    textGenerating = false;
                    speechGenerating = false;
                    speechPlaying = false;
                    serverDone = false;
                    speechDoneReceived = false;
                    contextMessageId = "";
                    responseMessageId = "";
                    pendingSpeechChunks.clear();
                    skippedSpeechChunkIndexes.clear();
                    receivedSpeechChunkKeys.clear();
                    playedSpeechChunkKeys.clear();
                    acceptedMessageIds.clear();
                    currentSpeechChunkCount = 0;
                    nextSpeechIndexToPlay = 0;
                    missingFallbackScheduledIndex = -1;
                    missingFallbackScheduledMessageId = "";
                }
                if (callToCancel != null) {
                    callToCancel.cancel();
                }
                stopCurrentAudio();
                requesting = false;
                voiceFlowActive = false;
                updateSimulatedSpotButton();
                guideStateText.setText(useNfcFallback ? "基础讲解" : "请求失败");
                voiceMainButton.setText("🎙 长按说话");
                if (useNfcFallback) {
                    showGuideAnswer(nfcFallbackGuideSummary.trim());
                    showToast("网络讲解暂时失败，已先展示 NFC 点位基础讲解。");
                } else {
                    showGuideAnswer(firstNotEmpty(fallbackMessage, "AI 服务暂时不可用，请稍后再试。"));
                }
                returnDigitalHumanToIdle();
            }
        });
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
        route.routeIntent = true;
        showRouteCardIfNeeded(route);
    }

    private boolean shouldAllowRouteCardForResponse(GuideResponse response) {
        if (response == null) {
            return false;
        }
        String answerStatus = safeString(response.answerStatus).trim().toLowerCase(Locale.ROOT);
        if ("knowledge_miss".equals(answerStatus)
                || "no_evidence".equals(answerStatus)
                || "user_instruction".equals(answerStatus)
                || "short_term_profile".equals(answerStatus)
                || "instruction_saved".equals(answerStatus)
                || "location_confirmation_required".equals(answerStatus)) {
            return false;
        }
        if ("route_recommend".equals(answerStatus)) {
            return response.route != null;
        }
        return true;
    }

    private boolean isRouteIntentQuestion(String question) {
        return isRouteRecommendIntent(question);
    }

    private boolean isRouteRecommendIntent(String text) {
        String safeText = text == null ? "" : text.trim();
        if (safeText.length() == 0) {
            Log.d(TAG, "[RouteIntent] text=, matched=false");
            return false;
        }
        if (!isOnsiteMode() && ("route".equals(contextType) || "route_planning".equals(mode))) {
            Log.d(TAG, "[RouteIntent] text=" + safeRouteIntentLogText(safeText)
                    + ", matched=true, reason=context:route_mode");
            return true;
        }

        String reason = findRouteRecommendIntentReason(safeText);
        boolean matched = reason.length() > 0;
        Log.d(TAG, "[RouteIntent] text=" + safeRouteIntentLogText(safeText)
                + ", matched=" + matched
                + (matched ? ", reason=" + reason : ""));
        return matched;
    }

    private String findRouteRecommendIntentReason(String text) {
        String[] strongKeywords = new String[]{
                "推荐路线",
                "路线推荐",
                "规划路线",
                "路线规划",
                "游览路线",
                "导览路线",
                "最佳路线",
                "最优路线",
                "怎么走",
                "先去哪",
                "先去哪里",
                "接下来去哪",
                "接下来去哪里",
                "下一站去哪",
                "下一站去哪里",
                "玩一圈",
                "逛一圈",
                "半日游",
                "一日游",
                "从这里开始",
                "从当前景点开始",
                "按我的偏好",
                "帮我规划",
                "帮我安排",
                "拍照打卡路线",
                "拍照路线",
                "亲子路线",
                "避开拥挤",
                "省力路线",
                "深度游路线",
                "经典路线",
                "游览顺序",
                "游玩顺序",
                "参观顺序",
                "怎么逛",
                "如何逛"
        };
        for (String keyword : strongKeywords) {
            if (text.contains(keyword)) {
                return "keyword:" + keyword;
            }
        }

        String[] routeActions = new String[]{
                "推荐",
                "安排",
                "规划",
                "帮我推荐",
                "帮我安排",
                "帮我规划",
                "怎么玩",
                "怎么游",
                "怎么逛",
                "玩",
                "逛"
        };
        String[] routeContexts = new String[]{
                "路线",
                "顺序",
                "游览",
                "导览",
                "一圈",
                "半日游",
                "一日游",
                "偏好",
                "拍照打卡",
                "亲子",
                "拥挤",
                "省力",
                "深度游",
                "经典"
        };
        String action = firstMatchedKeyword(text, routeActions);
        String context = firstMatchedKeyword(text, routeContexts);
        if (action.length() > 0 && context.length() > 0) {
            return "combo:" + action + "+" + context;
        }

        String planningAction = firstMatchedKeyword(text, new String[]{"帮我规划", "帮我安排", "规划", "安排"});
        if (planningAction.length() > 0 && text.contains("景点")
                && containsAnyKeyword(text, new String[]{"先", "再", "接下来", "下一个", "下一站", "顺序", "一圈"})) {
            return "combo:" + planningAction + "+景点顺序";
        }

        return "";
    }

    private boolean containsAnyKeyword(String text, String[] keywords) {
        return firstMatchedKeyword(text, keywords).length() > 0;
    }

    private String firstMatchedKeyword(String text, String[] keywords) {
        if (text == null || keywords == null) {
            return "";
        }
        for (String keyword : keywords) {
            if (keyword != null && keyword.length() > 0 && text.contains(keyword)) {
                return keyword;
            }
        }
        return "";
    }

    private String safeRouteIntentLogText(String text) {
        String value = safeString(text).replace("\n", " ").replace("\r", " ").trim();
        return value.length() > 80 ? value.substring(0, 80) + "..." : value;
    }

    private String normalizeRouteRequestSource(String source) {
        String value = source == null ? "" : source.trim();
        if (value.length() == 0) {
            return "text";
        }
        return value;
    }

    private String ensureGuideConversationIdForRequest() {
        String current = firstNotEmpty(conversationId, sessionId);
        if (current.length() == 0) {
            String base = getTargetName();
            if (base == null || base.trim().length() == 0) {
                base = "general";
            }
            current = "guide-" + Math.abs(base.hashCode()) + "-" + System.currentTimeMillis();
        }
        conversationId = current;
        sessionId = current;
        return current;
    }

    private void rememberGuideConversationId(String value) {
        String current = firstNotEmpty(value);
        if (current.length() == 0) {
            return;
        }
        conversationId = current;
        sessionId = current;
        Log.d(TAG, "[GuideChat] conversationId updated=" + current);
    }

    private String normalizeAreaCode(String rawAreaCode, String rawAreaId) {
        String code = firstNotEmpty(rawAreaCode);
        if (code.length() == 0) {
            String idText = firstNotEmpty(rawAreaId);
            if (isIntegerText(idText)) {
                return formatAreaCode(idText);
            }
            return "";
        }
        if (isIntegerText(code)) {
            return formatAreaCode(code);
        }
        return code;
    }

    private String resolveAreaIdForGuideRequest(String rawAreaId, String rawAreaCode) {
        String idText = firstNotEmpty(rawAreaId);
        if (idText.length() > 0) {
            return idText;
        }
        String code = firstNotEmpty(rawAreaCode);
        if (isIntegerText(code)) {
            return code;
        }
        String upper = code.toUpperCase(Locale.ROOT);
        if (upper.startsWith("AREA_")) {
            String suffix = upper.substring("AREA_".length());
            if (isIntegerText(suffix)) {
                try {
                    return String.valueOf(Long.parseLong(suffix));
                } catch (NumberFormatException ignored) {
                    return suffix;
                }
            }
        }
        return "";
    }

    private boolean isIntegerText(String value) {
        String text = safeString(value).trim();
        if (text.length() == 0) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String formatAreaCode(String numericText) {
        try {
            long value = Long.parseLong(safeString(numericText).trim());
            return String.format(Locale.ROOT, "AREA_%04d", value);
        } catch (Exception e) {
            return firstNotEmpty(numericText);
        }
    }

    private String normalizeAvailableMinutesText(String value) {
        String text = safeString(value).trim();
        if (text.length() == 0) {
            return "";
        }
        if (text.contains("全天")) {
            return "360";
        }
        if (text.contains("半天")) {
            return "180";
        }
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.length() == 0) {
            return "";
        }
        try {
            int number = Integer.parseInt(digits);
            if (text.contains("小时")) {
                return String.valueOf(number * 60);
            }
            return String.valueOf(number);
        } catch (Exception e) {
            return "";
        }
    }

    private JSONObject buildTravelPartyObject() {
        JSONObject party = new JSONObject();
        try {
            String groupCount = normalizeGroupSizeText(groupSize);
            if (groupCount.length() > 0) {
                party.put("groupSize", Integer.parseInt(groupCount));
            }
            String merged = (safeString(groupSize) + " " + safeString(travelType) + " " + safeString(visitPreference)).trim();
            if (merged.length() > 0) {
                party.put("withChildren", merged.contains("亲子") || merged.contains("儿童") || merged.contains("孩子"));
                party.put("withElderly", merged.contains("老人") || merged.contains("长辈") || merged.contains("老年"));
            }
        } catch (Exception ignored) {
        }
        return party;
    }

    private String normalizeGroupSizeText(String value) {
        String text = safeString(value).trim();
        if (text.length() == 0) {
            return "";
        }
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.length() == 0) {
            return "";
        }
        if (text.contains("-") && digits.length() > 1) {
            return digits.substring(0, 1);
        }
        return digits;
    }

    private void appendCurrentTravelConditionFields(JSONObject requestJson) throws Exception {
        JSONObject travelParty = buildTravelPartyObject();
        if (travelParty.length() > 0) {
            requestJson.put("travelParty", travelParty);
            requestJson.put("travel_party", travelParty);
        }
        String minutes = firstNotEmpty(availableMinutes, normalizeAvailableMinutesText(estimatedDuration));
        if (minutes.length() > 0) {
            int value = Integer.parseInt(minutes);
            requestJson.put("availableMinutes", value);
            requestJson.put("available_minutes", value);
        }

        String status = resolveCurrentVisitStatusForChat();
        boolean inside = VISIT_STATUS_IN_PARK.equals(status);
        requestJson.put("visitStatus", status);
        requestJson.put("visit_status", status);
        requestJson.put("isInsideArea", inside);
        requestJson.put("is_inside_area", inside);
    }

    private String resolveCurrentVisitStatusForChat() {
        if (isRealOnsiteRouteContext() || isOnsiteGuide || startVisitGuide || allowEndVisit) {
            return VISIT_STATUS_IN_PARK;
        }
        String explicit = firstNotEmpty(visitStatus);
        if (explicit.length() > 0) {
            return normalizeVisitStatusForContract(explicit);
        }
        return VISIT_STATUS_NOT_ARRIVED;
    }

    private JSONObject buildRequestJson(String question) throws Exception {
        return buildRequestJson(question, false);
    }

    private JSONObject buildRequestJson(String question, boolean suppressRoute) throws Exception {
        return buildRequestJson(question, suppressRoute, "text");
    }

    private JSONObject buildRequestJson(String question, boolean suppressRoute, String requestSource) throws Exception {
        JSONObject requestJson = new JSONObject();

        String realUserId = getEffectiveNativeUserId();
        String realConversationId = ensureGuideConversationIdForRequest();
        String realSessionId = firstNotEmpty(sessionId, realConversationId);
        boolean routeIntent = shouldAllowRouteResponse(question, suppressRoute);
        String routeRequestSource = normalizeRouteRequestSource(requestSource);
        String requestAreaId = resolveAreaIdForGuideRequest(areaId, areaCode);
        String requestAreaCode = normalizeAreaCode(areaCode, requestAreaId);

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

        requestJson.put("areaCode", safeString(requestAreaCode));
        requestJson.put("area_code", safeString(requestAreaCode));
        putLongOrString(requestJson, "areaId", requestAreaId);
        putLongOrString(requestJson, "area_id", requestAreaId);
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
        putCoordinateIfPresent(requestJson, "currentLatitude", latitude);
        putCoordinateIfPresent(requestJson, "current_latitude", latitude);
        putCoordinateIfPresent(requestJson, "currentLongitude", longitude);
        putCoordinateIfPresent(requestJson, "current_longitude", longitude);

        requestJson.put("mode", safeString(mode));
        requestJson.put("trigger", safeString(trigger));
        requestJson.put("distance", safeString(distance));
        requestJson.put("sourcePage", GUIDE_SOURCE);
        requestJson.put("source_page", GUIDE_SOURCE);
        requestJson.put("routeIntent", routeIntent);
        requestJson.put("route_intent", routeIntent);
        if (routeIntent) {
            requestJson.put("suppressRoute", false);
            requestJson.put("suppress_route", false);
            requestJson.put("requestType", "route_recommend");
            requestJson.put("request_type", "route_recommend");
            requestJson.put("routeEnabled", true);
            requestJson.put("route_enabled", true);
            requestJson.put("routeQuestion", question);
            requestJson.put("route_question", question);
            requestJson.put("triggerQuestion", question);
            requestJson.put("trigger_question", question);
            requestJson.put("routeRequestSource", routeRequestSource);
            requestJson.put("route_request_source", routeRequestSource);
        } else {
            requestJson.put("route", false);
            requestJson.put("suppressRoute", true);
            requestJson.put("suppress_route", true);
            requestJson.put("requestType", "spot_explain");
            requestJson.put("request_type", "spot_explain");
        }
        appendRouteVisitStatusParams(requestJson, routeIntent);
        appendRouteStartParams(requestJson, routeIntent, !routeIntent);

        // 路线推荐时只传 routeStart*；不把手机 GPS 当作景区内路线起点。
        if (!routeIntent) {
            putCoordinateIfPresent(requestJson, "latitude", latitude);
            putCoordinateIfPresent(requestJson, "longitude", longitude);
        }

        // ==================== NFC 位置上下文 & 网络上下文 ====================
        if (nfcLocationActive && !routeIntent) {
            // 如果问题为"给我讲讲这里"且 NFC 已识别景点，携带 NFC location_context
            String nfcSpotId = nfcCurrentSpotId;
            String nfcSpotName = nfcCurrentSpotName;
            String nfcScene = nfcSceneCode;
            long nfcAreaId = 1L;
            String nfcAreaCode = "AREA_0001";
            String nfcAreaName = firstNotEmpty(areaName, "灵山胜境");

            requestJson.put("areaId", nfcAreaId);
            requestJson.put("area_id", nfcAreaId);
            requestJson.put("areaCode", nfcAreaCode);
            requestJson.put("area_code", nfcAreaCode);
            requestJson.put("areaName", safeString(nfcAreaName));
            requestJson.put("area_name", safeString(nfcAreaName));

            if (!nfcSpotId.isEmpty() || !nfcScene.isEmpty()) {
                JSONObject locCtx = new JSONObject();
                locCtx.put("source", "NFC");
                locCtx.put("confidence", 0.98);
                locCtx.put("confidence_level", "HIGH");
                locCtx.put("area_id", nfcAreaId);
                locCtx.put("areaId", nfcAreaId);
                locCtx.put("area_code", nfcAreaCode);
                locCtx.put("areaCode", nfcAreaCode);
                locCtx.put("area_name", safeString(nfcAreaName));
                locCtx.put("areaName", safeString(nfcAreaName));
                putLongOrString(locCtx, "current_spot_id", nfcSpotId);
                putLongOrString(locCtx, "currentSpotId", nfcSpotId);
                locCtx.put("current_spot_name", safeString(nfcSpotName));
                locCtx.put("currentSpotName", safeString(nfcSpotName));
                locCtx.put("scene_code", safeString(nfcScene));
                locCtx.put("sceneCode", safeString(nfcScene));
                requestJson.put("location_context", locCtx);
                requestJson.put("locationContext", locCtx);

                // 更新 current_spot 为 NFC 识别的景点
                requestJson.put("currentSpotId", safeString(nfcSpotId));
                requestJson.put("current_spot_id", safeString(nfcSpotId));
                requestJson.put("currentSpotName", safeString(nfcSpotName));
                requestJson.put("current_spot_name", safeString(nfcSpotName));
                requestJson.put("sceneCode", safeString(nfcScene));
                requestJson.put("scene_code", safeString(nfcScene));

                Log.d(TAG, "[NFC] location_context injected: spot=" + nfcSpotName + ", scene=" + nfcScene);
            }

            // 网络上下文
            NetworkLevel level = networkStateHelper != null
                    ? networkStateHelper.getNetworkLevel()
                    : NetworkLevel.NORMAL;
            JSONObject netCtx = new JSONObject();
            netCtx.put("network_level", level.name());
            netCtx.put("networkLevel", level.name());
            if (level == NetworkLevel.WEAK) {
                netCtx.put("prefer_text_first", true);
                netCtx.put("preferTextFirst", true);
                netCtx.put("tts_async", true);
                netCtx.put("ttsAsync", true);
            } else {
                netCtx.put("prefer_text_first", false);
                netCtx.put("preferTextFirst", false);
                netCtx.put("tts_async", false);
                netCtx.put("ttsAsync", false);
            }
            netCtx.put("allow_offline_fallback", true);
            netCtx.put("allowOfflineFallback", true);
            requestJson.put("network_context", netCtx);
            requestJson.put("networkContext", netCtx);
            Log.d(TAG, "[NFC] network_context injected: level=" + level.name());
            Object locationContextSnake = requestJson.opt("location_context");
            Object locationContextCamel = requestJson.opt("locationContext");
            Object networkContextSnake = requestJson.opt("network_context");
            Object networkContextCamel = requestJson.opt("networkContext");
            Log.d(TAG, "[GuideChat] context field types: location_context="
                    + (locationContextSnake == null ? "null" : locationContextSnake.getClass().getSimpleName())
                    + ", locationContext=" + (locationContextCamel == null ? "null" : locationContextCamel.getClass().getSimpleName())
                    + ", network_context=" + (networkContextSnake == null ? "null" : networkContextSnake.getClass().getSimpleName())
                    + ", networkContext=" + (networkContextCamel == null ? "null" : networkContextCamel.getClass().getSimpleName()));
        }

        ensureGuideAiContractFields(requestJson, routeIntent, !routeIntent || suppressRoute);

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
        if (routeIntent) {
            Log.d(TAG, "[RouteRequest] source=" + routeRequestSource);
            Log.d(TAG, "[RouteRequest] routeIntent=true, suppressRoute=false, requestType=route_recommend");
        }
        logAiQuestion(question);

        return requestJson;
    }

    private void logRouteContext(JSONObject requestJson, String question, boolean allowRouteResponse) {
        try {
            Log.d(TAG, "[RouteContext] entry=" + safeString(entry)
                    + ", mode=" + safeString(mode)
                    + ", isOnsiteGuide=" + isOnsiteGuide
                    + ", startVisitGuide=" + startVisitGuide
                    + ", visitId=" + safeString(visitId)
                    + ", isRealOnsiteRouteContext=" + isRealOnsiteRouteContext()
                    + ", routeIntent=" + allowRouteResponse
                    + ", visitStatus=" + resolveRouteVisitStatusForRequest()
                    + ", isInsideArea=" + resolveIsInsideAreaForRequest()
                    + ", routeStartType=" + routeStartType
                    + ", currentSpotId=" + safeString(routeStartCurrentSpotId)
                    + ", currentSpotName=" + safeString(routeStartCurrentSpotName));
        } catch (Exception e) {
            Log.d(TAG, "[RouteContext] log error: " + e.getMessage());
        }
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
        putCoordinateIfPresent(requestJson, "routeStartLatitude", routeStartLatitude);
        putCoordinateIfPresent(requestJson, "route_start_latitude", routeStartLatitude);
        putCoordinateIfPresent(requestJson, "routeStartLongitude", routeStartLongitude);
        putCoordinateIfPresent(requestJson, "route_start_longitude", routeStartLongitude);
        putCoordinateIfPresent(requestJson, "startLatitude", routeStartLatitude);
        putCoordinateIfPresent(requestJson, "start_latitude", routeStartLatitude);
        putCoordinateIfPresent(requestJson, "startLongitude", routeStartLongitude);
        putCoordinateIfPresent(requestJson, "start_longitude", routeStartLongitude);
        if (routeIntent) {
            requestJson.put("currentSpotId", safeString(startSpotId));
            requestJson.put("current_spot_id", safeString(startSpotId));
            requestJson.put("currentSpotName", safeString(startSpotName));
            requestJson.put("current_spot_name", safeString(startSpotName));
            putCoordinateIfPresent(requestJson, "latitude", routeStartLatitude);
            putCoordinateIfPresent(requestJson, "longitude", routeStartLongitude);
            Log.d(TAG, "[RouteStart] send route request start spotId=" + startSpotId
                    + ", spotName=" + startSpotName);
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
            String requestVisitStatus = resolveRouteVisitStatusForRequest();
            String requestIsInsideArea = resolveIsInsideAreaForRequest();
            if (requestVisitStatus.length() > 0) {
                clientContext.put("visit_status", requestVisitStatus);
            }
            if (requestIsInsideArea.length() > 0) {
                clientContext.put("is_inside_area", parseBooleanLike(requestIsInsideArea));
            }
            clientContext.put("routeTrigger", "manual");
            clientContext.put("requestType", "route_recommend");
            clientContext.put("routeEnabled", true);
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

    private void appendRouteVisitStatusParams(JSONObject requestJson, boolean routeIntent) throws Exception {
        if (!routeIntent) {
            return;
        }

        String requestVisitStatus = resolveRouteVisitStatusForRequest();
        String requestIsInsideArea = resolveIsInsideAreaForRequest();

        if (requestVisitStatus.length() > 0) {
            requestJson.put("visit_status", requestVisitStatus);
            requestJson.put("visitStatus", requestVisitStatus);
        }
        if (requestIsInsideArea.length() > 0) {
            boolean inside = parseBooleanLike(requestIsInsideArea);
            requestJson.put("is_inside_area", inside);
            requestJson.put("isInsideArea", inside);
        }

        JSONObject context = buildRouteLocationContextForRequest();
        if (context != null) {
            requestJson.put("location_context", context);
            requestJson.put("locationContext", context);
        }
    }

    private String resolveRouteVisitStatusForRequest() {
        if (isRealOnsiteRouteContext()) {
            return VISIT_STATUS_IN_PARK;
        }
        // 非现场入口：优先使用 visitStatus；未传则默认 NOT_ARRIVED
        String explicit = firstNotEmpty(visitStatus);
        if (explicit.length() > 0) {
            return normalizeVisitStatusForContract(explicit);
        }
        return VISIT_STATUS_NOT_ARRIVED;
    }

    private String resolveIsInsideAreaForRequest() {
        if (isRealOnsiteRouteContext()) {
            return "true";
        }
        // 非现场入口：优先使用 isInsideArea；未传则默认 false
        String explicit = firstNotEmpty(isInsideArea);
        if (explicit.length() > 0) {
            return String.valueOf(parseBooleanLike(explicit));
        }
        return "false";
    }

    private JSONObject buildRouteLocationContextForRequest() {
        try {
            if (locationContext != null && locationContext.trim().startsWith("{")) {
                return new JSONObject(locationContext.trim());
            }

            if (latitude.length() == 0 && longitude.length() == 0) {
                return null;
            }

            JSONObject context = new JSONObject();
            context.put("source", "ANDROID_NATIVE");
            putCoordinateIfPresent(context, "latitude", latitude);
            putCoordinateIfPresent(context, "longitude", longitude);
            context.put("visit_status", resolveRouteVisitStatusForRequest());
            context.put("is_inside_area", parseBooleanLike(resolveIsInsideAreaForRequest()));
            return context;
        } catch (Exception e) {
            return null;
        }
    }

    private void ensureGuideAiContractFields(JSONObject requestJson, boolean routeIntent, boolean suppressRoute) throws Exception {
        if (requestJson == null) {
            return;
        }

        String requestAreaId = firstNotEmpty(
                getJsonText(requestJson, "areaId", "area_id"),
                resolveAreaIdForGuideRequest(areaId, areaCode)
        );
        String requestAreaCode = normalizeAreaCode(
                firstNotEmpty(getJsonText(requestJson, "areaCode", "area_code"), areaCode),
                requestAreaId
        );
        String requestAreaName = firstNotEmpty(
                getJsonText(requestJson, "areaName", "area_name"),
                areaName,
                parkName,
                scenicName
        );
        String requestSpotId = firstNotEmpty(
                getJsonText(requestJson, "currentSpotId", "current_spot_id"),
                nfcLocationActive ? nfcCurrentSpotId : "",
                spotId,
                scenicId
        );
        String requestSpotName = firstNotEmpty(
                getJsonText(requestJson, "currentSpotName", "current_spot_name"),
                nfcLocationActive ? nfcCurrentSpotName : "",
                spotName,
                scenicName
        );
        String requestSceneCode = firstNotEmpty(
                getJsonText(requestJson, "sceneCode", "scene_code"),
                nfcLocationActive ? nfcSceneCode : "",
                spotId,
                scenicId
        );

        putLongOrString(requestJson, "areaId", requestAreaId);
        putLongOrString(requestJson, "area_id", requestAreaId);
        requestJson.put("areaCode", safeString(requestAreaCode));
        requestJson.put("area_code", safeString(requestAreaCode));
        requestJson.put("areaName", safeString(requestAreaName));
        requestJson.put("area_name", safeString(requestAreaName));
        requestJson.put("currentSpotId", safeString(requestSpotId));
        requestJson.put("current_spot_id", safeString(requestSpotId));
        requestJson.put("currentSpotName", safeString(requestSpotName));
        requestJson.put("current_spot_name", safeString(requestSpotName));
        requestJson.put("sceneCode", safeString(requestSceneCode));
        requestJson.put("scene_code", safeString(requestSceneCode));

        JSONObject location = firstJsonObject(
                getJsonObject(requestJson, "locationContext", "location_context"),
                buildGuideLocationContextObject(requestAreaId, requestAreaCode, requestAreaName, requestSpotId, requestSpotName, requestSceneCode)
        );
        fillGuideLocationContext(location, requestAreaId, requestAreaCode, requestAreaName, requestSpotId, requestSpotName, requestSceneCode);
        requestJson.put("locationContext", location);
        requestJson.put("location_context", location);

        JSONObject network = firstJsonObject(
                getJsonObject(requestJson, "networkContext", "network_context"),
                buildGuideNetworkContextObject()
        );
        requestJson.put("networkContext", network);
        requestJson.put("network_context", network);

        JSONObject context = getJsonObject(requestJson, "context");
        if (context == null) {
            context = new JSONObject();
        }
        putIfMissingText(context, "currentSpotId", requestSpotId);
        putIfMissingText(context, "current_spot_id", requestSpotId);
        putIfMissingText(context, "currentSpotName", requestSpotName);
        putIfMissingText(context, "current_spot_name", requestSpotName);
        putIfMissingText(context, "sceneCode", requestSceneCode);
        putIfMissingText(context, "scene_code", requestSceneCode);
        context.put("location", location);
        requestJson.put("context", context);

        appendCurrentTravelConditionFields(requestJson);

        JSONObject options = getJsonObject(requestJson, "options");
        if (options == null) {
            options = new JSONObject();
        }
        putIfMissingText(options, "responseMode", "digital_human");
        putIfMissingBoolean(options, "enableTts", true);
        putIfMissingText(options, "ttsMode", "async");
        putIfMissingBoolean(options, "includeMouthFrames", true);
        JSONObject routeOptions = getJsonObject(options, "route");
        if (routeOptions == null) {
            routeOptions = new JSONObject();
        }
        routeOptions.put("enabled", routeIntent);
        routeOptions.put("suppressRoute", suppressRoute);
        routeOptions.put("suppress_route", suppressRoute);
        options.put("route", routeOptions);
        requestJson.put("options", options);

        Log.d(TAG, "[GuideChat] contract fields conversationId="
                + safeString(getJsonText(requestJson, "conversationId", "conversation_id"))
                + ", areaCode=" + requestAreaCode
                + ", areaId=" + requestAreaId
                + ", currentSpotId=" + requestSpotId
                + ", currentSpotName=" + requestSpotName
                + ", sceneCode=" + requestSceneCode);
    }

    private JSONObject buildGuideLocationContextObject(String requestAreaId,
                                                       String requestAreaCode,
                                                       String requestAreaName,
                                                       String requestSpotId,
                                                       String requestSpotName,
                                                       String requestSceneCode) {
        JSONObject location = null;
        try {
            if (locationContext != null && locationContext.trim().startsWith("{")) {
                location = new JSONObject(locationContext.trim());
            }
        } catch (Exception ignored) {
            location = null;
        }
        if (location == null) {
            location = new JSONObject();
        }
        fillGuideLocationContext(location, requestAreaId, requestAreaCode, requestAreaName, requestSpotId, requestSpotName, requestSceneCode);
        return location;
    }

    private void fillGuideLocationContext(JSONObject location,
                                          String requestAreaId,
                                          String requestAreaCode,
                                          String requestAreaName,
                                          String requestSpotId,
                                          String requestSpotName,
                                          String requestSceneCode) {
        if (location == null) {
            return;
        }
        try {
            putIfMissingText(location, "source", nfcLocationActive ? "NFC" : "ANDROID_NATIVE");
            putLongOrStringIfMissing(location, "areaId", requestAreaId);
            putLongOrStringIfMissing(location, "area_id", requestAreaId);
            putIfMissingText(location, "areaCode", requestAreaCode);
            putIfMissingText(location, "area_code", requestAreaCode);
            putIfMissingText(location, "areaName", requestAreaName);
            putIfMissingText(location, "area_name", requestAreaName);
            putLongOrStringIfMissing(location, "currentSpotId", requestSpotId);
            putLongOrStringIfMissing(location, "current_spot_id", requestSpotId);
            putIfMissingText(location, "currentSpotName", requestSpotName);
            putIfMissingText(location, "current_spot_name", requestSpotName);
            putIfMissingText(location, "sceneCode", requestSceneCode);
            putIfMissingText(location, "scene_code", requestSceneCode);
            putCoordinateIfPresent(location, "latitude", latitude);
            putCoordinateIfPresent(location, "longitude", longitude);
            putIfMissingText(location, "visit_status", resolveRouteVisitStatusForRequest());
            if (!location.has("is_inside_area") || location.isNull("is_inside_area")) {
                location.put("is_inside_area", parseBooleanLike(resolveIsInsideAreaForRequest()));
            }
        } catch (Exception ignored) {
        }
    }

    private JSONObject buildGuideNetworkContextObject() {
        JSONObject network = new JSONObject();
        try {
            NetworkLevel level = networkStateHelper != null
                    ? networkStateHelper.getNetworkLevel()
                    : NetworkLevel.NORMAL;
            network.put("network_level", level.name());
            network.put("networkLevel", level.name());
            network.put("prefer_text_first", level == NetworkLevel.WEAK);
            network.put("preferTextFirst", level == NetworkLevel.WEAK);
            network.put("tts_async", level == NetworkLevel.WEAK);
            network.put("ttsAsync", level == NetworkLevel.WEAK);
            network.put("allow_offline_fallback", true);
            network.put("allowOfflineFallback", true);
        } catch (Exception ignored) {
        }
        return network;
    }

    private JSONObject firstJsonObject(JSONObject first, JSONObject fallback) {
        return first != null ? first : fallback;
    }

    private void putIfMissingText(JSONObject object, String key, String value) throws Exception {
        if (object == null || key == null) {
            return;
        }
        if (!object.has(key) || object.isNull(key) || safeString(String.valueOf(object.opt(key))).trim().length() == 0) {
            object.put(key, safeString(value));
        }
    }

    private void putIfMissingBoolean(JSONObject object, String key, boolean value) throws Exception {
        if (object == null || key == null) {
            return;
        }
        if (!object.has(key) || object.isNull(key)) {
            object.put(key, value);
        }
    }

    private void putLongOrStringIfMissing(JSONObject object, String key, String value) throws Exception {
        if (object == null || key == null) {
            return;
        }
        if (!object.has(key) || object.isNull(key) || safeString(String.valueOf(object.opt(key))).trim().length() == 0) {
            putLongOrString(object, key, value);
        }
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
                    String realConversationId = ensureGuideConversationIdForRequest();
                    String realSessionId = firstNotEmpty(sessionId, realConversationId);
                    String requestAreaId = resolveAreaIdForGuideRequest(areaId, areaCode);
                    String requestAreaCode = normalizeAreaCode(areaCode, requestAreaId);
                    String requestAreaName = firstNotEmpty(areaName, parkName, scenicName);
                    String requestSceneCode = firstNotEmpty(spotId, scenicId);
                    String requestSceneName = firstNotEmpty(spotName, scenicName);
                    String requestSpotId = firstNotEmpty(spotId, scenicId);
                    String requestSpotName = firstNotEmpty(spotName, scenicName);
                    JSONObject voiceLocationContext = buildGuideLocationContextObject(
                            requestAreaId,
                            requestAreaCode,
                            requestAreaName,
                            requestSpotId,
                            requestSpotName,
                            requestSceneCode
                    );
                    JSONObject voiceNetworkContext = buildGuideNetworkContextObject();
                    JSONObject voiceContext = new JSONObject();
                    voiceContext.put("currentSpotId", safeString(requestSpotId));
                    voiceContext.put("current_spot_id", safeString(requestSpotId));
                    voiceContext.put("currentSpotName", safeString(requestSpotName));
                    voiceContext.put("current_spot_name", safeString(requestSpotName));
                    voiceContext.put("sceneCode", safeString(requestSceneCode));
                    voiceContext.put("scene_code", safeString(requestSceneCode));
                    voiceContext.put("location", voiceLocationContext);
                    JSONObject voiceOptions = new JSONObject();
                    voiceOptions.put("responseMode", "digital_human");
                    voiceOptions.put("enableTts", true);
                    voiceOptions.put("ttsMode", "async");
                    voiceOptions.put("includeMouthFrames", true);
                    JSONObject voiceRouteOptions = new JSONObject();
                    voiceRouteOptions.put("enabled", false);
                    voiceRouteOptions.put("suppressRoute", true);
                    voiceRouteOptions.put("suppress_route", true);
                    voiceOptions.put("route", voiceRouteOptions);

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

                    writeFormField(outputStream, boundary, "conversationId", realConversationId);
                    writeFormField(outputStream, boundary, "conversation_id", realConversationId);
                    writeFormField(outputStream, boundary, "question", "");

                    writeFormField(outputStream, boundary, "scenicName", getTargetName());
                    writeFormField(outputStream, boundary, "scenic_name", getTargetName());
                    writeGuideContextFormFields(outputStream, boundary, realUserId, "voice");
                    writeFormField(outputStream, boundary, "areaCode", safeString(requestAreaCode));
                    writeFormField(outputStream, boundary, "area_code", safeString(requestAreaCode));
                    writeFormField(outputStream, boundary, "areaId", safeString(requestAreaId));
                    writeFormField(outputStream, boundary, "area_id", safeString(requestAreaId));
                    writeFormField(outputStream, boundary, "areaName", safeString(requestAreaName));
                    writeFormField(outputStream, boundary, "area_name", safeString(requestAreaName));

                    writeFormField(outputStream, boundary, "sceneCode", safeString(requestSceneCode));
                    writeFormField(outputStream, boundary, "scene_code", safeString(requestSceneCode));
                    writeFormField(outputStream, boundary, "sceneName", safeString(requestSceneName));
                    writeFormField(outputStream, boundary, "scene_name", safeString(requestSceneName));

                    writeFormField(outputStream, boundary, "currentSpotId", safeString(requestSpotId));
                    writeFormField(outputStream, boundary, "current_spot_id", safeString(requestSpotId));
                    writeFormField(outputStream, boundary, "currentSpotName", safeString(requestSpotName));
                    writeFormField(outputStream, boundary, "current_spot_name", safeString(requestSpotName));

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
                    String requestVisitStatus = resolveRouteVisitStatusForRequest();
                    String requestIsInsideArea = resolveIsInsideAreaForRequest();
                    if (requestVisitStatus.length() > 0) {
                        writeFormField(outputStream, boundary, "visit_status", requestVisitStatus);
                        writeFormField(outputStream, boundary, "visitStatus", requestVisitStatus);
                    }
                    if (requestIsInsideArea.length() > 0) {
                        writeFormField(outputStream, boundary, "is_inside_area", String.valueOf(parseBooleanLike(requestIsInsideArea)));
                        writeFormField(outputStream, boundary, "isInsideArea", String.valueOf(parseBooleanLike(requestIsInsideArea)));
                    }
                    String requestAvailableMinutes = firstNotEmpty(availableMinutes, normalizeAvailableMinutesText(estimatedDuration));
                    if (requestAvailableMinutes.length() > 0) {
                        writeFormField(outputStream, boundary, "availableMinutes", requestAvailableMinutes);
                        writeFormField(outputStream, boundary, "available_minutes", requestAvailableMinutes);
                    }
                    JSONObject requestTravelParty = buildTravelPartyObject();
                    if (requestTravelParty.length() > 0) {
                        writeFormField(outputStream, boundary, "travelParty", requestTravelParty.toString());
                        writeFormField(outputStream, boundary, "travel_party", requestTravelParty.toString());
                    }
                    JSONObject routeLocationContext = buildRouteLocationContextForRequest();
                    if (routeLocationContext != null) {
                        writeFormField(outputStream, boundary, "location_context", routeLocationContext.toString());
                        writeFormField(outputStream, boundary, "locationContext", routeLocationContext.toString());
                    } else {
                        writeFormField(outputStream, boundary, "location_context", voiceLocationContext.toString());
                        writeFormField(outputStream, boundary, "locationContext", voiceLocationContext.toString());
                    }
                    writeFormField(outputStream, boundary, "network_context", voiceNetworkContext.toString());
                    writeFormField(outputStream, boundary, "networkContext", voiceNetworkContext.toString());
                    writeFormField(outputStream, boundary, "context", voiceContext.toString());
                    writeFormField(outputStream, boundary, "options", voiceOptions.toString());
                    writeFormField(outputStream, boundary, "route", "false");
                    writeFormField(outputStream, boundary, "suppressRoute", "true");
                    writeFormField(outputStream, boundary, "suppress_route", "true");
                    writeFormField(outputStream, boundary, "requestType", "voice_chat");
                    writeFormField(outputStream, boundary, "request_type", "voice_chat");

                    Log.d(TAG, "语音问答真实身份参数 realUserId=" + realUserId
                            + ", appUserId=" + appUserId
                            + ", loginUserId=" + loginUserId
                            + ", visitorId=" + visitorId
                            + ", appAuthSessionId=" + appAuthSessionId
                            + ", sessionId=" + realSessionId
                            + ", conversationId=" + realConversationId
                            + ", areaCode=" + requestAreaCode
                            + ", areaId=" + requestAreaId
                            + ", currentSpotId=" + requestSpotId
                            + ", currentSpotName=" + requestSpotName
                            + ", sceneCode=" + requestSceneCode
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

                                if (guideResponse.conversationId != null && guideResponse.conversationId.trim().length() > 0) {
                                    rememberGuideConversationId(guideResponse.conversationId);
                                }

                                String recognizedText = guideResponse.questionText == null ? "" : guideResponse.questionText.trim();
                                if (recognizedText.length() > 0
                                        && !"null".equalsIgnoreCase(recognizedText)
                                        && isRouteRecommendIntent(recognizedText)) {
                                    Log.d(TAG, "[RouteIntent] voice input route request, use route button flow");
                                    requestRouteRecommendation(recognizedText, "voice_input");
                                    return;
                                }

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
                                        (guideResponse.routeIntent || isRouteIntentQuestion(guideResponse.questionText))
                                                && shouldAllowRouteCardForResponse(guideResponse),
                                        "voice"
                                );

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
        return parseGuideResponse(responseText, 200, false);
    }

    private GuideResponse parseGuideResponse(String responseText, int httpStatus) {
        return parseGuideResponse(responseText, httpStatus, true);
    }

    private GuideResponse parseGuideResponse(String responseText, int httpStatus, boolean requireBusinessCode) {
        GuideResponse result = new GuideResponse();
        result.success = false;
        result.httpStatus = httpStatus;

        try {
            JSONObject root = new JSONObject(responseText);
            JSONObject data = null;
            String dataText = "";
            if (root.has("data") && !root.isNull("data")) {
                Object dataObj = root.get("data");
                if (dataObj instanceof JSONObject) {
                    data = (JSONObject) dataObj;
                } else if (!(dataObj instanceof JSONArray)) {
                    dataText = String.valueOf(dataObj).trim();
                }
            }

            JSONObject source = data != null ? data : root;
            JSONObject audioJson = getJsonObject(source, "audio");
            JSONObject rootAudioJson = getJsonObject(root, "audio");
            JSONObject mouthJson = getJsonObject(source, "mouth");
            JSONObject rootMouthJson = getJsonObject(root, "mouth");
            JSONObject digitalHumanJson = getJsonObject(source, "digitalHuman", "digital_human");

            int businessCode = root.optInt("code", -1);
            String businessMsg = firstNotEmpty(
                    root.optString("msg", ""),
                    root.optString("message", "")
            );
            result.businessCode = businessCode;
            result.businessMsg = businessMsg;
            Log.d(TAG, "[GuideChat] businessCode=" + businessCode + " msg=" + businessMsg);

            result.interactionCategory = firstNotEmpty(
                    getJsonText(source, "interactionCategory", "interaction_category"),
                    getJsonText(root, "interactionCategory", "interaction_category")
            );
            result.answerStatus = firstNotEmpty(
                    getJsonText(source, "answerStatus", "answer_status"),
                    getJsonText(root, "answerStatus", "answer_status")
            );
            result.routeIntent = getBooleanCompat(source, "routeIntent", "route_intent")
                    || getBooleanCompat(root, "routeIntent", "route_intent");
            result.fallbackReason = firstNotEmpty(
                    getJsonText(source, "fallbackReason", "fallback_reason"),
                    getJsonText(root, "fallbackReason", "fallback_reason")
            );
            result.issueCategory = firstNotEmpty(
                    getJsonText(source, "issueCategory", "issue_category"),
                    getJsonText(root, "issueCategory", "issue_category")
            );
            result.issueType = firstNotEmpty(
                    getJsonText(source, "issueType", "issue_type"),
                    getJsonText(root, "issueType", "issue_type")
            );
            result.requiresAdminAction = getBooleanCompat(source, "requiresAdminAction", "requires_admin_action")
                    || getBooleanCompat(root, "requiresAdminAction", "requires_admin_action");
            Object knowledgeGapCandidate = firstJsonValue(source, root, "knowledgeGapCandidate", "knowledge_gap_candidate");
            if (knowledgeGapCandidate != null) {
                result.knowledgeGapCandidate = String.valueOf(knowledgeGapCandidate);
            }
            Object grounding = firstJsonValue(source, root, "grounding");
            if (grounding != null) {
                result.grounding = String.valueOf(grounding);
            }
            Object sources = firstJsonValue(source, root, "sources");
            if (sources != null) {
                result.sources = String.valueOf(sources);
            }
            String parsedAnswer = firstNotEmpty(
                    getJsonText(data, "answer", "reply", "content"),
                    dataText,
                    getJsonText(root, "answer", "reply", "content")
            );

            boolean httpOk = httpStatus >= 200 && httpStatus < 300;
            boolean businessOk = businessCode == 0 || businessCode == 200
                    || (!requireBusinessCode && businessCode == -1);
            boolean statusCanDisplay = isDisplayableAnswerStatus(result.answerStatus) && parsedAnswer.length() > 0;
            if (!httpOk || (!businessOk && !statusCanDisplay)) {
                if (businessMsg.contains("用户不存在")
                        || businessMsg.contains("登录")
                        || businessMsg.contains("token")
                        || businessMsg.contains("Token")) {
                    result.answer = "登录状态异常，请返回首页重新进入导览";
                } else {
                    result.answer = businessMsg.length() > 0 ? businessMsg : "请求失败，请稍后重试";
                }
                Log.e(TAG, "[GuideChat] parse failed: httpStatus=" + httpStatus
                        + ", businessCode=" + businessCode
                        + ", msg=" + businessMsg
                        + ", response=" + responseText);
                return result;
            }
            result.success = true;

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

            result.answer = parsedAnswer;

            result.audioUrl = firstNotEmpty(
                    getJsonText(data, "audioUrl", "audio_url"),
                    getJsonText(root, "audioUrl", "audio_url"),
                    getJsonText(audioJson, "url", "audioUrl", "audio_url"),
                    getJsonText(rootAudioJson, "url", "audioUrl", "audio_url")
            );
            result.audioStatus = firstNotEmpty(
                    getJsonText(source, "audioStatus", "audio_status", "ttsStatus", "tts_status"),
                    getJsonText(root, "audioStatus", "audio_status", "ttsStatus", "tts_status"),
                    getJsonText(audioJson, "status", "audioStatus", "audio_status", "ttsStatus", "tts_status"),
                    getJsonText(rootAudioJson, "status", "audioStatus", "audio_status", "ttsStatus", "tts_status")
            );
            result.ttsStatus = firstNotEmpty(
                    getJsonText(source, "ttsStatus", "tts_status"),
                    getJsonText(root, "ttsStatus", "tts_status"),
                    getJsonText(audioJson, "ttsStatus", "tts_status", "status"),
                    getJsonText(rootAudioJson, "ttsStatus", "tts_status", "status")
            );
            result.ttsTaskId = firstNotEmpty(
                    getJsonText(source, "ttsTaskId", "tts_task_id", "taskId", "task_id"),
                    getJsonText(root, "ttsTaskId", "tts_task_id", "taskId", "task_id"),
                    getJsonText(audioJson, "taskId", "task_id"),
                    getJsonText(rootAudioJson, "taskId", "task_id")
            );
            result.audioDurationMs = parseLongOrDefault(firstNotEmpty(
                    getJsonText(source, "audioDurationMs", "audio_duration_ms", "durationMs", "duration_ms"),
                    getJsonText(root, "audioDurationMs", "audio_duration_ms", "durationMs", "duration_ms"),
                    getJsonText(audioJson, "audioDurationMs", "audio_duration_ms", "durationMs", "duration_ms", "duration"),
                    getJsonText(rootAudioJson, "audioDurationMs", "audio_duration_ms", "durationMs", "duration_ms", "duration")
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
            if (result.conversationId == null || result.conversationId.trim().length() == 0) {
                result.conversationId = firstNotEmpty(
                        getJsonText(source, "sessionId", "session_id"),
                        getJsonText(root, "conversationId", "conversation_id", "sessionId", "session_id")
                );
            }

            result.messageId = source.optString("messageId", "");
            if (result.messageId == null || result.messageId.trim().length() == 0) {
                result.messageId = source.optString("message_id", "");
            }

            result.ttsError = source.optString("ttsError", "");
            if (result.ttsError == null || result.ttsError.trim().length() == 0) {
                result.ttsError = source.optString("tts_error", "");
            }
            if (result.ttsError == null || result.ttsError.trim().length() == 0) {
                result.ttsError = firstNotEmpty(
                        getJsonText(root, "ttsError", "tts_error"),
                        getJsonText(audioJson, "error", "ttsError", "tts_error"),
                        getJsonText(rootAudioJson, "error", "ttsError", "tts_error")
                );
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
            if (mouthFramesArray == null && source != root) {
                mouthFramesArray = getJsonArray(root, "mouthFrames", "mouth_frames");
            }
            if (mouthFramesArray == null) {
                mouthFramesArray = getJsonArray(mouthJson, "frames", "mouthFrames", "mouth_frames");
            }
            if (mouthFramesArray == null) {
                mouthFramesArray = getJsonArray(rootMouthJson, "frames", "mouthFrames", "mouth_frames");
            }
            if (mouthFramesArray == null) {
                mouthFramesArray = getJsonArray(audioJson, "frames", "mouthFrames", "mouth_frames");
            }
            if (mouthFramesArray == null) {
                mouthFramesArray = getJsonArray(rootAudioJson, "frames", "mouthFrames", "mouth_frames");
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
            result.mouthStatus = firstNotEmpty(
                    getJsonText(source, "mouthStatus", "mouth_status"),
                    getJsonText(root, "mouthStatus", "mouth_status"),
                    getJsonText(mouthJson, "status", "mouthStatus", "mouth_status"),
                    getJsonText(rootMouthJson, "status", "mouthStatus", "mouth_status")
            );
            result.mouthError = firstNotEmpty(
                    getJsonText(source, "mouthError", "mouth_error"),
                    getJsonText(root, "mouthError", "mouth_error"),
                    getJsonText(mouthJson, "error", "mouthError", "mouth_error"),
                    getJsonText(rootMouthJson, "error", "mouthError", "mouth_error")
            );

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

            Log.d(TAG, "[GuideChat] parsed success=true, answerLength=" + safeString(result.answer).length()
                    + ", hasAudioUrl=" + (safeString(result.audioUrl).length() > 0)
                    + ", mouthFrames=" + result.mouthFrames.size()
                    + ", answerStatus=" + safeString(result.answerStatus)
                    + ", interactionCategory=" + safeString(result.interactionCategory)
                    + ", conversationId=" + safeString(result.conversationId));

            return result;
        } catch (Exception e) {
            result.success = false;
            result.answer = firstNotEmpty(responseText, "请求失败，请稍后重试");
            Log.e(TAG, "[GuideChat] parse failed: " + e.getMessage()
                    + ", httpStatus=" + httpStatus
                    + ", response=" + responseText, e);
            return result;
        }
    }

    private RouteInfo parseRouteFromAiResponse(JSONObject data) {
        JSONObject routeJson = findRouteJson(data);
        if (routeJson == null) {
            return null;
        }
        return attachRouteEnvelopeFields(attachRouteEnvelopeFields(parseRouteInfo(routeJson), routeJson), data);
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
        route.schemaVersion = getJsonText(routeJson, "schemaVersion", "schema_version");
        route.planId = getJsonText(routeJson, "planId", "plan_id", "routePlanId", "route_plan_id");
        route.routeId = getJsonText(routeJson, "routeId", "route_id");
        route.originalRouteId = route.routeId;
        route.routeName = getJsonText(routeJson, "routeName", "route_name", "name", "title");
        route.reason = getJsonText(routeJson, "reason", "recommendReason", "recommend_reason");
        route.recommendReason = firstNotEmpty(getJsonText(routeJson, "recommendReason", "recommend_reason"), route.reason);
        route.profileVersion = getJsonText(routeJson, "profileVersion", "profile_version");
        route.matchedTags.addAll(parseMatchedTags(routeJson));
        route.totalDistanceM = getJsonText(routeJson, "totalDistanceM", "total_distance_m", "distanceM", "distance_m");
        route.estimatedDurationMin = getJsonText(routeJson,
                "estimatedDurationMinutes", "estimated_duration_minutes",
                "estimatedDurationMin", "estimated_duration_min",
                "durationMin", "duration_min");
        route.mapAction = getJsonText(routeJson, "mapAction", "map_action");
        route.routeMapReady = getJsonText(routeJson, "routeMapReady", "route_map_ready", "mapReady", "map_ready");
        route.routeMode = getJsonText(routeJson, "route_mode", "routeMode");
        route.visitStatus = getJsonText(routeJson, "visit_status", "visitStatus");
        route.routeIntent = getBooleanCompat(routeJson, "routeIntent", "route_intent");
        route.hasShouldShowRouteCard = hasJsonKey(routeJson, "should_show_route_card", "shouldShowRouteCard");
        route.shouldShowRouteCard = getBooleanCompat(routeJson, "should_show_route_card", "shouldShowRouteCard");
        route.isOfficialTemplate = getBooleanCompat(routeJson, "is_official_template", "isOfficialTemplate");
        route.algorithmVersion = getJsonText(routeJson, "algorithmVersion", "algorithm_version");
        route.rawPolylinePoints.addAll(parseRoutePolyline(routeJson));
        if (route.rawPolylinePoints.size() >= 2) {
            if (hasJsonKey(routeJson,
                    "routePolyline", "route_polyline",
                    "walkingPolyline", "walking_polyline",
                    "amapPolyline", "amap_polyline",
                    "mapPolyline", "map_polyline",
                    "roadPolyline", "road_polyline",
                    "polyline")) {
                Log.d(TAG, "[RouteMap] using backend routePolyline points=" + route.rawPolylinePoints.size());
            } else if (hasJsonKey(routeJson, "segments")) {
                Log.d(TAG, "[RouteMap] using backend segments realPolylinePoints=" + route.rawPolylinePoints.size());
            }
        }

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
                RouteNode node = new RouteNode();
                JSONObject nodeJson = nodesArray.optJSONObject(i);
                if (nodeJson == null) {
                    String nodeName = nodesArray.optString(i, "").trim();
                    if (nodeName.length() == 0) {
                        continue;
                    }
                    node.order = String.valueOf(i + 1);
                    node.orderNumber = i + 1;
                    node.name = nodeName;
                    node.spotName = nodeName;
                    node.scenicName = nodeName;
                    node.displayName = nodeName;
                    node.nodeType = "SPOT";
                    route.nodes.add(node);
                    continue;
                }

                node.order = firstNotEmpty(getJsonText(nodeJson, "sortOrder", "sort_order", "order", "sort", "index"), String.valueOf(i + 1));
                node.orderNumber = parseIntOrDefault(node.order, i + 1);
                node.nodeType = firstNotEmpty(getJsonText(nodeJson, "nodeType", "node_type"), "");
                node.facilityId = getJsonText(nodeJson, "facilityId", "facility_id");
                node.sceneCode = getJsonText(nodeJson, "sceneCode", "scene_code");
                node.id = getJsonText(nodeJson, "spotId", "spot_id", "scenicId", "scenic_id", "sceneCode", "scene_code", "id");
                node.scenicId = getJsonText(nodeJson, "scenicId", "scenic_id", "sceneCode", "scene_code", "id");
                node.spotId = getJsonText(nodeJson, "spotId", "spot_id", "id", "scenicId", "scenic_id");
                node.displayName = getJsonText(nodeJson, "displayName", "display_name");
                node.name = firstNotEmpty(getJsonText(nodeJson, "nodeName", "node_name", "spotName", "spot_name", "scenicName", "scenic_name", "name", "title"), node.displayName);
                node.spotName = firstNotEmpty(getJsonText(nodeJson, "spotName", "spot_name"), node.name, node.displayName);
                node.scenicName = firstNotEmpty(getJsonText(nodeJson, "scenicName", "scenic_name", "name", "title"), node.name, node.displayName);
                if (node.displayName.length() == 0) {
                    node.displayName = firstNotEmpty(node.name, node.spotName, node.scenicName);
                }
                if (node.nodeType.length() == 0 && (node.spotId.length() > 0 || node.sceneCode.length() > 0 || node.scenicId.length() > 0)) {
                    node.nodeType = "SPOT";
                }
                node.guideText = getJsonText(nodeJson, "guideText", "guide_text", "description", "desc");
                node.recommendedStayMin = getJsonText(nodeJson, "recommendedStayMinutes", "recommended_stay_minutes", "recommendedStayMin", "recommended_stay_min", "stayMin", "stay_min");
                node.distanceFromPreviousMeters = getJsonText(nodeJson, "distanceFromPreviousMeters", "distance_from_previous_meters");
                node.estimatedWalkMinutes = getJsonText(nodeJson, "estimatedWalkMinutes", "estimated_walk_minutes");
                node.latitude = getJsonText(nodeJson, "latitude", "lat");
                node.longitude = getJsonText(nodeJson, "longitude", "lng", "lon");
                normalizeRouteNodeLocation(node);
                route.nodes.add(node);
            }
        }

        normalizeRouteNodes(route);
        ensureRouteId(route);

        return route;
    }

    private void ensureRouteId(RouteInfo route) {
        if (route == null) {
            return;
        }
        if (route.originalRouteId.length() == 0 && route.routeId.length() > 0 && !route.localRouteIdGenerated) {
            route.originalRouteId = route.routeId;
        }
        route.routeId = firstNotEmpty(route.routeId, route.planId);
        if (route.routeId.length() == 0) {
            route.routeId = "ROUTE_" + firstNotEmpty(conversationId, sessionId, "LOCAL")
                    + "_" + System.currentTimeMillis();
            route.localRouteIdGenerated = true;
        }
        if (route.planId.length() == 0) {
            route.planId = route.routeId;
        }
    }

    private List<String> parseMatchedTags(JSONObject routeJson) {
        List<String> result = new ArrayList<>();
        JSONArray tags = getJsonArray(routeJson, "matchedTags", "matched_tags");
        if (tags == null) {
            return result;
        }
        for (int i = 0; i < tags.length(); i++) {
            Object item = tags.opt(i);
            String tagCode = "";
            if (item instanceof JSONObject) {
                JSONObject tag = (JSONObject) item;
                tagCode = firstNotEmpty(
                        getJsonText(tag, "tagCode", "tag_code", "code"),
                        getJsonText(tag, "tagName", "tag_name", "name")
                );
            } else if (item != null) {
                tagCode = safeString(String.valueOf(item));
            }
            if (tagCode.trim().length() > 0) {
                result.add(tagCode.trim());
            }
        }
        return result;
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

    private boolean hasJsonKey(JSONObject object, String... keys) {
        if (object == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            if (key != null && object.has(key) && !object.isNull(key)) {
                return true;
            }
        }
        return false;
    }

    private Object firstJsonValue(JSONObject primary, JSONObject fallback, String... keys) {
        Object value = getJsonValue(primary, keys);
        if (value != null) {
            return value;
        }
        return getJsonValue(fallback, keys);
    }

    private Object getJsonValue(JSONObject object, String... keys) {
        if (object == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null || !object.has(key) || object.isNull(key)) {
                continue;
            }
            return object.opt(key);
        }
        return null;
    }

    private boolean isDisplayableAnswerStatus(String answerStatus) {
        String status = safeString(answerStatus).trim().toLowerCase(Locale.ROOT);
        if (status.length() == 0) {
            return true;
        }
        return "grounded".equals(status)
                || "partially_grounded".equals(status)
                || "knowledge_miss".equals(status)
                || "no_evidence".equals(status)
                || "user_instruction".equals(status)
                || "short_term_profile".equals(status)
                || "instruction_saved".equals(status)
                || "non_scenic_chat".equals(status)
                || "route_recommend".equals(status)
                || "offline_fallback".equals(status)
                || "location_confirmation_required".equals(status);
    }

    private List<LatLng> parseRoutePolyline(JSONObject routeJson) {
        List<LatLng> result = new ArrayList<>();
        if (routeJson == null) {
            return result;
        }
        Object polylineObject = null;
        String[] keys = new String[]{
                "routePolyline", "route_polyline",
                "mapPolyline", "map_polyline",
                "roadPolyline", "road_polyline",
                "walkingPolyline", "walking_polyline",
                "amapPolyline", "amap_polyline",
                "polyline", "polylinePoints", "polyline_points",
                "path", "paths", "segments"
        };
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
            if (isFallbackRoutePolylineObject(object)) {
                Log.d(TAG, "[RouteMap] ignore fallback route polyline object success=false/fallback=true");
                return;
            }
            double lat = parseDoubleOrNaN(getJsonText(object, "latitude", "lat"));
            double lon = parseDoubleOrNaN(getJsonText(object, "longitude", "lng", "lon"));
            if (isValidCoordinate(lat, lon)) {
                appendLatLngDedup(points, new LatLng(lat, lon));
                return;
            }
            String[] nestedKeys = new String[]{
                    "points", "polyline", "polylinePoints", "polyline_points",
                    "routePolyline", "route_polyline",
                    "walkingPolyline", "walking_polyline",
                    "amapPolyline", "amap_polyline",
                    "path", "paths", "segments", "steps"
            };
            for (String key : nestedKeys) {
                if (object.has(key) && !object.isNull(key)) {
                    appendRoutePolylineValue(points, object.opt(key));
                }
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

    private boolean isFallbackRoutePolylineObject(JSONObject object) {
        if (object == null) {
            return false;
        }
        if (hasJsonKey(object, "success", "successful") && !getBooleanCompat(object, "success", "successful")) {
            return true;
        }
        if (getBooleanCompat(object, "fallback", "fallbackLine", "fallback_line", "isFallback", "is_fallback")) {
            return true;
        }
        String source = getJsonText(object, "source", "routeMapSource", "route_map_source");
        return "node_fallback".equalsIgnoreCase(source)
                || "fallback".equalsIgnoreCase(source);
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

    private boolean getBooleanCompat(JSONObject object, String... keys) {
        if (object == null || keys == null) {
            return false;
        }

        for (String key : keys) {
            if (key == null || !object.has(key) || object.isNull(key)) {
                continue;
            }
            Object value = object.opt(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            if (value instanceof Number) {
                return ((Number) value).intValue() != 0;
            }
            String text = value == null ? "" : String.valueOf(value).trim();
            if ("true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text) || "0".equals(text) || "no".equalsIgnoreCase(text)) {
                return false;
            }
        }

        return false;
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
        normalizeRouteNodes(route);
        Log.d(TAG, "[RouteMap] route_ready parsed nodes=" + (route.nodes == null ? 0 : route.nodes.size())
                + " polylinePoints=" + (route.rawPolylinePoints == null ? 0 : route.rawPolylinePoints.size())
                + " source=" + (route.rawPolylinePoints != null && route.rawPolylinePoints.size() >= 2 ? "route_ready_polyline" : "route_ready_nodes"));
        if (!shouldShowOnsiteRouteCard(route, route.nodes)) {
            Log.d(TAG, "忽略非现场动态路线卡片 routeMode=" + route.routeMode
                    + ", visitStatus=" + route.visitStatus
                    + ", shouldShow=" + route.shouldShowRouteCard
                    + ", nodes=" + (route.nodes == null ? 0 : route.nodes.size()));
            return false;
        }

        currentRoute = route;
        currentRoutePreview = createInitialRoutePreview(route);
        route.preview = currentRoutePreview;
        routeCardExpanded = false;
        resetRouteDemoState("new_route_ready_replace");
        alignRouteStartState(route);
        renderRouteCard(route);
        if (routeCardContainer != null) {
            routeCardContainer.setVisibility(View.VISIBLE);
        }
        buildRoutePreviewWithAmap(route);
        trackRouteEventOnce("map_card_show", route, null);
        trackRouteEvent("route_view", route, null);
        trackRouteEvent("ROUTE_VIEW", route, null);
        return true;
    }

    private boolean shouldShowOnsiteRouteCard(RouteInfo route, List<RouteNode> nodes) {
        if (route == null) {
            return false;
        }

        String responseVisitStatus = safeString(route.visitStatus).trim();
        String localVisitStatus = resolveCurrentVisitStatusForChat();
        String resolvedVisitStatus = responseVisitStatus.length() > 0 ? responseVisitStatus : localVisitStatus;
        boolean isInPark = isInParkVisitStatus(resolvedVisitStatus)
                || isOnsiteGuide
                || startVisitGuide
                || allowEndVisit;
        boolean routeIntent = route.routeIntent;
        boolean isDynamicInPark = isDynamicInParkRoute(route);
        int spotsSize = nodes == null ? 0 : nodes.size();
        boolean finalShowCard = isInPark
                && routeIntent
                && route != null
                && isDynamicInPark
                && spotsSize > 0;

        Log.d(TAG, "[RouteCardGate] responseVisitStatus=" + responseVisitStatus
                + ", localStatus=" + localVisitStatus
                + ", resolvedVisitStatus=" + resolvedVisitStatus
                + ", routeMode=" + route.routeMode
                + ", routeIntent=" + routeIntent
                + ", spotsSize=" + spotsSize
                + ", finalShowCard=" + finalShowCard);

        if (finalShowCard) {
            Log.d(TAG, "[RouteCard] show dynamic in-park route routeId=" + getProvidedRouteId(route));
        } else {
            Log.d(TAG, "[RouteCard] suppress non-onsite route routeId="
                    + getProvidedRouteId(route)
                    + ", routeMode=" + route.routeMode
                    + ", resolvedVisitStatus=" + resolvedVisitStatus);
        }
        return finalShowCard;
    }

    private boolean isInParkRouteContext(RouteInfo route) {
        if (route == null) {
            return false;
        }
        String responseVisitStatus = safeString(route.visitStatus).trim();
        String resolvedVisitStatus = responseVisitStatus.length() > 0 ? responseVisitStatus : resolveCurrentVisitStatusForChat();
        return isInParkVisitStatus(resolvedVisitStatus)
                || isOnsiteGuide
                || startVisitGuide
                || allowEndVisit;
    }

    private boolean isDynamicInParkRoute(RouteInfo route) {
        return route != null && ROUTE_MODE_DYNAMIC_IN_PARK.equals(normalizeRouteModeForContract(route.routeMode));
    }

    private boolean isOfficialTemplateRoute(RouteInfo route) {
        return route != null && (route.isOfficialTemplate
                || ROUTE_MODE_OFFICIAL_TEMPLATE.equals(normalizeRouteModeForContract(route.routeMode)));
    }

    private String normalizeRouteModeForContract(String routeMode) {
        String text = safeString(routeMode).trim();
        if (text.length() == 0) {
            return "";
        }
        String upper = text.toUpperCase(Locale.ROOT);
        if (ROUTE_MODE_DYNAMIC_IN_PARK.equals(upper)
                || "ONSITE_DYNAMIC".equals(upper)
                || "DYNAMIC".equals(upper)) {
            return ROUTE_MODE_DYNAMIC_IN_PARK;
        }
        if (ROUTE_MODE_OFFICIAL_TEMPLATE.equals(upper)
                || "PRETRIP_TEMPLATE".equals(upper)
                || "PRE_TRIP_TEMPLATE".equals(upper)
                || "TEMPLATE".equals(upper)) {
            return ROUTE_MODE_OFFICIAL_TEMPLATE;
        }
        return upper;
    }

    private void hideRouteCard() {
        hideRouteCard("legacy_call");
    }

    private void hideRouteCard(String reason) {
        Log.d(TAG, "[RouteNav] hide route card reason=" + safeString(reason));
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
        resetRouteDemoState("hide_route_card:" + safeString(reason));
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
            preview.polylinePoints.clear();
            return preview;
        }

        preview.calculating = true;
        preview.message = "正在生成高德步行路线...";
        preview.polylinePoints.clear();
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

        final String routeId = getProvidedRouteId(route);
        final List<RouteNode> effectiveRouteNodes = buildRoutePlanningNodes(preview.routePreviewStartPoint, route);
        final List<RouteNode> locatedNodes = filterLocatedNodes(effectiveRouteNodes);
        final int missingCoords = Math.max(0, effectiveRouteNodes.size() - locatedNodes.size());
        Log.d(TAG, "[RouteMap] open routeId=" + routeId
                + ", effectiveNodes=" + effectiveRouteNodes.size()
                + ", nodesWithCoords=" + locatedNodes.size()
                + ", missingCoords=" + missingCoords);

        int rawPolylineStartIndex = findRouteNodeIndex(route.nodes, preview.routePreviewStartPoint);
        List<LatLng> existingPolylinePoints = getEffectiveRawRoutePolylinePoints(route, preview);
        if (existingPolylinePoints.size() >= 2) {
            preview.calculating = false;
            preview.amapRouteReady = true;
            preview.partialFallback = false;
            preview.message = "已生成高德步行路线";
            preview.polylinePoints.clear();
            preview.polylinePoints.addAll(existingPolylinePoints);
            Log.d(TAG, "[RouteMap] use existing polyline source=backend_route_polyline points="
                    + existingPolylinePoints.size()
                    + ", rawPoints=" + route.rawPolylinePoints.size()
                    + ", startIndex=" + rawPolylineStartIndex);
            updateRouteCard(preview);
            drawRouteOnMap(preview);
            if (routeExpandedOverlay != null) {
                showRouteExpandedPanel(false);
            }
            return;
        } else if (route.rawPolylinePoints != null && route.rawPolylinePoints.size() >= 2) {
            LatLng startLatLng = toLatLng(preview.routePreviewStartPoint);
            Log.d(TAG, "[RouteMap] skip raw polyline reason=start_mismatch"
                    + ", startIndex=" + rawPolylineStartIndex
                    + ", nearestDistanceM=" + formatDistanceMeterForLog(
                    getNearestPolylineDistanceMeters(route.rawPolylinePoints, startLatLng))
                    + ", rawPoints=" + route.rawPolylinePoints.size());
        }

        final int requestSeq = ++routePreviewRequestSeq;

        if (locatedNodes.size() < 2) {
            preview.calculating = false;
            preview.amapRouteReady = false;
            preview.partialFallback = true;
            preview.message = "当前路线缺少景点坐标，暂按推荐顺序模拟导览";
            preview.polylinePoints.clear();
            Log.d(TAG, "[RouteMap] fallback nodes only, skip direct polyline reason=located_nodes_lt_2"
                    + ", routeId=" + routeId
                    + ", effectiveNodes=" + effectiveRouteNodes.size()
                    + ", nodesWithCoords=" + locatedNodes.size()
                    + ", missingCoords=" + missingCoords);
            drawRouteOnMap(preview);
            updateRouteCard(preview);
            return;
        }

        preview.calculating = true;
        preview.amapRouteReady = false;
        preview.partialFallback = false;
        preview.message = "正在生成高德步行路线...";
        preview.polylinePoints.clear();
        updateRouteCard(preview);
        Log.d(TAG, "[RouteMap] fallback nodes only, skip direct polyline reason=waiting_amap");
        drawRouteOnMap(preview);
        Log.d(TAG, "[RouteMap] start build amap walking route routeId=" + routeId);
        Log.d(TAG, "[RouteMap] no backend real polyline, fallback android RouteSearchV2; check backend [AmapWalk] logs for status/info/infocode");

        if (routeAMap == null || routeMapView == null) {
            preview.calculating = false;
            preview.amapRouteReady = false;
            preview.partialFallback = true;
            preview.message = "高德路线暂不可用，已保留节点顺序";
            preview.polylinePoints.clear();
            updateRouteCard(preview);
            drawRouteOnMap(preview);
            return;
        }

        requestWalkingRouteSegments(requestSeq, preview, locatedNodes, routeId);
    }

    private void requestWalkingRouteSegments(int requestSeq, RoutePreviewData preview, List<RouteNode> locatedNodes, String routeId) {
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
            Log.d(TAG, "[RouteMap] segment index=" + segmentIndex
                    + ", from=" + getRouteNodeName(fromNode) + "(" + formatLngLatForAmap(from) + ")"
                    + ", to=" + getRouteNodeName(toNode) + "(" + formatLngLatForAmap(to) + ")");

            if (from == null || to == null) {
                Log.d(TAG, "[RouteMap] segment index=" + segmentIndex
                        + " response status=missing_coords, info=invalid coordinate, paths=0, steps=0");
                Log.d(TAG, "[RouteMap] segment index=" + segmentIndex + " polylinePoints=0");
                finishRouteSegment(state, segmentIndex, buildFallbackSegment(from, to));
                continue;
            }
            Log.d(TAG, "[RouteMap] segment index=" + segmentIndex
                    + " request origin=" + formatLngLatForAmap(from)
                    + " destination=" + formatLngLatForAmap(to));

            final boolean[] completedByCallback = new boolean[]{false};
            if (mainHandler != null) {
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!completedByCallback[0]) {
                            completedByCallback[0] = true;
                            Log.d(TAG, "[RouteMap] segment index=" + segmentIndex
                                    + " response status=timeout, info=amap walking route timeout, paths=0, steps=0");
                            Log.d(TAG, "[RouteMap] segment index=" + segmentIndex + " polylinePoints=0");
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
                            segmentResult = parseWalkRouteResult(result, from, to, segmentIndex);
                        } else {
                            Log.e(TAG, "高德步行路线规划失败 segment=" + segmentIndex + ", errorCode=" + errorCode);
                            Log.d(TAG, "[RouteMap] segment index=" + segmentIndex
                                    + " response status=sdk_error, info=errorCode=" + errorCode
                                    + ", paths=0, steps=0");
                            Log.d(TAG, "[RouteMap] segment index=" + segmentIndex + " polylinePoints=0");
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
                Log.d(TAG, "[RouteMap] segment index=" + segmentIndex
                        + " response status=exception, info=" + safeLogText(e.getMessage())
                        + ", paths=0, steps=0");
                Log.d(TAG, "[RouteMap] segment index=" + segmentIndex + " polylinePoints=0");
                finishRouteSegment(state, segmentIndex, buildFallbackSegment(from, to));
            }
        }
    }

    private RouteSegmentResult parseWalkRouteResult(WalkRouteResultV2 result, LatLng from, LatLng to, int segmentIndex) {
        int pathCount = result == null || result.getPaths() == null ? 0 : result.getPaths().size();
        int stepCount = 0;
        if (result == null || result.getPaths() == null || result.getPaths().size() == 0) {
            Log.d(TAG, "[RouteMap] segment index=" + segmentIndex
                    + " response status=sdk_success, info=no paths, paths=0, steps=0");
            Log.d(TAG, "[RouteMap] segment index=" + segmentIndex + " polylinePoints=0");
            return buildFallbackSegment(from, to);
        }
        Object pathObject = result.getPaths().get(0);
        if (!(pathObject instanceof WalkPath)) {
            Log.d(TAG, "[RouteMap] segment index=" + segmentIndex
                    + " response status=sdk_success, info=path type mismatch, paths=" + pathCount + ", steps=0");
            Log.d(TAG, "[RouteMap] segment index=" + segmentIndex + " polylinePoints=0");
            return buildFallbackSegment(from, to);
        }

        WalkPath path = (WalkPath) pathObject;
        stepCount = path.getSteps() == null ? 0 : path.getSteps().size();
        Log.d(TAG, "[RouteMap] segment index=" + segmentIndex
                + " response status=sdk_success, info=OK, paths=" + pathCount
                + ", steps=" + stepCount);
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
            Log.d(TAG, "[RouteMap] segment index=" + segmentIndex + " polylinePoints=0");
            return buildFallbackSegment(from, to);
        }
        if (segmentResult.distanceMeter <= 0) {
            segmentResult.distanceMeter = calculateDistanceMeters(from, to);
        }
        if (segmentResult.durationSecond <= 0) {
            segmentResult.durationSecond = estimateWalkDurationSecond(segmentResult.distanceMeter);
        }
        segmentResult.fallbackLine = false;
        Log.d(TAG, "[RouteMap] segment index=" + segmentIndex
                + " polylinePoints=" + segmentResult.points.size());
        return segmentResult;
    }

    private RouteSegmentResult buildFallbackSegment(LatLng from, LatLng to) {
        RouteSegmentResult segmentResult = new RouteSegmentResult();
        segmentResult.fallbackLine = true;
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
            Log.d(TAG, "[RouteMap] segment index=" + segmentIndex
                    + ", success=" + (result != null && !result.fallbackLine)
                    + ", points=" + (result == null || result.points == null ? 0 : result.points.size())
                    + ", distanceM=" + (result == null ? 0f : result.distanceMeter)
                    + ", durationSecond=" + (result == null ? 0L : result.durationSecond));
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
            if (!segmentResult.fallbackLine) {
                for (LatLng point : segmentResult.points) {
                    appendLatLngDedup(preview.polylinePoints, point);
                }
            }
        }

        preview.calculating = false;
        preview.amapRouteReady = preview.polylinePoints != null && preview.polylinePoints.size() >= 2;
        preview.partialFallback = hasFallback;
        state.searches.clear();
        if (allFallback || preview.polylinePoints == null || preview.polylinePoints.size() < 2) {
            preview.polylinePoints.clear();
            preview.amapRouteReady = false;
            preview.message = allFallback
                    ? "步行路线暂未生成，已保留节点顺序"
                    : "步行路线暂未生成，已保留节点顺序";
            Log.d(TAG, "[RouteMap] fallback nodes only, skip direct polyline reason="
                    + (allFallback ? "all_amap_fallback" : "partial_amap_no_success_points"));
        } else if (hasFallback) {
            preview.message = "部分路段未获取到高德路线，已显示成功路段";
            Log.d(TAG, "[RouteMap] use partial amap polyline source=amap_segments points="
                    + preview.polylinePoints.size()
                    + ", failedSegments=" + countFallbackSegments(state));
        } else {
            preview.message = "已生成高德步行路线";
            Log.d(TAG, "[RouteMap] use existing polyline source=amap_segments points="
                    + preview.polylinePoints.size());
        }
        Log.d(TAG, "[RouteMap] finalPolylinePoints="
                + (preview.polylinePoints == null ? 0 : preview.polylinePoints.size()));

        updateRouteCard(preview);
        drawRouteOnMap(preview);
        if (routeExpandedOverlay != null) {
            showRouteExpandedPanel(false);
        }
    }

    private int countFallbackSegments(RouteSegmentRequestState state) {
        if (state == null || state.results == null) {
            return 0;
        }
        int count = 0;
        for (RouteSegmentResult segment : state.results.values()) {
            if (segment != null && segment.fallbackLine) {
                count++;
            }
        }
        return count;
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

        List<RouteNode> displayNodes = buildRoutePlanningNodes(preview.routePreviewStartPoint, currentRoute);
        if (displayNodes.size() == 0) {
            displayNodes = preview.nodes;
        }
        List<LatLng> boundsPoints = getRouteDrawablePoints(currentRoute);
        if (getRoutePolylinePoints(currentRoute).size() < 2) {
            boundsPoints.clear();
            for (RouteNode node : displayNodes) {
                LatLng latLng = toLatLng(node);
                if (latLng != null) {
                    appendLatLngDedup(boundsPoints, latLng);
                }
            }
        }
        drawRoutePolyline(currentRoute);
        drawRouteMarkers(displayNodes);

        RouteNode startPoint = preview.routePreviewStartPoint;
        // 只有当“游客当前位置/手动模拟起点”和路线第一个景点不是同一个点时，才额外画一个“我”。
        // 否则地图上会同时出现“我”和路线节点“起”，看起来像两个起点。
        boolean drawSeparateStartMarker = shouldDrawSeparateRouteStartMarker(startPoint, displayNodes);
        if (drawSeparateStartMarker) {
            LatLng latLng = toLatLng(startPoint);
            if (latLng != null) {
                Marker marker = routeAMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title("当前起点：" + getRouteNodeName(startPoint))
                        .icon(createRouteMarkerIcon(routeGuideActive ? "我" : "起", Color.rgb(245, 158, 11), BitmapDescriptorFactory.HUE_YELLOW)));
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
                markerLabel = routeGuideActive ? "我" : "起";
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
                    .title((routeGuideActive ? "当前位置：" : "当前起点：") + getRouteNodeName(currentNode))
                    .icon(createRouteMarkerIcon(routeGuideActive ? "我" : "起", Color.rgb(245, 158, 11), BitmapDescriptorFactory.HUE_YELLOW)));
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
        List<LatLng> points = getRoutePolylinePoints(route);
        if (points.size() < 2) {
            Log.d(TAG, "[RouteMap] fallback nodes only, skip direct polyline reason=no_real_polyline");
            return;
        }
        boolean fallback = currentRoutePreview != null && currentRoutePreview.partialFallback;
        int polylineWidth = dp(8);
        Log.d(TAG, "[RouteMap] finalPolylinePoints=" + points.size());
        Polyline polyline = routeAMap.addPolyline(new PolylineOptions()
                .addAll(points)
                .width(polylineWidth)
                .color(fallback ? Color.rgb(245, 158, 11) : Color.rgb(47, 128, 237)));
        if (polyline != null) {
            routePreviewPolylines.add(polyline);
            Log.d(TAG, "[RouteMap] addPolyline success points=" + points.size());
        } else {
            Log.w(TAG, "[RouteMap] addPolyline returned null points=" + points.size());
        }
        Log.d(TAG, "[RouteMap] polyline object created=" + (polyline != null)
                + ", width=" + polylineWidth
                + ", points=" + points.size());
    }

    private List<LatLng> getRoutePolylinePoints(RouteInfo route) {
        List<LatLng> result = new ArrayList<>();
        List<LatLng> rawPoints = getEffectiveRawRoutePolylinePoints(route, currentRoutePreview);
        if (rawPoints.size() >= 2) {
            result.addAll(rawPoints);
            return result;
        }
        if (currentRoutePreview != null
                && currentRoutePreview.polylinePoints != null
                && currentRoutePreview.polylinePoints.size() >= 2
                && currentRoutePreview.amapRouteReady) {
            result.addAll(currentRoutePreview.polylinePoints);
        }
        return result;
    }

    private boolean shouldUseRawRoutePolyline(RouteInfo route, RoutePreviewData preview) {
        return getEffectiveRawRoutePolylinePoints(route, preview).size() >= 2;
    }

    private List<LatLng> getEffectiveRawRoutePolylinePoints(RouteInfo route, RoutePreviewData preview) {
        List<LatLng> result = new ArrayList<>();
        if (route == null || route.rawPolylinePoints == null || route.rawPolylinePoints.size() < 2) {
            return result;
        }

        RouteNode startPoint = preview == null ? null : preview.routePreviewStartPoint;
        LatLng startLatLng = toLatLng(startPoint);
        int startIndex = findRouteNodeIndex(route.nodes, startPoint);
        if (startLatLng == null || startIndex <= 0) {
            result.addAll(route.rawPolylinePoints);
            return result;
        }

        int nearestIndex = findNearestPolylinePointIndex(route.rawPolylinePoints, startLatLng);
        float nearestDistance = nearestIndex < 0
                ? Float.MAX_VALUE
                : calculateDistanceMeters(startLatLng, route.rawPolylinePoints.get(nearestIndex));
        if (nearestIndex >= 0
                && nearestDistance <= 80f
                && route.rawPolylinePoints.size() - nearestIndex >= 2) {
            for (int i = nearestIndex; i < route.rawPolylinePoints.size(); i++) {
                appendLatLngDedup(result, route.rawPolylinePoints.get(i));
            }
        }
        return result;
    }

    private int findNearestPolylinePointIndex(List<LatLng> points, LatLng target) {
        if (points == null || target == null) {
            return -1;
        }
        int nearestIndex = -1;
        float nearestDistance = Float.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            LatLng point = points.get(i);
            if (point == null) {
                continue;
            }
            float distance = calculateDistanceMeters(target, point);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = i;
            }
        }
        return nearestIndex;
    }

    private float getNearestPolylineDistanceMeters(List<LatLng> points, LatLng target) {
        int index = findNearestPolylinePointIndex(points, target);
        if (index < 0 || points == null || index >= points.size()) {
            return Float.MAX_VALUE;
        }
        return calculateDistanceMeters(target, points.get(index));
    }

    private String formatDistanceMeterForLog(float distanceMeter) {
        if (distanceMeter == Float.MAX_VALUE || Float.isNaN(distanceMeter) || Float.isInfinite(distanceMeter)) {
            return "NA";
        }
        return String.format(Locale.US, "%.1f", distanceMeter);
    }

    private String formatLngLatForAmap(LatLng point) {
        if (point == null) {
            return "-";
        }
        return String.format(Locale.US, "%.6f,%.6f", point.longitude, point.latitude);
    }

    private List<LatLng> getRouteDrawablePoints(RouteInfo route) {
        List<LatLng> result = new ArrayList<>();
        List<LatLng> polylinePoints = getRoutePolylinePoints(route);
        if (polylinePoints.size() >= 2) {
            result.addAll(polylinePoints);
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
            routeCardStartNextText.setText(formatRouteStartNextText(currentRoute));
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
        RouteNode matchedSelectedStart = findRouteNodeByRouteStart(route);
        if (matchedSelectedStart != null && matchedSelectedStart.hasLocation) {
            return copyRouteNode(matchedSelectedStart);
        }
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
        int routeStartIndex = findRouteNodeIndex(route == null ? null : route.nodes, startPoint);
        if (routeStartIndex >= 0 && route != null && route.nodes != null) {
            for (int i = routeStartIndex; i < route.nodes.size(); i++) {
                RouteNode node = route.nodes.get(i);
                if (node != null) {
                    result.add(node);
                }
            }
            return result;
        }

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

    private void alignRouteStartState(RouteInfo route) {
        if (route == null || route.nodes == null || route.nodes.size() == 0) {
            currentRouteNodeIndex = -1;
            logRouteState(route);
            return;
        }

        int matchedIndex = findRouteNodeIndexByRouteStart(route);
        if (matchedIndex < 0) {
            RouteNode previewStart = currentRoutePreview == null ? null : currentRoutePreview.routePreviewStartPoint;
            matchedIndex = findRouteNodeIndex(route.nodes, previewStart);
        }
        if (matchedIndex < 0) {
            matchedIndex = 0;
        }
        currentRouteNodeIndex = matchedIndex;
        routeDemoNodeIndex = matchedIndex;
        currentDemoRouteNode = route.nodes.get(matchedIndex);
        Log.d(TAG, "[RouteStart] matched route node index=" + matchedIndex
                + ", nodeName=" + getRouteNodeName(currentDemoRouteNode));
        logRouteState(route);
    }

    private RouteNode findRouteNodeByRouteStart(RouteInfo route) {
        int index = findRouteNodeIndexByRouteStart(route);
        if (index >= 0 && route != null && route.nodes != null && index < route.nodes.size()) {
            return route.nodes.get(index);
        }
        return null;
    }

    private void logRouteState(RouteInfo route) {
        RouteNode current = getCurrentNavigationNode(route);
        RouteNode next = getNextNavigationNode(route);
        RouteNode first = route == null || route.nodes == null || route.nodes.size() == 0 ? null : route.nodes.get(0);
        Log.d(TAG, "[RouteState] currentRouteNodeIndex=" + currentRouteNodeIndex
                + ", current=" + getRouteNodeName(current)
                + ", next=" + getRouteNodeName(next)
                + ", routeFirst=" + getRouteNodeName(first));
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
            routeCardStartNextText.setText(formatRouteStartNextText(route));
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
                writeRouteCardEvent("ROUTE_REJECT", route, null);
                dismissRouteExpandedPanel();
                hideRouteCard("user_close_expanded_route_card");
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
        if (!shouldShowOnsiteRouteCard(route, route.nodes)) {
            showToast("该路线仅作为游览前建议，现场导览中不进入导航");
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

        buildRoutePreviewWithAmap(route);
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
        if (!shouldShowOnsiteRouteCard(route, route.nodes)) {
            showToast("该路线仅作为游览前建议，不能开始现场导航");
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
        reportRouteUseIfNeeded(route, "start_navigation");
        trackRouteEvent("ROUTE_ACCEPT", route, null);
        trackRouteEvent("ROUTE_START", route, null);
        trackRouteEvent("navigation_start", route, null);
        if (routeAMap == null) {
            requestPendingRouteNavigationRefresh();
        }
        renderRouteCard(route);
        updateRouteDemoController(route);
        Log.d(TAG, "[RouteNav] start navigation currentIndex=" + currentRouteNodeIndex
                + ", firstTime=" + firstTime
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
        logRouteState(route);
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
        if (currentRoute == null || !shouldShowOnsiteRouteCard(currentRoute, currentRoute.nodes)) {
            showToast("该路线不能进入现场节点导览");
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
        logRouteState(currentRoute);
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
            return firstNotEmpty(preview.message, "部分路段未获取到高德路线，已显示成功路段");
        }
        if (preview.amapRouteReady) {
            return "已生成高德步行路线";
        }
        if (preview.partialFallback && preview.polylinePoints != null && preview.polylinePoints.size() >= 2) {
            return firstNotEmpty(preview.message, "部分路段未获取到高德路线，已显示成功路段");
        }
        if (preview.partialFallback && firstNotEmpty(preview.message).length() > 0) {
            return preview.message;
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
        RouteNode current = getCurrentNavigationNode(route);
        RouteNode next = getNextNavigationNode(route);
        String currentName = firstNotEmpty(getRouteNodeName(current), getRoutePreviewStartName(), "当前起点");
        String nextName = next == null ? "暂无下一站" : getRouteNodeName(next);
        return "当前：" + currentName + " · 下一站：" + nextName;
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
            return "暂无路线统计";
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
            return "暂无路线统计";
        }
        return builder.toString();
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
        int startIndex = currentRouteNodeIndex >= 0 && currentRouteNodeIndex < route.nodes.size()
                ? currentRouteNodeIndex
                : 0;
        int maxCount = Math.min(route.nodes.size() - startIndex, 5);
        for (int i = 0; i < maxCount; i++) {
            if (i > 0) {
                builder.append(" → ");
            }
            builder.append(getRouteNodeName(route.nodes.get(startIndex + i)));
        }
        if (route.nodes.size() > startIndex + maxCount) {
            builder.append(" → ...");
        }
        String reason = buildRouteRecommendReason(route);
        if (reason.length() > 0) {
            builder.append("\n依据：").append(limitRouteCardText(reason, 42));
        }
        return builder.toString();
    }

    private String limitRouteCardText(String text, int maxLength) {
        String value = safeString(text).replace("\n", " ").replace("\r", " ").trim();
        if (maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String buildRouteRecommendReason(RouteInfo route) {
        if (route == null) {
            return "";
        }
        String reason = firstNotEmpty(route.recommendReason, route.reason);
        if (reason.length() > 0) {
            return reason;
        }
        if (route.matchedTags == null || route.matchedTags.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (String code : route.matchedTags) {
            String name = mapMatchedTagName(code);
            if (name.length() > 0 && !names.contains(name)) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("根据您偏好");
        for (int i = 0; i < names.size() && i < 4; i++) {
            if (i > 0) {
                builder.append("、");
            }
            builder.append(names.get(i));
        }
        builder.append("，为您推荐这条路线。");
        return builder.toString();
    }

    private String mapMatchedTagName(String code) {
        String key = safeString(code).trim();
        if (key.length() == 0) return "";
        if ("history_culture".equals(key)) return "历史文化";
        if ("nature".equals(key)) return "自然风光";
        if ("photo".equals(key)) return "拍照";
        if ("deep_explanation".equals(key)) return "深度讲解";
        if ("avoid_crowd".equals(key)) return "避开拥挤";
        if ("fewer_steps".equals(key)) return "少走台阶";
        if ("many_steps".equals(key)) return "台阶较多";
        if ("low_intensity".equals(key)) return "低强度";
        if ("elder_friendly".equals(key)) return "老人友好";
        if ("family_friendly".equals(key)) return "亲子友好";
        if ("accessibility".equals(key)) return "无障碍";
        if (key.matches("[A-Za-z0-9_\\-]+")) return "";
        return key;
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
        resetRouteDemoState("legacy_call");
    }

    private void resetRouteDemoState(String reason) {
        Log.d(TAG, "[RouteNav] clear route state reason=" + safeString(reason));
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
        if (!shouldShowOnsiteRouteCard(currentRoute, currentRoute.nodes)) {
            showToast("该路线仅作为游览前建议，不能模拟现场路线");
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

        int startIndex = findRouteNodeIndex(activeRouteNodes, startPoint);
        if (startIndex < 0) {
            startIndex = currentRouteNodeIndex >= 0 && currentRouteNodeIndex < activeRouteNodes.size()
                    ? currentRouteNodeIndex
                    : 0;
        }

        routeGuideActive = true;
        currentRouteNodeIndex = startIndex;
        routeDemoNodeIndex = startIndex;
        currentDemoRouteNode = activeRouteNodes.get(startIndex);
        applyDemoNodeToCurrentContext(currentDemoRouteNode);
        logRouteState(currentRoute);
        dismissRouteExpandedPanel();
        renderRouteCard(currentRoute);
        reportRouteUseIfNeeded(currentRoute, "start_node_guide");

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
        if (currentRoute == null || !shouldShowOnsiteRouteCard(currentRoute, currentRoute.nodes)) {
            showToast("该路线不能模拟到达下一站");
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
        Log.d(TAG, "[RouteNav] simulate next from=" + getRouteNodeName(previousNode)
                + " to=" + getRouteNodeName(nextNode));

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
                            if (routeCardContainer != null && !routeMapModeActive) {
                                routeCardContainer.setVisibility(View.VISIBLE);
                            }
                            refreshRouteMapForRouteState();
                            speakRouteGuideText("已到达" + getRouteNodeName(nextNode) + "，下面为你讲解这里的特色。");
                            if (!previousLeaveOk) {
                                showToast("已到达新景点，但上一景点离开记录写入失败");
                            }
                            Log.d(TAG, "[RouteNav] keep route card after spot explain");
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
            hideRouteCard("user_end_route_without_active_navigation");
            return;
        }
        exitNavigationMode();
        Log.d(TAG, "[RouteNav] clear route state reason=user_end_route_navigation");
        routeGuideActive = false;
        routeNavigationModeActive = false;
        routeDemoRequesting = false;
        currentRouteNodeIndex = -1;
        routeDemoNodeIndex = currentRoute == null || currentRoute.nodes == null ? -1 : currentRoute.nodes.size();
        currentDemoRouteNode = null;
        activeRouteNodes.clear();
        hideRouteCard("user_end_route_navigation");
        if (completed) {
            trackRouteEvent("ROUTE_COMPLETE", currentRoute, null);
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
        askGuideInternal("请讲解一下【" + nodeName + "】", true, true, "route_navigation_step");
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
        if (!shouldShowOnsiteRouteCard(currentRoute, currentRoute.nodes)) {
            showToast("该路线不能模拟到达下一站");
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
        latitude = firstNotEmpty(node.latitude, latitude);
        longitude = firstNotEmpty(node.longitude, longitude);
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
                + ", routeId=" + resolveRouteEventRouteId(route)
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
                    String routeId = resolveRouteEventRouteId(route);
                    requestJson.put("routeId", routeId);
                    requestJson.put("route_id", routeId);
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
                    extra.put("routeId", routeId);
                    extra.put("route_id", routeId);
                    extra.put("profileVersion", route == null ? "" : safeString(route.profileVersion));
                    extra.put("profile_version", route == null ? "" : safeString(route.profileVersion));
                    extra.put("recommendReason", route == null ? "" : buildRouteRecommendReason(route));
                    extra.put("recommend_reason", route == null ? "" : buildRouteRecommendReason(route));
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
        // 只有现场导览页才允许 current_spot 作为路线起点
        // 非现场页面（景区列表 AI 讲解等）即使有 spotName/scenicName 也不走 current_spot
        if (isRealOnsiteRouteContext()) {
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
        } else {
            // 非现场入口：强制 park_entrance，不把景区名称当 current_spot
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
        Log.d(TAG, "[RouteStart] selected start spotId=" + routeStartCurrentSpotId
                + ", spotName=" + routeStartCurrentSpotName
                + ", lng=" + routeStartLongitude
                + ", lat=" + routeStartLatitude);
        showToast("路线起点已设为：" + routeStartCurrentSpotName);
    }

    private void refreshRouteCardForRouteStartChanged() {
        if (currentRoute != null && routeCardContainer != null && routeCardContainer.getVisibility() == View.VISIBLE) {
            resetRouteDemoState("route_start_changed");
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
                + ", routeId=" + resolveRouteEventRouteId(route)
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

    private void reportRouteUseIfNeeded(RouteInfo route, String trigger) {
        String routeId = getProvidedRouteId(route);
        boolean canReport = routeId.length() > 0
                && isDynamicInParkRoute(route)
                && isInParkRouteContext(route)
                && ("start_navigation".equals(safeString(trigger))
                    || "start_node_guide".equals(safeString(trigger)));
        if (!canReport) {
            Log.d(TAG, "[RouteUse] skip routeId=" + routeId
                    + ", routeMode=" + (route == null ? "" : route.routeMode)
                    + ", visitStatus=" + (route == null ? "" : route.visitStatus)
                    + ", trigger=" + safeString(trigger));
            return;
        }
        if (routeId.equals(lastReportedRouteUseId)) {
            Log.d(TAG, "[RouteUse] skip duplicate routeId=" + routeId);
            return;
        }
        if (!hasRouteEventWriteContext()) {
            Log.d(TAG, "[RouteUse] skip missing context routeId=" + routeId);
            return;
        }
        lastReportedRouteUseId = routeId;
        Log.d(TAG, "[RouteUse] report start routeId=" + routeId + ", trigger=" + safeString(trigger));
        trackRouteEvent("ROUTE_USE", route, null);
    }

    private String getProvidedRouteId(RouteInfo route) {
        if (route == null) {
            return "";
        }
        return firstNotEmpty(route.originalRouteId, route.localRouteIdGenerated ? "" : route.routeId);
    }

    private String getRoutePlanEventKey(RouteInfo route) {
        if (route == null) {
            return "";
        }
        String routeId = resolveRouteEventRouteId(route);
        if (routeId.length() > 0) {
            return routeId;
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

    private String resolveRouteEventRouteId(RouteInfo route) {
        if (route == null) {
            return "";
        }
        ensureRouteId(route);
        return firstNotEmpty(route.routeId, route.planId);
    }

    private void postRouteCardEvent(final String eventName, final RouteInfo route, final RouteNode node, final String spotId, final String spotName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    boolean routeUseEvent = "ROUTE_USE".equalsIgnoreCase(safeString(eventName));
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
                    String routeId = resolveRouteEventRouteId(route);
                    requestJson.put("routeId", routeId);
                    requestJson.put("route_id", routeId);
                    requestJson.put("entityType", route == null ? "ROUTE" : (node == null ? "ROUTE" : "SPOT"));
                    requestJson.put("entity_type", route == null ? "ROUTE" : (node == null ? "ROUTE" : "SPOT"));
                    requestJson.put("entityId", node == null ? routeId : firstNotEmpty(spotId, node == null ? "" : getRouteNodeScenicId(node)));
                    requestJson.put("entity_id", node == null ? routeId : firstNotEmpty(spotId, node == null ? "" : getRouteNodeScenicId(node)));
                    requestJson.put("objectType", route == null ? "ROUTE" : (node == null ? "ROUTE" : "SPOT"));
                    requestJson.put("object_type", route == null ? "ROUTE" : (node == null ? "ROUTE" : "SPOT"));
                    requestJson.put("objectId", node == null ? routeId : firstNotEmpty(spotId, node == null ? "" : getRouteNodeScenicId(node)));
                    requestJson.put("object_id", node == null ? routeId : firstNotEmpty(spotId, node == null ? "" : getRouteNodeScenicId(node)));
                    requestJson.put("routeName", route == null ? "" : safeString(route.routeName));
                    requestJson.put("route_name", route == null ? "" : safeString(route.routeName));
                    requestJson.put("source", ROUTE_EVENT_SOURCE);
                    requestJson.put("sourcePage", ROUTE_EVENT_SOURCE);
                    requestJson.put("source_page", ROUTE_EVENT_SOURCE);

                    if (node != null) {
                        putCoordinateIfPresent(requestJson, "latitude", node.latitude);
                        putCoordinateIfPresent(requestJson, "longitude", node.longitude);
                    }

                    JSONObject extra = new JSONObject();
                    extra.put("routeId", routeId);
                    extra.put("route_id", routeId);
                    extra.put("routeName", route == null ? "" : safeString(route.routeName));
                    extra.put("route_name", route == null ? "" : safeString(route.routeName));
                    extra.put("profileVersion", route == null ? "" : safeString(route.profileVersion));
                    extra.put("profile_version", route == null ? "" : safeString(route.profileVersion));
                    extra.put("recommendReason", route == null ? "" : buildRouteRecommendReason(route));
                    extra.put("recommend_reason", route == null ? "" : buildRouteRecommendReason(route));
                    if (route != null && route.matchedTags != null && !route.matchedTags.isEmpty()) {
                        JSONArray tags = new JSONArray();
                        for (String tag : route.matchedTags) {
                            tags.put(tag);
                        }
                        extra.put("matchedTags", tags);
                        extra.put("matched_tags", tags);
                    }
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
                        if (routeUseEvent) {
                            Log.d(TAG, "[RouteUse] reported routeId=" + routeId);
                        } else {
                            Log.d(TAG, "[RouteCardEvent] 上报成功 event=" + eventName + ", response=" + responseText);
                        }
                    } else {
                        if (routeUseEvent) {
                            Log.e(TAG, "[RouteUse] report failed routeId=" + routeId
                                    + ", code=" + responseCode
                                    + ", response=" + responseText);
                        } else {
                            Log.e(TAG, "[RouteCardEvent] 上报失败 event=" + eventName
                                    + ", code=" + responseCode
                                    + ", url=" + eventUrl
                                    + ", response=" + responseText);
                        }
                    }
                } catch (Exception e) {
                    if ("ROUTE_USE".equalsIgnoreCase(safeString(eventName))) {
                        Log.e(TAG, "[RouteUse] report failed routeId=" + getProvidedRouteId(route), e);
                    } else {
                        Log.e(TAG, "[RouteCardEvent] 上报异常 event=" + eventName
                                + ", url=" + buildBehaviorEventUrl(), e);
                    }
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

    private String sanitizeGuideChatRequestBody(JSONObject requestJson) {
        if (requestJson == null) {
            return "";
        }
        try {
            Object sanitized = sanitizeGuideChatLogValue(requestJson, "");
            return sanitized == null ? "" : sanitized.toString();
        } catch (Exception e) {
            Log.w(TAG, "[GuideChat] request body sanitize failed: " + e.getMessage());
            return requestJson.toString();
        }
    }

    private Object sanitizeGuideChatLogValue(Object value, String key) throws Exception {
        if (isSensitiveGuideChatLogKey(key)) {
            return "***";
        }
        if (value instanceof JSONObject) {
            JSONObject source = (JSONObject) value;
            JSONObject copy = new JSONObject();
            JSONArray names = source.names();
            if (names == null) {
                return copy;
            }
            for (int i = 0; i < names.length(); i++) {
                String childKey = names.optString(i, "");
                if (childKey.length() == 0) {
                    continue;
                }
                copy.put(childKey, sanitizeGuideChatLogValue(source.opt(childKey), childKey));
            }
            return copy;
        }
        if (value instanceof JSONArray) {
            JSONArray source = (JSONArray) value;
            JSONArray copy = new JSONArray();
            for (int i = 0; i < source.length(); i++) {
                copy.put(sanitizeGuideChatLogValue(source.opt(i), key));
            }
            return copy;
        }
        return value;
    }

    private boolean isSensitiveGuideChatLogKey(String key) {
        String text = safeString(key).toLowerCase(Locale.ROOT);
        return text.contains("token")
                || text.contains("authorization")
                || text.contains("authsession");
    }

    private boolean shouldUseNfcGuideFallback(String question) {
        return nfcLocationActive
                && "给我讲讲这里".equals(safeString(question).trim())
                && nfcFallbackGuideSummary != null
                && nfcFallbackGuideSummary.trim().length() > 0;
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

        estimatedDuration = firstNotEmpty(
                getSafeExtra(intent, "estimatedDuration"),
                getSafeExtra(intent, "estimated_duration"),
                getSafeExtra(intent, "travelDuration"),
                getSafeExtra(intent, "travel_duration")
        );
        availableMinutes = firstNotEmpty(
                normalizeAvailableMinutesText(getSafeExtra(intent, "availableMinutes")),
                normalizeAvailableMinutesText(getSafeExtra(intent, "available_minutes")),
                normalizeAvailableMinutesText(estimatedDuration)
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
        visitStatus = firstNotEmpty(
                getSafeExtra(intent, "visit_status"),
                getSafeExtra(intent, "visitStatus")
        );
        isInsideArea = firstNotEmpty(
                getSafeExtra(intent, "is_inside_area"),
                getSafeExtra(intent, "isInsideArea")
        );
        locationContext = firstNotEmpty(
                getSafeExtra(intent, "location_context"),
                getSafeExtra(intent, "locationContext")
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
        aiBaseUrl = firstNotEmpty(
                getSafeExtra(intent, "aiBaseUrl"),
                getSafeExtra(intent, "ai_base_url"),
                getSafeExtra(intent, "aiApiBaseUrl"),
                getSafeExtra(intent, "ai_api_base_url"),
                getDigitalHumanConfigText(digitalHumanConfigJson, "aiBaseUrl", "ai_base_url")
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

        // ==================== NFC / 离线导览开关 ====================
        String nfcFlag = firstNotEmpty(
                getSafeExtra(intent, "nfcOfflineGuideEnabled"),
                getSafeExtra(intent, "nfc_offline_guide_enabled")
        );
        if (nfcFlag.length() > 0) {
            nfcOfflineGuideEnabled = parseBooleanLike(nfcFlag);
        } else {
            nfcOfflineGuideEnabled = true; // 默认开启
        }
        Log.d(TAG, "nfcOfflineGuideEnabled = " + nfcOfflineGuideEnabled);

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
        Log.d(TAG, "estimatedDuration=" + estimatedDuration);
        Log.d(TAG, "availableMinutes=" + availableMinutes);
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
        Log.d(TAG, "visitStatus = " + visitStatus);
        Log.d(TAG, "isInsideArea = " + isInsideArea);
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

    private void applyAuthorizationHeader(Request.Builder builder) {
        if (builder == null) {
            return;
        }

        String authorization = buildAuthorizationHeader();
        if (authorization.length() > 0) {
            builder.header("Authorization", authorization);
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

    private String normalizeVisitStatusForContract(String status) {
        String text = safeString(status).trim();
        if (text.length() == 0) {
            return VISIT_STATUS_NOT_ARRIVED;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        if (VISIT_STATUS_IN_PARK.equals(upper)
                || "IN_AREA".equals(upper)
                || "ARRIVED".equals(upper)
                || "VISITING".equals(upper)
                || "ONSITE".equals(upper)) {
            return VISIT_STATUS_IN_PARK;
        }
        if (VISIT_STATUS_NOT_ARRIVED.equals(upper)
                || "NOT_IN_AREA".equals(upper)
                || "OUT_OF_PARK".equals(upper)
                || "OUTSIDE".equals(upper)) {
            return VISIT_STATUS_NOT_ARRIVED;
        }
        return upper;
    }

    private boolean isInParkVisitStatus(String status) {
        return VISIT_STATUS_IN_PARK.equals(normalizeVisitStatusForContract(status));
    }

    /**
     * 真正的现场路线上下文。
     * 景区列表 AI 讲解 / 景区详情 AI 讲解 / 景点详情 AI 讲解等
     * 虽然可能有 scenicName/areaName/spotName，但不是现场导览页。
     * 只根据 isOnsiteGuide（由前端 is_onsite_guide 明确传入）+ visitId 判断，
     * 不根据 areaName/scenicName/currentSpotName 推断。
     */
    private boolean isRealOnsiteRouteContext() {
        return isOnsiteGuide
            && visitId != null && visitId.trim().length() > 0;
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
        String audioStatus = firstNotEmpty(guideResponse.audioStatus, guideResponse.ttsStatus);
        Log.d(TAG, "[AudioPlay] final response audioStatus=" + safeString(audioStatus)
                + ", audioUrl=" + safeString(audioUrl)
                + ", ttsTaskId=" + safeString(guideResponse.ttsTaskId)
                + ", ttsError=" + safeString(guideResponse.ttsError)
                + ", mouthStatus=" + safeString(guideResponse.mouthStatus)
                + ", mouthError=" + safeString(guideResponse.mouthError)
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
                        stopBackendAudioSpeaking();
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
                    startBackendAudioSpeaking(guideResponse);
                    forceRenderLive2D();
                }
            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Log.d(TAG, "[AudioPlay] onCompletion");
                    stopBackendAudioSpeaking();
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
                    stopBackendAudioSpeaking();
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
            stopBackendAudioSpeaking();
            stopMouthSync();
            returnDigitalHumanToIdle();
            stopCurrentAudio();
            applyGuideResponseDigitalHuman(guideResponse, "explain", "warm");
            Log.d(TAG, "[TTS] fallback speak");
            speakText(guideResponse == null ? "" : guideResponse.answer);
        }
    }

    private void startBackendAudioSpeaking(GuideResponse guideResponse) {
        backendAudioSpeaking = true;
        backendAudioStartMs = android.os.SystemClock.uptimeMillis();
        lastSpeakingMotionMs = 0L;
        Log.d(TAG, "[SpeakingMotion] start audio speaking");

        String action = guideResponse == null ? "" : firstNotEmpty(guideResponse.action, guideResponse.actionCode);
        String emotion = guideResponse == null ? "" : firstNotEmpty(guideResponse.emotion, guideResponse.emotionCode);
        triggerSpeakingMotion(firstNotEmpty(action, "explain"), firstNotEmpty(emotion, "warm"));
        scheduleSpeakingMotionTick();
    }

    private void stopBackendAudioSpeaking() {
        if (mainHandler != null) {
            mainHandler.removeCallbacks(speakingMotionRunnable);
        }
        if (backendAudioSpeaking) {
            Log.d(TAG, "[SpeakingMotion] stop audio speaking");
        }
        backendAudioSpeaking = false;
        backendAudioStartMs = 0L;
        lastSpeakingMotionMs = 0L;
    }

    private void scheduleSpeakingMotionTick() {
        if (!backendAudioSpeaking || mainHandler == null) {
            return;
        }
        mainHandler.removeCallbacks(speakingMotionRunnable);
        mainHandler.postDelayed(speakingMotionRunnable, SPEAKING_MOTION_INTERVAL_MS);
    }

    private void handleSpeakingMotionTick() {
        if (!backendAudioSpeaking) {
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        if (lastSpeakingMotionMs <= 0L || now - lastSpeakingMotionMs >= SPEAKING_MOTION_INTERVAL_MS) {
            triggerSpeakingMotion("explain", "warm");
        }
        scheduleSpeakingMotionTick();
    }

    private void triggerSpeakingMotion(String action, String emotion) {
        if (!backendAudioSpeaking) {
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        long elapsedMs = backendAudioStartMs > 0L ? Math.max(0L, now - backendAudioStartMs) : 0L;
        boolean started = false;
        try {
            started = DigitalHumanActionController.getInstance().triggerMotionOnly(action, emotion);
        } catch (Throwable e) {
            Log.e(TAG, "[SpeakingMotion] trigger failed action=" + safeString(action), e);
        }
        if (started) {
            lastSpeakingMotionMs = now;
            Log.d(TAG, "[SpeakingMotion] trigger action=" + firstNotEmpty(action, "explain")
                    + ", elapsedMs=" + elapsedMs);
            forceRenderLive2D();
        } else {
            Log.d(TAG, "[SpeakingMotion] skip reason=motion_busy");
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
        String url = safeString(audioUrl).trim();
        if (url.length() == 0) {
            return "";
        }
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return url;
            }

            String aiBase = trimTrailingSlash(aiBaseUrl);
            if (aiBase.length() == 0) {
                Log.w(TAG, "[SSE][Audio] AI_BASE_URL missing, skip relative audioUrl=" + safeString(url));
                return "";
            }

            URL aiUrl = new URL(aiBase);
            String aiOrigin = aiUrl.getProtocol() + "://" + aiUrl.getHost();
            if (aiUrl.getPort() > 0) {
                aiOrigin += ":" + aiUrl.getPort();
            }
            if (url.startsWith("//")) {
                return aiUrl.getProtocol() + ":" + url;
            }
            if (url.startsWith("/")) {
                return aiOrigin + url;
            }
            return aiOrigin + "/" + url;
        } catch (Exception e) {
            Log.w(TAG, "[SSE][Audio] resolve audioUrl failed, skip audioUrl=" + safeString(url)
                    + ", aiBaseUrl=" + safeString(aiBaseUrl)
                    + ", message=" + e.getMessage());
            return "";
        }
    }

    private String trimTrailingSlash(String value) {
        String text = safeString(value).trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private void releaseMediaPlayerOnly() {
        try {
            if (mediaPlayer != null) {
                Log.d(TAG, "[SSE][Audio] release MediaPlayer isPlaying=" + mediaPlayer.isPlaying());
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "[SSE][Audio] release MediaPlayer failed: " + e.getMessage());
            mediaPlayer = null;
        }
    }

    private void stopCurrentAudio() {
        stopBackendAudioSpeaking();
        stopMouthSync();
        currentTtsUtteranceId = "";
        releaseMediaPlayerOnly();
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

        Log.d(TAG, "[NFC] onNewIntent action=" + (intent != null ? intent.getAction() : "null"));

        // ==================== NFC 标签处理 ====================
        if (tryHandleNfcIntent(intent)) {
            // NFC tag was processed — don't re-run full guide intent logic
            return;
        }

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
        Log.d(TAG, "[NFC] onResume, nfcInitialized=" + nfcInitialized
                + ", nfcOfflineGuideEnabled=" + nfcOfflineGuideEnabled);
        if (routeMapView != null) {
            routeMapView.onResume();
        }
        forceRenderLive2D();
        hideSystemBars();
        enableNfcReaderMode();
        trySyncPendingBehaviors();
        trySyncPendingOfflineNfcEvents();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (routeMapView != null) {
            routeMapView.onPause();
        }
        disableNfcReaderMode();
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
        Log.d(TAG, "[SSE][Lifecycle] onStop " + getSseLifecycleState());
        if (voiceFlowActive || recording) {
            Log.d(TAG, "语音流程中，跳过 Live2D onStop，避免黑屏");
            return;
        }
        LAppDelegate.getInstance().onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "[SSE][Lifecycle] onDestroy " + getSseLifecycleState());
        releaseRecorder();
        cancelCurrentSseRound("destroy");
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
        boolean success = true;
        int httpStatus = 200;
        int businessCode = -1;
        String businessMsg = "";
        String questionText = "";
        String answer = "";
        String audioUrl = "";
        String audioStatus = "";
        String ttsStatus = "";
        String ttsTaskId = "";
        long audioDurationMs = 0L;
        String conversationId = "";
        String messageId = "";
        String ttsError = "";
        String mouthStatus = "";
        String mouthError = "";
        String interactionCategory = "";
        String answerStatus = "";
        String fallbackReason = "";
        String issueCategory = "";
        String issueType = "";
        String knowledgeGapCandidate = "";
        boolean requiresAdminAction = false;
        String grounding = "";
        String sources = "";
        String action = "";
        String actionCode = "";
        String emotion = "";
        String emotionCode = "";
        boolean routeIntent = false;
        RouteInfo route;
        List<String> suggestions = new ArrayList<>();
        List<MouthSyncManager.MouthFrame> mouthFrames = new ArrayList<>();
        List<MouthSyncController.MouthFrame> controllerMouthFrames = new ArrayList<>();
    }

    private static class SpeechChunk {
        String messageId = "";
        String chunkId = "";
        int roundSeq = 0;
        int chunkIndex = 0;
        String text = "";
        String audioUrl = "";
        long durationMs = 0L;
        boolean isLast = false;
        String action = "";
        String emotion = "";
        List<MouthSyncManager.MouthFrame> mouthFrames = new ArrayList<MouthSyncManager.MouthFrame>();
        List<MouthSyncController.MouthFrame> controllerMouthFrames = new ArrayList<MouthSyncController.MouthFrame>();
    }

    private static class VisitEndResult {
        boolean success = false;
        String visitId = "";
        String reportVisitId = "";
    }

    private static class RouteInfo {
        String schemaVersion = "";
        String planId = "";
        String routeId = "";
        String originalRouteId = "";
        boolean localRouteIdGenerated = false;
        String routeName = "";
        String reason = "";
        String recommendReason = "";
        String profileVersion = "";
        String totalDistanceM = "";
        String estimatedDurationMin = "";
        String mapAction = "";
        String routeMapReady = "";
        String routeMode = "";
        String visitStatus = "";
        String algorithmVersion = "";
        boolean routeIntent = false;
        boolean hasShouldShowRouteCard = false;
        boolean shouldShowRouteCard = false;
        boolean isOfficialTemplate = false;
        RoutePreviewData preview;
        List<String> matchedTags = new ArrayList<>();
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
        String facilityId = "";
        String sceneCode = "";
        String nodeType = "";
        String spotName = "";
        String scenicName = "";
        String displayName = "";
        String guideText = "";
        String recommendedStayMin = "";
        String distanceFromPreviousMeters = "";
        String estimatedWalkMinutes = "";
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

    // ==================== NFC / Offline Guide 方法 ====================

    private void tryUpdateOfflinePackageIfNeeded() {
        if (!nfcOfflineGuideEnabled) {
            return;
        }

        final String apiBaseUrl = firstNotEmpty(
                behaviorBackendBaseUrl,
                getBaseUrlFromFullUrl(GUIDE_CHAT_URL)
        );
        if (apiBaseUrl.length() == 0) {
            return;
        }

        final String offlineAreaId = resolveOfflinePackageAreaIdText();
        if (offlineAreaId.length() == 0) {
            return;
        }

        NetworkLevel networkLevel = networkStateHelper != null
                ? networkStateHelper.getNetworkLevel()
                : NetworkLevel.NORMAL;
        if (networkLevel == NetworkLevel.OFFLINE) {
            return;
        }

        if (isOfflinePackageCheckThrottled(offlineAreaId)) {
            Log.d(TAG, "[OfflinePackage] check latest throttled areaId=" + offlineAreaId);
            return;
        }
        markOfflinePackageCheck(offlineAreaId);

        Log.d(TAG, "[OfflinePackage] check latest areaId=" + offlineAreaId);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OfflinePackageClient client = new OfflinePackageClient(
                            MainActivity.this,
                            apiBaseUrl,
                            authToken
                    );
                    OfflinePackageInfo latest = client.fetchLatest(offlineAreaId);
                    if (latest == null || latest.packageUrl == null
                            || latest.packageUrl.trim().length() == 0) {
                        return;
                    }

                    String localVersion = client.getLocalPackageVersion(offlineAreaId);
                    Log.d(TAG, "[OfflinePackage] server version=" + latest.packageVersion
                            + ", local version=" + localVersion);

                    boolean downloaded = client.downloadManifest(latest, offlineAreaId);
                    if (downloaded && offlinePackageManager != null) {
                        int areaIdInt = parsePositiveInt(offlineAreaId, 0);
                        if (areaIdInt > 0) {
                            offlinePackageManager.reloadPackage(areaIdInt);
                        }
                    } else if (!downloaded) {
                        showToast("离线包更新失败，已继续使用本地缓存。");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "[OfflinePackage] update failed: " + e.getMessage(), e);
                    showToast("离线包更新失败，已继续使用本地缓存。");
                }
            }
        }).start();
    }

    private boolean isOfflinePackageCheckThrottled(String areaIdText) {
        try {
            SharedPreferences prefs = getSharedPreferences(OFFLINE_PACKAGE_PREFS, MODE_PRIVATE);
            String lastAreaId = prefs.getString(PREF_LAST_OFFLINE_PACKAGE_CHECK_AREA_ID, "");
            long lastAt = prefs.getLong(PREF_LAST_OFFLINE_PACKAGE_CHECK_AT, 0L);
            long now = System.currentTimeMillis();
            return areaIdText.equals(lastAreaId)
                    && lastAt > 0L
                    && now - lastAt >= 0L
                    && now - lastAt < OFFLINE_PACKAGE_CHECK_INTERVAL_MS;
        } catch (Exception e) {
            return false;
        }
    }

    private void markOfflinePackageCheck(String areaIdText) {
        try {
            SharedPreferences prefs = getSharedPreferences(OFFLINE_PACKAGE_PREFS, MODE_PRIVATE);
            prefs.edit()
                    .putString(PREF_LAST_OFFLINE_PACKAGE_CHECK_AREA_ID, areaIdText)
                    .putLong(PREF_LAST_OFFLINE_PACKAGE_CHECK_AT, System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {}
    }

    private String resolveOfflinePackageAreaIdText() {
        String value = firstNotEmpty(areaId, guideContext.scenicId, scenicId, parkId, guideContext.parkId, areaCode);
        int parsed = parsePositiveInt(value, 0);
        return parsed > 0 ? String.valueOf(parsed) : "";
    }

    private int parsePositiveInt(String value, int fallback) {
        if (value == null || value.trim().length() == 0) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * 初始化 NFC 离线导览能力。
     * 如果初始化失败，自动降级为 no-op，不影响原有数字人页面。
     */
    private void initNfcOfflineGuide() {
        Log.d(TAG, "[NFC] initNfc start, nfcOfflineGuideEnabled=" + nfcOfflineGuideEnabled);

        if (!nfcOfflineGuideEnabled) {
            Log.d(TAG, "[NFC] nfcOfflineGuideEnabled=false, skip init");
            return;
        }

        try {
            // 初始化离线包管理器
            offlinePackageManager = new OfflineGuidePackageManager(this);
            Log.d(TAG, "[NFC] OfflineGuidePackageManager initialized, hasPackage="
                    + offlinePackageManager.hasAnyPackage());

            // 初始化行为队列
            offlineBehaviorQueue = new OfflineBehaviorQueue(this);
            offlineNfcEventQueue = new OfflineNfcEventQueue(this);

            // 初始化网络状态检测
            networkStateHelper = new NetworkStateHelper(this);
            trySyncPendingOfflineNfcEvents();

            // 初始化 NFC 适配器
            nfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if (nfcAdapter == null) {
                Log.d(TAG, "[NFC] Device does not support NFC, disabling NFC features");
                nfcInitialized = false;
                return;
            }

            // 创建 NFC PendingIntent（用于前台分发）
            // Android 12+ 必须使用 FLAG_MUTABLE，否则 NFC Intent 数据无法传递
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int flags;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
            } else {
                flags = PendingIntent.FLAG_UPDATE_CURRENT;
            }
            nfcPendingIntent = PendingIntent.getActivity(this, 0, intent, flags);
            Log.d(TAG, "[NFC] PendingIntent created: flags=" + flags
                    + ", FLAG_ACTIVITY_SINGLE_TOP set, SDK=" + Build.VERSION.SDK_INT);

            // NFC Intent 过滤器（匹配所有 NDEF 标签）
            IntentFilter ndefFilter = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
            try {
                ndefFilter.addDataType("*/*");
            } catch (IntentFilter.MalformedMimeTypeException e) {
                Log.w(TAG, "[NFC] Failed to add MIME type filter", e);
            }
            nfcIntentFilters = new IntentFilter[]{ndefFilter};

            // 技术列表（匹配所有 NFC 技术）
            nfcTechLists = new String[][]{new String[]{
                    android.nfc.tech.Ndef.class.getName(),
                    android.nfc.tech.NdefFormatable.class.getName()
            }};

            nfcInitialized = true;
            Log.d(TAG, "[NFC] initNfc done: adapter="
                    + (nfcAdapter != null ? "present" : "null")
                    + ", enabled=" + (nfcAdapter != null && nfcAdapter.isEnabled())
                    + ", initialized=" + nfcInitialized);
        } catch (Exception e) {
            Log.w(TAG, "[NFC] initNfcOfflineGuide failed, degrading to no-op", e);
            nfcOfflineGuideEnabled = false;
            nfcInitialized = false;
        }
    }

    /**
     * 在 onResume 中启用 NFC ReaderMode（主方案）。
     * ReaderMode 直接回调 tag，不走 Intent/PendingIntent，不触发 BAL_BLOCK。
     */
    private void enableNfcReaderMode() {
        if (!nfcInitialized || nfcAdapter == null) return;
        if (!nfcAdapter.isEnabled()) {
            Log.d(TAG, "[NFC] NFC adapter disabled, skip reader mode");
            return;
        }
        if (nfcReaderModeActive) {
            Log.d(TAG, "[NFC] ReaderMode already active, skip");
            return;
        }
        try {
            Log.d(TAG, "[NFC] onResume, enableReaderMode start");
            final int readerFlags = NfcAdapter.FLAG_READER_NFC_A
                    | NfcAdapter.FLAG_READER_NFC_B
                    | NfcAdapter.FLAG_READER_NFC_F
                    | NfcAdapter.FLAG_READER_NFC_V;
            nfcAdapter.enableReaderMode(
                    MainActivity.this,
                    new NfcAdapter.ReaderCallback() {
                        @Override
                        public void onTagDiscovered(final Tag tag) {
                            Log.d(TAG, "[NFC] readerMode tag discovered: " + tag);
                            try {
                                final String markerCode = NfcMarkerReader.extractMarkerCodeFromTag(tag);
                                Log.d(TAG, "[NFC] readerMode extracted marker_code: "
                                        + (markerCode != null && !markerCode.isEmpty() ? markerCode : "(empty)"));
                                if (markerCode != null && !markerCode.isEmpty()) {
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            handleNfcMarkerCodeDeduped(markerCode);
                                        }
                                    });
                                } else {
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            showToast("已读取到 NFC 标签，但无法识别点位编码。");
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "[NFC] readerMode tag handling failed", e);
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        showToast("已读取到 NFC 标签，但无法识别点位编码。");
                                    }
                                });
                            }
                        }
                    },
                    readerFlags,
                    null
            );
            nfcReaderModeActive = true;
            Log.d(TAG, "[NFC] enableReaderMode success");
        } catch (Exception e) {
            Log.w(TAG, "[NFC] Failed to enable reader mode, falling back to foreground dispatch", e);
            nfcReaderModeActive = false;
            enableNfcForegroundDispatchFallback();
        }
    }

    /**
     * 在 onPause 中禁用 NFC ReaderMode。
     */
    private void disableNfcReaderMode() {
        if (!nfcInitialized || nfcAdapter == null) return;
        try {
            if (nfcReaderModeActive) {
                nfcAdapter.disableReaderMode(this);
                nfcReaderModeActive = false;
                Log.d(TAG, "[NFC] disableReaderMode success");
            }
        } catch (Exception e) {
            Log.w(TAG, "[NFC] Failed to disable reader mode", e);
        }
    }

    /**
     * ForegroundDispatch 降级方案（ReaderMode 不可用时）。
     * 某些设备 MIUI/HyperOS 可能拦截 ReaderMode，此时退回原方案。
     */
    private void enableNfcForegroundDispatchFallback() {
        if (!nfcInitialized || nfcAdapter == null) return;
        if (nfcPendingIntent == null || nfcIntentFilters == null) return;
        try {
            nfcAdapter.enableForegroundDispatch(
                    this,
                    nfcPendingIntent,
                    nfcIntentFilters,
                    nfcTechLists
            );
            Log.d(TAG, "[NFC] enableForegroundDispatch (fallback) success");
        } catch (Exception e) {
            Log.w(TAG, "[NFC] Failed to enable foreground dispatch fallback", e);
        }
    }

    /**
     * 去重包装：同一 markerCode 在 NFC_DEDUP_WINDOW_MS 内不重复处理。
     * ReaderMode 可能对同一张卡连续回调多次。
     */
    private void handleNfcMarkerCodeDeduped(String markerCode) {
        if (markerCode == null || markerCode.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (markerCode.equals(lastNfcMarkerCode)
                && (now - lastNfcHandledAt) < NFC_DEDUP_WINDOW_MS) {
            Log.d(TAG, "[NFC] Duplicate marker_code within dedup window, skipped: " + markerCode);
            return;
        }
        lastNfcMarkerCode = markerCode;
        lastNfcHandledAt = now;
        handleNfcMarkerCode(markerCode);
    }

    /**
     * 尝试处理 NFC Intent。
     * 返回 true 表示已处理 NFC 标签（不再走普通 Intent 流程）。
     */
    private boolean tryHandleNfcIntent(Intent intent) {
        Log.d(TAG, "[NFC] tryHandleNfcIntent action=" + (intent != null ? intent.getAction() : "null")
                + ", nfcOfflineGuideEnabled=" + nfcOfflineGuideEnabled
                + ", nfcInitialized=" + nfcInitialized);

        if (!nfcOfflineGuideEnabled || !nfcInitialized) return false;
        if (intent == null) return false;

        String action = intent.getAction();
        if (action == null) {
            Log.d(TAG, "[NFC] tryHandleNfcIntent: intent action is null, returning false");
            return false;
        }

        // 检查是否是 NFC 相关 Action
        if (!NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)
                && !NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                && !NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
            Log.d(TAG, "[NFC] tryHandleNfcIntent: non-NFC action=" + action + ", returning false");
            return false;
        }

        Log.d(TAG, "[NFC] NFC tag detected, action=" + action);

        try {
            // 从 Intent 中提取 NDEF 消息
            android.os.Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            if (rawMsgs == null || rawMsgs.length == 0) {
                showToast("已读取到 NFC 标签，但无法识别点位编码。");
                Log.w(TAG, "[NFC] No NDEF messages in intent");
                return true;
            }

            NdefMessage ndefMessage = (NdefMessage) rawMsgs[0];
            String markerCode = NfcMarkerReader.extractMarkerCode(ndefMessage);

            if (markerCode.isEmpty()) {
                showToast("已读取到 NFC 标签，但无法识别点位编码。");
                Log.w(TAG, "[NFC] Empty marker_code extracted");
                return true;
            }

            Log.d(TAG, "[NFC] Extracted marker_code: " + markerCode);
            handleNfcMarkerCode(markerCode);
        } catch (Exception e) {
            Log.w(TAG, "[NFC] Failed to process NFC tag", e);
            showToast("已读取到 NFC 标签，但无法识别点位编码。");
        }

        return true;
    }

    /**
     * 处理从 NFC 标签中提取的 marker_code。
     * 完整的分流逻辑：
     *   1. 检查开关
     *   2. 尝试离线包查找
     *   3. 根据网络状态分 NORMAL / WEAK / OFFLINE 处理
     */
    private void handleNfcMarkerCode(String markerCode) {
        if (!nfcOfflineGuideEnabled) return;
        if (markerCode == null || markerCode.isEmpty()) return;

        Log.d(TAG, "[NFC] handleNfcMarkerCode: " + markerCode);
        nfcCurrentMarkerCode = markerCode;

        // 获取当前上下文
        String currentUserId = firstNotEmpty(userId, appUserId, loginUserId);
        int areaIdInt = 1; // 默认灵山胜境
        try { areaIdInt = Integer.parseInt(areaId); } catch (Exception ignored) {}

        // 1. 尝试从离线包查找
        OfflineSpot offlineSpot = null;
        if (offlinePackageManager != null) {
            offlineSpot = offlinePackageManager.findSpotByMarkerCode(markerCode, areaIdInt);
        }

        // 2. 判断网络状态
        NetworkLevel networkLevel = networkStateHelper != null
                ? networkStateHelper.getNetworkLevel()
                : NetworkLevel.NORMAL;

        Log.d(TAG, "[NFC] markerCode=" + markerCode + ", networkLevel=" + networkLevel
                + ", offlineSpot=" + (offlineSpot != null ? offlineSpot.name : "null"));

        if (offlineSpot != null) {
            // 本地找到了景点 — 更新上下文
            nfcCurrentSpotId = String.valueOf(offlineSpot.spotId);
            nfcCurrentSpotName = offlineSpot.name;
            nfcSceneCode = offlineSpot.sceneCode;
            nfcLocationActive = true;
            rememberOfflineNfcFallbackGuide(offlineSpot);

            if (networkLevel == NetworkLevel.NORMAL) {
                handleNfcNormal(markerCode, offlineSpot, currentUserId, areaIdInt);
            } else if (networkLevel == NetworkLevel.WEAK) {
                handleNfcWeak(markerCode, offlineSpot, currentUserId, areaIdInt);
            } else {
                handleNfcOffline(markerCode, offlineSpot, currentUserId, areaIdInt);
                return;
            }
        } else {
            // 本地没有找到 — 根据网络状态处理
            if (networkLevel == NetworkLevel.NORMAL) {
                // 有网：尝试后端 checkin
                showToast("已读取到 NFC 标签，正在联网查找点位信息...");
                nfcCheckinAndGuide(markerCode, currentUserId, areaIdInt);
            } else if (networkLevel == NetworkLevel.WEAK) {
                // 弱网：尝试 checkin 但短超时
                showToast("已读取到 NFC 标签，但当前离线包中没有该点位信息，请联网更新离线包。");
                nfcCheckinBrief(markerCode, currentUserId, areaIdInt);
            } else {
                // 无网：直接提示
                showToast("已读取到 NFC 标签，但当前离线包中没有该点位信息，请联网更新离线包。");
            }
        }
    }

    /**
     * 网络 NORMAL：NFC checkin → 复用现有 /api/guide/chat 链路
     */
    private void handleNfcNormal(String markerCode, OfflineSpot spot,
                                  String userId, int areaId) {
        showToast("已识别当前位置：" + spot.name + "。我将为你展示这里的讲解内容。");

        // 先调用后端 checkin
        nfcCheckinAndGuide(markerCode, userId, areaId, spot, "CHECKIN_FAILED", "NORMAL");

        // 同时使用本地数据兜底
        updateNfcSpotContext(spot);
    }

    /**
     * 网络 WEAK：先尝试后端 checkin，失败或超时再使用本地文字兜底。
     */
    private void handleNfcWeak(String markerCode, OfflineSpot spot,
                                String userId, int areaId) {
        showToast("当前网络较弱，正在尝试识别点位信息...");
        updateNfcSpotContext(spot);
        nfcCheckinAndGuide(markerCode, userId, areaId, spot, "CHECKIN_FAILED", "WEAK");
    }

    /**
     * 网络 OFFLINE：展示离线 guide_text，不请求任何接口
     */
    private void handleNfcOffline(String markerCode, OfflineSpot spot,
                                   String userId, int areaId) {
        handleOfflineNfcGuide(markerCode, spot, "OFFLINE");
    }

    /**
     * 调用后端 NFC checkin 接口，成功后触发 AI 讲解。
     */
    private void nfcCheckinAndGuide(final String markerCode, final String userId, final int areaId) {
        nfcCheckinAndGuide(markerCode, userId, areaId, null, "", "NORMAL");
    }

    private void nfcCheckinAndGuide(
            final String markerCode,
            final String userId,
            final int areaId,
            final OfflineSpot fallbackSpot,
            final String fallbackReason,
            final String networkStatus
    ) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String baseUrl = behaviorBackendBaseUrl;
                    if (baseUrl.isEmpty()) {
                        baseUrl = getBaseUrlFromFullUrl(GUIDE_CHAT_URL);
                    }

                    NfcCheckinClient client = new NfcCheckinClient(baseUrl, authToken);
                    NfcCheckinRequest req = new NfcCheckinRequest();
                    req.userId = userId;
                    req.visitId = visitId;
                    req.areaId = (long) areaId;
                    req.markerCode = markerCode;
                    req.clientTime = getCurrentIsoTimeForNfc();
                    req.networkStatus = firstNotEmpty(networkStatus, "NORMAL");

                    final NfcCheckinResponseData data = client.checkin(req);
                    if (data != null) {
                        Log.d(TAG, "[NFC] checkin success callback: spotName=" + data.spotName);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                // 使用后端返回的上下文覆盖
                                if (data.spotId != null) {
                                    nfcCurrentSpotId = String.valueOf(data.spotId);
                                }
                                if (data.spotName != null && !data.spotName.isEmpty()) {
                                    nfcCurrentSpotName = data.spotName;
                                } else if (data.targetName != null && !data.targetName.isEmpty()) {
                                    nfcCurrentSpotName = data.targetName;
                                }
                                if (data.sceneCode != null && !data.sceneCode.isEmpty()) {
                                    nfcSceneCode = data.sceneCode;
                                }
                                if (data.areaId != null) {
                                    MainActivity.this.areaId = String.valueOf(data.areaId);
                                }
                                if (data.areaName != null && !data.areaName.isEmpty()) {
                                    areaName = data.areaName;
                                }
                                nfcCurrentMarkerCode = firstNotEmpty(data.markerCode, markerCode);
                                rememberNfcCheckinGuideFallback(data);
                                nfcLocationActive = true;

                                // 提示成功并触发 AI 讲解
                                String spotLabel = nfcCurrentSpotName.isEmpty()
                                        ? data.markerCode : nfcCurrentSpotName;
                                showToast("已识别当前位置：" + spotLabel + "。我将为你展示这里的讲解内容。");
                                triggerNfcAiGuide();
                                trySyncPendingOfflineNfcEvents();
                            }
                        });
                    } else {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (fallbackSpot != null) {
                                    Log.w(TAG, "[NFC] Backend checkin failed, using offline fallback: "
                                            + markerCode);
                                    handleOfflineNfcGuide(markerCode, fallbackSpot,
                                            firstNotEmpty(fallbackReason, "CHECKIN_FAILED"));
                                } else {
                                    Log.w(TAG, "[NFC] Backend checkin failed, no local data available");
                                    showToast("已读取到 NFC 标签，但暂时无法获取点位信息，请稍后重试。");
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.w(TAG, "[NFC] nfcCheckinAndGuide failed", e);
                    if (fallbackSpot != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                handleOfflineNfcGuide(markerCode, fallbackSpot,
                                        firstNotEmpty(fallbackReason, "CHECKIN_FAILED"));
                            }
                        });
                    }
                }
            }
        }).start();
    }

    /**
     * 弱网时异步尝试 NFC checkin（短超时）
     */
    private void nfcCheckinAndGuideAsync(final String markerCode, final String userId, final int areaId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String baseUrl = behaviorBackendBaseUrl;
                    if (baseUrl.isEmpty()) {
                        baseUrl = getBaseUrlFromFullUrl(GUIDE_CHAT_URL);
                    }

                    NfcCheckinClient client = new NfcCheckinClient(baseUrl, authToken);
                    NfcCheckinRequest req = new NfcCheckinRequest();
                    req.userId = userId;
                    req.visitId = visitId;
                    req.areaId = (long) areaId;
                    req.markerCode = markerCode;
                    req.clientTime = getCurrentIsoTimeForNfc();
                    req.networkStatus = "WEAK";

                    final NfcCheckinResponseData data = client.checkin(req);
                    if (data != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (data.spotId != null) nfcCurrentSpotId = String.valueOf(data.spotId);
                                if (data.spotName != null && !data.spotName.isEmpty()) {
                                    nfcCurrentSpotName = data.spotName;
                                } else if (data.targetName != null && !data.targetName.isEmpty()) {
                                    nfcCurrentSpotName = data.targetName;
                                }
                                if (data.sceneCode != null && !data.sceneCode.isEmpty()) nfcSceneCode = data.sceneCode;
                                if (data.areaId != null) MainActivity.this.areaId = String.valueOf(data.areaId);
                                if (data.areaName != null && !data.areaName.isEmpty()) areaName = data.areaName;
                                nfcCurrentMarkerCode = firstNotEmpty(data.markerCode, markerCode);
                                rememberNfcCheckinGuideFallback(data);
                                nfcLocationActive = true;

                                // AI 成功返回则展示更自然的讲解
                                String label = nfcCurrentSpotName.isEmpty() ? data.markerCode : nfcCurrentSpotName;
                                showToast("已识别当前位置：" + label + "。");
                                triggerNfcAiGuide();
                                trySyncPendingOfflineNfcEvents();
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.w(TAG, "[NFC] async checkin failed (weak network, expected)", e);
                }
            }
        }).start();
    }

    /**
     * 简短的 NFC checkin（弱网或无本地数据时）
     */
    private void nfcCheckinBrief(final String markerCode, final String userId, final int areaId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String baseUrl = behaviorBackendBaseUrl;
                    if (baseUrl.isEmpty()) {
                        baseUrl = getBaseUrlFromFullUrl(GUIDE_CHAT_URL);
                    }

                    NfcCheckinClient client = new NfcCheckinClient(baseUrl, authToken);
                    NfcCheckinRequest req = new NfcCheckinRequest();
                    req.userId = userId;
                    req.visitId = visitId;
                    req.areaId = (long) areaId;
                    req.markerCode = markerCode;
                    req.clientTime = getCurrentIsoTimeForNfc();
                    req.networkStatus = "WEAK";

                    final NfcCheckinResponseData data = client.checkin(req);
                    final boolean hasName = (data != null && data.spotName != null && !data.spotName.isEmpty())
                            || (data != null && data.targetName != null && !data.targetName.isEmpty());
                    if (hasName) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                nfcCurrentSpotId = data.spotId != null ? String.valueOf(data.spotId) : "";
                                if (data.spotName != null && !data.spotName.isEmpty()) {
                                    nfcCurrentSpotName = data.spotName;
                                } else {
                                    nfcCurrentSpotName = data.targetName;
                                }
                                nfcSceneCode = data.sceneCode;
                                if (data.areaId != null) MainActivity.this.areaId = String.valueOf(data.areaId);
                                if (data.areaName != null && !data.areaName.isEmpty()) areaName = data.areaName;
                                nfcCurrentMarkerCode = firstNotEmpty(data.markerCode, markerCode);
                                rememberNfcCheckinGuideFallback(data);
                                nfcLocationActive = true;
                                showToast("已识别当前位置：" + nfcCurrentSpotName + "。");
                                triggerNfcAiGuide();
                                trySyncPendingOfflineNfcEvents();
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.w(TAG, "[NFC] Brief checkin failed", e);
                }
            }
        }).start();
    }

    private void rememberNfcCheckinGuideFallback(NfcCheckinResponseData data) {
        String title = "";
        String summary = "";
        if (data != null && data.guide != null) {
            title = safeString(data.guide.title);
            summary = safeString(data.guide.summary);
        }
        nfcFallbackGuideTitle = title;
        nfcFallbackGuideSummary = summary;
        Log.d(TAG, "[NFC] fallback guide saved: title=" + safeLogText(nfcFallbackGuideTitle)
                + ", summaryLength=" + nfcFallbackGuideSummary.length());
    }

    private void rememberOfflineNfcFallbackGuide(OfflineSpot spot) {
        if (spot == null) {
            return;
        }
        nfcFallbackGuideTitle = firstNotEmpty(spot.name + "讲解", nfcFallbackGuideTitle);
        nfcFallbackGuideSummary = firstNotEmpty(spot.guideText, spot.shortIntro, nfcFallbackGuideSummary);
        Log.d(TAG, "[NFC] offline fallback guide saved: title=" + safeLogText(nfcFallbackGuideTitle)
                + ", summaryLength=" + nfcFallbackGuideSummary.length());
    }

    /**
     * 触发基于 NFC 位置的 AI 讲解。
     * 复用现有 /api/guide/chat 链路，不直连 AI。
     */
    private void triggerNfcAiGuide() {
        if (questionInput == null) return;

        String question = "给我讲讲这里";
        if (requesting) {
            Log.d(TAG, "[GuideChat] duplicate NFC guide skipped: guide/chat requesting");
            return;
        }

        String markerKey = firstNotEmpty(nfcCurrentMarkerCode, nfcSceneCode, nfcCurrentSpotId);
        long now = System.currentTimeMillis();
        if (markerKey.length() > 0
                && markerKey.equals(lastNfcGuideMarkerCode)
                && now - lastNfcGuideTriggeredAt < NFC_DEDUP_WINDOW_MS) {
            Log.d(TAG, "[GuideChat] duplicate NFC guide skipped: markerCode=" + markerKey);
            return;
        }
        lastNfcGuideMarkerCode = markerKey;
        lastNfcGuideTriggeredAt = now;

        boolean appendUserToChat = !isNfcAutoGuideQuestionAlreadyDisplayed(question);
        questionInput.setText(question);

        // 构建 location_context 和 network_context 将作为额外字段添加到
        // 现有的 askGuide 请求体中。MainActivity 的现有 askGuide 方法会
        // 发送请求到 /api/guide/chat，其中网络状态信息通过此上下文传递。
        //
        // 注意：现有 askGuide 方法体量巨大且脆弱，这里通过
        // nfcLocationActive 标志让 buildChatRequestBody 方法感知 NFC 上下文。
        askGuideInternal(question, appendUserToChat, true, "nfc");
    }

    private boolean isNfcAutoGuideQuestionAlreadyDisplayed(String question) {
        if (lastQuestionText == null) {
            return false;
        }
        String current = String.valueOf(lastQuestionText.getText()).trim();
        return current.equals("游客提问：" + safeString(question).trim());
    }

    private void handleOfflineNfcGuide(
            final String markerCode,
            final OfflineSpot offlineSpot,
            final String reason
    ) {
        if (offlineSpot == null) {
            return;
        }
        if (requesting) {
            Log.d(TAG, "[NFC][OfflineFallback] skip because guide/chat requesting markerCode=" + markerCode);
            return;
        }

        Log.d(TAG, "[NFC][OfflineFallback] start markerCode=" + markerCode
                + ", reason=" + firstNotEmpty(reason, "OFFLINE"));
        Log.d(TAG, "[NFC][OfflineFallback] spot=" + offlineSpot.name
                + ", spotId=" + offlineSpot.spotId
                + ", sceneCode=" + offlineSpot.sceneCode);

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (requesting) {
                    Log.d(TAG, "[NFC][OfflineFallback] skip on UI because guide/chat requesting markerCode="
                            + markerCode);
                    return;
                }

                String spotLabel = firstNotEmpty(offlineSpot.name, offlineSpot.sceneCode, markerCode, "当前位置");
                nfcCurrentMarkerCode = firstNotEmpty(markerCode, nfcCurrentMarkerCode);
                updateNfcSpotContext(offlineSpot);
                spotId = offlineSpot.spotId > 0 ? String.valueOf(offlineSpot.spotId) : spotId;
                spotName = spotLabel;
                scenicId = firstNotEmpty(offlineSpot.sceneCode, scenicId);
                scenicName = spotLabel;
                rememberOfflineNfcFallbackGuide(offlineSpot);

                showToast("已通过离线包识别当前位置：" + spotLabel);
                appendSystemMessage("已通过离线导览包识别当前位置：" + spotLabel);

                String guideText = buildOfflineGuideText(offlineSpot, spotLabel);
                Log.d(TAG, "[NFC][OfflineFallback] guide text ready length=" + guideText.length());
                appendAiMessage("【离线导览】" + spotLabel + "讲解\n\n" + guideText);

                if (guideStateText != null) {
                    guideStateText.setText("离线导览：" + spotLabel);
                }
                if (offlineSpot.localAudio != null && offlineSpot.localAudio.length() > 0) {
                    Log.d(TAG, "[NFC][OfflineFallback] localAudio exists, TODO play later: "
                            + offlineSpot.localAudio);
                }
                enqueueOfflineNfcEvent(markerCode, offlineSpot, firstNotEmpty(reason, "OFFLINE"));
                Log.d(TAG, "[NFC][OfflineFallback] complete markerCode=" + markerCode);
            }
        });
    }

    private void enqueueOfflineNfcEvent(String markerCode, OfflineSpot offlineSpot, String reason) {
        if (offlineNfcEventQueue == null || offlineSpot == null) {
            return;
        }
        String queueUserId = firstNotEmpty(userId, appUserId, loginUserId);
        int queueAreaId = resolveOfflineNfcAreaId();
        offlineNfcEventQueue.enqueueOfflineNfcEvent(
                queueUserId,
                visitId,
                queueAreaId,
                markerCode,
                offlineSpot,
                reason,
                getCurrentIsoTimeForNfc()
        );
    }

    private int resolveOfflineNfcAreaId() {
        String areaIdText = firstNotEmpty(areaId, guideContext.scenicId, scenicId, parkId, guideContext.parkId, areaCode);
        int parsed = parsePositiveInt(areaIdText, 0);
        return parsed > 0 ? parsed : 1;
    }

    private String buildOfflineGuideText(OfflineSpot spot, String spotLabel) {
        if (spot == null) {
            return "你已到达" + firstNotEmpty(spotLabel, "当前位置")
                    + "。当前处于离线导览模式，已根据本地离线包为你识别当前位置。";
        }

        String guideText = firstNotEmpty(
                spot.guideText,
                spot.shortIntro,
                spot.name
        );
        if (guideText.length() > 0 && !guideText.equals(spot.name)) {
            return guideText;
        }

        return "你已到达" + firstNotEmpty(spotLabel, spot.name, "当前位置")
                + "。当前处于离线导览模式，已根据本地离线包为你识别当前位置。";
    }

    /**
     * 展示离线 guide_text（替换或追加到 answerText）。
     */
    private void displayOfflineGuideText(final OfflineSpot spot) {
        if (spot == null || answerText == null) return;

        final String guideText = spot.guideText != null && !spot.guideText.isEmpty()
                ? spot.guideText
                : spot.shortIntro;

        if (guideText.isEmpty()) return;

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (answerText != null) {
                    answerText.setText("【离线讲解】" + spot.name + "\n\n" + guideText);
                }
                if (guideStateText != null) {
                    guideStateText.setText("离线讲解中：" + spot.name);
                }
                if (answerScrollView != null) {
                    answerScrollView.post(new Runnable() {
                        @Override
                        public void run() {
                            answerScrollView.fullScroll(View.FOCUS_DOWN);
                        }
                    });
                }
            }
        });
    }

    /**
     * 更新 NFC 景点上下文（当前景点名、ID 等）
     */
    private void updateNfcSpotContext(OfflineSpot spot) {
        if (spot == null) return;

        nfcCurrentSpotId = String.valueOf(spot.spotId);
        nfcCurrentSpotName = spot.name;
        nfcSceneCode = spot.sceneCode;
        nfcLocationActive = true;

        // 更新页面顶部标题
        if (targetText != null) {
            targetText.post(new Runnable() {
                @Override
                public void run() {
                    targetText.setText("📍 NFC识别：" + nfcCurrentSpotName);
                }
            });
        }
    }

    /**
     * 将 NFC_CHECKIN 事件写入本地行为队列。
     */
    private void queueNfcCheckinEvent(String userId, OfflineSpot spot, int areaId, String markerCode) {
        if (offlineBehaviorQueue == null) return;

        try {
            String eventId = offlineBehaviorQueue.addPendingEvent(
                    userId,
                    visitId,
                    (long) areaId,
                    (long) spot.spotId,
                    spot.sceneCode,
                    spot.name,
                    markerCode
            );
            Log.d(TAG, "[NFC] Queued offline event: " + eventId + " for " + spot.name
                    + " marker=" + markerCode);
        } catch (Exception e) {
            Log.w(TAG, "[NFC] Failed to queue offline event", e);
        }
    }

    private void trySyncPendingOfflineNfcEvents() {
        if (offlineNfcEventQueue == null || networkStateHelper == null) {
            return;
        }
        if (networkStateHelper.getNetworkLevel() != NetworkLevel.NORMAL) {
            Log.d(TAG, "[NFC][OfflineSync] network not normal, skip");
            return;
        }

        final List<OfflineNfcEventQueue.OfflineNfcEvent> pending =
                offlineNfcEventQueue.loadPendingEvents();
        if (pending.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (lastOfflineNfcSyncAt > 0L
                && now - lastOfflineNfcSyncAt >= 0L
                && now - lastOfflineNfcSyncAt < OFFLINE_NFC_SYNC_INTERVAL_MS) {
            Log.d(TAG, "[NFC][OfflineSync] skip throttled");
            return;
        }
        lastOfflineNfcSyncAt = now;

        Log.d(TAG, "[NFC][OfflineSync] start pendingCount=" + pending.size());
        new Thread(new Runnable() {
            @Override
            public void run() {
                int success = 0;
                int failed = 0;
                try {
                    String baseUrl = behaviorBackendBaseUrl;
                    if (baseUrl.isEmpty()) {
                        baseUrl = getBaseUrlFromFullUrl(GUIDE_CHAT_URL);
                    }

                    NfcCheckinClient client = new NfcCheckinClient(baseUrl, authToken);
                    for (int i = 0; i < pending.size(); i++) {
                        OfflineNfcEventQueue.OfflineNfcEvent event = pending.get(i);
                        if (event.retryCount >= 5) {
                            Log.d(TAG, "[NFC][OfflineSync] retry limit reached localId=" + event.localId);
                            failed++;
                            continue;
                        }

                        Log.d(TAG, "[NFC][OfflineSync] syncing localId=" + event.localId
                                + ", markerCode=" + event.markerCode);
                        NfcCheckinClient.SilentSyncResult result = client.silentSync(event);
                        if (result != null && result.success) {
                            offlineNfcEventQueue.markSynced(event.localId);
                            success++;
                            Log.d(TAG, "[NFC][OfflineSync] synced localId=" + event.localId
                                    + ", markerCode=" + event.markerCode
                                    + ", spotName=" + event.spotName);
                        } else {
                            failed++;
                            String error = result == null ? "sync result null" : result.error;
                            offlineNfcEventQueue.markFailed(event.localId, error);
                        }
                    }
                } catch (Exception e) {
                    failed = pending.size() - success;
                    Log.w(TAG, "[NFC][OfflineSync] sync failed", e);
                }
                Log.d(TAG, "[NFC][OfflineSync] complete success=" + success
                        + ", failed=" + failed);
            }
        }).start();
    }

    /**
     * 网络恢复时尝试补传 PENDING 行为事件。
     */
    private void trySyncPendingBehaviors() {
        if (offlineBehaviorQueue == null || networkStateHelper == null) return;
        if (networkStateHelper.getNetworkLevel() != NetworkLevel.NORMAL) return;
        if (!nfcOfflineGuideEnabled) return;

        final List<BehaviorEventItem> pending = offlineBehaviorQueue.listPendingEvents();
        if (pending.isEmpty()) return;

        Log.d(TAG, "[NFC] Found " + pending.size() + " pending events, attempting sync");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String baseUrl = behaviorBackendBaseUrl;
                    if (baseUrl.isEmpty()) {
                        baseUrl = getBaseUrlFromFullUrl(GUIDE_CHAT_URL);
                    }

                    BehaviorSyncClient client = new BehaviorSyncClient(baseUrl, authToken);
                    BehaviorBatchSyncRequest req = new BehaviorBatchSyncRequest();

                    String syncUserId = firstNotEmpty(userId, appUserId, loginUserId);
                    req.userId = syncUserId.isEmpty() ? "" : syncUserId;
                    req.events = pending;

                    final BehaviorBatchSyncResponseData result = client.batchSync(req);
                    if (result != null && result.success > 0) {
                        // Remove synced events
                        final List<String> syncedIds = new ArrayList<>();
                        for (BehaviorEventItem item : pending) {
                            syncedIds.add(item.eventId);
                        }
                        offlineBehaviorQueue.removeEvents(syncedIds);
                        Log.d(TAG, "[NFC] Batch sync successful: " + result.success + " events");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "[NFC] Batch sync failed, will retry later", e);
                }
            }
        }).start();
    }

    private String getCurrentIsoTimeForNfc() {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
            sdf.setTimeZone(java.util.TimeZone.getDefault());
            return sdf.format(new java.util.Date());
        } catch (Exception e) {
            return "";
        }
    }

}
