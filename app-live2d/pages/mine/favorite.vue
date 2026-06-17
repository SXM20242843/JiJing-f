<template>
  <view class="page">
    <view class="top-card">
      <view class="top-title">收藏景点</view>
      <view class="top-desc">
        已收藏 {{ favoriteList.length }} 个景点，可继续查看详情或发起 AI 讲解
      </view>

      <view class="top-actions">
        <view class="top-btn ghost" @click="goScenicList">去逛景区</view>
        <view class="top-btn" @click="clearFavorites">清空收藏</view>
      </view>
    </view>

    <view v-if="loading" class="empty-card">
      <view class="empty-title">正在加载收藏...</view>
      <view class="empty-desc">
        正在同步你的云端收藏数据。
      </view>
    </view>

    <view v-else-if="favoriteList.length === 0" class="empty-card">
      <view class="empty-title">暂无收藏景点</view>
      <view class="empty-desc">
        你可以在景点或景区详情页点击收藏，后续这里会自动展示。
      </view>
      <view class="empty-btn" @click="goScenicList">去浏览景区</view>
    </view>

    <view v-else class="list">
      <view class="card scenic-card" v-for="item in favoriteList" :key="item.key">
        <view class="cover">
          <view class="cover-tag">{{ item.sourceLabel }}</view>
        </view>

        <view class="info">
          <view class="name">{{ item.name || '未命名景点' }}</view>
          <view class="desc">{{ item.desc || '暂无景点简介' }}</view>

          <view class="tag-row" v-if="item.tags && item.tags.length">
            <text class="tag-chip" v-for="tag in item.tags" :key="tag">
              {{ tag }}
            </text>
          </view>

          <view class="action-row">
            <view class="pill" @click="goDetail(item)">查看详情</view>
            <view class="pill primary" @click="goAiExplain(item)">AI讲解</view>
            <view class="pill danger" @click="removeFavoriteItem(item)">取消收藏</view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { isLogin, getCurrentUserId } from '@/utils/auth'
import {
  getFavoriteList,
  removeFavorite as removeCloudFavorite
} from '@/utils/favorite'
import { trackPageView, trackEvent } from '@/utils/track'

const FAVORITE_KEY = 'favoriteScenics'
const SCENIC_MODE_KEY = 'scenic_list_mode'

const SCENIC_DETAIL_PAGE_PATH = '/pages/scenic/detail'
const PARK_DETAIL_PAGE_PATH = '/pages/park/detail'

const favoriteList = ref([])
const loading = ref(false)

function safeParseArray(value) {
  if (!value) return []

  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return []
  }
}

function getRawFavoriteList() {
  const cache = uni.getStorageSync(FAVORITE_KEY)
  return safeParseArray(cache)
}

function saveFavorites(rawList) {
  uni.setStorageSync(FAVORITE_KEY, JSON.stringify(rawList))
}

function getUserFavoriteCacheKey() {
  const userId = getCurrentUserId()
  return userId ? `${FAVORITE_KEY}_${userId}` : ''
}

function getUserFavoriteCache() {
  const cacheKey = getUserFavoriteCacheKey()

  if (!cacheKey) return []

  const cache = uni.getStorageSync(cacheKey)
  return safeParseArray(cache)
}

function saveUserFavoriteCache(rawList = []) {
  const cacheKey = getUserFavoriteCacheKey()

  if (!cacheKey) return

  uni.setStorageSync(cacheKey, JSON.stringify(Array.isArray(rawList) ? rawList : []))
}

function clearUserFavoriteCache() {
  const cacheKey = getUserFavoriteCacheKey()

  if (!cacheKey) return

  uni.removeStorageSync(cacheKey)
}

function normalizeLocalItem(item, index) {
  const sceneCode =
    item.scene_code ||
    item.sceneCode ||
    item.id ||
    ''

  return {
    raw: item,
    key: `local-${sceneCode || index}`,
    source: 'local',
    sourceLabel: '本地收藏',
    id: sceneCode || `favorite-${index}`,
    target_type: 'SPOT',
    target_id: null,
    scene_code: sceneCode,
    area_code: item.area_code || item.areaCode || item.parkId || '',
    name: item.name || item.scenicName || item.sceneName || '未命名景点',
    desc: item.desc || item.introduction || '',
    tags: Array.isArray(item.tags) ? item.tags : []
  }
}

function normalizeCloudItem(item, index) {
  return {
    raw: item,
    key: `cloud-${item.id || item.target_id || index}`,
    source: 'cloud',
    sourceLabel: '已收藏',
    id: item.scene_code || item.target_id || item.id || '',
    target_type: item.target_type || 'SPOT',
    target_id: item.target_id || null,
    scene_code: item.scene_code || '',
    area_code: item.area_code || '',
    name: item.target_name || item.name || '未命名景点',
    desc: item.intro || item.desc || '',
    image_url: item.image_url || '',
    tags: Array.isArray(item.tags) ? item.tags : []
  }
}

