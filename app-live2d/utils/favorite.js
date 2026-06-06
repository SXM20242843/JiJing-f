import request from './request'
import { getAuthPayload, isLogin } from './auth'
import { trackEvent } from './track'

function normalizeFavoriteTarget(target = {}) {
  const sceneCode =
    target.scene_code ||
    target.sceneCode ||
    target.scene_id ||
    target.sceneId ||
    ''

  const areaCode =
    target.area_code ||
    target.areaCode ||
    target.parkId ||
    ''

  const targetType =
    target.target_type ||
    target.targetType ||
    (sceneCode ? 'SPOT' : areaCode ? 'AREA' : 'SPOT')

  const explicitTargetId =
    target.target_id ||
    target.targetId ||
    target.dbId ||
    null

  return {
    target_type: String(targetType).toUpperCase(),
    target_id: explicitTargetId,
    area_code: areaCode,
    scene_code: sceneCode,
    name: target.name || target.scenicName || target.sceneName || target.areaName || ''
  }
}

function getLoginUserId() {
  const auth = getAuthPayload()
  return auth.user_id || ''
}

function checkLoginForFavorite() {
  if (!isLogin() || !getLoginUserId()) {
    uni.showModal({
      title: '需要登录',
      content: '登录后可以同步收藏到云端，并用于个性化导览和偏好分析。',
      confirmText: '去登录',
      cancelText: '先看看',
      success: res => {
        if (res.confirm) {
          uni.navigateTo({
            url: '/pages/login/login'
          })
        }
      }
    })

    return false
  }

  return true
}

function addFavorite(target = {}) {
  if (!checkLoginForFavorite()) {
    return Promise.reject(new Error('未登录'))
  }

  const userId = getLoginUserId()

  const payload = {
    user_id: userId,
    ...normalizeFavoriteTarget(target)
  }

  return request({
    url: '/api/user/favorite/add',
    method: 'POST',
    data: payload,
    needAuth: true
  }).then(res => {
    trackEvent({
      event_type: 'FAVORITE',
      event_name: payload.name ? `收藏${payload.name}` : '收藏景点',
      entity_type: payload.target_type,
      entity_id: payload.target_id || payload.scene_code || payload.area_code,
      area_code: payload.area_code,
      scene_code: payload.scene_code,
      content: payload.name,
      extra: payload
    })

    return res
  })
}

function removeFavorite(target = {}) {
  if (!checkLoginForFavorite()) {
    return Promise.reject(new Error('未登录'))
  }

  const userId = getLoginUserId()

  const payload = {
    user_id: userId,
    ...normalizeFavoriteTarget(target)
  }

  return request({
    url: '/api/user/favorite/remove',
    method: 'POST',
    data: payload,
    needAuth: true
  }).then(res => {
    trackEvent({
      event_type: 'FAVORITE_CANCEL',
      event_name: payload.name ? `取消收藏${payload.name}` : '取消收藏',
      entity_type: payload.target_type,
      entity_id: payload.target_id || payload.scene_code || payload.area_code,
      area_code: payload.area_code,
      scene_code: payload.scene_code,
      content: payload.name,
      extra: payload
    })

    return res
  })
}

function getFavoriteStatus(target = {}) {
  if (!isLogin() || !getLoginUserId()) {
    return Promise.resolve(false)
  }

  const payload = {
    user_id: getLoginUserId(),
    ...normalizeFavoriteTarget(target)
  }

  return request({
    url: '/api/user/favorite/status',
    method: 'POST',
    data: payload,
    needAuth: true
  }).then(res => {
    const data = res.data || res
    return !!data.favorite
  })
}

function getFavoriteList() {
  if (!checkLoginForFavorite()) {
    return Promise.reject(new Error('未登录'))
  }

  const userId = getLoginUserId()

  return request({
    url: `/api/user/favorite/list?user_id=${encodeURIComponent(userId)}`,
    method: 'GET',
    needAuth: true
  }).then(res => {
    return res.data || res || []
  })
}

export {
  addFavorite,
  removeFavorite,
  getFavoriteStatus,
  getFavoriteList,
  normalizeFavoriteTarget
}
