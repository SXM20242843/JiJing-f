<script>
import { checkOnsiteGuideByLocation } from '@/common/onsite-guide.js'
import {
  checkPendingGuideReturn,
  clearCurrentVisitInfo,
  clearPendingGuideReturnCheck,
  goVisitReport,
  markVisitEndedLocal
} from '@/utils/visit'

let lastHandledExternalUrl = ''
let lastHandledExternalTime = 0
let lastExternalHandledAt = 0
let onsiteCheckTimer = null
let lastOnsiteCheckTime = 0
let lastOpenedReportVisitId = ''
let lastOpenedReportTime = 0

const EXTERNAL_DUPLICATE_WINDOW = 10 * 60 * 1000
const ONSITE_CHECK_INTERVAL = 8 * 1000
const ONSITE_CHECK_DELAY = 800

export default {
  onLaunch: function (options) {
    console.log('App Launch', options)

    clearNativeGuideReturnState()

    const handled = handleExternalOpen(options, 'launch')

    if (!handled) {
      handleGuideReturnThenCheck('launch')
    }
  },

  onShow: function (options) {
    console.log('App Show', options)

    const handled = handleExternalOpen(options, 'show')

    if (!handled) {
      handleGuideReturnThenCheck('show')
    }
  },

  onHide: function () {
    console.log('App Hide')
  }
}

/**
 * 清理之前“强制返回 AI 导览”的残留状态。
 * 这几个缓存现在不再使用，避免影响页面栈和登录状态。
 */
function clearNativeGuideReturnState() {
  try {
    uni.removeStorageSync('NATIVE_GUIDE_RETURN_CONTEXT')
    uni.removeStorageSync('NATIVE_GUIDE_RETURNING')
    uni.removeStorageSync('NATIVE_GUIDE_RETURN_LOCK')
  } catch (error) {
    console.log('清理原生导览返回状态失败：', error)
  }
}

/**
 * 延迟检查是否进入景区。
 * 这里加防抖，避免 App 从原生页 / 地图页回来时反复触发现场导览弹窗。
 */
function delayCheckOnsiteGuide(source = '') {
  const now = Date.now()

  // 刚刚处理过外部打开地图，不马上做现场导览检测
  if (now - lastExternalHandledAt < 5000) {
    console.log('刚处理外部打开，跳过本次现场导览检测：', source)
    return
  }

  // 短时间内不要重复检测
  if (now - lastOnsiteCheckTime < ONSITE_CHECK_INTERVAL) {
    console.log('现场导览检测过于频繁，跳过：', source)
    return
  }

  if (onsiteCheckTimer) {
    clearTimeout(onsiteCheckTimer)
    onsiteCheckTimer = null
  }

  onsiteCheckTimer = setTimeout(() => {
    lastOnsiteCheckTime = Date.now()

    try {
      checkOnsiteGuideByLocation({
        showPrompt: false
      })
    } catch (error) {
      console.log('现场导览检测异常：', error)
    }
  }, ONSITE_CHECK_DELAY)
}

async function handleGuideReturnThenCheck(source = '') {
  try {
    if (openStoredGuideEndReport(source)) {
      return
    }

    const result = await checkPendingGuideReturn()

    if (result?.ended && result.visitId) {
      console.log('原生现场导览已结束，打开游玩报告：', result.visitId)
      openEndedGuideReport({
        visitId: result.visitId,
        areaName: result.areaName
      }, source)
      return
    }

    if (result?.loginRequired) {
      uni.showToast({
        title: '请先登录后查看导览状态',
        icon: 'none'
      })
      delayCheckOnsiteGuide(source)
      return
    }

    if (result?.handled) {
      delayCheckOnsiteGuide(source)
      return
    }
  } catch (error) {
    console.log('处理原生导览返回状态失败：', error)
  }

  delayCheckOnsiteGuide(source)
}

function openStoredGuideEndReport(source = '') {
  const query = readStoredGuideEndQuery()

  if (!query) {
    return false
  }

  return openEndedGuideReport(query, source)
}

