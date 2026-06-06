// common/onsite-guide.js

import {
  getCurrentLocation,
  matchCurrentScenicFence
} from './location-utils.js'

export const ONSITE_GUIDE_CONTEXT_KEY = 'ONSITE_GUIDE_CONTEXT'
export const CURRENT_ONSITE_STATUS_KEY = 'CURRENT_ONSITE_STATUS'

const LAST_PROMPT_KEY = 'LAST_ONSITE_GUIDE_PROMPT'
const CHECK_LOCK_KEY = 'ONSITE_LOCATION_CHECK_LOCK'
const ENTER_GUIDE_LOCK_KEY = 'ONSITE_ENTER_GUIDE_LOCK'

// 现场导览弹窗是否正在显示，防止连续弹多个 modal
let promptShowing = false

function getCurrentPageRoute() {
  const pages = getCurrentPages()

  if (!pages || pages.length === 0) {
    return ''
  }

  const currentPage = pages[pages.length - 1]
  return currentPage?.route || ''
}

/**
 * 这些页面不主动弹现场导览提示。
 * 目的：避免从原生页/地图页/我的页/登录页切换时，App.vue onShow 再次触发弹窗，导致页面栈混乱。
 */
function isSilentPage() {
  const route = getCurrentPageRoute()

  return [
    'pages/guide/guide',
    'pages/map/map',
    'pages/mine/mine',
    'pages/login/login',
    'pages/user/login',
    'pages/user/register'
  ].includes(route)
}

function isCheckingLocked() {
  const lockTime = uni.getStorageSync(CHECK_LOCK_KEY)

  if (!lockTime) {
    return false
  }

  const diff = Date.now() - Number(lockTime)

  // 8 秒内不重复触发定位检查
  return diff < 8 * 1000
}

function setCheckingLock() {
  uni.setStorageSync(CHECK_LOCK_KEY, Date.now())
}

function isEnterGuideLocked() {
  const lockTime = uni.getStorageSync(ENTER_GUIDE_LOCK_KEY)

  if (!lockTime) {
    return false
  }

  const diff = Date.now() - Number(lockTime)

  // 3 秒内不重复进入原生 AI 导览
  return diff < 3 * 1000
}

function setEnterGuideLock() {
  uni.setStorageSync(ENTER_GUIDE_LOCK_KEY, Date.now())
}

function canShowPrompt(parkId) {
  const lastPrompt = uni.getStorageSync(LAST_PROMPT_KEY)

  if (!lastPrompt || !lastPrompt.parkId || !lastPrompt.time) {
    return true
  }

  const diff = Date.now() - Number(lastPrompt.time)

  // 同一个景区 10 分钟内不重复弹窗
  if (lastPrompt.parkId === parkId && diff < 10 * 60 * 1000) {
    return false
  }

  return true
}

function markPromptShown(parkId) {
  uni.setStorageSync(LAST_PROMPT_KEY, {
    parkId,
    time: Date.now()
  })
}

export function saveOnsiteGuideContext(context) {
  uni.setStorageSync(ONSITE_GUIDE_CONTEXT_KEY, context)
}

export function takeOnsiteGuideContext() {
  const context = uni.getStorageSync(ONSITE_GUIDE_CONTEXT_KEY)

  if (context) {
    uni.removeStorageSync(ONSITE_GUIDE_CONTEXT_KEY)
  }

  return context || null
}

export function getCurrentOnsiteStatus() {
  return uni.getStorageSync(CURRENT_ONSITE_STATUS_KEY) || {
    inside: false
  }
}

function saveCurrentOnsiteStatus(status) {
  uni.setStorageSync(CURRENT_ONSITE_STATUS_KEY, {
    ...status,
    updateTime: Date.now()
  })
}

export function clearOnsiteGuideRuntimeLocks() {
  try {
    uni.removeStorageSync(CHECK_LOCK_KEY)
    uni.removeStorageSync(ENTER_GUIDE_LOCK_KEY)
    promptShowing = false
  } catch (error) {
    console.log('清理现场导览运行锁失败：', error)
  }
}

export function enterOnsiteGuide(context) {
  if (!context) {
    uni.showToast({
      title: '暂无现场导览信息',
      icon: 'none'
    })
    return
  }

  if (isEnterGuideLocked()) {
    console.log('进入现场导览过于频繁，跳过')
    return
  }

  setEnterGuideLock()
  saveOnsiteGuideContext(context)

  uni.switchTab({
    url: '/pages/guide/guide',
    fail(err) {
      console.log('跳转 AI 导览 tab 失败：', err)
      uni.showToast({
        title: '无法进入 AI 导览页',
        icon: 'none'
      })
    }
  })
}

/**
 * 根据 GPS 检查是否进入景区
 */
export async function checkOnsiteGuideByLocation(options = {}) {
  // #ifndef APP-PLUS
  return
  // #endif

  const { showPrompt = true, force = false } = options

  // 地图页、我的页、登录页、AI 导览页不主动弹现场导览提示
  if (!force && showPrompt && isSilentPage()) {
    console.log('当前页面不弹现场导览提示：', getCurrentPageRoute())
    return getCurrentOnsiteStatus()
  }

  if (!force && isCheckingLocked()) {
    console.log('现场导览定位检查过于频繁，跳过')
    return getCurrentOnsiteStatus()
  }

  setCheckingLock()

  try {
    const location = await getCurrentLocation()
    const matchedScenic = matchCurrentScenicFence(location)

    if (!matchedScenic) {
      console.log('当前未进入景区范围：', location)

      const status = {
        inside: false,
        location
      }

      saveCurrentOnsiteStatus(status)

      return status
    }

    const context = {
      mode: 'onsite',
      trigger: 'gps',
      parkId: matchedScenic.parkId,
      parkName: matchedScenic.parkName,
      distance: matchedScenic.distance,
      latitude: location.latitude,
      longitude: location.longitude,
      enterTime: Date.now(),
      autoQuestion: `我已经到达${matchedScenic.parkName}，请以现场导游的身份为我进行欢迎讲解，并介绍接下来可以怎么玩。`
    }

    const status = {
      inside: true,
      scenic: matchedScenic,
      location,
      context
    }

    saveCurrentOnsiteStatus(status)

    if (!showPrompt) {
      return status
    }

    // 静默页面只更新状态，不弹窗
    if (!force && isSilentPage()) {
      console.log('当前页面只更新现场导览状态，不弹窗：', getCurrentPageRoute())
      return status
    }

    if (!canShowPrompt(matchedScenic.parkId)) {
      console.log('现场导览提示过于频繁，跳过弹窗')
      return status
    }

    if (promptShowing) {
      console.log('现场导览弹窗正在显示，跳过重复弹窗')
      return status
    }

    promptShowing = true
    markPromptShown(matchedScenic.parkId)

    uni.showModal({
      title: `欢迎来到${matchedScenic.parkName}`,
      content:
        '检测到你已进入景区范围，AI 数字人导游已为你准备好现场讲解，是否开启智能导览？',
      confirmText: '开启导览',
      cancelText: '稍后再说',
      success(res) {
        if (res.confirm) {
          enterOnsiteGuide(context)
        }
      },
      complete() {
        promptShowing = false
      }
    })

    return status
  } catch (err) {
    console.log('GPS 定位失败：', err)

    const status = {
      inside: false,
      error: err
    }

    saveCurrentOnsiteStatus(status)

    return status
  }
}