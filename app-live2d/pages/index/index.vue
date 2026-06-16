<template>
  <view class="page">
    <view class="hero">
      <view class="hero-bg-circle hero-bg-circle-1"></view>
      <view class="hero-bg-circle hero-bg-circle-2"></view>

      <view class="hero-top">
        <view class="hero-left">
          <view class="hero-title" @click="handleHeroTitleClick">即境</view>
          <view class="hero-subtitle">
            AI 数字人随行导览，讲解、问答、路线推荐一站完成
          </view>
        </view>
      </view>

      <view class="hero-search" @click="handleSearch">
        <text class="hero-search-icon">🧭</text>
        <text class="hero-search-text">搜索景区、景点或目的地</text>
      </view>

      <view class="hero-stats">
        <view class="stat-item">
          <view class="stat-value">24h</view>
          <view class="stat-label">智能导览</view>
        </view>
        <view class="stat-item">
          <view class="stat-value">AI</view>
          <view class="stat-label">语音问答</view>
        </view>
        <view class="stat-item">
          <view class="stat-value">数字人</view>
          <view class="stat-label">交互服务</view>
        </view>
      </view>
    </view>

    <view class="location-status-card card">
      <view class="location-status-title">系统支持真实 GPS 识别用户是否进入景区</view>
      <view class="location-status-text">
        当前定位状态：{{ locationStatusText }}
      </view>
      <view class="location-status-meta" v-if="locationState.source === 'demo'">
        当前为演示进入景区
      </view>
      <view class="location-actions">
        <view class="location-demo-btn" @click="handleDemoEnterScenic">
          演示进入景区
        </view>
      </view>
    </view>

    <!-- AI 数字人导览入口卡 -->
    <view class="guide-entry-card card" :class="{ onsite: isOnsiteMode }">
      <view class="guide-entry-main">
        <view class="guide-entry-avatar">
          {{ isOnsiteMode ? '导' : 'AI' }}
        </view>

        <view class="guide-entry-info">
          <view class="guide-entry-top">
            <view class="guide-entry-tag">
              {{ guideEntryTag }}
            </view>
          </view>

          <view class="guide-entry-title">
            {{ guideEntryTitle }}
          </view>

          <view class="guide-entry-subtitle">
            {{ guideEntrySubtitle }}
          </view>

          <view class="guide-entry-desc" v-if="guideEntryDesc">
            {{ guideEntryDesc }}
          </view>

          <view v-if="locationState.isInsideScenic && onsiteDistanceText" class="guide-entry-distance">
            {{ onsiteDistanceText }}
          </view>
        </view>
      </view>

      <view class="guide-entry-actions">
        <view class="guide-primary-btn" @click.stop="handleGuideEntry">
          {{ guideEntryButtonText }}
        </view>
        <view
          v-if="visitState.hasRunningVisit"
          class="guide-secondary-btn"
          @click.stop="goCurrentParkMap"
        >
          现场地图
        </view>
      </view>
    </view>

    <view
      v-if="tripInfoDialogVisible"
      class="trip-info-mask"
      @click.stop
      @touchmove.stop.prevent
    >
      <view class="trip-info-dialog" @click.stop>
        <view class="trip-info-title">本次出行信息</view>
        <view class="trip-info-subtitle">
          {{ (pendingTripInfoArea && pendingTripInfoArea.areaName) || '当前景区' }}现场导览将根据这些信息生成讲解和路线建议
        </view>

        <view class="trip-info-field">
          <view class="trip-info-label">出行人数</view>
          <view class="trip-info-options">
            <view
              v-for="item in groupSizeOptions"
              :key="item"
              class="trip-info-chip"
              :class="{ active: tripInfoForm.groupSize === item }"
              @click="selectTripInfoField('groupSize', item)"
            >
              {{ item }}
            </view>
          </view>
        </view>

        <view class="trip-info-field">
          <view class="trip-info-label">出行类型</view>
          <view class="trip-info-options">
            <view
              v-for="item in travelTypeOptions"
              :key="item"
              class="trip-info-chip"
              :class="{ active: tripInfoForm.travelType === item }"
              @click="selectTripInfoField('travelType', item)"
            >
              {{ item }}
            </view>
          </view>
        </view>

        <view class="trip-info-field">
          <view class="trip-info-label">游玩偏好</view>
          <view class="trip-info-options">
            <view
              v-for="item in visitPreferenceOptions"
              :key="item"
              class="trip-info-chip"
              :class="{ active: tripInfoForm.visitPreference === item }"
              @click="selectTripInfoField('visitPreference', item)"
            >
              {{ item }}
            </view>
          </view>
        </view>

        <view class="trip-info-field">
          <view class="trip-info-label">游玩时长</view>
          <view class="trip-info-options">
            <view
              v-for="item in tripDurationOptions"
              :key="item"
              class="trip-info-chip"
              :class="{ active: tripInfoForm.estimatedDuration === item }"
              @click="selectTripInfoField('estimatedDuration', item)"
            >
              {{ item }}
            </view>
          </view>
        </view>

        <view class="trip-info-actions">
          <view class="trip-info-cancel" @click="cancelTripInfoForm">稍后再说</view>
          <view
            class="trip-info-submit"
            :class="{ disabled: tripInfoSubmitting }"
            @click="submitTripInfoForm"
          >
            {{ tripInfoSubmitting ? '正在开启...' : '提交并开启导览' }}
          </view>
        </view>
      </view>
    </view>

    <view class="section-header">
      <view class="section-title">快捷服务</view>
      <view class="section-more">常用功能</view>
    </view>

    <view class="service-grid">
      <view
        class="service-item"
        v-for="item in quickList"
        :key="item.name"
        @click="handleQuickAction(item)"
      >
        <view class="service-icon">{{ item.icon }}</view>
        <view class="service-name">{{ item.name }}</view>
      </view>
    </view>

    <view class="section-header">
      <view class="section-title">热门推荐</view>
      <view class="section-more" @click="goParkList">查看热门</view>
    </view>

    <view v-if="parkLoading" class="status-card card">
      <text class="status-text">正在加载热门景区...</text>
    </view>

    <view v-else-if="recommendParkList.length === 0" class="status-card card">
      <text class="status-text">暂无热门景区</text>
    </view>

    <view v-else class="scenic-list">
      <view
        class="scenic-card card"
        v-for="(item, index) in recommendParkList"
        :key="item.id"
        @click="handleParkClick(item)"
      >
        <view class="scenic-cover">
          <image
            v-if="getParkImage(item)"
            class="scenic-cover-image"
            :src="getParkImage(item)"
            mode="aspectFill"
            @error="handleParkImageError(item)"
          />

          <view v-else class="scenic-cover-placeholder">
            <text class="scenic-cover-placeholder-text">
              {{ getCoverText(item) }}
            </text>
          </view>

          <view class="scenic-cover-mask"></view>

          <view class="scenic-tag">TOP {{ index + 1 }}</view>
        </view>

        <view class="scenic-content">
          <view class="scenic-name">{{ item.name || '未命名景区' }}</view>
          <view class="scenic-desc">{{ formatShortDesc(item.desc, 44) }}</view>

          <view class="scenic-meta">
            <text class="scenic-meta-item">景点数量：{{ item.scenicCount ?? 0 }}</text>
            <text class="scenic-meta-item">热度：{{ item.heat || '推荐' }}</text>
          </view>

          <view class="scenic-tags" v-if="item.tags && item.tags.length">
            <text class="chip" v-for="tag in item.tags.slice(0, 2)" :key="tag">
              {{ tag }}
            </text>
          </view>

          <view class="scenic-actions" @click.stop>
            <view class="small-btn" @click="handleParkClick(item)">查看详情</view>
            <view class="small-btn primary" @click="goAiExplainPark(item)">AI讲解</view>
          </view>
        </view>
      </view>
    </view>

    <view v-if="showHomeNotice">
      <view class="section-header">
        <view class="section-title">公告与活动</view>
        <view class="section-more" @click="handleNoticeMore">实时更新</view>
      </view>

      <view v-if="noticeLoading" class="notice-card card">
        <view class="notice-item">
          <view class="notice-dot"></view>
          <view class="notice-main">
            <view class="notice-title">正在加载公告活动...</view>
          </view>
        </view>
      </view>

      <view v-else-if="noticeList.length === 0" class="notice-card card">
        <view class="notice-item">
          <view class="notice-dot"></view>
          <view class="notice-main">
            <view class="notice-title">暂无公告活动</view>
          </view>
        </view>
      </view>

      <view v-else class="notice-card card">
        <view
          class="notice-item"
          v-for="item in noticeList"
          :key="item.id || item.title"
          @click="handleNoticeClick(item)"
        >
          <view class="notice-dot"></view>
          <view class="notice-main">
            <view class="notice-title">{{ item.title }}</view>
            <view class="notice-desc">{{ item.desc }}</view>
          </view>
        </view>
      </view>
    </view>

  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { API_BASE, NATIVE_LIVE2D_SOURCE } from '../../utils/api'