function readStoredGuideEndQuery() {
  const openReport = readStorageValue('openReport')
  const guideEnded = readStorageValue('guideEnded')
  const nativeOpenReport = readStorageValue('NATIVE_OPEN_REPORT')
  const nativeGuideEnded = readStorageValue('NATIVE_GUIDE_ENDED')

  if (
    !isTruthy(openReport) &&
    !isTruthy(guideEnded) &&
    !isTruthy(nativeOpenReport) &&
    !isTruthy(nativeGuideEnded)
  ) {
    return null
  }

  const query = {
    openReport: openReport || nativeOpenReport,
    guideEnded: guideEnded || nativeGuideEnded,
    visitId:
      readStorageValue('visitId') ||
      readStorageValue('reportVisitId') ||
      readStorageValue('report_visit_id') ||
      readStorageValue('NATIVE_REPORT_VISIT_ID') ||
      readStorageValue('lastEndedVisitId') ||
      readStorageValue('currentVisitId'),
    areaName:
      readStorageValue('lastEndedAreaName') ||
      readStorageValue('currentGuideAreaName') ||
      readStorageValue('currentParkName')
  }

  clearStoredGuideEndMarkers()
  return query
}

function clearStoredGuideEndMarkers() {
  [
    'openReport',
    'guideEnded',
    'reportVisitId',
    'report_visit_id',
    'NATIVE_OPEN_REPORT',
    'NATIVE_GUIDE_ENDED',
    'NATIVE_REPORT_VISIT_ID'
  ].forEach(key => {
    try {
      uni.removeStorageSync(key)
    } catch (error) {
      console.log('清理原生报告跳转标记失败：', key, error)
    }
  })
}

function openEndedGuideReport(query = {}, source = '') {
  const visitId = resolveReportVisitId(query)

  if (!visitId) {
    console.log('原生导览结束但缺少 visitId，跳过报告跳转：', query)
    return false
  }

  const areaName = resolveReportAreaName(query)
  markVisitEndedLocal({
    visitId,
    areaName
  })
  clearCurrentVisitInfo()
  clearPendingGuideReturnCheck()

  if (isReportOpenLocked(visitId)) {
    console.log('报告页已在短时间内打开，跳过重复跳转：', visitId)
    return true
  }

  if (isCurrentReportPage(visitId)) {
    console.log('当前已经在对应游玩报告页：', visitId)
    return true
  }

  markReportOpenLocked(visitId)
  goVisitReport(visitId, 'navigateTo', {
    fromNativeEnd: '1',
    source
  })
  return true
}

/**
 * 处理从 Android 原生 Live2D 页面打开过来的参数。
 *
 * 例如：
 * digitalhuman://map?parkId=AREA_0001
 * digitalhuman://map?areaCode=AREA_0001&areaName=灵山胜境
 */
function handleExternalOpen(options = {}, source = '') {
  const openInfo = getExternalOpenInfo(options)

  if (!openInfo || !openInfo.url) {
    return false
  }

  const url = String(openInfo.url || '').trim()

  if (!url) {
    return false
  }

  const now = Date.now()

  // 防止 plus.runtime.arguments 残留导致同一个外部打开参数被反复处理
  if (
    lastHandledExternalUrl === url &&
    now - lastHandledExternalTime < EXTERNAL_DUPLICATE_WINDOW
  ) {
    console.log('重复外部打开参数，跳过：', url)
    tryClearRuntimeArguments()
    return true
  }

  lastHandledExternalUrl = url
  lastHandledExternalTime = now
  lastExternalHandledAt = now

  console.log('收到外部打开参数：', source, url)

  const parsed = parseExternalUrl(url)

  if (!parsed) {
    tryClearRuntimeArguments()
    return false
  }

  if (isReportOpenQuery(parsed.query, parsed.action)) {
    openEndedGuideReport(parsed.query, source)
    tryClearRuntimeArguments()
    return true
  }

  if (parsed.action === 'map') {
    openMapFromExternal(parsed.query)
    tryClearRuntimeArguments()
    return true
  }

  tryClearRuntimeArguments()
  return false
}

/**
 * 获取外部打开参数
 */
