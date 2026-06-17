<template>
  <view class="page">
    <view v-if="loading" class="status-card card">
      <text class="status-text">正在加载游玩报告...</text>
    </view>

    <view v-else-if="error" class="status-card card">
      <text class="status-title">报告加载失败，请稍后重试</text>
      <text class="status-desc">{{ errorMessage }}</text>
      <view class="retry-btn" @click="loadReport">重新加载</view>
    </view>

    <view v-else-if="empty" class="status-card card">
      <text class="status-title">暂无游玩报告</text>
      <text class="status-desc">结束现场导览后，可在这里查看本次游玩的汇总信息。</text>
      <view class="retry-btn" @click="loadReport">重新加载</view>
    </view>

    <view v-else-if="unfinished" class="status-card card">
      <text class="status-title">导览尚未结束</text>
      <text class="status-desc">当前 visit 仍在进行中，结束现场导览后再查看完整报告。</text>
      <view class="retry-btn" @click="loadReport">重新加载</view>
      <view class="home-btn" @click="goHome">返回首页</view>
    </view>

    <template v-else>
      <view class="report-hero card">
        <view class="hero-tag">游玩报告</view>
        <view class="hero-title">{{ reportView.parkName || '本次游玩' }}</view>
        <view class="hero-subtitle">
          {{ reportView.startTime || '暂无开始时间' }} - {{ reportView.endTime || '暂无结束时间' }}
        </view>

        <view class="summary-grid">
          <view class="summary-item">
            <view class="summary-value">{{ reportView.stayDurationText }}</view>
            <view class="summary-label">总停留</view>
          </view>
          <view class="summary-item">
            <view class="summary-value">{{ reportView.spotCount }}</view>
            <view class="summary-label">景点数</view>
          </view>
          <view class="summary-item">
            <view class="summary-value">{{ reportView.aiQuestionCount }}</view>
            <view class="summary-label">AI 提问</view>
          </view>
          <view class="summary-item">
            <view class="summary-value">{{ reportView.favoriteCount }}</view>
            <view class="summary-label">收藏</view>
          </view>
        </view>
      </view>

      <view class="section-card card">
        <view class="section-title">本次出行信息</view>
        <view class="info-list">
          <view class="info-row">
            <text class="info-label">同行人数</text>
            <text class="info-value">{{ reportView.groupSize || '暂无' }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">出行类型</text>
            <text class="info-value">{{ reportView.travelType || '暂无' }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">本次偏好</text>
            <text class="info-value">{{ reportView.visitPreference || '暂无' }}</text>
          </view>
        </view>
      </view>

      <view class="section-card card">
        <view class="section-title">景点停留明细</view>
        <view v-if="reportView.spots.length === 0" class="empty-line">
          暂无景点停留记录，后续可通过景点讲解自动生成
        </view>
        <view v-else class="spot-list">
          <view
            class="spot-item"
            v-for="(item, index) in reportView.spots"
            :key="item.id || item.scenicId || item.scenicName || index"
          >
            <view class="spot-main">
              <view class="spot-name">{{ item.scenicName || '未命名景点' }}</view>
              <view class="spot-duration">{{ item.stayDurationText }}</view>
            </view>
            <view class="spot-time">
              {{ item.enterTime || '暂无进入时间' }} - {{ item.leaveTime || '暂无离开时间' }}
            </view>
          </view>
        </view>
      </view>

      <view class="section-card card">
        <view class="section-title">消费记录</view>
        <view class="consume-status" :class="reportView.consumeStatus">
          {{ consumeStatusText }}
        </view>
        <view class="cost-grid">
          <view class="cost-item" v-for="item in costItems" :key="item.key">
            <text class="cost-label">{{ item.label }}</text>
            <text class="cost-value">{{ item.value }} 元</text>
          </view>
        </view>
        <view class="consume-detail">
          <view v-if="reportView.consumeList.length === 0" class="empty-line">暂无消费明细</view>
          <view v-else class="consume-list">
            <view
              class="consume-item"
              v-for="(item, index) in reportView.consumeList"
              :key="item.paymentId || index"
            >
              <view class="consume-main">
                <view class="consume-merchant">{{ item.merchantName || '未知商户' }}</view>
                <view class="consume-amount">{{ formatMoney(item.amount) }} 元</view>
              </view>
              <view class="consume-meta">
                {{ item.consumptionType || '其他消费' }} · {{ item.payTime || '暂无时间' }}
              </view>
            </view>
          </view>
        </view>
      </view>

      <view class="section-card card">
        <view class="section-title">
          {{ hasSubmittedFeedback ? '已收到你的反馈' : '本次体验反馈' }}
        </view>
        <view class="section-desc">
          {{ hasSubmittedFeedback ? '感谢你对本次导览的评价' : '你的反馈将帮助数字人导游为你提供更好的路线推荐' }}
        </view>

        <template v-if="hasSubmittedFeedback">
          <view class="feedback-result">
            <view class="feedback-result-row">
              <text class="info-label">满意度</text>
              <view class="readonly-stars">
                <text
                  class="star"
                  :class="{ active: star <= Number(reportView.satisfaction) }"
                  v-for="star in feedbackStars"
                  :key="star"
                >
                  ★
                </text>
                <text class="readonly-score">{{ formatSatisfaction(reportView.satisfaction) }}</text>
              </view>
            </view>
            <view class="feedback-result-row">
              <text class="info-label">是否愿意推荐</text>
              <text class="info-value">{{ formatRecommend(reportView.recommend) }}</text>
            </view>
            <view class="comment-box">
              {{ reportView.comment || '暂无反馈内容' }}
            </view>
          </view>
        </template>

        <template v-else>
          <view class="feedback-form">
            <view class="form-label">满意度</view>
            <view class="star-select">
              <text
                class="star"
                :class="{ active: star <= feedbackForm.satisfaction }"
                v-for="star in feedbackStars"
                :key="star"
                @click="setFeedbackSatisfaction(star)"
              >
                ★
              </text>
            </view>

            <view class="form-label">一句话反馈</view>
            <textarea
              class="feedback-textarea"
              v-model="feedbackForm.comment"
              maxlength="100"
              auto-height
              placeholder="可以说说本次导览体验"
              placeholder-class="textarea-placeholder"
            ></textarea>

            <view class="form-label">是否愿意推荐</view>
            <view class="recommend-options">
              <view
                class="recommend-option"
                :class="{ active: feedbackForm.recommend === true }"
                @click="setFeedbackRecommend(true)"
              >
                是
              </view>
              <view
                class="recommend-option"
                :class="{ active: feedbackForm.recommend === false }"
                @click="setFeedbackRecommend(false)"
              >
                否
              </view>
            </view>

            <view
              class="feedback-submit"
              :class="{ disabled: feedbackSubmitting }"
              @click="submitFeedback"
            >
              {{ feedbackSubmitting ? '提交中...' : '提交反馈' }}
            </view>
          </view>
        </template>
      </view>

      <view class="section-card card">
        <view class="section-title">相似景区推荐</view>
        <view v-if="reportView.recommendParks.length === 0" class="empty-line">暂无推荐</view>
        <view v-else class="recommend-list">
          <view
            class="recommend-item"
            v-for="item in reportView.recommendParks"
            :key="item.id || item.parkId || item.name"
            @click="goParkDetail(item)"
          >
            <view class="recommend-name">{{ item.name || item.parkName || '未命名景区' }}</view>
            <view class="recommend-desc">{{ formatText(item.desc || item.description || '查看景区详情', 42) }}</view>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onBackPress, onLoad } from '@dcloudio/uni-app'
import request from '@/utils/request'
import { getCurrentUserId, isLogin } from '@/utils/auth'
import { clearVisitCacheForLogout, getLastEndedVisit, getLastVisitId } from '@/utils/visit'

const loading = ref(true)
const error = ref(false)
const empty = ref(false)
const unfinished = ref(false)
const errorMessage = ref('')
const visitId = ref('')
const fromNativeEnd = ref(false)
const report = ref(null)
const feedbackSubmitting = ref(false)
const feedbackForm = ref({
  satisfaction: 0,
  comment: '',
  recommend: true
})
const feedbackStars = [1, 2, 3, 4, 5]

const reportView = computed(() => normalizeReport(report.value || {}))

const hasSubmittedFeedback = computed(() => {
  return hasValue(reportView.value.satisfaction)
})

const consumeStatusText = computed(() => {
  if (reportView.value.consumeStatus === 'pending') {
    return '消费记录待景区管理员确认'
  }

  if (reportView.value.consumeStatus === 'confirmed') {
    return `本次消费合计 ${formatMoney(reportView.value.totalCost)} 元`
  }

  return '暂无消费记录'
})

const costItems = computed(() => [
  { key: 'ticketCost', label: '门票', value: formatMoney(reportView.value.ticketCost) },
  { key: 'foodCost', label: '餐饮', value: formatMoney(reportView.value.foodCost) },
  { key: 'shoppingCost', label: '购物', value: formatMoney(reportView.value.shoppingCost) },
  { key: 'transportCost', label: '交通', value: formatMoney(reportView.value.transportCost) },
  { key: 'entertainmentCost', label: '娱乐', value: formatMoney(reportView.value.entertainmentCost) },
  { key: 'totalCost', label: '合计', value: formatMoney(reportView.value.totalCost) }
])

function pickFirst(...values) {
  const found = values.find(value => value !== undefined && value !== null && value !== '')
  return found === undefined ? '' : found
}

function pickFirstList(...values) {
  const nonEmptyList = values.find(value => Array.isArray(value) && value.length > 0)
  if (nonEmptyList) return nonEmptyList
  return values.find(value => Array.isArray(value)) || []
}

function hasValue(value) {
  return value !== undefined && value !== null && value !== ''
}

function debugStringify(value) {
  try {
    return JSON.stringify(value, null, 2)
  } catch (error) {
    return String(value)
  }
}

function resolveVisitId(options = {}) {
  const urlVisitId = String(pickFirst(
    options.visitId,
    options.visit_id,
    options.reportVisitId,
    options.report_visit_id,
    options.lastEndedVisitId,
    options.last_ended_visit_id
  ))
  if (urlVisitId) {
    return urlVisitId
  }

  const ended = getLastEndedVisit({ fallbackGlobal: false })
  return String(pickFirst(
    ended.visitId,
    getLastVisitId({ fallbackGlobal: false })
  ))
}

function isTruthy(value) {
  if (value === true || value === 1) return true

  const text = String(value || '').trim().toLowerCase()
  return ['true', '1', 'yes', 'y', '是'].includes(text)
}

function unwrapReportResponse(response) {
  if (!response) return null

  if (
    Object.prototype.hasOwnProperty.call(response, 'code') &&
    Object.prototype.hasOwnProperty.call(response, 'data')
  ) {
    return response.data
  }

  return response
}

function normalizeReportStatus(value) {
  const text = String(value || '').trim().toUpperCase()
  if (['ACTIVE', 'IN_PROGRESS', 'ONGOING', 'RUNNING', 'STARTED', 'VISITING'].includes(text)) {
    return 'ACTIVE'
  }
  if (['ENDED', 'COMPLETED', 'FINISHED', 'DONE'].includes(text)) {
    return 'ENDED'
  }
  return ''
}

function isUnfinishedReport(data = {}) {
  const endTime = pickFirst(data.endTime, data.end_time)
  const status = normalizeReportStatus(pickFirst(
    data.status,
    data.visitStatus,
    data.visit_status,
    data.state,
    data.sessionStatus,
    data.session_status
  ))
  return !endTime && status === 'ACTIVE'
}

function normalizeDuration(value) {
  if (value === undefined || value === null || value === '') return ''

  const seconds = Number(value)
  if (!Number.isFinite(seconds)) {
    return String(value)
  }

  if (seconds < 60) {
    return `${Math.max(0, Math.round(seconds))}秒`
  }

  const minutes = Math.floor(seconds / 60)
  const restSeconds = Math.round(seconds % 60)

  if (minutes < 60) {
    return restSeconds ? `${minutes}分${restSeconds}秒` : `${minutes}分钟`
  }

  const hours = Math.floor(minutes / 60)
  const restMinutes = minutes % 60
  return restMinutes ? `${hours}小时${restMinutes}分钟` : `${hours}小时`
}

function normalizeMoney(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

function normalizeCount(value) {
  const number = Number(value)
  return Number.isFinite(number) ? Math.max(0, Math.floor(number)) : 0
}

function formatMoney(value) {
  const number = normalizeMoney(value)
  return Number.isInteger(number) ? String(number) : number.toFixed(2)
}

function formatText(text, maxLength = 40) {
  const value = String(text || '').replace(/\s+/g, '').trim()
  if (!value) return ''
  return value.length > maxLength ? `${value.slice(0, maxLength)}...` : value
}

function formatSatisfaction(value) {
  if (!hasValue(value)) return '暂无'

  const number = Number(value)
  return Number.isFinite(number) ? `${number}星` : String(value)
}

function normalizeRecommend(value) {
  if (value === true || value === 1) return true
  if (value === false || value === 0) return false

  const text = String(value || '').trim().toLowerCase()
  if (['true', 'yes', 'y', '1', '愿意', '是'].includes(text)) return true
  if (['false', 'no', 'n', '0', '不愿意', '否'].includes(text)) return false

  return ''
}

function formatRecommend(value) {
  const normalized = normalizeRecommend(value)
  if (normalized === true) return '愿意推荐'
  if (normalized === false) return '暂不推荐'
  return '暂无'
}

function normalizeSpot(raw = {}) {
  const stayText = pickFirst(
    raw.durationText,
    raw.duration_text,
    raw.stayDurationText,
    raw.stay_duration_text
  )

  return {
    ...raw,
    spotId: pickFirst(raw.spotId, raw.spot_id),
    scenicId: pickFirst(raw.scenicId, raw.scenic_id, raw.spotId, raw.spot_id, raw.sceneCode, raw.scene_code, raw.id),
    scenicName: pickFirst(raw.spotName, raw.spot_name, raw.scenicName, raw.scenic_name, raw.sceneName, raw.scene_name, raw.name),
    enterTime: pickFirst(raw.enterTime, raw.enter_time, raw.startTime, raw.start_time),
    leaveTime: pickFirst(raw.leaveTime, raw.leave_time, raw.endTime, raw.end_time),
    stayDurationText: stayText || normalizeDuration(pickFirst(
      raw.durationSeconds,
      raw.duration_seconds,
      raw.staySeconds,
      raw.stay_seconds,
      raw.duration,
      raw.stayDuration,
      raw.stay_duration
    ))
  }
}

function normalizeConsumeRecord(raw = {}) {
  return {
    ...raw,
    paymentId: pickFirst(raw.paymentId, raw.payment_id, raw.id),
    merchantName: pickFirst(raw.merchantName, raw.merchant_name, raw.merchant, raw.shopName, raw.shop_name),
    consumptionType: pickFirst(raw.consumptionType, raw.consumption_type, raw.type, raw.category),
    amount: normalizeMoney(pickFirst(raw.amount, raw.payAmount, raw.pay_amount)),
    payTime: pickFirst(raw.payTime, raw.pay_time, raw.createTime, raw.create_time, raw.createdAt, raw.created_at)
  }
}

function summarizeConsumeList(list = []) {
  const result = {
    ticketCost: 0,
    foodCost: 0,
    shoppingCost: 0,
    transportCost: 0,
    entertainmentCost: 0,
    totalCost: 0
  }

  list.forEach((item = {}) => {
    const amount = normalizeMoney(pickFirst(item.amount, item.payAmount, item.pay_amount))
    if (!amount) return

    const type = String(pickFirst(item.consumptionType, item.consumption_type, item.type, 'entertainment')).toLowerCase()
    result.totalCost += amount

    if (type.includes('ticket') || type.includes('门票')) {
      result.ticketCost += amount
    } else if (type.includes('food') || type.includes('餐') || type.includes('美食')) {
      result.foodCost += amount
    } else if (type.includes('shop') || type.includes('shopping') || type.includes('购物')) {
      result.shoppingCost += amount
    } else if (type.includes('transport') || type.includes('parking') || type.includes('交通')) {
      result.transportCost += amount
    } else {
      result.entertainmentCost += amount
    }
  })

  return result
}

function normalizeReport(source = {}) {
  const consume = source.consume || source.consumption || source.consumptionSummary || source.consumption_summary || source.cost || source.costs || {}
  const feedback = source.feedback && typeof source.feedback === 'object' ? source.feedback : {}
  const feedbackText = typeof source.feedback === 'string' ? source.feedback : ''
  const spots = pickFirstList(
    source.spots,
    source.spotStayList,
    source.spot_stay_list,
    source.visitedSpots,
    source.visited_spots,
    source.scenicStays,
    source.scenic_stays,
    source.spotDetails,
    source.spot_details
  )
  const recommendParks = pickFirstList(
    source.recommendParks,
    source.recommend_parks,
    source.recommendationSimilarScenic,
    source.recommendation_similar_scenic,
    source.similarParks,
    source.similar_parks
  )
  const consumeList = pickFirstList(
    source.consumeList,
    source.consume_list,
    source.paymentRecords,
    source.payment_records,
    consume.consumeList,
    consume.consume_list,
    consume.paymentRecords,
    consume.payment_records
  )
  const normalizedConsumeList = Array.isArray(consumeList) ? consumeList.map(normalizeConsumeRecord) : []

  const consumeSummaryFromList = summarizeConsumeList(normalizedConsumeList)
  const ticketCost = normalizeMoney(pickFirst(source.ticketCost, source.ticket_cost, consume.ticketCost, consume.ticket_cost, consumeSummaryFromList.ticketCost))
  const foodCost = normalizeMoney(pickFirst(source.foodCost, source.food_cost, consume.foodCost, consume.food_cost, consumeSummaryFromList.foodCost))
  const shoppingCost = normalizeMoney(pickFirst(source.shoppingCost, source.shopping_cost, consume.shoppingCost, consume.shopping_cost, consumeSummaryFromList.shoppingCost))
  const transportCost = normalizeMoney(pickFirst(source.transportCost, source.transport_cost, consume.transportCost, consume.transport_cost, consumeSummaryFromList.transportCost))
  const entertainmentCost = normalizeMoney(pickFirst(source.entertainmentCost, source.entertainment_cost, consume.entertainmentCost, consume.entertainment_cost, consumeSummaryFromList.entertainmentCost))
  const totalCost = normalizeMoney(
    pickFirst(source.totalCost, source.total_cost, consume.totalCost, consume.total_cost, consumeSummaryFromList.totalCost) ||
    ticketCost + foodCost + shoppingCost + transportCost + entertainmentCost
  )
  const normalizedSpots = Array.isArray(spots) ? spots.map(normalizeSpot) : []
  const rawSpotCount = normalizeCount(pickFirst(source.spotCount, source.spot_count))
  const rawVisitedSpotCount = normalizeCount(pickFirst(source.visitedSpotCount, source.visited_spot_count))
  const normalizedSpotCount = Math.max(normalizedSpots.length, rawSpotCount, rawVisitedSpotCount)
  const normalizedVisitedSpotCount = Math.max(normalizedSpots.length, rawVisitedSpotCount, rawSpotCount)
  console.log('[visit/report] raw spotCount=' + rawSpotCount
    + ', visitedSpotCount=' + rawVisitedSpotCount
    + ', spots.length=' + normalizedSpots.length)
  console.log('[visit/report] normalized spotCount=' + normalizedSpotCount
    + ', normalized spots.length=' + normalizedSpots.length)
  const stayDurationText = pickFirst(source.stayDurationText, source.stay_duration_text, source.durationText, source.duration_text) ||
    normalizeDuration(pickFirst(
      source.durationSeconds,
      source.duration_seconds,
      source.duration,
      source.stayDuration,
      source.stay_duration,
      source.staySeconds,
      source.stay_seconds
    ))
  const consumeStatusRaw = pickFirst(
    source.consumeStatus,
    source.consume_status,
    consume.consumeStatus,
    consume.consume_status,
    consume.status
  )
  const consumeStatus = consumeStatusRaw
    ? String(consumeStatusRaw).toLowerCase()
    : (normalizedConsumeList.length > 0 || totalCost > 0 ? 'confirmed' : 'none')

  return {
    parkName: pickFirst(source.parkName, source.park_name, source.areaName, source.area_name),
    groupSize: pickFirst(source.groupSize, source.group_size),
    travelType: pickFirst(source.travelType, source.travel_type),
    visitPreference: pickFirst(source.visitPreference, source.visit_preference),
    startTime: pickFirst(source.startTime, source.start_time),
    endTime: pickFirst(source.endTime, source.end_time),
    status: pickFirst(source.status, source.visitStatus, source.visit_status),
    stayDurationText: stayDurationText || '暂无',
    spotCount: normalizedSpotCount,
    visitedSpotCount: normalizedVisitedSpotCount,
    aiQuestionCount: pickFirst(source.aiQuestionCount, source.ai_question_count, source.questionCount, source.question_count, 0),
    favoriteCount: pickFirst(source.favoriteCount, source.favorite_count, 0),
    spots: normalizedSpots,
    consumeStatus,
    consumeList: normalizedConsumeList,
    ticketCost,
    foodCost,
    shoppingCost,
    transportCost,
    entertainmentCost,
    totalCost,
    satisfaction: pickFirst(source.satisfaction, source.rating, source.score, feedback.satisfaction, feedback.rating, feedback.score),
    comment: pickFirst(source.comment, feedbackText, source.userComment, source.user_comment, feedback.comment, feedback.userComment, feedback.user_comment),
    recommend: pickFirst(source.recommend, source.isRecommend, source.is_recommend, source.willingRecommend, source.willing_recommend, feedback.recommend, feedback.isRecommend, feedback.is_recommend),
    recommendParks: Array.isArray(recommendParks) ? recommendParks : []
  }
}

async function loadReport() {
  const currentUserId = getCurrentUserId()
  console.log('[visit/report] currentUserId=' + currentUserId + ', visitId=' + visitId.value)

  if (!isLogin() && !fromNativeEnd.value) {
    loading.value = false
    error.value = true
    empty.value = false
    unfinished.value = false
    errorMessage.value = '请先登录后查看游玩报告'
    report.value = null
    uni.showToast({
      title: '请先登录后查看游玩报告',
      icon: 'none'
    })
    return
  }

  if (!visitId.value) {
    loading.value = false
    error.value = false
    empty.value = true
    report.value = null
    return
  }

  loading.value = true
  error.value = false
  empty.value = false
  unfinished.value = false
  errorMessage.value = ''

  try {
    const response = await request({
      url: '/api/app/visit/report/detail',
      method: 'GET',
      data: {
        visitId: visitId.value,
        userId: currentUserId
      },
      needAuth: true,
      showErrorToast: false
    })

    const data = unwrapReportResponse(response)

    if (!data || (typeof data === 'object' && Object.keys(data).length === 0)) {
      report.value = null
      empty.value = true
      return
    }

    const reportUserId = String(pickFirst(data.userId, data.user_id, data.ownerUserId, data.owner_user_id))
    if (reportUserId && currentUserId && reportUserId !== currentUserId) {
      console.warn('[visit/report] user mismatch, clear stale visit cache')
      clearVisitCacheForLogout(currentUserId)
      report.value = null
      error.value = true
      empty.value = false
      unfinished.value = false
      errorMessage.value = '当前账号无权查看该报告，请重新进入现场导览'
      uni.showToast({
        title: '当前账号无权查看该报告',
        icon: 'none'
      })
      return
    }

    if (isUnfinishedReport(data)) {
      report.value = data
      unfinished.value = true
      return
    }

    report.value = data
  } catch (err) {
    console.warn('游玩报告加载失败：', err)
    error.value = true
    errorMessage.value = err?.message || '报告加载失败，请稍后重试'
    report.value = null
    unfinished.value = false
  } finally {
    loading.value = false
  }
}

function setFeedbackSatisfaction(value) {
  feedbackForm.value.satisfaction = value
}

function setFeedbackRecommend(value) {
  feedbackForm.value.recommend = value
}

function unwrapFeedbackResponse(response) {
  if (!response) return null

  if (response.success === false) {
    throw new Error(response.msg || response.message || '提交失败')
  }

  if (
    Object.prototype.hasOwnProperty.call(response, 'code') &&
    response.code !== 0 &&
    response.code !== 200
  ) {
    throw new Error(response.msg || response.message || '提交失败')
  }

  return Object.prototype.hasOwnProperty.call(response, 'data')
    ? response.data
    : response
}

async function submitFeedback() {
  if (feedbackSubmitting.value) {
    return
  }

  if (!visitId.value) {
    uni.showToast({
      title: '缺少游玩记录',
      icon: 'none'
    })
    return
  }

  if (!feedbackForm.value.satisfaction) {
    uni.showToast({
      title: '请选择满意度',
      icon: 'none'
    })
    return
  }

  const payload = {
    visitId: visitId.value,
    satisfaction: feedbackForm.value.satisfaction,
    comment: String(feedbackForm.value.comment || '').trim(),
    recommend: feedbackForm.value.recommend
  }

  console.log('[visit/feedback] request:', debugStringify(payload))

  feedbackSubmitting.value = true

  try {
    const response = await request({
      url: '/api/visit/feedback',
      method: 'POST',
      data: payload,
      showErrorToast: false
    })

    console.log('[visit/feedback] response:', debugStringify(response))

    unwrapFeedbackResponse(response)

    uni.showToast({
      title: '感谢反馈',
      icon: 'success'
    })

    await loadReport()
  } catch (err) {
    console.warn('满意度反馈提交失败：', err)
    uni.showToast({
      title: '提交失败，请稍后重试',
      icon: 'none'
    })
  } finally {
    feedbackSubmitting.value = false
  }
}

function goParkDetail(item = {}) {
  const parkId = pickFirst(item.id, item.parkId, item.park_id, item.areaCode, item.area_code)

  if (!parkId) {
    uni.showToast({
      title: '暂无景区信息',
      icon: 'none'
    })
    return
  }

  uni.navigateTo({
    url: `/pages/park/detail?id=${encodeURIComponent(parkId)}`
  })
}

onLoad((options = {}) => {
  fromNativeEnd.value =
    isTruthy(options.fromNativeEnd) ||
    isTruthy(options.from_native_end) ||
    isTruthy(options.openReport) ||
    isTruthy(options.open_report)
  visitId.value = resolveVisitId(options)
  loadReport()
})

onBackPress(() => {
  goHome()
  return true
})

function goHome() {
  uni.switchTab({
    url: '/pages/index/index',
    fail() {
      uni.reLaunch({
        url: '/pages/index/index'
      })
    }
  })
}
</script>

<style>
/* ============================================================
   即境 · 游玩报告 — 闭环亮点页
   设计方向：Premium Travel Report
   签名元素：深蓝渐变 Hero + 毛玻璃摘要网格 + 时间线景点
   本轮只改 CSS，不改 template / script / 任何业务入口
   ============================================================ */

/* ---------- Page ---------- */
.page {
  min-height: 100vh;
  background: #f0f4f8;
  padding: 20rpx 24rpx 60rpx;
  box-sizing: border-box;
}

/* ---------- Base Card ---------- */
.card {
  background: #ffffff;
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04), 0 8rpx 24rpx rgba(0, 0, 0, 0.05);
}

/* ---------- Status States (loading / error / empty / unfinished) ---------- */
.status-card {
  padding: 80rpx 40rpx 64rpx;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.status-title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: #1f2937;
  line-height: 1.4;
}

.status-text,
.status-desc {
  display: block;
  margin-top: 16rpx;
  font-size: 26rpx;
  color: #6b7280;
  line-height: 1.7;
  max-width: 480rpx;
}

.retry-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 36rpx;
  min-width: 200rpx;
  height: 72rpx;
  padding: 0 36rpx;
  border-radius: 999rpx;
  background: #2f80ed;
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 600;
  box-shadow: 0 6rpx 18rpx rgba(47, 128, 237, 0.25);
  transition: opacity 0.15s ease;
}

