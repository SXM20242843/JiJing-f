<template>
  <view class="page">
    <view class="map-wrap">
      <map
        id="scenicMap"
        class="map"
        :latitude="mapCenter.latitude"
        :longitude="mapCenter.longitude"
        :scale="mapScale"
        :markers="markers"
        :polyline="polylines"
        :include-points="includePoints"
        :show-location="false"
        :enable-zoom="true"
        :enable-scroll="true"
        :enable-rotate="false"
        :enable-poi="true"
        @markertap="onMarkerTap"
        @callouttap="onMarkerTap"
      ></map>
    </view>

    <view class="panel">
      <view class="park-card card">
        <view class="park-main">
          <view class="park-title">{{ park.name || '景区地图' }}</view>
          <view class="park-address">{{ park.address || '暂无地址信息' }}</view>
          <view class="park-desc">{{ park.desc || '暂无景区简介' }}</view>
        </view>

        <view class="park-status">
          <text class="park-status-num">{{ scenicList.length }}</text>
          <text class="park-status-label">景点</text>
        </view>
      </view>

      <view v-if="loading" class="map-tip card">
        正在加载数据库地图数据...
      </view>

      <view v-else-if="!hasAnyMapPoint" class="map-tip warning card">
        当前景区暂无有效经纬度数据，请后端补充景区或景点 latitude / longitude 字段。
      </view>

      <view class="action-row">
        <view class="action-btn" @click="locateMe">
          当前位置
        </view>
        <view class="action-btn" @click="drawRecommendRoute">
          推荐路线
        </view>
        <view class="action-btn primary" @click="startParkGuide">
          景区讲解
        </view>
      </view>

      <view v-if="selectedScenic" class="selected-card card">
        <view class="selected-top">
          <view class="selected-info">
            <view class="selected-name">{{ selectedScenic.name }}</view>
            <view class="selected-intro">{{ selectedScenic.intro }}</view>
          </view>

          <view class="selected-index">
            {{ selectedScenic.sort }}
          </view>
        </view>

        <view class="selected-actions">
          <view class="selected-btn" @click="focusScenic(selectedScenic)">
            查看位置
          </view>
          <view class="selected-btn primary" @click="startScenicGuide(selectedScenic)">
            数字人讲解
          </view>
        </view>
      </view>

      <view v-else class="empty-card card">
        <view class="empty-title">请选择地图上的景点</view>
        <view class="empty-text">
          点击地图标记后，可查看景点介绍并进入 AI 数字人讲解。
        </view>
      </view>

      <view class="section-title">
        <text>推荐游览顺序</text>
        <text class="route-tip">{{ routeTip }}</text>
      </view>

      <scroll-view scroll-x class="scenic-scroll">
        <view
          v-for="item in scenicList"
          :key="item.id"
          class="scenic-chip"
          :class="{ active: selectedScenic && selectedScenic.id === item.id }"
          @click="selectScenic(item)"
        >
          <text class="chip-index">{{ item.sort }}</text>
          <text class="chip-name">{{ item.name }}</text>
        </view>
      </scroll-view>

      <view class="location-text">
        {{ locationText }}
      </view>
    </view>

    <TripInfoPopup
      :show="showTripInfoPopup"
      @cancel="handleTripInfoCancel"
      @confirm="handleTripInfoConfirm"
    />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { API_BASE } from '../../utils/api'
import { demoParkMap } from '../../utils/mapMockData.js'
import { openNativeLive2DGuide } from '../../utils/openNativeLive2D.js'
import { trackUserBehavior } from '@/utils/behavior'
import TripInfoPopup from '@/components/TripInfoPopup.vue'
import {
  useTripInfoConfirm,
  logTripInfoSelection
} from '@/utils/visit'

const DEFAULT_CENTER = {
  latitude: 30.259297,
  longitude: 120.130663
}

const USER_LOCATION_MARKER_ID = 99999
const USER_LOCATION_ICON = '/static/map/current-location.png'

