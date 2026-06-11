<template>
  <view class="page">
    <view v-if="loading" class="status-box">
      <text class="status-text">正在加载景区详情...</text>
    </view>

    <template v-else>
      <view class="hero-card">
        <view class="hero-cover">
          <image
            v-if="getParkImage()"
            class="hero-cover-image"
            :src="getParkImage()"
            mode="aspectFill"
            @error="handleParkImageError"
          />
        
          <view v-else class="hero-cover-placeholder">
            <text class="hero-cover-placeholder-text">
              {{ getCoverText(park) }}
            </text>
          </view>
        
          <view class="hero-cover-mask"></view>
        
          <view class="hero-badge">{{ park.tag || '推荐' }}</view>
        </view>

        <view class="hero-content">
          <view class="hero-title">{{ park.name || '景区详情' }}</view>
          <view class="hero-desc">
            {{ formatShortDesc(park.desc || '暂无景区简介', 160) }}
          </view>

          <view class="hero-meta">
            <text class="meta-pill">景点数量：{{ park.scenicCount ?? 0 }}</text>
            <text class="meta-pill">热度：{{ park.heat || '推荐' }}</text>
          </view>

          <view class="hero-actions">
            <view class="hero-action-btn primary" @click="goAiExplainPark">
              AI讲解景区
            </view>
            <view class="hero-action-btn" @click="goParkMap">
              查看景区地图
            </view>
          </view>
        </view>
      </view>

      <view class="section-header">
        <view class="section-title">景点列表</view>
        <view class="section-more" @click="goAiExplainPark">AI讲解景区</view>
      </view>

      <view
        v-if="!scenicListInPark.length && park.isSingleScenic"
        class="single-scenic-card"
      >
        <view class="single-title">该景区属于单体核心景点</view>
        <view class="single-desc">
          当前景区本身就是主要讲解对象，可直接使用 AI 讲解。
        </view>

        <view class="single-actions">
          <view class="single-btn ghost" @click="handleCollectSingleScenic">
            收藏景点
          </view>
          <view class="single-btn" @click="goAiExplainPark">
            AI讲解
          </view>
        </view>
      </view>

      <view v-else-if="!scenicListInPark.length" class="status-box">
        <text class="status-text">当前景区暂无景点数据</text>
      </view>

      <view v-else class="scenic-list">
        <view
          class="scenic-card"
          v-for="item in scenicListInPark"
          :key="item.id"
          @click="goScenicDetail(item)"
        >
          <view class="scenic-cover">
            <image
              v-if="getScenicImage(item)"
              class="scenic-cover-image"
              :src="getScenicImage(item)"
              mode="aspectFill"
              @error="handleScenicImageError(item)"
            />
          
            <view v-else class="scenic-cover-placeholder">
              <text class="scenic-cover-placeholder-text">
                {{ getCoverText(item) }}
              </text>
            </view>
          
            <view class="scenic-tag">{{ item.badge || '景点' }}</view>
          </view>

          <view class="scenic-info">
            <view class="scenic-name">{{ item.name || '未命名景点' }}</view>
            <view class="scenic-desc">
              {{ formatShortDesc(item.desc || '暂无景点简介', 56) }}
            </view>

            <view class="scenic-meta">
              <text>开放时间：{{ item.time || '暂无' }}</text>
              <text>推荐时长：{{ item.duration || '暂无' }}</text>
            </view>

            <view class="scenic-tags" v-if="item.tags && item.tags.length">
              <text class="chip" v-for="tag in item.tags" :key="tag">
                {{ tag }}
              </text>
            </view>
          </view>
        </view>
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
import { trackUserBehavior } from '@/utils/behavior'
import TripInfoPopup from '@/components/TripInfoPopup.vue'
import {
  useTripInfoConfirm,
  logTripInfoSelection
} from '@/utils/visit'

