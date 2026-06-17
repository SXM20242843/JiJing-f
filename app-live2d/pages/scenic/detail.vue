<template>
  <view class="page">
    <view v-if="loading" class="status-box">
      <text class="status-text">正在加载景点详情...</text>
    </view>

    <template v-else>
      <view class="hero-card">
        <view class="hero-cover">
          <image
            v-if="getScenicImage()"
            class="hero-cover-image"
            :src="getScenicImage()"
            mode="aspectFill"
            @error="handleScenicImageError"
          />
        
          <view v-else class="hero-cover-placeholder">
            <text class="hero-cover-placeholder-text">
              {{ getCoverText(scenic) }}
            </text>
          </view>
        
          <view class="hero-cover-mask"></view>
        
          <view class="hero-badge">{{ scenic.badge || '推荐景点' }}</view>
        </view>

        <view class="hero-content">
          <view class="hero-title">{{ scenic.name || '景点详情' }}</view>
          <view class="hero-desc">{{ scenic.desc || '暂无景点简介' }}</view>

          <view class="hero-meta">
            <view class="meta-pill">开放时间：{{ scenic.time || '暂无' }}</view>
            <view class="meta-pill">推荐时长：{{ scenic.duration || '暂无' }}</view>
          </view>
        </view>
      </view>

      <view class="section-card">
        <view class="section-title">景点介绍</view>
        <view class="section-text">
          {{ scenic.introduction || '暂无景点介绍' }}
        </view>
      </view>

      <view class="section-card">
        <view class="section-title">游玩亮点</view>
        <view class="highlight-list" v-if="scenic.highlights && scenic.highlights.length">
          <view class="highlight-item" v-for="item in scenic.highlights" :key="item">
            <view class="highlight-dot"></view>
            <view class="highlight-text">{{ item }}</view>
          </view>
        </view>
        <view v-else class="section-text">暂无游玩亮点</view>
      </view>

      <view class="section-card">
        <view class="section-title">温馨提示</view>
        <view class="tips-list" v-if="scenic.tips && scenic.tips.length">
          <view class="tips-item" v-for="item in scenic.tips" :key="item">
            {{ item }}
          </view>
        </view>
        <view v-else class="section-text">暂无温馨提示</view>
      </view>

      <view class="section-card" v-if="scenic.tags && scenic.tags.length">
        <view class="section-title">标签信息</view>
        <view class="tag-row">
          <text class="tag-chip" v-for="tag in scenic.tags" :key="tag">
            {{ tag }}
          </text>
        </view>
      </view>

      <view class="action-bar">
        <button
          class="action-btn"
          :class="{ collected: isFavorite }"
          @click="handleCollect"
        >
          {{ isFavorite ? '已收藏' : '收藏景点' }}
        </button>
        <button class="action-btn secondary" @click="handleRecommendRoute">
          推荐路线
        </button>
        <button class="action-btn primary" @click="handleAiExplain">
          AI讲解
        </button>
      </view>
    </template>

    <TripInfoPopup
      :show="showTripInfoPopup"
      @cancel="handleTripInfoCancel"
      @confirm="handleTripInfoConfirm"
    />
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { API_BASE, NATIVE_LIVE2D_SOURCE } from '../../utils/api'
import { resolveImageUrl } from '../../utils/image'
import { openNativeLive2DGuide } from '../../utils/openNativeLive2D.js'
import { resolveRouteVisitLocationStatus } from '../../common/location-utils.js'
import TripInfoPopup from '@/components/TripInfoPopup.vue'
import {
  addFavorite,
  removeFavorite,
  getFavoriteStatus
} from '@/utils/favorite'
import { trackUserBehavior } from '@/utils/behavior'
import {
  useTripInfoConfirm,
  logTripInfoSelection
} from '@/utils/visit'

const GUIDE_CONVERSATION_ID_KEY = 'guideConversationId'
const FAVORITE_KEY = 'favoriteScenics'

