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

function isProfileCompleted(userInfo = getUserInfo(), userId = getCurrentUserId()) {
  const backendFlag = readProfileFlag(userInfo || {})

  if (backendFlag !== null) {
    return backendFlag
  }

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

function markProfileCompleted(userId = getCurrentUserId()) {
  if (!userId) return

  uni.setStorageSync(getStorageKey(PROFILE_COMPLETED_PREFIX, userId), true)

  const userInfo = getUserInfo() || {}
  setUserInfo({
    ...userInfo,
    profileCompleted: true,
    hasProfile: true,
    profile_status: 'completed'
  })
}

function shouldPromptProfile({ source = 'login', userInfo = getUserInfo(), userId = getCurrentUserId() } = {}) {
  if (!userId) return false
  if (isProfileCompleted(userInfo, userId)) return false
  if (source === 'register') return true
  return !hasPromptedProfile(userId)
}

function showProfileOnboarding(options = {}) {
  const userId = options.userId || getCurrentUserId()

  if (!shouldPromptProfile({
    source: options.source || 'login',
    userInfo: options.userInfo || getUserInfo(),
    userId
  })) {
    return Promise.resolve(false)
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
  showProfileOnboarding
}
