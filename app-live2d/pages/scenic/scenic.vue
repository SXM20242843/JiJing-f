<template>
  <view class="page">
    <view class="top-card">
      <view class="page-title">{{ pageTitle }}</view>
      <view class="page-subtitle">
        {{ pageSubtitle }}
      </view>

      <view class="search-box">
        <input
          class="search-input"
          v-model="keyword"
          :placeholder="searchPlaceholder"
          confirm-type="search"
        />
      </view>
    </view>

    <view v-if="loading" class="status-box">
      <text class="status-text">正在加载景区数据...</text>
    </view>

    <view v-else-if="displayParkList.length === 0" class="status-box">
      <text class="status-text">
        {{ keyword ? '没有搜索到相关景区' : emptyText }}
      </text>
    </view>

    <view v-else class="list">
      <view class="scenic-card" v-for="item in displayParkList" :key="item.id">
        <view class="cover">
          <image
            v-if="getParkImage(item)"
            class="cover-image"
            :src="getParkImage(item)"
            mode="aspectFill"
            @error="handleParkImageError(item)"
          />
        
          <view v-else class="cover-placeholder">
            <text class="cover-placeholder-text">
              {{ getCoverText(item) }}
            </text>
          </view>
        
          <view class="cover-mask"></view>
        
          <view class="cover-tag" v-if="getParkTag(item)">
            {{ getParkTag(item) }}
          </view>
        </view>

        <view class="info">
          <view class="name">{{ item.name || '未命名景区' }}</view>
          <view class="desc">{{ formatShortDesc(item.desc, 86) }}</view>

          <view class="meta-row">
            <text class="meta-item">景点数量：{{ item.scenicCount ?? 0 }}</text>

            <text
              class="meta-item"
              v-if="scenicListMode === 'hot' && getParkHeat(item)"
            >
              热度：{{ getParkHeat(item) }}
            </text>

            <text class="meta-item" v-else>
              类型：{{ getParkTag(item) }}
            </text>
          </view>

          <view class="tag-row" v-if="item.tags && item.tags.length">
            <text class="tag-chip" v-for="tag in item.tags.slice(0, 3)" :key="tag">
              {{ tag }}
            </text>
          </view>

          <view class="action-row">
            <view class="pill" @click="goParkDetail(item)">查看详情</view>
            <view class="pill primary" @click="goAiExplain(item)">AI讲解</view>
          </view>
        </view>
      </view>
    </view>

    <TripInfoPopup
      :show="showTripInfoPopup"
      @cancel="handleTripInfoCancel"
      @confirm="handleTripInfoConfirm"
    />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { API_BASE, IMAGE_BASE } from '../../utils/api'
import { openNativeLive2DGuide } from '../../utils/openNativeLive2D.js'
import TripInfoPopup from '@/components/TripInfoPopup.vue'
import {
  useTripInfoConfirm,
  logTripInfoSelection
} from '@/utils/visit'

const PARKS_API = `${API_BASE}/api/app/parks`
const HOT_PARKS_API = `${API_BASE}/api/app/parks/hot?limit=50`
const SCENIC_LIST_MODE_KEY = 'scenicListMode'
const GUIDE_CONVERSATION_ID_KEY = 'guideConversationId'

const loading = ref(false)
const parkList = ref([])
const keyword = ref('')
const scenicListMode = ref('all')
const imageErrorMap = ref({})
const {
  showTripInfoPopup,
  openTripInfoConfirm,
  cancelTripInfoConfirm,
  consumePendingTripInfoAction
} = useTripInfoConfirm()

const pageTitle = computed(() => {
  return scenicListMode.value === 'hot' ? '热门推荐' : '全部景区'
})

const pageSubtitle = computed(() => {
  return scenicListMode.value === 'hot'
    ? '根据游客行为热度，为你优先展示热门景区'
    : '浏览全部景区，开启智慧导览之旅'
})

const searchPlaceholder = computed(() => {
  return scenicListMode.value === 'hot'
    ? '搜索热门景区 / 推荐目的地'
    : '搜索景区 / 目的地'
})

const emptyText = computed(() => {
  return scenicListMode.value === 'hot' ? '暂无热门推荐景区' : '暂无景区数据'
})

const filteredParkList = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  const list = [...parkList.value]

  if (!text) return list

  return list.filter(item => {
    const name = (item.name || '').toLowerCase()
    const desc = (item.desc || '').toLowerCase()
    const tags = Array.isArray(item.tags) ? item.tags.join(' ').toLowerCase() : ''

    return (
      name.includes(text) ||
      desc.includes(text) ||
      tags.includes(text)
    )
  })
})