.retry-btn:active {
  opacity: 0.85;
}

.home-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 20rpx;
  min-width: 200rpx;
  height: 72rpx;
  padding: 0 36rpx;
  border-radius: 999rpx;
  background: #eef2ff;
  color: #2f80ed;
  font-size: 26rpx;
  font-weight: 600;
  transition: opacity 0.15s ease;
}

.home-btn:active {
  opacity: 0.75;
}

/* ---------- Report Hero (签名元素 · 报告封面) ---------- */
.report-hero {
  position: relative;
  padding: 36rpx 30rpx 32rpx;
  margin-bottom: 24rpx;
  background: linear-gradient(160deg, #1d4ed8 0%, #2f80ed 45%, #56ccf2 100%);
  color: #ffffff;
  border-radius: 28rpx;
  overflow: hidden;
}

/* 装饰圆 — 右上 */
.report-hero::before {
  content: '';
  position: absolute;
  top: -48rpx;
  right: -32rpx;
  width: 180rpx;
  height: 180rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
  pointer-events: none;
}

/* 装饰圆 — 左下 */
.report-hero::after {
  content: '';
  position: absolute;
  bottom: -56rpx;
  left: -48rpx;
  width: 220rpx;
  height: 220rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.04);
  pointer-events: none;
}

.hero-tag {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.2);
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 2rpx;
}