const loading = ref(false)
const currentParkId = ref('')
const {
  showTripInfoPopup,
  openTripInfoConfirm,
  cancelTripInfoConfirm,
  consumePendingTripInfoAction
} = useTripInfoConfirm()

const park = ref({
  id: '',
  name: '',
  address: '',
  desc: '',
  latitude: DEFAULT_CENTER.latitude,
  longitude: DEFAULT_CENTER.longitude,
  hasLocation: false,
  isFallbackLocation: false,
  scenics: []
})

const mapCenter = ref({
  latitude: DEFAULT_CENTER.latitude,
  longitude: DEFAULT_CENTER.longitude
})

const mapScale = ref(14)
const selectedScenic = ref(null)
const userLocation = ref(null)
const polylines = ref([])
const locationText = ref('尚未获取当前位置')
const routeTip = ref('等待生成路线')

const scenicList = computed(() => {
  return [...(park.value.scenics || [])].sort((a, b) => {
    return (a.sort || 0) - (b.sort || 0)
  })
})

const validScenicPoints = computed(() => {
  return scenicList.value.filter(item => item.hasLocation)
})

const hasAnyMapPoint = computed(() => {
  return park.value.hasLocation || validScenicPoints.value.length > 0 || !!userLocation.value
})

const markers = computed(() => {
  const result = []

  if (userLocation.value && isValidCoordinate(userLocation.value.latitude, userLocation.value.longitude)) {
    result.push({
      id: USER_LOCATION_MARKER_ID,
      latitude: Number(userLocation.value.latitude),
      longitude: Number(userLocation.value.longitude),
      title: '我的位置',
      iconPath: USER_LOCATION_ICON,
      width: 48,
      height: 48,
      zIndex: 999,
      callout: {
        content: '我的位置',
        display: 'ALWAYS',
        padding: 8,
        borderRadius: 8,
        bgColor: '#EF4444',
        color: '#ffffff',
        fontSize: 13
      }
    })
  }

  if (park.value.hasLocation && !park.value.isFallbackLocation) {
    result.push({
      id: 1,
      latitude: Number(park.value.latitude),
      longitude: Number(park.value.longitude),
      title: park.value.name,
      width: 34,
      height: 34,
      zIndex: 20,
      callout: {
        content: park.value.name || '当前景区',
        display: 'ALWAYS',
        padding: 8,
        borderRadius: 8,
        bgColor: '#ffffff',
        color: '#1f2937',
        fontSize: 13
      }
    })
  }

  scenicList.value.forEach((item, index) => {
    if (!item.hasLocation) return

    result.push({
      id: index + 100,
      scenicId: item.id,
      latitude: Number(item.latitude),
      longitude: Number(item.longitude),
      title: item.name,
      width: 30,
      height: 30,
      zIndex: selectedScenic.value && selectedScenic.value.id === item.id ? 80 : 40,
      callout: {
        content: `${item.sort}. ${item.name}`,
        display:
          selectedScenic.value && selectedScenic.value.id === item.id
            ? 'ALWAYS'
            : 'BYCLICK',
        padding: 8,
        borderRadius: 8,
        bgColor: '#ffffff',
        color: '#16a34a',
        fontSize: 13
      }
    })
  })

  return result
})

const includePoints = computed(() => {
  const points = []

  if (park.value.hasLocation) {
    points.push({
      latitude: Number(park.value.latitude),
      longitude: Number(park.value.longitude)
    })
  }

  scenicList.value.forEach(item => {
    if (item.hasLocation) {
      points.push({
        latitude: Number(item.latitude),
        longitude: Number(item.longitude)
      })
    }
  })

  if (userLocation.value) {
    points.push({
      latitude: Number(userLocation.value.latitude),
      longitude: Number(userLocation.value.longitude)
    })
  }

  if (!points.length) {
    points.push({
      latitude: mapCenter.value.latitude,
      longitude: mapCenter.value.longitude
    })
  }

  return points
})

