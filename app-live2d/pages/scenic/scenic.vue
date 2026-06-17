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
/* ============================================================
   即境 · 景区列表 — 游览前规划入口
   设计方向：Clean Scenic Directory · 统一列表卡片
   签名元素：弱化 accent bar + 固定高度卡片 + 紧凑信息层次
   本轮只改 CSS，不改 template / script / 任何业务入口
   ============================================================ */

/* ---------- Page ---------- */
.page {
  min-height: 100vh;
  background: #f5f7fb;
  padding: 24rpx;
  box-sizing: border-box;
}

/* ---------- 顶部头部（无卡片感 · 标题直贴页面背景） ---------- */
.top-card {
  background: transparent;
  border: none;
  border-radius: 0;
  box-shadow: none;
  padding: 0 0 22rpx 0;
  margin-bottom: 16rpx;
}

.page-title {
  font-size: 36rpx;
  font-weight: 700;
  color: #1f2937;
}

.page-subtitle {
  margin-top: 4rpx;
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.5;
}

/* ---------- 搜索框（独立白色卡片） ---------- */
.search-box {
  margin-top: 16rpx;
  background: #ffffff;
  border: 1rpx solid #e5eaf3;
  border-radius: 24rpx;
  padding: 18rpx 22rpx;
  box-shadow: 0 8rpx 18rpx rgba(15, 23, 42, 0.04);
}

.search-input {
  width: 100%;
  font-size: 26rpx;
  color: #1f2937;
}

.search-input::placeholder {
  color: #9ca3af;
}

/* ---------- Status States (loading / empty) ---------- */
.status-box {
  background: #ffffff;
  border-radius: 24rpx;
  padding: 80rpx 24rpx 64rpx;
  text-align: center;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
}

/* 装饰圆 — 空态视觉锚点 */
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

/* ---------- List ---------- */
.list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

/* ---------- Scenic Card (统一高度 244rpx) ---------- */
.scenic-card {
  height: 244rpx;
  background: #ffffff;
  border-radius: 28rpx;
  overflow: hidden;
  display: flex;
  align-items: stretch;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
  border: 1rpx solid #f3f4f6;
}

/* ---------- Cover (固定 184×244) ---------- */
.cover {
  width: 184rpx;
  flex-shrink: 0;
  background: linear-gradient(160deg, #dbeafe 0%, #bfdbfe 60%, #e0e7ff 100%);
  position: relative;
  overflow: hidden;
}

.cover-image {
  width: 100%;
  height: 100%;
  display: block;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #dbeafe 0%, #e0e7ff 50%, #c7d2fe 100%);
}

.cover-placeholder-text {
  font-size: 44rpx;
  font-weight: 800;
  color: #2f80ed;
  opacity: 0.5;
}

/* 底部渐变遮罩 */
.cover-mask {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 80rpx;
  background: linear-gradient(
    180deg,
    rgba(15, 23, 42, 0) 0%,
    rgba(15, 23, 42, 0.22) 100%
  );
  pointer-events: none;
}

/* 毛玻璃角标 */
.cover-tag {
  position: absolute;
  left: 10rpx;
  top: 10rpx;
  background: rgba(255, 255, 255, 0.88);
  color: #2f80ed;
  font-size: 20rpx;
  font-weight: 600;
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  z-index: 2;
  box-shadow: 0 2rpx 6rpx rgba(15, 23, 42, 0.06);
}

/* ---------- Info (flex 列，按钮沉底) ---------- */
.info {
  flex: 1;
  padding: 16rpx 20rpx;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

/* ---------- Name (单行 · 省略号) ---------- */
.name {
  font-size: 28rpx;
  font-weight: 700;
  color: #1f2937;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------- Desc (固定 2 行 · 省略号) ---------- */
.desc {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #6b7280;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ---------- Meta Row (单行 · 不换行 · 溢隐藏) ---------- */
.meta-row {
  margin-top: 6rpx;
  display: flex;
  flex-wrap: nowrap;
  gap: 14rpx;
  overflow: hidden;
}

.meta-item {
  font-size: 20rpx;
  color: #9ca3af;
  flex-shrink: 0;
  white-space: nowrap;
}

/* ---------- Tag Row (单行 · 不换行 · 溢隐藏) ---------- */
.tag-row {
  margin-top: 4rpx;
  display: flex;
  flex-wrap: nowrap;
  gap: 8rpx;
  overflow: hidden;
}

.tag-chip {
  font-size: 18rpx;
  font-weight: 500;
  color: #2f80ed;
  background: #eff6ff;
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  border: 1rpx solid rgba(47, 128, 237, 0.08);
  flex-shrink: 0;
  white-space: nowrap;
}

/* ---------- Action Row (沉底 · 按钮高度统一) ---------- */
.action-row {
  margin-top: auto;
  display: flex;
  gap: 10rpx;
  flex-wrap: nowrap;
}

.pill {
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  background: #f3f4f6;
  color: #374151;
  font-size: 22rpx;
  font-weight: 500;
  line-height: 1;
  flex-shrink: 0;
  transition: opacity 0.15s ease;
}

.pill:active {
  opacity: 0.7;
}

/* AI讲解 — 绿色渐变主操作 · 阴影减轻 */
.pill.primary {
  background: linear-gradient(135deg, #18b368 0%, #16a34a 100%);
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 2rpx 8rpx rgba(24, 179, 104, 0.18);
}

.pill.primary:active {
  opacity: 0.85;
}
</style>