import { openNativeLive2DGuide } from '../../utils/openNativeLive2D.js'
import { resolveImageUrl } from '../../utils/image'
import { trackUserBehavior } from '@/utils/behavior'
import {
  getCurrentVisitId,
  queryVisitStatus,
  getLastEndedVisit,
  getRecentNativeGuideEndedReturn,
  markVisitEndedLocal,
  clearCurrentVisitInfo,
  clearNativeGuideEndedReturn,
  checkPendingGuideReturn,
  goVisitReport,
  isLoginRequiredVisitError,
  startVisitGuide,
  TRIP_GROUP_SIZE_OPTIONS,
  TRIP_TRAVEL_TYPE_OPTIONS,
  TRIP_VISIT_PREFERENCE_OPTIONS
} from '@/utils/visit'
import {
  getCurrentOnsiteStatus,
  checkOnsiteGuideByLocation,
  clearOnsiteGuideStorage
} from '@/common/onsite-guide.js'
import {
  getCurrentUserId,
  requireLogin
} from '@/utils/auth'

const HOT_PARKS_API = `${API_BASE}/api/app/parks/hot?limit=2`
const NOTICES_API = `${API_BASE}/api/app/notices`
const SCENIC_LIST_MODE_KEY = 'scenicListMode'
const SETTINGS_KEY = 'appSettings'
const CURRENT_DEMO_PARK_ID_KEY = 'currentDemoParkId'
const CURRENT_DEMO_PARK_NAME_KEY = 'currentDemoParkName'
const HERO_TITLE_DOUBLE_CLICK_INTERVAL = 420
const TRIP_DURATION_OPTIONS = ['1小时', '2小时', '半天', '全天']

const defaultAppSettings = {
  enableVoice: true,
  autoPlayVoice: true,
  showConsultHistory: true,
  showHomeNotice: true
}

const DEMO_PARK_OPTIONS = [
  {
    parkId: 1,
    areaId: 1,
    parkCode: 'AREA_0001',
    areaCode: 'AREA_0001',
    parkName: '灵山胜境',
    areaName: '灵山胜境'
  },
  {
    parkId: 2,
    areaId: 2,
    parkCode: 'AREA_0002',
    areaCode: 'AREA_0002',
    parkName: '拈花湾禅意小镇',
    areaName: '拈花湾禅意小镇'
  }
]

const NATIVE_RETURN_RESIDUE_KEYS = [
  'NATIVE_GUIDE_RETURN_CONTEXT',
  'NATIVE_GUIDE_RETURNING',
  'NATIVE_GUIDE_RETURN_LOCK'
]

function isAreaBusinessCode(value) {
  return /^AREA_\d+$/i.test(String(value || '').trim())
}

function parseNumericAreaId(...values) {
  for (const value of values) {
    if (value === undefined || value === null || value === '') {
      continue
    }

    const text = String(value).trim()
    if (!text) {
      continue
    }

    if (/^\d+$/.test(text)) {
      return Number(text)
    }

    const match = text.match(/^AREA_0*(\d+)$/i)
    if (match) {
      return Number(match[1])
    }
  }

  return ''
}

function pickAreaCode(...values) {
  for (const value of values) {
    if (value === undefined || value === null || value === '') {
      continue
    }

    const text = String(value).trim()
    if (isAreaBusinessCode(text)) {
      return text.toUpperCase()
    }
  }

  return ''
}

let lastHeroTitleClickTime = 0

const parkLoading = ref(false)
const noticeLoading = ref(false)
const showHomeNotice = ref(true)
const openingNativeGuide = ref(false)
const tripInfoDialogVisible = ref(false)
const tripInfoSubmitting = ref(false)
const currentVisitId = ref('')
const onsiteGuideState = ref('NORMAL')
const lastEndedVisitId = ref('')
const lastEndedAreaName = ref('')
const syncingVisitStatus = ref(false)
const imageErrorMap = ref({})

const groupSizeOptions = TRIP_GROUP_SIZE_OPTIONS
const travelTypeOptions = TRIP_TRAVEL_TYPE_OPTIONS
const visitPreferenceOptions = TRIP_VISIT_PREFERENCE_OPTIONS
const tripDurationOptions = TRIP_DURATION_OPTIONS

const onsiteStatus = ref({
  inside: false
})

const locationState = ref({
  gpsSupported: true,
  locating: false,
  isInsideScenic: false,
  hitArea: null,
  source: 'real',
  currentLat: '',
  currentLng: '',
  locationText: '当前未进入景区'
})

const visitState = ref({
  hasRunningVisit: false,
  visitId: '',
  areaId: '',
  areaName: '',
  startedAt: ''
})

const reportState = ref({
  hasReport: false,
  reportVisitId: '',
  reportAreaName: '',
  finishedAt: ''
})

const pendingTripInfoArea = ref(null)
const pendingTripInfoSource = ref('gps')
const tripInfoForm = ref({
  groupSize: groupSizeOptions[0],
  travelType: travelTypeOptions[0],
  visitPreference: visitPreferenceOptions[0],
  estimatedDuration: tripDurationOptions[1]
})

const promptState = ref({
  arrivalPromptVisible: false,
  lastPromptAreaId: '',
  suppressAutoPromptUntilExit: false
})

const parkList = ref([])

const noticeList = ref([
  {
    id: 'notice-1',
    title: '公告与活动信息接入中',
    desc: '后续将同步展示景区官方公告、活动安排与服务通知。'
  },
  {
    id: 'notice-2',
    title: '更多资讯敬请期待',
    desc: '当前页面已预留公告能力，后续将逐步完善。'
  }
])

const recommendParkList = computed(() => {
  return parkList.value
})

const isOnsiteMode = computed(() => {
  return visitState.value.hasRunningVisit ||
    reportState.value.hasReport ||
    locationState.value.isInsideScenic
})

const onsiteParkName = computed(() => {
  return (
    locationState.value.hitArea?.areaName ||
    onsiteStatus.value?.context?.parkName ||
    onsiteStatus.value?.context?.areaName ||
    onsiteStatus.value?.scenic?.parkName ||
    onsiteStatus.value?.scenic?.areaName ||
    '当前景区'
  )
})

const activeVisitParkName = computed(() => {
  return visitState.value.areaName || resolveGuideAreaName()
})

const guideEntryTag = computed(() => {
  if (visitState.value.hasRunningVisit) return '现场导览模式'
  if (reportState.value.hasReport) return '游玩报告'
  if (locationState.value.isInsideScenic) return '现场导览模式'
  return '行前规划模式'
})

const guideEntryTitle = computed(() => {
  if (visitState.value.hasRunningVisit) return '现场导览进行中'
  if (reportState.value.hasReport) return '本次导览已完成'
  if (locationState.value.isInsideScenic) return '现场导览已就绪'
  return '即境 AI 导览助手'
})

const guideEntrySubtitle = computed(() => {
  if (visitState.value.hasRunningVisit) {
    return `你正在 ${activeVisitParkName.value} 景区导览中`
  }

  if (reportState.value.hasReport) {
    return '已为你生成游玩报告'
  }

  if (locationState.value.isInsideScenic) {
    return `欢迎来到 ${onsiteParkName.value}，AI 数字人导游已就绪`
  }

  return '未进入景区时，可用于景区咨询、路线规划和行前问答'
})

const guideEntryDesc = computed(() => {
  if (visitState.value.hasRunningVisit) {
    return '可继续回到原生数字人现场导览，或查看当前景区现场地图'
  }

  if (locationState.value.isInsideScenic) {
    return 'AI 数字人导游已为你准备现场讲解、路线推荐与附近景点介绍'
  }

  if (!reportState.value.hasReport) {
    return '支持景点讲解、路线推荐、语音问答与游客服务咨询'
  }

  return ''
})

const guideEntryButtonText = computed(() => {
  if (visitState.value.hasRunningVisit) return '继续导览'
  if (reportState.value.hasReport) return '查看报告'
  if (locationState.value.isInsideScenic) return '开启导览'
  return '立即进入'
})

