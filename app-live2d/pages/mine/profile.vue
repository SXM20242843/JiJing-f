<template>
  <view class="page">
    <view v-if="!loggedIn" class="status-card card">
      <text class="status-title">登录后完善游玩偏好</text>
      <text class="status-desc">完善偏好后，AI 数字人会为你推荐更合适的讲解内容和游览路线。</text>
      <view class="primary-btn" @click="goLogin">去登录</view>
    </view>

    <template v-else>
      <view class="hero-card">
        <view class="hero-bg-circle hero-bg-circle-1"></view>
        <view class="hero-bg-circle hero-bg-circle-2"></view>
        <view class="hero-title">我的游玩偏好</view>
        <view class="hero-desc">
          即境会根据你的主动偏好，以及后续浏览、收藏、提问、路线和游玩行为，持续完善个性化推荐。
        </view>
      </view>

      <view v-if="loading" class="status-card card">
        <text class="status-desc">正在加载偏好...</text>
      </view>

      <template v-else>
        <view class="form-card card">
          <view class="section-title">兴趣偏好</view>
          <view class="option-row">
            <view
              class="option-chip"
              :class="{ active: form.interestTags.includes(item) }"
              v-for="item in interestTagOptions"
              :key="item"
              @click="toggleMulti('interestTags', item)"
            >
              {{ item }}
            </view>
          </view>
        </view>

        <view class="form-card card">
          <view class="section-title">出行习惯</view>

          <view class="field-block">
            <view class="field-label">出行节奏</view>
            <view class="option-row">
              <view
                class="option-chip"
                :class="{ active: form.travelPace === item.value }"
                v-for="item in travelPaceOptions"
                :key="item.value"
                @click="selectSingle('travelPace', item.value)"
              >
                {{ item.label }}
              </view>
            </view>
          </view>

          <view class="field-block">
            <view class="field-label">同行人群</view>
            <view class="option-row">
              <view
                class="option-chip"
                :class="{ active: form.companionType === item.value }"
                v-for="item in companionTypeOptions"
                :key="item.value"
                @click="selectSingle('companionType', item.value)"
              >
                {{ item.label }}
              </view>
            </view>
          </view>

          <view class="field-block">
            <view class="field-label">体力偏好</view>
            <view class="option-row">
              <view
                class="option-chip"
                :class="{ active: form.walkingPreference === item.value }"
                v-for="item in walkingPreferenceOptions"
                :key="item.value"
                @click="selectSingle('walkingPreference', item.value)"
              >
                {{ item.label }}
              </view>
            </view>
          </view>

          <view class="field-block">
            <view class="field-label">讲解偏好</view>
            <view class="option-row">
              <view
                class="option-chip"
                :class="{ active: form.guidePreference === item.value }"
                v-for="item in guidePreferenceOptions"
                :key="item.value"
                @click="selectSingle('guidePreference', item.value)"
              >
                {{ item.label }}
              </view>
            </view>
          </view>
        </view>

        <view class="form-card card">
          <view class="section-title">消费偏好</view>
          <view class="option-row">
            <view
              class="option-chip"
              :class="{ active: form.consumePreference.includes(item) }"
              v-for="item in consumePreferenceOptions"
              :key="item"
              @click="toggleMulti('consumePreference', item)"
            >
              {{ item }}
            </view>
          </view>
        </view>

        <view class="save-btn" :class="{ disabled: saving }" @click="saveProfile">
          {{ saving ? '保存中...' : '保存偏好' }}
        </view>
      </template>
    </template>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import request from '@/utils/request'
import { getCurrentUserId, isLogin, getUserInfo, setUserInfo } from '@/utils/auth'
import { markProfileCompleted } from '@/utils/profileOnboarding'

const interestTagOptions = [
  '历史文化',
  '自然风光',
  '亲子游',
  '佛教文化',
  '美食餐饮',
  '拍照打卡',
  '轻松游',
  '深度游'
]

const travelPaceOptions = [
  { label: '轻松慢游', value: 'slow' },
  { label: '标准游览', value: 'standard' },
  { label: '高效打卡', value: 'fast' }
]

const companionTypeOptions = [
  { label: '独自', value: 'solo' },
  { label: '朋友', value: 'friends' },
  { label: '情侣', value: 'couple' },
  { label: '家庭', value: 'family' },
  { label: '老人', value: 'elderly' },
  { label: '儿童', value: 'children' }
]