function getExternalOpenInfo(options = {}) {
  // 1. 生命周期 options 里可能带 query
  if (options && options.query && Object.keys(options.query).length > 0) {
    const query = options.query

    if (isReportOpenQuery(query, query.action || query.page || '')) {
      return {
        url: buildVirtualUrlFromQuery(query, 'report')
      }
    }

    if (
      query.parkId ||
      query.areaCode ||
      query.page === 'map' ||
      query.action === 'map'
    ) {
      return {
        url: buildVirtualUrlFromQuery(query, 'map')
      }
    }
  }

  // 2. Android 显式 Intent 传入的 data，一般从 plus.runtime.arguments 获取
  // #ifdef APP-PLUS
  try {
    if (typeof plus !== 'undefined' && plus.runtime && plus.runtime.arguments) {
      const args = String(plus.runtime.arguments || '').trim()

      if (args) {
        return {
          url: args
        }
      }
    }
  } catch (error) {
    console.log('读取 plus.runtime.arguments 失败：', error)
  }
  // #endif

  return null
}

/**
 * 尝试清空 Android 外部打开参数，避免回到前台时重复处理。
 */
function tryClearRuntimeArguments() {
  // #ifdef APP-PLUS
  try {
    if (typeof plus !== 'undefined' && plus.runtime) {
      plus.runtime.arguments = ''
    }
  } catch (error) {
    console.log('清空 plus.runtime.arguments 失败，可忽略：', error)
  }
  // #endif
}

