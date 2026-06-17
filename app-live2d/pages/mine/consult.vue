<template>
  <view class="page">
    <view class="top-card">
      <view class="top-title">咨询记录</view>
      <view class="top-desc">
        {{ loading ? '正在同步最近的 AI 导览问答记录' : `共 ${consultList.length} 条记录，可继续追问或重新进入 AI 导览` }}
      </view>

      <view class="top-actions">
        <view class="top-btn ghost" @click="goGuide">去 AI 助手</view>
        <view v-if="hasLocalConsults" class="top-btn" @click="clearConsults">清空记录</view>
      </view>
    </view>
	
	<view v-if="!canShowConsultHistory" class="empty-card">
	  <view class="empty-title">咨询记录展示已关闭</view>
	  <view class="empty-desc">
	    你已在系统设置中关闭“显示咨询记录”，当前页面不再展示历史问答内容。
	  </view>
	  <view class="empty-btn" @click="goGuide">去 AI 助手</view>
	</view>

    <view v-else-if="consultList.length === 0" class="empty-card">
      <view class="empty-title">暂无咨询记录</view>
      <view class="empty-desc">
        暂无咨询记录，向 AI 数字人提问后会自动保存到这里。
      </view>
      <view class="empty-btn" @click="goGuide">去发起咨询</view>
    </view>

    <view v-else class="list">
      <view class="consult-card card" v-for="item in consultList" :key="item.id">
        <view class="consult-question">
          问：{{ item.question || '未记录问题' }}
        </view>

        <view class="consult-answer">
          答：{{ formatAnswerSummary(item.answer) || '暂无回答内容' }}
        </view>

        <view class="consult-meta">
          <text>{{ item.contextName || 'AI导览' }}</text>
          <text>{{ item.sourceName || 'AI 数字人导览' }}</text>
          <text>{{ item.timeText || '' }}</text>
        </view>

        <view class="action-row">
          <view class="pill primary" @click="continueConsult(item)">继续咨询</view>
          <view class="pill" @click="askAgain(item)">再次提问</view>
          <view v-if="!item.cloud" class="pill danger" @click="removeConsult(item)">删除</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request from '@/utils/request'
import { getCurrentUserId, isLogin } from '@/utils/auth'

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

const consultList = ref([])
const loading = ref(false)

const canShowConsultHistory = computed(() => {
  return appSettings.showConsultHistory !== false
})

