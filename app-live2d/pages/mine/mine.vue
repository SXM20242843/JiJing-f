<template>
  <view class="page">
    <view class="profile-card">
      <view class="profile-bg-circle profile-bg-circle-1"></view>
      <view class="profile-bg-circle profile-bg-circle-2"></view>

      <view class="profile-top">
        <view class="avatar">
          {{ avatarText }}
        </view>

        <view class="profile-info" @click="handleProfileClick">
          <view class="name">
            {{ displayName }}
          </view>
          <view class="identity-line">
            身份：{{ loggedIn ? '已登录' : '未登录' }}
          </view>
          <view class="desc">
            {{ profileDesc }}
          </view>
        </view>

        <view v-if="!loggedIn" class="login-chip" @click="goLogin">
          去登录
        </view>
      </view>

      <view class="profile-stats">
        <view class="stat-item">
          <view class="stat-value">{{ consultCount }}</view>
          <view class="stat-label">咨询记录</view>
        </view>
        <view class="stat-item">
          <view class="stat-value">{{ favoriteCount }}</view>
          <view class="stat-label">收藏景点</view>
        </view>
        <view class="stat-item">
          <view class="stat-value">{{ loggedIn ? '已登录' : '未登录' }}</view>
          <view class="stat-label">身份状态</view>
        </view>
      </view>
    </view>

    <view class="menu-card">
      <view class="section-title">常用服务</view>

      <view
        class="menu-item"
        v-for="item in menuList"
        :key="item.title"
        @click="handleMenuClick(item)"
      >
        <view class="menu-left">
          <image
            v-if="item.avatar"
            class="menu-avatar"
            :src="item.avatar"
            mode="aspectFill"
          />
          <view v-else class="menu-icon">{{ item.icon }}</view>
          <view class="menu-text-wrap">
            <view class="menu-title">{{ item.title }}</view>
            <view class="menu-desc">{{ item.desc }}</view>
          </view>
        </view>
        <view class="menu-arrow">›</view>
      </view>
    </view>

    <view class="about-card">
      <view class="section-title" @click="handleAboutTitleTap">关于系统</view>
      <view class="about-text">
        本系统面向景区游客提供 AI 数字人导览、语音问答、景点推荐、智能路线等智慧化服务，
        帮助游客获得更自然、更便捷的游览体验。
      </view>
    </view>

    <view v-if="loggedIn" class="logout-card" @click="handleLogout">
      退出登录
    </view>

    <view v-if="accountDialogVisible" class="modal-mask" @click="closeAccountDialog">
      <view class="account-dialog" @click.stop>
        <view class="dialog-title">账号与数据标识</view>

        <view class="dialog-section">
          <view class="dialog-section-title">登录账号</view>
          <view class="account-row">
            <text class="account-label">用户昵称</text>
            <text class="account-value">{{ displayName }}</text>
          </view>
          <view class="account-row">
            <text class="account-label">登录状态</text>
            <text class="account-value">{{ loggedIn ? '已登录' : '未登录' }}</text>
          </view>
          <view class="account-row">
            <text class="account-label">用户编号</text>
            <view class="account-id-wrap">
              <text class="account-id">{{ shortId(currentUserId) || '暂无' }}</text>
              <text v-if="currentUserId" class="copy-btn" @click.stop="copyId(currentUserId)">复制</text>
            </view>
          </view>
        </view>

        <view class="dialog-section">
          <view class="dialog-section-title">设备游客标识</view>
          <view class="account-row">
            <text class="account-label">visitor_id</text>
            <view class="account-id-wrap">
              <text class="account-id">{{ shortId(visitorId) }}</text>
              <text class="copy-btn" @click.stop="copyId(visitorId)">复制</text>
            </view>
          </view>
        </view>

        <view class="dialog-section">
          <view class="dialog-section-title">当前会话标识</view>
          <view class="account-row">
            <text class="account-label">session_id</text>
            <view class="account-id-wrap">
              <text class="account-id">{{ shortId(sessionId) }}</text>
              <text class="copy-btn" @click.stop="copyId(sessionId)">复制</text>
            </view>
          </view>
        </view>

        <view class="dialog-tip">
          这些标识用于关联收藏、咨询记录、游玩报告和个性化导览数据。
        </view>

        <view class="dialog-confirm" @click="closeAccountDialog">知道了</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request from '@/utils/request'