onLoad(options => {
  // 稳定版：地图页只负责展示地图，不再拦截返回，不再强制拉起原生 AI。
  // 避免页面栈重复、返回首页异常、登录状态显示混乱等问题。
  clearNativeGuideReturnState()

  const parkId = options?.parkId || options?.areaCode || options?.id || ''
  currentParkId.value = parkId

  if (parkId) {
    loadParkMapData(parkId)
  } else {
    loadMockMapData()
  }
})

onShow(() => {
  // 进入地图页时不强制定位，避免每次进来都弹权限。
})

function requestGet(url) {
  return new Promise((resolve, reject) => {
    uni.request({
      url,
      method: 'GET',
      success: res => {
        console.log('地图接口返回：', url, res)
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data)
        } else {
          reject(new Error(`HTTP ${res.statusCode}`))
        }
      },
      fail: reject
    })
  })
}

function pick(obj, keys, fallback = '') {
  for (const key of keys) {
    if (obj && obj[key] !== undefined && obj[key] !== null && obj[key] !== '') {
      return obj[key]
    }
  }
  return fallback
}

function toNumber(value) {
  if (value === undefined || value === null || value === '') {
    return null
  }

  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

function isValidCoordinate(latitude, longitude) {
  const lat = Number(latitude)
  const lng = Number(longitude)

  if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
    return false
  }

  if (lat === 0 && lng === 0) {
    return false
  }

  return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
}

function normalizeScenic(item, index) {
  const latitude = toNumber(pick(item, [
    'latitude',
    'lat',
    'mapLat',
    'gcjLat',
    'gcj02Lat'
  ], null))

  const longitude = toNumber(pick(item, [
    'longitude',
    'lng',
    'lon',
    'mapLng',
    'gcjLng',
    'gcj02Lng'
  ], null))

  const sortValue = toNumber(pick(item, ['sort', 'order', 'index'], index + 1))

  return {
    ...item,
    id: pick(item, ['id', 'scenicId', 'sceneCode', 'scene_code'], `scenic_${index}`),
    name: pick(item, ['name', 'scenicName', 'sceneName', 'scene_name'], '未命名景点'),
    intro: pick(item, ['intro', 'desc', 'description'], '暂无景点介绍'),
    latitude,
    longitude,
    sort: sortValue || index + 1,
    hasLocation: isValidCoordinate(latitude, longitude)
  }
}

function resolveMapCenter(parkLatitude, parkLongitude, scenics) {
  if (isValidCoordinate(parkLatitude, parkLongitude)) {
    return {
      latitude: parkLatitude,
      longitude: parkLongitude,
      source: 'park'
    }
  }

  const firstScenic = scenics.find(item => item.hasLocation)

  if (firstScenic) {
    return {
      latitude: firstScenic.latitude,
      longitude: firstScenic.longitude,
      source: 'scenic'
    }
  }

  return {
    latitude: DEFAULT_CENTER.latitude,
    longitude: DEFAULT_CENTER.longitude,
    source: 'default'
  }
}

function readSelectedParkMapContext() {
  try {
    const cache = uni.getStorageSync('selectedParkMapContext')

    if (!cache) return null
    if (typeof cache === 'object') return cache

    return JSON.parse(cache)
  } catch (error) {
    console.log('读取地图上下文失败：', error)
    return null
  }
}

function clearNativeGuideReturnState() {
  try {
    uni.removeStorageSync('NATIVE_GUIDE_RETURN_CONTEXT')
    uni.removeStorageSync('NATIVE_GUIDE_RETURNING')
    uni.removeStorageSync('NATIVE_GUIDE_RETURN_LOCK')
  } catch (error) {
    console.log('清理原生导览返回状态失败：', error)
  }
}

function buildMapBehaviorPayload(extra = {}) {
  const parkId = park.value.id || currentParkId.value || ''
  const parkName = park.value.name || ''

  return {
    entityType: 'MAP',
    entityId: parkId || 'map-page',
    areaCode: parkId,
    sourcePage: 'pages/map/map',
    parkId,
    parkName,
    extra: {
      parkId,
      parkName,
      sourcePage: 'pages/map/map',
      ...extra
    }
  }
}