async function loadFavorites() {
  loading.value = true

  try {
    if (isLogin()) {
      const cloudList = await getFavoriteList()
      const list = Array.isArray(cloudList) ? cloudList : []

      saveUserFavoriteCache(list)
      favoriteList.value = list.map((item, index) => normalizeCloudItem(item, index))

      return
    }

    const localList = getRawFavoriteList()
    favoriteList.value = localList.map((item, index) => normalizeLocalItem(item, index))
  } catch (err) {
    console.log('加载收藏失败，使用本地缓存：', err)

    if (isLogin()) {
      const cachedList = getUserFavoriteCache()
      favoriteList.value = cachedList.map((item, index) => normalizeCloudItem(item, index))
      return
    }

    const localList = getRawFavoriteList()
    favoriteList.value = localList.map((item, index) => normalizeLocalItem(item, index))
  } finally {
    loading.value = false
  }
}

function clearLocalFavorites() {
  uni.removeStorageSync(FAVORITE_KEY)
  favoriteList.value = []

  uni.showToast({
    title: '已清空收藏',
    icon: 'none'
  })
}

function clearFavorites() {
  if (!favoriteList.value.length) {
    uni.showToast({
      title: '当前没有收藏内容',
      icon: 'none'
    })
    return
  }

  uni.showModal({
    title: '提示',
    content: isLogin()
      ? '确定清空全部云端收藏吗？'
      : '确定清空全部本地收藏吗？',
    success: async (res) => {
      if (!res.confirm) return

      if (!isLogin()) {
        clearLocalFavorites()
        return
      }

      uni.showLoading({
        title: '正在清空'
      })

      try {
        const tasks = favoriteList.value.map(item => {
          return removeCloudFavorite({
            target_type: item.target_type,
            target_id: item.target_id,
            scene_code: item.scene_code,
            area_code: item.area_code,
            name: item.name
          })
        })

        await Promise.all(tasks)

        clearUserFavoriteCache()
        favoriteList.value = []

        uni.showToast({
          title: '已清空收藏',
          icon: 'none'
        })
      } catch (err) {
        console.log('清空云端收藏失败：', err)
        uni.showToast({
          title: '清空失败',
          icon: 'none'
        })
      } finally {
        uni.hideLoading()
      }
    }
  })
}

async function removeFavoriteItem(item) {
  if (item.source === 'cloud' && isLogin()) {
    try {
      await removeCloudFavorite({
        target_type: item.target_type,
        target_id: item.target_id,
        scene_code: item.scene_code,
        area_code: item.area_code,
        name: item.name
      })

      await loadFavorites()

      uni.showToast({
        title: '已取消收藏',
        icon: 'none'
      })
    } catch (err) {
      console.log('取消云端收藏失败：', err)
      uni.showToast({
        title: '取消收藏失败',
        icon: 'none'
      })
    }

    return
  }

  const rawList = getRawFavoriteList()
  const nextList = rawList.filter((row, index) => {
    const rowId = row.id || row.sceneCode || row.scene_code || `favorite-${index}`
    return rowId !== item.id
  })

  saveFavorites(nextList)
  loadFavorites()

  trackEvent({
    event_type: 'FAVORITE_CANCEL',
    event_name: item.name ? `取消收藏${item.name}` : '取消收藏',
    entity_type: item.target_type || 'SPOT',
    entity_id: item.scene_code || item.id,
    scene_code: item.scene_code || item.id,
    area_code: item.area_code || '',
    source_page: 'pages/mine/favorite',
    content: item.name || '',
    extra: {
      source: 'local'
    }
  })

  uni.showToast({
    title: '已取消收藏',
    icon: 'none'
  })
}

function goScenicList() {
  uni.setStorageSync(SCENIC_MODE_KEY, 'all')
  uni.switchTab({
    url: '/pages/scenic/scenic'
  })
}

function goDetail(item) {
  if (!item.id && !item.scene_code && !item.target_id) {
    uni.showToast({
      title: '景点标识缺失',
      icon: 'none'
    })
    return
  }

  if (item.target_type === 'AREA') {
    const areaId = item.area_code || item.target_id || item.id

    uni.navigateTo({
      url: `${PARK_DETAIL_PAGE_PATH}?id=${areaId}`
    })
    return
  }

  const scenicId = item.scene_code || item.id || item.target_id

  uni.navigateTo({
    url: `${SCENIC_DETAIL_PAGE_PATH}?id=${scenicId}`
  })
}