import {
  isLogin,
  getUserInfo,
  getCurrentUserId,
  getVisitorId,
  getSessionId,
  clearLogin
} from '@/utils/auth'
import { getFavoriteList } from '@/utils/favorite'
import { trackPageView, trackClick } from '@/utils/track'
import { clearVisitCacheForLogout, getLastEndedVisit, getLastVisitId } from '@/utils/visit'

const FAVORITE_KEY = 'favoriteScenics'
const CONSULT_KEY = 'recentConsults'
const SETTINGS_KEY = 'appSettings'

const defaultAppSettings = {
  enableVoice: true,
  autoPlayVoice: true,
  showConsultHistory: true,
  showHomeNotice: true
}

const appSettings = reactive({
  ...defaultAppSettings
})

const favoriteList = ref([])
const consultList = ref([])

const loggedIn = ref(false)
const userInfo = ref(null)
const visitorId = ref('')
const sessionId = ref('')
const accountDialogVisible = ref(false)
const aboutTapCount = ref(0)
const lastAboutTapAt = ref(0)

const currentUserId = computed(() => {
  return getCurrentUserId()
})

const favoriteCount = computed(() => favoriteList.value.length)

const consultCount = computed(() => {
  return consultList.value.length
})

const displayName = computed(() => {
  if (!loggedIn.value) {
    return '未登录'
  }

  const info = userInfo.value || {}

  return (
    info.nickname ||
    info.nickName ||
    info.username ||
    info.account ||
    info.phone ||
    '已登录用户'
  )
})

const avatarText = computed(() => {
  if (!loggedIn.value) {
    return '游'
  }

  const name = displayName.value || '用'
  return name.slice(0, 1)
})

const profileDesc = computed(() => {
  if (!loggedIn.value) {
    return '登录后可同步收藏、咨询记录和个性化导览数据'
  }

  return '已开启个性化导览与行为分析数据关联'
})

const recentVisitId = computed(() => {
  if (!loggedIn.value || !currentUserId.value) {
    return ''
  }
  const ended = getLastEndedVisit({ fallbackGlobal: false })
  return ended.visitId || getLastVisitId({ fallbackGlobal: false }) || ''
})

const menuList = computed(() => {
  const list = [
    {
      title: '收藏景点',
      desc: favoriteCount.value
        ? `已收藏 ${favoriteCount.value} 个景点，进入查看详情`
        : '已收藏 0 个景点，进入查看详情',
      icon: '⭐',
      action: 'favorites'
    }
  ]

  list.push({
    title: '我的消费记录',
    desc: loggedIn.value
      ? '查看扫码消费和模拟支付流水'
      : '登录后可查看个人消费记录',
    icon: '💳',
    action: 'payments'
  })

  list.push({
    title: '咨询记录',
    desc: '查看最近的 AI 导览问答记录',
    icon: '🗂️',
    action: 'consults'
  })

  list.push({
    title: '我的偏好',
    desc: '完善偏好信息，获得更懂你的数字人导览推荐',
    icon: '🧩',
    action: 'profile'
  })

  list.push({
    title: '最近游玩报告',
    desc: '查看最近一次现场导览总结',
    icon: '📄',
    action: 'visitReport'
  })

  list.push({
    title: '账号信息',
    desc: '查看当前登录账号与游客数据标识',
    icon: '👤',
    action: 'account'
  })

  list.push({
    title: '系统设置',
    desc: '管理语音播报、记录显示与系统偏好',
    icon: '⚙️',
    action: 'settings'
  })

  return list
})

function safeParseArray(value) {
  if (!value) return []

  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return []
  }
}

function getFavoriteCacheKey() {
  const userId = currentUserId.value || getCurrentUserId()
  return userId ? `${FAVORITE_KEY}_${userId}` : ''
}

function getUserFavoriteCache() {
  const cacheKey = getFavoriteCacheKey()

  if (!cacheKey) return []

  const cache = uni.getStorageSync(cacheKey)
  return safeParseArray(cache)
}

function saveUserFavoriteCache(list = []) {
  const cacheKey = getFavoriteCacheKey()

  if (!cacheKey) return

  uni.setStorageSync(cacheKey, JSON.stringify(Array.isArray(list) ? list : []))
}

