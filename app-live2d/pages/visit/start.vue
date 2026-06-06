<template>
  <view class="page">
    <view class="header">
      <view class="title">现场导览启动</view>
      <view class="subtitle">填写本次出行信息后，即可开启 AI 数字人现场导览。</view>
    </view>

    <view class="welcome-card card">
      <view class="welcome-title">欢迎来到{{ areaName || '当前景区' }}</view>
      <view class="welcome-desc">
        AI 数字人导游已准备好为你讲解景点、规划路线、记录游玩过程
      </view>
      <view class="location-row">
        <view class="location-main">当前已进入{{ areaName || '当前景区' }}</view>
        <view class="location-sub">系统已识别当前位置</view>
      </view>
      <view v-if="distanceText" class="distance-text">{{ distanceText }}</view>
    </view>

    <view class="form-card card">
      <view class="form-title">本次出行信息</view>

      <view class="field-group">
        <view class="field-label">出行人数</view>
        <view class="option-grid">
          <view
            v-for="item in peopleOptions"
            :key="item"
            class="option-chip"
            :class="{ active: form.travelPeopleCount === item }"
            @click="form.travelPeopleCount = item"
          >
            {{ item }}
          </view>
        </view>
      </view>

      <view class="field-group">
        <view class="field-label">出行类型</view>
        <view class="option-grid">
          <view
            v-for="item in travelTypeOptions"
            :key="item"
            class="option-chip"
            :class="{ active: form.travelType === item }"
            @click="form.travelType = item"
          >
            {{ item }}
          </view>
        </view>
      </view>

      <view class="field-group">
        <view class="field-label">游玩偏好</view>
        <view class="option-grid option-grid-wide">
          <view
            v-for="item in preferenceOptions"
            :key="item"
            class="option-chip"
            :class="{ active: form.travelPreference === item }"
            @click="form.travelPreference = item"
          >
            {{ item }}
          </view>
        </view>
      </view>

      <view class="field-group">
        <view class="field-label">预计游玩时长</view>
        <view class="option-grid">
          <view
            v-for="item in durationOptions"
            :key="item"
            class="option-chip"
            :class="{ active: form.estimatedDuration === item }"
            @click="form.estimatedDuration = item"
          >
            {{ item }}
          </view>
        </view>
      </view>
    </view>

    <view class="bottom-bar">
      <button class="start-btn" :loading="submitting" @click="handleStartGuide">
        开启现场导览
      </button>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { NATIVE_LIVE2D_SOURCE } from '@/utils/api'
import { openNativeLive2DGuide } from '@/utils/openNativeLive2D.js'
import { startVisitGuide } from '@/utils/visit'

const peopleOptions = ['1人', '2人', '3-5人', '5人以上']
const travelTypeOptions = ['独自', '情侣', '朋友', '亲子', '老人同行']
const preferenceOptions = ['佛教文化', '建筑打卡', '轻松路线', '深度讲解', '美食休闲']
const durationOptions = ['1小时', '2小时', '半天', '全天']

const areaId = ref('')
const parkId = ref('')
const areaCode = ref('')
const parkCode = ref('')
const areaName = ref('')
const parkName = ref('')
const entrySource = ref('gps')
const latitude = ref('')
const longitude = ref('')
const distanceText = ref('')
const submitting = ref(false)

const form = ref({
  travelPeopleCount: peopleOptions[0],
  travelType: travelTypeOptions[0],
  travelPreference: preferenceOptions[0],
  estimatedDuration: durationOptions[1]
})

onLoad((query = {}) => {
  const rawAreaId = decodeQuery(query.areaId || '')
  const rawParkId = decodeQuery(query.parkId || '')
  const rawScenicId = decodeQuery(query.scenicId || '')
  const rawAreaCode = decodeQuery(query.areaCode || '')
  const rawParkCode = decodeQuery(query.parkCode || '')

  areaId.value = parseNumericAreaId(rawAreaId, rawParkId, rawScenicId, rawAreaCode, rawParkCode)
  parkId.value = parseNumericAreaId(rawParkId, rawAreaId, rawScenicId, rawParkCode, rawAreaCode) || areaId.value
  areaCode.value = pickAreaCode(rawAreaCode, rawParkCode, rawAreaId, rawParkId, rawScenicId)
  parkCode.value = pickAreaCode(rawParkCode, rawAreaCode, rawParkId, rawAreaId, rawScenicId) || areaCode.value
  areaName.value = decodeQuery(query.areaName || query.parkName || query.scenicName || '当前景区')
  parkName.value = decodeQuery(query.parkName || query.areaName || query.scenicName || areaName.value)
  entrySource.value = decodeQuery(query.entrySource || 'gps')
  latitude.value = decodeQuery(query.latitude || '')
  longitude.value = decodeQuery(query.longitude || '')
  distanceText.value = decodeQuery(query.distanceText || '')
})

function decodeQuery(value) {
  if (value === undefined || value === null) {
    return ''
  }

  try {
    return decodeURIComponent(String(value))
  } catch (error) {
    return String(value)
  }
}

function parseNumericAreaId(...values) {
  for (const value of values) {
    if (value === undefined || value === null || value === '') {
      continue
    }

    const text = String(value).trim()
    if (!text) {
      continue
    }

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
    if (value === undefined || value === null || value === '') {
      continue
    }

    const text = String(value).trim()
    if (/^AREA_\d+$/i.test(text)) {
      return text.toUpperCase()
    }
  }

  return ''
}