const onsiteDistanceText = computed(() => {
  const distance =
    locationState.value.hitArea?.distance ??
    onsiteStatus.value?.context?.distance ??
    onsiteStatus.value?.scenic?.distance

  if (distance === undefined || distance === null || distance === '') {
    return ''
  }

  return `距离景区中心约 ${distance} 米`
})

const locationStatusText = computed(() => {
  if (locationState.value.locating) {
    return '正在定位...'
  }

  return locationState.value.locationText || '当前未进入景区'
})

const quickList = [
  { name: '扫码消费', icon: '💳', action: 'scan_pay' },
  { name: '景区导览', icon: '📍', action: 'park' },
  { name: '游客须知', icon: '📘', action: 'visitor_notice' },
  { name: '使用帮助', icon: '🧭', action: 'help' }
]

function clearNativeGuideReturnResidue() {
  NATIVE_RETURN_RESIDUE_KEYS.forEach(key => {
    try {
      uni.removeStorageSync(key)
    } catch (error) {
      console.log('清理原生导览返回残留失败：', key, error)
    }
  })
}

function markHomeStableState() {
  clearNativeGuideReturnResidue()
  uni.setStorageSync('lastNonGuideTab', '/pages/index/index')
}

function refreshOnsiteStatus() {
  onsiteStatus.value = getCurrentOnsiteStatus()
}

function refreshCurrentVisitId() {
  const visitId = getCurrentVisitId()
  currentVisitId.value = visitId || ''
}

function refreshLastEndedVisit() {
  const ended = getLastEndedVisit()
  lastEndedVisitId.value = ended.visitId || ''
  lastEndedAreaName.value = ended.areaName || ''

  reportState.value = {
    hasReport: !!ended.visitId,
    reportVisitId: ended.visitId || '',
    reportAreaName: ended.areaName || '',
    finishedAt: ended.finishedAt || ''
  }

  if (ended.visitId && !visitState.value.hasRunningVisit && !currentVisitId.value) {
    promptState.value = {
      ...promptState.value,
      suppressAutoPromptUntilExit: true
    }
  }
}

function resolveGuideAreaName() {
  return (
    uni.getStorageSync('currentGuideAreaName') ||
    uni.getStorageSync('currentParkName') ||
    lastEndedAreaName.value ||
    resolveCurrentParkName(onsiteStatus.value) ||
    onsiteParkName.value ||
    '当前景区'
  )
}

function refreshGuideStateFromLocal() {
  refreshCurrentVisitId()
  refreshLastEndedVisit()

  if (visitState.value.hasRunningVisit || currentVisitId.value) {
    onsiteGuideState.value = 'ACTIVE'
    return
  }

  if (reportState.value.hasReport || lastEndedVisitId.value) {
    onsiteGuideState.value = 'ENDED'
    return
  }

  onsiteGuideState.value = locationState.value.isInsideScenic ? 'READY' : 'NORMAL'
}

function setRunningVisit(data = {}) {
  const visitId = data.visitId || ''
  const areaId = data.areaId || data.parkId || data.areaCode || ''
  const areaName = data.areaName || data.parkName || data.currentParkName || ''

  currentVisitId.value = visitId
  visitState.value = {
    hasRunningVisit: !!visitId,
    visitId,
    areaId,
    areaName,
    startedAt: data.startedAt || data.startTime || data.start_time || ''
  }
  onsiteGuideState.value = visitId ? 'ACTIVE' : onsiteGuideState.value
}

function clearRunningVisitState() {
  currentVisitId.value = ''
  visitState.value = {
    hasRunningVisit: false,
    visitId: '',
    areaId: '',
    areaName: '',
    startedAt: ''
  }
}

function setReportState(data = {}) {
  const reportVisitId = data.visitId || data.reportVisitId || ''
  const reportAreaName = data.areaName || data.parkName || data.reportAreaName || ''

  reportState.value = {
    hasReport: !!reportVisitId,
    reportVisitId,
    reportAreaName,
    finishedAt: data.finishedAt || data.endTime || data.end_time || ''
  }
  lastEndedVisitId.value = reportVisitId
  lastEndedAreaName.value = reportAreaName
  onsiteGuideState.value = reportVisitId ? 'ENDED' : onsiteGuideState.value
}

function resetPromptSuppression() {
  promptState.value = {
    ...promptState.value,
    lastPromptAreaId: '',
    suppressAutoPromptUntilExit: false
  }
}

function applyOutsideLocation(text = '当前未进入景区') {
  locationState.value = {
    ...locationState.value,
    locating: false,
    isInsideScenic: false,
    hitArea: null,
    source: 'real',
    locationText: text
  }
  resetPromptSuppression()
  refreshGuideStateFromLocal()
}

function normalizeArea(area = {}) {
  const areaId = parseNumericAreaId(
    area.areaId,
    area.area_id,
    area.parkId,
    area.park_id,
    area.id,
    area.areaCode,
    area.area_code,
    area.parkCode,
    area.park_code
  )
  const parkId = parseNumericAreaId(
    area.parkId,
    area.park_id,
    area.areaId,
    area.area_id,
    area.id,
    area.parkCode,
    area.park_code,
    area.areaCode,
    area.area_code
  ) || areaId
  const areaCode = pickAreaCode(
    area.areaCode,
    area.area_code,
    area.parkCode,
    area.park_code,
    area.areaId,
    area.area_id,
    area.parkId,
    area.park_id
  )
  const parkCode = pickAreaCode(
    area.parkCode,
    area.park_code,
    area.areaCode,
    area.area_code,
    area.parkId,
    area.park_id,
    area.areaId,
    area.area_id
  ) || areaCode
  const areaName = area.areaName || area.area_name || area.parkName || area.park_name || area.name || '当前景区'

  return {
    ...area,
    areaId,
    parkId: parkId || areaId,
    areaCode,
    parkCode,
    areaName,
    parkName: area.parkName || area.park_name || areaName,
    latitude: area.latitude || '',
    longitude: area.longitude || '',
    distance: area.distance ?? '',
    distanceText: area.distanceText || (area.distance !== undefined && area.distance !== null && area.distance !== ''
      ? `距离景区中心约 ${area.distance} 米`
      : '')
  }
}

function buildAreaFromOnsiteStatus(status = {}) {
  const context = status.context || {}
  const scenic = status.scenic || {}
  const location = status.location || {}

  return normalizeArea({
    areaId: context.areaId || context.area_id || scenic.areaId || scenic.area_id || context.parkId || context.park_id || scenic.parkId || scenic.park_id || '',
    parkId: context.parkId || context.park_id || scenic.parkId || scenic.park_id || context.areaId || context.area_id || scenic.areaId || scenic.area_id || '',
    areaCode: context.areaCode || context.area_code || scenic.areaCode || scenic.area_code || context.parkCode || context.park_code || scenic.parkCode || scenic.park_code || '',
    parkCode: context.parkCode || context.park_code || scenic.parkCode || scenic.park_code || context.areaCode || context.area_code || scenic.areaCode || scenic.area_code || '',
    areaName: context.parkName || scenic.parkName || context.areaName || scenic.areaName || '',
    latitude: context.latitude || location.latitude || '',
    longitude: context.longitude || location.longitude || '',
    distance: context.distance ?? scenic.distance ?? ''
  })
}

function buildAreaFromDemoPark(park = {}) {
  return normalizeArea({
    areaId: park.areaId || park.parkId,
    parkId: park.parkId || park.areaId,
    areaCode: park.areaCode || park.parkCode,
    parkCode: park.parkCode || park.areaCode,
    areaName: park.areaName || park.parkName,
    parkName: park.parkName || park.areaName,
    latitude: park.latitude || '',
    longitude: park.longitude || '',
    distanceText: '演示进入景区'
  })
}

