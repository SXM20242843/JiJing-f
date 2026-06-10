import { ref } from 'vue'
import request from './request'
import { getCurrentUserId, getToken } from './auth'
import { trackUserBehavior } from './behavior'

const CURRENT_VISIT_ID_KEY = 'currentVisitId'
const CURRENT_PARK_ID_KEY = 'currentParkId'
const CURRENT_PARK_NAME_KEY = 'currentParkName'
const CURRENT_GROUP_SIZE_KEY = 'groupSize'
const CURRENT_TRAVEL_TYPE_KEY = 'travelType'
const CURRENT_VISIT_PREFERENCE_KEY = 'visitPreference'
const CURRENT_VISIT_INFO_KEY = 'currentVisitInfo'
const ACTIVE_VISIT_KEY = 'activeVisit'
const ACTIVE_VISIT_ID_KEY = 'activeVisitId'
const ACTIVE_SCENIC_ID_KEY = 'activeScenicId'
const ACTIVE_SCENIC_NAME_KEY = 'activeScenicName'
const LAST_VISIT_ID_KEY = 'lastVisitId'
const LAST_ENDED_VISIT_ID_KEY = 'lastEndedVisitId'
const LAST_ENDED_AREA_NAME_KEY = 'lastEndedAreaName'
const REPORT_VISIT_ID_KEY = 'reportVisitId'
const PENDING_GUIDE_RETURN_CHECK_KEY = 'pendingGuideReturnCheck'
const CURRENT_GUIDE_AREA_NAME_KEY = 'currentGuideAreaName'
const NATIVE_GUIDE_ENDED_RETURN_KEY = 'nativeGuideEndedReturn'
const NATIVE_GUIDE_ENDED_RETURN_TTL = 5 * 60 * 1000

let pendingTripInfoSelection = null

function createLoginRequiredError(message = '请先登录') {
  const error = new Error(message)
  error.loginRequired = true
  return error
}

export function isLoginRequiredVisitError(error) {
  const message = String(error?.message || error || '')
  return error?.loginRequired === true ||
    message.includes('请先登录') ||
    message.includes('未登录') ||
    message.includes('登录已过期')
}

export const TRIP_GROUP_SIZE_OPTIONS = [
  '1人',
  '2人',
  '3-5人',
  '5人以上'
]

export const TRIP_TRAVEL_TYPE_OPTIONS = [
  '个人游',
  '情侣游',
  '朋友游',
  '家庭游',
  '亲子游'
]

export const TRIP_VISIT_PREFERENCE_OPTIONS = [
  '轻松游',
  '深度文化',
  '拍照打卡',
  '亲子娱乐',
  '美食购物'
]

export function normalizeTripInfoSelection(selection = {}) {
  return {
    groupSize: selection.groupSize || '',
    travelType: selection.travelType || '',
    visitPreference: selection.visitPreference || ''
  }
}

export function logTripInfoSelection(selection = {}) {
  const payload = normalizeTripInfoSelection(selection)
  pendingTripInfoSelection = payload
  console.log('[trip-info] selection:', JSON.stringify(payload, null, 2))
  return payload
}

export function takePendingTripInfoSelection() {
  const payload = pendingTripInfoSelection
  pendingTripInfoSelection = null
  return payload ? normalizeTripInfoSelection(payload) : null
}

export function clearPendingTripInfoSelection() {
  pendingTripInfoSelection = null
}

function normalizeVisitId(value) {
  if (value === undefined || value === null || value === '') return ''
  return String(value)
}

function normalizeScenicId(value) {
  if (value === undefined || value === null || value === '') return ''
  return String(value)
}

function safeGetStorage(key) {
  try {
    return uni.getStorageSync(key)
  } catch (error) {
    console.warn(`读取本地缓存失败：${key}`, error)
    return ''
  }
}

function safeSetStorage(key, value) {
  try {
    uni.setStorageSync(key, value)
  } catch (error) {
    console.warn(`写入本地缓存失败：${key}`, error)
  }
}

function safeRemoveStorage(key) {
  try {
    uni.removeStorageSync(key)
  } catch (error) {
    console.warn(`删除本地缓存失败：${key}`, error)
  }
}

function normalizeVisitUserScope(value) {
  return value === undefined || value === null ? '' : String(value).trim()
}

export function getVisitUserScope(userId = '') {
  return normalizeVisitUserScope(userId || getCurrentUserId())
}

export function getUserScopedVisitKey(baseKey, userId = '') {
  const scope = getVisitUserScope(userId)
  return scope ? `${baseKey}_${scope}` : baseKey
}