function trackMapView(extra = {}) {
  trackUserBehavior('view_map', {
    ...buildMapBehaviorPayload(extra),
    eventName: 'view_map'
  })
}

function applyUserLocationFromContext(context) {
  if (!context) return

  const latitude = toNumber(pick(context, ['latitude', 'lat'], null))
  const longitude = toNumber(pick(context, ['longitude', 'lng', 'lon'], null))

  if (!isValidCoordinate(latitude, longitude)) {
    return
  }

  userLocation.value = {
    latitude,
    longitude,
    accuracy: context.accuracy || '',
    source: context.source || 'onsite'
  }
}

function applyFallbackLocationFromContext(mapData, context) {
  if (!context) return mapData

  if (mapData.hasLocation) {
    return mapData
  }

  const latitude = toNumber(pick(context, ['latitude', 'lat'], null))
  const longitude = toNumber(pick(context, ['longitude', 'lng', 'lon'], null))

  if (!isValidCoordinate(latitude, longitude)) {
    return mapData
  }

  return {
    ...mapData,
    latitude,
    longitude,
    hasLocation: true,
    isFallbackLocation: true,
    centerSource: 'onsite_context'
  }
}

function normalizeParkMapData(parkRaw, scenicsRaw) {
  const parkData = parkRaw?.data || parkRaw || {}

  const scenicArray = Array.isArray(scenicsRaw?.data)
    ? scenicsRaw.data
    : Array.isArray(scenicsRaw)
      ? scenicsRaw
      : Array.isArray(parkData.scenics)
        ? parkData.scenics
        : Array.isArray(parkData.scenicList)
          ? parkData.scenicList
          : []

  const normalizedScenics = scenicArray.map((item, index) => {
    return normalizeScenic(item, index)
  })

  const parkLatitude = toNumber(pick(parkData, [
    'latitude',
    'lat',
    'mapLat',
    'gcjLat',
    'gcj02Lat'
  ], null))

  const parkLongitude = toNumber(pick(parkData, [
    'longitude',
    'lng',
    'lon',
    'mapLng',
    'gcjLng',
    'gcj02Lng'
  ], null))

  const parkHasLocation = isValidCoordinate(parkLatitude, parkLongitude)
  const center = resolveMapCenter(parkLatitude, parkLongitude, normalizedScenics)

  return {
    ...parkData,
    id: pick(parkData, ['id', 'parkId', 'areaCode', 'area_code'], currentParkId.value || ''),
    name: pick(parkData, ['name', 'parkName', 'areaName', 'area_name'], '未命名景区'),
    address: pick(parkData, ['address', 'location'], '暂无地址信息'),
    desc: pick(parkData, ['desc', 'intro', 'introduction', 'description'], '暂无景区简介'),
    latitude: parkHasLocation ? parkLatitude : center.latitude,
    longitude: parkHasLocation ? parkLongitude : center.longitude,
    hasLocation: parkHasLocation,
    isFallbackLocation: false,
    centerSource: center.source,
    scenics: normalizedScenics
  }
}

function applyMapData(mapData, text = '已加载地图数据') {
  park.value = mapData

  mapCenter.value = {
    latitude: Number(mapData.latitude),
    longitude: Number(mapData.longitude)
  }

  const firstLocatedScenic = scenicList.value.find(item => item.hasLocation)
  selectedScenic.value = firstLocatedScenic || scenicList.value[0] || null

  drawRecommendRoute(false)

  if (mapData.centerSource === 'onsite_context') {
    locationText.value = `${text}，后端景区坐标暂缺，已使用现场定位坐标作为地图中心`
  } else if (!hasAnyMapPoint.value) {
    locationText.value = '当前景区暂无有效经纬度数据'
  } else if (!mapData.hasLocation && mapData.centerSource === 'scenic') {
    locationText.value = `${text}，景区中心点暂缺，已使用首个有坐标的景点作为地图中心`
  } else {
    locationText.value = text
  }

  trackMapView({
    trigger: 'page_enter',
    centerSource: mapData.centerSource || ''
  })
}

