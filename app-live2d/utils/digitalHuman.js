import request from './request'

export const DEFAULT_DIGITAL_HUMAN_CONFIG = {
  parkId: '',
  avatarId: 'guide_female_01',
  avatarName: '灵灵',
  modelPath: 'guide_female_01',
  voiceId: 'zhitian_emo',
  voiceName: '知甜',
  welcomeText: '欢迎来到本景区，我是您的 AI 数字人导游。',
  isEnabled: 1
}

const configCache = new Map()
const STORAGE_KEY_PREFIX = 'currentDigitalHumanConfig_'

function pickFirst(...values) {
  for (const value of values) {
    if (value !== undefined && value !== null && value !== '') {
      return value
    }
  }

  return ''
}

function unwrapConfigResponse(response) {
  if (!response) return null

  if (
    Object.prototype.hasOwnProperty.call(response, 'code') &&
    Object.prototype.hasOwnProperty.call(response, 'data')
  ) {
    return response.data
  }

  return response
}

export function normalizeDigitalHumanConfig(data = {}, parkId = '') {
  const raw = data && typeof data === 'object' ? data : {}
  const finalParkId = pickFirst(raw.parkId, raw.park_id, parkId)
  const avatarId = pickFirst(raw.avatarId, raw.avatar_id, DEFAULT_DIGITAL_HUMAN_CONFIG.avatarId)
  const modelPath = pickFirst(raw.modelPath, raw.model_path, avatarId, DEFAULT_DIGITAL_HUMAN_CONFIG.modelPath)

  return {
    parkId: String(finalParkId || ''),
    avatarId: String(avatarId || DEFAULT_DIGITAL_HUMAN_CONFIG.avatarId),
    avatarName: String(pickFirst(raw.avatarName, raw.avatar_name, DEFAULT_DIGITAL_HUMAN_CONFIG.avatarName)),
    modelPath: String(modelPath || DEFAULT_DIGITAL_HUMAN_CONFIG.modelPath),
    voiceId: String(pickFirst(raw.voiceId, raw.voice_id, DEFAULT_DIGITAL_HUMAN_CONFIG.voiceId)),
    voiceName: String(pickFirst(raw.voiceName, raw.voice_name, DEFAULT_DIGITAL_HUMAN_CONFIG.voiceName)),
    welcomeText: String(pickFirst(raw.welcomeText, raw.welcome_text, DEFAULT_DIGITAL_HUMAN_CONFIG.welcomeText)),
    isEnabled: pickFirst(raw.isEnabled, raw.is_enabled, DEFAULT_DIGITAL_HUMAN_CONFIG.isEnabled)
  }
}

function getStorageKey(parkId = '') {
  const finalParkId = String(parkId || '').trim()
  return finalParkId ? `${STORAGE_KEY_PREFIX}${finalParkId}` : ''
}

export function getCachedDigitalHumanConfig(parkId = '') {
  const key = getStorageKey(parkId)

  if (!key) {
    return null
  }

  try {
    const cache = uni.getStorageSync(key)

    if (!cache) {
      return null
    }

    const parsed = typeof cache === 'string' ? JSON.parse(cache) : cache
    return normalizeDigitalHumanConfig(parsed, parkId)
  } catch (error) {
    console.warn('读取景区数字人本地缓存失败：', parkId, error)
    return null
  }
}

export function saveCachedDigitalHumanConfig(config = {}, parkId = '') {
  const finalParkId = String(parkId || config.parkId || config.park_id || '').trim()
  const key = getStorageKey(finalParkId)

  if (!key) {
    return
  }

  try {
    uni.setStorageSync(key, normalizeDigitalHumanConfig(config, finalParkId))
  } catch (error) {
    console.warn('写入景区数字人本地缓存失败：', finalParkId, error)
  }
}

export async function fetchDigitalHumanConfig(parkId, options = {}) {
  const finalParkId = String(parkId || '').trim()

  if (!finalParkId) {
    return normalizeDigitalHumanConfig(null, '')
  }

  if (options.forceRefresh !== true && configCache.has(finalParkId)) {
    return configCache.get(finalParkId)
  }

  try {
    const response = await request({
      url: '/api/app/digital-human/config',
      method: 'GET',
      data: {
        parkId: finalParkId
      },
      showErrorToast: false
    })

    const config = normalizeDigitalHumanConfig(unwrapConfigResponse(response), finalParkId)
    configCache.set(finalParkId, config)
    saveCachedDigitalHumanConfig(config, finalParkId)
    return config
  } catch (error) {
    console.warn('获取游客端数字人配置失败，使用默认数字人：', error)
    return normalizeDigitalHumanConfig(null, finalParkId)
  }
}