function normalizeFavoriteItem(item, index) {
  return {
    id:
      item.scene_code ||
      item.sceneCode ||
      item.target_id ||
      item.targetId ||
      item.id ||
      `favorite-${index}`,
    name:
      item.target_name ||
      item.name ||
      item.scenicName ||
      item.sceneName ||
      '未命名景点',
    desc:
      item.intro ||
      item.desc ||
      item.introduction ||
      item.summary ||
      ''
  }
}

function loadAppSettings() {
  const cache = uni.getStorageSync(SETTINGS_KEY)

  if (!cache) {
    Object.assign(appSettings, defaultAppSettings)
    return
  }

  try {
    const parsed = typeof cache === 'string' ? JSON.parse(cache) : cache
    Object.assign(appSettings, defaultAppSettings, parsed || {})
  } catch (e) {
    Object.assign(appSettings, defaultAppSettings)
  }
}

async function loadFavorites() {
  if (!loggedIn.value || !currentUserId.value) {
    favoriteList.value = []
    return
  }

  try {
    const cloudList = await getFavoriteList()
    const list = Array.isArray(cloudList) ? cloudList : []

    saveUserFavoriteCache(list)
    favoriteList.value = list.map((item, index) => normalizeFavoriteItem(item, index))
  } catch (error) {
    console.warn('个人中心收藏数量加载失败：', error)

    const cachedList = getUserFavoriteCache()
    favoriteList.value = cachedList.map((item, index) => normalizeFavoriteItem(item, index))
  }
}

function loadConsults() {
  const localList = getLocalConsults()

  consultList.value = localList.map((item, index) => ({
    id: item.id || `consult-${index}`,
    question: item.question || '',
    answer: item.answer || '',
    contextName: item.contextName || item.parkName || item.scenicName || item.sceneName || ''
  }))

  if (!loggedIn.value || !currentUserId.value) {
    return
  }

  request({
    url: '/api/app/user/consult/history',
    method: 'GET',
    data: {
      userId: currentUserId.value,
      limit: 100
    },
    showErrorToast: false
  })
    .then(response => {
      const data = response?.data || response || []
      const list = Array.isArray(data) ? data : (data.records || data.list || data.items || [])

      consultList.value = list.map((item, index) => ({
        id: item.id || item.messageId || item.message_id || `consult-${index}`,
        question: item.question || item.questionText || item.question_text || '',
        answer: item.answer || item.answerText || item.answer_text || '',
        contextName: item.parkName || item.park_name || item.scenicName || item.scenic_name || item.contextName || ''
      }))
    })
    .catch(error => {
      console.warn('个人中心咨询数量加载失败，使用本地缓存：', error)
    })
}

function getLocalConsults() {
  const cache = uni.getStorageSync(CONSULT_KEY)
  return safeParseArray(cache)
}

function loadLoginState() {
  loggedIn.value = isLogin()
  userInfo.value = getUserInfo()
  visitorId.value = getVisitorId()
  sessionId.value = getSessionId()
}

function loadPageData() {
  loadLoginState()
  loadAppSettings()
  loadFavorites()
  loadConsults()
}

function goLogin() {
  uni.navigateTo({
    url: '/pages/login/login'
  })
}

function handleProfileClick() {
  if (!loggedIn.value) {
    goLogin()
  }
}

function handleMenuClick(item) {
  trackClick(`点击${item.title}`, {
	entity_type: 'PAGE',
	entity_id: item.action,
	menu_title: item.title,
	menu_action: item.action
  })
	
  switch (item.action) {
    case 'favorites':
      uni.navigateTo({
        url: '/pages/mine/favorite'
      })
      break

    case 'payments':
      if (!loggedIn.value || !currentUserId.value) {
        uni.showToast({
          title: '请先登录',
          icon: 'none'
        })
        goLogin()
        return
      }

      uni.navigateTo({
        url: '/pages/pay/records'
      })
      break

    case 'consults':
      if (!appSettings.showConsultHistory) {
        uni.showToast({
          title: '咨询记录展示已关闭',
          icon: 'none'
        })
        return
      }

      uni.navigateTo({
        url: '/pages/mine/consult'
      })
      break

    case 'profile':
      uni.navigateTo({
        url: '/pages/mine/profile'
      })
      break

    case 'visitReport': {
      if (!loggedIn.value || !currentUserId.value) {
        uni.showToast({
          title: '请先登录后查看游玩报告',
          icon: 'none'
        })
        goLogin()
        return
      }
      const visitId = recentVisitId.value
      uni.navigateTo({
        url: visitId
          ? `/pages/visit/report?visitId=${encodeURIComponent(visitId)}`
          : '/pages/visit/report'
      })
      break
    }

    case 'account':
      openAccountDialog()
      break

    case 'settings':
      uni.navigateTo({
        url: '/pages/mine/settings'
      })
      break

    default:
      uni.showToast({
        title: '功能开发中',
        icon: 'none'
      })
  }
}