const loading = ref(false)
const isFavorite = ref(false)
const favoriteLoading = ref(false)
const imageLoadError = ref(false)
const routeParkId = ref('')
const {
  showTripInfoPopup,
  openTripInfoConfirm,
  cancelTripInfoConfirm,
  consumePendingTripInfoAction
} = useTripInfoConfirm()

const scenic = ref({
  id: '',
  sceneCode: '',
  scene_code: '',
  parkId: '',
  parkName: '',
  areaCode: '',
  area_code: '',
  name: '',
  desc: '',
  time: '',
  duration: '',
  badge: '',
  tags: [],
  introduction: '',
  highlights: [],
  tips: [],
  imageUrl: '',
  heat: '',
  digitalHumanConfig: null
})

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

function getCoverText(item) {
  const name = String(item?.name || '景点').trim()
  return name.slice(0, 2)
}

function getScenicImage() {
  if (imageLoadError.value) return ''

  return resolveImageUrl(
    scenic.value.imageUrl ||
    scenic.value.image_url ||
    scenic.value.coverUrl ||
    scenic.value.cover ||
    ''
  )
}

function handleScenicImageError() {
  imageLoadError.value = true
}

async function fetchScenicDetail(id) {
  const res = await requestGet(`${API_BASE}/api/app/scenics/${id}`)
  const data = res?.data || {}
  const cacheDetail = getSelectedScenicDetail()
  
  imageLoadError.value = false

  scenic.value = {
    id: data.id || cacheDetail.id || '',
    sceneCode: data.sceneCode || data.scene_code || cacheDetail.sceneCode || cacheDetail.scene_code || data.id || cacheDetail.id || '',
    scene_code: data.scene_code || data.sceneCode || cacheDetail.scene_code || cacheDetail.sceneCode || data.id || cacheDetail.id || '',
    parkId: data.parkId || data.areaCode || data.area_code || cacheDetail.parkId || cacheDetail.areaCode || cacheDetail.area_code || '',
    parkName: data.parkName || data.areaName || data.area_name || cacheDetail.parkName || cacheDetail.areaName || cacheDetail.area_name || '',
    areaCode: data.areaCode || data.area_code || data.parkId || cacheDetail.areaCode || cacheDetail.area_code || cacheDetail.parkId || '',
    area_code: data.area_code || data.areaCode || data.parkId || cacheDetail.area_code || cacheDetail.areaCode || cacheDetail.parkId || '',
    name: data.name || cacheDetail.name || '',
    desc: data.desc || cacheDetail.desc || '',
    time: data.time || cacheDetail.time || '',
    duration: data.duration || cacheDetail.duration || '',
    badge: data.badge || cacheDetail.badge || '推荐',
    tags: Array.isArray(data.tags) ? data.tags : (Array.isArray(cacheDetail.tags) ? cacheDetail.tags : []),
    introduction: data.introduction || cacheDetail.introduction || '',
    highlights: Array.isArray(data.highlights) ? data.highlights : (Array.isArray(cacheDetail.highlights) ? cacheDetail.highlights : []),
    tips: Array.isArray(data.tips) ? data.tips : (Array.isArray(cacheDetail.tips) ? cacheDetail.tips : []),
    imageUrl: data.imageUrl || data.image_url || cacheDetail.imageUrl || cacheDetail.image_url || '',
    heat: data.heat || cacheDetail.heat || '',
	digitalHumanConfig:
	  data.digitalHumanConfig ||
	  data.digital_human_config ||
	  cacheDetail.digitalHumanConfig ||
	  cacheDetail.digital_human_config ||
	  null
  }
}

async function initPage(id) {
  if (!id) {
    uni.showToast({
      title: '缺少景点标识',
      icon: 'none'
    })
    return
  }

  loading.value = true

  try {
    await fetchScenicDetail(id)
    await loadFavoriteStatus()
    trackScenicView()
  } catch (error) {
    console.log('景点详情加载失败：', error)
    uni.showToast({
      title: '景点详情加载失败',
      icon: 'none'
    })
  } finally {
    loading.value = false
  }
}

