import { API_BASE } from './api'
import { getAuthPayload, getToken, getVisitorId, getSessionId } from './auth'

const DEFAULT_BEHAVIOR_API_PATH = '/api/user/behavior/add'
const BEHAVIOR_API_PATH_KEY = 'APP_BEHAVIOR_API_PATH'
const BEHAVIOR_CONTEXT_KEY = 'APP_BEHAVIOR_CONTEXT'
const AI_CONTEXT_KEY = 'aiContext'
const AI_CONTEXT_TYPE_KEY = 'aiContextType'
const SELECTED_SCENIC_ID_KEY = 'selectedScenicId'
const SELECTED_SCENIC_NAME_KEY = 'selectedScenicName'

function createEventId() {
  return `event_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
}

function normalizeUrl(url) {
  if (!url) return ''

  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url
  }

  if (url.startsWith('/')) {
    return `${API_BASE}${url}`
  }

  return `${API_BASE}/${url}`
}

function getCurrentPage() {
  const pages = getCurrentPages()

  if (!pages || !pages.length) {
    return null
  }

  return pages[pages.length - 1] || null
}

function getCurrentPagePath() {
  const current = getCurrentPage()
  return current?.route || ''
}

function getCurrentPageOptions() {
  const current = getCurrentPage()
  return current?.options || current?.$page?.options || {}
}

function safeGetStorage(key) {
  try {
    return uni.getStorageSync(key)
  } catch (err) {
    console.warn(`读取本地缓存失败: ${key}`, err)
    return ''
  }
}

function safeSetStorage(key, value) {
  try {
    uni.setStorageSync(key, value)
  } catch (err) {
    console.warn(`写入本地缓存失败: ${key}`, err)
  }
}

function safeRemoveStorage(key) {
  try {
    uni.removeStorageSync(key)
  } catch (err) {
    console.warn(`删除本地缓存失败: ${key}`, err)
  }
}

function parseObject(value) {
  if (!value) return {}

  if (typeof value === 'object' && !Array.isArray(value)) {
    return value
  }

  if (typeof value !== 'string') {
    return {}
  }

  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch (err) {
    console.warn('解析对象缓存失败：', err)
    return {}
  }
}

function normalizeText(value) {
  if (value === null || value === undefined) return ''
  return String(value).trim()
}

function pickFirst(...values) {
  for (const value of values) {
    if (value === null || value === undefined) {
      continue
    }

    if (typeof value === 'string') {
      const trimmed = value.trim()
      if (trimmed) {
        return trimmed
      }

      continue
    }

    if (value !== '') {
      return value
    }
  }

  return ''
}

function normalizeExtra(extra) {
  if (!extra) {
    return {}
  }

  if (typeof extra === 'object' && !Array.isArray(extra)) {
    return { ...extra }
  }

  return {
    raw: extra
  }
}

function getBehaviorApiPath() {
  return normalizeText(safeGetStorage(BEHAVIOR_API_PATH_KEY)) || DEFAULT_BEHAVIOR_API_PATH
}

function setBehaviorApiPath(path = '') {
  const normalized = normalizeText(path)

  if (!normalized) {
    safeRemoveStorage(BEHAVIOR_API_PATH_KEY)
    return DEFAULT_BEHAVIOR_API_PATH
  }

  safeSetStorage(BEHAVIOR_API_PATH_KEY, normalized)
  return normalized
}

function clearBehaviorApiPath() {
  safeRemoveStorage(BEHAVIOR_API_PATH_KEY)
}

function getStoredVisitContext() {
  const storedContext = parseObject(safeGetStorage(BEHAVIOR_CONTEXT_KEY))
  const currentVisit = parseObject(safeGetStorage('currentVisit'))
  const activeVisit = parseObject(safeGetStorage('activeVisit'))
  const visitContext = parseObject(safeGetStorage('visitContext'))
  const aiContext = parseObject(safeGetStorage(AI_CONTEXT_KEY))
  const aiContextType = normalizeText(safeGetStorage(AI_CONTEXT_TYPE_KEY))
  const pageOptions = getCurrentPageOptions()

  const selectedScenicId = aiContextType === 'park' ? safeGetStorage(SELECTED_SCENIC_ID_KEY) : ''
  const selectedScenicName = aiContextType === 'park' ? safeGetStorage(SELECTED_SCENIC_NAME_KEY) : ''

  return {
    currentVisitId: normalizeText(
      pickFirst(
        storedContext.currentVisitId,
        storedContext.visitId,
        currentVisit.currentVisitId,
        currentVisit.visitId,
        activeVisit.currentVisitId,
        activeVisit.visitId,
        visitContext.currentVisitId,
        visitContext.visitId,
        safeGetStorage('currentVisitId'),
        safeGetStorage('activeVisitId'),
        safeGetStorage('visitId'),
        pageOptions.visitId,
        pageOptions.currentVisitId
      )
    ),
    currentParkId: normalizeText(
      pickFirst(
        storedContext.currentParkId,
        storedContext.parkId,
        storedContext.areaCode,
        storedContext.area_code,
        currentVisit.currentParkId,
        currentVisit.parkId,
        currentVisit.areaCode,
        currentVisit.area_code,
        activeVisit.currentParkId,
        activeVisit.parkId,
        activeVisit.areaCode,
        activeVisit.area_code,
        visitContext.currentParkId,
        visitContext.parkId,
        visitContext.areaCode,
        visitContext.area_code,
        safeGetStorage('currentParkId'),
        aiContext.parkId,
        aiContext.park_id,
        aiContext.areaCode,
        aiContext.area_code,
        pageOptions.parkId,
        pageOptions.park_id,
        pageOptions.areaCode,
        pageOptions.area_code,
        pageOptions.id,
        selectedScenicId
      )
    ),
    currentParkName: normalizeText(
      pickFirst(
        storedContext.currentParkName,
        storedContext.parkName,
        storedContext.areaName,
        storedContext.area_name,
        currentVisit.currentParkName,
        currentVisit.parkName,
        currentVisit.areaName,
        currentVisit.area_name,
        activeVisit.currentParkName,
        activeVisit.parkName,
        activeVisit.areaName,
        activeVisit.area_name,
        visitContext.currentParkName,
        visitContext.parkName,
        visitContext.areaName,
        visitContext.area_name,
        safeGetStorage('currentParkName'),
        aiContext.parkName,
        aiContext.park_name,
        aiContext.areaName,
        aiContext.area_name,
        pageOptions.parkName,
        pageOptions.park_name,
        pageOptions.areaName,
        pageOptions.area_name,
        pageOptions.name,
        selectedScenicName
      )
    )
  }
}

function getCurrentBehaviorContext() {
  return getStoredVisitContext()
}

function setCurrentBehaviorContext(context = {}) {
  const current = getCurrentBehaviorContext()
  const next = {
    currentVisitId: normalizeText(
      pickFirst(
        context.currentVisitId,
        context.visitId,
        current.currentVisitId
      )
    ),
    currentParkId: normalizeText(
      pickFirst(
        context.currentParkId,
        context.parkId,
        context.areaCode,
        context.area_code,
        current.currentParkId
      )
    ),
    currentParkName: normalizeText(
      pickFirst(
        context.currentParkName,
        context.parkName,
        context.areaName,
        context.area_name,
        current.currentParkName
      )
    )
  }

  safeSetStorage(BEHAVIOR_CONTEXT_KEY, next)
  return next
}

function clearCurrentBehaviorContext() {
  safeRemoveStorage(BEHAVIOR_CONTEXT_KEY)
}

function buildBehaviorExtra(optionsExtra = {}, context = {}) {
  const extra = normalizeExtra(optionsExtra)
  const visitId = normalizeText(context.currentVisitId)
  const parkId = normalizeText(context.currentParkId)
  const parkName = normalizeText(context.currentParkName)

  if (visitId && extra.visitId === undefined && extra.visit_id === undefined) {
    extra.visitId = visitId
    extra.visit_id = visitId
  }

  if (parkId && extra.parkId === undefined && extra.park_id === undefined) {
    extra.parkId = parkId
    extra.park_id = parkId
  }

  if (parkName && extra.parkName === undefined && extra.park_name === undefined) {
    extra.parkName = parkName
    extra.park_name = parkName
  }

  return extra
}

function buildBehaviorPayload(options = {}) {
  const authPayload = getAuthPayload()
  const storedContext = getCurrentBehaviorContext()
  const currentVisitId = normalizeText(
    pickFirst(options.visit_id, options.visitId, storedContext.currentVisitId)
  )
  const currentParkId = normalizeText(
    pickFirst(
      options.park_id,
      options.parkId,
      options.area_code,
      options.areaCode,
      storedContext.currentParkId
    )
  )
  const currentParkName = normalizeText(
    pickFirst(
      options.park_name,
      options.parkName,
      options.area_name,
      options.areaName,
      storedContext.currentParkName
    )
  )
  const sourcePage = options.source_page || options.sourcePage || getCurrentPagePath()

  return {
    event_id: options.event_id || options.eventId || createEventId(),

    user_id: authPayload.user_id || '',
    visitor_id: authPayload.visitor_id,
    session_id: authPayload.session_id,

    event_type: options.event_type || options.eventType || '',
    event_name: options.event_name || options.eventName || '',

    entity_type: options.entity_type || options.entityType || 'OTHER',
    entity_id: options.entity_id || options.entityId || '',

    area_id: options.area_id || options.areaId || null,
    spot_id: options.spot_id || options.spotId || null,
    facility_id: options.facility_id || options.facilityId || null,

    area_code: options.area_code || options.areaCode || currentParkId || '',
    scene_code: options.scene_code || options.sceneCode || '',

    source_page: sourcePage,
    keyword: options.keyword || '',
    content: options.content || '',
    score: options.score || null,
    duration_seconds: options.duration_seconds || options.durationSeconds || null,

    longitude: options.longitude || null,
    latitude: options.latitude || null,
    gps_accuracy_m: options.gps_accuracy_m || options.gpsAccuracyM || null,

    client_type: 'APP',
    extra: buildBehaviorExtra(options.extra, {
      currentVisitId,
      currentParkId,
      currentParkName
    })
  }
}

function sendBehaviorRequest(payload, apiPath = getBehaviorApiPath()) {
  const token = getToken()
  const finalHeader = {
    'Content-Type': 'application/json',
    'X-Visitor-Id': getVisitorId(),
    'X-Session-Id': getSessionId()
  }

  if (token) {
    finalHeader.Authorization = `Bearer ${token}`
  }

  return new Promise(resolve => {
    uni.request({
      url: normalizeUrl(apiPath),
      method: 'POST',
      data: payload,
      header: finalHeader,
      success: res => {
        const statusCode = res.statusCode
        const body = res.data || {}

        if (statusCode < 200 || statusCode >= 300) {
          console.warn('行为上报失败：', {
            statusCode,
            apiPath,
            body,
            payload
          })
          resolve(null)
          return
        }

        if (
          Object.prototype.hasOwnProperty.call(body, 'code') &&
          body.code !== 0 &&
          body.code !== 200
        ) {
          console.warn('行为上报失败：', {
            apiPath,
            body,
            payload
          })
          resolve(null)
          return
        }

        resolve(body)
      },
      fail: err => {
        console.warn('行为上报失败：', {
          apiPath,
          err,
          payload
        })
        resolve(null)
      }
    })
  })
}

/**
 * 通用行为上报
 *
 * event_type 示例：
 * VIEW / CLICK / ASK / VOICE_INPUT / ROUTE_REQUEST / FAVORITE
 * LIKE / DISLIKE / RATING / FEEDBACK / AUDIO_PLAY / AUDIO_FINISH
 */
function trackEvent(options = {}) {
  const payload = buildBehaviorPayload(options)

  if (!payload.event_type) {
    console.warn('trackEvent 缺少 event_type：', payload)
    return Promise.resolve(null)
  }

  return sendBehaviorRequest(payload)
}

function trackUserBehavior(eventType, data = {}) {
  return trackEvent({
    ...data,
    event_type: data.event_type || data.eventType || eventType
  })
}

function trackPageView(pageName, extra = {}) {
  return trackUserBehavior('VIEW', {
    event_name: `浏览${pageName}`,
    entity_type: 'PAGE',
    entity_id: getCurrentPagePath(),
    content: `用户浏览${pageName}`,
    extra
  })
}

function trackClick(name, extra = {}) {
  return trackUserBehavior('CLICK', {
    event_name: name,
    entity_type: extra.entity_type || extra.entityType || 'PAGE',
    entity_id: extra.entity_id || extra.entityId || getCurrentPagePath(),
    content: name,
    extra
  })
}

function trackFavorite(target = {}) {
  return trackUserBehavior('FAVORITE', {
    event_name: target.name ? `收藏${target.name}` : '收藏',
    entity_type: target.entity_type || target.entityType || 'SPOT',
    entity_id: target.entity_id || target.entityId || target.id || target.scene_code || target.sceneCode || '',
    area_code: target.area_code || target.areaCode || '',
    scene_code: target.scene_code || target.sceneCode || target.id || '',
    content: target.name || target.scenicName || target.sceneName || '',
    extra: target
  })
}

function trackAsk(question, context = {}) {
  return trackUserBehavior('ASK', {
    event_name: '发起AI问答',
    entity_type: 'CHAT',
    entity_id: context.conversation_id || context.conversationId || '',
    area_code: context.area_code || context.areaCode || '',
    scene_code: context.scene_code || context.sceneCode || '',
    keyword: question,
    content: question,
    extra: context
  })
}

export {
  DEFAULT_BEHAVIOR_API_PATH,
  getBehaviorApiPath,
  setBehaviorApiPath,
  clearBehaviorApiPath,
  getCurrentBehaviorContext,
  setCurrentBehaviorContext,
  clearCurrentBehaviorContext,
  trackEvent,
  trackUserBehavior,
  trackPageView,
  trackClick,
  trackFavorite,
  trackAsk
}
