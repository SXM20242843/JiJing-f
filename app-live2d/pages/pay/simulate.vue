<template>
  <view class="page">
    <view class="pay-card">
      <view class="pay-header">
        <view class="pay-icon">💳</view>
        <view class="pay-title-wrap">
          <view class="pay-title">景区模拟支付</view>
          <view class="pay-subtitle">扫描设施收款二维码后生成消费记录</view>
        </view>
      </view>

      <view class="merchant-card">
        <view class="merchant-row">
          <text class="label">商户名称</text>
          <text class="value">{{ shopNameText }}</text>
        </view>

        <view class="merchant-row">
          <text class="label">商户编号</text>
          <text class="value">{{ form.shopCode || '暂无' }}</text>
        </view>

        <view class="merchant-row">
          <text class="label">消费类型</text>
          <text class="value">{{ shopTypeText }}</text>
        </view>

        <view class="merchant-row">
          <text class="label">所属位置</text>
          <text class="value">{{ locationText }}</text>
        </view>

        <view class="merchant-row">
          <text class="label">消费金额</text>
          <input
            class="input amount-input"
            v-model="amountText"
            type="digit"
            placeholder="请输入金额"
          />
        </view>
      </view>

      <view v-if="rawQrText" class="qr-card">
        <view class="qr-title">扫码来源</view>
        <view class="qr-content">{{ rawQrText }}</view>
      </view>

      <view class="notice-card">
        <view class="notice-title">演示说明</view>
        <view class="notice-text">
          当前为比赛演示支付流程，不接入真实微信扣款。点击“模拟支付成功”后，
          系统会将本次消费写入游客支付记录表，并可用于用户画像和消费分析。
        </view>
      </view>

      <button
        class="pay-btn"
        :disabled="submitting"
        @click="handleSubmit"
      >
        {{ submitting ? '提交中...' : '模拟微信支付成功' }}
      </button>

      <view class="record-link" @click="goRecords">
        查看我的消费记录
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  getCurrentUserId,
  requireLogin
} from '@/utils/auth'
import {
  getPaymentShopInfo,
  submitSimulatePayment
} from '@/utils/payment'
import {
  getCurrentVisitId
} from '@/utils/visit'

const typeOptions = [
  { label: '门票消费', value: 'ticket' },
  { label: '餐饮消费', value: 'food' },
  { label: '购物消费', value: 'shopping' },
  { label: '交通消费', value: 'transport' },
  { label: '住宿消费', value: 'accommodation' },
  { label: '娱乐消费', value: 'entertainment' }
]

const rawQrText = ref('')
const amountText = ref('')
const submitting = ref(false)
const shopInfoLoading = ref(false)
const typeIndex = ref(1)

const form = ref({
  shopCode: '',
  merchantId: '',
  merchantName: '',
  shopType: '',
  shopTypeName: '',
  locationId: '',
  spotName: '',
  parkName: '',
  consumptionType: '',
  rawQr: ''
})

const shopNameText = computed(() => {
  if (shopInfoLoading.value) {
    return '正在查询商户信息...'
  }

  return form.value.merchantName || '商户信息待确认'
})

const shopTypeText = computed(() => {
  return (
    form.value.shopTypeName ||
    getTypeLabel(form.value.consumptionType) ||
    getTypeLabel(form.value.shopType) ||
    '待确认'
  )
})

const locationText = computed(() => {
  return (
    form.value.spotName ||
    form.value.parkName ||
    form.value.locationId ||
    '暂无'
  )
})

function pickFirst(...values) {
  return values.find(value => value !== undefined && value !== null && value !== '') || ''
}

function safeGetStorage(key) {
  try {
    return uni.getStorageSync(key)
  } catch (error) {
    console.warn(`读取本地缓存失败：${key}`, error)
    return ''
  }
}

function getPaymentVisitContext() {
  const currentVisitInfo = safeGetStorage('currentVisitInfo') || {}
  const visitId = pickFirst(
    getCurrentVisitId(),
    currentVisitInfo.currentVisitId,
    currentVisitInfo.visitId,
    safeGetStorage('currentVisitId')
  )
  const areaId = pickFirst(
    currentVisitInfo.currentParkId,
    currentVisitInfo.parkId,
    currentVisitInfo.areaId,
    safeGetStorage('currentParkId')
  )

  return {
    visitId,
    areaId
  }
}

function parseQuery(queryString = '') {
  const result = {}

  queryString
    .replace(/^\?/, '')
    .split('&')
    .filter(Boolean)
    .forEach(pair => {
      const [rawKey, rawValue = ''] = pair.split('=')
      const key = decodeURIComponent(rawKey || '')
      const value = decodeURIComponent(rawValue || '')
      if (key) {
        result[key] = value
      }
    })

  return result
}