function safeParseJson(value, fallback = null) {
  if (!value) return fallback
  if (typeof value === 'object') return value

  try {
    return JSON.parse(value)
  } catch (e) {
    return fallback
  }
}

function safeParseArray(value) {
  if (!value) return []

  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return []
  }
}

function getSelectedScenicDetail() {
  const cache = uni.getStorageSync('selectedScenicDetail')
  return safeParseJson(cache, {}) || {}
}

function getParkContext() {
  const cache = uni.getStorageSync('selectedParkContext')
  const parkContext = safeParseJson(cache, {}) || {}

  if (!parkContext.name && scenic.value.parkName) {
    parkContext.name = scenic.value.parkName
  }

  if (!parkContext.id && scenic.value.parkId) {
    parkContext.id = scenic.value.parkId
  }

  if (!parkContext.id && routeParkId.value) {
    parkContext.id = routeParkId.value
  }

  return parkContext
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
    return null
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

function getScenicDigitalHumanConfig() {
  const scenicName = scenic.value.name || '当前景点'
  const parkContext = getParkContext()

  return (
    normalizeDigitalHumanConfig(scenic.value.digitalHumanConfig, scenicName) ||
    normalizeDigitalHumanConfig(parkContext.digitalHumanConfig, scenicName) ||
    buildDefaultDigitalHumanConfig(scenicName)
  )
}

function getFavoritePayload() {
  return {
    target_type: 'SPOT',

    // 这里不要直接把 scenic.id 当 target_id。
    // 你的 app 里 scenic.id 很可能是 scene_code。
    // 后端会根据 scene_code 去 scenic_spot 表查真正的 id。
    target_id: scenic.value.targetId || scenic.value.target_id || scenic.value.dbId || null,

    scene_code:
      scenic.value.scene_code ||
      scenic.value.sceneCode ||
      scenic.value.id ||
      '',

    area_code:
      scenic.value.area_code ||
      scenic.value.areaCode ||
      scenic.value.parkId ||
      '',

    name: scenic.value.name || '',
    desc: scenic.value.desc || '',
    parkId: scenic.value.parkId || '',
    parkName: scenic.value.parkName || '',
    time: scenic.value.time || '',
    duration: scenic.value.duration || '',
    tags: Array.isArray(scenic.value.tags) ? scenic.value.tags : [],
    introduction: scenic.value.introduction || ''
  }
}

async function loadFavoriteStatus() {
  try {
    isFavorite.value = await getFavoriteStatus(getFavoritePayload())
  } catch (err) {
    console.warn('获取收藏状态失败：', err)
    isFavorite.value = isLocalFavorite()
  }
}

function getLocalFavoriteList() {
  const cache = uni.getStorageSync(FAVORITE_KEY)
  return safeParseArray(cache)
}

function saveLocalFavoriteList(list) {
  uni.setStorageSync(FAVORITE_KEY, JSON.stringify(list))
}

function isLocalFavorite() {
  const payload = getFavoritePayload()
  const list = getLocalFavoriteList()

  return list.some(item => {
    const itemId = item.id || item.sceneCode || item.scene_code || ''
    return itemId && itemId === payload.scene_code
  })
}

function addLocalFavorite() {
  const payload = getFavoritePayload()
  const list = getLocalFavoriteList()

  const exists = list.some(item => {
    const itemId = item.id || item.sceneCode || item.scene_code || ''
    return itemId && itemId === payload.scene_code
  })

  if (exists) return

  list.unshift({
    id: payload.scene_code,
    sceneCode: payload.scene_code,
    scene_code: payload.scene_code,
    name: payload.name,
    desc: payload.desc,
    parkId: payload.parkId,
    parkName: payload.parkName,
    time: payload.time,
    duration: payload.duration,
    tags: payload.tags,
    introduction: payload.introduction
  })

  saveLocalFavoriteList(list)
}

function removeLocalFavorite() {
  const payload = getFavoritePayload()
  const list = getLocalFavoriteList()

  const nextList = list.filter(item => {
    const itemId = item.id || item.sceneCode || item.scene_code || ''
    return itemId !== payload.scene_code
  })

  saveLocalFavoriteList(nextList)
}

function buildScenicBehaviorPayload(extra = {}) {
  const payload = getFavoritePayload()
  const sceneCode = payload.scene_code || scenic.value.sceneCode || scenic.value.id || ''
  const parkId = payload.area_code || scenic.value.parkId || ''
  const parkName = scenic.value.parkName || ''
  const scenicId = scenic.value.id || sceneCode
  const scenicName = scenic.value.name || ''

  return {
    entityType: 'SPOT',
    entityId: sceneCode || scenicId || 'scenic-detail',
    areaCode: parkId,
    sceneCode,
    sourcePage: 'pages/scenic/detail',
    parkId,
    parkName,
    extra: {
      parkId,
      parkName,
      scenicId,
      scenicName,
      sceneCode,
      sourcePage: 'pages/scenic/detail',
      ...extra
    }
  }
}

function trackScenicView() {
  trackUserBehavior('view_scenic_detail', {
    ...buildScenicBehaviorPayload(),
    eventName: 'view_scenic_detail'
  })
}

async function setScenicAiContext(autoQuestion, entry, extraOptions = {}) {
  const scenicName = scenic.value.name || '当前景点'
  const scenicId = scenic.value.id || scenic.value.sceneCode || scenic.value.scene_code || ''
  const sceneCode = scenic.value.scene_code || scenic.value.sceneCode || scenicId
  const parkContext = getParkContext()
  const parkId =
    scenic.value.parkId ||
    scenic.value.park_id ||
    scenic.value.areaId ||
    scenic.value.area_id ||
    parkContext.id ||
    routeParkId.value ||
    ''
  const parkName = parkContext.name || scenic.value.parkName || ''
  const areaId = scenic.value.areaCode || scenic.value.area_code || parkId || ''
  const areaCode = parkId
  const digitalHumanConfig = getScenicDigitalHumanConfig()
  const guideMode = extraOptions.mode || 'spot_explain'
  const trigger = extraOptions.trigger || 'spot-detail-ai-explain'
  const routeLocationStatus = extraOptions.routeVisitStatus
    ? await resolveRouteVisitLocationStatus()
    : {}

  uni.removeStorageSync(GUIDE_CONVERSATION_ID_KEY)

  uni.setStorageSync('selectedScenicName', scenicName)
  uni.setStorageSync('selectedScenicId', scenicId)
  uni.setStorageSync('aiContextType', 'scenic')
  uni.setStorageSync('aiContextName', scenicName)

  uni.setStorageSync('aiContext', JSON.stringify({
    page: 'spot_detail',
    contextType: 'scenic',
    contextName: scenicName,
    areaId,
    areaCode,
    areaName: parkName,
    parkId,
    parkName,
    scenicId,
    scenicName,
    sceneCode,
    currentSpotId: scenicId,
    currentSpotName: scenicName,
    source: NATIVE_LIVE2D_SOURCE,
    mode: guideMode,
    trigger,
    digitalHumanConfig
  }))

  uni.setStorageSync('aiAutoQuestion', autoQuestion)

  openNativeLive2DGuide({
    entry,
    source: NATIVE_LIVE2D_SOURCE,

    contextType: 'scenic',
    contextName: scenicName,
    trackScenicVisit: false,
    enterSource: entry === 'scenic-detail-route' ? 'route_recommend_click' : 'spot_detail_ai_explain',

    scenicName,
    scenicId,

    parkId,
    parkName,
    areaId,
    areaCode,
    areaName: parkName,

    spotId: scenicId,
    spotName: scenicName,
    sceneCode,

    autoQuestion,
    mode: guideMode,
    trigger,
    allowEndVisit: extraOptions.allowEndVisit === true,
    startVisitGuide: extraOptions.startVisitGuide === true,
    ...routeLocationStatus
  })
}

function openAiExplain() {
  const scenicName = scenic.value.name || '当前景点'

  setScenicAiContext(
    `请为我讲解「${scenicName}」这个景点，重点介绍历史背景、看点特色和参观注意事项。`,
    'scenic-detail-explain',
    {
      mode: 'spot_explain',
      trigger: 'spot-detail-ai-explain',
      allowEndVisit: false,
      startVisitGuide: false
    }
  )
}

function handleAiExplain() {
  trackUserBehavior('click_ai_guide', {
    ...buildScenicBehaviorPayload({
      trigger: 'scenic_detail_ai_guide'
    }),
    eventName: 'click_ai_guide'
  })

  openAiExplain()
}

function openRecommendRoute() {
  const scenicName = scenic.value.name || '当前景点'

  trackUserBehavior('route_request', {
    ...buildScenicBehaviorPayload({
      action: 'recommend_route'
    }),
    eventName: 'route_request'
  })

  setScenicAiContext(
    `请为“${scenicName}”推荐一条游览路线`,
    'scenic-detail-route',
    {
      mode: 'route_planning',
      trigger: 'route-planning',
      allowEndVisit: false,
      startVisitGuide: false,
      routeVisitStatus: true
    }
  )
}

function handleRecommendRoute() {
  openTripInfoConfirm(() => openRecommendRoute())
}

function handleTripInfoCancel() {
  cancelTripInfoConfirm()
}

function handleTripInfoConfirm(selection) {
  logTripInfoSelection(selection)

  const action = consumePendingTripInfoAction()

  Promise.resolve(typeof action === 'function' ? action(selection) : null).catch(error => {
    console.log('景点导览打开失败：', error)
  })
}

async function handleCollect() {
  if (favoriteLoading.value) return

  const payload = getFavoritePayload()

  if (!payload.scene_code && !payload.target_id) {
    uni.showToast({
      title: '景点标识缺失，暂无法收藏',
      icon: 'none'
    })
    return
  }

  favoriteLoading.value = true

  try {
    if (isFavorite.value) {
      await removeFavorite(payload)
      removeLocalFavorite()

      isFavorite.value = false

      uni.showToast({
        title: '已取消收藏',
        icon: 'none'
      })
    } else {
      await addFavorite(payload)
      addLocalFavorite()

      isFavorite.value = true

      trackUserBehavior('favorite_scenic', {
        ...buildScenicBehaviorPayload({
          trigger: 'favorite_button'
        }),
        eventName: 'favorite_scenic'
      })

      uni.showToast({
        title: `已收藏：${scenic.value.name}`,
        icon: 'success'
      })
    }
  } catch (err) {
    console.log('收藏操作失败：', err)

    if (err && err.message === '未登录') {
      return
    }

    uni.showToast({
      title: err?.message || '收藏操作失败',
      icon: 'none'
    })
  } finally {
    favoriteLoading.value = false
  }
}

onLoad((options) => {
  const routeId = options?.id || ''
  routeParkId.value = options?.parkId || options?.park_id || ''

  if (routeId) {
    initPage(routeId)
    return
  }

  // 兼容旧缓存方式
  try {
    const cache = uni.getStorageSync('selectedScenicDetail')

    if (cache) {
      const parsed = JSON.parse(cache)

      if (parsed?.id) {
        scenic.value = {
          ...scenic.value,
          ...parsed,
          sceneCode: parsed.sceneCode || parsed.scene_code || parsed.id || '',
          scene_code: parsed.scene_code || parsed.sceneCode || parsed.id || '',
          areaCode: parsed.areaCode || parsed.area_code || parsed.parkId || '',
          area_code: parsed.area_code || parsed.areaCode || parsed.parkId || '',
          tags: Array.isArray(parsed.tags) ? parsed.tags : [],
          highlights: Array.isArray(parsed.highlights) ? parsed.highlights : [],
          tips: Array.isArray(parsed.tips) ? parsed.tips : []
        }

        initPage(parsed.id)
        return
      }

      scenic.value = {
        ...scenic.value,
        ...parsed,
        sceneCode: parsed.sceneCode || parsed.scene_code || parsed.id || '',
        scene_code: parsed.scene_code || parsed.sceneCode || parsed.id || '',
        areaCode: parsed.areaCode || parsed.area_code || parsed.parkId || '',
        area_code: parsed.area_code || parsed.areaCode || parsed.parkId || '',
        tags: Array.isArray(parsed.tags) ? parsed.tags : [],
        highlights: Array.isArray(parsed.highlights) ? parsed.highlights : [],
        tips: Array.isArray(parsed.tips) ? parsed.tips : []
      }

      loadFavoriteStatus()
      trackScenicView()
    }
  } catch (e) {
    console.log('读取景点详情缓存失败', e)
  }
})
</script>

<style>
/* ============================================================
   即境 · 景点详情 — 景点介绍 + AI讲解 + 推荐路线
   设计方向：Clean APP Detail · 统一卡片 + 左蓝竖线标题
   签名元素：border-left 标题 · 3 行省略简介 · 轻量提示条
   本轮只改 CSS，不改 template / script / 任何业务入口
   ============================================================ */

/* ---------- Page ---------- */
.page {
  min-height: 100vh;
  background: #f5f7fb;
  padding: 24rpx 24rpx 160rpx;
  box-sizing: border-box;
}

/* ---------- Status (loading / empty) ---------- */
.status-box {
  background: #ffffff;
  border-radius: 24rpx;
  padding: 80rpx 24rpx 64rpx;
  text-align: center;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
  border: 1rpx solid #f3f4f6;
}

/* 空态视觉锚点 — 装饰圆 */
.status-box::before {
  content: '';
  display: block;
  width: 72rpx;
  height: 72rpx;
  margin: 0 auto 28rpx;
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  border-radius: 50%;
}

.status-text {
  font-size: 26rpx;
  color: #6b7280;
  line-height: 1.7;
}

/* ---------- Hero Card ---------- */
.hero-card {
  background: #ffffff;
  border-radius: 28rpx;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
  border: 1rpx solid #f3f4f6;
  margin-bottom: 24rpx;
  overflow: hidden;
}

/* ---------- Hero Cover (260rpx，不偏高) ---------- */
.hero-cover {
  height: 260rpx;
  background: linear-gradient(160deg, #dbeafe 0%, #bfdbfe 60%, #e0e7ff 100%);
  position: relative;
  overflow: hidden;
}

.hero-cover-image {
  width: 100%;
  height: 260rpx;
  display: block;
}

.hero-cover-placeholder {
  width: 100%;
  height: 260rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #dbeafe 0%, #e0e7ff 50%, #c7d2fe 100%);
}

.hero-cover-placeholder-text {
  font-size: 48rpx;
  font-weight: 800;
  color: #2f80ed;
  opacity: 0.5;
}

/* 底部渐变遮罩 — 高度跟随 cover */
.hero-cover-mask {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 90rpx;
  background: linear-gradient(
    180deg,
    rgba(15, 23, 42, 0) 0%,
    rgba(15, 23, 42, 0.2) 100%
  );
  pointer-events: none;
}

/* 轻量角标 */
.hero-badge {
  position: absolute;
  left: 16rpx;
  top: 16rpx;
  background: rgba(255, 255, 255, 0.88);
  color: #2f80ed;
  font-size: 20rpx;
  font-weight: 600;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  z-index: 2;
  box-shadow: 0 2rpx 6rpx rgba(15, 23, 42, 0.06);
}

/* ---------- Hero Content ---------- */
.hero-content {
  padding: 24rpx;
}

.hero-title {
  font-size: 36rpx;
  font-weight: 700;
  color: #1f2937;
  line-height: 1.3;
}

/* 简介 — 最多 3 行，超出省略 */
.hero-desc {
  margin-top: 10rpx;
  font-size: 26rpx;
  color: #6b7280;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* Meta 行 — 开放时间 / 推荐时长 pill（真机防溢出） */
.hero-meta {
  margin-top: 16rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  overflow: visible;
}

.meta-pill {
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: #eff6ff;
  color: #2f80ed;
  font-size: 20rpx;
  font-weight: 500;
  border: 1rpx solid rgba(47, 128, 237, 0.08);
  white-space: nowrap;
  max-width: 100%;
  box-sizing: border-box;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ---------- Section Card ---------- */
.section-card {
  background: #ffffff;
  border-radius: 28rpx;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
  border: 1rpx solid #f3f4f6;
  margin-bottom: 20rpx;
  padding: 24rpx;
  overflow: hidden;
}

/* 标题 — 左侧蓝色短竖线（参考报告页，用 border-left 不用伪元素） */
.section-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 18rpx;
  padding-left: 18rpx;
  border-left: 6rpx solid #2f80ed;
  line-height: 1.3;
}

/* 正文 */
.section-text {
  font-size: 26rpx;
  color: #4b5563;
  line-height: 1.8;
}

/* ---------- 游玩亮点 ---------- */
.highlight-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.highlight-item {
  display: flex;
  align-items: flex-start;
}

/* 小绿圆点 */
.highlight-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #18b368;
  margin-top: 12rpx;
  margin-right: 14rpx;
  flex-shrink: 0;
}

.highlight-text {
  font-size: 26rpx;
  color: #4b5563;
  line-height: 1.7;
  flex: 1;
}

/* ---------- 温馨提示（轻量提示条） ---------- */
.tips-list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.tips-item {
  font-size: 25rpx;
  color: #4b5563;
  line-height: 1.6;
  padding: 12rpx 16rpx;
  background: #f8fafc;
  border-radius: 14rpx;
  border-left: 4rpx solid #93c5fd;
}

/* ---------- 标签信息 ---------- */
.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

.tag-chip {
  font-size: 18rpx;
  font-weight: 500;
  color: #2f80ed;
  background: #eff6ff;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  border: 1rpx solid rgba(47, 128, 237, 0.08);
}

/* ---------- Action Bar（固定底部 · 三按钮） ---------- */
.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  gap: 14rpx;
  padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));
  background: #ffffff;
  box-shadow: 0 -4rpx 20rpx rgba(15, 23, 42, 0.06);
  z-index: 10;
}

.action-btn {
  flex: 1;
  height: 76rpx;
  line-height: 76rpx;
  padding: 0;
  border-radius: 999rpx;
  font-size: 26rpx;
  font-weight: 500;
  background: #f3f4f6;
  color: #374151;
  border: none;
  text-align: center;
}

.action-btn::after {
  border: none;
}

/* 已收藏 */
.action-btn.collected {
  background: #eff6ff;
  color: #2f80ed;
  border: 1rpx solid rgba(47, 128, 237, 0.12);
}

/* 推荐路线 — 蓝色次主按钮 */
.action-btn.secondary {
  background: #eff6ff;
  color: #2f80ed;
  font-weight: 600;
  border: 1rpx solid rgba(47, 128, 237, 0.15);
}

/* AI讲解 — 绿色主按钮 */
.action-btn.primary {
  background: linear-gradient(135deg, #18b368 0%, #16a34a 100%);
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 4rpx 12rpx rgba(24, 179, 104, 0.2);
}
</style>