function readUserScopedVisitStorage(baseKey, options = {}) {
  const scopedKey = getUserScopedVisitKey(baseKey, options.userId || '')
  const scopedValue = safeGetStorage(scopedKey)
  if (scopedValue !== undefined && scopedValue !== null && scopedValue !== '') {
    return scopedValue
  }

  if (options.fallbackGlobal === true && scopedKey !== baseKey) {
    return safeGetStorage(baseKey)
  }

  return ''
}

function writeUserScopedVisitStorage(baseKey, value, options = {}) {
  const scopedKey = getUserScopedVisitKey(baseKey, options.userId || '')
  safeSetStorage(scopedKey, value)
  if (scopedKey !== baseKey) {
    safeRemoveStorage(baseKey)
  }
}

function removeUserScopedVisitStorage(baseKey, userId = '') {
  const scopedKey = getUserScopedVisitKey(baseKey, userId)
  safeRemoveStorage(scopedKey)
  if (scopedKey !== baseKey) {
    safeRemoveStorage(baseKey)
  }
}

function normalizeStartSource(value = '') {
  return value === 'gps' ? 'gps' : 'manual'
}

function parseNumericAreaId(...values) {
  for (const value of values) {
    if (value === undefined || value === null || value === '') continue

    const text = String(value).trim()
    if (!text) continue

    if (/^\d+$/.test(text)) {
      return Number(text)
    }

    const match = text.match(/^AREA_0*(\d+)$/i)
    if (match) {
      return Number(match[1])
    }
  }

  return ''
}

function pickAreaCode(...values) {
  for (const value of values) {
    if (value === undefined || value === null || value === '') continue

    const text = String(value).trim()
    if (/^AREA_\d+$/i.test(text)) {
      return text.toUpperCase()
    }
  }

  return ''
}

function pickFirst(...values) {
  for (const value of values) {
    if (value !== undefined && value !== null && value !== '') return value
  }

  return ''
}

function buildVisitStartPayload(options = {}) {
  const rawAreaId = pickFirst(options.areaId, options.area_id, options.parkId, options.park_id)
  const rawParkId = pickFirst(options.parkId, options.park_id, options.areaId, options.area_id)
  const rawAreaCode = pickFirst(options.areaCode, options.area_code)
  const rawParkCode = pickFirst(options.parkCode, options.park_code)

  const areaId = parseNumericAreaId(rawAreaId, rawParkId, rawAreaCode, rawParkCode)
  const parkId = parseNumericAreaId(rawParkId, rawAreaId, rawParkCode, rawAreaCode) || areaId
  const areaCode = pickAreaCode(rawAreaCode, rawParkCode, rawAreaId, rawParkId)
  const parkCode = pickAreaCode(rawParkCode, rawAreaCode, rawParkId, rawAreaId) || areaCode

  if (!areaId) {
    throw new Error('景区ID异常，请重新进入现场导览')
  }

  const payload = {
    userId: options.userId || getCurrentUserId() || '',
    parkId,
    parkCode,
    parkName: pickFirst(options.parkName, options.park_name, options.areaName, options.area_name),
    areaId,
    areaCode,
    areaName: pickFirst(options.areaName, options.area_name, options.parkName, options.park_name),
    groupSize: pickFirst(options.groupSize, options.travelPeopleCount),
    travelPeopleCount: pickFirst(options.travelPeopleCount, options.groupSize),
    travelType: options.travelType || '',
    visitPreference: pickFirst(options.visitPreference, options.travelPreference),
    travelPreference: pickFirst(options.travelPreference, options.visitPreference),
    estimatedDuration: options.estimatedDuration || '',
    entrySource: pickFirst(options.entrySource, options.startSource),
    startSource: normalizeStartSource(pickFirst(options.startSource, options.entrySource))
  }

  if (options.latitude !== undefined && options.latitude !== null && options.latitude !== '') {
    payload.latitude = options.latitude
  }

  if (options.longitude !== undefined && options.longitude !== null && options.longitude !== '') {
    payload.longitude = options.longitude
  }

  return payload
}

function buildVisitEndPayload(options = {}) {
  const payload = {
    visitId: normalizeVisitId(options.visitId || getCurrentVisitId()),
    endSource: options.endSource || 'manual_exit'
  }

  if (options.latitude !== undefined && options.latitude !== null && options.latitude !== '') {
    payload.latitude = options.latitude
  }

  if (options.longitude !== undefined && options.longitude !== null && options.longitude !== '') {
    payload.longitude = options.longitude
  }

  return payload
}