.hero-title {
  position: relative;
  z-index: 1;
  margin-top: 20rpx;
  font-size: 44rpx;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: 2rpx;
}

.hero-subtitle {
  position: relative;
  z-index: 1;
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.6;
  opacity: 0.88;
}

/* ---------- Summary Grid (毛玻璃统计卡片) ---------- */
.summary-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12rpx;
  margin-top: 32rpx;
}

.summary-item {
  padding: 20rpx 8rpx 16rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.13);
  border: 1rpx solid rgba(255, 255, 255, 0.1);
  text-align: center;
}

.summary-value {
  font-size: 32rpx;
  font-weight: 800;
  line-height: 1.2;
  letter-spacing: 1rpx;
}

.summary-label {
  margin-top: 8rpx;
  font-size: 20rpx;
  opacity: 0.82;
  font-weight: 500;
}

/* ---------- Section Card ---------- */
.section-card {
  padding: 28rpx 26rpx;
  margin-bottom: 24rpx;
}

.section-title {
  position: relative;
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 22rpx;
  padding-left: 18rpx;
  border-left: 6rpx solid #2f80ed;
  line-height: 1.3;
}

.section-desc {
  margin-top: -10rpx;
  margin-bottom: 24rpx;
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.65;
}

/* ---------- Info List (出行信息) ---------- */
.info-list {
  display: flex;
  flex-direction: column;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24rpx;
  padding: 18rpx 0;
  border-bottom: 1rpx solid #f3f4f6;
}

