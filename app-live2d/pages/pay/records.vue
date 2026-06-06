<template>
  <view class="page">
    <view class="summary-card">
      <view class="summary-title">我的消费记录</view>
      <view class="summary-desc">
        展示通过景区设施二维码产生的模拟支付流水，可用于用户画像和消费偏好分析。
      </view>

      <view class="summary-stats">
        <view class="summary-stat">
          <view class="summary-value">{{ recordList.length }}</view>
          <view class="summary-label">消费笔数</view>
        </view>
        <view class="summary-stat">
          <view class="summary-value">¥{{ totalAmountText }}</view>
          <view class="summary-label">累计消费</view>
        </view>
      </view>
    </view>

    <view v-if="loading" class="status-card">
      正在加载消费记录...
    </view>

    <view v-else-if="recordList.length === 0" class="empty-card">
      <view class="empty-icon">💳</view>
      <view class="empty-title">暂无消费记录</view>
      <view class="empty-desc">
        可在首页点击“扫码消费”，扫描管理员端商户二维码后完成模拟支付。
      </view>
    </view>

    <view v-else class="record-list">
      <view
        class="record-card"
        v-for="item in recordList"
        :key="item.id || item.paymentId || item.payment_id"
      >
        <view class="record-top">
          <view class="merchant-info">
            <view class="merchant-name">{{ item.shopName || '景区商户' }}</view>
            <view class="record-time">{{ item.consumeTime || '暂无时间' }}</view>
          </view>

          <view class="amount">
            ¥{{ formatAmount(item.amount) }}
          </view>
        </view>

        <view class="record-tags">
          <text class="tag">{{ getTypeLabel(item.shopType) }}</text>
          <text class="tag status">{{ getStatusLabel(item.status) }}</text>
        </view>

        <view class="record-info">
          <view class="info-row">
            <text class="info-label">商户编码</text>
            <text class="info-value">{{ item.shopCode || '暂无' }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">所属位置</text>
            <text class="info-value">{{ item.locationName || '暂无' }}</text>
          </view>
          <view class="info-row" v-if="item.paymentId">
            <text class="info-label">支付流水</text>
            <text class="info-value">{{ item.paymentId }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { getCurrentUserId, requireLogin } from '@/utils/auth'
import { getPaymentRecords } from '@/utils/payment'

const loading = ref(false)
const recordList = ref([])

const totalAmountText = computed(() => {
  const total = recordList.value.reduce((sum, item) => {
    const amount = Number(item.amount || 0)
    return Number.isFinite(amount) ? sum + amount : sum
  }, 0)

  return total.toFixed(2)
})

function pickFirst(...values) {
  return values.find(value => value !== undefined && value !== null && value !== '') || ''
}

function isRealLoginUserId(userId) {
  const text = String(userId || '').trim()

  if (!text) return false
  if (text === 'anonymous') return false
  if (text.startsWith('visitor_')) return false
  if (text.startsWith('android-live2d-')) return false

  return true
}

function normalizeRecord(item = {}) {
  const shopType = pickFirst(
    item.shop_type,
    item.shopType,
    item.consumption_type,
    item.consumptionType,
    item.scene_type,
    item.sceneType
  )
  const parkName = pickFirst(item.park_name, item.parkName, item.areaName)
  const spotName = pickFirst(item.spot_name, item.spotName, item.scenicName, item.sceneName)
  const locationId = pickFirst(item.location_id, item.locationId)

  return {
    id: item.id,
    paymentId: pickFirst(item.payment_id, item.paymentId, item.record_no, item.recordNo),
    shopCode: pickFirst(item.shop_code, item.shopCode, item.merchant_code, item.merchantCode),
    shopName: pickFirst(item.shop_name, item.shopName, item.merchant_name, item.merchantName),
    shopType,
    amount: pickFirst(item.amount, item.pay_amount, item.payAmount, 0),
    status: pickFirst(item.status, item.pay_status, item.payStatus, 'completed'),
    consumeTime: pickFirst(
      item.consume_time,
      item.consumeTime,
      item.pay_time,
      item.payTime,
      item.created_at,
      item.createdAt
    ),
    locationName: pickFirst(spotName, parkName, locationId)
  }
}

function extractList(data) {
  if (Array.isArray(data)) return data

  if (Array.isArray(data?.records)) return data.records
  if (Array.isArray(data?.list)) return data.list
  if (Array.isArray(data?.rows)) return data.rows

  if (Array.isArray(data?.data)) return data.data
  if (Array.isArray(data?.data?.records)) return data.data.records
  if (Array.isArray(data?.data?.list)) return data.data.list
  if (Array.isArray(data?.data?.rows)) return data.data.rows

  return []
}

function formatAmount(amount) {
  const value = Number(amount || 0)
  return Number.isFinite(value) ? value.toFixed(2) : '0.00'
}

function getTypeLabel(type) {
  const map = {
    ticket: '门票消费',
    food: '餐饮消费',
    restaurant: '餐饮消费',
    dining: '餐饮消费',
    shopping: '购物消费',
    shop: '购物消费',
    transport: '交通消费',
    parking: '交通消费',
    entertainment: '娱乐消费',
	accommodation: '住宿消费',
	hotel: '住宿消费'
  }

  return map[String(type || '').toLowerCase()] || type || '其他消费'
}

function getStatusLabel(status) {
  const map = {
    completed: '已完成',
    success: '已完成',
    paid: '已完成',
    pending: '待确认',
    refunded: '已退款',
    failed: '支付失败'
  }

  return map[String(status || '').toLowerCase()] || status || '已完成'
}

async function loadRecords() {
  if (!requireLogin()) {
    recordList.value = []
    uni.stopPullDownRefresh()
    return
  }

  const userId = getCurrentUserId()

  if (!isRealLoginUserId(userId)) {
    recordList.value = []
    uni.showToast({
      title: '请使用登录账号查看',
      icon: 'none'
    })
    uni.stopPullDownRefresh()
    return
  }

  loading.value = true

  try {
    const data = await getPaymentRecords()
    const list = extractList(data)
    recordList.value = list.map(item => normalizeRecord(item))
  } catch (error) {
    console.warn('消费记录加载失败：', error)
    uni.showToast({
      title: '消费记录加载失败',
      icon: 'none'
    })
    recordList.value = []
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

onShow(() => {
  loadRecords()
})

onPullDownRefresh(() => {
  loadRecords()
})
</script>

<style>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f5f7fb 0%, #eef4ff 100%);
  padding: 24rpx;
  box-sizing: border-box;
}

.summary-card,
.status-card,
.empty-card,
.record-card {
  background: #ffffff;
  border-radius: 28rpx;
  box-shadow: 0 12rpx 28rpx rgba(15, 23, 42, 0.06);
}

.summary-card {
  padding: 28rpx 24rpx;
  margin-bottom: 24rpx;
}

.summary-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #1f2937;
}

.summary-desc {
  margin-top: 10rpx;
  color: #6b7280;
  font-size: 24rpx;
  line-height: 1.7;
}

.summary-stats {
  display: flex;
  gap: 18rpx;
  margin-top: 24rpx;
}

.summary-stat {
  flex: 1;
  background: #f5f8ff;
  border-radius: 22rpx;
  padding: 22rpx 12rpx;
  text-align: center;
}

.summary-value {
  font-size: 34rpx;
  font-weight: 700;
  color: #2f80ed;
}

.summary-label {
  margin-top: 8rpx;
  color: #64748b;
  font-size: 22rpx;
}

.status-card {
  padding: 30rpx 24rpx;
  color: #6b7280;
  font-size: 25rpx;
  text-align: center;
}

.empty-card {
  padding: 60rpx 36rpx;
  text-align: center;
}

.empty-icon {
  font-size: 60rpx;
}

.empty-title {
  margin-top: 20rpx;
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2937;
}

.empty-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.7;
}

.record-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.record-card {
  padding: 24rpx;
}

.record-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20rpx;
}

.merchant-info {
  flex: 1;
  min-width: 0;
}

.merchant-name {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2937;
}

.record-time {
  margin-top: 8rpx;
  color: #9ca3af;
  font-size: 22rpx;
}

.amount {
  color: #ef4444;
  font-size: 34rpx;
  font-weight: 700;
  flex-shrink: 0;
}

.record-tags {
  display: flex;
  gap: 12rpx;
  margin-top: 16rpx;
}

.tag {
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: #eff6ff;
  color: #2f80ed;
  font-size: 22rpx;
}

.tag.status {
  background: #ecfdf5;
  color: #18b368;
}

.record-info {
  margin-top: 18rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #eef2f7;
}

.info-row {
  display: flex;
  align-items: flex-start;
  margin-top: 10rpx;
}

.info-label {
  width: 130rpx;
  color: #9ca3af;
  font-size: 22rpx;
  flex-shrink: 0;
}

.info-value {
  flex: 1;
  color: #6b7280;
  font-size: 22rpx;
  word-break: break-all;
}
</style>