function extractVisitId(response) {
  if (!response) return ''

  const directVisitId = response.visitId
  if (directVisitId !== undefined && directVisitId !== null && directVisitId !== '') {
    return normalizeVisitId(directVisitId)
  }

  const data = response.data

  if (data && typeof data === 'object') {
    const nestedVisitId = data.visitId
    if (nestedVisitId !== undefined && nestedVisitId !== null && nestedVisitId !== '') {
      return normalizeVisitId(nestedVisitId)
    }
  }

  if (
    data !== undefined &&
    data !== null &&
    data !== '' &&
    (typeof data === 'string' || typeof data === 'number')
  ) {
    return normalizeVisitId(data)
  }

  return ''
}

function unwrapApiData(response) {
  if (!response) return null

  if (
    Object.prototype.hasOwnProperty.call(response, 'code') &&
    Object.prototype.hasOwnProperty.call(response, 'data')
  ) {
    return response.data
  }

  return Object.prototype.hasOwnProperty.call(response, 'data')
    ? response.data
    : response
}

export function normalizeVisitStatus(value) {
  const text = String(value || '').trim().toUpperCase()

  if (['ACTIVE', 'IN_PROGRESS', 'ONGOING', 'RUNNING', 'STARTED'].includes(text)) {
    return 'ACTIVE'
  }

  if (['ENDED', 'COMPLETED', 'FINISHED', 'DONE'].includes(text)) {
    return 'ENDED'
  }

  if (['NOT_FOUND', 'INVALID', 'NONE', 'CANCELLED', 'CANCELED'].includes(text)) {
    return 'INVALID'
  }

  return ''
}

function normalizeVisitStatusResponse(response, fallbackVisitId = '') {
  const data = unwrapApiData(response)

  if (!data || (typeof data === 'object' && Object.keys(data).length === 0)) {
    return {
      state: 'INVALID',
      raw: data || null,
      visitId: normalizeVisitId(fallbackVisitId)
    }
  }

  if (typeof data === 'string') {
    return {
      state: normalizeVisitStatus(data),
      raw: data,
      visitId: normalizeVisitId(fallbackVisitId)
    }
  }

  const status = pickFirst(
    data.status,
    data.visitStatus,
    data.visit_status,
    data.state,
    data.sessionStatus,
    data.session_status
  )

  const state = normalizeVisitStatus(status) ||
    (pickFirst(data.endTime, data.end_time) ? 'ENDED' : '')

  return {
    state: state || 'INVALID',
    raw: data,
    visitId: normalizeVisitId(pickFirst(data.visitId, data.visit_id, data.id, fallbackVisitId)),
    parkId: pickFirst(data.parkId, data.park_id, data.areaId, data.area_id, data.areaCode, data.area_code),
    parkName: pickFirst(data.parkName, data.park_name, data.areaName, data.area_name)
  }
}

function isVisitEndSuccessResponse(response) {
  if (!response) return false

  if (Object.prototype.hasOwnProperty.call(response, 'code') && Number(response.code) === 0) {
    return true
  }

  const data = unwrapApiData(response)
  if (!data || typeof data !== 'object') {
    return false
  }

  const status = normalizeVisitStatus(pickFirst(
    data.status,
    data.visitStatus,
    data.visit_status,
    data.state,
    data.sessionStatus,
    data.session_status
  ))

  return status === 'ENDED'
}

export function saveCurrentVisitInfo(data = {}) {
  const visitId = normalizeVisitId(data.visitId)
  const currentParkId = data.currentParkId || data.parkId || ''
  const currentParkName = data.currentParkName || data.parkName || ''
  const groupSize = data.groupSize || ''
  const travelType = data.travelType || ''
  const visitPreference = data.visitPreference || ''

  safeSetStorage(CURRENT_VISIT_ID_KEY, visitId)
  safeSetStorage(CURRENT_PARK_ID_KEY, currentParkId)
  safeSetStorage(CURRENT_PARK_NAME_KEY, currentParkName)
  safeSetStorage(CURRENT_GROUP_SIZE_KEY, groupSize)
  safeSetStorage(CURRENT_TRAVEL_TYPE_KEY, travelType)
  safeSetStorage(CURRENT_VISIT_PREFERENCE_KEY, visitPreference)
  safeSetStorage(CURRENT_VISIT_INFO_KEY, {
    currentVisitId: visitId,
    currentParkId,
    currentParkName,
    groupSize,
    travelType,
    visitPreference
  })
  safeSetStorage(ACTIVE_VISIT_ID_KEY, visitId)
  safeSetStorage(ACTIVE_VISIT_KEY, {
    currentVisitId: visitId,
    visitId,
    currentParkId,
    parkId: currentParkId,
    currentParkName,
    parkName: currentParkName,
    groupSize,
    travelType,
    visitPreference,
    status: 'ACTIVE',
    updateTime: Date.now()
  })
  clearActiveScenicVisit()

  return {
    currentVisitId: visitId,
    currentParkId,
    currentParkName,
    groupSize,
    travelType,
    visitPreference
  }
}