function shortId(value) {
  const text = String(value || '').trim()
  if (!text) return ''
  if (text.length <= 20) return text
  return `${text.slice(0, 8)}...${text.slice(-8)}`
}

function copyId(value) {
  const text = String(value || '').trim()

  if (!text) {
    uni.showToast({
      title: '暂无可复制内容',
      icon: 'none'
    })
    return
  }

  uni.setClipboardData({
    data: text,
    success: () => {
      uni.showToast({
        title: '已复制',
        icon: 'none'
      })
    }
  })
}

function openAccountDialog() {
  accountDialogVisible.value = true
}

function closeAccountDialog() {
  accountDialogVisible.value = false
}

function handleAboutTitleTap() {
  const now = Date.now()

  if (now - lastAboutTapAt.value > 1200) {
    aboutTapCount.value = 0
  }

  lastAboutTapAt.value = now
  aboutTapCount.value += 1

  if (aboutTapCount.value >= 5) {
    aboutTapCount.value = 0
    openAccountDialog()
  }
}

function handleLogout() {
  uni.showModal({
    title: '退出登录',
    content: '退出后仍可使用游客模式体验，收藏和咨询记录会先保留在本地。',
    confirmText: '退出',
    cancelText: '取消',
    success: res => {
      if (!res.confirm) return

      const logoutUserId = currentUserId.value || getCurrentUserId()
      clearLogin()
      clearVisitCacheForLogout(logoutUserId)
      loadLoginState()
      favoriteList.value = []

      uni.showToast({
        title: '已退出登录',
        icon: 'success'
      })
    }
  })
}

onShow(() => {
  loadPageData()
  uni.setStorageSync('lastNonGuideTab', '/pages/mine/mine')
  trackPageView('我的页面')
})
</script>

<style>
/* ============================================================
   即境 · 个人中心 — 数据概览 + 服务入口 + 游玩闭环
   设计方向：Clean APP Profile · 统一卡片 + 左蓝竖线标题
   签名元素：弱化圆装饰 · 统一菜单项 · 轻量弹窗
   本轮只改 CSS，不改 template / script / 任何业务入口
   ============================================================ */

/* ---------- Page ---------- */
.page {
  min-height: 100vh;
  background: #f5f7fb;
  padding: 24rpx;
  box-sizing: border-box;
}

/* ---------- Profile Card（蓝渐变 · 弱化装饰圆） ---------- */
.profile-card {
  background: linear-gradient(135deg, #2f80ed 0%, #409eef 100%);
  border-radius: 28rpx;
  padding: 28rpx 24rpx;
  color: #ffffff;
  position: relative;
  overflow: hidden;
  margin-bottom: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.08);
}

/* 弱化背景圆 */
.profile-bg-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.07);
}

.profile-bg-circle-1 {
  width: 200rpx;
  height: 200rpx;
  right: -20rpx;
  top: -20rpx;
}

.profile-bg-circle-2 {
  width: 140rpx;
  height: 140rpx;
  right: 100rpx;
  bottom: -40rpx;
}

.profile-top {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
}

.avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.18);
  color: #ffffff;
  font-size: 34rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 2rpx solid rgba(255, 255, 255, 0.3);
}

.profile-info {
  margin-left: 18rpx;
  flex: 1;
  min-width: 0;
}