.info-row:last-child {
  border-bottom: 0;
  padding-bottom: 4rpx;
}

.info-label {
  font-size: 25rpx;
  color: #6b7280;
  flex-shrink: 0;
}

.info-value {
  flex: 1;
  text-align: right;
  font-size: 25rpx;
  font-weight: 500;
  color: #1f2937;
}

.empty-line {
  padding: 28rpx 0;
  font-size: 25rpx;
  color: #9ca3af;
  line-height: 1.7;
  text-align: center;
}

/* ---------- Spot Timeline (景点停留明细 · 时间线) ---------- */
.spot-list {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0;
  padding-left: 28rpx;
  border-left: 2rpx solid #e5e7eb;
}

.spot-item {
  position: relative;
  padding: 12rpx 0 20rpx 20rpx;
  border-radius: 0;
  background: transparent;
}

/* 时间线圆点 */
.spot-item::before {
  content: '';
  position: absolute;
  left: -35rpx;
  top: 20rpx;
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #ffffff;
  border: 3rpx solid #2f80ed;
  box-shadow: 0 0 0 4rpx rgba(47, 128, 237, 0.1);
  z-index: 1;
  pointer-events: none;
}

/* 第一个点实心强调起点 */
.spot-item:first-child::before {
  background: #2f80ed;
  border-color: #2f80ed;
}

