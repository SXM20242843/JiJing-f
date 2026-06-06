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
  uni.setStorageSync('aiAutoQuestion', `请为我讲解“${scenicName}”这个景点`)

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
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f5f7fb 0%, #eef4ff 100%);
  padding: 24rpx;
  box-sizing: border-box;
}

.card,
.top-card,
.empty-card {
  background: #ffffff;
  border-radius: 28rpx;
  box-shadow: 0 12rpx 28rpx rgba(15, 23, 42, 0.06);
}

.top-card {
  padding: 28rpx 24rpx;
  margin-bottom: 24rpx;
}

.top-title {
  font-size: 38rpx;
  font-weight: 700;
  color: #1f2937;
}

.top-desc {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.7;
}

.top-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
}

.top-btn {
  padding: 14rpx 24rpx;
  border-radius: 999rpx;
  background: #2f80ed;
  color: #ffffff;
  font-size: 24rpx;
}

.top-btn.ghost {
  background: #eff6ff;
  color: #2f80ed;
}

.empty-card {
  padding: 50rpx 28rpx;
  text-align: center;
}

.empty-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2937;
}

.empty-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.8;
}

.empty-btn {
  display: inline-block;
  margin-top: 24rpx;
  padding: 16rpx 28rpx;
  border-radius: 999rpx;
  background: #2f80ed;
  color: #ffffff;
  font-size: 24rpx;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.scenic-card {
  display: flex;
  overflow: hidden;
}

.cover {
  width: 190rpx;
  min-height: 220rpx;
  background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
  position: relative;
  flex-shrink: 0;
}

.cover-tag {
  position: absolute;
  left: 16rpx;
  top: 16rpx;
  background: rgba(255, 255, 255, 0.9);
  color: #2f80ed;
  font-size: 22rpx;
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
}

.info {
  flex: 1;
  padding: 22rpx;
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
  line-height: 1.7;
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

.pill.danger {
  background: #fff1f2;
  color: #e11d48;
}
</style>