async function handleStartGuide() {
  if (submitting.value) {
    return
  }

  const finalAreaId = parseNumericAreaId(areaId.value, parkId.value, areaCode.value, parkCode.value)
  const finalParkId = parseNumericAreaId(parkId.value, areaId.value, parkCode.value, areaCode.value) || finalAreaId
  const finalAreaCode = areaCode.value || pickAreaCode(areaId.value, parkId.value, parkCode.value)
  const finalParkCode = parkCode.value || finalAreaCode
  const finalAreaName = areaName.value || parkName.value || '当前景区'
  const finalParkName = parkName.value || areaName.value || finalAreaName

  if (!finalAreaId) {
    uni.showToast({
      title: '景区ID异常，请重新进入现场导览',
      icon: 'none'
    })
    return
  }

  submitting.value = true

  try {
    const startResult = await startVisitGuide({
      areaId: finalAreaId,
      areaCode: finalAreaCode,
      areaName: finalAreaName,
      parkId: finalParkId,
      parkCode: finalParkCode,
      parkName: finalParkName,
      entrySource: entrySource.value,
      startSource: entrySource.value === 'gps' ? 'gps' : 'manual',
      latitude: latitude.value,
      longitude: longitude.value,
      travelPeopleCount: form.value.travelPeopleCount,
      groupSize: form.value.travelPeopleCount,
      travelType: form.value.travelType,
      travelPreference: form.value.travelPreference,
      visitPreference: form.value.travelPreference,
      estimatedDuration: form.value.estimatedDuration
    })

    const visitId = startResult.visitId

    const opened = await openNativeLive2DGuide({
      entry: 'visit-start-onsite',
      source: NATIVE_LIVE2D_SOURCE,
      mode: 'onsite',
      isOnsiteGuide: true,
      startVisitGuide: true,
      allowEndVisit: true,
      visitId,
      areaId: finalAreaId,
      areaCode: finalAreaCode,
      areaName: finalAreaName,
      parkId: finalParkId,
      parkCode: finalParkCode,
      parkName: finalParkName,
      scenicId: finalAreaId,
      scenicName: finalAreaName,
      contextType: 'park',
      contextName: finalAreaName,
      trigger: entrySource.value === 'demo' ? 'demo-onsite' : 'gps-onsite',
      entrySource: entrySource.value,
      latitude: latitude.value,
      longitude: longitude.value,
      travelPeopleCount: form.value.travelPeopleCount,
      travelType: form.value.travelType,
      travelPreference: form.value.travelPreference,
      estimatedDuration: form.value.estimatedDuration,
      autoQuestion: `我已经到达${finalAreaName}，请以现场导游的身份为我进行欢迎讲解，并介绍接下来可以怎么玩。`
    })

    if (opened) {
      setTimeout(() => {
        uni.switchTab({
          url: '/pages/index/index'
        })
      }, 300)
    }
  } catch (error) {
    console.log('开启现场导览失败：', error)
    uni.showToast({
      title: error?.message || '开启导览失败，请稍后重试',
      icon: 'none'
    })
  } finally {
    setTimeout(() => {
      submitting.value = false
    }, 1200)
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f5f7fb;
  padding: 28rpx 24rpx 140rpx;
  box-sizing: border-box;
}

.card {
  background: #ffffff;
  border-radius: 24rpx;
  box-shadow: 0 12rpx 28rpx rgba(15, 23, 42, 0.06);
}

.header {
  padding: 8rpx 4rpx 24rpx;
}

.title {
  font-size: 40rpx;
  font-weight: 800;
  color: #111827;
  line-height: 1.4;
}

.subtitle {
  margin-top: 8rpx;
  font-size: 25rpx;
  color: #6b7280;
  line-height: 1.6;
}

.welcome-card {
  padding: 28rpx 26rpx;
  margin-bottom: 22rpx;
  border: 2rpx solid rgba(24, 179, 104, 0.16);
}

.welcome-title {
  font-size: 34rpx;
  font-weight: 800;
  color: #111827;
  line-height: 1.45;
}

.welcome-desc {
  margin-top: 10rpx;
  font-size: 25rpx;
  color: #4b5563;
  line-height: 1.7;
}

.location-row {
  margin-top: 22rpx;
  padding: 20rpx;
  border-radius: 18rpx;
  background: #ecfdf5;
}

.location-main {
  font-size: 25rpx;
  font-weight: 700;
  color: #047857;
}

.location-sub,
.distance-text {
  margin-top: 6rpx;
  font-size: 23rpx;
  color: #059669;
}

.form-card {
  padding: 28rpx 26rpx 12rpx;
}

.form-title {
  font-size: 31rpx;
  font-weight: 800;
  color: #111827;
  margin-bottom: 24rpx;
}

.field-group {
  margin-bottom: 28rpx;
}

.field-label {
  margin-bottom: 14rpx;
  font-size: 25rpx;
  font-weight: 700;
  color: #374151;
}

.option-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14rpx;
}

.option-grid-wide {
  grid-template-columns: repeat(3, 1fr);
}

.option-chip {
  min-height: 58rpx;
  padding: 12rpx 8rpx;
  border-radius: 16rpx;
  background: #f3f4f6;
  color: #374151;
  font-size: 23rpx;
  line-height: 1.35;
  text-align: center;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
}

.option-chip.active {
  background: #eff6ff;
  color: #2f80ed;
  font-weight: 700;
  box-shadow: inset 0 0 0 2rpx rgba(47, 128, 237, 0.24);
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 -8rpx 24rpx rgba(15, 23, 42, 0.08);
}

.start-btn {
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 999rpx;
  background: #18b368;
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 800;
}

.start-btn::after {
  border: none;
}
</style>