.spot-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20rpx;
}

.spot-name {
  flex: 1;
  font-size: 27rpx;
  font-weight: 600;
  color: #1f2937;
  line-height: 1.4;
}

.spot-duration {
  font-size: 22rpx;
  font-weight: 600;
  color: #18b368;
  background: #ecfdf5;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  flex-shrink: 0;
}

.spot-time {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #6b7280;
  line-height: 1.6;
}

/* ---------- Consume Status Banner ---------- */
.consume-status {
  display: flex;
  align-items: center;
  padding: 16rpx 20rpx;
  border-radius: 16rpx;
  background: #fff7ed;
  color: #ea580c;
  font-size: 24rpx;
  font-weight: 500;
  line-height: 1.5;
}

.consume-status::before {
  content: '';
  width: 8rpx;
  height: 8rpx;
  border-radius: 50%;
  margin-right: 12rpx;
  flex-shrink: 0;
  background: currentColor;
}

.consume-status.confirmed {
  background: #ecfdf5;
  color: #18b368;
}

/* ---------- Cost Grid (消费分类) ---------- */
.cost-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12rpx;
  margin-top: 20rpx;
}

.cost-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8rpx;
  padding: 20rpx 18rpx;
  border-radius: 16rpx;
  background: #f8fafb;
  border: 1rpx solid #f1f5f9;
}