const walkingPreferenceOptions = [
  { label: '少走路', value: 'less_walk' },
  { label: '正常', value: 'normal' },
  { label: '可接受较长步行', value: 'long_walk' }
]

const guidePreferenceOptions = [
  { label: '简洁讲解', value: 'brief' },
  { label: '标准讲解', value: 'standard' },
  { label: '深度讲解', value: 'deep' }
]

const consumePreferenceOptions = [
  '餐饮',
  '文创',
  '门票',
  '纪念品',
  '暂不偏好'
]

const loading = ref(false)
const saving = ref(false)
const loggedIn = ref(false)
const fromOnboarding = ref(false)

const form = reactive({
  interestTags: [],
  travelPace: '',
  companionType: '',
  walkingPreference: '',
  guidePreference: '',
  consumePreference: []
})

function pickFirst(...values) {
  for (const value of values) {
    if (value === undefined || value === null || value === '') {
      continue
    }

    if (Array.isArray(value) && value.length === 0) {
      continue
    }

    return value
  }

  return ''
}

function normalizeArray(value) {
  if (Array.isArray(value)) return value
  if (!value) return []

  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value)
      if (Array.isArray(parsed)) return parsed
    } catch (error) {
      return value
        .replace(/，|、/g, ',')
        .split(',')
        .map(item => item.trim())
        .filter(Boolean)
    }
  }

  return []
}

function parseJsonValue(value) {
  if (!value) return null

  if (typeof value === 'object') {
    return value
  }

  if (typeof value !== 'string') {
    return null
  }

  try {
    return JSON.parse(value)
  } catch (error) {
    return null
  }
}

function normalizeEnumText(value) {
  return String(value || '').trim().toUpperCase()
}

function normalizeTravelPaceValue(value) {
  const text = String(value || '').trim()
  const enumText = normalizeEnumText(text)
  const map = {
    RELAXED: 'slow',
    DEEP: 'standard',
    FAST: 'fast',
    PARENT_CHILD: 'standard',
    ELDERLY: 'slow'
  }

  return map[enumText] || text
}

function normalizeWalkingPreferenceValue(value) {
  const text = String(value || '').trim()
  const enumText = normalizeEnumText(text)
  const map = {
    LOW: 'less_walk',
    MIDDLE: 'normal',
    HIGH: 'long_walk'
  }

  return map[enumText] || text
}

function normalizeCompanionTypeValue(value) {
  const text = String(value || '').trim()
  const enumText = normalizeEnumText(text)
  const map = {
    ALONE: 'solo',
    FRIENDS: 'friends',
    COUPLE: 'couple',
    FAMILY: 'family',
    ELDERLY: 'elderly',
    CHILD: 'children'
  }

  return map[enumText] || text
}

function readGuidePreference(data = {}) {
  const direct = pickFirst(data.guidePreference, data.guide_preference)

  if (direct) {
    return direct
  }

  const accessibilityNeed = parseJsonValue(data.accessibility_need || data.accessibilityNeed)

  if (!accessibilityNeed || Array.isArray(accessibilityNeed)) {
    return ''
  }

  return pickFirst(
    accessibilityNeed.guidePreference,
    accessibilityNeed.guide_preference
  )
}

function unwrapResponse(response) {
  if (!response) return null

  if (response.success === false) {
    throw new Error(response.msg || response.message || '请求失败')
  }

  if (
    Object.prototype.hasOwnProperty.call(response, 'code') &&
    response.code !== 0 &&
    response.code !== 200
  ) {
    throw new Error(response.msg || response.message || '请求失败')
  }

  return Object.prototype.hasOwnProperty.call(response, 'data')
    ? response.data
    : response
}

function getProfileCacheKey() {
  const userId = getCurrentUserId()
  return userId ? `USER_PROFILE_${userId}` : ''
}

function getCachedProfile() {
  const key = getProfileCacheKey()
  if (!key) return null

  const cache = uni.getStorageSync(key)
  if (!cache) return null

  if (typeof cache === 'object') {
    return cache
  }

  try {
    return JSON.parse(cache)
  } catch (error) {
    return null
  }
}

function saveCachedProfile(profile = {}) {
  const key = getProfileCacheKey()
  if (!key) return
  uni.setStorageSync(key, profile)
}