function buildPageQuery(query = {}) {
  return Object.keys(query)
    .filter(key => query[key] !== undefined && query[key] !== null && query[key] !== '')
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(String(query[key]))}`)
    .join('&')
}

function resolveCurrentParkId(status) {
  const context = status?.context || {}
  const scenic = status?.scenic || {}

  return parseNumericAreaId(
    context.parkId,
    context.park_id,
    context.areaId,
    context.area_id,
    context.id,
    scenic.parkId,
    scenic.park_id,
    scenic.areaId,
    scenic.area_id,
    scenic.id,
    context.areaCode,
    context.area_code,
    scenic.areaCode,
    scenic.area_code
  ) || ''
}

function resolveCurrentParkName(status) {
  const context = status?.context || {}
  const scenic = status?.scenic || {}

  return (
    context.parkName ||
    context.areaName ||
    context.area_name ||
    context.name ||
    scenic.parkName ||
    scenic.areaName ||
    scenic.area_name ||
    ''
  )
}

async function syncVisitState() {
  if (syncingVisitStatus.value) {
    return
  }

  syncingVisitStatus.value = true

  try {
    refreshOnsiteStatus()

    const returnResult = await checkPendingGuideReturn()

    if (returnResult?.ended && returnResult.visitId) {
      clearOnsiteGuideStorage()
      clearRunningVisitState()
      setReportState({
        visitId: returnResult.visitId,
        areaName: returnResult.areaName
      })
      promptState.value = {
        ...promptState.value,
        suppressAutoPromptUntilExit: true
      }
      refreshGuideStateFromLocal()
      return
    }

    if (returnResult?.invalid) {
      clearRunningVisitState()
      refreshGuideStateFromLocal()
      return
    }

    if (returnResult?.loginRequired) {
      clearCurrentVisitInfo()
      clearRunningVisitState()
      refreshGuideStateFromLocal()
      uni.showToast({
        title: '请先登录后查看导览状态',
        icon: 'none'
      })
      return
    }

    if (returnResult?.active && returnResult.status) {
      setRunningVisit({
        visitId: returnResult.status.visitId || getCurrentVisitId(),
        areaId: returnResult.status.parkId,
        areaName: returnResult.status.parkName
      })
      return
    }

    const activeVisit = uni.getStorageSync('activeVisit') || {}
    const nativeEndedReturn = getRecentNativeGuideEndedReturn()
    const localVisitId =
      currentVisitId.value ||
      getCurrentVisitId() ||
      uni.getStorageSync('activeVisitId') ||
      activeVisit.visitId ||
      activeVisit.currentVisitId ||
      ''

    if (nativeEndedReturn.recent) {
      if (!localVisitId || localVisitId === nativeEndedReturn.visitId) {
        markVisitEndedLocal({
          visitId: nativeEndedReturn.visitId,
          areaName: nativeEndedReturn.areaName || resolveGuideAreaName()
        })
        clearOnsiteGuideStorage()
        clearCurrentVisitInfo()
        clearRunningVisitState()
        setReportState({
          visitId: nativeEndedReturn.visitId,
          areaName: nativeEndedReturn.areaName || resolveGuideAreaName()
        })
        promptState.value = {
          ...promptState.value,
          suppressAutoPromptUntilExit: true
        }
        refreshGuideStateFromLocal()
        return
      }

      clearNativeGuideEndedReturn()
    }

    if (localVisitId) {
      try {
        const status = await queryVisitStatus(localVisitId)

        if (status.state === 'ACTIVE') {
          setRunningVisit({
            visitId: status.visitId || localVisitId,
            areaId: status.parkId || activeVisit.parkId || activeVisit.currentParkId || '',
            areaName: status.parkName || activeVisit.parkName || activeVisit.currentParkName || resolveGuideAreaName()
          })
          return
        }

        if (status.state === 'ENDED') {
          markVisitEndedLocal({
            visitId: status.visitId || localVisitId,
            areaName: status.parkName || activeVisit.parkName || activeVisit.currentParkName || resolveGuideAreaName()
          })
          clearOnsiteGuideStorage()
          clearRunningVisitState()
          setReportState({
            visitId: status.visitId || localVisitId,
            areaName: status.parkName || activeVisit.parkName || activeVisit.currentParkName || resolveGuideAreaName()
          })
          promptState.value = {
            ...promptState.value,
            suppressAutoPromptUntilExit: true
          }
          refreshGuideStateFromLocal()
          return
        }

        if (status.state === 'INVALID') {
          clearCurrentVisitInfo()
          clearRunningVisitState()
          refreshGuideStateFromLocal()
          return
        }
      } catch (error) {
        if (isLoginRequiredVisitError(error)) {
          console.warn('首页查询导览状态需要登录，停止本轮同步：', error)
          clearCurrentVisitInfo()
          clearRunningVisitState()
          refreshGuideStateFromLocal()
          uni.showToast({
            title: '请先登录后查看导览状态',
            icon: 'none'
          })
          return
        }

        console.warn('首页查询导览状态失败，暂用本地状态：', error)
        const recentNativeEnd = getRecentNativeGuideEndedReturn()
        if (recentNativeEnd.recent && (!localVisitId || localVisitId === recentNativeEnd.visitId)) {
          markVisitEndedLocal({
            visitId: recentNativeEnd.visitId,
            areaName: recentNativeEnd.areaName || resolveGuideAreaName()
          })
          clearOnsiteGuideStorage()
          clearCurrentVisitInfo()
          clearRunningVisitState()
          setReportState({
            visitId: recentNativeEnd.visitId,
            areaName: recentNativeEnd.areaName || resolveGuideAreaName()
          })
          promptState.value = {
            ...promptState.value,
            suppressAutoPromptUntilExit: true
          }
          refreshGuideStateFromLocal()
          return
        }

        setRunningVisit({
          visitId: localVisitId,
          areaId: activeVisit.parkId || activeVisit.currentParkId || uni.getStorageSync('currentParkId') || '',
          areaName: activeVisit.parkName || activeVisit.currentParkName || uni.getStorageSync('currentParkName') || resolveGuideAreaName()
        })
        return
      }
    }

    clearRunningVisitState()
    refreshGuideStateFromLocal()
  } finally {
    syncingVisitStatus.value = false
  }
}

async function refreshLocationDetection(options = {}) {
  locationState.value = {
    ...locationState.value,
    locating: true,
    gpsSupported: true
  }

  try {
    const status = await checkOnsiteGuideByLocation({
      showPrompt: false,
      force: options.force === true
    })

    refreshOnsiteStatus()

    const finalStatus = status || onsiteStatus.value

    if (!finalStatus?.inside) {
      applyOutsideLocation('当前未进入景区')
      return finalStatus
    }

    const area = buildAreaFromOnsiteStatus(finalStatus)
    handleScenicHit(area, 'real')
    return finalStatus
  } catch (error) {
    console.log('首页 GPS 检测失败：', error)
    locationState.value = {
      ...locationState.value,
      locating: false,
      gpsSupported: false,
      locationText: '定位失败，可稍后重试或使用演示进入景区'
    }
    refreshGuideStateFromLocal()
    return null
  }
}

async function refreshHomeRuntimeState(options = {}) {
  await Promise.all([
    syncVisitState(),
    refreshLocationDetection(options)
  ])
}

function handleScenicHit(area, source = 'real') {
  const normalizedArea = normalizeArea(area)

  locationState.value = {
    ...locationState.value,
    locating: false,
    isInsideScenic: true,
    hitArea: normalizedArea,
    source: source === 'demo' ? 'demo' : 'real',
    currentLat: normalizedArea.latitude || '',
    currentLng: normalizedArea.longitude || '',
    locationText: `已进入${normalizedArea.areaName}`
  }

  refreshGuideStateFromLocal()

  if (visitState.value.hasRunningVisit || getCurrentVisitId()) {
    return
  }

  if (source === 'demo') {
    showArrivalPrompt(normalizedArea, source)
    return
  }

  if (promptState.value.suppressAutoPromptUntilExit) {
    return
  }

  if (promptState.value.lastPromptAreaId === normalizedArea.areaId) {
    return
  }

  showArrivalPrompt(normalizedArea, source)
}

function showArrivalPrompt(area, source = 'real') {
  if (!area?.areaId || promptState.value.arrivalPromptVisible) {
    return
  }

  promptState.value = {
    ...promptState.value,
    arrivalPromptVisible: true,
    lastPromptAreaId: source === 'demo' ? '' : area.areaId
  }

  uni.showModal({
    title: `欢迎来到${area.areaName}`,
    content: `欢迎来到${area.areaName}，是否开启智能导览？`,
    confirmText: '开启智能导览',
    cancelText: '稍后再说',
    success(res) {
      if (res.confirm) {
        goVisitStart(area, source)
      }
    },
    complete() {
      promptState.value = {
        ...promptState.value,
        arrivalPromptVisible: false
      }
    }
  })
}

function goVisitStart(area = locationState.value.hitArea, source = locationState.value.source) {
  const normalizedArea = normalizeArea(area || {})

  if (!normalizedArea.areaId) {
    uni.showToast({
      title: '景区ID异常，请重新进入现场导览',
      icon: 'none'
    })
    return
  }

  showTripInfoForm(normalizedArea, source)
}

function resetTripInfoForm() {
  tripInfoForm.value = {
    groupSize: groupSizeOptions[0],
    travelType: travelTypeOptions[0],
    visitPreference: visitPreferenceOptions[0],
    estimatedDuration: tripDurationOptions[1]
  }
}

function showTripInfoForm(area = locationState.value.hitArea, source = locationState.value.source) {
  const normalizedArea = normalizeArea(area || {})

  if (!normalizedArea.areaId) {
    uni.showToast({
      title: '景区ID异常，请重新进入现场导览',
      icon: 'none'
    })
    return
  }

  if (visitState.value.hasRunningVisit || getCurrentVisitId()) {
    uni.showToast({
      title: '当前已有进行中的现场导览',
      icon: 'none'
    })
    return
  }

  pendingTripInfoArea.value = normalizedArea
  pendingTripInfoSource.value = source === 'demo' ? 'demo' : 'gps'
  resetTripInfoForm()
  tripInfoDialogVisible.value = true
}

function cancelTripInfoForm() {
  if (tripInfoSubmitting.value) {
    return
  }

  tripInfoDialogVisible.value = false
  pendingTripInfoArea.value = null
}

function selectTripInfoField(field, value) {
  if (tripInfoSubmitting.value) {
    return
  }

  tripInfoForm.value = {
    ...tripInfoForm.value,
    [field]: value
  }
}

async function submitTripInfoForm() {
  if (tripInfoSubmitting.value) {
    return
  }

  const area = normalizeArea(pendingTripInfoArea.value || locationState.value.hitArea || {})

  if (!area.areaId) {
    uni.showToast({
      title: '景区ID异常，请重新进入现场导览',
      icon: 'none'
    })
    return
  }

  if (!requireLogin()) {
    return
  }

  const userId = getCurrentUserId()

  if (!isRealLoginUserId(userId)) {
    uni.showToast({
      title: '请使用登录账号开启导览',
      icon: 'none'
    })
    return
  }

  const entrySource = pendingTripInfoSource.value === 'demo' ? 'demo' : 'gps'
  const startSource = entrySource === 'gps' ? 'gps' : 'manual'
  const finalAreaId = parseNumericAreaId(area.areaId, area.parkId, area.areaCode, area.parkCode)
  const finalParkId = parseNumericAreaId(area.parkId, area.areaId, area.parkCode, area.areaCode) || finalAreaId
  const finalAreaCode = area.areaCode || pickAreaCode(area.areaId, area.parkId, area.parkCode)
  const finalParkCode = area.parkCode || finalAreaCode
  const finalAreaName = area.areaName || area.parkName || '当前景区'
  const finalParkName = area.parkName || area.areaName || finalAreaName

  tripInfoSubmitting.value = true

  try {
    const form = tripInfoForm.value
    const startResult = await startVisitGuide({
      userId,
      areaId: finalAreaId,
      areaCode: finalAreaCode,
      areaName: finalAreaName,
      parkId: finalParkId,
      parkCode: finalParkCode,
      parkName: finalParkName,
      entrySource,
      startSource,
      latitude: area.latitude || '',
      longitude: area.longitude || '',
      travelPeopleCount: form.groupSize,
      groupSize: form.groupSize,
      travelType: form.travelType,
      travelPreference: form.visitPreference,
      visitPreference: form.visitPreference,
      estimatedDuration: form.estimatedDuration
    })

    const visitId = startResult.visitId

    if (!visitId) {
      throw new Error('visitId missing')
    }

    setRunningVisit({
      visitId,
      areaId: finalParkId,
      areaName: finalParkName,
      startedAt: Date.now()
    })

    const opened = await openNativeLive2DGuide({
      entry: 'home-start-onsite-guide',
      source: NATIVE_LIVE2D_SOURCE,
      userId,
      mode: 'onsite',
      isOnsiteGuide: true,
      startVisitGuide: false,
      allowEndVisit: true,
      visitId,
      areaId: finalAreaId,
      areaCode: finalAreaCode,
      areaName: finalAreaName,
      parkId: finalParkId,
      parkCode: finalParkCode,
      parkName: finalParkName,
      scenicId: finalAreaId,
      scenicName: finalAreaName,
      contextType: 'park',
      contextName: finalAreaName,
      trigger: entrySource === 'demo' ? 'demo-onsite' : 'gps-onsite',
      entrySource,
      latitude: area.latitude || '',
      longitude: area.longitude || '',
      travelPeopleCount: form.groupSize,
      groupSize: form.groupSize,
      travelType: form.travelType,
      travelPreference: form.visitPreference,
      visitPreference: form.visitPreference,
      estimatedDuration: form.estimatedDuration,
      autoQuestion: '',
      welcomeText: `欢迎来到${finalAreaName}，我是你的AI数字人导游。现场导览已开启，你可以主动向我提问或点击推荐路线。`
    })

    if (opened) {
      tripInfoDialogVisible.value = false
      pendingTripInfoArea.value = null
    }
  } catch (error) {
    console.log('首页开启现场导览失败：', error)
    uni.showToast({
      title: error?.message || '开启导览失败，请稍后重试',
      icon: 'none'
    })
  } finally {
    setTimeout(() => {
      tripInfoSubmitting.value = false
    }, 800)
  }
}

function handleHeroTitleClick() {
  const now = Date.now()

  if (now - lastHeroTitleClickTime <= HERO_TITLE_DOUBLE_CLICK_INTERVAL) {
    lastHeroTitleClickTime = 0
    showDemoParkSelector()
    return
  }

  lastHeroTitleClickTime = now
}

function showDemoParkSelector() {
  const currentDemoParkName = uni.getStorageSync(CURRENT_DEMO_PARK_NAME_KEY) || ''

  const itemList = [
    '演示命中：灵山胜境',
    '演示命中：拈花湾禅意小镇',
    currentDemoParkName ? `清除演示景区（当前：${currentDemoParkName}）` : '清除演示景区'
  ]

  uni.showActionSheet({
    itemList,
    success: (res) => {
      const index = res.tapIndex

      if (index === 0 || index === 1) {
        setCurrentDemoPark(DEMO_PARK_OPTIONS[index])
        return
      }

      clearCurrentDemoPark()
    }
  })
}

function setCurrentDemoPark(park) {
  const normalizedArea = normalizeArea(park || {})

  if (!normalizedArea.areaId) {
    uni.showToast({
      title: '演示景区ID异常',
      icon: 'none'
    })
    return
  }

  uni.setStorageSync(CURRENT_DEMO_PARK_ID_KEY, normalizedArea.parkId || normalizedArea.areaId)
  uni.setStorageSync(CURRENT_DEMO_PARK_NAME_KEY, normalizedArea.parkName || normalizedArea.areaName)
  triggerDemoScenicHit(normalizedArea)
}

function clearCurrentDemoPark() {
  uni.removeStorageSync(CURRENT_DEMO_PARK_ID_KEY)
  uni.removeStorageSync(CURRENT_DEMO_PARK_NAME_KEY)
  applyOutsideLocation('当前未进入景区')

  uni.showToast({
    title: '已清除演示景区',
    icon: 'none'
  })
}

async function triggerDemoScenicHit(park) {
  await syncVisitState()

  if (visitState.value.hasRunningVisit || getCurrentVisitId()) {
    uni.showToast({
      title: '当前已有进行中的现场导览',
      icon: 'none'
    })
    return
  }

  handleScenicHit(buildAreaFromDemoPark(park), 'demo')
}

function handleDemoEnterScenic() {
  triggerDemoScenicHit(DEMO_PARK_OPTIONS[0])
}

function handleGuideEntry() {
  if (visitState.value.hasRunningVisit) {
    handleContinueCurrentVisit()
    return
  }

  if (reportState.value.hasReport) {
    goVisitReport(reportState.value.reportVisitId)
    return
  }

  if (locationState.value.isInsideScenic) {
    goVisitStart(locationState.value.hitArea, locationState.value.source)
    return
  }

  goGuide()
}

function handleCheckLocationAgain() {
  uni.showLoading({
    title: '正在定位...'
  })

  refreshLocationDetection({ force: true })
    .then(() => {
      if (locationState.value.isInsideScenic) {
        uni.showToast({
          title: '已识别到当前景区',
          icon: 'none'
        })
      } else {
        uni.showToast({
          title: '当前未进入景区范围',
          icon: 'none'
        })
      }
    })
    .catch(() => {
      uni.showToast({
        title: '定位失败，请稍后重试',
        icon: 'none'
      })
    })
    .finally(() => {
      uni.hideLoading()
    })
}

async function handleContinueCurrentVisit() {
  if (!requireLogin()) {
    return
  }

  const userId = getCurrentUserId()

  if (!isRealLoginUserId(userId)) {
    uni.showToast({
      title: '登录用户信息异常，请重新登录后继续导览',
      icon: 'none'
    })
    return
  }

  const visitId = visitState.value.visitId || currentVisitId.value || uni.getStorageSync('currentVisitId') || getCurrentVisitId()
  const parkId =
    visitState.value.areaId ||
    uni.getStorageSync('currentParkId') ||
    resolveCurrentParkId(onsiteStatus.value) ||
    ''
  const parkName =
    visitState.value.areaName ||
    uni.getStorageSync('currentParkName') ||
    resolveCurrentParkName(onsiteStatus.value) ||
    onsiteParkName.value ||
    ''

  if (!visitId) {
    uni.showToast({
      title: '暂无可继续的现场导览信息，请重新定位',
      icon: 'none'
    })
    refreshCurrentVisitId()
    return
  }

  await openNativeLive2DGuide({
    entry: 'home-continue-onsite-guide',
    userId,
    mode: 'onsite',
    trigger: 'continue-onsite',
    source: NATIVE_LIVE2D_SOURCE,
    visitId,
    contextType: 'park',
    contextName: parkName,
    parkId,
    parkName,
    areaId: parkId,
    areaCode: parkId,
    areaName: parkName,
    scenicId: parkId,
    scenicName: parkName,
    allowEndVisit: true,
    startVisitGuide: false
  })
}

function goCurrentParkMap() {
  const area = normalizeArea({
    areaId: visitState.value.areaId || locationState.value.hitArea?.areaId || resolveCurrentParkId(onsiteStatus.value),
    areaName: visitState.value.areaName || locationState.value.hitArea?.areaName || resolveCurrentParkName(onsiteStatus.value),
    latitude: locationState.value.hitArea?.latitude || onsiteStatus.value?.location?.latitude || '',
    longitude: locationState.value.hitArea?.longitude || onsiteStatus.value?.location?.longitude || ''
  })

  if (!area.areaId) {
    uni.showToast({
      title: '暂无景区地图信息',
      icon: 'none'
    })
    return
  }

  uni.setStorageSync('selectedParkMapContext', JSON.stringify({
    id: area.areaId,
    name: area.areaName,
    latitude: area.latitude || '',
    longitude: area.longitude || '',
    source: 'home-onsite'
  }))

  uni.navigateTo({
    url: `/pages/map/map?${buildPageQuery({
      parkId: area.areaId,
      areaName: area.areaName,
      latitude: area.latitude || '',
      longitude: area.longitude || '',
      source: 'home-onsite'
    })}`
  })
}

function formatShortDesc(desc, maxLength = 44) {
  if (!desc) return '暂无景区简介'

  const text = String(desc)
    .replace(/\s+/g, '')
    .replace(/&nbsp;/g, '')
    .trim()

  if (!text) return '暂无景区简介'

  return text.length > maxLength ? text.slice(0, maxLength) + '...' : text
}

function getParkImage(item) {
  if (!item) return ''

  const id = item.id || item.name || ''
  if (id && imageErrorMap.value[id]) {
    return ''
  }

  return resolveImageUrl(
    item.imageUrl ||
    item.image_url ||
    item.coverUrl ||
    item.cover ||
    ''
  )
}

function handleParkImageError(item) {
  const id = item?.id || item?.name || ''
  if (!id) return

  imageErrorMap.value = {
    ...imageErrorMap.value,
    [id]: true
  }
}

function getCoverText(item) {
  const name = String(item?.name || '景区').trim()
  return name.slice(0, 2)
}

function buildDefaultDigitalHumanConfig(targetName = '') {
  return {
    avatarId: 'guide_female_01',
    avatarName: '灵灵',
    clothesMode: '',
    voiceId: 'zhitian_emo',
    voiceName: '知甜',
    welcomeText: targetName
      ? `你好，我是你的AI数字人导游。现在为你讲解“${targetName}”。`
      : '你好，我是你的AI数字人导游，很高兴为你服务。'
  }
}

function normalizeDigitalHumanConfig(source = {}, targetName = '') {
  const raw =
    source?.digitalHumanConfig ||
    source?.digital_human_config ||
    source?.digitalHuman ||
    source?.digital_human ||
    source ||
    {}

  const avatarId = raw.avatarId || raw.avatar_id || ''
  const avatarName = raw.avatarName || raw.avatar_name || ''
  const clothesMode =
    raw.clothesMode ||
    raw.clothes_mode ||
    raw.avatarClothesMode ||
    raw.avatar_clothes_mode ||
    ''
  const voiceId = raw.voiceId || raw.voice_id || raw.voice || ''
  const voiceName = raw.voiceName || raw.voice_name || ''
  const welcomeText = raw.welcomeText || raw.welcome_text || ''

  const hasConfig =
    avatarId ||
    avatarName ||
    clothesMode ||
    voiceId ||
    voiceName ||
    welcomeText

  if (!hasConfig) {
    return buildDefaultDigitalHumanConfig(targetName)
  }

  const fallback = buildDefaultDigitalHumanConfig(targetName)

  return {
    avatarId: avatarId || fallback.avatarId,
    avatarName: avatarName || fallback.avatarName,
    clothesMode: clothesMode || fallback.clothesMode,
    voiceId: voiceId || fallback.voiceId,
    voiceName: voiceName || fallback.voiceName,
    welcomeText: welcomeText || fallback.welcomeText
  }
}

function loadAppSettings() {
  const cache = uni.getStorageSync(SETTINGS_KEY)

  if (!cache) {
    showHomeNotice.value = defaultAppSettings.showHomeNotice
    return
  }

  try {
    const parsed = typeof cache === 'string' ? JSON.parse(cache) : cache
    showHomeNotice.value = parsed?.showHomeNotice !== false
  } catch (e) {
    showHomeNotice.value = defaultAppSettings.showHomeNotice
  }
}

function requestGet(url) {
  return new Promise((resolve, reject) => {
    uni.request({
      url,
      method: 'GET',
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data)
        } else {
          reject(new Error(`HTTP ${res.statusCode}`))
        }
      },
      fail: (err) => reject(err)
    })
  })
}

async function fetchParks() {
  parkLoading.value = true
  imageErrorMap.value = {}

  try {
    const hotRes = await requestGet(HOT_PARKS_API)
    const hotData = hotRes?.data || []
    const hotList = Array.isArray(hotData) ? hotData : []

    parkList.value = hotList.slice(0, 2).map(item => {
      return {
        ...item,
        imageUrl:
          item.imageUrl ||
          item.image_url ||
          item.coverUrl ||
          item.cover ||
          ''
      }
    })
  } catch (error) {
    console.log('热门景区加载失败：', error)
    parkList.value = []
    uni.showToast({
      title: '热门景区加载失败',
      icon: 'none'
    })
  } finally {
    parkLoading.value = false
  }
}

async function fetchNotices() {
  if (!showHomeNotice.value) return

  noticeLoading.value = true
  try {
    const res = await requestGet(NOTICES_API)
    const data = res?.data || []
    if (Array.isArray(data) && data.length > 0) {
      noticeList.value = data
    }
  } catch (error) {
    console.log('首页公告加载失败：', error)
  } finally {
    noticeLoading.value = false
  }
}

function handleSearch() {
  uni.setStorageSync(SCENIC_LIST_MODE_KEY, 'all')
  uni.switchTab({ url: '/pages/scenic/scenic' })
}

function clearNormalGuideContext() {
  uni.setStorageSync('selectedScenicName', '通用导览')
  uni.removeStorageSync('selectedScenicId')
  uni.setStorageSync('aiContextType', 'general')
  uni.setStorageSync('aiContextName', '通用导览')
  uni.removeStorageSync('aiAutoQuestion')
  uni.removeStorageSync('aiContext')
}

function openNativeGuideSafely(options = {}) {
  if (openingNativeGuide.value) {
    return
  }

  openingNativeGuide.value = true
  clearNativeGuideReturnResidue()

  try {
    openNativeLive2DGuide(options)
  } finally {
    setTimeout(() => {
      openingNativeGuide.value = false
    }, 1500)
  }
}

function openGeneralGuide() {
  clearNormalGuideContext()

  const digitalHumanConfig = buildDefaultDigitalHumanConfig('通用导览')

  openNativeGuideSafely({
    entry: 'home-ai-assistant',
    source: NATIVE_LIVE2D_SOURCE,
    scenicName: '通用导览',
    scenicId: '',
    contextType: 'general',
    contextName: '通用导览',
    autoQuestion: '',
    mode: 'normal',
    trigger: 'home-ai-assistant',
    allowEndVisit: false,
    startVisitGuide: false,

    avatarId: digitalHumanConfig.avatarId,
    avatarName: digitalHumanConfig.avatarName,
    clothesMode: digitalHumanConfig.clothesMode,
    voiceId: digitalHumanConfig.voiceId,
    voiceName: digitalHumanConfig.voiceName,
    welcomeText: digitalHumanConfig.welcomeText
  })
}

function goGuide() {
  openGeneralGuide()
}

function goParkList() {
  uni.setStorageSync(SCENIC_LIST_MODE_KEY, 'hot')
  uni.switchTab({ url: '/pages/scenic/scenic' })
}

function goHelp() {
  uni.navigateTo({
    url: '/pages/help/help'
  })
}

function showVisitorNotice() {
  uni.showModal({
    title: '即境导览须知',
    content:
      '1. 即境提供“游览前规划、现场数字人导览、游览后报告生成”的完整智慧导览流程\n' +
      '2. 游览前可使用 AI 助手查询景区、了解景点、咨询路线和做行前规划\n' +
      '3. 到达景区后，APP 可结合 GPS/NFC 或演示模式识别当前位置并开启现场导览\n' +
      '4. 现场导览中，AI 数字人支持景点讲解、语音问答、路线推荐和游客服务咨询\n' +
      '5. 普通景点讲解不会自动弹出路线卡片，只有明确提出推荐路线或规划路线时才生成路线建议\n' +
      '6. 结束导览后，系统会汇总景点停留、AI 对话、路线记录和消费记录，生成本次游玩报告',
    confirmText: '我知道了',
    showCancel: false
  })
}

function isRealLoginUserId(userId) {
  const text = String(userId || '').trim()

  if (!text) return false
  if (text === 'anonymous') return false
  if (text.startsWith('visitor_')) return false
  if (text.startsWith('android-live2d-')) return false

  // 后端 AppUserService.register() 生成的登录业务 user_id 就是 tourist_xxx，不能拦截。
  return true
}

function handleScanPay() {
  if (!requireLogin()) {
    return
  }

  if (!isRealLoginUserId(getCurrentUserId())) {
    uni.showToast({
      title: '请使用登录账号支付',
      icon: 'none'
    })
    return
  }

  // #ifdef APP-PLUS
  uni.scanCode({
    onlyFromCamera: true,
    scanType: ['qrCode'],
    success: res => {
      const result = String(res?.result || '').trim()

      if (!result) {
        uni.showToast({
          title: '未识别到二维码内容',
          icon: 'none'
        })
        return
      }

      uni.navigateTo({
        url: `/pages/pay/simulate?qr=${encodeURIComponent(result)}&source=home`
      })
    },
    fail: error => {
      console.log('扫码失败：', error)
      uni.showToast({
        title: '扫码失败或已取消',
        icon: 'none'
      })
    }
  })
  // #endif

  // #ifndef APP-PLUS
  uni.showModal({
    title: '请在APP中扫码',
    content: 'H5浏览器环境下扫码能力不稳定，请在真机APP中扫描管理员端收款二维码。',
    showCancel: false,
    confirmText: '知道了'
  })
  // #endif
}

function handleParkClick(park) {
  uni.setStorageSync('selectedPark', JSON.stringify(park))
  uni.navigateTo({ url: `/pages/park/detail?id=${park.id}` })
}

function openAiExplainPark(park) {
  const parkName = park.name || '当前景区'
  const parkId = park.id || park.areaCode || park.area_code || ''
  const areaId = park.areaId || park.area_id || parkId
  const areaCode = park.areaCode || park.area_code || parkId
  const autoQuestion = `请为我讲解「${parkName}」这个景区，重点介绍景区特色、文化背景、代表景点和参观注意事项。`
  const digitalHumanConfig = normalizeDigitalHumanConfig(park, parkName)

  uni.setStorageSync('selectedScenicName', parkName)
  uni.setStorageSync('selectedScenicId', parkId)
  uni.setStorageSync('aiContextType', 'park')
  uni.setStorageSync('aiContextName', parkName)
  uni.setStorageSync('aiAutoQuestion', autoQuestion)

  uni.setStorageSync('aiContext', JSON.stringify({
    page: 'home_park_card',
    areaId,
    areaCode,
    areaName: parkName,
    parkId,
    parkName,
    scenicId: parkId,
    scenicName: parkName,
    currentSpotId: '',
    currentSpotName: '',
    source: NATIVE_LIVE2D_SOURCE,
    mode: 'scenic_explain',
    trigger: 'home-scenic-explain',
    digitalHumanConfig
  }))

  openNativeGuideSafely({
    entry: 'home-park-explain',
    source: NATIVE_LIVE2D_SOURCE,
    scenicName: parkName,
    scenicId: parkId,
    contextType: 'park',
    contextName: parkName,
    parkId,
    parkName,
    areaId,
    areaCode,
    areaName: parkName,
    autoQuestion,
    mode: 'scenic_explain',
    trigger: 'home-scenic-explain',
    allowEndVisit: false,
    startVisitGuide: false
  })
}

function goAiExplainPark(park) {
  openAiExplainPark(park)
}

function goParkListAll() {
  uni.setStorageSync(SCENIC_LIST_MODE_KEY, 'all')
  uni.switchTab({ url: '/pages/scenic/scenic' })
}

function handleQuickAction(item) {
  switch (item.action) {
    case 'scan_pay':
      handleScanPay()
      break
    case 'park':
      goParkListAll()
      break
    case 'visitor_notice':
      showVisitorNotice()
      break
    case 'help':
      goHelp()
      break
    default:
      uni.showToast({
        title: '功能开发中',
        icon: 'none'
      })
  }
}

function handleNoticeMore() {
  uni.showToast({ title: '公告活动内容敬请期待', icon: 'none' })
}

function handleNoticeClick(item) {
  uni.showToast({
    title: item.title || '公告活动内容敬请期待',
    icon: 'none'
  })
}

function trackHomeView() {
  trackUserBehavior('view_home', {
    eventName: 'view_home',
    entityType: 'PAGE',
    entityId: 'pages/index/index',
    sourcePage: 'pages/index/index',
    extra: {
      sourcePage: 'pages/index/index'
    }
  })
}

onMounted(() => {
  markHomeStableState()
  loadAppSettings()
  refreshOnsiteStatus()
  refreshGuideStateFromLocal()
  refreshHomeRuntimeState({ force: true })
  fetchParks()
  fetchNotices()
})

onShow(() => {
  markHomeStableState()
  loadAppSettings()
  refreshHomeRuntimeState({ force: true })
  trackHomeView()
})
</script>

<style>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f5f7fb 0%, #eef4ff 100%);
  padding: 24rpx;
  box-sizing: border-box;
}

.card {
  background: #ffffff;
  border-radius: 28rpx;
  box-shadow: 0 12rpx 28rpx rgba(15, 23, 42, 0.06);
}

.status-card {
  padding: 28rpx 24rpx;
  margin-bottom: 22rpx;
  text-align: center;
}

.status-text {
  font-size: 24rpx;
  color: #6b7280;
}

.hero {
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #2f80ed 0%, #56ccf2 100%);
  border-radius: 32rpx;
  padding: 30rpx;
  margin-bottom: 24rpx;
  color: #ffffff;
}

.hero-bg-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
}

.hero-bg-circle-1 {
  width: 240rpx;
  height: 240rpx;
  right: -40rpx;
  top: -30rpx;
}

.hero-bg-circle-2 {
  width: 180rpx;
  height: 180rpx;
  right: 120rpx;
  bottom: -60rpx;
}

.hero-top {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.hero-left {
  flex: 1;
  padding-right: 20rpx;
}

.hero-title {
  font-size: 46rpx;
  font-weight: 800;
  line-height: 1.35;
  letter-spacing: 4rpx;
}

.hero-subtitle {
  margin-top: 14rpx;
  font-size: 25rpx;
  line-height: 1.7;
  opacity: 0.96;
}

.hero-search {
  position: relative;
  z-index: 1;
  margin-top: 28rpx;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 22rpx;
  padding: 22rpx 24rpx;
  display: flex;
  align-items: center;
}

.hero-search-icon {
  font-size: 28rpx;
  margin-right: 12rpx;
}

.hero-search-text {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.96);
}

.hero-stats {
  position: relative;
  z-index: 1;
  display: flex;
  margin-top: 26rpx;
  gap: 18rpx;
}

.stat-item {
  flex: 1;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 20rpx;
  padding: 18rpx 12rpx;
  text-align: center;
}

.stat-value {
  font-size: 34rpx;
  font-weight: 700;
}

.stat-label {
  margin-top: 8rpx;
  font-size: 22rpx;
  opacity: 0.95;
}

.location-status-card {
  padding: 24rpx;
  margin-bottom: 24rpx;
}

.location-status-title {
  font-size: 26rpx;
  font-weight: 700;
  color: #1f2937;
  line-height: 1.5;
}

.location-status-text {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #4b5563;
  line-height: 1.6;
}

.location-status-meta {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #18b368;
}

.location-actions {
  margin-top: 18rpx;
  display: flex;
  justify-content: flex-end;
}

.location-demo-btn {
  height: 56rpx;
  line-height: 56rpx;
  padding: 0 22rpx;
  border-radius: 999rpx;
  background: #eff6ff;
  color: #2f80ed;
  font-size: 23rpx;
  font-weight: 600;
}

.guide-entry-card {
  margin-bottom: 26rpx;
  padding: 26rpx 24rpx;
  border: 2rpx solid rgba(47, 128, 237, 0.08);
}

.guide-entry-card.onsite {
  background: linear-gradient(135deg, #ecfdf5 0%, #ffffff 58%, #eff6ff 100%);
  border-color: rgba(24, 179, 104, 0.22);
}

.guide-entry-main {
  display: flex;
  align-items: flex-start;
}

.guide-entry-avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #2f80ed 0%, #56ccf2 100%);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  font-weight: 700;
  flex-shrink: 0;
}

.guide-entry-card.onsite .guide-entry-avatar {
  background: linear-gradient(135deg, #18b368 0%, #4cd7a3 100%);
}

.guide-entry-info {
  flex: 1;
  margin-left: 20rpx;
}

.guide-entry-top {
  display: flex;
  align-items: center;
}

.guide-entry-tag {
  display: inline-flex;
  align-items: center;
  padding: 7rpx 16rpx;
  border-radius: 999rpx;
  background: #eff6ff;
  color: #2f80ed;
  font-size: 22rpx;
  font-weight: 600;
}

.guide-entry-card.onsite .guide-entry-tag {
  background: #dcfce7;
  color: #18b368;
}

.guide-entry-title {
  margin-top: 12rpx;
  font-size: 32rpx;
  font-weight: 700;
  color: #1f2937;
  line-height: 1.45;
}

.guide-entry-subtitle {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #374151;
  line-height: 1.6;
}

.guide-entry-desc {
  margin-top: 6rpx;
  font-size: 23rpx;
  color: #6b7280;
  line-height: 1.6;
}

.guide-entry-distance {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #18b368;
}

.guide-entry-actions {
  margin-top: 22rpx;
  display: flex;
  gap: 16rpx;
}

.guide-primary-btn {
  height: 64rpx;
  line-height: 64rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  text-align: center;
}

.guide-primary-btn {
  flex: 1;
  background: #2f80ed;
  color: #ffffff;
}

.guide-entry-card.onsite .guide-primary-btn {
  background: #18b368;
}

.guide-secondary-btn {
  min-width: 160rpx;
  height: 64rpx;
  line-height: 64rpx;
  padding: 0 22rpx;
  border-radius: 999rpx;
  background: #f3f4f6;
  color: #374151;
  font-size: 24rpx;
  text-align: center;
  box-sizing: border-box;
}

.trip-info-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 99;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: flex-end;
  padding: 28rpx;
  box-sizing: border-box;
}

.trip-info-dialog {
  width: 100%;
  max-height: 86vh;
  overflow-y: auto;
  background: #ffffff;
  border-radius: 28rpx 28rpx 18rpx 18rpx;
  padding: 30rpx 26rpx 26rpx;
  box-sizing: border-box;
}

.trip-info-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #1f2937;
  line-height: 1.45;
}

.trip-info-subtitle {
  margin-top: 8rpx;
  font-size: 23rpx;
  color: #6b7280;
  line-height: 1.6;
}

.trip-info-field {
  margin-top: 24rpx;
}

.trip-info-label {
  font-size: 25rpx;
  font-weight: 600;
  color: #374151;
}

.trip-info-options {
  margin-top: 14rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.trip-info-chip {
  min-width: 132rpx;
  height: 56rpx;
  line-height: 56rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  background: #f3f4f6;
  color: #4b5563;
  font-size: 23rpx;
  text-align: center;
  box-sizing: border-box;
}

.trip-info-chip.active {
  background: #18b368;
  color: #ffffff;
  font-weight: 600;
}

.trip-info-actions {
  margin-top: 30rpx;
  display: flex;
  gap: 16rpx;
}

.trip-info-cancel,
.trip-info-submit {
  flex: 1;
  height: 66rpx;
  line-height: 66rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  text-align: center;
  font-weight: 600;
}

.trip-info-cancel {
  background: #f3f4f6;
  color: #4b5563;
}

.trip-info-submit {
  background: #18b368;
  color: #ffffff;
}

.trip-info-submit.disabled {
  opacity: 0.62;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 6rpx 0 16rpx;
}

.section-title {
  font-size: 31rpx;
  font-weight: 700;
  color: #1f2937;
}

.section-more {
  font-size: 23rpx;
  color: #9ca3af;
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14rpx;
  margin: 18rpx 0 26rpx;
}

.service-item {
  background: #fff;
  border-radius: 22rpx;
  padding: 22rpx 0;
  text-align: center;
  box-shadow: 0 8rpx 20rpx rgba(15, 23, 42, 0.04);
}

.service-icon {
  font-size: 34rpx;
}

.service-name {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #374151;
}

.scenic-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.scenic-card {
  display: flex;
  overflow: hidden;
}

.scenic-cover {
  width: 160rpx;
  min-height: 160rpx;
  background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
  position: relative;
  flex-shrink: 0;
  overflow: hidden;
}

.scenic-cover-image {
  width: 160rpx;
  height: 100%;
  min-height: 160rpx;
  display: block;
}

.scenic-cover-placeholder {
  width: 160rpx;
  height: 100%;
  min-height: 160rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
}

.scenic-cover-placeholder-text {
  font-size: 34rpx;
  font-weight: 700;
  color: #2f80ed;
}

.scenic-cover-mask {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 70rpx;
  background: linear-gradient(
    180deg,
    rgba(15, 23, 42, 0) 0%,
    rgba(15, 23, 42, 0.25) 100%
  );
  pointer-events: none;
}

.scenic-tag {
  position: absolute;
  left: 14rpx;
  top: 14rpx;
  background: rgba(255, 255, 255, 0.92);
  color: #2f80ed;
  font-size: 21rpx;
  padding: 7rpx 13rpx;
  border-radius: 999rpx;
  z-index: 2;
}

.scenic-content {
  flex: 1;
  padding: 18rpx 20rpx;
}

.scenic-name {
  font-size: 28rpx;
  font-weight: 700;
  color: #1f2937;
  line-height: 1.35;
}

.scenic-desc {
  margin-top: 8rpx;
  font-size: 23rpx;
  color: #6b7280;
  line-height: 1.48;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.scenic-meta {
  margin-top: 9rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

.scenic-meta-item {
  font-size: 21rpx;
  color: #9ca3af;
}

.scenic-tags {
  margin-top: 10rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

.chip {
  font-size: 21rpx;
  color: #2f80ed;
  background: #eff6ff;
  padding: 7rpx 13rpx;
  border-radius: 999rpx;
}

.scenic-actions {
  margin-top: 12rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

.small-btn {
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: #f3f4f6;
  color: #374151;
  font-size: 21rpx;
}

.small-btn.primary {
  background: #18b368;
  color: #ffffff;
}

.notice-card {
  padding: 22rpx 24rpx;
  margin-bottom: 30rpx;
}

.notice-item {
  display: flex;
  align-items: flex-start;
  padding: 12rpx 0;
}

.notice-dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #2f80ed;
  margin-top: 12rpx;
  margin-right: 14rpx;
  flex-shrink: 0;
}

.notice-main {
  flex: 1;
}

.notice-title {
  font-size: 26rpx;
  color: #1f2937;
  font-weight: 600;
  line-height: 1.6;
}

.notice-desc {
  margin-top: 6rpx;
  font-size: 23rpx;
  color: #6b7280;
  line-height: 1.6;
}
</style>