.cost-label,
.cost-value {
  font-size: 24rpx;
}

.cost-label {
  color: #6b7280;
  flex-shrink: 0;
}

.cost-value {
  color: #1f2937;
  font-weight: 600;
}

/* 合计行 — 跨两列高亮 */
.cost-item:last-child {
  grid-column: 1 / -1;
  background: #eff6ff;
  border-color: #dbeafe;
  padding: 22rpx 20rpx;
  margin-top: 4rpx;
}

.cost-item:last-child .cost-label,
.cost-item:last-child .cost-value {
  font-weight: 700;
  color: #2f80ed;
  font-size: 26rpx;
}

/* ---------- Consume Detail (消费明细 · 账单条目) ---------- */
.consume-detail {
  margin-top: 20rpx;
}

.consume-list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.consume-item {
  padding: 20rpx 18rpx;
  border-radius: 16rpx;
  background: #f8fafb;
  border: 1rpx solid #f1f5f9;
}

.consume-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18rpx;
}

.consume-merchant {
  flex: 1;
  font-size: 25rpx;
  font-weight: 600;
  color: #1f2937;
  line-height: 1.4;
}

.consume-amount {
  font-size: 26rpx;
  font-weight: 700;
  color: #18b368;
  flex-shrink: 0;
}

.consume-meta {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #6b7280;
  line-height: 1.5;
}

