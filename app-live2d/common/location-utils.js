// common/location-utils.js

import { SCENIC_GEOFENCES } from './scenic-geofence.js'

const CURRENT_DEMO_PARK_ID_KEY = 'currentDemoParkId'

function toRad(deg) {
  return (deg * Math.PI) / 180
}

/**
 * 计算两点经纬度距离
 * 返回单位：米
 */
export function getDistanceMeters(lat1, lng1, lat2, lng2) {
  const earthRadius = 6378137

  const radLat1 = toRad(lat1)
  const radLat2 = toRad(lat2)

  const deltaLat = radLat1 - radLat2
  const deltaLng = toRad(lng1) - toRad(lng2)

  const a =
    Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
    Math.cos(radLat1) *
      Math.cos(radLat2) *
      Math.sin(deltaLng / 2) *
      Math.sin(deltaLng / 2)

  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

  return Math.round(earthRadius * c)
}

/**
 * 获取当前位置
 */
export function getCurrentLocation() {
  return new Promise((resolve, reject) => {
    uni.getLocation({
      type: 'gcj02',
      highAccuracyExpireTime: 3000,
      success(res) {
        resolve({
          latitude: Number(res.latitude),
          longitude: Number(res.longitude),
          speed: res.speed,
          accuracy: res.accuracy
        })
      },
      fail(err) {
        reject(err)
      }
    })
  })
}

function getCurrentDemoParkId() {
  try {
    return uni.getStorageSync(CURRENT_DEMO_PARK_ID_KEY) || ''
  } catch (error) {
    console.log('读取演示景区失败：', error)
    return ''
  }
}

/**
 * 判断当前位置是否进入某个景区围栏
 */
export function matchCurrentScenicFence(location, fences = SCENIC_GEOFENCES) {
  if (!location || !location.latitude || !location.longitude) {
    return null
  }

  const list = fences
    .map(item => {
      const distance = getDistanceMeters(
        location.latitude,
        location.longitude,
        item.latitude,
        item.longitude
      )

      return {
        ...item,
        distance,
        isInside: distance <= item.radius
      }
    })
    .sort((a, b) => a.distance - b.distance)

  const matchedParks = list.filter(item => item.isInside)

  if (matchedParks.length === 0) {
    return null
  }

  if (matchedParks.length === 1) {
    return matchedParks[0]
  }

  const currentDemoParkId = getCurrentDemoParkId()

  if (currentDemoParkId) {
    const demoMatchedPark = matchedParks.find(item => {
      return String(item.parkId || '') === String(currentDemoParkId)
    })

    if (demoMatchedPark) {
      return demoMatchedPark
    }
  }

  return matchedParks[0]
}

export async function resolveRouteVisitLocationStatus(fences = SCENIC_GEOFENCES) {
  try {
    const location = await getCurrentLocation()
    const nearestScenic = findNearestScenicFence(location, fences)
    const matchedScenic = matchCurrentScenicFence(location, fences)
    const accuracy = Number(location.accuracy)
    const accuracyTooLow = Number.isFinite(accuracy) && accuracy > 1000
    const isInsideArea = !!matchedScenic && !accuracyTooLow
    const visitStatus = accuracyTooLow ? 'UNKNOWN' : (isInsideArea ? 'IN_AREA' : 'NOT_IN_AREA')

    return {
      visit_status: visitStatus,
      visitStatus,
      is_inside_area: isInsideArea,
      isInsideArea,
      latitude: location.latitude,
      longitude: location.longitude,
      location_context: buildRouteLocationContext(location, matchedScenic || nearestScenic, accuracyTooLow),
      locationContext: buildRouteLocationContext(location, matchedScenic || nearestScenic, accuracyTooLow)
    }
  } catch (error) {
    console.log('路线推荐定位状态判断失败：', error)
    return {
      visit_status: 'UNKNOWN',
      visitStatus: 'UNKNOWN',
      is_inside_area: false,
      isInsideArea: false
    }
  }
}

function findNearestScenicFence(location, fences = SCENIC_GEOFENCES) {
  if (!location || !location.latitude || !location.longitude) {
    return null
  }

  return fences
    .map(item => ({
      ...item,
      distance: getDistanceMeters(
        location.latitude,
        location.longitude,
        item.latitude,
        item.longitude
      )
    }))
    .sort((a, b) => a.distance - b.distance)[0] || null
}

function buildRouteLocationContext(location, scenic, accuracyTooLow = false) {
  if (!location) {
    return null
  }

  const context = {
    source: 'GPS',
    latitude: location.latitude,
    longitude: location.longitude,
    accuracy: location.accuracy
  }

  if (scenic && scenic.distance !== undefined) {
    context.distance_to_area_center_m = scenic.distance
  }

  if (scenic && scenic.parkId) {
    context.area_code = scenic.parkId
    context.area_name = scenic.parkName || ''
  }

  if (accuracyTooLow) {
    context.status_reason = 'LOW_ACCURACY'
  }

  return context
}