export function getCurrentVisitId() {
  return normalizeVisitId(safeGetStorage(CURRENT_VISIT_ID_KEY))
}

export function getActiveScenicVisit() {
  return {
    activeScenicId: normalizeScenicId(safeGetStorage(ACTIVE_SCENIC_ID_KEY)),
    activeScenicName: safeGetStorage(ACTIVE_SCENIC_NAME_KEY) || ''
  }
}

export function saveActiveScenicVisit(data = {}) {
  const activeScenicId = normalizeScenicId(data.scenicId || data.activeScenicId || '')
  const activeScenicName = data.scenicName || data.activeScenicName || ''

  safeSetStorage(ACTIVE_SCENIC_ID_KEY, activeScenicId)
  safeSetStorage(ACTIVE_SCENIC_NAME_KEY, activeScenicName)

  return {
    activeScenicId,
    activeScenicName
  }
}

export function clearActiveScenicVisit() {
  safeRemoveStorage(ACTIVE_SCENIC_ID_KEY)
  safeRemoveStorage(ACTIVE_SCENIC_NAME_KEY)
}

export function clearCurrentVisitInfo() {
  safeRemoveStorage(CURRENT_VISIT_ID_KEY)
  safeRemoveStorage(CURRENT_PARK_ID_KEY)
  safeRemoveStorage(CURRENT_PARK_NAME_KEY)
  safeRemoveStorage(CURRENT_GROUP_SIZE_KEY)
  safeRemoveStorage(CURRENT_TRAVEL_TYPE_KEY)
  safeRemoveStorage(CURRENT_VISIT_PREFERENCE_KEY)
  safeRemoveStorage(CURRENT_VISIT_INFO_KEY)
  safeRemoveStorage(ACTIVE_VISIT_KEY)
  safeRemoveStorage(ACTIVE_VISIT_ID_KEY)
}

export function saveLastVisitId(visitId, options = {}) {
  const normalizedVisitId = normalizeVisitId(visitId)
  if (normalizedVisitId) {
    writeUserScopedVisitStorage(LAST_VISIT_ID_KEY, normalizedVisitId, options)
  }

  return normalizedVisitId
}

export function getLastVisitId(options = {}) {
  return normalizeVisitId(readUserScopedVisitStorage(LAST_VISIT_ID_KEY, options))
}

export function saveLastEndedVisit(data = {}) {
  const visitId = normalizeVisitId(data.visitId)
  const areaName = data.areaName || data.parkName || data.currentParkName || ''
  const options = {
    userId: data.userId || ''
  }

  if (visitId) {
    writeUserScopedVisitStorage(LAST_ENDED_VISIT_ID_KEY, visitId, options)
    saveLastVisitId(visitId, options)
  }

  if (areaName) {
    writeUserScopedVisitStorage(LAST_ENDED_AREA_NAME_KEY, areaName, options)
  }

  return {
    visitId,
    areaName
  }
}

export function getLastEndedVisit(options = {}) {
  return {
    visitId: normalizeVisitId(readUserScopedVisitStorage(LAST_ENDED_VISIT_ID_KEY, options)),
    areaName: readUserScopedVisitStorage(LAST_ENDED_AREA_NAME_KEY, options) || ''
  }
}

export function markPendingGuideReturnCheck(data = {}) {
  const visitId = normalizeVisitId(data.visitId || getCurrentVisitId())
  const areaName = data.areaName || data.parkName || safeGetStorage(CURRENT_PARK_NAME_KEY) || ''

  if (!visitId) {
    return null
  }

  safeSetStorage(PENDING_GUIDE_RETURN_CHECK_KEY, true)
  safeSetStorage(CURRENT_VISIT_ID_KEY, visitId)
  safeSetStorage(CURRENT_GUIDE_AREA_NAME_KEY, areaName)

  return {
    visitId,
    areaName
  }
}

export function getPendingGuideReturnCheck() {
  const pendingValue = safeGetStorage(PENDING_GUIDE_RETURN_CHECK_KEY)
  const pending = pendingValue === true || pendingValue === 'true' || pendingValue === 1 || pendingValue === '1'
  const visitId = normalizeVisitId(safeGetStorage(CURRENT_VISIT_ID_KEY))
  const areaName = safeGetStorage(CURRENT_GUIDE_AREA_NAME_KEY) || safeGetStorage(CURRENT_PARK_NAME_KEY) || ''

  return {
    pending,
    visitId,
    areaName
  }
}