/* ---------- Feedback Result (已反馈展示) ---------- */
.feedback-result {
  display: flex;
  flex-direction: column;
}

.feedback-result-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24rpx;
  padding: 18rpx 0;
  border-bottom: 1rpx solid #f3f4f6;
}

.feedback-result-row:last-of-type {
  border-bottom: 0;
}

.readonly-stars,
.star-select {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.star {
  font-size: 44rpx;
  color: #d1d5db;
  line-height: 1;
  transition: transform 0.15s ease, color 0.15s ease;
}

.star.active {
  color: #f59e0b;
}

.star:active {
  transform: scale(1.15);
}

.readonly-score {
  margin-left: 10rpx;
  font-size: 25rpx;
  font-weight: 600;
  color: #1f2937;
}

.comment-box {
  margin-top: 20rpx;
  padding: 22rpx 20rpx;
  border-radius: 16rpx;
  background: #f8fafb;
  border: 1rpx solid #f1f5f9;
  color: #4b5563;
  font-size: 25rpx;
  line-height: 1.7;
}

/* ---------- Feedback Form (未反馈表单) ---------- */
.feedback-form {
  display: flex;
  flex-direction: column;
}

.form-label {
  margin-top: 22rpx;
  margin-bottom: 14rpx;
  font-size: 25rpx;
  font-weight: 600;
  color: #374151;
}

.form-label:first-child {
  margin-top: 0;
}

.feedback-textarea {
  width: 100%;
  min-height: 140rpx;
  padding: 20rpx;
  box-sizing: border-box;
  border-radius: 16rpx;
  background: #f8fafb;
  border: 1rpx solid #eef0f4;
  color: #1f2937;
  font-size: 25rpx;
  line-height: 1.65;
}

.textarea-placeholder {
  color: #9ca3af;
}

.recommend-options {
  display: flex;
  gap: 16rpx;
}

.recommend-option {
  flex: 1;
  height: 72rpx;
  line-height: 72rpx;
  border-radius: 999rpx;
  background: #f3f4f6;
  color: #4b5563;
  font-size: 26rpx;
  font-weight: 500;
  text-align: center;
  border: 2rpx solid transparent;
  transition: background 0.2s ease, color 0.2s ease, border-color 0.2s ease;
}

.recommend-option:active {
  transform: scale(0.97);
}

.recommend-option.active {
  background: #eff6ff;
  color: #2f80ed;
  font-weight: 600;
  border-color: #2f80ed;
}

.feedback-submit {
  margin-top: 32rpx;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #2f80ed 0%, #3b82f6 100%);
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 700;
  text-align: center;
  letter-spacing: 2rpx;
  box-shadow: 0 8rpx 22rpx rgba(47, 128, 237, 0.3);
  transition: opacity 0.2s ease, transform 0.1s ease;
}

.feedback-submit:active {
  transform: scale(0.98);
  opacity: 0.9;
}

.feedback-submit.disabled {
  opacity: 0.5;
  box-shadow: none;
}

/* ---------- Recommend List (相似景区推荐 · 可点击卡片) ---------- */
.recommend-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.recommend-item {
  position: relative;
  padding: 22rpx 52rpx 22rpx 20rpx;
  border-radius: 18rpx;
  background: #f8fafb;
  border: 1rpx solid #f1f5f9;
  transition: background 0.15s ease, border-color 0.15s ease;
}

/* 右侧箭头 — 点击 affordance */
.recommend-item::after {
  content: '›';
  position: absolute;
  right: 22rpx;
  top: 50%;
  transform: translateY(-50%);
  font-size: 36rpx;
  color: #9ca3af;
  font-weight: 300;
  line-height: 1;
  pointer-events: none;
}

.recommend-item:active {
  background: #eff6ff;
  border-color: #dbeafe;
}

.recommend-name {
  font-size: 27rpx;
  font-weight: 600;
  color: #1f2937;
  line-height: 1.4;
}

.recommend-desc {
  margin-top: 8rpx;
  font-size: 23rpx;
  color: #6b7280;
  line-height: 1.6;
}
</style>