async function loadParkMapData(parkId) {
  loading.value = true
  locationText.value = '正在加载数据库地图数据...'

  try {
    const [parkRes, scenicRes] = await Promise.all([
      requestGet(`${API_BASE}/api/app/parks/${parkId}`),
      requestGet(`${API_BASE}/api/app/parks/${parkId}/scenics`)
    ])

    const cacheContext = readSelectedParkMapContext()
    applyUserLocationFromContext(cacheContext)

    let mapData = normalizeParkMapData(parkRes, scenicRes)
    mapData = applyFallbackLocationFromContext(mapData, cacheContext)

    applyMapData(mapData, '已加载数据库地图数据')
  } catch (error) {
    console.log('地图数据加载失败：', error)

    uni.showToast({
      title: '地图数据加载失败',
      icon: 'none'
    })

    try {
      const cache = uni.getStorageSync('selectedParkMapContext')
      if (cache) {
        const parsed = typeof cache === 'object' ? cache : JSON.parse(cache)
        applyUserLocationFromContext(parsed)
        const mapData = normalizeParkMapData(parsed, parsed.scenics || [])
        applyMapData(mapData, '已使用缓存景区数据')
      }
    } catch (e) {
      console.log('读取地图缓存失败：', e)
    }
  } finally {
    loading.value = false
  }
}

function loadMockMapData() {
  const mapData = normalizeParkMapData(demoParkMap, demoParkMap.scenics)
  applyMapData(mapData, '当前为演示地图数据')
}

function locateMe() {
  uni.showLoading({
    title: '正在定位...'
  })

  uni.getLocation({
    type: 'gcj02',
    isHighAccuracy: true,
    geocode: false,
    success(res) {
      userLocation.value = {
        latitude: Number(res.latitude),
        longitude: Number(res.longitude),
        accuracy: res.accuracy,
        source: 'gps'
      }

      mapCenter.value = {
        latitude: Number(res.latitude),
        longitude: Number(res.longitude)
      }

      mapScale.value = 16
      locationText.value = `定位成功：${res.latitude}, ${res.longitude}`

      uni.showToast({
        title: '定位成功',
        icon: 'success'
      })
    },
    fail(err) {
      console.log('定位失败：', err)

      locationText.value = '定位失败，请检查定位权限'

      uni.showModal({
        title: '定位失败',
        content: '请确认手机定位已开启，并允许 APP 使用定位权限。',
        showCancel: false
      })
    },
    complete() {
      uni.hideLoading()
    }
  })
}

function onMarkerTap(e) {
  const markerId = Number(e.detail.markerId)

  if (markerId === USER_LOCATION_MARKER_ID) {
    if (userLocation.value) {
      mapCenter.value = {
        latitude: Number(userLocation.value.latitude),
        longitude: Number(userLocation.value.longitude)
      }
      mapScale.value = 17
    }
    return
  }

  const marker = markers.value.find(item => Number(item.id) === markerId)

  if (!marker || !marker.scenicId) {
    return
  }

  const scenic = scenicList.value.find(item => item.id === marker.scenicId)

  if (scenic) {
    selectScenic(scenic)
  }
}

function selectScenic(item) {
  selectedScenic.value = item
  focusScenic(item)
}

function focusScenic(item) {
  if (!item || !item.hasLocation) {
    uni.showToast({
      title: '该景点暂无地图坐标',
      icon: 'none'
    })
    return
  }

  mapCenter.value = {
    latitude: Number(item.latitude),
    longitude: Number(item.longitude)
  }

  mapScale.value = 16
}

function drawRecommendRoute(showTip = true) {
  const points = scenicList.value
    .filter(item => item.hasLocation)
    .map(item => ({
      latitude: Number(item.latitude),
      longitude: Number(item.longitude)
    }))

  if (points.length < 2) {
    polylines.value = []
    routeTip.value = '可连线景点不足'

    if (showTip) {
      uni.showToast({
        title: '可连线景点不足',
        icon: 'none'
      })
    }

    return
  }

  polylines.value = [
    {
      points,
      color: '#16A34ACC',
      width: 6,
      dottedLine: false,
      arrowLine: true
    }
  ]

  routeTip.value = '已按有坐标的景点生成路线'

  if (showTip) {
    uni.showToast({
    title: '已生成推荐路线',
      icon: 'none'
    })
  }
}