function parseQrContent(content = '') {
  const text = String(content || '').trim()

  if (!text) {
    return {}
  }

  try {
    const json = JSON.parse(text)
    return {
      shopCode: pickFirst(json.shop_code, json.shopCode, json.code)
    }
  } catch (e) {
    // 不是 JSON，继续按 URL / 文本解析
  }

  if (text.includes('?')) {
    const queryString = text.split('?')[1] || ''
    const params = parseQuery(queryString)

    return {
      shopCode: pickFirst(params.shop_code, params.shopCode, params.code)
    }
  }

  if (text.includes(':')) {
    const parts = text.split(':')
    return {
      shopCode: parts[1] || parts[0]
    }
  }

  return {
    shopCode: text
  }
}

function normalizeConsumptionType(value = '') {
  const text = String(value || '').trim().toLowerCase()

  if (!text) return ''
  if (['ticket', '门票'].includes(text)) return 'ticket'
  if (['food', 'restaurant', 'dining', '餐饮', '餐厅'].includes(text)) return 'food'
  if (['shopping', 'shop', '文创', '购物', '商店'].includes(text)) return 'shopping'
  if (['transport', 'parking', 'bus', '交通', '停车场'].includes(text)) return 'transport'
  if (['entertainment', '娱乐'].includes(text)) return 'entertainment'
  if (['accommodation', 'hotel', '住宿', '酒店', '客栈', '民宿'].includes(text)) return 'accommodation'

  return text
}

function syncTypeIndex(typeValue) {
  const normalized = normalizeConsumptionType(typeValue)

  if (!normalized) {
    form.value.consumptionType = ''
    return
  }

  const index = typeOptions.findIndex(item => item.value === normalized)
  typeIndex.value = index >= 0 ? index : 1
  form.value.consumptionType = index >= 0 ? typeOptions[typeIndex.value].value : normalized
}

function normalizeShopInfo(info = {}) {
  if (!info) return {}

  return {
    shopCode: pickFirst(info.shop_code, info.shopCode, info.code, form.value.shopCode),
    merchantId: pickFirst(info.merchant_id, info.merchantId, info.facilityId, info.id),
    merchantName: pickFirst(
      info.shop_name,
      info.shopName,
      info.merchant_name,
      info.merchantName,
      info.facilityName,
      info.name
    ),
    shopType: pickFirst(info.shop_type, info.shopType, info.consumption_type, info.consumptionType, info.type),
    shopTypeName: pickFirst(info.shop_type_name, info.shopTypeName, info.type_name, info.typeName),
    locationId: pickFirst(info.location_id, info.locationId, info.areaId, info.spotId),
    spotName: pickFirst(info.spot_name, info.spotName, info.scenicName, info.sceneName),
    parkName: pickFirst(info.park_name, info.parkName, info.areaName),
    consumptionType: pickFirst(info.consumption_type, info.consumptionType, info.shop_type, info.shopType, info.type)
  }
}

async function loadShopInfo(shopCode) {
  if (!shopCode) return

  shopInfoLoading.value = true

  try {
    const info = await getPaymentShopInfo(shopCode)
    const normalized = normalizeShopInfo(info || {})

    form.value = {
      ...form.value,
      ...normalized,
      merchantName: normalized.merchantName || form.value.merchantName || '',
      locationId: normalized.locationId || form.value.locationId || ''
    }

    syncTypeIndex(normalized.consumptionType || form.value.consumptionType)
  } catch (error) {
    console.log('商户信息查询失败，等待后端按 shopCode 校验：', error)
  } finally {
    shopInfoLoading.value = false
  }
}

function validateAmount(value) {
  const amount = Number(value)

  if (!Number.isFinite(amount) || amount <= 0) {
    return 0
  }

  return Number(amount.toFixed(2))
}

function getTypeLabel(type) {
  if (!String(type || '').trim()) return ''

  const normalized = normalizeConsumptionType(type)
  const item = typeOptions.find(row => row.value === normalized)
  return item?.label || type
}

function isRealLoginUserId(userId) {
  const text = String(userId || '').trim()

  if (!text) return false
  if (text === 'anonymous') return false
  if (text.startsWith('visitor_')) return false
  if (text.startsWith('android-live2d-')) return false

  return true
}