export function clearPendingGuideReturnCheck() {
  safeRemoveStorage(PENDING_GUIDE_RETURN_CHECK_KEY)
  safeRemoveStorage(CURRENT_GUIDE_AREA_NAME_KEY)
}

export function markVisitEndedLocal(data = {}) {
  const visitId = normalizeVisitId(data.visitId || getCurrentVisitId())
  const areaName = data.areaName || data.parkName || safeGetStorage(CURRENT_PARK_NAME_KEY) || ''
  const saved = saveLastEndedVisit({
    visitId,
    areaName
  })

  clearActiveScenicVisit()
  clearCurrentVisitInfo()

  return saved
}

export function markNativeGuideEndedReturn(data = {}) {
  const visitId = normalizeVisitId(data.visitId || data.reportVisitId || getCurrentVisitId())
  const userId = data.userId || getCurrentUserId() || ''
  const areaName = data.areaName || data.parkName || getLastEndedVisit({ userId }).areaName || safeGetStorage(CURRENT_PARK_NAME_KEY) || ''

  if (!visitId) {
    return null
  }

  const payload = {
    guideEnded: true,
    visitId,
    areaName,
    userId,
    source: data.source || 'native-live2d',
    statusFailed: data.statusFailed === true,
    time: Date.now()
  }

  writeUserScopedVisitStorage(NATIVE_GUIDE_ENDED_RETURN_KEY, payload, { userId })
  return payload
}

export function getRecentNativeGuideEndedReturn() {
  const userId = getCurrentUserId() || ''
  let value = readUserScopedVisitStorage(NATIVE_GUIDE_ENDED_RETURN_KEY, { userId })
  if (!value && userId) {
    const legacyValue = safeGetStorage(NATIVE_GUIDE_ENDED_RETURN_KEY)
    if (legacyValue && typeof legacyValue === 'object') {
      const legacyUserId = legacyValue.userId || ''
      if (!legacyUserId || legacyUserId === userId) {
        value = legacyValue
      } else {
        safeRemoveStorage(NATIVE_GUIDE_ENDED_RETURN_KEY)
      }
    }
  }

  if (!value || typeof value !== 'object') {
    return {
      recent: false
    }
  }

  const visitId = normalizeVisitId(value.visitId)
  const time = Number(value.time || 0)

  if (!value.guideEnded || !visitId || !time || Date.now() - time > NATIVE_GUIDE_ENDED_RETURN_TTL) {
    clearNativeGuideEndedReturn()
    return {
      recent: false
    }
  }

  return {
    recent: true,
    visitId,
    areaName: value.areaName || '',
    statusFailed: value.statusFailed === true,
    source: value.source || ''
  }
}

export function clearNativeGuideEndedReturn() {
  removeUserScopedVisitStorage(NATIVE_GUIDE_ENDED_RETURN_KEY)
}

export function clearVisitCacheForLogout(userId = '') {
  const scope = getVisitUserScope(userId)
  const transientKeys = [
    CURRENT_VISIT_ID_KEY,
    CURRENT_PARK_ID_KEY,
    CURRENT_PARK_NAME_KEY,
    CURRENT_GROUP_SIZE_KEY,
    CURRENT_TRAVEL_TYPE_KEY,
    CURRENT_VISIT_PREFERENCE_KEY,
    CURRENT_VISIT_INFO_KEY,
    ACTIVE_VISIT_KEY,
    ACTIVE_VISIT_ID_KEY,
    ACTIVE_SCENIC_ID_KEY,
    ACTIVE_SCENIC_NAME_KEY,
    PENDING_GUIDE_RETURN_CHECK_KEY,
    CURRENT_GUIDE_AREA_NAME_KEY,
    NATIVE_GUIDE_ENDED_RETURN_KEY,
    REPORT_VISIT_ID_KEY,
    LAST_VISIT_ID_KEY,
    LAST_ENDED_VISIT_ID_KEY,
    LAST_ENDED_AREA_NAME_KEY
  ]

  transientKeys.forEach(key => safeRemoveStorage(key))

  if (scope) {
    safeRemoveStorage(getUserScopedVisitKey(NATIVE_GUIDE_ENDED_RETURN_KEY, scope))
    safeRemoveStorage(getUserScopedVisitKey(REPORT_VISIT_ID_KEY, scope))
    safeRemoveStorage(getUserScopedVisitKey(LAST_VISIT_ID_KEY, scope))
    safeRemoveStorage(getUserScopedVisitKey(LAST_ENDED_VISIT_ID_KEY, scope))
    safeRemoveStorage(getUserScopedVisitKey(LAST_ENDED_AREA_NAME_KEY, scope))
  }
}