function normalizeProfile(data = {}) {
  return {
    interestTags: normalizeArray(pickFirst(data.interestTags, data.interest_tags)),
    travelPace: normalizeTravelPaceValue(pickFirst(
      data.travelPace,
      data.travel_pace,
      data.travelStyle,
      data.travel_style
    )),
    companionType: normalizeCompanionTypeValue(pickFirst(
      data.companionType,
      data.companion_type
    )),
    walkingPreference: normalizeWalkingPreferenceValue(pickFirst(
      data.walkingPreference,
      data.walking_preference,
      data.physicalLevel,
      data.physical_level
    )),
    guidePreference: readGuidePreference(data),
    consumePreference: normalizeArray(pickFirst(
      data.consumePreference,
      data.consume_preference,
      data.foodPreference,
      data.food_preference
    ))
  }
}

function applyProfile(data = {}) {
  const profile = normalizeProfile(data)

  form.interestTags = profile.interestTags
  form.travelPace = profile.travelPace
  form.companionType = profile.companionType
  form.walkingPreference = profile.walkingPreference
  form.guidePreference = profile.guidePreference
  form.consumePreference = profile.consumePreference
}

function selectSingle(key, value) {
  form[key] = form[key] === value ? '' : value
}

function toggleMulti(key, value) {
  const list = Array.isArray(form[key]) ? form[key] : []

  if (list.includes(value)) {
    form[key] = list.filter(item => item !== value)
    return
  }

  if (key === 'consumePreference' && value === '暂不偏好') {
    form[key] = ['暂不偏好']
    return
  }

  const nextList = key === 'consumePreference'
    ? list.filter(item => item !== '暂不偏好')
    : list

  form[key] = [...nextList, value]
}

function buildPayload() {
  const userId = getCurrentUserId() || ''

  return {
    userId,
    user_id: userId,
    interestTags: form.interestTags,
    interest_tags: form.interestTags,
    travelPace: form.travelPace,
    travel_pace: form.travelPace,
    companionType: form.companionType,
    companion_type: form.companionType,
    walkingPreference: form.walkingPreference,
    walking_preference: form.walkingPreference,
    guidePreference: form.guidePreference,
    guide_preference: form.guidePreference,
    consumePreference: form.consumePreference,
    consume_preference: form.consumePreference
  }
}

async function loadProfile() {
  loggedIn.value = isLogin()

  if (!loggedIn.value) {
    loading.value = false
    return
  }

  const userId = getCurrentUserId()
  if (!userId) {
    loading.value = false
    uni.showToast({
      title: '加载失败',
      icon: 'none'
    })
    return
  }

  loading.value = true

  try {
    const response = await request({
      url: `/api/user/profile?userId=${encodeURIComponent(userId)}`,
      method: 'GET',
      needAuth: true,
      showErrorToast: false
    })
    const data = unwrapResponse(response) || {}
    applyProfile(data)
    saveCachedProfile(data)
  } catch (error) {
    console.warn('用户偏好加载失败，使用本地缓存：', error)
    applyProfile(getCachedProfile() || {})
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  if (saving.value) {
    return
  }

  if (!isLogin() || !getCurrentUserId()) {
    uni.showToast({
      title: '请先登录',
      icon: 'none'
    })
    return
  }

  saving.value = true

  try {
    const payload = buildPayload()
    const response = await request({
      url: '/api/user/profile/save',
      method: 'POST',
      data: payload,
      needAuth: true,
      showErrorToast: false
    })

    const savedProfile = unwrapResponse(response) || payload
    applyProfile(savedProfile)
    saveCachedProfile({
      ...savedProfile,
      profileCompleted: true
    })
    markProfileCompleted(payload.userId)
    patchLocalUserProfileCompleted()

    uni.showToast({
      title: '偏好已保存',
      icon: 'success'
    })

    if (fromOnboarding.value) {
      setTimeout(() => {
        uni.switchTab({
          url: '/pages/mine/mine'
        })
      }, 600)
      return
    }
  } catch (error) {
    console.warn('用户偏好保存失败：', error)
    uni.showToast({
      title: '保存失败，请稍后重试',
      icon: 'none'
    })
  } finally {
    saving.value = false
  }
}


function patchLocalUserProfileCompleted() {
  const userInfo = getUserInfo() || {}
  setUserInfo({
    ...userInfo,
    profileCompleted: true,
    profile_completed: true,
    hasProfile: true,
    has_profile: true,
    profile_status: 'completed',
    profileStatus: 'completed'
  })
}

function goLogin() {
  uni.navigateTo({
    url: '/pages/login/login'
  })
}

onLoad((options = {}) => {
  fromOnboarding.value = options.from === 'onboarding'
  loadProfile()
})

onShow(() => {
  const wasLoggedIn = loggedIn.value
  loggedIn.value = isLogin()

  if (loggedIn.value && (!wasLoggedIn || !saving.value)) {
    loadProfile()
  }
})
</script>

<style>
/* ============================================================
   即境 · 我的偏好 — 用户画像 + 个性化推荐
   设计方向：Clean APP Settings · 统一卡片 + chip 选择器
   签名元素：左蓝竖线标题 · 统一 chip 高度 · 选中态绿色
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
.card {
  background: #ffffff;
  border-radius: 28rpx;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
  border: 1rpx solid #f3f4f6;
}

/* ---------- Hero Card（蓝渐变 · 弱化装饰） ---------- */
.hero-card {
  position: relative;
  overflow: hidden;
  border-radius: 28rpx;
  padding: 28rpx;
  margin-bottom: 24rpx;
  background: linear-gradient(135deg, #2f80ed 0%, #409eef 100%);
  color: #ffffff;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.08);
}

.hero-bg-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.07);
}