async function handleSubmit() {
  if (!requireLogin()) {
    return
  }

  const userId = getCurrentUserId()

  if (!userId) {
    uni.showToast({
      title: '未获取到用户ID',
      icon: 'none'
    })
    return
  }

  if (!isRealLoginUserId(userId)) {
    uni.showToast({
      title: '请使用登录账号支付',
      icon: 'none'
    })
    return
  }

  const amount = validateAmount(amountText.value)

  if (!amount) {
    uni.showToast({
      title: '请输入有效金额',
      icon: 'none'
    })
    return
  }

  if (!form.value.shopCode) {
    uni.showToast({
      title: '未识别到商户二维码',
      icon: 'none'
    })
    return
  }

  if (submitting.value) {
    return
  }

  submitting.value = true

  const visitContext = getPaymentVisitContext()

  const payload = {
    shopCode: form.value.shopCode,
    shop_code: form.value.shopCode,
    amount,
    merchantId: form.value.merchantId || undefined,
    merchant_id: form.value.merchantId || undefined,
    merchantName: form.value.merchantName || undefined,
    merchant_name: form.value.merchantName || undefined,
    locationId: form.value.locationId || undefined,
    location_id: form.value.locationId || undefined,
    consumptionType: form.value.consumptionType || form.value.shopType || undefined,
    consumption_type: form.value.consumptionType || form.value.shopType || undefined,
    visitId: visitContext.visitId || undefined,
    visit_id: visitContext.visitId || undefined,
    areaId: visitContext.areaId || undefined,
    area_id: visitContext.areaId || undefined,
    source: 'app-scan-pay'
  }

  try {
    await submitSimulatePayment(payload)

    uni.showModal({
      title: '支付成功',
      content: `已模拟支付 ¥${amount.toFixed(2)}，消费记录已提交入库。`,
      confirmText: '查看记录',
      cancelText: '返回首页',
      success: res => {
        if (res.confirm) {
          goRecords()
        } else {
          uni.switchTab({
            url: '/pages/index/index'
          })
        }
      }
    })
  } catch (error) {
    console.warn('模拟支付失败：', error)
  } finally {
    submitting.value = false
  }
}

function goRecords() {
  uni.navigateTo({
    url: '/pages/pay/records'
  })
}

onLoad(async options => {
  if (!requireLogin()) {
    return
  }

  const qr = decodeURIComponent(options.qr || '')
  rawQrText.value = qr

  const parsed = parseQrContent(qr)

  form.value = {
    ...form.value,
    shopCode: parsed.shopCode || '',
    merchantId: '',
    merchantName: '',
    shopType: '',
    shopTypeName: '',
    locationId: '',
    spotName: '',
    parkName: '',
    consumptionType: '',
    rawQr: qr
  }

  syncTypeIndex(form.value.consumptionType)

  if (form.value.shopCode) {
    await loadShopInfo(form.value.shopCode)
  } else {
    uni.showToast({
      title: '二维码格式不正确',
      icon: 'none'
    })
  }
})
</script>

<style>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f5f7fb 0%, #eef4ff 100%);
  padding: 24rpx;
  box-sizing: border-box;
}

.pay-card {
  background: #ffffff;
  border-radius: 30rpx;
  padding: 28rpx 24rpx;
  box-shadow: 0 12rpx 28rpx rgba(15, 23, 42, 0.06);
}

.pay-header {
  display: flex;
  align-items: center;
  margin-bottom: 28rpx;
}

.pay-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #2f80ed 0%, #56ccf2 100%);
  color: #ffffff;
  font-size: 38rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.pay-title-wrap {
  margin-left: 20rpx;
  flex: 1;
}

.pay-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #1f2937;
}

.pay-subtitle {
  margin-top: 8rpx;
  font-size: 23rpx;
  color: #6b7280;
  line-height: 1.6;
}

.merchant-card {
  border-radius: 24rpx;
  background: #f8fbff;
  padding: 12rpx 22rpx;
}

.merchant-row {
  min-height: 88rpx;
  display: flex;
  align-items: center;
  border-bottom: 1rpx solid #e5eefb;
}

.merchant-row:last-child {
  border-bottom: none;
}

.label {
  width: 150rpx;
  font-size: 25rpx;
  color: #64748b;
  flex-shrink: 0;
}

.value {
  flex: 1;
  font-size: 26rpx;
  color: #1f2937;
  word-break: break-all;
}

.input {
  flex: 1;
  height: 72rpx;
  font-size: 26rpx;
  color: #1f2937;
}

.amount-input {
  font-weight: 700;
}

.picker {
  flex: 1;
}

.picker-text {
  height: 72rpx;
  line-height: 72rpx;
  font-size: 26rpx;
  color: #1f2937;
}

.qr-card,
.notice-card {
  margin-top: 22rpx;
  padding: 22rpx;
  border-radius: 22rpx;
}

.qr-card {
  background: #f9fafb;
}

.qr-title,
.notice-title {
  font-size: 26rpx;
  font-weight: 700;
  color: #1f2937;
}

.qr-content {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #6b7280;
  line-height: 1.7;
  word-break: break-all;
}

.notice-card {
  background: #fff7ed;
}

.notice-text {
  margin-top: 10rpx;
  font-size: 23rpx;
  color: #9a3412;
  line-height: 1.7;
}

.pay-btn {
  margin-top: 30rpx;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 999rpx;
  background: #2f80ed;
  color: #ffffff;
  font-size: 29rpx;
  font-weight: 700;
}

.pay-btn[disabled] {
  background: #9ca3af;
  color: #ffffff;
}

.record-link {
  margin-top: 24rpx;
  text-align: center;
  color: #2f80ed;
  font-size: 25rpx;
}
</style>