const hasLocalConsults = computed(() => {
  return getRawConsultList().length > 0
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

function formatTime(value) {
  if (!value) return ''

  try {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return String(value)

    const month = `${date.getMonth() + 1}`.padStart(2, '0')
    const day = `${date.getDate()}`.padStart(2, '0')
    const hour = `${date.getHours()}`.padStart(2, '0')
    const minute = `${date.getMinutes()}`.padStart(2, '0')

    return `${month}-${day} ${hour}:${minute}`
  } catch (e) {
    return String(value)
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

function getRawConsultList() {
  const cache = uni.getStorageSync(CONSULT_KEY)
  return safeParseArray(cache)
}

function loadConsults() {
  const list = getRawConsultList()

  consultList.value = list
    .map((item, index) => normalizeConsultItem(item, index, false))
    .reverse()

  loadCloudConsults()
}

async function loadCloudConsults() {
  if (!isLogin() || !getCurrentUserId()) {
    return
  }

  loading.value = true

  try {
    const response = await request({
      url: '/api/app/user/consult/history',
      method: 'GET',
      data: {
        userId: getCurrentUserId(),
        limit: 50
      },
      showErrorToast: false
    })

    const data = response?.data || response || []
    const list = Array.isArray(data) ? data : (data.records || data.list || data.items || [])
    consultList.value = list.map((item, index) => normalizeConsultItem(item, index, true))
  } catch (error) {
    console.warn('咨询记录云端加载失败，使用本地缓存：', error)
  } finally {
    loading.value = false
  }
}

function normalizeConsultItem(item = {}, index = 0, cloud = false) {
  return {
    raw: item,
    cloud,
    id: item.id || item.messageId || item.message_id || `consult-${index}`,
    question: item.question || item.questionText || item.question_text || '',
    answer: item.answer || item.answerText || item.answer_text || '',
    contextName:
      item.contextName ||
      item.parkName ||
      item.park_name ||
      item.scenicName ||
      item.scenic_name ||
      item.sceneName ||
      item.scene_name ||
      '',
    sourceName: item.sourceName || item.source_name || mapSourceName(item.source, item.inputType || item.input_type),
    timeText: formatTime(item.createdAt || item.created_at || item.time || item.timestamp),
    createdAt: item.createdAt || item.created_at || item.time || item.timestamp || ''
  }
}

function mapSourceName(source = '', inputType = '') {
  const sourceText = String(source || '')
  const inputText = String(inputType || '')

  if (inputText === 'voice' || sourceText.includes('voice')) {
    return '语音问答'
  }

  if (sourceText === 'app-guide') {
    return '景区咨询'
  }

  return 'AI 数字人导览'
}

function formatAnswerSummary(answer = '') {
  const text = String(answer || '').trim()
  if (text.length <= 80) return text
  return `${text.slice(0, 80)}...`
}

function saveConsults(rawList) {
  uni.setStorageSync(CONSULT_KEY, JSON.stringify(rawList))
}

function clearConsults() {
  if (!canShowConsultHistory.value) {
    uni.showToast({
      title: '咨询记录展示已关闭',
      icon: 'none'
    })
    return
  }

  if (!consultList.value.length) {
    uni.showToast({
      title: '当前没有咨询记录',
      icon: 'none'
    })
    return
  }

  uni.showModal({
    title: '提示',
    content: '确定清空全部咨询记录吗？',
    success: (res) => {
      if (!res.confirm) return

      uni.removeStorageSync(CONSULT_KEY)
      consultList.value = []

      uni.showToast({
        title: '已清空咨询记录',
        icon: 'none'
      })
    }
  })
}

function removeConsult(item) {
  if (!canShowConsultHistory.value) {
    uni.showToast({
      title: '咨询记录展示已关闭',
      icon: 'none'
    })
    return
  }

  const rawList = getRawConsultList()
  const nextList = rawList.filter((row, index) => {
    const rowId = row.id || `consult-${index}`
    return rowId !== item.id
  })

  saveConsults(nextList)
  loadConsults()

  uni.showToast({
    title: '已删除记录',
    icon: 'none'
  })
}

function continueConsult(item) {
  if (!canShowConsultHistory.value) {
    uni.showToast({
      title: '咨询记录展示已关闭',
      icon: 'none'
    })
    return
  }

  if (item.contextName) {
    uni.setStorageSync('selectedScenicName', item.contextName)
    uni.setStorageSync('aiContextName', item.contextName)
  }

  if (item.question) {
    uni.setStorageSync('aiAutoQuestion', item.question)
  }

  uni.switchTab({
    url: '/pages/guide/guide'
  })
}

function askAgain(item) {
  if (!canShowConsultHistory.value) {
    uni.showToast({
      title: '咨询记录展示已关闭',
      icon: 'none'
    })
    return
  }

  const question = item.question || '请继续为我介绍相关内容'

  if (item.contextName) {
    uni.setStorageSync('selectedScenicName', item.contextName)
    uni.setStorageSync('aiContextName', item.contextName)
  }

  uni.setStorageSync('aiAutoQuestion', question)

  uni.switchTab({
    url: '/pages/guide/guide'
  })
}

function goGuide() {
  uni.switchTab({
    url: '/pages/guide/guide'
  })
}

onShow(() => {
  loadAppSettings()
  loadConsults()
})
</script>

<style>
/* ============================================================
   即境 · 咨询记录 — AI 问答历史 + 继续追问
   设计方向：Clean APP History · 统一卡片 + Q&A 层级
   签名元素：左蓝竖线标题 · Q/A 区分 · 统一 pill 行
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

/* ---------- Top Card ---------- */
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
  background: #fef2f2;
  color: #ef4444;
  font-size: 24rpx;
  font-weight: 500;
  text-align: center;
  border: 1rpx solid rgba(239, 68, 68, 0.1);
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

/* ---------- Consult Card ---------- */
.consult-card {
  padding: 24rpx;
}

/* 问 — 深色突出 */
.consult-question {
  font-size: 26rpx;
  color: #1f2937;
  font-weight: 600;
  line-height: 1.6;
}

/* 答 — 浅灰区分 · 最多 3 行省略 */
.consult-answer {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* Meta — 弱化 · 允许换行但不凌乱 */
.consult-meta {
  margin-top: 12rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
  font-size: 20rpx;
  color: #9ca3af;
}

/* 按钮行 — nowrap 防跳 */
.action-row {
  margin-top: 16rpx;
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

/* 继续咨询 — 绿色渐变主按钮 */
.pill.primary {
  background: linear-gradient(135deg, #18b368 0%, #16a34a 100%);
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 2rpx 8rpx rgba(24, 179, 104, 0.15);
}

/* 删除 — 红色语义弱化 */
.pill.danger {
  background: #fef2f2;
  color: #ef4444;
  border: 1rpx solid rgba(239, 68, 68, 0.1);
}
</style>