.name {
  font-size: 32rpx;
  font-weight: 700;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.identity-line {
  margin-top: 4rpx;
  font-size: 22rpx;
  line-height: 1.4;
  opacity: 0.88;
}

.desc {
  margin-top: 6rpx;
  font-size: 22rpx;
  line-height: 1.5;
  opacity: 0.85;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.login-chip {
  position: relative;
  z-index: 2;
  padding: 10rpx 22rpx;
  border-radius: 999rpx;
  background: #ffffff;
  color: #2f80ed;
  font-size: 24rpx;
  font-weight: 700;
  margin-left: 14rpx;
  flex-shrink: 0;
  box-shadow: 0 4rpx 12rpx rgba(15, 23, 42, 0.1);
}

/* ---------- Profile Stats（三列数据 · 防溢出） ---------- */
.profile-stats {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 14rpx;
  margin-top: 22rpx;
}

.stat-item {
  flex: 1;
  background: rgba(255, 255, 255, 0.13);
  border-radius: 18rpx;
  padding: 16rpx 8rpx;
  text-align: center;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.stat-value {
  font-size: 30rpx;
  font-weight: 700;
  color: #ffffff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stat-label {
  margin-top: 6rpx;
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.82);
  white-space: nowrap;
}

/* ---------- Menu / About Card ---------- */
.menu-card,
.about-card {
  background: #ffffff;
  border-radius: 28rpx;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
  border: 1rpx solid #f3f4f6;
  padding: 24rpx;
  margin-bottom: 24rpx;
  overflow: hidden;
}

/* 左蓝竖线标题 — 与报告页、景点详情页统一 */
.section-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 18rpx;
  padding-left: 18rpx;
  border-left: 6rpx solid #2f80ed;
  line-height: 1.3;
}

/* ---------- Menu Items（正式 APP 列表项） ---------- */
.menu-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 22rpx 0;
  border-bottom: 1rpx solid #f3f4f6;
}

.menu-item:last-child {
  border-bottom: none;
}

.menu-left {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.menu-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 18rpx;
  background: #eff6ff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  flex-shrink: 0;
  border: 1rpx solid rgba(47, 128, 237, 0.06);
}

.menu-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 18rpx;
  background: #eff6ff;
  flex-shrink: 0;
}

.menu-text-wrap {
  margin-left: 16rpx;
  flex: 1;
  min-width: 0;
}

.menu-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-desc {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #9ca3af;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.menu-arrow {
  color: #c0c8d4;
  font-size: 32rpx;
  margin-left: 10rpx;
  flex-shrink: 0;
}

/* ---------- About ---------- */
.about-text {
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.8;
}

/* ---------- Logout ---------- */
.logout-card {
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  border-radius: 24rpx;
  background: #ffffff;
  color: #ef4444;
  font-size: 28rpx;
  font-weight: 600;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
  border: 1rpx solid #f3f4f6;
  margin-bottom: 24rpx;
}

/* ---------- Modal Mask ---------- */
.modal-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 99;
  background: rgba(15, 23, 42, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48rpx 32rpx;
  box-sizing: border-box;
}

/* ---------- Account Dialog ---------- */
.account-dialog {
  width: 100%;
  max-height: 80vh;
  overflow: auto;
  border-radius: 28rpx;
  background: #ffffff;
  padding: 28rpx;
  box-sizing: border-box;
  box-shadow: 0 16rpx 40rpx rgba(15, 23, 42, 0.12);
}

.dialog-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 20rpx;
}

.dialog-section {
  padding: 18rpx 0;
  border-top: 1rpx solid #f3f4f6;
}

.dialog-section-title {
  font-size: 24rpx;
  font-weight: 700;
  color: #374151;
  margin-bottom: 12rpx;
  padding-left: 14rpx;
  border-left: 4rpx solid #2f80ed;
}

.account-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  min-height: 50rpx;
  font-size: 24rpx;
}

.account-label {
  color: #6b7280;
  flex-shrink: 0;
  font-size: 24rpx;
}

.account-value {
  color: #1f2937;
  font-weight: 600;
  text-align: right;
  font-size: 24rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 60%;
}

.account-id-wrap {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12rpx;
  min-width: 0;
  flex: 1;
}

.account-id {
  color: #1f2937;
  font-size: 22rpx;
  font-family: monospace;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.copy-btn {
  flex-shrink: 0;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: #eff6ff;
  color: #2f80ed;
  font-size: 20rpx;
  font-weight: 600;
  border: 1rpx solid rgba(47, 128, 237, 0.08);
}

.dialog-tip {
  margin-top: 16rpx;
  padding: 16rpx 18rpx;
  border-radius: 14rpx;
  background: #f8fafc;
  color: #64748b;
  font-size: 22rpx;
  line-height: 1.6;
  border-left: 4rpx solid #93c5fd;
}

.dialog-confirm {
  height: 80rpx;
  line-height: 80rpx;
  margin-top: 24rpx;
  border-radius: 999rpx;
  background: #2f80ed;
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 600;
  text-align: center;
}
</style>