function buildVirtualUrlFromQuery(query = {}, action = 'map') {
  const pairs = []

  Object.keys(query).forEach(key => {
    if (query[key] !== undefined && query[key] !== null) {
      pairs.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(query[key]))}`)
    }
  })

  return `digitalhuman://${action || 'map'}?${pairs.join('&')}`
}

/**
 * 解析：
 * digitalhuman://map?parkId=AREA_0001
 */
function parseExternalUrl(url) {
  if (!url || typeof url !== 'string') {
    return null
  }

  const rawUrl = url.trim()

  if (!rawUrl) {
    return null
  }

  let action = ''
  let queryString = ''

  const questionIndex = rawUrl.indexOf('?')
  const beforeQuery = questionIndex >= 0 ? rawUrl.slice(0, questionIndex) : rawUrl

  if (questionIndex >= 0) {
    queryString = rawUrl.slice(questionIndex + 1)
  } else if (!rawUrl.includes('://') && rawUrl.includes('=')) {
    queryString = rawUrl
    action = 'callback'
  }

  if (beforeQuery.includes('://')) {
    const afterScheme = beforeQuery.split('://')[1] || ''
    action = afterScheme.replace(/^\/+/, '')
  } else if (!action) {
    action = beforeQuery.replace(/^\/+/, '')
  }

  if (action.includes('/')) {
    const parts = action.split('/').filter(Boolean)
    action = parts[parts.length - 1] || action
  }

  const query = parseQueryString(queryString)

  if (query.action && !isReportOpenQuery(query, query.action)) {
    action = query.action
  }

  if (query.page === 'map') {
    action = 'map'
  }

  return {
    action,
    query
  }
}

function parseQueryString(queryString) {
  const query = {}

  if (!queryString) {
    return query
  }

  queryString.split('&').forEach(pair => {
    if (!pair) return

    const index = pair.indexOf('=')
    const rawKey = index >= 0 ? pair.slice(0, index) : pair
    const rawValue = index >= 0 ? pair.slice(index + 1) : ''

    let key = ''
    let value = ''

    try {
      key = decodeURIComponent(rawKey || '').trim()
      value = decodeURIComponent((rawValue || '').replace(/\+/g, '%20')).trim()
    } catch (error) {
      console.log('解析外部参数失败：', pair, error)
      return
    }

    if (key) {
      query[key] = value
    }
  })

  return query
}

function isReportOpenQuery(query = {}, action = '') {
  if (!query || typeof query !== 'object') {
    return false
  }

  const reportAction = ['report', 'openReport', 'visitReport'].includes(String(action || ''))
  const wantsReport =
    reportAction ||
    isTruthy(query.openReport) ||
    isTruthy(query.open_report) ||
    isTruthy(query.guideEnded) ||
    isTruthy(query.guide_ended) ||
    query.page === 'report' ||
    query.action === 'report'

  return wantsReport && !!resolveReportVisitId(query)
}

function resolveReportVisitId(query = {}) {
  return String(
    query.visitId ||
    query.visit_id ||
    query.reportVisitId ||
    query.report_visit_id ||
    query.lastEndedVisitId ||
    query.last_ended_visit_id ||
    query.NATIVE_REPORT_VISIT_ID ||
    ''
  ).trim()
}

function resolveReportAreaName(query = {}) {
  return String(
    query.areaName ||
    query.area_name ||
    query.parkName ||
    query.park_name ||
    query.lastEndedAreaName ||
    query.last_ended_area_name ||
    ''
  ).trim()
}

function isTruthy(value) {
  if (value === true || value === 1) return true

  const text = String(value || '').trim().toLowerCase()
  return ['true', '1', 'yes', 'y', '是'].includes(text)
}

function readStorageValue(key) {
  try {
    return uni.getStorageSync(key)
  } catch (error) {
    console.log('读取本地报告跳转标记失败：', key, error)
    return ''
  }
}

function isReportOpenLocked(visitId) {
  const now = Date.now()
  return lastOpenedReportVisitId === visitId && now - lastOpenedReportTime < 3000
}

function markReportOpenLocked(visitId) {
  lastOpenedReportVisitId = visitId
  lastOpenedReportTime = Date.now()
}

function isCurrentReportPage(visitId) {
  const pages = getCurrentPages()

  if (!pages || pages.length === 0) {
    return false
  }

  const currentPage = pages[pages.length - 1]
  const route = currentPage?.route || ''
  const options = currentPage?.options || {}

  return route === 'pages/visit/report' &&
    String(options.visitId || options.visit_id || '') === String(visitId)
}

/**
 * 从原生页打开 uni-app 地图页。
 * 稳定版：只打开地图，不再保存“强制返回 AI 导览”的状态。
 */
function openMapFromExternal(query = {}) {
  const parkId =
    query.parkId ||
    query.areaCode ||
    query.id ||
    ''

  const parkName =
    query.parkName ||
    query.areaName ||
    query.name ||
    ''

  const latitude =
    query.latitude ||
    query.lat ||
    ''

  const longitude =
    query.longitude ||
    query.lng ||
    query.lon ||
    ''

  if (!parkId) {
    uni.showToast({
      title: '缺少景区 ID，无法打开地图',
      icon: 'none'
    })
    return
  }

  clearNativeGuideReturnState()

  uni.setStorageSync('selectedParkMapContext', JSON.stringify({
    id: parkId,
    name: parkName || '当前景区',
    latitude,
    longitude,
    source: 'native-live2d'
  }))

  const mapQuery = {
    parkId,
    areaName: parkName || '',
    latitude,
    longitude,
    source: 'native-live2d'
  }

  const mapUrl = `/pages/map/map?${buildPageQuery(mapQuery)}`

  // 外部原生页打开地图时，不能再用 navigateTo。
  // navigateTo 会把首页留在下面，导致返回栈混乱：地图返回首页、首页再返回又异常。
  // reLaunch 会关闭当前页面栈，直接以地图页作为当前页面。
  uni.reLaunch({
    url: mapUrl,
    success() {
      console.log('外部打开地图成功：', mapUrl)
    },
    fail(err) {
      console.log('reLaunch 地图失败，尝试 navigateTo：', err)

      uni.navigateTo({
        url: mapUrl,
        fail(error) {
          console.log('navigateTo 地图也失败：', error)

          uni.showToast({
            title: '无法打开景区地图',
            icon: 'none'
          })
        }
      })
    }
  })
}

function buildPageQuery(query = {}) {
  const pairs = []

  Object.keys(query).forEach(key => {
    const value = query[key]

    if (value !== undefined && value !== null && value !== '') {
      pairs.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    }
  })

  return pairs.join('&')
}
</script>

<style>
/* 每个页面公共css */
</style>