.hero-bg-circle-1 {
  width: 180rpx;
  height: 180rpx;
  right: -20rpx;
  top: -20rpx;
}

.hero-bg-circle-2 {
  width: 130rpx;
  height: 130rpx;
  right: 100rpx;
  bottom: -40rpx;
}

.hero-title,
.hero-desc {
  position: relative;
  z-index: 1;
}

.hero-title {
  font-size: 34rpx;
  font-weight: 700;
  line-height: 1.35;
}

.hero-desc {
  margin-top: 10rpx;
  font-size: 24rpx;
  line-height: 1.6;
  opacity: 0.88;
}

/* ---------- Status Card（空态 / 加载） ---------- */
.status-card {
  padding: 60rpx 28rpx 52rpx;
  text-align: center;
  margin-bottom: 24rpx;
}

.status-title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #1f2937;
}

.status-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.65;
}

/* ---------- Buttons（主按钮 · 绿色统一） ---------- */
.primary-btn,
.save-btn {
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 600;
  text-align: center;
}

.primary-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 180rpx;
  padding: 0 36rpx;
  margin-top: 24rpx;
  background: linear-gradient(135deg, #18b368 0%, #16a34a 100%);
  color: #ffffff;
  box-shadow: 0 4rpx 12rpx rgba(24, 179, 104, 0.18);
}

.save-btn {
  background: linear-gradient(135deg, #18b368 0%, #16a34a 100%);
  color: #ffffff;
  box-shadow: 0 4rpx 12rpx rgba(24, 179, 104, 0.18);
  margin-bottom: 24rpx;
}

.save-btn.disabled {
  opacity: 0.5;
}

/* ---------- Form Card ---------- */
.form-card {
  padding: 24rpx;
  margin-bottom: 20rpx;
}

/* 左蓝竖线标题 — 与报告页、景点详情页统一 */
.section-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 20rpx;
  padding-left: 18rpx;
  border-left: 6rpx solid #2f80ed;
  line-height: 1.3;
}

/* ---------- Field Block ---------- */
.field-block {
  margin-top: 28rpx;
}

.field-block:first-of-type {
  margin-top: 0;
}

.field-label {
  margin-bottom: 14rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: #374151;
}

/* ---------- Option Row & Chip ---------- */
.option-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.option-chip {
  min-height: 58rpx;
  line-height: 58rpx;
  padding: 0 22rpx;
  border-radius: 999rpx;
  background: #f3f4f6;
  color: #4b5563;
  font-size: 24rpx;
  font-weight: 500;
  text-align: center;
  box-sizing: border-box;
  border: 1rpx solid transparent;
}

/* 选中态 — 绿色 */
.option-chip.active {
  background: #ecfdf5;
  color: #16a34a;
  font-weight: 600;
  border-color: rgba(22, 163, 74, 0.2);
}
</style>