function goAiExplain(item) {
  const scenicName = item.name || '当前景点'
  const scenicId = item.scene_code || item.id || item.target_id || ''

  uni.setStorageSync('selectedScenicName', scenicName)
  uni.setStorageSync('selectedScenicId', scenicId)
  uni.setStorageSync('aiContextType', 'scenic')
  uni.setStorageSync('aiContextName', scenicName)
  uni.setStorageSync('aiAutoQuestion', `请为我讲解「${scenicName}」这个景点，重点介绍历史背景、看点特色和参观注意事项。`)

  trackEvent({
    event_type: 'CLICK',
    event_name: `收藏页AI讲解：${scenicName}`,
    entity_type: item.target_type || 'SPOT',
    entity_id: scenicId,
    area_code: item.area_code || '',
    scene_code: item.scene_code || '',
    source_page: 'pages/mine/favorite',
    content: scenicName,
    extra: {
      action: 'favorite_ai_explain'
    }
  })

  uni.switchTab({
    url: '/pages/guide/guide'
  })
}

onShow(() => {
  trackPageView('收藏景点页面')
  loadFavorites()
})
</script>

<style>
/* ============================================================
   即境 · 收藏景点 — 收藏夹 + 兴趣中心
   设计方向：Clean APP Favorites · 统一列表卡片
   签名元素：固定高度卡片 · 轻量标签角标 · 统一按钮行
   本轮只改 CSS，不改 template / script / 任何业务入口
   ============================================================ */

/* ---------- Page ---------- */
.page {
  min-height: 100vh;
  background: #f5f7fb;
  padding: 24rpx;
  box-sizing: border-box;
}

/* ---------- Card Base ---------- */
.card,
.top-card,
.empty-card {
  background: #ffffff;
  border-radius: 28rpx;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
  border: 1rpx solid #f3f4f6;
}

/* ---------- Top Card（收藏汇总） ---------- */
.top-card {
  padding: 28rpx 24rpx;
  margin-bottom: 24rpx;
}

.top-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2937;
  padding-left: 18rpx;
  border-left: 6rpx solid #2f80ed;
  line-height: 1.3;
}

.top-desc {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.6;
}

.top-actions {
  display: flex;
  gap: 14rpx;
  margin-top: 18rpx;
}

.top-btn {
  height: 64rpx;
  line-height: 64rpx;
  padding: 0 28rpx;
  border-radius: 999rpx;
  background: #ef4444;
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 500;
  text-align: center;
}

.top-btn.ghost {
  background: #eff6ff;
  color: #2f80ed;
  font-weight: 600;
  border: 1rpx solid rgba(47, 128, 237, 0.1);
}

/* ---------- Empty Card ---------- */
.empty-card {
  padding: 72rpx 36rpx 64rpx;
  text-align: center;
}

.empty-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #1f2937;
}

.empty-desc {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.65;
}

.empty-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 72rpx;
  line-height: 72rpx;
  margin-top: 22rpx;
  padding: 0 32rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #18b368 0%, #16a34a 100%);
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 600;
  box-shadow: 0 4rpx 12rpx rgba(24, 179, 104, 0.18);
}

/* ---------- List ---------- */
.list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

/* ---------- Scenic Card（固定高度 210rpx） ---------- */
.scenic-card {
  height: 210rpx;
  display: flex;
  overflow: hidden;
  align-items: stretch;
}

/* ---------- Cover（固定宽 · 纯占位不突兀） ---------- */
.cover {
  width: 176rpx;
  flex-shrink: 0;
  background: linear-gradient(160deg, #dbeafe 0%, #bfdbfe 60%, #e0e7ff 100%);
  position: relative;
  overflow: hidden;
}

/* 轻量角标 — 已收藏 / 本地收藏 */
.cover-tag {
  position: absolute;
  left: 12rpx;
  top: 12rpx;
  background: rgba(255, 255, 255, 0.88);
  color: #2f80ed;
  font-size: 20rpx;
  font-weight: 600;
  padding: 5rpx 12rpx;
  border-radius: 999rpx;
  z-index: 2;
  box-shadow: 0 2rpx 6rpx rgba(15, 23, 42, 0.06);
}

/* ---------- Info（flex 列，按钮沉底） ---------- */
.info {
  flex: 1;
  padding: 16rpx 20rpx;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

/* 名称 — 单行省略 */
.name {
  font-size: 28rpx;
  font-weight: 700;
  color: #1f2937;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 简介 — 最多 2 行省略 */
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

/* 标签行 — nowrap + overflow hidden */
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

/* 按钮行 — 沉底 · nowrap */
.action-row {
  margin-top: auto;
  display: flex;
  gap: 10rpx;
  flex-wrap: nowrap;
}

.pill {
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: #f3f4f6;
  color: #374151;
  font-size: 20rpx;
  font-weight: 500;
  flex-shrink: 0;
  line-height: 1;
}

/* AI讲解 — 绿色渐变主按钮 */
.pill.primary {
  background: linear-gradient(135deg, #18b368 0%, #16a34a 100%);
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 2rpx 8rpx rgba(24, 179, 104, 0.15);
}

/* 取消收藏 — 红色语义弱化 */
.pill.danger {
  background: #fef2f2;
  color: #ef4444;
  border: 1rpx solid rgba(239, 68, 68, 0.1);
}
</style>