function openParkGuide() {
  const parkName = park.value.name || '当前景区'
  const parkId = park.value.id || currentParkId.value || ''

  uni.setStorageSync('selectedScenicName', parkName)
  uni.setStorageSync('selectedScenicId', parkId)
  uni.setStorageSync('aiContextType', 'park')
  uni.setStorageSync('aiContextName', parkName)

  uni.setStorageSync('aiContext', JSON.stringify({
    page: 'map',
    areaCode: parkId,
    areaName: parkName,
    currentSpotId: '',
    currentSpotName: ''
  }))

  uni.setStorageSync('aiAutoQuestion', `请为我讲解“${parkName}”这个景区`)

  openNativeLive2DGuide({
    entry: 'map-park-guide',
    scenicName: parkName,
    scenicId: parkId,
    contextType: 'park',
    contextName: parkName,
    parkId,
    parkName,
    areaId: parkId,
    areaCode: parkId,
    areaName: parkName,
    spotId: '',
    spotName: '',
    autoQuestion: `请为我讲解“${parkName}”这个景区`,
    mode: 'normal',
    trigger: 'map',
    latitude: userLocation.value?.latitude || park.value.latitude || '',
    longitude: userLocation.value?.longitude || park.value.longitude || ''
  })
}

function startParkGuide() {
  openTripInfoConfirm(() => openParkGuide())
}

function openScenicGuide(item) {
  const scenicName = item.name || '当前景点'
  const scenicId = item.id || ''
  const parkId = park.value.id || currentParkId.value || ''
  const parkName = park.value.name || ''

  uni.setStorageSync('selectedScenicName', scenicName)
  uni.setStorageSync('selectedScenicId', scenicId)
  uni.setStorageSync('aiContextType', 'scenic')
  uni.setStorageSync('aiContextName', scenicName)

  uni.setStorageSync('aiContext', JSON.stringify({
    page: 'map',
    parkId,
    parkName,
    areaId: parkId,
    areaCode: parkId,
    areaName: parkName,
    currentSpotId: scenicId,
    currentSpotName: scenicName
  }))

  uni.setStorageSync('aiAutoQuestion', `请为我讲解“${scenicName}”这个景点`)

  openNativeLive2DGuide({
    entry: 'map-scenic-guide',
    scenicName,
    scenicId,
    contextType: 'scenic',
    contextName: scenicName,
    parkId,
    parkName,
    areaId: parkId,
    areaCode: parkId,
    areaName: parkName,
    spotId: scenicId,
    spotName: scenicName,
    autoQuestion: `请为我讲解“${scenicName}”这个景点`,
    mode: 'normal',
    trigger: 'map',
    latitude: userLocation.value?.latitude || item.latitude || park.value.latitude || '',
    longitude: userLocation.value?.longitude || item.longitude || park.value.longitude || ''
  })
}

function startScenicGuide(item) {
  openTripInfoConfirm(() => openScenicGuide(item))
}

function handleTripInfoCancel() {
  cancelTripInfoConfirm()
}

function handleTripInfoConfirm(selection) {
  logTripInfoSelection(selection)

  const action = consumePendingTripInfoAction()

  Promise.resolve(typeof action === 'function' ? action(selection) : null).catch(error => {
    console.log('地图导览打开失败：', error)
  })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f5f7f4;
}

.map-wrap {
  width: 750rpx;
  height: 560rpx;
  background: #e5e7eb;
}

.map {
  width: 750rpx;
  height: 560rpx;
}

.panel {
  margin-top: -10rpx;
  padding: 24rpx;
  border-radius: 32rpx 32rpx 0 0;
  background: #f5f7f4;
  position: relative;
}

