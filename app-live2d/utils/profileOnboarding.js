import request from './request'
import {
  getCurrentUserId,
  getUserInfo,
  setUserInfo
} from './auth'

const PROFILE_COMPLETED_PREFIX = 'PROFILE_COMPLETED_'
const PROFILE_PROMPTED_PREFIX = 'PROFILE_PROMPTED_'

function getStorageKey(prefix, userId = '') {
  return `${prefix}${userId || getCurrentUserId() || ''}`
}

function normalizeBool(value) {
  if (value === true || value === 1) return true
  if (value === false || value === 0) return false

  if (typeof value === 'string') {
    const text = value.trim().toLowerCase()

    if (['true', '1', 'completed', 'complete', 'done', 'yes'].includes(text)) {
      return true
    }

    if (['false', '0', 'pending', 'incomplete', 'none', 'no'].includes(text)) {
      return false
    }
  }

  return null
}

function pickFirstFilledValue(...values) {
  for (const value of values) {
    if (value === undefined || value === null) continue

    if (Array.isArray(value)) {
      if (value.length > 0) return value
      continue
    }

    if (typeof value === 'string') {
      const trimmed = value.trim()
      if (trimmed && trimmed !== 'null' && trimmed !== 'undefined') return trimmed
      continue
    }

    if (value !== '') return value
  }

  return ''
}

function unwrapResponse(response) {
  if (!response) return {}

  const body = response.data !== undefined ? response.data : response

  if (
    body &&
    typeof body === 'object' &&
    body.data &&
    typeof body.data === 'object' &&
    (
      Object.prototype.hasOwnProperty.call(body, 'code') ||
      Object.prototype.hasOwnProperty.call(body, 'success') ||
      Object.prototype.hasOwnProperty.call(body, 'msg') ||
      Object.prototype.hasOwnProperty.call(body, 'message')
    )
  ) {
    return body.data
  }

  return body || {}
}

function readProfileFlag(userInfo = {}) {
  const candidates = [
    userInfo.profileCompleted,
    userInfo.profile_completed,
    userInfo.hasProfile,
    userInfo.has_profile,
    userInfo.profile_status,
    userInfo.profileStatus,
    userInfo.userInfo?.profileCompleted,
    userInfo.userInfo?.profile_completed,
    userInfo.data?.userInfo?.profileCompleted,
    userInfo.data?.userInfo?.profile_completed
  ]

  for (const item of candidates) {
    const normalized = normalizeBool(item)

    if (normalized !== null) {
      return normalized
    }
  }

  return null
}

function isNonEmptyArray(value) {
  return Array.isArray(value) && value.some(item => String(item || '').trim())
}

function isNonEmptyText(value) {
  return typeof value === 'string' && value.trim() && value.trim() !== 'null' && value.trim() !== 'undefined'
}

function hasMeaningfulProfile(profile = {}) {
  const data = unwrapResponse(profile)
  const completedFlag = normalizeBool(
    pickFirstFilledValue(
      data.profileCompleted,
      data.profile_completed,
      data.hasProfile,
      data.has_profile,
      data.profileStatus,
      data.profile_status
    )
  )

  if (completedFlag === true) {
    return true
  }

  return isNonEmptyArray(data.interestTags || data.interest_tags)
    || isNonEmptyArray(data.consumePreference || data.consume_preference)
    || isNonEmptyText(data.travelPace || data.travel_pace)
    || isNonEmptyText(data.companionType || data.companion_type)
    || isNonEmptyText(data.walkingPreference || data.walking_preference)
    || isNonEmptyText(data.guidePreference || data.guide_preference)
    || isNonEmptyText(data.travelPreference || data.travel_preference)
    || isNonEmptyText(data.consumptionLevel || data.consumption_level)
}

function isProfileCompleted(userInfo = getUserInfo(), userId = getCurrentUserId()) {
  const backendFlag = readProfileFlag(userInfo || {})

  if (backendFlag === true) {
    return true
  }

  // 注意：登录接口有时没有真正返回画像状态，前端 parse 时可能默认塞 false。
  // 因此这里不要因为 false 就直接否定本地已完成标记，避免老用户退出登录后再次被强制弹窗。
  if (!userId) {
    return false
  }

  return uni.getStorageSync(getStorageKey(PROFILE_COMPLETED_PREFIX, userId)) === true
}

function hasPromptedProfile(userId = getCurrentUserId()) {
  if (!userId) return false
  return uni.getStorageSync(getStorageKey(PROFILE_PROMPTED_PREFIX, userId)) === true
}

function markProfilePrompted(userId = getCurrentUserId()) {
  if (!userId) return
  uni.setStorageSync(getStorageKey(PROFILE_PROMPTED_PREFIX, userId), true)
}

function markProfileCompleted(userId = getCurrentUserId(), profile = {}) {
  if (!userId) return

  uni.setStorageSync(getStorageKey(PROFILE_COMPLETED_PREFIX, userId), true)

  const userInfo = getUserInfo() || {}
  const profileData = unwrapResponse(profile)

  setUserInfo({
    ...userInfo,
    ...profileData,
    user_id: userInfo.user_id || profileData.user_id || profileData.userId || userId,
    userId: userInfo.userId || profileData.userId || profileData.user_id || userId,
    profileCompleted: true,
    profile_completed: true,
    hasProfile: true,
    has_profile: true,
    profile_status: 'completed'
  })
}

async function fetchBackendProfile(userId) {
  if (!userId) return {}

  const response = await request({
    url: `/api/user/profile?userId=${encodeURIComponent(userId)}`,
    method: 'GET',
    needAuth: true,
    showErrorToast: false
  })

  return unwrapResponse(response)
}

async function shouldPromptProfileAsync({ source = 'login', userInfo = getUserInfo(), userId = getCurrentUserId() } = {}) {
  if (!userId) return false

  if (isProfileCompleted(userInfo, userId)) {
    return false
  }

  try {
    const backendProfile = await fetchBackendProfile(userId)

    if (hasMeaningfulProfile(backendProfile)) {
      markProfileCompleted(userId, backendProfile)
      return false
    }
  } catch (error) {
    console.warn('检查用户画像完成状态失败，降级使用本地标记：', error)
  }

  if (source === 'register') return true

  return !hasPromptedProfile(userId)
}

function shouldPromptProfile({ source = 'login', userInfo = getUserInfo(), userId = getCurrentUserId() } = {}) {
  if (!userId) return false
  if (isProfileCompleted(userInfo, userId)) return false
  if (source === 'register') return true
  return !hasPromptedProfile(userId)
}

async function showProfileOnboarding(options = {}) {
  const userId = options.userId || getCurrentUserId()
  const shouldPrompt = await shouldPromptProfileAsync({
    source: options.source || 'login',
    userInfo: options.userInfo || getUserInfo(),
    userId
  })

  if (!shouldPrompt) {
    return false
  }

  return new Promise(resolve => {
    uni.showModal({
      title: '完善游玩偏好',
      content: '告诉即境你的游玩兴趣和出行习惯，AI 数字人会为你推荐更合适的讲解内容和游览路线。',
      confirmText: '去完善',
      cancelText: '稍后再说',
      success: res => {
        if (res.confirm) {
          resolve(true)
          return
        }

        markProfilePrompted(userId)
        resolve(false)
      },
      fail: () => {
        markProfilePrompted(userId)
        resolve(false)
      }
    })
  })
}

export {
  isProfileCompleted,
  hasPromptedProfile,
  markProfilePrompted,
  markProfileCompleted,
  shouldPromptProfile,
  shouldPromptProfileAsync,
  showProfileOnboarding
}