export async function queryVisitStatus(visitId) {
  const normalizedVisitId = normalizeVisitId(visitId)

  if (!normalizedVisitId) {
    return {
      state: 'INVALID',
      visitId: ''
    }
  }

  if (!getToken()) {
    throw createLoginRequiredError('请先登录')
  }

  let response = null

  try {
    response = await request({
      url: '/api/app/visit/status',
      method: 'GET',
      data: {
        visitId: normalizedVisitId
      },
      needAuth: true,
      showErrorToast: false
    })
  } catch (error) {
    if (isLoginRequiredVisitError(error)) {
      throw createLoginRequiredError(error.message || '请先登录')
    }

    throw error
  }

  return normalizeVisitStatusResponse(response, normalizedVisitId)
}

export function goVisitReport(visitId = '', method = 'navigateTo', extraQuery = {}) {
  const finalVisitId = normalizeVisitId(
    visitId ||
    getLastEndedVisit({ fallbackGlobal: false }).visitId ||
    getLastVisitId({ fallbackGlobal: false })
  )
  const query = {
    ...extraQuery
  }

  if (finalVisitId) {
    query.visitId = finalVisitId
    writeUserScopedVisitStorage(REPORT_VISIT_ID_KEY, finalVisitId)
  }

  const queryString = Object.keys(query)
    .filter(key => query[key] !== undefined && query[key] !== null && query[key] !== '')
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(String(query[key]))}`)
    .join('&')
  const url = queryString
    ? `/pages/visit/report?${queryString}`
    : '/pages/visit/report'

  const action = method === 'redirectTo' ? 'redirectTo' : 'navigateTo'

  uni[action]({
    url,
    fail() {
      uni.navigateTo({ url })
    }
  })
}

export async function checkPendingGuideReturn() {
  const pending = getPendingGuideReturnCheck()

  if (!pending.pending || !pending.visitId) {
    return {
      handled: false
    }
  }

  try {
    const status = await queryVisitStatus(pending.visitId)

    if (status.state === 'ENDED') {
      const ended = markVisitEndedLocal({
        visitId: status.visitId || pending.visitId,
        areaName: status.parkName || pending.areaName
      })

      clearPendingGuideReturnCheck()

      return {
        handled: true,
        ended: true,
        visitId: ended.visitId,
        areaName: ended.areaName,
        status
      }
    }

    if (status.state === 'INVALID') {
      clearPendingGuideReturnCheck()
      clearCurrentVisitInfo()

      return {
        handled: true,
        invalid: true,
        status
      }
    }

    return {
      handled: true,
      active: true,
      status
    }
  } catch (error) {
    if (isLoginRequiredVisitError(error)) {
      console.warn('查询原生导览返回状态需要登录：', error)
      clearPendingGuideReturnCheck()

      return {
        handled: true,
        loginRequired: true,
        visitId: pending.visitId,
        areaName: pending.areaName,
        error
      }
    }

    console.warn('查询原生导览返回状态失败：', error)
    return {
      handled: true,
      error
    }
  }
}

export function shouldStartVisitGuide(options = {}) {
  if (options.contextType === 'general') {
    return false
  }

  if (!options.parkId || !options.parkName) {
    return false
  }

  return true
}

export async function startVisitGuide(options = {}) {
  const payload = buildVisitStartPayload(options)

  console.log('[visit/start] request:', JSON.stringify(payload, null, 2))

  const response = await request({
    url: '/api/visit/start',
    method: 'POST',
    data: payload,
    showErrorToast: false
  })

  console.log('[visit/start] response:', JSON.stringify(response, null, 2))

  const visitId = extractVisitId(response)

  if (!visitId) {
    throw new Error('visitId missing')
  }

  const savedVisitInfo = saveCurrentVisitInfo({
    visitId,
    parkId: payload.parkId,
    parkName: payload.parkName,
    groupSize: payload.groupSize,
    travelType: payload.travelType,
    visitPreference: payload.visitPreference
  })

  console.log('[visit/start] saved storage:', JSON.stringify({
    ...savedVisitInfo,
    storageCurrentVisitId: safeGetStorage(CURRENT_VISIT_ID_KEY),
    storageCurrentParkId: safeGetStorage(CURRENT_PARK_ID_KEY),
    storageCurrentParkName: safeGetStorage(CURRENT_PARK_NAME_KEY),
    storageGroupSize: safeGetStorage(CURRENT_GROUP_SIZE_KEY),
    storageTravelType: safeGetStorage(CURRENT_TRAVEL_TYPE_KEY),
    storageVisitPreference: safeGetStorage(CURRENT_VISIT_PREFERENCE_KEY)
  }, null, 2))

  trackUserBehavior('start_onsite_guide', {
    eventName: 'start_onsite_guide',
    entityType: 'VISIT',
    entityId: visitId,
    areaCode: payload.areaCode || payload.parkCode || '',
    sourcePage: 'utils/visit',
    extra: {
      userId: payload.userId || '',
      visitId,
      parkId: payload.parkId || '',
      parkCode: payload.parkCode || '',
      areaId: payload.areaId || '',
      areaCode: payload.areaCode || '',
      parkName: payload.parkName || '',
      groupSize: payload.groupSize || '',
      travelType: payload.travelType || '',
      visitPreference: payload.visitPreference || '',
      startSource: payload.startSource || 'manual'
    }
  })

  return {
    visitId,
    payload,
    response
  }
}

export async function enterScenicVisit(options = {}) {
  const payload = {
    visitId: normalizeVisitId(options.visitId || getCurrentVisitId()),
    userId: options.userId || getCurrentUserId() || '',
    parkId: options.parkId || safeGetStorage(CURRENT_PARK_ID_KEY) || '',
    scenicId: normalizeScenicId(options.scenicId || ''),
    scenicName: options.scenicName || '',
    enterSource: options.enterSource || 'ai_guide_click'
  }

  console.log('[enterScenicVisit] request:', JSON.stringify(payload, null, 2))

  if (!payload.visitId || !payload.scenicId) {
    console.warn('[enterScenicVisit] skipped:', JSON.stringify({
      reason: !payload.visitId ? 'missing_visitId' : 'missing_scenicId',
      payload
    }, null, 2))
    return {
      success: false,
      skipped: true,
      payload
    }
  }

  try {
    const response = await request({
      url: '/api/visit/scenic/enter',
      method: 'POST',
      data: payload,
      showErrorToast: false
    })

    console.log('[enterScenicVisit] response:', JSON.stringify(response, null, 2))

    return {
      success: true,
      payload,
      response
    }
  } catch (error) {
    console.warn('[enterScenicVisit] failed:', JSON.stringify({
      message: error?.message || String(error),
      payload
    }, null, 2))

    return {
      success: false,
      payload,
      error
    }
  }
}

export async function leaveScenicVisit(options = {}) {
  const payload = {
    visitId: normalizeVisitId(options.visitId || getCurrentVisitId()),
    scenicId: normalizeScenicId(options.scenicId || '')
  }

  console.log('[leaveScenicVisit] request:', JSON.stringify(payload, null, 2))

  if (!payload.visitId || !payload.scenicId) {
    console.warn('[leaveScenicVisit] skipped:', JSON.stringify({
      reason: !payload.visitId ? 'missing_visitId' : 'missing_scenicId',
      payload
    }, null, 2))
    return {
      success: false,
      skipped: true,
      payload
    }
  }

  try {
    const response = await request({
      url: '/api/visit/scenic/leave',
      method: 'POST',
      data: payload,
      showErrorToast: false
    })

    console.log('[leaveScenicVisit] response:', JSON.stringify(response, null, 2))

    return {
      success: true,
      payload,
      response
    }
  } catch (error) {
    console.warn('[leaveScenicVisit] failed:', JSON.stringify({
      message: error?.message || String(error),
      payload
    }, null, 2))

    return {
      success: false,
      payload,
      error
    }
  }
}

export async function switchActiveScenicVisit(options = {}) {
  const visitId = normalizeVisitId(options.visitId || getCurrentVisitId())
  const scenicId = normalizeScenicId(options.scenicId || '')
  const scenicName = options.scenicName || ''
  const userId = options.userId || getCurrentUserId() || ''
  const parkId = options.parkId || safeGetStorage(CURRENT_PARK_ID_KEY) || ''
  const enterSource = options.enterSource || 'ai_guide_click'

  console.log('[switchActiveScenicVisit] start:', JSON.stringify({
    visitId,
    userId,
    parkId,
    scenicId,
    scenicName,
    enterSource
  }, null, 2))

  if (!visitId) {
    console.warn('[switchActiveScenicVisit] skipped:', JSON.stringify({
      reason: 'missing_visitId',
      visitId,
      userId,
      parkId,
      scenicId,
      scenicName,
      enterSource
    }, null, 2))

    return {
      success: false,
      skipped: true,
      reason: 'missing_visit'
    }
  }

  if (!scenicId) {
    console.warn('[switchActiveScenicVisit] skipped:', JSON.stringify({
      reason: 'missing_scenicId',
      visitId,
      userId,
      parkId,
      scenicId,
      scenicName,
      enterSource,
      options
    }, null, 2))

    return {
      success: false,
      skipped: true,
      reason: 'missing_scenic'
    }
  }

  const currentActive = getActiveScenicVisit()
  console.log('[switchActiveScenicVisit] current active:', JSON.stringify(currentActive, null, 2))

  if (currentActive.activeScenicId && currentActive.activeScenicId === scenicId) {
    return {
      success: true,
      skipped: true,
      reason: 'same_scenic',
      activeScenicId: scenicId
    }
  }

  if (currentActive.activeScenicId && currentActive.activeScenicId !== scenicId) {
    await leaveScenicVisit({
      visitId,
      scenicId: currentActive.activeScenicId
    })
  }

  const enterResult = await enterScenicVisit({
    visitId,
    userId,
    parkId,
    scenicId,
    scenicName,
    enterSource
  })

  if (enterResult.success === true) {
    saveActiveScenicVisit({
      scenicId,
      scenicName
    })
  } else {
    console.warn('[switchActiveScenicVisit] enter failed, active scenic not saved:', JSON.stringify({
      visitId,
      scenicId,
      scenicName,
      enterResult
    }, null, 2))
  }

  return {
    success: enterResult.success,
    visitId,
    scenicId,
    scenicName,
    previousScenicId: currentActive.activeScenicId || '',
    enterResult
  }
}

export async function endCurrentVisit(options = {}) {
  const payload = buildVisitEndPayload(options)

  if (!payload.visitId) {
    uni.showToast({
      title: '当前没有进行中的现场导览',
      icon: 'none'
    })

    return {
      success: false,
      skipped: true,
      reason: 'missing_visit'
    }
  }

  const activeScenic = getActiveScenicVisit()
  console.log('[endCurrentVisit] active scenic before end:', JSON.stringify(activeScenic, null, 2))

  if (activeScenic.activeScenicId) {
    const leaveResult = await leaveScenicVisit({
      visitId: payload.visitId,
      scenicId: activeScenic.activeScenicId
    })

    if (!leaveResult.success) {
      console.warn('结束导览前离开当前景点失败，继续结束导览：', leaveResult)
    }
  }

  console.log('[visit/end] request:', JSON.stringify(payload, null, 2))

  const response = await request({
    url: '/api/visit/end',
    method: 'POST',
    data: payload,
    showErrorToast: false
  })

  console.log('[visit/end] response:', JSON.stringify(response, null, 2))

  if (!isVisitEndSuccessResponse(response)) {
    console.warn('[visit/end] backend did not confirm completed status, keep local visit state:', response)
    return {
      success: false,
      visitId: payload.visitId,
      payload,
      response
    }
  }

  clearActiveScenicVisit()
  saveLastVisitId(payload.visitId)

  trackUserBehavior('end_onsite_guide', {
    eventName: 'end_onsite_guide',
    entityType: 'VISIT',
    entityId: payload.visitId,
    sourcePage: 'utils/visit',
    extra: {
      userId: options.userId || getCurrentUserId() || '',
      visitId: payload.visitId,
      parkId: safeGetStorage(CURRENT_PARK_ID_KEY) || '',
      parkName: safeGetStorage(CURRENT_PARK_NAME_KEY) || '',
      groupSize: safeGetStorage(CURRENT_GROUP_SIZE_KEY) || '',
      travelType: safeGetStorage(CURRENT_TRAVEL_TYPE_KEY) || '',
      visitPreference: safeGetStorage(CURRENT_VISIT_PREFERENCE_KEY) || '',
      endSource: payload.endSource
    }
  })
  clearCurrentVisitInfo()

  return {
    success: true,
    visitId: payload.visitId,
    payload,
    response
  }
}

export function useTripInfoConfirm() {
  const showTripInfoPopup = ref(false)
  let pendingAction = null

  function openTripInfoConfirm(action) {
    if (showTripInfoPopup.value) {
      return
    }

    pendingAction = typeof action === 'function' ? action : null
    showTripInfoPopup.value = true
  }

  function cancelTripInfoConfirm() {
    pendingAction = null
    showTripInfoPopup.value = false
  }

  function consumePendingTripInfoAction() {
    const action = pendingAction
    pendingAction = null
    showTripInfoPopup.value = false
    return action
  }

  return {
    showTripInfoPopup,
    openTripInfoConfirm,
    cancelTripInfoConfirm,
    consumePendingTripInfoAction
  }
}