.card {
  background: #ffffff;
  border-radius: 28rpx;
  box-shadow: 0 14rpx 34rpx rgba(15, 23, 42, 0.06);
}

.park-card {
  padding: 24rpx;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.park-main {
  flex: 1;
  padding-right: 20rpx;
}

.park-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #111827;
}

.park-address {
  margin-top: 8rpx;
  font-size: 23rpx;
  color: #6b7280;
}

.park-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #4b5563;
  line-height: 1.6;
}

.park-status {
  width: 88rpx;
  height: 88rpx;
  border-radius: 26rpx;
  background: #ecfdf5;
  color: #16a34a;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.park-status-num {
  font-size: 30rpx;
  font-weight: 700;
}

.park-status-label {
  margin-top: 2rpx;
  font-size: 20rpx;
}

.map-tip {
  margin-top: 18rpx;
  padding: 20rpx 24rpx;
  font-size: 24rpx;
  color: #4b5563;
  line-height: 1.6;
}

.map-tip.warning {
  color: #b45309;
  background: #fffbeb;
}

.action-row {
  margin: 22rpx 0;
  display: flex;
  gap: 16rpx;
}

.action-btn {
  flex: 1;
  height: 72rpx;
  border-radius: 999rpx;
  background: #ffffff;
  color: #374151;
  font-size: 25rpx;
  font-weight: 600;
  text-align: center;
  line-height: 72rpx;
  box-shadow: 0 10rpx 24rpx rgba(15, 23, 42, 0.05);
}

.action-btn.primary {
  background: #16a34a;
  color: #ffffff;
}

.selected-card,
.empty-card {
  padding: 24rpx;
}

.selected-top {
  display: flex;
  justify-content: space-between;
  gap: 20rpx;
}

.selected-info {
  flex: 1;
}

.selected-name {
  font-size: 30rpx;
  font-weight: 700;
  color: #111827;
}

.selected-intro {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.6;
}

.selected-index {
  width: 58rpx;
  height: 58rpx;
  border-radius: 18rpx;
  background: #16a34a;
  color: #ffffff;
  text-align: center;
  line-height: 58rpx;
  font-size: 27rpx;
  font-weight: 700;
}

.selected-actions {
  margin-top: 22rpx;
  display: flex;
  gap: 16rpx;
}

.selected-btn {
  flex: 1;
  height: 70rpx;
  border-radius: 999rpx;
  background: #ecfdf5;
  color: #15803d;
  font-size: 25rpx;
  font-weight: 600;
  text-align: center;
  line-height: 70rpx;
}

.selected-btn.primary {
  background: #16a34a;
  color: #ffffff;
}

.empty-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #111827;
}

.empty-text {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.6;
}

.section-title {
  margin: 28rpx 4rpx 18rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title text:first-child {
  font-size: 28rpx;
  font-weight: 700;
  color: #111827;
}

.route-tip {
  font-size: 22rpx;
  color: #9ca3af;
}

.scenic-scroll {
  white-space: nowrap;
  width: 100%;
}

.scenic-chip {
  display: inline-flex;
  align-items: center;
  margin-right: 16rpx;
  padding: 16rpx 20rpx;
  border-radius: 999rpx;
  background: #ffffff;
  box-shadow: 0 10rpx 24rpx rgba(15, 23, 42, 0.05);
}

.scenic-chip.active {
  background: #16a34a;
}

.chip-index {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: #dcfce7;
  color: #15803d;
  text-align: center;
  line-height: 36rpx;
  font-size: 22rpx;
  font-weight: 700;
}

.scenic-chip.active .chip-index {
  background: #ffffff;
  color: #15803d;
}

.chip-name {
  margin-left: 10rpx;
  font-size: 24rpx;
  color: #374151;
  font-weight: 600;
}

.scenic-chip.active .chip-name {
  color: #ffffff;
}

.location-text {
  margin: 22rpx 4rpx 30rpx;
  font-size: 23rpx;
  color: #6b7280;
  word-break: break-all;
}
</style>