const displayParkList = computed(() => filteredParkList.value)

function formatShortDesc(desc, maxLength = 86) {
  if (!desc) return '暂无景区简介'

  const text = String(desc)
    .replace(/\s+/g, '')
    .replace(/&nbsp;/g, '')
    .trim()

  if (!text) return '暂无景区简介'

  return text.length > maxLength ? text.slice(0, maxLength) + '...' : text
}

const IMAGE_UPLOAD_BASE = IMAGE_BASE

function normalizeImageUrl(url) {
  const rawUrl = String(url || '').trim()

  if (!rawUrl) return ''

  if (rawUrl.startsWith('http://') || rawUrl.startsWith('https://')) {
    return rawUrl
  }

  if (rawUrl.startsWith('//')) {
    return `https:${rawUrl}`
  }

  if (rawUrl.startsWith('/static/')) {
    return rawUrl
  }

  // 后端返回 /uploads/xxx.png
  if (rawUrl.startsWith('/')) {
    return `${IMAGE_UPLOAD_BASE}${rawUrl}`
  }

  // 兜底：后端返回 uploads/xxx.png
  return `${IMAGE_UPLOAD_BASE}/${rawUrl}`
}

function getParkImage(item) {
  if (!item) return ''

  const id = item.id || item.name || ''
  if (id && imageErrorMap.value[id]) {
    return ''
  }

  return normalizeImageUrl(
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

function fetchMode() {
  const mode = uni.getStorageSync(SCENIC_LIST_MODE_KEY)

  if (mode === 'hot') {
    scenicListMode.value = 'hot'
    uni.removeStorageSync(SCENIC_LIST_MODE_KEY)
    return
  }

  if (mode === 'all') {
    scenicListMode.value = 'all'
    uni.removeStorageSync(SCENIC_LIST_MODE_KEY)
    return
  }

  scenicListMode.value = 'all'
}

function fetchParks() {
  loading.value = true

  uni.request({
    url: scenicListMode.value === 'hot' ? HOT_PARKS_API : PARKS_API,
    method: 'GET',
    success: (res) => {
      if (res.statusCode >= 200 && res.statusCode < 300) {
        const data = res.data?.data || []
        
        parkList.value = Array.isArray(data)
          ? data.map(item => ({
              ...item,
              imageUrl: item.imageUrl || item.image_url || ''
            }))
          : []
      } else {
        uni.showToast({
          title: '景区数据加载失败',
          icon: 'none'
        })
      }
    },
    fail: (err) => {
      console.log('景区列表请求失败：', err)
      uni.showToast({
        title: '无法连接后端',
        icon: 'none'
      })
    },
    complete: () => {
      loading.value = false
    }
  })
}

function getParkTag(item) {
  const rawTag = String(item?.tag || '').trim()
  const count = Number(item?.scenicCount || 0)

  if (scenicListMode.value === 'hot') {
    if (rawTag && rawTag !== '推荐') {
      return rawTag === '单景点景区' ? '单体景区' : rawTag
    }

    if (getParkHeat(item)) {
      return '热门'
    }

    return count > 1 ? '综合景区' : '单体景区'
  }

  if (rawTag && rawTag !== '推荐' && rawTag !== '热门') {
    return rawTag === '单景点景区' ? '单体景区' : rawTag
  }

  return count > 1 ? '综合景区' : '单体景区'
}

function getParkHeat(item) {
  const heat = String(item?.heat || '').trim()

  if (!heat) return ''
  if (heat === '推荐') return ''
  if (heat === '热门') return ''

  return heat
}

function goParkDetail(park) {
  uni.setStorageSync('selectedPark', JSON.stringify(park))
  uni.navigateTo({ url: `/pages/park/detail?id=${park.id}` })
}

function openAiExplain(park) {
  const parkName = park.name || '当前景区'
  const parkId = park.id || park.areaCode || park.area_code || ''
  const areaId = park.areaId || park.area_id || parkId
  const areaCode = park.areaCode || park.area_code || parkId
  const autoQuestion = `请为我讲解「${parkName}」这个景区，重点介绍景区特色、文化背景、代表景点和参观注意事项。`

  // 从景区列表重新进入 AI，默认开启一轮新的导览会话，避免和上一个景区串上下文
  uni.removeStorageSync(GUIDE_CONVERSATION_ID_KEY)

  uni.setStorageSync('selectedScenicName', parkName)
  uni.setStorageSync('selectedScenicId', parkId)
  uni.setStorageSync('aiContextType', 'park')
  uni.setStorageSync('aiContextName', parkName)

  uni.setStorageSync('aiContext', JSON.stringify({
    page: 'scenic_list',
    contextType: 'park',
    contextName: parkName,
    areaId,
    areaCode,
    areaName: parkName,
    parkId,
    parkName,
    scenicId: parkId,
    scenicName: parkName,
    currentSpotId: '',
    currentSpotName: '',
    mode: 'scenic_explain',
    trigger: 'scenic-list-ai-explain'
  }))

  uni.setStorageSync('aiAutoQuestion', autoQuestion)

  openNativeLive2DGuide({
    entry: 'scenic-list-explain',
    contextType: 'park',
    contextName: parkName,
    parkId,
    parkName,
    areaId,
    areaCode,
    areaName: parkName,
    scenicName: parkName,
    scenicId: parkId,
    autoQuestion,
    mode: 'scenic_explain',
    trigger: 'scenic-list-ai-explain',
    allowEndVisit: false,
    startVisitGuide: false
  })
}

function goAiExplain(park) {
  openAiExplain(park)
}

function handleTripInfoCancel() {
  cancelTripInfoConfirm()
}

function handleTripInfoConfirm(selection) {
  logTripInfoSelection(selection)

  const action = consumePendingTripInfoAction()

  Promise.resolve(typeof action === 'function' ? action(selection) : null).catch(error => {
    console.log('景区列表导览打开失败：', error)
  })
}

onShow(() => {
  uni.setStorageSync('lastNonGuideTab', '/pages/scenic/scenic')
  fetchMode()
  fetchParks()
})
</script>

<style>
.page {
  min-height: 100vh;
  background: #f5f7fb;
  padding: 24rpx;
  box-sizing: border-box;
}

.top-card {
  background: #ffffff;
  border-radius: 28rpx;
  padding: 24rpx;
  box-shadow: 0 12rpx 28rpx rgba(15, 23, 42, 0.06);
  margin-bottom: 24rpx;
}

.page-title {
  font-size: 38rpx;
  font-weight: 700;
  color: #1f2937;
}

.page-subtitle {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.7;
}

.search-box {
  margin-top: 20rpx;
  background: #f3f4f6;
  border-radius: 20rpx;
  padding: 22rpx;
}

.search-input {
  width: 100%;
  font-size: 26rpx;
  color: #1f2937;
}

.status-box {
  background: #ffffff;
  border-radius: 24rpx;
  padding: 40rpx 24rpx;
  text-align: center;
  box-shadow: 0 12rpx 28rpx rgba(15, 23, 42, 0.06);
}

.status-text {
  font-size: 26rpx;
  color: #6b7280;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.scenic-card {
  background: #ffffff;
  border-radius: 28rpx;
  overflow: hidden;
  display: flex;
  box-shadow: 0 12rpx 28rpx rgba(15, 23, 42, 0.06);
}

.cover {
  width: 190rpx;
  min-height: 230rpx;
  max-height: 260rpx;
  background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
  position: relative;
  flex-shrink: 0;
  overflow: hidden;
}

.cover-image {
  width: 190rpx;
  height: 100%;
  min-height: 230rpx;
  max-height: 260rpx;
  display: block;
}

.cover-placeholder {
  width: 190rpx;
  height: 100%;
  min-height: 230rpx;
  max-height: 260rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
}

.cover-placeholder-text {
  font-size: 36rpx;
  font-weight: 700;
  color: #2f80ed;
}

.cover-mask {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 90rpx;
  background: linear-gradient(
    180deg,
    rgba(15, 23, 42, 0) 0%,
    rgba(15, 23, 42, 0.28) 100%
  );
  pointer-events: none;
}

.cover-tag {
  position: absolute;
  left: 16rpx;
  top: 16rpx;
  background: rgba(255, 255, 255, 0.92);
  color: #2f80ed;
  font-size: 22rpx;
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  z-index: 2;
}

.info {
  flex: 1;
  padding: 22rpx;
  min-width: 0;
}

.name {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2937;
}

.desc {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta-row {
  margin-top: 12rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.meta-item {
  font-size: 22rpx;
  color: #9ca3af;
}

.tag-row {
  margin-top: 14rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.tag-chip {
  font-size: 22rpx;
  color: #2f80ed;
  background: #eff6ff;
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
}

.action-row {
  margin-top: 18rpx;
  display: flex;
  gap: 12rpx;
  flex-wrap: wrap;
}

.pill {
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  background: #f3f4f6;
  color: #374151;
  font-size: 22rpx;
}

.pill.primary {
  background: #18b368;
  color: #ffffff;
}
</style>
