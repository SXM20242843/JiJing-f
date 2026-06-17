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
/* ============================================================
   即境 · 消费记录 — 账单流水 + 消费统计
   设计方向：Clean APP Billing · 统一卡片 + 金额突出
   签名元素：汇总统计卡 · 流水卡片 · 轻量标签
   本轮只改 CSS，不改 template / script / 任何业务入口
   ============================================================ */

/* ---------- Page ---------- */
.page {
  min-height: 100vh;
  background: #f5f7fb;
  padding: 24rpx;
  box-sizing: border-box;
}

/* ---------- Summary Card ---------- */
.summary-card {
  background: #ffffff;
  border-radius: 28rpx;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
  border: 1rpx solid #f3f4f6;
  padding: 28rpx 24rpx;
  margin-bottom: 24rpx;
}

.summary-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2937;
  padding-left: 18rpx;
  border-left: 6rpx solid #2f80ed;
  line-height: 1.3;
}

.summary-desc {
  margin-top: 10rpx;
  color: #6b7280;
  font-size: 24rpx;
  line-height: 1.6;
}

.summary-stats {
  display: flex;
  gap: 14rpx;
  margin-top: 22rpx;
}

.summary-stat {
  flex: 1;
  background: #f8fafc;
  border-radius: 20rpx;
  border: 1rpx solid #f3f4f6;
  padding: 20rpx 12rpx;
  text-align: center;
}

.summary-value {
  font-size: 32rpx;
  font-weight: 700;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-label {
  margin-top: 6rpx;
  color: #6b7280;
  font-size: 22rpx;
}

/* ---------- Status / Loading ---------- */
.status-card {
  background: #ffffff;
  border-radius: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
  border: 1rpx solid #f3f4f6;
  padding: 80rpx 24rpx 64rpx;
  color: #6b7280;
  font-size: 26rpx;
  text-align: center;
}

/* ---------- Empty State ---------- */
.empty-card {
  background: #ffffff;
  border-radius: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
  border: 1rpx solid #f3f4f6;
  padding: 72rpx 36rpx 64rpx;
  text-align: center;
}

.empty-icon {
  font-size: 56rpx;
}

.empty-title {
  margin-top: 20rpx;
  font-size: 28rpx;
  font-weight: 700;
  color: #1f2937;
}

.empty-desc {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.65;
}

/* ---------- Record List ---------- */
.record-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

/* ---------- Record Card（账单流水卡） ---------- */
.record-card {
  background: #ffffff;
  border-radius: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
  border: 1rpx solid #f3f4f6;
  padding: 24rpx;
}

.record-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16rpx;
}

.merchant-info {
  flex: 1;
  min-width: 0;
}

.merchant-name {
  font-size: 28rpx;
  font-weight: 700;
  color: #1f2937;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-time {
  margin-top: 6rpx;
  color: #9ca3af;
  font-size: 22rpx;
}

/* 金额 — 深色突出，非红色 */
.amount {
  color: #1f2937;
  font-size: 32rpx;
  font-weight: 700;
  flex-shrink: 0;
  white-space: nowrap;
}

/* ---------- Tags（消费类型 + 支付状态） ---------- */
.record-tags {
  display: flex;
  gap: 10rpx;
  margin-top: 14rpx;
}

.tag {
  padding: 5rpx 14rpx;
  border-radius: 999rpx;
  background: #eff6ff;
  color: #2f80ed;
  font-size: 20rpx;
  font-weight: 500;
  border: 1rpx solid rgba(47, 128, 237, 0.08);
}

.tag.status {
  background: #ecfdf5;
  color: #16a34a;
  border-color: rgba(22, 163, 74, 0.12);
}

/* ---------- Record Info（详情字段） ---------- */
.record-info {
  margin-top: 16rpx;
  padding-top: 14rpx;
  border-top: 1rpx solid #f3f4f6;
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
  line-height: 1.5;
  word-break: break-all;
  min-width: 0;
}
</style>