const GUIDE_CONVERSATION_ID_KEY = 'guideConversationId'
const FAVORITE_SCENICS_KEY = 'favoriteScenics'
const {
  showTripInfoPopup,
  openTripInfoConfirm,
  cancelTripInfoConfirm,
  consumePendingTripInfoAction
} = useTripInfoConfirm()

const loading = ref(false)
const currentParkId = ref('')
const parkImageError = ref(false)
const scenicImageErrorMap = ref({})

const park = ref({
  id: '',
  name: '',
  desc: '',
  scenicCount: 0,
  heat: '',
  tag: '',
  tags: [],
  isSingleScenic: false,
  digitalHumanConfig: null
})

const scenicListInPark = ref([])

function requestGet(url) {
  return new Promise((resolve, reject) => {
    uni.request({
      url,
      method: 'GET',
      success: (res) => {
        console.log('接口返回：', url, res)
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

function formatShortDesc(desc, maxLength = 56) {
  if (!desc) return '暂无简介'

  const text = String(desc)
    .replace(/\s+/g, '')
    .replace(/&nbsp;/g, '')
    .trim()

  if (!text) return '暂无简介'

  return text.length > maxLength ? text.slice(0, maxLength) + '...' : text
}

function getCoverText(item) {
  const name = String(item?.name || '景区').trim()
  return name.slice(0, 2)
}

function getParkImage() {
  if (parkImageError.value) return ''

  return resolveImageUrl(
    park.value.imageUrl ||
    park.value.image_url ||
    park.value.coverUrl ||
    park.value.cover ||
    ''
  )
}

function handleParkImageError() {
  parkImageError.value = true
}

function getScenicImage(item) {
  if (!item) return ''

  const id = item.id || item.name || ''
  if (id && scenicImageErrorMap.value[id]) {
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

function handleScenicImageError(item) {
  const id = item?.id || item?.name || ''
  if (!id) return

  scenicImageErrorMap.value = {
    ...scenicImageErrorMap.value,
    [id]: true
  }
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

function buildParkBehaviorPayload(extra = {}) {
  const parkId = park.value.id || currentParkId.value || ''
  const parkName = park.value.name || ''
  const areaCode = parkId

  return {
    entityType: 'PARK',
    entityId: parkId || 'park-detail',
    areaCode,
    sourcePage: 'pages/park/detail',
    parkId,
    parkName,
    extra: {
      parkId,
      parkName,
      areaCode,
      sourcePage: 'pages/park/detail',
      ...extra
    }
  }
}

function trackParkDetailView() {
  trackUserBehavior('view_park_detail', {
    ...buildParkBehaviorPayload(),
    eventName: 'view_park_detail'
  })
}

async function fetchParkDetail(id) {
  const res = await requestGet(`${API_BASE}/api/app/parks/${id}`)
  const data = res?.data || {}
  
  parkImageError.value = false

  park.value = {
    id: data.id || id || '',
    name: data.name || '',
    desc: data.desc || data.introduction || '',
    scenicCount: data.scenicCount ?? 0,
    heat: data.heat || '推荐',
    tag: data.tag || '推荐',
    tags: Array.isArray(data.tags) ? data.tags : [],
    parkType: data.parkType || '',
    isSingleScenic: !!data.isSingleScenic,
    introduction: data.introduction || '',
    openInfo: data.openInfo || '',
    location: data.location || '',
    address: data.address || data.location || '',
    imageUrl: data.imageUrl || '',
    latitude: data.latitude ?? data.lat ?? null,
    longitude: data.longitude ?? data.lng ?? data.lon ?? null,
	digitalHumanConfig: normalizeDigitalHumanConfig(data, data.name || '')
  }
}

async function fetchParkScenics(id) {
  const res = await requestGet(`${API_BASE}/api/app/parks/${id}/scenics`)
  const data = res?.data || []
  scenicImageErrorMap.value = {}
  
  scenicListInPark.value = Array.isArray(data)
    ? data.map(item => ({
        ...item,
        imageUrl: item.imageUrl || item.image_url || ''
      }))
    : []
}

async function initPage(id) {
  if (!id) {
    uni.showToast({
      title: '缺少景区标识',
      icon: 'none'
    })
    return
  }

  loading.value = true
  try {
    await Promise.all([
      fetchParkDetail(id),
      fetchParkScenics(id)
    ])
    trackParkDetailView()
  } catch (error) {
    console.log('景区详情加载失败：', error)
    uni.showToast({
      title: '景区详情加载失败',
      icon: 'none'
    })
  } finally {
    loading.value = false
  }
}

function saveCurrentParkContext() {
  uni.setStorageSync('selectedParkContext', JSON.stringify({
    id: park.value.id || currentParkId.value || '',
    name: park.value.name || '',
    digitalHumanConfig: park.value.digitalHumanConfig || null
  }))
}

function goParkMap() {
  const parkId = park.value.id || currentParkId.value || ''

  if (!parkId) {
    uni.showToast({
      title: '缺少景区标识',
      icon: 'none'
    })
    return
  }

  trackUserBehavior('view_map', {
    ...buildParkBehaviorPayload({
      trigger: 'click_map_button'
    }),
    eventName: 'view_map'
  })

  uni.setStorageSync('selectedParkMapContext', JSON.stringify({
    ...park.value,
    scenics: scenicListInPark.value
  }))

  uni.navigateTo({
    url: `/pages/map/map?parkId=${encodeURIComponent(parkId)}`
  })
}

function goScenicDetail(scenic) {
  saveCurrentParkContext()

  uni.setStorageSync('selectedScenicDetail', JSON.stringify({
    ...scenic,
    parkId: park.value.id || currentParkId.value || scenic.parkId || '',
    parkName: park.value.name || '',
    digitalHumanConfig:
      scenic.digitalHumanConfig ||
      scenic.digital_human_config ||
      park.value.digitalHumanConfig ||
      null
  }))

  uni.navigateTo({
    url: `/pages/scenic/detail?id=${scenic.id}`
  })
}

function handleCollectSingleScenic() {
  const parkId = park.value.id || currentParkId.value || ''
  const parkName = park.value.name || '当前景区'

  if (!parkId) {
    uni.showToast({
      title: '缺少景点标识',
      icon: 'none'
    })
    return
  }

  const favoriteItem = {
    id: parkId,
    name: parkName,
    desc: park.value.desc || park.value.introduction || '',
    parkId,
    parkName,
    time: park.value.openInfo || '',
    duration: '',
    badge: park.value.tag || '单体景区',
    tags: Array.isArray(park.value.tags) ? park.value.tags : [],
    introduction: park.value.introduction || park.value.desc || '',
    location: park.value.location || park.value.address || '',
    imageUrl: park.value.imageUrl || '',
    isSingleScenic: true
  }

  let favoriteList = []

  try {
    const cache = uni.getStorageSync(FAVORITE_SCENICS_KEY)
    favoriteList = cache ? JSON.parse(cache) : []
    if (!Array.isArray(favoriteList)) {
      favoriteList = []
    }
  } catch (error) {
    console.log('读取收藏景点失败：', error)
    favoriteList = []
  }

  const exists = favoriteList.some(item => String(item.id) === String(favoriteItem.id))

  if (exists) {
    uni.showToast({
      title: '该景点已收藏',
      icon: 'none'
    })
    return
  }

  favoriteList.unshift(favoriteItem)
  uni.setStorageSync(FAVORITE_SCENICS_KEY, JSON.stringify(favoriteList))

  trackUserBehavior('favorite_park', {
    ...buildParkBehaviorPayload({
      favoriteMode: 'single_scenic_proxy'
    }),
    eventName: 'favorite_park'
  })

  uni.showToast({
    title: '已收藏景点',
    icon: 'success'
  })
}

function openAiExplainPark() {
  const parkName = park.value.name || '当前景区'
  const parkId = park.value.id || currentParkId.value || ''
  const areaId = parkId
  const areaCode = parkId
  const autoQuestion = `请为我讲解「${parkName}」这个景区，重点介绍景区特色、文化背景、代表景点和参观注意事项。`
  const digitalHumanConfig = normalizeDigitalHumanConfig(
    park.value.digitalHumanConfig || park.value,
    parkName
  )

  uni.removeStorageSync(GUIDE_CONVERSATION_ID_KEY)

  uni.setStorageSync('selectedScenicName', parkName)
  uni.setStorageSync('selectedScenicId', parkId)
  uni.setStorageSync('aiContextType', 'park')
  uni.setStorageSync('aiContextName', parkName)

  uni.setStorageSync('aiContext', JSON.stringify({
    page: 'park_detail',
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
    trigger: 'park-detail-ai-explain',
    digitalHumanConfig
  }))

  uni.setStorageSync('aiAutoQuestion', autoQuestion)

  openNativeLive2DGuide({
    entry: 'park-detail-explain',
    source: NATIVE_LIVE2D_SOURCE,

    contextType: 'park',
    contextName: parkName,
    trackScenicVisit: false,
    scenicTrackId: parkId,
    scenicTrackName: parkName,
    enterSource: 'park_detail_ai_explain',

    scenicName: parkName,
    scenicId: parkId,

    parkId,
    parkName,
    areaId,
    areaCode,
    areaName: parkName,

    spotId: '',
    spotName: '',

    autoQuestion,
    mode: 'scenic_explain',
    trigger: 'park-detail-ai-explain',
    allowEndVisit: false,
    startVisitGuide: false
  })
}

function goAiExplainPark() {
  trackUserBehavior('click_ai_guide', {
    ...buildParkBehaviorPayload({
      trigger: 'park_detail_ai_guide'
    }),
    eventName: 'click_ai_guide'
  })

  openAiExplainPark()
}

function handleTripInfoCancel() {
  cancelTripInfoConfirm()
}

function handleTripInfoConfirm(selection) {
  logTripInfoSelection(selection)

  const action = consumePendingTripInfoAction()

  Promise.resolve(typeof action === 'function' ? action(selection) : null).catch(error => {
    console.log('景区讲解打开失败：', error)
  })
}

onLoad((options) => {
  const routeId = options?.id || ''
  currentParkId.value = routeId

  if (routeId) {
    initPage(routeId)
    return
  }

  try {
    const cache = uni.getStorageSync('selectedPark')
    if (cache) {
      const parsed = JSON.parse(cache)
      const cacheId = parsed?.id || ''
      if (cacheId) {
        currentParkId.value = cacheId
        initPage(cacheId)
      }
    }
  } catch (e) {
    console.log('读取景区缓存失败：', e)
  }
})
</script>

<style scoped>
.page {
  padding: 24rpx;
  background: #f5f7fb;
  min-height: 100vh;
}

.status-box {
  background: #fff;
  border-radius: 24rpx;
  padding: 40rpx 24rpx;
  text-align: center;
  box-shadow: 0 12rpx 28rpx rgba(15, 23, 42, 0.06);
}

.status-text {
  font-size: 26rpx;
  color: #6b7280;
}

.hero-card {
  background: #fff;
  border-radius: 28rpx;
  overflow: hidden;
  margin-bottom: 24rpx;
}

.hero-cover {
  height: 200rpx;
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
  position: relative;
  overflow: hidden;
}

.hero-cover-image {
  width: 100%;
  height: 200rpx;
  display: block;
}

.hero-cover-placeholder {
  width: 100%;
  height: 200rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
}

.hero-cover-placeholder-text {
  font-size: 42rpx;
  font-weight: 700;
  color: #2f80ed;
}

.hero-cover-mask {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 100rpx;
  background: linear-gradient(
    180deg,
    rgba(15, 23, 42, 0) 0%,
    rgba(15, 23, 42, 0.28) 100%
  );
  pointer-events: none;
}

.hero-badge {
  position: absolute;
  left: 20rpx;
  top: 20rpx;
  background: rgba(255,255,255,0.9);
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  color: #2f80ed;
  z-index: 2;
}

.scenic-cover {
  width: 160rpx;
  height: 160rpx;
  background: linear-gradient(135deg, #e0e7ff, #c7d2fe);
  border-radius: 20rpx;
  position: relative;
  margin-right: 20rpx;
  flex-shrink: 0;
  overflow: hidden;
}

.scenic-cover-image {
  width: 160rpx;
  height: 160rpx;
  display: block;
}

.scenic-cover-placeholder {
  width: 160rpx;
  height: 160rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e0e7ff, #c7d2fe);
}

.scenic-cover-placeholder-text {
  font-size: 30rpx;
  font-weight: 700;
  color: #2f80ed;
}

.scenic-tag {
  position: absolute;
  left: 12rpx;
  top: 12rpx;
  background: rgba(255,255,255,0.85);
  padding: 4rpx 12rpx;
  border-radius: 20rpx;
  font-size: 20rpx;
  z-index: 2;
}

.hero-content {
  padding: 24rpx;
}

.hero-title {
  font-size: 40rpx;
  font-weight: bold;
}

.hero-desc {
  font-size: 26rpx;
  color: #6b7280;
  margin-top: 8rpx;
  line-height: 1.7;
}

.hero-meta {
  display: flex;
  gap: 16rpx;
  margin-top: 16rpx;
  flex-wrap: wrap;
}

.meta-pill {
  background: #eff6ff;
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  color: #2f80ed;
}

.hero-actions {
  margin-top: 22rpx;
  display: flex;
  gap: 16rpx;
}

.hero-action-btn {
  flex: 1;
  height: 72rpx;
  border-radius: 999rpx;
  background: #eff6ff;
  color: #2f80ed;
  font-size: 25rpx;
  font-weight: 600;
  text-align: center;
  line-height: 72rpx;
}

.hero-action-btn.primary {
  background: #18b368;
  color: #ffffff;
}

.section-header {
  display: flex;
  justify-content: space-between;
  margin: 16rpx 0;
  align-items: center;
}

.section-title {
  font-size: 32rpx;
  font-weight: bold;
}

.section-more {
  color: #2f80ed;
  font-size: 24rpx;
}

.single-scenic-card {
  background: #fff;
  border-radius: 24rpx;
  padding: 24rpx;
  box-shadow: 0 12rpx 28rpx rgba(15, 23, 42, 0.06);
}

.single-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2937;
}

.single-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.7;
}

.single-actions {
  margin-top: 18rpx;
  display: flex;
  gap: 12rpx;
}

.single-btn {
  padding: 12rpx 22rpx;
  border-radius: 999rpx;
  background: #18b368;
  color: #fff;
  font-size: 22rpx;
}

.single-btn.ghost {
  background: #eff6ff;
  color: #2f80ed;
}

.scenic-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.scenic-card {
  background: #fff;
  border-radius: 24rpx;
  display: flex;
  padding: 20rpx;
  box-shadow: 0 12rpx 28rpx rgba(15, 23, 42, 0.06);
}

.scenic-info {
  flex: 1;
  min-width: 0;
}

.scenic-name {
  font-size: 30rpx;
  font-weight: bold;
}

.scenic-desc {
  font-size: 24rpx;
  color: #6b7280;
  margin-top: 8rpx;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.scenic-meta {
  display: flex;
  gap: 16rpx;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #9ca3af;
  flex-wrap: wrap;
}

.scenic-tags {
  margin-top: 12rpx;
  display: flex;
  gap: 12rpx;
  flex-wrap: wrap;
}

.chip {
  background: #eff6ff;
  padding: 6rpx 16rpx;
  border-radius: 20rpx;
  color: #2f80ed;
  font-size: 20rpx;
}
</style>
